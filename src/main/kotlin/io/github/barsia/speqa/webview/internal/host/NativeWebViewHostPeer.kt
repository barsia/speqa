// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package io.github.barsia.speqa.webview.internal.host

import com.intellij.openapi.util.SystemInfo
import io.github.barsia.speqa.webview.WebViewFacade
import io.github.barsia.speqa.webview.internal.mac.MacNativeWebViewHostPeer
import io.github.barsia.speqa.webview.internal.mac.MacWebViewFacade
import io.github.barsia.speqa.webview.internal.windows.WinNativeWebViewHostPeer
import io.github.barsia.speqa.webview.internal.windows.WinWebViewFacade
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus
import java.awt.Component
import java.awt.Rectangle
import java.awt.event.KeyEvent
import kotlin.math.ceil
import kotlin.math.floor

internal data class NativePoint(
  val x: Double,
  val y: Double,
)

internal sealed interface NativeOverlayClipShape {
  val bounds: Rectangle

  /**
   * Shape-accurate point-containment test in the shape's local coordinate system. Used by the
   * native hit-test to decide whether a click should be consumed by the overlay or routed to
   * the underlying WKWebView. Must mirror the geometry of the visual mask path so clicks in
   * the rounded-corner / polygon-concavity regions correctly fall through to the WKWebView.
   */
  fun contains(x: Double, y: Double): Boolean

  data class Rect(val x: Int, val y: Int, val width: Int, val height: Int) : NativeOverlayClipShape {
    override val bounds: Rectangle = Rectangle(x, y, width, height)
    override fun contains(x: Double, y: Double): Boolean {
      return x >= this.x && y >= this.y && x < this.x + width && y < this.y + height
    }
  }

  data class RoundedRect(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val radius: Double,
  ) : NativeOverlayClipShape {
    override val bounds: Rectangle = Rectangle(x, y, width, height)
    override fun contains(x: Double, y: Double): Boolean {
      if (x < this.x || y < this.y || x >= this.x + width || y >= this.y + height) return false
      val maxRadius = minOf(width, height) / 2.0
      val r = radius.coerceIn(0.0, maxRadius)
      if (r == 0.0) return true
      val xFromLeft = x - this.x
      val xFromRight = this.x + width - x
      val yFromTop = y - this.y
      val yFromBottom = this.y + height - y
      val dx = when {
        xFromLeft < r -> r - xFromLeft
        xFromRight < r -> r - xFromRight
        else -> 0.0
      }
      val dy = when {
        yFromTop < r -> r - yFromTop
        yFromBottom < r -> r - yFromBottom
        else -> 0.0
      }
      return dx * dx + dy * dy <= r * r
    }
  }

  data class Polygon(val points: List<NativePoint>) : NativeOverlayClipShape {
    // Use floor(min) / ceil(max) so fractional polygon extents produce an inclusive
    // integer AABB; truncating with toInt() shrinks the bbox and hides edge pixels
    // from the hit-test/clip-check paths.
    override val bounds: Rectangle = run {
      if (points.isEmpty()) return@run Rectangle()
      var minX = points[0].x
      var minY = points[0].y
      var maxX = minX
      var maxY = minY
      for (p in points) {
        if (p.x < minX) minX = p.x
        if (p.y < minY) minY = p.y
        if (p.x > maxX) maxX = p.x
        if (p.y > maxY) maxY = p.y
      }
      val x = floor(minX).toInt()
      val y = floor(minY).toInt()
      Rectangle(
        x,
        y,
        ceil(maxX).toInt() - x,
        ceil(maxY).toInt() - y,
      )
    }

    override fun contains(x: Double, y: Double): Boolean {
      if (points.size < 3) return false
      var inside = false
      var j = points.size - 1
      for (i in points.indices) {
        val xi = points[i].x
        val yi = points[i].y
        val xj = points[j].x
        val yj = points[j].y
        val crossesY = (yi > y) != (yj > y)
        if (crossesY) {
          val xIntersect = (xj - xi) * (y - yi) / (yj - yi) + xi
          if (x < xIntersect) inside = !inside
        }
        j = i
      }
      return inside
    }
  }
}

@ApiStatus.Internal
internal interface NativeWebViewHostPeer {
  fun attach(host: Component): Boolean
  fun detach()
  fun scheduleFrameUpdate(host: Component)
  fun updateVisibility(host: Component, hidden: Boolean)
  /**
   * Pushes the latest overlay clip shapes to the native peer.
   *
   * @param awaitNativeCommit when `true`, the call must synchronously commit the native mask
   *   update (e.g. via `Foundation.executeOnMainThread(waitUntilDone=true)` plus a `CATransaction`
   *   flush) before returning. Used on hide transitions, where the user-visible latency between
   *   "balloon disappears at Swing layer" and "mask hole disappears in WKWebView" must be
   *   minimized to avoid a residual white frame.
   *
   *   When `false` (default), the call may be async and debounced.
   */
  fun updateOverlayClipRects(
    host: Component,
    shapes: List<NativeOverlayClipShape>,
    awaitNativeCommit: Boolean = false,
  ) = Unit
  fun requestFocus()
  fun clearFocus()
  fun dispatchNativeTextEditingShortcut(event: KeyEvent): Boolean = false
  fun dispatchNativeTextEditingCommand(command: String): Boolean = false

  companion object {
    fun create(scope: CoroutineScope, facade: WebViewFacade): NativeWebViewHostPeer? {
      return when {
        SystemInfo.isMac && facade is MacWebViewFacade -> MacNativeWebViewHostPeer(scope, facade)
        SystemInfo.isWindows && facade is WinWebViewFacade -> WinNativeWebViewHostPeer(facade)
        SystemInfo.isLinux && facade is io.github.barsia.speqa.webview.internal.linux.JcefWebViewFacade ->
          io.github.barsia.speqa.webview.internal.linux.LinuxJcefWebViewHostPeer(facade)
        else -> null
      }
    }
  }
}
