// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package io.github.barsia.speqa.webview.internal.linux

import io.github.barsia.speqa.webview.SwingWebViewHostPanel
import io.github.barsia.speqa.webview.internal.host.NativeWebViewHostPeer
import java.awt.Component
import javax.swing.SwingUtilities

internal class LinuxWaylandSnapshotWebViewHostPeer(
  private val facade: LinuxWebKitWebViewFacade,
) : NativeWebViewHostPeer {

  private var attached = false
  private var lastAppliedFrame: AppliedFrame? = null
  private var snapshotHost: SwingWebViewHostPanel? = null

  override fun attach(host: Component): Boolean {
    val hostPanel = host as? SwingWebViewHostPanel ?: return false
    snapshotHost = hostPanel
    facade.setSnapshotHandler { width, height, pixels ->
      hostPanel.setSnapshotImage(width, height, pixels)
    }
    facade.attachOffscreen()
    attached = true
    lastAppliedFrame = null

    scheduleFrameUpdate(host)
    facade.setHidden(false)
    SwingUtilities.invokeLater { scheduleFrameUpdate(host) }
    return true
  }

  override fun detach() {
    if (!attached) return
    snapshotHost?.clearSnapshotImage()
    snapshotHost = null
    facade.setSnapshotHandler(null)
    facade.detach()
    attached = false
    lastAppliedFrame = null
  }

  override fun scheduleFrameUpdate(host: Component) {
    if (!attached) return
    val frame = AppliedFrame(host.width, host.height)
    if (frame == lastAppliedFrame) return
    lastAppliedFrame = frame
    facade.setBounds(0, 0, host.width, host.height, 1.0)
  }

  override fun updateVisibility(host: Component, hidden: Boolean) {
    if (!attached) return
    if (!hidden) {
      scheduleFrameUpdate(host)
    }
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
    val width: Int,
    val height: Int,
  )
}
