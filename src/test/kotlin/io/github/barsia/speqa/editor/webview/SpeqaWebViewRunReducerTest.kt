// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package io.github.barsia.speqa.editor.webview

import io.github.barsia.speqa.model.RunResult
import io.github.barsia.speqa.model.StepResult
import io.github.barsia.speqa.model.StepVerdict
import io.github.barsia.speqa.model.TestRun
import io.github.barsia.speqa.parser.PatchOperation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class SpeqaWebViewRunReducerTest {
  private val fixedNow: LocalDateTime = LocalDateTime.of(2026, 5, 17, 14, 0)
  private val now: () -> LocalDateTime = { fixedNow }

  private fun runWith(vararg verdicts: StepVerdict, resultOverride: Boolean = false, result: RunResult = RunResult.NOT_STARTED): TestRun =
    TestRun(
      resultOverride = resultOverride,
      result = result,
      stepResults = verdicts.map { StepResult(action = "a", expected = "e", verdict = it) },
    )

  @Test
  fun `step verdict change without resultOverride derives top-level result`() {
    val current = runWith(StepVerdict.NONE, StepVerdict.NONE)
    val change = SpeqaWebViewRunReducer.stepVerdictChanged(current, index = 0, verdict = StepVerdict.PASSED, now = now)!!

    assertEquals(RunResult.IN_PROGRESS, change.run.result)
    assertEquals(PatchOperation.SetRunStepVerdict(0, StepVerdict.PASSED), change.primary)
    assertTrue(change.followups.contains(PatchOperation.SetRunVerdict(RunResult.IN_PROGRESS)))
  }

  @Test
  fun `all steps passed derives PASSED top-level result`() {
    val current = runWith(StepVerdict.PASSED, StepVerdict.NONE)
    val change = SpeqaWebViewRunReducer.stepVerdictChanged(current, index = 1, verdict = StepVerdict.PASSED, now = now)!!

    assertEquals(RunResult.PASSED, change.run.result)
    assertTrue(change.followups.contains(PatchOperation.SetRunVerdict(RunResult.PASSED)))
  }

  @Test
  fun `step verdict change with resultOverride preserves top-level result`() {
    val current = runWith(
      StepVerdict.NONE, StepVerdict.NONE,
      resultOverride = true,
      result = RunResult.BLOCKED,
    )
    val change = SpeqaWebViewRunReducer.stepVerdictChanged(current, index = 0, verdict = StepVerdict.PASSED, now = now)!!

    assertEquals(RunResult.BLOCKED, change.run.result)
    assertTrue(change.run.resultOverride)
    assertTrue(change.followups.none { it is PatchOperation.SetRunVerdict })
  }

  @Test
  fun `no follow-up SetRunVerdict when derived equals current result`() {
    val current = runWith(StepVerdict.PASSED, StepVerdict.NONE, result = RunResult.IN_PROGRESS)
    val change = SpeqaWebViewRunReducer.stepVerdictChanged(current, index = 1, verdict = StepVerdict.NONE, now = now)!!

    assertEquals(RunResult.IN_PROGRESS, change.run.result)
    assertTrue(change.followups.none { it is PatchOperation.SetRunVerdict })
  }

  @Test
  fun `out of range index returns null`() {
    val change = SpeqaWebViewRunReducer.stepVerdictChanged(runWith(), index = 0, verdict = StepVerdict.PASSED, now = now)
    assertNull(change)
  }

  @Test
  fun `run result picked sets resultOverride and emits override patch on first transition`() {
    val current = runWith(StepVerdict.NONE, resultOverride = false)
    val change = SpeqaWebViewRunReducer.runResultPicked(current, RunResult.BLOCKED)

    assertEquals(RunResult.BLOCKED, change.run.result)
    assertTrue(change.run.resultOverride)
    assertEquals(PatchOperation.SetRunVerdict(RunResult.BLOCKED), change.primary)
    assertEquals(listOf(PatchOperation.SetRunResultOverride(true)), change.followups)
  }

  @Test
  fun `run result picked skips override patch when resultOverride already true`() {
    val current = runWith(StepVerdict.NONE, resultOverride = true, result = RunResult.FAILED)
    val change = SpeqaWebViewRunReducer.runResultPicked(current, RunResult.PASSED)

    assertEquals(RunResult.PASSED, change.run.result)
    assertTrue(change.run.resultOverride)
    assertEquals(emptyList<PatchOperation>(), change.followups)
  }
}
