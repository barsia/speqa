package io.github.barsia.speqa.editor.webview

import io.github.barsia.speqa.model.DescriptionBlock
import io.github.barsia.speqa.model.PreconditionsBlock
import io.github.barsia.speqa.model.PreconditionsMarkerStyle
import io.github.barsia.speqa.model.Priority
import io.github.barsia.speqa.model.RunResult
import io.github.barsia.speqa.model.Status
import io.github.barsia.speqa.model.StepVerdict
import io.github.barsia.speqa.model.TestCase
import io.github.barsia.speqa.model.TestCaseBodyBlock
import io.github.barsia.speqa.parser.PatchOperation
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.reflect.KClass

internal data class SpeqaWebViewPreviewChange(
  val testCase: TestCase,
  val operation: PatchOperation,
  val publishSnapshot: Boolean = false,
)

internal object SpeqaWebViewPreviewReducer {
  fun fieldChanged(current: TestCase, field: String, value: String): SpeqaWebViewPreviewChange? {
    return when (field) {
      "id" -> {
        val normalized = value.trim()
        val nextId = if (normalized.isEmpty()) null else normalized.toIntOrNull() ?: return null
        SpeqaWebViewPreviewChange(
          current.copy(id = nextId),
          PatchOperation.SetFrontmatterField("id", normalized.ifEmpty { null }),
        )
      }
      "title" -> SpeqaWebViewPreviewChange(
        current.copy(title = value),
        PatchOperation.SetFrontmatterField("title", value),
      )
      "priority" -> {
        val priority = Priority.fromString(value)
        SpeqaWebViewPreviewChange(
          current.copy(priority = priority),
          PatchOperation.SetFrontmatterField("priority", priority.label),
        )
      }
      "status" -> {
        val status = Status.fromString(value)
        SpeqaWebViewPreviewChange(
          current.copy(status = status),
          PatchOperation.SetFrontmatterField("status", status.label),
        )
      }
      else -> null
    }
  }

  fun listChanged(current: TestCase, field: String, value: String): SpeqaWebViewPreviewChange? {
    val values = splitCommaSeparated(value)
    return when (field) {
      "environment" -> SpeqaWebViewPreviewChange(
        current.copy(environment = values),
        PatchOperation.SetFrontmatterList("environment", values),
      )
      "tags" -> SpeqaWebViewPreviewChange(
        current.copy(tags = values),
        PatchOperation.SetFrontmatterList("tags", values),
      )
      else -> null
    }
  }

  fun bodyChanged(current: TestCase, kind: String, value: String): SpeqaWebViewPreviewChange? {
    return when (kind) {
      "description" -> {
        val next = replaceBodyBlock(current, DescriptionBlock::class, DescriptionBlock(value))
        SpeqaWebViewPreviewChange(current.copy(bodyBlocks = next), PatchOperation.SetDescription(value))
      }
      "preconditions" -> {
        val style = current.bodyBlocks.filterIsInstance<PreconditionsBlock>().firstOrNull()?.markerStyle
                    ?: PreconditionsMarkerStyle.PRECONDITIONS
        val next = replaceBodyBlock(current, PreconditionsBlock::class, PreconditionsBlock(style, value))
        SpeqaWebViewPreviewChange(current.copy(bodyBlocks = next), PatchOperation.SetPreconditions(style, value))
      }
      else -> null
    }
  }

  fun stepChanged(current: TestCase, index: Int, field: String, value: String): SpeqaWebViewPreviewChange? {
    val oldStep = current.steps.getOrNull(index) ?: return null
    val nextSteps = current.steps.toMutableList()
    return when (field) {
      "action" -> {
        nextSteps[index] = oldStep.copy(action = value)
        SpeqaWebViewPreviewChange(current.copy(steps = nextSteps), PatchOperation.SetStepAction(index, value))
      }
      "expected" -> {
        val expected = value.ifBlank { null }
        nextSteps[index] = oldStep.copy(expected = expected)
        SpeqaWebViewPreviewChange(current.copy(steps = nextSteps), PatchOperation.SetStepExpected(index, expected))
      }
      else -> null
    }
  }

  fun addStep(current: TestCase): SpeqaWebViewPreviewChange {
    val step = SpeqaWebViewPreviewSupport.newStep()
    return SpeqaWebViewPreviewChange(
      current.copy(steps = current.steps + step),
      PatchOperation.AddStep(step),
      publishSnapshot = true,
    )
  }

  /**
   * Decodes a run-side bridge envelope into a [PatchOperation].
   *
   * The envelope shape is `{ "method": <full method id>, "payload": { ... } }`. Returns `null`
   * when the method id is unknown or required payload fields are missing/malformed. Currently
   * handles the two run-only kinds posted by the WebView in run mode; the test-case-side
   * messages continue to flow through the typed reducer functions (`fieldChanged`, `stepChanged`,
   * etc.) called directly from the panel.
   */
  fun decodePatchOperation(message: JsonObject): PatchOperation? {
    val method = message["method"]?.jsonPrimitive?.contentOrNull ?: return null
    val payload = message["payload"]?.jsonObject ?: JsonObject(emptyMap())
    return when (method) {
      "speqa/testCase/setStepVerdict" -> {
        val index = payload["index"]?.jsonPrimitive?.intOrNull ?: return null
        val verdictLabel = payload["verdict"]?.jsonPrimitive?.contentOrNull.orEmpty()
        PatchOperation.SetRunStepVerdict(index, StepVerdict.fromString(verdictLabel))
      }
      "speqa/testCase/setRunResult" -> {
        val resultLabel = payload["result"]?.jsonPrimitive?.contentOrNull.orEmpty()
        PatchOperation.SetRunVerdict(RunResult.fromString(resultLabel))
      }
      else -> null
    }
  }

  fun reorderStep(current: TestCase, fromIndex: Int, toIndex: Int): SpeqaWebViewPreviewChange? {
    if (fromIndex == toIndex) return null
    if (fromIndex !in current.steps.indices || toIndex !in current.steps.indices) return null
    val nextSteps = current.steps.toMutableList()
    val moved = nextSteps.removeAt(fromIndex)
    nextSteps.add(toIndex, moved)
    return SpeqaWebViewPreviewChange(
      current.copy(steps = nextSteps),
      PatchOperation.ReorderSteps(fromIndex, toIndex),
      publishSnapshot = true,
    )
  }

  private fun replaceBodyBlock(
    current: TestCase,
    type: KClass<out TestCaseBodyBlock>,
    replacement: TestCaseBodyBlock,
  ): List<TestCaseBodyBlock> {
    var replaced = false
    val mapped = current.bodyBlocks.mapNotNull {
      if (type.isInstance(it)) {
        if (replaced) null
        else {
          replaced = true
          replacement
        }
      }
      else {
        it
      }
    }
    val withReplacement = if (replaced) mapped else mapped + replacement
    return withReplacement.filterIsInstance<DescriptionBlock>() + withReplacement.filterIsInstance<PreconditionsBlock>()
  }

  private fun splitCommaSeparated(value: String): List<String> {
    return value
      .split(',')
      .map { it.trim() }
      .filter { it.isNotEmpty() }
  }
}
