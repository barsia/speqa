// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package io.github.barsia.speqa.webview.internal.linux

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LinuxWebKitGtkBridgeJniSymbolsTest {
  private val expectedSymbols = listOf(
    "createNative",
    "destroyNative",
    "attachToParentNative",
    "detachNative",
    "setBoundsNative",
    "setVisibleNative",
    "focusNative",
    "clearFocusNative",
    "loadUrlNative",
    "loadHtmlNative",
    "evaluateJavaScriptNative",
    "deliverJsonToJavaScriptNative",
    "shutdownRuntimeNative",
  )

  @Test
  fun `Rust JNI exports use the speqa package prefix`() {
    val source = File(System.getProperty("user.dir"), "native/LinuxWebKitGtkBridge/src/lib.rs").readText()
    for (symbol in expectedSymbols) {
      val expected = "Java_io_github_barsia_speqa_webview_internal_linux_LinuxWebKitGtkBridge_$symbol"
      assertTrue(
        "Expected JNI export `$expected` in native/LinuxWebKitGtkBridge/src/lib.rs",
        source.contains(expected),
      )
    }
    assertTrue(
      "Stale `com_intellij_ui` JNI prefix must not appear",
      !source.contains("Java_com_intellij_ui_webview_internal_linux_"),
    )
  }
}
