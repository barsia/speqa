// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package io.github.barsia.speqa.webview.internal.linux

import org.jetbrains.annotations.ApiStatus

/**
 * Builds the JS bootstrap snippet injected into every page loaded by the JCEF Linux backend.
 *
 * The snippet exposes `window.webkit.messageHandlers.webviewIpc.postMessage(raw)` so
 * preview.js dispatches outbound JSON-RPC frames with platform-agnostic code.
 *
 * The actual JS-to-Kotlin transport is `JBCefJSQuery`, whose `inject(<jsArg>)` returns a
 * JS expression that, when evaluated, fires the registered handler. The caller passes
 * the injected expression as [queryInjection], which is interpolated into the shim body.
 */
@ApiStatus.Internal
internal object JcefBootstrapScript {
  fun build(queryInjection: String): String =
    """
    (function() {
      window.webkit = window.webkit || {};
      window.webkit.messageHandlers = window.webkit.messageHandlers || {};
      if (window.webkit.messageHandlers.webviewIpc) return;
      window.webkit.messageHandlers.webviewIpc = {
        postMessage: function(raw) {
          $queryInjection;
        }
      };
    })();
    """.trimIndent()
}
