// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package io.github.barsia.speqa.run

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
