// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package io.github.barsia.speqa.webview.internal.linux

import io.github.barsia.speqa.webview.SwingWebViewHostPanel
import io.github.barsia.speqa.webview.internal.host.NativeWebViewHostPeer
import java.awt.Component
import javax.swing.SwingUtilities

internal class LinuxX11NativeWebViewHostPeer(
  private val facade: LinuxWebKitWebViewFacade,
) : NativeWebViewHostPeer {

  private val debug = com.intellij.openapi.diagnostic.Logger.getInstance("SpeqaDebug")

  private var attached = false
  private var lastAppliedFrame: AppliedFrame? = null

  override fun attach(host: Component): Boolean {
    val parentXid = LinuxX11WindowUtil.resolveWindowXid(host)
    debug.warn("x11-peer attach: host=${host.javaClass.simpleName} hostBounds=${host.bounds} isShowing=${host.isShowing} parentXid=$parentXid")
    if (parentXid == null) return false
    facade.attachToX11Parent(parentXid)
    attached = true
    lastAppliedFrame = null

    scheduleFrameUpdate(host)
    facade.setHidden(!host.isShowing)
    debug.warn("x11-peer attach: setHidden(${!host.isShowing}) initial")
    SwingUtilities.invokeLater { scheduleFrameUpdate(host) }
    return true
  }

  override fun detach() {
    if (!attached) return
    debug.warn("x11-peer detach")
    facade.detach()
    attached = false
    lastAppliedFrame = null
  }

  override fun scheduleFrameUpdate(host: Component) {
    if (!attached) return
    val anchor = SwingWebViewHostPanel.resolveWindowsAnchor(host)
    if (anchor == null) {
      debug.warn("x11-peer scheduleFrameUpdate: no anchor — skipping")
      return
    }
    val bounds = SwingWebViewHostPanel.calculateWindowsBounds(host, anchor)
    val scale = LinuxX11WindowUtil.scale(host)
    val frame = AppliedFrame(bounds, scale)
    if (frame == lastAppliedFrame) return
    lastAppliedFrame = frame
    debug.warn("x11-peer setBounds: x=${bounds.x} y=${bounds.y} w=${bounds.width} h=${bounds.height} scale=$scale host=${host.bounds} anchor=${anchor.javaClass.simpleName}/${anchor.bounds}")
    facade.setBounds(bounds.x, bounds.y, bounds.width, bounds.height, scale)
  }

  override fun updateVisibility(host: Component, hidden: Boolean) {
    if (!attached) return
    debug.warn("x11-peer updateVisibility: hidden=$hidden")
    facade.setHidden(hidden)
  }

  override fun requestFocus() {
    if (!attached) return
    debug.warn("x11-peer requestFocus")
    facade.requestFocus()
  }

  override fun clearFocus() {
    if (!attached) return
    debug.warn("x11-peer clearFocus")
    facade.clearFocus()
  }

  private data class AppliedFrame(
    val bounds: SwingWebViewHostPanel.NativeBounds,
    val scale: Double,
  )
}
