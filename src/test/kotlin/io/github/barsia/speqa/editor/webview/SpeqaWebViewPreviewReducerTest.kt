package io.github.barsia.speqa.editor.webview

import io.github.barsia.speqa.model.DescriptionBlock
import io.github.barsia.speqa.model.PreconditionsBlock
import io.github.barsia.speqa.model.PreconditionsMarkerStyle
import io.github.barsia.speqa.model.Priority
import io.github.barsia.speqa.model.RunResult
import io.github.barsia.speqa.model.Status
import io.github.barsia.speqa.model.StepVerdict
import io.github.barsia.speqa.model.TestCase
import io.github.barsia.speqa.model.TestStep
import io.github.barsia.speqa.parser.PatchOperation
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeqaWebViewPreviewReducerTest {
  @Test
  fun `field change updates id and emits frontmatter patch`() {
    val change = SpeqaWebViewPreviewReducer.fieldChanged(TestCase(id = 1), "id", " 42 ")!!

    assertEquals(42, change.testCase.id)
    assertEquals(PatchOperation.SetFrontmatterField("id", "42"), change.operation)
  }

  @Test
  fun `invalid id field change is ignored`() {
    assertNull(SpeqaWebViewPreviewReducer.fieldChanged(TestCase(id = 1), "id", "abc"))
  }

  @Test
  fun `priority and status field changes use canonical labels in patches`() {
    val priority = SpeqaWebViewPreviewReducer.fieldChanged(TestCase(), "priority", "critical")!!
    val status = SpeqaWebViewPreviewReducer.fieldChanged(TestCase(), "status", "ready")!!

    assertEquals(Priority.CRITICAL, priority.testCase.priority)
    assertEquals(PatchOperation.SetFrontmatterField("priority", "critical"), priority.operation)
    assertEquals(Status.READY, status.testCase.status)
    assertEquals(PatchOperation.SetFrontmatterField("status", "ready"), status.operation)
  }

  @Test
  fun `list change trims comma separated metadata values`() {
    val change = SpeqaWebViewPreviewReducer.listChanged(TestCase(), "tags", " smoke, regression ,, ui ")!!

    assertEquals(listOf("smoke", "regression", "ui"), change.testCase.tags)
    assertEquals(PatchOperation.SetFrontmatterList("tags", listOf("smoke", "regression", "ui")), change.operation)
  }

  @Test
  fun `description body change replaces only the description block while keeping preconditions`() {
    val current = TestCase(
      bodyBlocks = listOf(
        DescriptionBlock("old"),
        PreconditionsBlock(PreconditionsMarkerStyle.PRE_CONDITIONS, "pre"),
      ),
    )

    val change = SpeqaWebViewPreviewReducer.bodyChanged(current, "description", "new")!!

    assertEquals(listOf(DescriptionBlock("new"), PreconditionsBlock(PreconditionsMarkerStyle.PRE_CONDITIONS, "pre")), change.testCase.bodyBlocks)
    assertEquals(PatchOperation.SetDescription("new"), change.operation)
  }

  @Test
  fun `preconditions body change preserves existing marker style`() {
    val current = TestCase(bodyBlocks = listOf(PreconditionsBlock(PreconditionsMarkerStyle.PRE_CONDITIONS, "old")))

    val change = SpeqaWebViewPreviewReducer.bodyChanged(current, "preconditions", "new")!!

    assertEquals(listOf(PreconditionsBlock(PreconditionsMarkerStyle.PRE_CONDITIONS, "new")), change.testCase.bodyBlocks)
    assertEquals(PatchOperation.SetPreconditions(PreconditionsMarkerStyle.PRE_CONDITIONS, "new"), change.operation)
  }

  @Test
  fun `step expected change stores blank value as null`() {
    val current = TestCase(steps = listOf(TestStep(action = "Act", expected = "Old")))

    val change = SpeqaWebViewPreviewReducer.stepChanged(current, index = 0, field = "expected", value = "   ")!!

    assertEquals(null, change.testCase.steps.single().expected)
    assertEquals(PatchOperation.SetStepExpected(0, null), change.operation)
  }

  @Test
  fun `add step appends a blank step and requests snapshot publish`() {
    val change = SpeqaWebViewPreviewReducer.addStep(TestCase(steps = listOf(TestStep(action = "Existing"))))

    assertEquals(2, change.testCase.steps.size)
    assertEquals("", change.testCase.steps.last().action)
    assertEquals(PatchOperation.AddStep(change.testCase.steps.last()), change.operation)
    assertTrue(change.publishSnapshot)
  }

  @Test
  fun `reorder step moves step and emits reorder patch`() {
    val first = TestStep(action = "first")
    val second = TestStep(action = "second")
    val third = TestStep(action = "third")
    val current = TestCase(steps = listOf(first, second, third))

    val change = SpeqaWebViewPreviewReducer.reorderStep(current, fromIndex = 0, toIndex = 2)!!

    assertEquals(listOf(second, third, first), change.testCase.steps)
    assertEquals(PatchOperation.ReorderSteps(0, 2), change.operation)
    assertTrue(change.publishSnapshot)
  }

  @Test
  fun `invalid reorder step is ignored`() {
    val current = TestCase(steps = listOf(TestStep(action = "only")))

    assertNull(SpeqaWebViewPreviewReducer.reorderStep(current, fromIndex = 0, toIndex = 0))
    assertNull(SpeqaWebViewPreviewReducer.reorderStep(current, fromIndex = 0, toIndex = 4))
  }

  @Test
  fun `decodes setStepVerdict to SetRunStepVerdict`() {
    val message = Json.parseToJsonElement(
      """{"method":"speqa/testCase/setStepVerdict","payload":{"index":2,"verdict":"failed"}}"""
    ).jsonObject

    val op = SpeqaWebViewPreviewReducer.decodePatchOperation(message)

    assertEquals(PatchOperation.SetRunStepVerdict(2, StepVerdict.FAILED), op)
  }

  @Test
  fun `decodes setStepVerdict with verdict none to StepVerdict NONE`() {
    val message = Json.parseToJsonElement(
      """{"method":"speqa/testCase/setStepVerdict","payload":{"index":0,"verdict":"none"}}"""
    ).jsonObject

    val op = SpeqaWebViewPreviewReducer.decodePatchOperation(message)

    assertEquals(PatchOperation.SetRunStepVerdict(0, StepVerdict.NONE), op)
  }

  @Test
  fun `decodes setRunResult to SetRunVerdict`() {
    val message = Json.parseToJsonElement(
      """{"method":"speqa/testCase/setRunResult","payload":{"result":"passed"}}"""
    ).jsonObject

    val op = SpeqaWebViewPreviewReducer.decodePatchOperation(message)

    assertEquals(PatchOperation.SetRunVerdict(RunResult.PASSED), op)
  }

  @Test
  fun `unknown bridge method returns null`() {
    val message = Json.parseToJsonElement(
      """{"method":"speqa/testCase/doesNotExist","payload":{}}"""
    ).jsonObject

    assertNull(SpeqaWebViewPreviewReducer.decodePatchOperation(message))
  }

  @Test
  fun `setStepVerdict without index returns null`() {
    val message = Json.parseToJsonElement(
      """{"method":"speqa/testCase/setStepVerdict","payload":{"verdict":"passed"}}"""
    ).jsonObject

    assertNull(SpeqaWebViewPreviewReducer.decodePatchOperation(message))
  }
}
