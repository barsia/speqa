// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package io.github.barsia.speqa.webview

import org.jetbrains.annotations.ApiStatus

/**
 * Platform-independent runtime facade for a native system WebView instance.
 *
 * All methods must be called from the EDT or a coroutine scope bound to the EDT,
 * unless documented otherwise. [evaluateJavaScript] is a suspend function that
 * internally dispatches to the native main thread.
 */
@ApiStatus.Experimental
interface WebViewFacade {
  fun loadUrl(url: String)

  fun loadHtml(html: String, baseUrl: String? = null)

  /**
   * Evaluates [script] in the WebView's JavaScript context and returns the result as a string,
   * or `null` if the evaluation produces no result or the WebView is closed.
   */
  suspend fun evaluateJavaScript(script: String): String?

  fun close()
}
