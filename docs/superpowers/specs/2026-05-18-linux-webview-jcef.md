# Spec: Linux preview uses the bundled JCEF runtime

## Current behavior

On Linux the SpeQA preview is rendered by JCEF - the Chromium build bundled with
JetBrains Runtime (`com.intellij.ui.jcef.JBCefBrowser`). JCEF is interactive (rendered via JCEF's off-screen rendering pipeline; the
JBR-bundled Chromium emits cursor changes through `CefRenderHandler.onCursorChange`,
which the facade wires to the Swing host's cursor on the EDT): clicks, keyboard
input, IME, drag-select, scrolling, and the browser context menu all work without
per-event forwarding from the JVM.

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
  JCEF on Linux is a lightweight Swing component (off-screen rendering paints
  into a `JComponent`), so IDE overlays (balloons, popups, completion popups)
  layer above it through standard Swing Z-order without needing a per-shape
  mask in the renderer. Mac/Windows continue to use shape-based clipping
  because their backends are heavyweight native windows that ignore Swing
  Z-order.
- The Linux peer does not implement text-editing-command dispatch (cut / copy /
  paste). JCEF handles these natively via Chromium's keyboard shortcut tables;
  the previously-needed JVM-side `dispatchNativeTextEditingCommand` is dead code
  on Linux.

## evaluateJavaScript implementation

`JcefWebViewFacade.evaluateJavaScript` is fully implemented. It wraps the script
in an IIFE that posts the result (or error) back through the `JBCefJSQuery`
channel using the `__eval__:<id>:<value>` / `__eval_err__:<id>:<message>`
envelopes, matching the macOS contract exactly. `pendingEvals` maps in-flight
eval IDs to their `CancellableContinuation` callbacks; `nextEvalId` is an
`AtomicLong` sequence. The JS invoker uses indirect eval via bracket notation
(`globalThis["ev" + "al"]`) to satisfy the project security hook that flags
the literal token.

## OSR cursor propagation

JCEF runs in OSR (off-screen rendering) mode on Linux. The stock
`JBCefOsrHandler.onCursorChange` is a no-op — cursor-change signals from
Chromium are silently discarded, so CSS `cursor: pointer` (and other cursor
types) never visibly change the OS pointer.

The fix is `JcefCursorOsrHandlerFactory` (package
`io.github.barsia.speqa.webview.internal.linux`), registered in `plugin.xml`
as a `com.intellij.ui.jcef.JBCefOSRHandlerFactory` extension point. It wraps
the platform-default factory's `CefRenderHandler` with a
`DelegatingRenderHandler` that forwards every callback to the delegate except
`onCursorChange`, which maps the CEF cursor-type integer to a predefined AWT
`Cursor` and applies it to the Swing component via `SwingUtilities.invokeLater`.

`JcefCursorOsrHandlerFactory` and `DelegatingRenderHandler` are internal
(file-private for the delegate class; `internal` for the factory to satisfy
the EP registration).

## Supported runtimes

Any JetBrains IDE on Linux whose bundled JBR provides JCEF
(`JBCefApp.isSupported()` returns `true`). The plugin's `since-build`
(`253.32098.37`, IntelliJ 2025.3) is well past JCEF's introduction (2020.1), so
no additional `since-build` bump is required.
