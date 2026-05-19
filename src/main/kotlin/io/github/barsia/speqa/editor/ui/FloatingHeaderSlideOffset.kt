// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package io.github.barsia.speqa.editor.ui

/**
 * Linear interpolation for the floating-header slide-in animation.
 *
 * Returns the y-offset to apply to the bar's `location`:
 *  - At [progress] == 0 the bar is fully hidden at `y = -totalHeight`.
 *  - At [progress] == 1 the bar is fully visible at `y = 0`.
 *  - Values outside `[0, 1]` are clamped.
 */
fun floatingHeaderSlideOffset(progress: Float, totalHeight: Int): Int {
  val clamped = when {
    progress < 0f -> 0f
    progress > 1f -> 1f
    else -> progress
  }
  return (-totalHeight + (totalHeight * clamped)).toInt()
}
