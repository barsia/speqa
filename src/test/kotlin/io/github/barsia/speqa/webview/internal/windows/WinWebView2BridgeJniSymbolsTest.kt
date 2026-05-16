// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package io.github.barsia.speqa.webview.internal.windows

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

class WinWebView2BridgeJniSymbolsTest {
  private val expectedSymbols = listOf(
    "createNative",
    "destroyNative",
    "attachToParentNative",
    "detachFromParentNative",
    "setBoundsNative",
    "setVisibleNative",
    "focusNative",
    "clearFocusNative",
    "loadUrlNative",
    "loadHtmlNative",
    "evaluateJavaScriptNative",
    "deliverJsonToJavaScriptNative",
    "pumpMessagesNative",
  )

  @Test
  fun `Rust JNI exports use the speqa package prefix`() {
    val source = Files.readString(Path.of("native/WinWebView2Bridge/src/lib.rs"))
    for (symbol in expectedSymbols) {
      val expected = "Java_io_github_barsia_speqa_webview_internal_windows_WinWebView2Bridge_$symbol"
      assertTrue(
        "Expected JNI export `$expected` in native/WinWebView2Bridge/src/lib.rs",
        source.contains(expected),
      )
    }
    assertTrue(
      "Stale `com_intellij_ui` JNI prefix must not appear",
      !source.contains("Java_com_intellij_ui_webview_internal_windows_"),
    )
  }
}
