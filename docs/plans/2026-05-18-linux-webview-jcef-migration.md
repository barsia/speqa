# Linux WebView Backend Migration to JCEF - Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the Linux WebKitGTK-based `WebViewFacade` backend with JCEF (`JBCefBrowser`) so the SpeQA preview is interactive on Linux X11 and Wayland; delete all WebKitGTK/native Rust bridge code.

**Architecture:** A new `JcefWebViewFacade` implementing `WebViewFacade` wraps `JBCefBrowser`. The Kotlin->JS path uses `cefBrowser.executeJavaScript("window.__KWRY__.__deliver(...)")`. The JS->Kotlin path uses `JBCefJSQuery` injected at document-start as a shim under `window.webkit.messageHandlers.webviewIpc.postMessage`, so the existing `preview.js` works unchanged. The Linux peer becomes a thin `LinuxJcefWebViewHostPeer` that adds the JCEF Swing component as a child of `SwingWebViewHostPanel`. Mac and Windows are untouched.

**Tech Stack:** Kotlin, IntelliJ Platform, JBCef (`com.intellij.ui.jcef.*`), Swing, JUnit 4.

---

## File Structure

### New files
- `src/main/kotlin/io/github/barsia/speqa/webview/internal/linux/JcefWebViewFacade.kt` - `WebViewFacade` impl backed by `JBCefBrowser`; lifecycle, JS bridge, `evaluateJavaScript`.
- `src/main/kotlin/io/github/barsia/speqa/webview/internal/linux/JcefBootstrapScript.kt` - single source of truth for the JS shim injected into the page so JCEF speaks the same IPC as Mac WK / Windows WebView2.
- `src/main/kotlin/io/github/barsia/speqa/webview/internal/linux/LinuxJcefWebViewHostPeer.kt` - `NativeWebViewHostPeer` impl that adds the JCEF component to the host panel and removes it on detach.
- `docs/superpowers/specs/2026-05-18-linux-webview-jcef.md` - replaces the WebKitGTK runtime-selection spec.
- `src/test/kotlin/io/github/barsia/speqa/webview/internal/linux/JcefBootstrapScriptTest.kt` - pure-Kotlin tests for the JS shim builder.

### Modified files
- `src/main/kotlin/io/github/barsia/speqa/webview/WebViewFacadeFactory.kt` - `createLinuxFacade` / `createLinuxFacadeWithBus` route to JCEF; `JBCefApp.isSupported()` guard; delete `linuxBackend()` private helper.
- `src/main/kotlin/io/github/barsia/speqa/webview/internal/host/NativeWebViewHostPeer.kt` - `create()` factory returns `LinuxJcefWebViewHostPeer` on Linux instead of `LinuxWaylandSnapshotWebViewHostPeer`.
- `src/test/kotlin/io/github/barsia/speqa/webview/WebViewNativeHostContractTest.kt` - remove the JCEF ban; remove the snapshot-host test; add an assertion pinning the new JCEF wiring.
- `src/main/resources/messages/SpeqaBundle.properties` - add `webview.unsupported.jcef` user-visible message.

### Deleted (final task - only after JCEF is verified working in sandbox)
- `src/main/kotlin/io/github/barsia/speqa/webview/internal/linux/LinuxWebKitWebViewFacade.kt`
- `src/main/kotlin/io/github/barsia/speqa/webview/internal/linux/LinuxWebKitGtkBridge.kt`
- `src/main/kotlin/io/github/barsia/speqa/webview/internal/linux/LinuxWebKitGtkRuntime.kt`
- `src/main/kotlin/io/github/barsia/speqa/webview/internal/linux/LinuxWebKitGtkRuntimeProbe.kt`
- `src/main/kotlin/io/github/barsia/speqa/webview/internal/linux/LinuxWebKitBackend.kt`
- `src/main/kotlin/io/github/barsia/speqa/webview/internal/linux/LinuxX11WindowUtil.kt`
- `src/main/kotlin/io/github/barsia/speqa/webview/internal/linux/LinuxWaylandWindowUtil.kt`
- `src/main/kotlin/io/github/barsia/speqa/webview/internal/linux/LinuxWaylandSnapshotWebViewHostPeer.kt`
- `src/main/kotlin/io/github/barsia/speqa/webview/internal/linux/LinuxX11NativeWebViewHostPeer.kt`
- `src/main/kotlin/io/github/barsia/speqa/webview/internal/linux/LinuxNativeWebViewHostPeer.kt`
- `src/test/kotlin/io/github/barsia/speqa/webview/LinuxWebKitGtkRuntimeSelectionTest.kt`
- `src/test/kotlin/io/github/barsia/speqa/webview/LinuxWebKitGtkBridgeBundledLookupTest.kt`
- Entire directory: `native/LinuxWebKitGtkBridge/`
- Gradle tasks `buildLinuxWebKitGtkBridge41` / `buildLinuxWebKitGtkBridge40` and the `processResources` wiring that copies `.so` files into `native/linux/wk41/` / `native/linux/wk40/`.
- The CI step in `.github/workflows/build-plugin.yml` that builds the Linux native bridge and asserts glibc floors.

---

## Pre-flight reading

Before starting, the implementer must read these files end-to-end to hold the seam in their head:
- `src/main/kotlin/io/github/barsia/speqa/webview/WebViewFacade.kt` (27 lines) - the contract.
- `src/main/kotlin/io/github/barsia/speqa/webview/WebViewFacadeFactory.kt` - the routing.
- `src/main/kotlin/io/github/barsia/speqa/webview/internal/host/NativeWebViewHostPeer.kt` - peer interface and per-platform `create()` factory.
- `src/main/kotlin/io/github/barsia/speqa/webview/SwingWebViewHostPanel.kt` (focus on `addNotify`/`removeNotify`).
- `src/main/kotlin/io/github/barsia/speqa/webview/internal/mac/WKWebViewBridge.kt` lines 114-280 - reference for the IPC channel name (`webviewIpc`), the Kotlin->JS sink (`window.__KWRY__.__deliver`), and the JS-evaluation wrapper that posts the result back through the same `postMessage` channel with envelopes `__eval__:<id>:<value>` / `__eval_err__:<id>:<msg>`. Task 4 instructs you to mirror that exact wrapper in Kotlin, swapping the WK call for `cefBrowser.executeJavaScript(...)`.
- `src/main/resources/webview/test-case-preview/preview.js` - JS-side dispatch; confirms `window.webkit.messageHandlers.webviewIpc.postMessage` is the path JCEF must satisfy.

---

### Task 1: Update the Linux WebView spec

**Files:**
- Delete: `docs/superpowers/specs/2026-05-17-linux-webkitgtk-runtime-selection.md` (obsolete)
- Create: `docs/superpowers/specs/2026-05-18-linux-webview-jcef.md`

This task is **first** because the project rule (`CLAUDE.md`) requires a spec edit before any `.kt` edit in a session; the PreToolUse hook will block otherwise. The spec must describe the *current* product, not migration history.

- [ ] **Step 1: Delete the obsolete WebKitGTK spec**

```bash
rm docs/superpowers/specs/2026-05-17-linux-webkitgtk-runtime-selection.md
```

- [ ] **Step 2: Write the new JCEF spec**

Create `docs/superpowers/specs/2026-05-18-linux-webview-jcef.md` with this exact content:

```markdown
# Spec: Linux preview uses the bundled JCEF runtime

## Current behavior

On Linux the SpeQA preview is rendered by JCEF - the Chromium build bundled with
JetBrains Runtime (`com.intellij.ui.jcef.JBCefBrowser`). JCEF is windowed and
interactive: clicks, keyboard input, IME, drag-select, scrolling, and the
browser context menu all work without per-event forwarding from the JVM.

On startup `WebViewFacadeFactory.createLinuxFacadeWithBus` checks
`JBCefApp.isSupported()`. If JCEF is unavailable on the user's IDE runtime, the
factory throws an exception with the localized message
`webview.unsupported.jcef`, which `SpeqaWebViewPreviewPanel.showUnsupportedPanel`
renders as the preview fallback.

## IPC contract

The IPC contract is identical to macOS (`WKWebView`) and Windows (`WebView2`):

- JS -> Kotlin: JS code calls
  `window.webkit.messageHandlers.webviewIpc.postMessage(jsonString)`. On Linux
  this is a thin shim injected at document-start that forwards to a
  `JBCefJSQuery` handler. On macOS this is the native `WKScriptMessageHandler`
  registered with `WKUserContentController`. On Windows this is
  `window.chrome.webview.postMessage`.
- Kotlin -> JS: Kotlin calls
  `window.__KWRY__.__deliver(rawJson)` via `cefBrowser.executeJavaScript(...)`.
- JS evaluation results: `WebViewFacade.evaluateJavaScript` mirrors the
  macOS pattern (`WKWebViewBridge.evaluateJavaScript`): the script is wrapped
  in a try/catch and the value or error is posted back through the same
  `postMessage` channel with envelopes
  `__eval__:<id>:<value>` and `__eval_err__:<id>:<message>`.

## Lifecycle

`JcefWebViewFacade` owns the `JBCefBrowser` and registers it with the IntelliJ
`Disposer`. `close()` disposes the browser and the `JBCefJSQuery`; subsequent
calls are idempotent. `LinuxJcefWebViewHostPeer.attach(host)` adds
`browser.component` to the Swing host panel as the centre of a `BorderLayout`
and calls `host.revalidate()`. `detach()` removes it.

## Non-goals

- WebKitGTK is no longer supported; the native Rust bridge under
  `native/LinuxWebKitGtkBridge/` has been removed.
- The Linux peer does not implement overlay clipping (`NativeOverlayClipShape`).
  JCEF on Linux is a heavyweight Swing component; IDE overlays (balloons,
  popups, completion popups) draw over it using their normal Swing Z-order
  because they are themselves heavyweight on Linux. Mac/Windows continue to use
  shape-based clipping because their backends are not Swing-aware.
- The Linux peer does not implement text-editing-command dispatch (cut / copy /
  paste). JCEF handles these natively via Chromium's keyboard shortcut tables;
  the previously-needed JVM-side `dispatchNativeTextEditingCommand` is dead code
  on Linux.

## Supported runtimes

Any JetBrains IDE on Linux whose bundled JBR provides JCEF
(`JBCefApp.isSupported()` returns `true`). The plugin's `since-build`
(`253.32098.37`, IntelliJ 2025.3) is well past JCEF's introduction (2020.1), so
no additional `since-build` bump is required.
```

- [ ] **Step 3: Commit**

```bash
git add docs/superpowers/specs/
git commit -m "spec: switch Linux preview spec to JCEF backend"
```

---

### Task 2: Bootstrap script builder (pure Kotlin, TDD)

The bootstrap script is the JS shim that runs on every loaded page so JCEF speaks the same IPC dialect as `WKWebView`. Because it is pure string construction, real TDD is cheap here.

**Files:**
- Create: `src/main/kotlin/io/github/barsia/speqa/webview/internal/linux/JcefBootstrapScript.kt`
- Test: `src/test/kotlin/io/github/barsia/speqa/webview/internal/linux/JcefBootstrapScriptTest.kt`

- [ ] **Step 1: Write the failing test**

Create `src/test/kotlin/io/github/barsia/speqa/webview/internal/linux/JcefBootstrapScriptTest.kt`:

```kotlin
// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package io.github.barsia.speqa.webview.internal.linux

import org.junit.Assert.assertTrue
import org.junit.Test

class JcefBootstrapScriptTest {
  @Test
  fun `script installs webviewIpc shim under webkit messageHandlers`() {
    val script = JcefBootstrapScript.build(queryInjection = "__JBCEF_QUERY__(raw)")
    assertTrue(script.contains("window.webkit"))
    assertTrue(script.contains("messageHandlers"))
    assertTrue(script.contains("webviewIpc"))
    assertTrue(script.contains("postMessage"))
    assertTrue(script.contains("__JBCEF_QUERY__(raw)"))
  }

  @Test
  fun `script is idempotent and does not overwrite an existing shim`() {
    val script = JcefBootstrapScript.build(queryInjection = "QUERY")
    assertTrue(script.contains("window.webkit = window.webkit || {}"))
    assertTrue(script.contains("window.webkit.messageHandlers = window.webkit.messageHandlers || {}"))
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "io.github.barsia.speqa.webview.internal.linux.JcefBootstrapScriptTest" 2>&1 | tail -10`
Expected: FAIL with "Unresolved reference: JcefBootstrapScript".

- [ ] **Step 3: Implement the bootstrap script builder**

Create `src/main/kotlin/io/github/barsia/speqa/webview/internal/linux/JcefBootstrapScript.kt`:

```kotlin
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
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "io.github.barsia.speqa.webview.internal.linux.JcefBootstrapScriptTest" 2>&1 | tail -10`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/io/github/barsia/speqa/webview/internal/linux/JcefBootstrapScript.kt \
        src/test/kotlin/io/github/barsia/speqa/webview/internal/linux/JcefBootstrapScriptTest.kt
git commit -m "linux: introduce JCEF bootstrap script builder"
```

---

### Task 3: JCEF facade skeleton with non-IPC methods

The facade is too entangled with the JCEF runtime to fully unit-test, so we build it in two passes. Pass 1 (this task): the lifecycle skeleton and the methods that do not need IPC (`loadUrl`, `loadHtml`, `close`, `deliverJsonToJavaScript`). Pass 2 (Task 4): IPC wiring (`evaluateJavaScript`, the `onMessage` callback).

**Files:**
- Create: `src/main/kotlin/io/github/barsia/speqa/webview/internal/linux/JcefWebViewFacade.kt`
- Modify: `src/main/resources/messages/SpeqaBundle.properties`

- [ ] **Step 1: Add the user-visible error message to the resource bundle**

Open `src/main/resources/messages/SpeqaBundle.properties`. Add this line at the end (preserve any existing trailing newline):

```
webview.unsupported.jcef=SpeQA preview requires JCEF (Chromium Embedded Framework). The current IDE runtime does not provide JCEF. Install a JetBrains Runtime with JCEF to enable the preview.
```

- [ ] **Step 2: Implement the facade skeleton**

Create `src/main/kotlin/io/github/barsia/speqa/webview/internal/linux/JcefWebViewFacade.kt`:

```kotlin
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
import kotlinx.coroutines.suspendCancellableCoroutine
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import javax.swing.JComponent
import kotlin.coroutines.resume

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
    // IPC wiring is added in Task 4. The skeleton stops here.
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
```

- [ ] **Step 3: Compile to verify it builds**

Run: `./gradlew compileKotlin 2>&1 | grep -E "^e:|BUILD" | tail -20`
Expected: `BUILD SUCCESSFUL`. If `e:` lines appear, fix them before moving on (most likely candidates: a missing import for `JBCefBrowserBase` or a deprecated `JBCefJSQuery.create` overload. Check the JBR JCEF docs for the current API).

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/io/github/barsia/speqa/webview/internal/linux/JcefWebViewFacade.kt \
        src/main/resources/messages/SpeqaBundle.properties
git commit -m "linux: add JCEF facade skeleton (no IPC yet)"
```

---

### Task 4: Wire IPC (JS-to-Kotlin and JS-eval result protocol)

Hook `JBCefJSQuery` to the inbound handler, inject the bootstrap shim on every load, and implement `evaluateJavaScript` by **mirroring the existing macOS pattern** in `src/main/kotlin/io/github/barsia/speqa/webview/internal/mac/WKWebViewBridge.kt` lines 252-264 (the `evaluateJavaScript(webView, script, evalId)` function). Reuse the same JS template that posts the result/error back through `window.webkit.messageHandlers.webviewIpc.postMessage` with the `__eval__:<id>:<value>` / `__eval_err__:<id>:<message>` envelopes. The only differences are:

- Call `browser.cefBrowser.executeJavaScript(taggedScript, browser.cefBrowser.url ?: "", 0)` instead of the WK `SEL_EVALUATE_JAVASCRIPT` selector.
- The receiving side is the `JBCefJSQuery` handler (added below) instead of the WK script-message handler.

**Files:**
- Modify: `src/main/kotlin/io/github/barsia/speqa/webview/internal/linux/JcefWebViewFacade.kt`

- [ ] **Step 1: Read the macOS reference**

Run: `sed -n '244,265p' src/main/kotlin/io/github/barsia/speqa/webview/internal/mac/WKWebViewBridge.kt`

Note the exact JS template. Copy it into the next step, swapping only the postMessage target. Keep `try/catch` and the `__eval__:` / `__eval_err__:` envelope strings byte-identical.

- [ ] **Step 2: Replace `initialize` with the wired version**

In `JcefWebViewFacade.kt`, replace the body of `initialize(onMessage)` with:

```kotlin
fun initialize(onMessage: (String) -> Unit) {
  inboundMessageHandler = onMessage

  jsQuery.addHandler { raw ->
    val payload = raw ?: return@addHandler null
    val evalPrefix = "__eval__:"
    val evalErrPrefix = "__eval_err__:"
    when {
      payload.startsWith(evalPrefix) -> {
        val rest = payload.removePrefix(evalPrefix)
        val sep = rest.indexOf(':')
        if (sep > 0) {
          val evalId = rest.substring(0, sep).toLongOrNull()
          val value = rest.substring(sep + 1)
          if (evalId != null) pendingEvals.remove(evalId)?.invoke(value)
        }
      }
      payload.startsWith(evalErrPrefix) -> {
        val rest = payload.removePrefix(evalErrPrefix)
        val sep = rest.indexOf(':')
        if (sep > 0) {
          val evalId = rest.substring(0, sep).toLongOrNull()
          val msg = rest.substring(sep + 1)
          WebViewLogger.LOG.warn("JCEF JavaScript evaluation failed: $msg")
          if (evalId != null) pendingEvals.remove(evalId)?.invoke(null)
        }
      }
      else -> inboundMessageHandler(payload)
    }
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
```

- [ ] **Step 3: Implement `evaluateJavaScript` by mirroring the macOS template**

Replace the stub `evaluateJavaScript` with an implementation that:

1. Returns `null` if `state.get() != State.Active`.
2. Allocates a new `evalId` via `nextEvalId.incrementAndGet()`.
3. Calls `suspendCancellableCoroutine` and stores a `pendingEvals[evalId] = { resume }` resolver.
4. Registers `continuation.invokeOnCancellation { pendingEvals.remove(evalId) }`.
5. Builds `taggedScript` by **copying the JS template from `WKWebViewBridge.evaluateJavaScript` lines 253-262 verbatim**: same `try/catch`, same `__eval__:$evalId:` / `__eval_err__:$evalId:` envelopes, same `String(__result)` stringification. Only the surrounding `executeJavaScript` call changes:

```kotlin
browser.cefBrowser.executeJavaScript(taggedScript, browser.cefBrowser.url ?: "", 0)
```

Place the new implementation in the same spot in `JcefWebViewFacade.kt` (replacing the stub). Use `escapeJsString(script)` exactly like the macOS pattern does.

- [ ] **Step 4: Compile**

Run: `./gradlew compileKotlin 2>&1 | grep -E "^e:|BUILD" | tail -20`
Expected: `BUILD SUCCESSFUL`. Likely fix-ups: the exact `org.cef.handler.CefLoadHandlerAdapter` `onLoadStart` signature varies across JBR versions; if compile fails, replace with the version-current overload (2-arg or 3-arg).

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/io/github/barsia/speqa/webview/internal/linux/JcefWebViewFacade.kt
git commit -m "linux: wire JCEF IPC (postMessage shim + eval-result envelope)"
```

---

### Task 5: Linux JCEF host peer

The peer adapts the JCEF Swing component to the `NativeWebViewHostPeer` interface that `SwingWebViewHostPanel` consumes. Most peer methods become no-ops on Linux because JCEF, being a Swing component, handles geometry / visibility / focus through standard Swing layout. We don't need per-event forwarding.

**Files:**
- Create: `src/main/kotlin/io/github/barsia/speqa/webview/internal/linux/LinuxJcefWebViewHostPeer.kt`

- [ ] **Step 1: Read the peer interface to confirm method signatures**

Run: `cat src/main/kotlin/io/github/barsia/speqa/webview/internal/host/NativeWebViewHostPeer.kt`

The interface defines `attach`, `detach`, `scheduleFrameUpdate`, `updateVisibility`, `updateOverlayClipRects`, `requestFocus`, `clearFocus`, `dispatchNativeTextEditingShortcut`, `dispatchNativeTextEditingCommand`. Note the exact signatures. The next step must match them.

- [ ] **Step 2: Implement the peer**

Create `src/main/kotlin/io/github/barsia/speqa/webview/internal/linux/LinuxJcefWebViewHostPeer.kt`:

```kotlin
// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package io.github.barsia.speqa.webview.internal.linux

import io.github.barsia.speqa.webview.SwingWebViewHostPanel
import io.github.barsia.speqa.webview.internal.host.NativeOverlayClipShape
import io.github.barsia.speqa.webview.internal.host.NativeWebViewHostPeer
import org.jetbrains.annotations.ApiStatus
import java.awt.BorderLayout
import java.awt.event.KeyEvent
import javax.swing.SwingUtilities

@ApiStatus.Internal
internal class LinuxJcefWebViewHostPeer(
  private val facade: JcefWebViewFacade,
) : NativeWebViewHostPeer {

  private var attachedHost: SwingWebViewHostPanel? = null

  override fun attach(host: SwingWebViewHostPanel) {
    attachedHost = host
    val component = facade.component
    // JCEF browser is a Swing component on Linux. Adding it as the BorderLayout
    // centre lets normal Swing layout drive its geometry; we don't need
    // scheduleFrameUpdate / setBounds plumbing.
    host.add(component, BorderLayout.CENTER)
    host.revalidate()
    host.repaint()
  }

  override fun detach() {
    val host = attachedHost ?: return
    val component = facade.component
    host.remove(component)
    host.revalidate()
    host.repaint()
    attachedHost = null
  }

  override fun scheduleFrameUpdate(host: SwingWebViewHostPanel) {
    // No-op: Swing layout already drives geometry for the embedded JCEF component.
  }

  override fun updateVisibility(host: SwingWebViewHostPanel, hidden: Boolean) {
    SwingUtilities.invokeLater { facade.component.isVisible = !hidden }
  }

  override fun updateOverlayClipRects(
    host: SwingWebViewHostPanel,
    shapes: List<NativeOverlayClipShape>,
    awaitNativeCommit: Boolean,
  ) {
    // No-op: IDE overlays draw above the JCEF heavyweight via Swing Z-order on Linux.
  }

  override fun requestFocus() {
    SwingUtilities.invokeLater { facade.component.requestFocusInWindow() }
  }

  override fun clearFocus() {
    // No-op: focus naturally leaves the JCEF component when another Swing component
    // claims the keyboard focus.
  }

  override fun dispatchNativeTextEditingShortcut(event: KeyEvent): Boolean {
    // JCEF/Chromium handles Cmd/Ctrl+X/C/V/A/Z natively; do not intercept.
    return false
  }

  override fun dispatchNativeTextEditingCommand(command: String): Boolean = false
}
```

- [ ] **Step 3: Compile**

Run: `./gradlew compileKotlin 2>&1 | grep -E "^e:|BUILD" | tail -20`
Expected: `BUILD SUCCESSFUL`. If any peer method signature differs (e.g. `updateOverlayClipRects` lacks the default `awaitNativeCommit` param), adapt to the actual interface and re-run.

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/io/github/barsia/speqa/webview/internal/linux/LinuxJcefWebViewHostPeer.kt
git commit -m "linux: add JCEF host peer (Swing layout + no-op overlay clip)"
```

---

### Task 6: Route Linux through JCEF in the factory and peer factory

Switch `WebViewFacadeFactory.createLinuxFacade*` to JCEF, add the `JBCefApp.isSupported()` guard, and update `NativeWebViewHostPeer.create()` so the Linux branch builds `LinuxJcefWebViewHostPeer`.

**Files:**
- Modify: `src/main/kotlin/io/github/barsia/speqa/webview/WebViewFacadeFactory.kt`
- Modify: `src/main/kotlin/io/github/barsia/speqa/webview/internal/host/NativeWebViewHostPeer.kt`

- [ ] **Step 1: Replace the Linux factory methods**

In `src/main/kotlin/io/github/barsia/speqa/webview/WebViewFacadeFactory.kt`:

Delete these imports:
```kotlin
import io.github.barsia.speqa.webview.internal.linux.LinuxWaylandWindowUtil
import io.github.barsia.speqa.webview.internal.linux.LinuxWebKitBackend
import io.github.barsia.speqa.webview.internal.linux.LinuxX11WindowUtil
import io.github.barsia.speqa.webview.internal.linux.createLinuxWebKitWebViewFacade
```

Add these imports:
```kotlin
import com.intellij.ui.jcef.JBCefApp
import io.github.barsia.speqa.SpeqaBundle
import io.github.barsia.speqa.webview.internal.linux.createJcefWebViewFacade
```

Replace `createLinuxFacade`, `createLinuxFacadeWithBus`, and the private `linuxBackend()` with:

```kotlin
/**
 * Creates a Linux [WebViewFacade] backed by the bundled JCEF (Chromium) runtime.
 *
 * Throws [IllegalStateException] with a localized message if JCEF is not available
 * on the current JetBrains Runtime; the caller is expected to surface it through the
 * preview's unsupported-fallback panel.
 */
@JvmStatic
fun createLinuxFacade(scope: CoroutineScope, onMessage: (String) -> Unit = {}): WebViewFacade {
  check(SystemInfo.isLinux) { "System WebView is supported only on Linux" }
  check(JBCefApp.isSupported()) { SpeqaBundle.message("webview.unsupported.jcef") }
  val facade = createJcefWebViewFacade(scope)
  facade.initialize(onMessage)
  return facade
}

/**
 * Creates a Linux [WebViewFacade] together with a [WebViewMessageBus] wired over JCEF IPC.
 */
@JvmStatic
fun createLinuxFacadeWithBus(scope: CoroutineScope): WebViewFacadeWithBus {
  check(SystemInfo.isLinux) { "System WebView is supported only on Linux" }
  check(JBCefApp.isSupported()) { SpeqaBundle.message("webview.unsupported.jcef") }
  val facade = createJcefWebViewFacade(scope)
  val bus = WebViewMessageBus(outgoingSink = { raw -> facade.deliverJsonToJavaScript(raw) })
  facade.initialize(onMessage = bus::onIncomingMessage)
  return WebViewFacadeWithBus(facade, bus)
}
```

The `linuxBackend()` private function is gone entirely. Delete its source.

- [ ] **Step 2: Update `NativeWebViewHostPeer.create()` for Linux**

Open `src/main/kotlin/io/github/barsia/speqa/webview/internal/host/NativeWebViewHostPeer.kt` and find the Linux branch of `create()`. Replace whatever it currently returns (will be `LinuxWaylandSnapshotWebViewHostPeer(...)` or similar) with:

```kotlin
SystemInfo.isLinux -> {
  val jcefFacade = facade as? io.github.barsia.speqa.webview.internal.linux.JcefWebViewFacade
    ?: error("Linux peer requires a JcefWebViewFacade; got " + facade.javaClass.name)
  io.github.barsia.speqa.webview.internal.linux.LinuxJcefWebViewHostPeer(jcefFacade)
}
```

Delete any imports of `LinuxWaylandSnapshotWebViewHostPeer`, `LinuxX11NativeWebViewHostPeer`, `LinuxNativeWebViewHostPeer`, `LinuxWebKitBackend` from this file.

- [ ] **Step 3: Compile**

Run: `./gradlew compileKotlin 2>&1 | grep -E "^e:|BUILD" | tail -20`
Expected: `BUILD SUCCESSFUL`. Compilation will likely flag broken references in the about-to-be-deleted files (`LinuxWebKitWebViewFacade.kt` etc.). They are removed in Task 8: either jump ahead to Task 8 and come back, or temporarily comment out their cross-references to get the build green.

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/io/github/barsia/speqa/webview/WebViewFacadeFactory.kt \
        src/main/kotlin/io/github/barsia/speqa/webview/internal/host/NativeWebViewHostPeer.kt
git commit -m "linux: route WebViewFacadeFactory + host peer through JCEF"
```

---

### Task 7: Refresh the contract test

The existing `WebViewNativeHostContractTest` bans JCEF outright (`assertTrue(!webviewSources.contains("JBCef"))`). That ban is now wrong. Replace the Linux assertions with new ones that pin the JCEF wiring.

**Files:**
- Modify: `src/test/kotlin/io/github/barsia/speqa/webview/WebViewNativeHostContractTest.kt`

- [ ] **Step 1: Update `WebViewNativeHostContractTest`**

Make these edits in `src/test/kotlin/io/github/barsia/speqa/webview/WebViewNativeHostContractTest.kt`:

1. Remove the `assertTrue(!webviewSources.contains("JBCef"))` line from the first test (`factory keeps system webview backends explicit and does not route through JBCef`). Rename that test to `factory exposes per-platform with-bus constructors and SpeqaWebViewPreviewPanel routes by SystemInfo`.
2. Delete the entire test method `linux wayland host uses offscreen snapshots and clears the handler on detach`.
3. Add a new test method **after** the windows test:

```kotlin
@Test
fun `linux host uses JCEF browser embedded as a Swing child of the host panel`() {
  val peer = source("src/main/kotlin/io/github/barsia/speqa/webview/internal/linux/LinuxJcefWebViewHostPeer.kt")
  val facade = source("src/main/kotlin/io/github/barsia/speqa/webview/internal/linux/JcefWebViewFacade.kt")
  val factory = source("src/main/kotlin/io/github/barsia/speqa/webview/WebViewFacadeFactory.kt")

  assertTrue(facade.contains("import com.intellij.ui.jcef.JBCefBrowser"))
  assertTrue(facade.contains("import com.intellij.ui.jcef.JBCefJSQuery"))
  assertTrue(facade.contains("window.__KWRY__"))
  assertTrue(facade.contains("__eval__:"))
  assertTrue(peer.contains("host.add(component, BorderLayout.CENTER)"))
  assertTrue(peer.contains("host.remove(component)"))
  assertTrue(factory.contains("JBCefApp.isSupported()"))
  assertTrue(factory.contains("createJcefWebViewFacade(scope)"))
}
```

- [ ] **Step 2: Run the contract test**

Run: `./gradlew test --tests "io.github.barsia.speqa.webview.WebViewNativeHostContractTest" 2>&1 | tail -15`
Expected: PASS (all surviving assertions hold against the files written in Tasks 3-6).

- [ ] **Step 3: Commit**

```bash
git add src/test/kotlin/io/github/barsia/speqa/webview/WebViewNativeHostContractTest.kt
git commit -m "test: pin Linux WebView contract to JCEF instead of WebKitGTK"
```

---

### Task 8: Delete the WebKitGTK backend

Now that JCEF is wired and tests pass, remove every WebKitGTK file, the Rust crate, the Gradle native build tasks, and the obsolete unit tests.

**Files (deletions):** see the "Deleted" list under File Structure.

- [ ] **Step 1: Delete the Linux WebKitGTK Kotlin sources**

```bash
rm src/main/kotlin/io/github/barsia/speqa/webview/internal/linux/LinuxWebKitWebViewFacade.kt
rm src/main/kotlin/io/github/barsia/speqa/webview/internal/linux/LinuxWebKitGtkBridge.kt
rm src/main/kotlin/io/github/barsia/speqa/webview/internal/linux/LinuxWebKitGtkRuntime.kt
rm src/main/kotlin/io/github/barsia/speqa/webview/internal/linux/LinuxWebKitGtkRuntimeProbe.kt
rm src/main/kotlin/io/github/barsia/speqa/webview/internal/linux/LinuxWebKitBackend.kt
rm src/main/kotlin/io/github/barsia/speqa/webview/internal/linux/LinuxX11WindowUtil.kt
rm src/main/kotlin/io/github/barsia/speqa/webview/internal/linux/LinuxWaylandWindowUtil.kt
rm src/main/kotlin/io/github/barsia/speqa/webview/internal/linux/LinuxWaylandSnapshotWebViewHostPeer.kt
rm src/main/kotlin/io/github/barsia/speqa/webview/internal/linux/LinuxX11NativeWebViewHostPeer.kt
rm src/main/kotlin/io/github/barsia/speqa/webview/internal/linux/LinuxNativeWebViewHostPeer.kt
```

- [ ] **Step 2: Delete the obsolete Linux unit tests**

```bash
rm src/test/kotlin/io/github/barsia/speqa/webview/LinuxWebKitGtkRuntimeSelectionTest.kt
rm src/test/kotlin/io/github/barsia/speqa/webview/LinuxWebKitGtkBridgeBundledLookupTest.kt
```

- [ ] **Step 3: Delete the Rust native bridge crate**

```bash
rm -rf native/LinuxWebKitGtkBridge/
```

- [ ] **Step 4: Strip the native build wiring from `build.gradle.kts`**

Open `build.gradle.kts` and remove every block referencing `LinuxWebKitGtkBridge`. Specifically:
- The two `register("buildLinuxWebKitGtkBridge41" / "buildLinuxWebKitGtkBridge40")` Gradle task definitions.
- Any `processResources` configuration that copies `target-wk41/release/*.so` or `target-wk40/release/*.so` into the plugin jar.
- The `dependsOn(buildLinuxWebKitGtkBridge41, buildLinuxWebKitGtkBridge40)` wiring on whichever task currently depends on them (likely `processResources` or `prepareSandbox`).

Grep to find every site:

```bash
grep -n "LinuxWebKitGtkBridge\|wk41\|wk40\|libwebkit2gtk" build.gradle.kts
```

Delete each match (or the enclosing block) so no references survive.

- [ ] **Step 5: Strip the CI Linux native bridge build**

Open `.github/workflows/build-plugin.yml` and remove every step that builds the Rust crate, runs `cargo-zigbuild`, or asserts glibc floors via `objdump`. Grep:

```bash
grep -n "LinuxWebKitGtkBridge\|cargo-zigbuild\|wk41\|wk40\|libwebkit2gtk\|objdump" .github/workflows/build-plugin.yml
```

Delete those steps. The remaining workflow should build the JVM plugin and produce the ZIP exactly as before, minus the Linux native step.

- [ ] **Step 6: Verify nothing else references the deleted code**

```bash
grep -rn "LinuxWebKitGtkBridge\|LinuxWebKitWebViewFacade\|LinuxWebKitBackend\|LinuxWaylandSnapshotWebViewHostPeer\|LinuxX11NativeWebViewHostPeer\|LinuxNativeWebViewHostPeer\|LinuxWebKitGtkRuntime\|LinuxX11WindowUtil\|LinuxWaylandWindowUtil" src/ build.gradle.kts .github/ 2>/dev/null
```

Expected output: empty. If any line shows up, fix that file (delete the dead reference) before continuing.

- [ ] **Step 7: Compile**

Run: `./gradlew compileKotlin 2>&1 | grep -E "^e:|BUILD" | tail -20`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 8: Run all tests**

Run: `./gradlew test 2>&1 | grep -E "FAILED|BUILD|Tests " | tail -20`
Expected: `BUILD SUCCESSFUL`, no `FAILED` lines.

- [ ] **Step 9: Commit**

```bash
git add -A src/ native/ build.gradle.kts .github/
git commit -m "linux: remove WebKitGTK backend, native Rust bridge, and CI native build"
```

---

### Task 9: Sandbox smoke test (manual)

JCEF cannot be unit-tested without a real IDE. This is the verification step the spec requires.

- [ ] **Step 1: Launch the sandbox IDE**

Run: `./gradlew runIde 2>&1 | tail -5 &`

Wait for the sandbox IDE window to appear.

- [ ] **Step 2: Open a `.speqa` test case file**

In the sandbox IDE, open any `.speqa` file (or create a new one). The SpeQA preview tab should appear next to the editor.

- [ ] **Step 3: Verify the preview is interactive**

In the preview pane, confirm each of these works:
- Click into a text field (e.g. the title). A native Chromium text caret appears.
- Type characters. They appear in the field; the underlying file updates.
- Select text with click-drag.
- Right-click. The Chromium context menu appears.
- Scroll with the mouse wheel; the preview scrolls.
- Click a step's chip / link / attachment row. The corresponding action fires (link opens browser, etc.).

If any of these fails, do not proceed. File the failure mode in the spec under a new "Known issues" section and either fix it before merge or open a follow-up plan.

- [ ] **Step 4: Verify the unsupported-fallback path**

This is harder to trigger automatically. If you are running on a JBR without JCEF (rare on a normal dev machine), the preview should show the localized "SpeQA preview requires JCEF..." message instead of crashing. If you cannot reproduce this, document the manual test path in the spec and move on.

- [ ] **Step 5: Stop the sandbox IDE; this task has no commit**

Close the sandbox IDE window. There is nothing to commit for this task.

---

## Self-Review (run before opening PR)

Re-read the plan against the spec. Confirm:

- **Spec coverage:** the spec describes JCEF, the IPC contract (`webviewIpc` + `__KWRY__.__deliver`), the lifecycle, and the non-goals (no overlay clipping, no text command dispatch). All four are implemented or explicitly skipped in the tasks.
- **No placeholders:** every `Step N` shows the exact code, command, or precise reference to mirror.
- **Type consistency:** `JcefWebViewFacade` is the type name used in factory (`createJcefWebViewFacade`), peer (`facade as? JcefWebViewFacade`), and contract test. `JcefBootstrapScript.build(queryInjection: String)` is referenced identically in test and call site. `LinuxJcefWebViewHostPeer(jcefFacade)` constructor signature matches its `attach` / `detach` use.
- **Project rules:**
  - Spec is updated first (Task 1) so the PreToolUse hook does not block `.kt` edits.
  - No "Generated with Claude Code" or "Co-Authored-By" tags in commit messages.
  - User-visible string in `SpeqaBundle.properties`, not hardcoded.
  - No destructive git commands (`reset`, `checkout --`, `stash`).
  - No new non-English comments.

If any of the above fails, fix the plan inline before handing off to execution.
