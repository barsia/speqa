// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package io.github.barsia.speqa.webview.interop

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import org.jetbrains.annotations.ApiStatus

/**
 * JSON-RPC 2.0 wire envelope for WebView ↔ Kotlin notifications.
 *
 * Minimal subset: carries only notification-style frames (no `id`, no `result`, no `error`).
 * Full request/response profile is specified in
 * `plugins/speqa/docs/WebView-Kotlin-JSON-RPC-Spec.md` (POC-1).
 */
@ApiStatus.Experimental
@Serializable
data class WebViewEnvelope(
  val jsonrpc: String = JSON_RPC_VERSION,
  val method: String,
  val params: JsonElement? = null,
) {
  companion object {
    const val JSON_RPC_VERSION: String = "2.0"
  }
}
