// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package io.github.barsia.speqa.run

import io.github.barsia.speqa.model.RunCase
import io.github.barsia.speqa.model.RunResult

/**
 * Locale-independent progress arithmetic for multi-case runs. Display strings are
 * formatted by the UI layer from the returned counts via the resource bundle.
 */
object RunProgressText {
  /**
   * Counts how many cases have reached a terminal result (anything other than
   * [RunResult.NOT_STARTED] or [RunResult.IN_PROGRESS]).
   *
   * @return `done to total`, where `total` is the number of cases.
   */
  fun caseProgress(cases: List<RunCase>): Pair<Int, Int> {
    val done = cases.count { it.result != RunResult.NOT_STARTED && it.result != RunResult.IN_PROGRESS }
    return done to cases.size
  }
}

/**
 * Composes the `"Progress: X/N"` string shown in the floating-header progress
 * label for the test-run preview. Negative inputs are treated as zero;
 * [completedCount] is clamped to `[0, stepCount]`.
 */
fun runProgressText(stepCount: Int, completedCount: Int): String {
  val total = stepCount.coerceAtLeast(0)
  val done = completedCount.coerceAtLeast(0).coerceAtMost(total)
  return "Progress: $done/$total"
}
