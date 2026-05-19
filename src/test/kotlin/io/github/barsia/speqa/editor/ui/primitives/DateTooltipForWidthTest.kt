// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package io.github.barsia.speqa.editor.ui.primitives

import org.junit.Assert.assertEquals
import org.junit.Test

class DateTooltipForWidthTest {
  @Test
  fun `not truncated returns normal tooltip`() {
    val r = dateTooltipForWidth(preferredWidth = 100, actualWidth = 120, normal = "Created", overflow = "Created date")
    assertEquals("Created", r)
  }

  @Test
  fun `truncated returns overflow tooltip`() {
    val r = dateTooltipForWidth(preferredWidth = 120, actualWidth = 100, normal = "Created", overflow = "Created date")
    assertEquals("Created date", r)
  }

  @Test
  fun `equal widths returns normal tooltip`() {
    val r = dateTooltipForWidth(preferredWidth = 100, actualWidth = 100, normal = "Modified", overflow = "Modified date")
    assertEquals("Modified", r)
  }

  @Test
  fun `zero actual width returns overflow tooltip`() {
    val r = dateTooltipForWidth(preferredWidth = 50, actualWidth = 0, normal = "Started", overflow = "Started date")
    assertEquals("Started date", r)
  }
}
