// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package io.github.barsia.speqa.editor.webview

import io.github.barsia.speqa.model.RunResult
import io.github.barsia.speqa.model.StepVerdict
import io.github.barsia.speqa.model.TestRun
import io.github.barsia.speqa.parser.PatchOperation
import io.github.barsia.speqa.run.TestRunSupport
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

internal data class SpeqaWebViewRunChange(
  val run: TestRun,
  val primary: PatchOperation,
  val followups: List<PatchOperation>,
)

internal object SpeqaWebViewRunReducer {
  private val AUTO_DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

  /**
   * Applies a step-level verdict change. Derives `started_at` / `finished_at` from the
   * verdict transitions and, unless `resultOverride` is set, recomputes the top-level
   * `result` via [TestRunSupport.deriveRunResult]. Returns `null` for out-of-range indices.
   */
  fun stepVerdictChanged(
    current: TestRun,
    index: Int,
    verdict: StepVerdict,
    now: () -> LocalDateTime = LocalDateTime::now,
  ): SpeqaWebViewRunChange? {
    val stepResults = current.stepResults.toMutableList()
    if (index !in stepResults.indices) return null
    stepResults[index] = stepResults[index].copy(verdict = verdict)

    val previousStartedAt = current.startedAt
    val previousFinishedAt = current.finishedAt
    val newStartedAt = previousStartedAt ?: now()
    val allHaveVerdict = stepResults.isNotEmpty() && stepResults.all { it.verdict != StepVerdict.NONE }
    val newFinishedAt = if (allHaveVerdict) previousFinishedAt ?: now() else null

    val previousResult = current.result
    val newResult = if (current.resultOverride) previousResult
    else TestRunSupport.deriveRunResult(stepResults)

    val updatedRun = current.copy(
      stepResults = stepResults,
      startedAt = newStartedAt,
      finishedAt = newFinishedAt,
      result = newResult,
    )
    val followups = buildList {
      if (newResult != previousResult) {
        add(PatchOperation.SetRunVerdict(newResult))
      }
      if (newStartedAt != previousStartedAt) {
        add(PatchOperation.SetFrontmatterField("started_at", AUTO_DATE_FORMATTER.format(newStartedAt)))
      }
      if (newFinishedAt != previousFinishedAt) {
        add(
          PatchOperation.SetFrontmatterField(
            "finished_at",
            newFinishedAt?.let { AUTO_DATE_FORMATTER.format(it) },
          ),
        )
      }
    }
    return SpeqaWebViewRunChange(
      run = updatedRun,
      primary = PatchOperation.SetRunStepVerdict(index, verdict),
      followups = followups,
    )
  }

  /**
   * Applies a top-level result pick. Always sets `resultOverride = true`; emits a
   * follow-up [PatchOperation.SetRunResultOverride] only on the transition from
   * auto-mode (so we don't re-write an already-present `result_override: true` line).
   */
  fun runResultPicked(current: TestRun, picked: RunResult): SpeqaWebViewRunChange {
    val updatedRun = current.copy(result = picked, resultOverride = true)
    val followups = if (current.resultOverride) emptyList()
    else listOf(PatchOperation.SetRunResultOverride(true))
    return SpeqaWebViewRunChange(
      run = updatedRun,
      primary = PatchOperation.SetRunVerdict(picked),
      followups = followups,
    )
  }
}
