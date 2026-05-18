package io.github.barsia.speqa.webview

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class WebViewNativeHostContractTest {
  @Test
  fun `factory exposes per-platform with-bus constructors and SpeqaWebViewPreviewPanel routes by SystemInfo`() {
    val factory = source("src/main/kotlin/io/github/barsia/speqa/webview/WebViewFacadeFactory.kt")
    val previewPanel = source("src/main/kotlin/io/github/barsia/speqa/editor/webview/SpeqaWebViewPreviewPanel.kt")

    assertTrue(previewPanel.contains("SystemInfo.isMac -> WebViewFacadeFactory.createMacOsFacadeWithBus(scope)"))
    assertTrue(previewPanel.contains("SystemInfo.isWindows -> WebViewFacadeFactory.createWindowsFacadeWithBus(scope)"))
    assertTrue(previewPanel.contains("SystemInfo.isLinux -> WebViewFacadeFactory.createLinuxFacadeWithBus(scope)"))
    assertTrue(factory.contains("fun createMacOsFacadeWithBus(scope: CoroutineScope): WebViewFacadeWithBus"))
    assertTrue(factory.contains("fun createWindowsFacadeWithBus(scope: CoroutineScope): WebViewFacadeWithBus"))
    assertTrue(factory.contains("fun createLinuxFacadeWithBus(scope: CoroutineScope): WebViewFacadeWithBus"))
  }

  @Test
  fun `mac host attaches WKWebView to the window content view and waits for a valid frame before showing it`() {
    val source = source("src/main/kotlin/io/github/barsia/speqa/webview/internal/mac/MacNativeWebViewHostPeer.kt")

    assertTrue(source.contains("val contentView = Foundation.invoke(nsWindow, \"contentView\")"))
    assertTrue(source.contains("facade.attachToParent(contentView)"))
    assertTrue(source.contains("facade.setHidden(true)"))
    assertTrue(source.contains("SwingUtilities.invokeLater {\n        hostHidden = !host.isShowing\n        scheduleFrameUpdate(host)\n      }"))
    assertTrue(source.contains("facade.setFrame(frame.x, frame.y, frame.width, frame.height)"))
    assertTrue(source.contains("facade.setHidden(hostHidden || !positiveFrameApplied || frameTemporarilyInvalid)"))
    assertTrue(source.contains("facade.setOverlayClipShapes(0, 0, emptyList())"))
  }

  @Test
  fun `mac overlay clipping is guarded by policy diagnostics and native fallback`() {
    val bridge = source("src/main/kotlin/io/github/barsia/speqa/webview/internal/mac/WKWebViewBridge.kt")
    val host = source("src/main/kotlin/io/github/barsia/speqa/webview/SwingWebViewHostPanel.kt")
    val policy = source("src/main/kotlin/io/github/barsia/speqa/webview/internal/host/NativeOverlayClippingPolicy.kt")

    assertTrue(policy.contains("speqa.webview.overlay.clipping"))
    assertTrue(policy.contains("speqa.webview.overlay.clipping.diagnostics"))
    assertTrue(host.contains("NativeOverlayClippingPolicy.logShapes(\"swing\""))
    assertTrue(bridge.contains("NativeOverlayClippingPolicy.disableForSession(\"failed to apply AppKit mask\""))
    assertTrue(bridge.contains("NativeOverlayClippingPolicy.disableForSession(\"native hitTest callback failed\""))
    assertTrue(bridge.contains("return try {\n          hitTestMaskableContainer(self, point)\n        }"))
  }

  @Test
  fun `windows host attaches WebView2 to the IDE HWND and applies clipped scaled bounds`() {
    val source = source("src/main/kotlin/io/github/barsia/speqa/webview/internal/windows/WinNativeWebViewHostPeer.kt")

    assertTrue(source.contains("WindowsHwndUtil.resolveWindowHwnd(host)"))
    assertTrue(source.contains("facade.setShortcutTarget(host)"))
    assertTrue(source.contains("facade.attachToParent(parentHwnd)"))
    assertTrue(source.contains("SwingWebViewHostPanel.calculateWindowsBounds(host, anchor)"))
    assertTrue(source.contains("WindowsHwndUtil.scale(host)"))
    assertTrue(source.contains("facade.setBounds(bounds.x, bounds.y, bounds.width, bounds.height, scale)"))
    assertTrue(source.contains("SwingUtilities.invokeLater { scheduleFrameUpdate(host) }"))
  }

  @Test
  fun `linux host uses JCEF browser embedded as a Swing child of the host panel`() {
    val peer = source("src/main/kotlin/io/github/barsia/speqa/webview/internal/linux/LinuxJcefWebViewHostPeer.kt")
    val facade = source("src/main/kotlin/io/github/barsia/speqa/webview/internal/linux/JcefWebViewFacade.kt")
    val osrFactory = source("src/main/kotlin/io/github/barsia/speqa/webview/internal/linux/JcefCursorOsrHandlerFactory.kt")
    val factory = source("src/main/kotlin/io/github/barsia/speqa/webview/WebViewFacadeFactory.kt")

    assertTrue(facade.contains("import com.intellij.ui.jcef.JBCefBrowser"))
    assertTrue(facade.contains("import com.intellij.ui.jcef.JBCefJSQuery"))
    assertTrue(facade.contains("window.__KWRY__"))
    assertTrue(facade.contains("__eval__:"))
    assertTrue(facade.contains("setOSRHandlerFactory(JcefCursorOsrHandlerFactory())"))
    assertTrue(osrFactory.contains("onCursorChange"))
    assertTrue(peer.contains("container.add(component, BorderLayout.CENTER)"))
    assertTrue(peer.contains("container.remove(component)"))
    assertTrue(factory.contains("JBCefApp.isSupported()"))
    assertTrue(factory.contains("createJcefWebViewFacade(scope)"))
  }

  @Test
  fun `swing host clears native focus when the user clicks elsewhere in the same IDE window`() {
    val source = source("src/main/kotlin/io/github/barsia/speqa/webview/SwingWebViewHostPanel.kt")

    assertTrue(source.contains("Toolkit.getDefaultToolkit().addAWTEventListener(focusListener, AWTEvent.MOUSE_EVENT_MASK)"))
    assertTrue(source.contains("Toolkit.getDefaultToolkit().removeAWTEventListener(it)"))
    assertTrue(source.contains("if (SwingUtilities.getWindowAncestor(source) != hostWindow) return@AWTEventListener"))
    assertTrue(source.contains("if (source === this || SwingUtilities.isDescendingFrom(source, this)) return@AWTEventListener"))
    assertTrue(source.contains("nativePeer?.clearFocus()"))
  }

  @Test
  fun `swing host refreshes native overlay clipping when IDE overlay components are added or removed`() {
    val source = source("src/main/kotlin/io/github/barsia/speqa/webview/SwingWebViewHostPanel.kt")

    assertTrue(source.contains("import java.awt.event.ContainerEvent"))
    assertTrue(source.contains("ContainerEvent.COMPONENT_ADDED"))
    assertTrue(source.contains("ContainerEvent.COMPONENT_REMOVED"))
    assertTrue(source.contains("AWTEvent.COMPONENT_EVENT_MASK or AWTEvent.CONTAINER_EVENT_MASK"))
  }

  private fun source(path: String): String =
    File(System.getProperty("user.dir"), path).readText()
}
