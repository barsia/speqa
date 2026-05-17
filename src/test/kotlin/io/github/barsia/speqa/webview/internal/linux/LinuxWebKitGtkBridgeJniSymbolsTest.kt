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

  @Test
  fun `lib_rs declares both webkit40 and webkit41 cfg-gated JS-eval shims`() {
    val source = File(System.getProperty("user.dir"), "native/LinuxWebKitGtkBridge/src/lib.rs").readText()
    assertTrue(
      "lib.rs must declare js_eval_async / js_eval_finish shims",
      source.contains("fn js_eval_async") && source.contains("fn js_eval_finish"),
    )
    assertTrue(
      "lib.rs must gate code on the webkit41 feature",
      source.contains("#[cfg(feature = \"webkit41\")]"),
    )
    assertTrue(
      "lib.rs must gate code on the webkit40 feature",
      source.contains("#[cfg(feature = \"webkit40\")]"),
    )
    // 4.0-only symbol (run_javascript_finish is unambiguous — not a substring of any 4.1 symbol).
    assertTrue(
      "lib.rs must reference webkit_web_view_run_javascript_finish under the webkit40 cfg",
      source.contains("webkit_web_view_run_javascript_finish"),
    )
    // 4.0-only symbol for releasing the boxed result.
    assertTrue(
      "lib.rs must declare webkit_javascript_result_unref for the webkit40 path",
      source.contains("webkit_javascript_result_unref"),
    )
    // 4.1-only symbol (evaluate_javascript_finish — distinct identifier from any 4.0 symbol).
    assertTrue(
      "lib.rs must reference webkit_web_view_evaluate_javascript_finish under the webkit41 cfg",
      source.contains("webkit_web_view_evaluate_javascript_finish"),
    )
    // Defensive: make sure both cfg-gated extern blocks exist (so the symbol checks above
    // can't be satisfied by the symbols leaking into the shared extern "C" block).
    assertTrue(
      "lib.rs must have a separate #[cfg(feature = \"webkit41\")] extern block",
      source.contains("#[cfg(feature = \"webkit41\")]\nextern \"C\""),
    )
    assertTrue(
      "lib.rs must have a separate #[cfg(feature = \"webkit40\")] extern block",
      source.contains("#[cfg(feature = \"webkit40\")]\nextern \"C\""),
    )
  }
}
