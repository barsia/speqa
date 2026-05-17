// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package io.github.barsia.speqa.editor.webview

import org.junit.Assert.assertEquals
import org.junit.Test

class WebViewFailureMessageTest {
  @Test
  fun `single throwable with message returns that message`() {
    val t = RuntimeException("boom")
    assertEquals("boom", rootFailureMessage(t))
  }

  @Test
  fun `chain with two distinct non-blank messages returns both joined with caused-by separator`() {
    val inner = UnsatisfiedLinkError("GLIBC_2.32 not found")
    val outer = IllegalStateException("Failed to load Linux WebKitGTK bridge (Wk40).", inner)
    assertEquals(
      "Failed to load Linux WebKitGTK bridge (Wk40). | Caused by: GLIBC_2.32 not found",
      rootFailureMessage(outer),
    )
  }

  @Test
  fun `outer with null message and inner with message returns just inner message`() {
    val inner = RuntimeException("inner reason")
    val outer = RuntimeException(null, inner)
    assertEquals("inner reason", rootFailureMessage(outer))
  }

  @Test
  fun `outer with blank message and inner with message returns just inner message`() {
    val inner = RuntimeException("inner reason")
    val outer = RuntimeException("   ", inner)
    assertEquals("inner reason", rootFailureMessage(outer))
  }

  @Test
  fun `duplicate messages in chain are deduplicated`() {
    val inner = RuntimeException("same")
    val outer = RuntimeException("same", inner)
    assertEquals("same", rootFailureMessage(outer))
  }

  @Test
  fun `chain with three distinct messages joins all`() {
    val a = RuntimeException("a")
    val b = RuntimeException("b", a)
    val c = RuntimeException("c", b)
    assertEquals("c | Caused by: b | Caused by: a", rootFailureMessage(c))
  }

  @Test
  fun `throwable with no message and no cause returns simple class name`() {
    val t = RuntimeException()
    assertEquals("RuntimeException", rootFailureMessage(t))
  }

  @Test
  fun `throwable with only blank messages in chain returns outer simple class name`() {
    val inner = RuntimeException("  ")
    val outer = IllegalStateException("", inner)
    assertEquals("IllegalStateException", rootFailureMessage(outer))
  }
}
