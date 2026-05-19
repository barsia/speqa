// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package io.github.barsia.speqa.editor.ui.chips

import org.junit.Assert.assertEquals
import org.junit.Test

class EditValueResultTest {
  @Test
  fun `simple rename replaces value at its index`() {
    val r = editValueResult(listOf("a", "b", "c"), oldValue = "b", newValue = "x")
    assertEquals(listOf("a", "x", "c"), r)
  }

  @Test
  fun `unchanged value returns original list reference-equal`() {
    val input = listOf("a", "b", "c")
    val r = editValueResult(input, oldValue = "b", newValue = "b")
    assertEquals(input, r)
  }

  @Test
  fun `empty new value returns original list unchanged`() {
    val input = listOf("a", "b", "c")
    val r = editValueResult(input, oldValue = "b", newValue = "")
    assertEquals(input, r)
  }

  @Test
  fun `rename to existing other value collapses to delete-of-old`() {
    val r = editValueResult(listOf("a", "b", "c"), oldValue = "b", newValue = "c")
    assertEquals(listOf("a", "c"), r)
  }

  @Test
  fun `rename of missing value returns original unchanged`() {
    val input = listOf("a", "b", "c")
    val r = editValueResult(input, oldValue = "z", newValue = "x")
    assertEquals(input, r)
  }

  @Test
  fun `whitespace-only new value returns original unchanged`() {
    val input = listOf("a", "b", "c")
    val r = editValueResult(input, oldValue = "b", newValue = "   ")
    assertEquals(input, r)
  }

  @Test
  fun `new value is trimmed before comparison`() {
    val r = editValueResult(listOf("a", "b", "c"), oldValue = "b", newValue = "  x  ")
    assertEquals(listOf("a", "x", "c"), r)
  }
}
