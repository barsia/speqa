// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package io.github.barsia.speqa.editor.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkDialogTest {
  @Test
  fun `https url is valid`() {
    assertTrue(isValidLinkUrl("https://e.com"))
  }

  @Test
  fun `http url is valid`() {
    assertTrue(isValidLinkUrl("http://x"))
  }

  @Test
  fun `empty string is invalid`() {
    assertFalse(isValidLinkUrl(""))
  }

  @Test
  fun `blank string is invalid`() {
    assertFalse(isValidLinkUrl("  "))
  }

  @Test
  fun `ftp scheme is invalid`() {
    assertFalse(isValidLinkUrl("ftp://x"))
  }

  @Test
  fun `bare host without scheme is invalid`() {
    assertFalse(isValidLinkUrl("e.com"))
  }

  @Test
  fun `javascript scheme is invalid`() {
    assertFalse(isValidLinkUrl("javascript:alert(1)"))
  }

  @Test
  fun `bare scheme with nothing after it is invalid`() {
    assertFalse(isValidLinkUrl("https://"))
    assertFalse(isValidLinkUrl("http://"))
  }
}
