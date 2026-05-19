// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package io.github.barsia.speqa.editor.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class FloatingHeaderSlideOffsetTest {
  @Test
  fun `progress zero returns negative full height`() {
    assertEquals(-26, floatingHeaderSlideOffset(progress = 0f, totalHeight = 26))
  }

  @Test
  fun `progress one returns zero`() {
    assertEquals(0, floatingHeaderSlideOffset(progress = 1f, totalHeight = 26))
  }

  @Test
  fun `progress half returns negative half height`() {
    assertEquals(-13, floatingHeaderSlideOffset(progress = 0.5f, totalHeight = 26))
  }

  @Test
  fun `progress below zero is clamped`() {
    assertEquals(-26, floatingHeaderSlideOffset(progress = -1f, totalHeight = 26))
  }

  @Test
  fun `progress above one is clamped`() {
    assertEquals(0, floatingHeaderSlideOffset(progress = 2f, totalHeight = 26))
  }
}
