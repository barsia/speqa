// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package io.github.barsia.speqa.webview

import com.intellij.openapi.util.SystemInfo
import io.github.barsia.speqa.webview.internal.linux.LinuxWaylandWindowUtil
import io.github.barsia.speqa.webview.internal.linux.LinuxWebKitBackend
import io.github.barsia.speqa.webview.internal.linux.LinuxX11WindowUtil
import io.github.barsia.speqa.webview.internal.linux.createLinuxWebKitWebViewFacade
import io.github.barsia.speqa.webview.internal.mac.createMacWebViewFacade
import io.github.barsia.speqa.webview.internal.windows.createWinWebViewFacade
import io.github.barsia.speqa.webview.interop.WebViewMessageBus
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus

/**
 * Factory for creating platform-specific [WebViewFacade] instances.
 */
@ApiStatus.Experimental
object WebViewFacadeFactory {
  /**
   * Creates a macOS [WebViewFacade] backed by WKWebView.
   *
   * @param onMessage callback invoked on the macOS main thread for JS -> JVM messages
   */
  @JvmStatic
  fun createMacOsFacade(scope: CoroutineScope, onMessage: (String) -> Unit = {}): WebViewFacade {
    check(SystemInfo.isMac) { "System WebView is supported only on macOS" }
    val facade = createMacWebViewFacade(scope)
    facade.initialize(onMessage)
    return facade
  }

  /**
   * Creates a macOS [WebViewFacade] together with a [WebViewMessageBus] wired over the bridge.
   *
   * The bus serializes outgoing Kotlin → JS frames via the facade's internal
   * `deliverJsonToJavaScript` sink (routed to `window.__KWRY__.__deliver` in the JS bootstrap)
   * and consumes incoming JS → Kotlin frames from the facade's message handler. See
   * [WebViewMessageBus] for the wire contract and RPC scope.
   */
  @JvmStatic
  fun createMacOsFacadeWithBus(scope: CoroutineScope): WebViewFacadeWithBus {
    check(SystemInfo.isMac) { "System WebView is supported only on macOS" }
    val facade = createMacWebViewFacade(scope)
    val bus = WebViewMessageBus(outgoingSink = { raw -> facade.deliverJsonToJavaScript(raw) })
    facade.initialize(onMessage = bus::onIncomingMessage)
    return WebViewFacadeWithBus(facade, bus)
  }

  /**
   * Creates a Windows [WebViewFacade] backed by WebView2.
   *
   * @param onMessage callback invoked on the WebView2 UI thread for JS -> JVM messages
   */
  @JvmStatic
  fun createWindowsFacade(scope: CoroutineScope, onMessage: (String) -> Unit = {}): WebViewFacade {
    check(SystemInfo.isWindows) { "System WebView is supported only on Windows" }
    val facade = createWinWebViewFacade(scope)
    facade.initialize(onMessage)
    return facade
  }

  /**
   * Creates a Windows [WebViewFacade] together with a [WebViewMessageBus] wired over WebView2 IPC.
   */
  @JvmStatic
  fun createWindowsFacadeWithBus(scope: CoroutineScope): WebViewFacadeWithBus {
    check(SystemInfo.isWindows) { "System WebView is supported only on Windows" }
    val facade = createWinWebViewFacade(scope)
    val bus = WebViewMessageBus(outgoingSink = { raw -> facade.deliverJsonToJavaScript(raw) })
    facade.initialize(onMessage = bus::onIncomingMessage)
    return WebViewFacadeWithBus(facade, bus)
  }

  /**
   * Creates a Linux [WebViewFacade] backed by WebKitGTK.
   *
   * Local Wayland/WLToolkit sessions use an offscreen WebKitGTK renderer with Swing snapshots
   * until a JBR child-surface API is available.
   */
  @JvmStatic
  fun createLinuxFacade(scope: CoroutineScope, onMessage: (String) -> Unit = {}): WebViewFacade {
    val facade = createLinuxWebKitWebViewFacade(scope, linuxBackend())
    facade.initialize(onMessage)
    return facade
  }

  /**
   * Creates a Linux [WebViewFacade] together with a [WebViewMessageBus] wired over WebKitGTK IPC.
   */
  @JvmStatic
  fun createLinuxFacadeWithBus(scope: CoroutineScope): WebViewFacadeWithBus {
    val facade = createLinuxWebKitWebViewFacade(scope, linuxBackend())
    val bus = WebViewMessageBus(outgoingSink = { raw -> facade.deliverJsonToJavaScript(raw) })
    facade.initialize(onMessage = bus::onIncomingMessage)
    return WebViewFacadeWithBus(facade, bus)
  }

  private fun linuxBackend(): LinuxWebKitBackend {
    check(SystemInfo.isLinux) { "System WebView is supported only on Linux" }
    val backend = when {
      LinuxWaylandWindowUtil.isSupportedToolkit() -> LinuxWebKitBackend.WaylandSnapshot
      LinuxX11WindowUtil.isSupportedToolkit() -> LinuxWebKitBackend.X11
      else -> error("Linux System WebView is supported only with X11 or Wayland/WLToolkit")
    }
    com.intellij.openapi.diagnostic.Logger.getInstance("SpeqaDebug").warn(
      "linux backend selected: $backend (wayland-toolkit=${LinuxWaylandWindowUtil.isSupportedToolkit()}, x11-toolkit=${LinuxX11WindowUtil.isSupportedToolkit()})"
    )
    return backend
  }
}

/**
 * Result of a platform-specific factory method — a paired facade + interop bus.
 */
@ApiStatus.Experimental
data class WebViewFacadeWithBus(
  val facade: WebViewFacade,
  val bus: WebViewMessageBus,
)
