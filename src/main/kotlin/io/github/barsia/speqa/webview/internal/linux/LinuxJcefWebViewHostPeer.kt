// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package io.github.barsia.speqa.webview.internal.linux

import io.github.barsia.speqa.webview.internal.host.NativeWebViewHostPeer
import org.jetbrains.annotations.ApiStatus
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Container
import javax.swing.SwingUtilities

@ApiStatus.Internal
internal class LinuxJcefWebViewHostPeer(
  private val facade: JcefWebViewFacade,
) : NativeWebViewHostPeer {

  private var attachedHost: Container? = null

  override fun attach(host: Component): Boolean {
    val container = host as? Container ?: return false
    attachedHost = container
    val component = facade.component
    // JCEF browser is a Swing component on Linux. Adding it as the BorderLayout
    // centre lets Swing layout drive its geometry; we don't need
    // scheduleFrameUpdate / setBounds plumbing.
    container.add(component, BorderLayout.CENTER)
    container.revalidate()
    container.repaint()
    return true
  }

  override fun detach() {
    val container = attachedHost ?: return
    val component = facade.component
    container.remove(component)
    container.revalidate()
    container.repaint()
    attachedHost = null
  }

  override fun scheduleFrameUpdate(host: Component) {
    // No-op: Swing layout already drives geometry for the embedded JCEF component.
  }

  override fun updateVisibility(host: Component, hidden: Boolean) {
    SwingUtilities.invokeLater { facade.component.isVisible = !hidden }
  }

  // updateOverlayClipRects: inherited default no-op is correct - IDE overlays draw
  // above the JCEF heavyweight via Swing Z-order on Linux.

  override fun requestFocus() {
    SwingUtilities.invokeLater { facade.component.requestFocusInWindow() }
  }

  override fun clearFocus() {
    // No-op: focus naturally leaves the JCEF component when another Swing component
    // claims the keyboard focus.
  }

  // dispatchNativeTextEditingShortcut / dispatchNativeTextEditingCommand:
  // inherited default `return false` is correct. JCEF/Chromium handles
  // Cmd/Ctrl+X/C/V/A/Z natively; do not intercept.
}
