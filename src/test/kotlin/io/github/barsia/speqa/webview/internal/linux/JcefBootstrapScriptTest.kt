// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package io.github.barsia.speqa.webview.internal.linux

import org.junit.Assert.assertTrue
import org.junit.Test

class JcefBootstrapScriptTest {
  @Test
  fun `script installs webviewIpc shim under webkit messageHandlers`() {
    val script = JcefBootstrapScript.build(queryInjection = "__JBCEF_QUERY__(raw)")
    assertTrue(script.contains("window.webkit"))
    assertTrue(script.contains("messageHandlers"))
    assertTrue(script.contains("webviewIpc"))
    assertTrue(script.contains("postMessage"))
    assertTrue(script.contains("__JBCEF_QUERY__(raw)"))
  }

  @Test
  fun `script is idempotent and does not overwrite an existing shim`() {
    val script = JcefBootstrapScript.build(queryInjection = "QUERY")
    assertTrue(script.contains("window.webkit = window.webkit || {}"))
    assertTrue(script.contains("window.webkit.messageHandlers = window.webkit.messageHandlers || {}"))
  }
}
