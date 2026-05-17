// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package io.github.barsia.speqa.webview.internal.linux

import com.intellij.openapi.util.SystemInfoRt
import io.github.barsia.speqa.webview.internal.WebViewLogger
import com.intellij.util.ui.StartupUiUtil
import kotlinx.coroutines.CancellationException
import org.jetbrains.annotations.ApiStatus
import java.awt.Component
import java.awt.Window
import javax.swing.SwingUtilities

@ApiStatus.Internal
internal object LinuxX11WindowUtil {
  fun isSupportedToolkit(): Boolean = SystemInfoRt.isLinux && StartupUiUtil.isXToolkit()

  fun resolveWindowXid(component: Component): Long? {
    if (!isSupportedToolkit()) return null
    val window = SwingUtilities.getWindowAncestor(component) ?: return null
    val peer = peerOf(window) ?: return null
    // Prefer the AWT content window: it is the X11 window where Swing actually renders.
    // Reparenting under the WM-managed top-level window puts our embedded child as a sibling
    // of the content window, which then obscures it on every repaint. As a child of the
    // content window the embedded WebView always paints above its (parent) Swing surface.
    return contentXWindow(peer) ?: topLevelXWindow(peer)
  }

  fun scale(component: Component): Double {
    return component.graphicsConfiguration?.defaultTransform?.scaleX?.takeIf { it > 0.0 } ?: 1.0
  }

  private fun peerOf(window: Window): Any? {
    return try {
      val peerField = Component::class.java.getDeclaredField("peer")
      peerField.isAccessible = true
      peerField.get(window)
    }
    catch (e: Exception) {
      if (e is CancellationException) throw e
      WebViewLogger.LOG.warn("Failed to access AWT peer for Linux X11 WebView host", e)
      null
    }
  }

  private fun topLevelXWindow(peer: Any): Long? {
    return try {
      val getWindow = Class.forName("sun.awt.X11.XBaseWindow").getDeclaredMethod("getWindow")
      getWindow.isAccessible = true
      getWindow.invoke(peer) as? Long
    }
    catch (e: Exception) {
      if (e is CancellationException) throw e
      WebViewLogger.LOG.warn("Failed to resolve Linux X11 top-level window id for WebView host", e)
      null
    }
  }

  private fun contentXWindow(peer: Any): Long? {
    return try {
      // sun.awt.X11.XDecoratedPeer (parent of XFramePeer / XDialogPeer) holds the
      // content XContentWindow in a 'content' field; XContentWindow extends XBaseWindow.
      var contentField: java.lang.reflect.Field? = null
      var cls: Class<*>? = peer.javaClass
      while (cls != null) {
        try {
          contentField = cls.getDeclaredField("content")
          break
        }
        catch (_: NoSuchFieldException) {
          cls = cls.superclass
        }
      }
      val field = contentField ?: return null
      field.isAccessible = true
      val contentWindow = field.get(peer) ?: return null
      val getWindow = Class.forName("sun.awt.X11.XBaseWindow").getDeclaredMethod("getWindow")
      getWindow.isAccessible = true
      getWindow.invoke(contentWindow) as? Long
    }
    catch (e: Exception) {
      if (e is CancellationException) throw e
      WebViewLogger.LOG.warn("Failed to resolve Linux X11 content window id, falling back to top-level", e)
      null
    }
  }
}
