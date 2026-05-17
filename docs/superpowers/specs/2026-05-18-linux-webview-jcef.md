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
