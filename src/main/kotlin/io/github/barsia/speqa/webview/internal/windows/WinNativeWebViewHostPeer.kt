// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package io.github.barsia.speqa.webview.internal.windows

import io.github.barsia.speqa.webview.SwingWebViewHostPanel
import io.github.barsia.speqa.webview.internal.host.NativeWebViewHostPeer
import org.jetbrains.annotations.ApiStatus
import java.awt.Component
import javax.swing.SwingUtilities

@ApiStatus.Internal
internal class WinNativeWebViewHostPeer(
  private val facade: WinWebViewFacade,
) : NativeWebViewHostPeer {

  private var attached = false
  private var lastAppliedFrame: AppliedFrame? = null

  override fun attach(host: Component): Boolean {
    val parentHwnd = WindowsHwndUtil.resolveWindowHwnd(host) ?: return false
    facade.setShortcutTarget(host)
    facade.attachToParent(parentHwnd)
    attached = true
    lastAppliedFrame = null

    scheduleFrameUpdate(host)
    facade.setHidden(!host.isShowing)
    SwingUtilities.invokeLater { scheduleFrameUpdate(host) }
    return true
  }

  override fun detach() {
    if (!attached) return
    facade.detachFromParent()
    facade.setShortcutTarget(null)
    attached = false
    lastAppliedFrame = null
  }

  override fun scheduleFrameUpdate(host: Component) {
    if (!attached) return
    val anchor = SwingWebViewHostPanel.resolveWindowsAnchor(host) ?: return
    val bounds = SwingWebViewHostPanel.calculateWindowsBounds(host, anchor)
    val scale = WindowsHwndUtil.scale(host)
    val frame = AppliedFrame(bounds, scale)
    if (frame == lastAppliedFrame) return
    lastAppliedFrame = frame
    facade.setBounds(bounds.x, bounds.y, bounds.width, bounds.height, scale)
  }

  override fun updateVisibility(host: Component, hidden: Boolean) {
    if (!attached) return
    facade.setHidden(hidden)
  }

  override fun requestFocus() {
    if (!attached) return
    facade.requestFocus()
  }

  override fun clearFocus() {
    if (!attached) return
    facade.clearFocus()
  }

  private data class AppliedFrame(
    val bounds: SwingWebViewHostPanel.NativeBounds,
    val scale: Double,
  )
}
