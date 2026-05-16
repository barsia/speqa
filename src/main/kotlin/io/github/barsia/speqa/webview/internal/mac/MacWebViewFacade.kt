// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package io.github.barsia.speqa.webview.internal.mac

import com.intellij.ui.mac.foundation.Foundation
import com.intellij.ui.mac.foundation.ID
import io.github.barsia.speqa.webview.WebViewFacade
import io.github.barsia.speqa.webview.internal.MacMainThreadDispatcher
import io.github.barsia.speqa.webview.internal.WebViewLogger
import io.github.barsia.speqa.webview.internal.host.NativeOverlayClipShape
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus
import java.awt.event.KeyEvent
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * macOS implementation of [WebViewFacade] backed by a native `WKWebView`.
 *
 * Lifecycle state machine: `New → Active → Closing → Closed`.
 *
 * All native operations are dispatched to the macOS main thread via [MacMainThreadDispatcher].
 * The facade creates a child [CoroutineScope] with [SupervisorJob] from the provided parent scope.
 */
@ApiStatus.Internal
internal class MacWebViewFacade(
  parentScope: CoroutineScope,
) : WebViewFacade {

  private companion object {
    const val EVAL_PREFIX = "__eval__:"
    const val EVAL_ERROR_PREFIX = "__eval_err__:"
  }

  private enum class State { New, Active, Closing, Closed }

  private val state = AtomicReference(State.New)

  @Suppress("RAW_SCOPE_CREATION") // Intentional: facade manages its own child scope lifecycle with close()
  private val scope = CoroutineScope(parentScope.coroutineContext + SupervisorJob(parentScope.coroutineContext[Job]))

  @Volatile
  private var handles: WKWebViewBridge.WebViewHandles? = null

  @Volatile
  private var inboundMessageHandler: (String) -> Unit = {}

  private val handlesReady = CompletableDeferred<WKWebViewBridge.WebViewHandles>()

  private val nextEvalId = AtomicLong(0)
  private val nextOutgoingLogId = AtomicLong(0)
  private val nextIncomingLogId = AtomicLong(0)
  private val pendingEvals = ConcurrentHashMap<Long, (String?) -> Unit>()

  /**
   * Initializes the native WKWebView. Must be called before any other facade method.
   * Called internally when the host panel attaches.
   *
   * @param onMessage callback invoked on the main thread for JS→JVM messages
   */
  fun initialize(onMessage: (String) -> Unit = {}) {
    while (true) {
      when (val current = state.get()) {
        State.New -> {
          if (!state.compareAndSet(State.New, State.Active)) continue

          // Install the handler only on the New -> Active transition. Subsequent
          // idempotent initialize() calls (e.g. from SwingWebViewHostPanel.addNotify)
          // must not clobber the handler the facade was constructed with.
          inboundMessageHandler = onMessage

          WebViewLogger.logLifecycle("create", "initializing WKWebView")
          scope.launch(MacMainThreadDispatcher) {
            try {
              val t0 = System.currentTimeMillis()
              val createdHandles = WKWebViewBridge.createWKWebView { message ->
                handleIncomingMessage(message)
              }
              WebViewLogger.logPerf("wkwebview-create", System.currentTimeMillis() - t0)

              if (state.get() != State.Active || !handlesReady.complete(createdHandles)) {
                WKWebViewBridge.release(createdHandles)
                handlesReady.cancel(CancellationException("Facade was closed during initialization"))
                return@launch
              }

              handles = createdHandles
              WebViewLogger.logLifecycle("create", "WKWebView ready")
            }
            catch (t: Throwable) {
              handlesReady.completeExceptionally(t)
              state.set(State.Closed)
              WebViewLogger.LOG.warn("Failed to initialize WKWebView", t)
            }
          }
          return
        }
        State.Active -> return
        State.Closing, State.Closed -> {
          WebViewLogger.LOG.warn("initialize() ignored: facade is $current")
          return
        }
      }
    }
  }

  override fun loadUrl(url: String) {
    ensureInitialized()
    if (state.get() != State.Active) return

    scope.launch(MacMainThreadDispatcher) {
      val wv = awaitWebViewId() ?: return@launch
      if (state.get() != State.Active) return@launch
      WKWebViewBridge.loadUrl(wv, url)
    }
  }

  override fun loadHtml(html: String, baseUrl: String?) {
    ensureInitialized()
    if (state.get() != State.Active) return

    scope.launch(MacMainThreadDispatcher) {
      val wv = awaitWebViewId() ?: return@launch
      if (state.get() != State.Active) return@launch
      WKWebViewBridge.loadHtml(wv, html, baseUrl)
    }
  }

  override suspend fun evaluateJavaScript(script: String): String? {
    ensureInitialized()
    if (state.get() != State.Active) return null

    val evalId = nextEvalId.incrementAndGet()

    return suspendCancellableCoroutine { continuation ->
      pendingEvals[evalId] = { result ->
        if (continuation.isActive) {
          continuation.resumeWith(Result.success(result))
        }
      }

      continuation.invokeOnCancellation {
        pendingEvals.remove(evalId)
      }

      scope.launch(MacMainThreadDispatcher) {
        val wv = awaitWebViewId()
        if (wv == null || state.get() != State.Active) {
          pendingEvals.remove(evalId)?.invoke(null)
          return@launch
        }

        WKWebViewBridge.evaluateJavaScript(wv, script, evalId)
      }
    }
  }

  /**
   * Sends a raw JSON-RPC frame to JS runtime via `window.__KWRY__.__deliver(...)`.
   */
  @Suppress("unused")
  internal fun deliverJsonToJavaScript(rawJson: String) {
    ensureInitialized()
    if (state.get() != State.Active) return

    scope.launch(MacMainThreadDispatcher) {
      val wv = awaitWebViewId() ?: return@launch
      if (state.get() != State.Active) return@launch
      logOutgoingToJavaScript(rawJson)
      WKWebViewBridge.deliverJsonToJavaScript(wv, rawJson)
    }
  }

  override fun close() {
    while (true) {
      when (state.get()) {
        State.New -> {
          if (state.compareAndSet(State.New, State.Closed)) {
            scope.cancel()
            cancelPendingEvaluations()
            handlesReady.cancel(CancellationException("Facade closed before initialization"))
            WebViewLogger.logLifecycle("close", "closed from New state")
            return
          }
        }
        State.Active -> {
          if (state.compareAndSet(State.Active, State.Closing)) {
            break
          }
        }
        State.Closing, State.Closed -> {
          WebViewLogger.logLifecycle("close", "already closing/closed, idempotent no-op")
          return
        }
      }
    }

    WebViewLogger.logLifecycle("close", "state=${state.get()}")
    cancelPendingEvaluations()

    scope.cancel()

    // Post native cleanup directly on macOS main thread — not through the cancelled scope.
    val currentHandles = handles
    handles = null
    if (currentHandles != null) {
      com.intellij.ui.mac.foundation.Foundation.executeOnMainThread(false, false) {
        WKWebViewBridge.release(currentHandles)
        handlesReady.cancel(CancellationException("Facade closed"))
        state.set(State.Closed)
        WebViewLogger.logLifecycle("close", "native cleanup complete")
      }
    }
    else {
      handlesReady.cancel(CancellationException("Facade closed"))
      state.set(State.Closed)
    }
  }

  /**
   * Attaches the native WKWebView as a subview of [parentNSView].
   * Must be called on the macOS main thread.
   */
  internal suspend fun attachToParent(parentNSView: ID) {
    val webViewHandles = awaitHandles() ?: return
    if (state.get() != State.Active) return
    WKWebViewBridge.attachToParent(webViewHandles.containerView, parentNSView)
  }

  /**
   * Detaches the native WKWebView from its superview.
   * Must be called on the macOS main thread.
   */
  internal fun detachFromParent() {
    val webViewHandles = handles ?: return
    WKWebViewBridge.detachFromParent(webViewHandles.containerView)
  }

  /**
   * Updates the native WKWebView frame to the given bounds.
   * Must be called on the macOS main thread.
   */
  internal fun setFrame(x: Double, y: Double, w: Double, h: Double) {
    val webViewHandles = handles ?: return
    WKWebViewBridge.setFrame(webViewHandles.containerView, webViewHandles.webView, x, y, w, h)
  }

  /**
   * Sets the visibility of the native WKWebView.
   * Must be called on the macOS main thread.
   */
  internal fun setHidden(hidden: Boolean) {
    val webViewHandles = handles ?: return
    WKWebViewBridge.setHidden(webViewHandles.containerView, hidden)
  }

  /**
   * Applies native AppKit clipping holes over Swing/AWT overlays that must appear above WKWebView.
   * Shapes are in the WebView host coordinate space after conversion to AppKit's bottom-left origin.
   *
   * Must be called on the macOS main thread.
   */
  internal fun setOverlayClipShapes(width: Int, height: Int, shapes: List<NativeOverlayClipShape>) {
    val webViewHandles = handles ?: return
    WKWebViewBridge.setOverlayClipShapes(
      webViewHandles.containerView,
      width.toDouble(),
      height.toDouble(),
      shapes,
      flushImmediately = false,
    )
  }

  /**
   * Synchronous variant of [setOverlayClipShapes] safe to invoke from any thread. When
   * [awaitNativeCommit] is `true`, blocks the caller until AppKit has committed the mask change,
   * and wraps the layer update in an explicit `CATransaction` whose commit is flushed so the
   * visual update is on-screen before this method returns. Used on overlay hide transitions
   * (balloon dismissal, popup close) to eliminate the residual mask hole frame.
   *
   * When [awaitNativeCommit] is `false`, falls back to the async path identical to
   * [setOverlayClipShapes] dispatched via [MacMainThreadDispatcher] by the caller.
   */
  internal fun setOverlayClipShapes(
    width: Int,
    height: Int,
    shapes: List<NativeOverlayClipShape>,
    awaitNativeCommit: Boolean,
  ) {
    if (!awaitNativeCommit) {
      setOverlayClipShapes(width, height, shapes)
      return
    }
    val webViewHandles = handles ?: return
    Foundation.executeOnMainThread(/* withAutoreleasePool = */ true, /* waitUntilDone = */ true) {
      WKWebViewBridge.setOverlayClipShapes(
        webViewHandles.containerView,
        width.toDouble(),
        height.toDouble(),
        shapes,
        flushImmediately = true,
      )
    }
  }

  internal fun requestFocus() {
    val wv = handles?.webView ?: return
    WKWebViewBridge.requestFocus(wv)
  }

  internal fun clearFocus() {
    val wv = handles?.webView ?: return
    WKWebViewBridge.clearFocus(wv)
  }

  internal fun dispatchNativeTextEditingShortcut(event: KeyEvent): Boolean {
    if (MacWebViewShortcutInterop.shouldConsumeWithoutDispatch(event)) {
      event.consume()
      WebViewLogger.LOG.debug(
        "Consuming WKWebView text shortcut without AppKit redispatch: keyCode=${event.keyCode}, modifiers=${event.modifiersEx}",
      )
      return true
    }
    val command = MacWebViewShortcutInterop.commandFor(event) ?: return false
    val wv = handles?.webView ?: return false
    if (state.get() != State.Active) return false

    WebViewLogger.LOG.debug(
      "Dispatching native WKWebView text command: command=$command, keyCode=${event.keyCode}, modifiers=${event.modifiersEx}",
    )
    event.consume()
    scope.launch(MacMainThreadDispatcher) {
      if (state.get() == State.Active) {
        WKWebViewBridge.performEditCommand(wv, command)
      }
    }
    return true
  }

  internal fun dispatchNativeTextEditingCommand(commandName: String): Boolean {
    val command = MacWebViewShortcutInterop.commandNamed(commandName) ?: return false
    val wv = handles?.webView ?: return false
    if (state.get() != State.Active) return false

    WebViewLogger.LOG.debug("Dispatching native WKWebView text command from JS: command=$command")
    scope.launch(MacMainThreadDispatcher) {
      if (state.get() == State.Active) {
        WKWebViewBridge.performEditCommand(wv, command)
      }
    }
    return true
  }

  private fun cancelPendingEvaluations() {
    pendingEvals.keys.forEach { evalId ->
      pendingEvals.remove(evalId)?.invoke(null)
    }
  }

  private suspend fun awaitWebViewId(): ID? {
    return awaitHandles()?.webView
  }

  private suspend fun awaitHandles(): WKWebViewBridge.WebViewHandles? {
    handles?.let { return it }

    return try {
      handlesReady.await()
    }
    catch (_: Throwable) {
      null
    }
  }

  private fun ensureInitialized() {
    if (state.get() == State.New) {
      initialize(inboundMessageHandler)
    }
  }

  private fun handleIncomingMessage(message: String) {
    logIncomingFromJavaScript(message)
    if (tryCompleteEvaluation(message)) return
    inboundMessageHandler(message)
  }

  private fun logOutgoingToJavaScript(rawJson: String) {
    val count = nextOutgoingLogId.incrementAndGet()
    val message = "Delivering WebView message to JS #$count (${rawJson.length} chars)"
    if (count <= 5) {
      WebViewLogger.LOG.info(message)
    }
    else {
      WebViewLogger.LOG.debug(message)
    }
  }

  private fun logIncomingFromJavaScript(message: String) {
    val count = nextIncomingLogId.incrementAndGet()
    WebViewLogger.LOG.info("Received WebView message from JS #$count (${message.length} chars)")
  }

  private fun tryCompleteEvaluation(message: String): Boolean {
    val isError = message.startsWith(EVAL_ERROR_PREFIX)
    val prefix = when {
      isError -> EVAL_ERROR_PREFIX
      message.startsWith(EVAL_PREFIX) -> EVAL_PREFIX
      else -> return false
    }

    val rest = message.removePrefix(prefix)
    val colonIdx = rest.indexOf(':')
    if (colonIdx < 0) return false

    val evalId = rest.substring(0, colonIdx).toLongOrNull() ?: return false
    val value = rest.substring(colonIdx + 1)
    pendingEvals.remove(evalId)?.invoke(if (isError) null else value)
    return true
  }

}

/**
 * Factory function for creating a macOS WebView facade.
 */
@ApiStatus.Internal
internal fun createMacWebViewFacade(parentScope: CoroutineScope): MacWebViewFacade {
  return MacWebViewFacade(parentScope)
}
