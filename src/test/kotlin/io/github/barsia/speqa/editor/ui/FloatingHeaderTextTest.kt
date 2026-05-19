// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package io.github.barsia.speqa.editor.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class FloatingHeaderTextTest {
  @Test
  fun `id plus title produces prefixed string`() {
    assertEquals("TC-12 · Login flow", floatingHeaderText("TC-", "12", "Login flow", untitled = "Untitled"))
  }

  @Test
  fun `no id falls back to title only`() {
    assertEquals("Login flow", floatingHeaderText("TC-", "", "Login flow", untitled = "Untitled"))
  }

  @Test
  fun `no id no title falls back to Untitled`() {
    assertEquals("Untitled", floatingHeaderText("TC-", "", "", untitled = "Untitled"))
  }

  @Test
  fun `id without title shows just id`() {
    assertEquals("TC-12", floatingHeaderText("TC-", "12", "", untitled = "Untitled"))
  }

  @Test
  fun `whitespace title is treated as blank`() {
    assertEquals("TC-12", floatingHeaderText("TC-", "12", "   ", untitled = "Untitled"))
  }

  @Test
  fun `test run prefix works the same way`() {
    assertEquals("TR-3 · Smoke pass", floatingHeaderText("TR-", "3", "Smoke pass", untitled = "Untitled"))
  }
}
