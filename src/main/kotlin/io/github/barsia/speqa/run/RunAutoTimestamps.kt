// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package io.github.barsia.speqa.run

import io.github.barsia.speqa.model.RunResult
import io.github.barsia.speqa.model.StepVerdict
import io.github.barsia.speqa.model.TestRun
import java.time.LocalDateTime

/**
 * Pure helper that derives `startedAt`, `finishedAt`, and `manualResult` from
 * the proposed next [TestRun] state. The owning panel calls this on every
 * verdict / overall-result mutation and uses the diff against the previous
 * state to decide whether to emit a targeted patch op (timestamps unchanged)
 * or a whole-document write (timestamps changed, so YAML frontmatter must be
 * rewritten).
 *
 * Rules:
 *  - `startedAt` is set once, the first time any step has a non-`NONE` verdict
 *    OR the user manually picks a terminal result. Once set, it is never
 *    cleared by automatic logic.
 *  - `finishedAt` is set to `now()` when every step has a non-`NONE` verdict
 *    (the last `NONE` step just got evaluated) and stays put while the run
 *    remains fully evaluated. If any step rolls back to `NONE`, `finishedAt`
 *    is reset to `null` -- unless the user has manually picked a terminal
 *    result, in which case it stays set.
 *  - `manualResult` reflects whether the user explicitly chose a result via
 *    the combo. The caller passes `manualResultOverride = true` from the
 *    result-combo handler (cleared back to `false` when the user picks
 *    `NOT_STARTED`); other emit sites pass `null` to leave the flag alone.
 *  - `result` is derived from the step verdicts via
 *    [TestRunSupport.deriveRunResult] whenever `manualResult` is `false`:
 *    no steps evaluated -> `NOT_STARTED`, partial -> `IN_PROGRESS`, all
 *    evaluated -> `FAILED` if any `FAILED`, else `BLOCKED` if any `BLOCKED`,
 *    else `PASSED` (with `SKIPPED` treated as neutral). When `manualResult`
 *    is `true`, the user's explicit choice is preserved verbatim.
 */
object RunAutoTimestamps {
    private val TERMINAL_RESULTS = setOf(RunResult.PASSED, RunResult.FAILED, RunResult.BLOCKED)

    fun apply(
        next: TestRun,
        manualResultOverride: Boolean? = null,
        now: () -> LocalDateTime = LocalDateTime::now,
    ): TestRun {
        val manualResult = manualResultOverride ?: next.manualResult
        val hasAnyVerdict = next.stepResults.any { it.verdict != StepVerdict.NONE }
        val allEvaluated = next.stepResults.isNotEmpty() &&
            next.stepResults.all { it.verdict != StepVerdict.NONE }
        val resolvedResult = if (manualResult) {
            next.result
        } else {
            TestRunSupport.deriveRunResult(next.stepResults)
        }
        val terminalManual = manualResult && resolvedResult in TERMINAL_RESULTS

        val startedAt = next.startedAt
            ?: if (hasAnyVerdict || terminalManual) now() else null

        val finishedAt = when {
            terminalManual -> next.finishedAt ?: now()
            allEvaluated -> next.finishedAt ?: now()
            else -> null
        }

        return next.copy(
            result = resolvedResult,
            startedAt = startedAt,
            finishedAt = finishedAt,
            manualResult = manualResult,
        )
    }
}
