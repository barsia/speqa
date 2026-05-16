// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package io.github.barsia.speqa.webview.internal.linux

import io.github.barsia.speqa.webview.internal.host.NativeWebViewHostPeer
import org.jetbrains.annotations.ApiStatus
import java.awt.Component

@ApiStatus.Internal
internal class LinuxNativeWebViewHostPeer(
  facade: LinuxWebKitWebViewFacade,
) : NativeWebViewHostPeer {

  private val delegate: NativeWebViewHostPeer = when (facade.backend) {
    LinuxWebKitBackend.X11 -> LinuxX11NativeWebViewHostPeer(facade)
    LinuxWebKitBackend.WaylandSnapshot -> LinuxWaylandSnapshotWebViewHostPeer(facade)
  }

  override fun attach(host: Component): Boolean = delegate.attach(host)

  override fun detach() = delegate.detach()

  override fun scheduleFrameUpdate(host: Component) = delegate.scheduleFrameUpdate(host)

  override fun updateVisibility(host: Component, hidden: Boolean) = delegate.updateVisibility(host, hidden)

  override fun requestFocus() = delegate.requestFocus()

  override fun clearFocus() = delegate.clearFocus()
}
