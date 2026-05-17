// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package io.github.barsia.speqa.webview.internal.linux

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefBrowserBase
import com.intellij.ui.jcef.JBCefJSQuery
import io.github.barsia.speqa.webview.WebViewFacade
import io.github.barsia.speqa.webview.internal.WebViewLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import javax.swing.JComponent

@ApiStatus.Internal
internal class JcefWebViewFacade(
  parentScope: CoroutineScope,
) : WebViewFacade, Disposable {

  private enum class State { New, Active, Closed }

  private val state = AtomicReference(State.New)

  @Suppress("RAW_SCOPE_CREATION")
  private val scope = CoroutineScope(parentScope.coroutineContext + SupervisorJob(parentScope.coroutineContext[Job]))

  private val browser: JBCefBrowser = JBCefBrowser.createBuilder()
    .setOffScreenRendering(false)
    .build()

  private val jsQuery: JBCefJSQuery = JBCefJSQuery.create(browser as JBCefBrowserBase)

  private val nextEvalId = AtomicLong(0)
  private val pendingEvals = ConcurrentHashMap<Long, (String?) -> Unit>()

  @Volatile
  private var inboundMessageHandler: (String) -> Unit = {}

  init {
    Disposer.register(this, browser)
    Disposer.register(this, jsQuery)
    state.set(State.Active)
    WebViewLogger.logLifecycle("linux-jcef-create", "JCEF browser ready")
  }

  val component: JComponent get() = browser.component

  fun initialize(onMessage: (String) -> Unit) {
    inboundMessageHandler = onMessage

    jsQuery.addHandler { raw ->
      val payload = raw ?: return@addHandler null
      inboundMessageHandler(payload)
      null
    }

    // Inject the bootstrap shim at document-start for every load.
    val loadHandler = object : org.cef.handler.CefLoadHandlerAdapter() {
      override fun onLoadStart(
        cefBrowser: org.cef.browser.CefBrowser,
        frame: org.cef.browser.CefFrame,
        transitionType: org.cef.network.CefRequest.TransitionType?,
      ) {
        val script = JcefBootstrapScript.build(queryInjection = jsQuery.inject("raw"))
        cefBrowser.executeJavaScript(script, frame.url ?: "", 0)
      }
    }
    browser.jbCefClient.addLoadHandler(loadHandler, browser.cefBrowser)
  }

  override fun loadUrl(url: String) {
    if (state.get() != State.Active) return
    browser.loadURL(url)
  }

  override fun loadHtml(html: String, baseUrl: String?) {
    if (state.get() != State.Active) return
    // JBCefBrowser.loadHTML ignores baseUrl; for our use (inlined preview.html with
    // data: URLs and no relative requests) this is fine and matches the WK/Mac path
    // which also passes baseUrl=null in practice.
    browser.loadHTML(html)
  }

  override suspend fun evaluateJavaScript(script: String): String? {
    // Wired in Task 4.
    return null
  }

  internal fun deliverJsonToJavaScript(rawJson: String) {
    if (state.get() != State.Active) return
    val escaped = escapeJsString(rawJson)
    browser.cefBrowser.executeJavaScript(
      "window.__KWRY__ && window.__KWRY__.__deliver($escaped);",
      browser.cefBrowser.url ?: "",
      0,
    )
  }

  override fun close() {
    val previous = state.getAndSet(State.Closed)
    if (previous == State.Closed) return
    cancelPendingEvaluations()
    scope.cancel()
    Disposer.dispose(this)
    WebViewLogger.logLifecycle("linux-jcef-close", "browser disposed")
  }

  override fun dispose() {
    // Disposer chain disposes browser + jsQuery; nothing else to do here.
  }

  private fun cancelPendingEvaluations() {
    pendingEvals.keys.forEach { evalId -> pendingEvals.remove(evalId)?.invoke(null) }
  }

  private fun escapeJsString(value: String): String {
    val sb = StringBuilder(value.length + 2)
    sb.append('\'')
    for (ch in value) {
      when (ch) {
        '\\' -> sb.append("\\\\")
        '\'' -> sb.append("\\'")
        '\n' -> sb.append("\\n")
        '\r' -> sb.append("\\r")
        else -> sb.append(ch)
      }
    }
    sb.append('\'')
    return sb.toString()
  }
}

@ApiStatus.Internal
internal fun createJcefWebViewFacade(parentScope: CoroutineScope): JcefWebViewFacade =
  JcefWebViewFacade(parentScope)
