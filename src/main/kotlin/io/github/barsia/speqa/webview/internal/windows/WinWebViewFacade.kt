// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package io.github.barsia.speqa.webview.internal.windows

import com.intellij.openapi.application.PathManager
import io.github.barsia.speqa.webview.WebViewFacade
import io.github.barsia.speqa.webview.internal.WebViewLogger
import com.intellij.util.ui.EDT
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.suspendCancellableCoroutine
import org.jetbrains.annotations.ApiStatus
import java.awt.Component
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import javax.swing.Timer
import javax.swing.SwingUtilities
import kotlin.coroutines.resume

@ApiStatus.Internal
internal class WinWebViewFacade(
  parentScope: CoroutineScope,
) : WebViewFacade {

  private enum class State { New, Creating, Active, Closing, Closed }

  private sealed interface PendingLoad {
    data class Url(val url: String) : PendingLoad
    data class Html(val html: String, val baseUrl: String?) : PendingLoad
  }

  private data class PendingBounds(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val scale: Double,
  )

  private val state = AtomicReference(State.New)

  @Suppress("RAW_SCOPE_CREATION") // Intentional: facade manages its own child scope lifecycle with close()
  private val scope = CoroutineScope(parentScope.coroutineContext + SupervisorJob(parentScope.coroutineContext[Job]))

  private val handleReady = CompletableDeferred<Long>()
  private val nextEvalId = AtomicLong(0)
  private val pendingEvals = ConcurrentHashMap<Long, (String?) -> Unit>()

  @Volatile
  private var nativeHandle: Long = 0

  @Volatile
  private var inboundMessageHandler: (String) -> Unit = {}

  @Volatile
  private var pendingLoad: PendingLoad? = null

  @Volatile
  private var pendingBounds: PendingBounds? = null

  @Volatile
  private var shortcutTarget: Component? = null

  @Volatile
  private var hidden = false

  private var messagePumpTimer: Timer? = null

  private val callbacks = object : WinWebView2Bridge.Callbacks {
    override fun onCreated(handle: Long) {
      runOnEdt {
        if (state.get() != State.Creating || (nativeHandle != 0L && nativeHandle != handle)) {
          WinWebView2Bridge.destroy(handle)
          return@runOnEdt
        }

        nativeHandle = handle
        state.set(State.Active)
        handleReady.complete(handle)
        applyPendingState(handle)
        startMessagePump()
        WebViewLogger.logLifecycle("win-webview2-create", "WebView2 ready")
      }
    }

    override fun onCreateFailed(message: String) {
      state.set(State.Closed)
      stopMessagePump()
      handleReady.completeExceptionally(IllegalStateException(message))
      cancelPendingEvaluations()
      WebViewLogger.LOG.warn("Failed to initialize WebView2: $message")
    }

    override fun onMessage(raw: String) {
      inboundMessageHandler(raw)
    }

    override fun onEvaluationResult(evalId: Long, result: String?) {
      pendingEvals.remove(evalId)?.invoke(result)
    }

    override fun onEvaluationError(evalId: Long, message: String) {
      WebViewLogger.LOG.warn("WebView2 JavaScript evaluation failed: $message")
      pendingEvals.remove(evalId)?.invoke(null)
    }

    override fun onAcceleratorKeyPressed(keyEventKind: Int, virtualKey: Int, modifiers: Int, keyEventLParam: Int): Boolean {
      return WinWebViewShortcutInterop.handleAcceleratorKeyPressed(shortcutTarget, keyEventKind, virtualKey, modifiers, keyEventLParam)
    }

    override fun onLog(level: Int, message: String) {
      when {
        level >= 2 -> WebViewLogger.LOG.warn(message)
        else -> WebViewLogger.LOG.info(message)
      }
    }
  }

  fun initialize(onMessage: (String) -> Unit = {}) {
    inboundMessageHandler = onMessage
  }

  internal fun attachToParent(parentHwnd: Long) {
    while (true) {
      when (state.get()) {
        State.New -> {
          if (!state.compareAndSet(State.New, State.Creating)) continue
          runOnEdt {
            try {
              WebViewLogger.logLifecycle("win-webview2-create", "initializing WebView2")
              nativeHandle = WinWebView2Bridge.create(parentHwnd, userDataDir().toString(), callbacks)
              WinWebView2Bridge.setVisible(nativeHandle, !hidden)
            }
            catch (t: Throwable) {
              state.set(State.Closed)
              handleReady.completeExceptionally(t)
              cancelPendingEvaluations()
              WebViewLogger.LOG.warn("Failed to start WebView2 initialization", t)
            }
          }
          return
        }
        State.Creating -> {
          val handle = nativeHandle
          if (handle != 0L) {
            runOnEdt { WinWebView2Bridge.attachToParent(handle, parentHwnd) }
          }
          return
        }
        State.Active -> {
          val handle = nativeHandle
          if (handle != 0L) {
            runOnEdt {
              WinWebView2Bridge.attachToParent(handle, parentHwnd)
              applyAttachmentState(handle)
            }
          }
          return
        }
        State.Closing, State.Closed -> return
      }
    }
  }

  internal fun detachFromParent() {
    val handle = nativeHandle
    if (handle == 0L || state.get() == State.Closed) return
    runOnEdt { WinWebView2Bridge.detachFromParent(handle) }
  }

  internal fun setBounds(x: Int, y: Int, width: Int, height: Int, scale: Double) {
    val bounds = PendingBounds(x, y, width, height, scale)
    pendingBounds = bounds
    val handle = nativeHandle
    if (handle == 0L || state.get() == State.Closed) return
    runOnEdt { applyBounds(handle, bounds) }
  }

  internal fun setHidden(hidden: Boolean) {
    this.hidden = hidden
    val handle = nativeHandle
    if (handle == 0L || state.get() == State.Closed) return
    runOnEdt { WinWebView2Bridge.setVisible(handle, !hidden) }
  }

  internal fun requestFocus() {
    val handle = nativeHandle
    if (handle == 0L || state.get() != State.Active) return
    runOnEdt { WinWebView2Bridge.focus(handle) }
  }

  internal fun clearFocus() {
    val handle = nativeHandle
    if (handle == 0L || state.get() != State.Active) return
    runOnEdt { WinWebView2Bridge.clearFocus(handle) }
  }

  internal fun setShortcutTarget(target: Component?) {
    shortcutTarget = target
  }

  override fun loadUrl(url: String) {
    val load = PendingLoad.Url(url)
    pendingLoad = load
    val handle = nativeHandle
    if (handle == 0L || state.get() != State.Active) return
    runOnEdt {
      WinWebView2Bridge.loadUrl(handle, url)
      markLoadApplied(load)
    }
  }

  override fun loadHtml(html: String, baseUrl: String?) {
    val load = PendingLoad.Html(html, baseUrl)
    pendingLoad = load
    val handle = nativeHandle
    if (handle == 0L || state.get() != State.Active) return
    runOnEdt {
      WinWebView2Bridge.loadHtml(handle, html, baseUrl)
      markLoadApplied(load)
    }
  }

  override suspend fun evaluateJavaScript(script: String): String? {
    if (state.get() == State.New || state.get() == State.Closing || state.get() == State.Closed) return null
    val handle = awaitHandle() ?: return null
    if (state.get() != State.Active) return null

    val evalId = nextEvalId.incrementAndGet()
    return suspendCancellableCoroutine { continuation ->
      pendingEvals[evalId] = { result ->
        if (continuation.isActive) {
          continuation.resume(result)
        }
      }

      continuation.invokeOnCancellation {
        pendingEvals.remove(evalId)
      }

      runOnEdt {
        if (state.get() != State.Active) {
          pendingEvals.remove(evalId)?.invoke(null)
          return@runOnEdt
        }
        WinWebView2Bridge.evaluateJavaScript(handle, evalId, script)
      }
    }
  }

  internal fun deliverJsonToJavaScript(rawJson: String) {
    val handle = nativeHandle
    if (handle == 0L || state.get() != State.Active) return
    runOnEdt {
      WinWebView2Bridge.deliverJsonToJavaScript(handle, rawJson)
    }
  }

  override fun close() {
    loop@ while (true) {
      when (val current = state.get()) {
        State.New -> {
          if (state.compareAndSet(State.New, State.Closed)) {
            scope.cancel()
            cancelPendingEvaluations()
            handleReady.cancel(CancellationException("Facade closed before initialization"))
            WebViewLogger.logLifecycle("win-webview2-close", "closed from New state")
            return
          }
        }
        State.Creating, State.Active -> {
          if (state.compareAndSet(current, State.Closing)) break@loop
        }
        State.Closing, State.Closed -> {
          WebViewLogger.logLifecycle("win-webview2-close", "already closing/closed, idempotent no-op")
          return
        }
      }
    }

    cancelPendingEvaluations()
    scope.cancel()

    val handle = nativeHandle
    nativeHandle = 0
    if (handle != 0L) {
      runOnEdt {
        stopMessagePump()
        WinWebView2Bridge.destroy(handle)
        handleReady.cancel(CancellationException("Facade closed"))
        state.set(State.Closed)
        WebViewLogger.logLifecycle("win-webview2-close", "native cleanup complete")
      }
    }
    else {
      handleReady.cancel(CancellationException("Facade closed"))
      state.set(State.Closed)
    }
  }

  private fun applyPendingState(handle: Long) {
    applyAttachmentState(handle)
    when (val load = pendingLoad) {
      is PendingLoad.Url -> {
        WinWebView2Bridge.loadUrl(handle, load.url)
        markLoadApplied(load)
      }
      is PendingLoad.Html -> {
        WinWebView2Bridge.loadHtml(handle, load.html, load.baseUrl)
        markLoadApplied(load)
      }
      null -> Unit
    }
  }

  private fun applyAttachmentState(handle: Long) {
    pendingBounds?.let { applyBounds(handle, it) }
    WinWebView2Bridge.setVisible(handle, !hidden)
  }

  private fun markLoadApplied(load: PendingLoad?) {
    if (pendingLoad === load) {
      pendingLoad = null
    }
  }

  private fun applyBounds(handle: Long, bounds: PendingBounds) {
    WinWebView2Bridge.setBounds(handle, bounds.x, bounds.y, bounds.width, bounds.height, bounds.scale)
  }

  private suspend fun awaitHandle(): Long? {
    val handle = nativeHandle
    if (handle != 0L && state.get() == State.Active) return handle

    return try {
      handleReady.await()
    }
    catch (_: Throwable) {
      null
    }
  }

  private fun cancelPendingEvaluations() {
    pendingEvals.keys.forEach { evalId ->
      pendingEvals.remove(evalId)?.invoke(null)
    }
  }

  private fun startMessagePump() {
    if (messagePumpTimer?.isRunning == true) return

    val timer = Timer(PUMP_INTERVAL_MILLIS) {
      if (state.get() != State.Active || nativeHandle == 0L) {
        stopMessagePump()
        return@Timer
      }
      WinWebView2Bridge.pumpMessages(MAX_PUMP_MESSAGES_PER_TICK)
    }
    timer.isRepeats = true
    timer.isCoalesce = true
    messagePumpTimer = timer
    timer.start()
  }

  private fun stopMessagePump() {
    messagePumpTimer?.stop()
    messagePumpTimer = null
  }

  private fun userDataDir(): Path = Path.of(PathManager.getSystemPath(), "webview2")

  private fun runOnEdt(action: () -> Unit) {
    if (EDT.isCurrentThreadEdt()) {
      action()
    }
    else {
      SwingUtilities.invokeLater(action)
    }
  }

  private companion object {
    private const val PUMP_INTERVAL_MILLIS = 16
    private const val MAX_PUMP_MESSAGES_PER_TICK = 16
  }
}

@ApiStatus.Internal
internal fun createWinWebViewFacade(parentScope: CoroutineScope): WinWebViewFacade {
  return WinWebViewFacade(parentScope)
}
