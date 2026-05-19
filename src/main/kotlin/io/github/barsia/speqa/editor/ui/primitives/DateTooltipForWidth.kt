// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package io.github.barsia.speqa.editor.ui.primitives

/**
 * Picks the tooltip string for a `DateIconLabel` icon based on whether the date
 * text next to it is being clipped by Swing's native ellipsis rendering.
 *
 * The text is considered truncated when its preferred (natural) width exceeds
 * the actual width allocated by the layout manager.
 */
fun dateTooltipForWidth(preferredWidth: Int, actualWidth: Int, normal: String, overflow: String): String {
  return if (actualWidth < preferredWidth) overflow else normal
}
