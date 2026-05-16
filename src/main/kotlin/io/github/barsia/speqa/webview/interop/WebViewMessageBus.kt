// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package io.github.barsia.speqa.webview.interop

import com.intellij.openapi.diagnostic.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.job
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.serializer
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Minimal event bus between a Kotlin host and a system WebView.
 *
 * ## Wire format
 *
 * Every frame is a JSON-RPC 2.0 notification (no `id`):
 * ```
 * {"jsonrpc":"2.0","method":"demo/board/snapshot","params":{ ... }}
 * ```
 * See [WebViewEnvelope] and `docs/poc-0-decisions/03-minimal-js-jvm-bridge.md`.
 *
 * ## Directions
 *
 * - Kotlin → JS: [publish] / [publishRaw] serialize `params`, wrap in an envelope and hand the
 *   serialized string to the `outgoingSink` that was wired at construction time (in the WKWebView
 *   backend it calls `window.__KWRY__.__deliver(raw)`).
 * - JS → Kotlin: raw strings arriving via `postMessage` are routed through [onIncomingMessage];
 *   the bus parses the envelope and fans them out by `method` to [subscribe] handlers.
 *
 * ## RPC (request/response)
 *
 * Request-style calls that `suspend` on a result are **out of scope for this minimal bus**.
 * The full protocol is specified in
 * `plugins/speqa/docs/WebView-Kotlin-JSON-RPC-Spec.md` (POC-1).
 * [request] exists here as an explicit stub so call sites can be written against the future shape;
 * invoking it throws.
 *
 * ## Lifetime
 *
 * Subscriptions are owned by the [CoroutineScope] passed to [subscribe] and auto-detach when the
 * scope is cancelled. No `Disposable` is involved.
 */
@ApiStatus.Experimental
class WebViewMessageBus internal constructor(
  private val outgoingSink: (String) -> Unit,
  private val json: Json = DEFAULT_JSON,
) {

  private val subscribers = ConcurrentHashMap<String, CopyOnWriteArrayList<(JsonElement?) -> Unit>>()
  private val loggedOutgoingMethods = ConcurrentHashMap.newKeySet<String>()

  /**
   * Kotlin → JS: serialize [params] with the supplied [serializer] and send as a notification.
   */
  fun <T> publish(method: String, params: T, serializer: KSerializer<T>) {
    val envelope = WebViewEnvelope(
      method = method,
      params = json.encodeToJsonElement(serializer, params),
    )
    val raw = json.encodeToString(WebViewEnvelope.serializer(), envelope)
    logOutgoing(method, raw)
    outgoingSink(raw)
  }

  /**
   * Kotlin → JS: send a notification with a pre-built [JsonElement] payload (or `null` for no params).
   */
  fun publishRaw(method: String, params: JsonElement? = null) {
    val envelope = WebViewEnvelope(method = method, params = params)
    val raw = json.encodeToString(WebViewEnvelope.serializer(), envelope)
    logOutgoing(method, raw)
    outgoingSink(raw)
  }

  /**
   * Registers a listener for incoming JS → Kotlin notifications with the given [method].
   * The listener is removed automatically when [scope] is cancelled.
   */
  fun subscribe(scope: CoroutineScope, method: String, handler: (JsonElement?) -> Unit) {
    val list = subscribers.computeIfAbsent(method) { CopyOnWriteArrayList() }
    list += handler
    scope.coroutineContext.job.invokeOnCompletion {
      list.remove(handler)
      subscribers.computeIfPresent(method) { _, current -> current.takeIf { it.isNotEmpty() } }
    }
  }

  /**
   * Entrypoint for raw strings arriving from JS (`postMessage` body). Parses the envelope
   * and dispatches to [subscribe] handlers. Malformed frames are silently dropped — this is the
   * documented POC-0 delivery semantic (see `docs/poc-0-decisions/03-minimal-js-jvm-bridge.md`).
   */
  fun onIncomingMessage(raw: String) {
    val envelope = try {
      json.decodeFromString(WebViewEnvelope.serializer(), raw)
    }
    catch (t: Throwable) {
      LOG.warn("Dropping malformed WebView message from JS (${raw.length} chars)", t)
      return
    }
    val list = subscribers[envelope.method]
    if (list == null) {
      LOG.info("Dropping WebView message from JS: method=${envelope.method}, subscribers=0")
      return
    }
    LOG.info("Received WebView message from JS: method=${envelope.method}, subscribers=${list.size}, hasParams=${envelope.params != null}")
    for (handler in list) {
      try {
        handler(envelope.params)
      }
      catch (t: Throwable) {
        LOG.warn("WebView message handler failed: method=${envelope.method}", t)
        // Per-handler isolation: one failing subscriber must not silence the rest.
      }
    }
  }

  private fun logOutgoing(method: String, raw: String) {
    if (loggedOutgoingMethods.add(method)) {
      LOG.info("Sending first WebView message to JS: method=$method, chars=${raw.length}")
    }
    else {
      LOG.debug("Sending WebView message to JS: method=$method, chars=${raw.length}")
    }
  }

  /**
   * Request/response RPC — **stub only**. Implementation lives in POC-1 (see
   * `plugins/speqa/docs/WebView-Kotlin-JSON-RPC-Spec.md`). The signature is
   * surfaced here so that future call sites can already reference it.
   */
  @Suppress("UNUSED_PARAMETER", "unused", "RedundantSuspendModifier")
  suspend fun <Req, Res> request(
    method: String,
    params: Req,
    paramsSerializer: KSerializer<Req>,
    resultSerializer: KSerializer<Res>,
  ): Res = error(
    "WebView request/response RPC is not implemented (POC-1 scope). " +
    "See plugins/speqa/docs/WebView-Kotlin-JSON-RPC-Spec.md."
  )

  companion object {
    private val LOG = Logger.getInstance("#io.github.barsia.speqa.webview")
    internal val DEFAULT_JSON: Json = Json {
      encodeDefaults = true
      ignoreUnknownKeys = true
    }
  }
}

/**
 * Reified convenience for [WebViewMessageBus.publish].
 */
@ApiStatus.Experimental
inline fun <reified T> WebViewMessageBus.publishTyped(method: String, params: T) {
  publish(method, params, serializer())
}
