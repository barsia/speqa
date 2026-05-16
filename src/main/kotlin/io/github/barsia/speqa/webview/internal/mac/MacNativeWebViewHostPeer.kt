// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package io.github.barsia.speqa.webview.internal.mac

import com.intellij.ui.mac.foundation.Foundation
import com.intellij.ui.mac.foundation.ID
import com.intellij.ui.mac.foundation.MacUtil
import io.github.barsia.speqa.webview.SwingWebViewHostPanel
import io.github.barsia.speqa.webview.internal.MacMainThreadDispatcher
import io.github.barsia.speqa.webview.internal.WebViewLogger
import io.github.barsia.speqa.webview.internal.host.NativeOverlayClipShape
import io.github.barsia.speqa.webview.internal.host.NativeOverlayClippingPolicy
import io.github.barsia.speqa.webview.internal.host.NativePoint
import io.github.barsia.speqa.webview.internal.host.NativeWebViewHostPeer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import java.awt.Component
import java.awt.event.KeyEvent
import javax.swing.SwingUtilities
import javax.swing.Timer

@ApiStatus.Internal
internal class MacNativeWebViewHostPeer(
  private val scope: CoroutineScope,
  private val facade: MacWebViewFacade,
) : NativeWebViewHostPeer {

  private companion object {
    private const val FRAME_RETRY_LIMIT = 20
    private const val FRAME_RETRY_DELAY_MS = 50
    private const val FRAME_BOUNDS_EPSILON = 1.0
    private const val PARENT_CHAIN_LIMIT = 8
  }

  private enum class FrameRejectionReason(val logName: String) {
    NON_POSITIVE("non-positive"),
    ANCHOR_UNAVAILABLE("anchor unavailable"),
    OUTSIDE_ANCHOR_BOUNDS("outside anchor bounds"),
    HOST_NOT_SHOWING("host not showing"),
  }

  @Volatile
  private var attached = false
  @Volatile
  private var hostHidden = true
  @Volatile
  private var positiveFrameApplied = false
  @Volatile
  private var frameTemporarilyInvalid = true

  private var lastAppliedFrame: SwingWebViewHostPanel.NativeFrame? = null
  private var frameRetryCount = 0
  private var frameRetryTimer: Timer? = null
  private var parentChainDumped = false
  private var lastOverlayClipShapes: List<NativeOverlayClipShape> = emptyList()

  /** Coalesces resize/move events without depending on IntelliJ internal update queues. */
  private var pendingFrame: SwingWebViewHostPanel.NativeFrame? = null
  private val resizeTimer = Timer(16) {
    val frame = pendingFrame
    if (frame != null) {
      pendingFrame = null
      scope.launch(MacMainThreadDispatcher) {
        applyFrame(frame)
      }
    }
  }.apply {
    isRepeats = false
  }

  override fun attach(host: Component): Boolean {
    if (attached) return true

    facade.initialize()

    val nsWindow = resolveNSWindow(host) ?: return false
    val contentView = Foundation.invoke(nsWindow, "contentView")
    if (Foundation.isNil(contentView)) return false

    val anchor = SwingWebViewHostPanel.resolveAnchor(host) ?: return false
    val initialFrame = SwingWebViewHostPanel.calculateNativeFrame(host, anchor)
    WebViewLogger.LOG.info("Attaching WKWebView host: frame=$initialFrame, showing=${host.isShowing}")
    hostHidden = !host.isShowing
    positiveFrameApplied = false
    frameTemporarilyInvalid = true

    scope.launch(MacMainThreadDispatcher) {
      facade.attachToParent(contentView)
      attached = true
      facade.setHidden(true)

      // `addNotify` can run before Swing's first layout pass. Re-sync on EDT after
      // attach so the native view does not stay at a degenerate zero-size frame.
      SwingUtilities.invokeLater {
        hostHidden = !host.isShowing
        scheduleFrameUpdate(host)
      }
    }
    return true
  }

  override fun detach() {
    if (!attached) return

    scope.launch(MacMainThreadDispatcher) {
      facade.setOverlayClipShapes(0, 0, emptyList())
      facade.detachFromParent()
    }
    resetFrameRetries()
    resizeTimer.stop()
    pendingFrame = null
    attached = false
    hostHidden = true
    lastOverlayClipShapes = emptyList()
    positiveFrameApplied = false
    frameTemporarilyInvalid = true
    lastAppliedFrame = null
  }

  override fun scheduleFrameUpdate(host: Component) {
    if (!attached) return
    val anchor = SwingWebViewHostPanel.resolveAnchor(host)
    if (anchor == null) {
      rejectFrame(host, null, null, FrameRejectionReason.ANCHOR_UNAVAILABLE)
      return
    }

    val frame = SwingWebViewHostPanel.calculateNativeFrame(host, anchor)
    val rejectionReason = validateFrame(host, anchor, frame)
    if (rejectionReason != null) {
      rejectFrame(host, anchor, frame, rejectionReason)
      return
    }

    resetFrameRetries()
    queueFrame(frame)
  }

  override fun updateVisibility(host: Component, hidden: Boolean) {
    if (!attached) return
    hostHidden = hidden

    if (!hidden) {
      frameTemporarilyInvalid = true
      scheduleFrameUpdate(host)
    }

    scope.launch(MacMainThreadDispatcher) {
      updateNativeVisibility()
    }
  }

  override fun updateOverlayClipRects(
    host: Component,
    shapes: List<NativeOverlayClipShape>,
    awaitNativeCommit: Boolean,
  ) {
    if (!attached) return
    val width = host.width
    val height = host.height
    val appKitShapes = if (NativeOverlayClippingPolicy.isEnabled()) {
      shapes.mapNotNull { shape -> flipShapeForAppKit(shape, height) }
    }
    else {
      emptyList()
    }
    if (appKitShapes == lastOverlayClipShapes) return

    lastOverlayClipShapes = appKitShapes
    NativeOverlayClippingPolicy.logShapes("appkit", appKitShapes)
    if (awaitNativeCommit) {
      // Hide-transition fast path: block the EDT until AppKit has committed the mask update,
      // so the user does not see a residual hole over the WKWebView after the AWT overlay is
      // already gone. Cheap because hide transitions are infrequent compared to show/resize.
      facade.setOverlayClipShapes(width, height, appKitShapes, awaitNativeCommit = true)
    }
    else {
      scope.launch(MacMainThreadDispatcher) {
        facade.setOverlayClipShapes(width, height, appKitShapes)
      }
    }
  }

  override fun requestFocus() {
    if (!attached) return

    scope.launch(MacMainThreadDispatcher) {
      facade.requestFocus()
    }
  }

  override fun clearFocus() {
    if (!attached) return

    scope.launch(MacMainThreadDispatcher) {
      facade.clearFocus()
    }
  }

  override fun dispatchNativeTextEditingShortcut(event: KeyEvent): Boolean {
    if (!attached) return false
    return facade.dispatchNativeTextEditingShortcut(event)
  }

  override fun dispatchNativeTextEditingCommand(command: String): Boolean {
    if (!attached) return false
    return facade.dispatchNativeTextEditingCommand(command)
  }

  private fun queueFrame(frame: SwingWebViewHostPanel.NativeFrame) {
    pendingFrame = frame
    if (!resizeTimer.isRunning) {
      resizeTimer.start()
    }
  }

  private fun applyFrame(frame: SwingWebViewHostPanel.NativeFrame) {
    val firstPositiveFrame = !positiveFrameApplied
    if (frame != lastAppliedFrame) {
      lastAppliedFrame = frame
      logFrame(frame, firstPositiveFrame)
      facade.setFrame(frame.x, frame.y, frame.width, frame.height)
    }

    positiveFrameApplied = true
    frameTemporarilyInvalid = false
    updateNativeVisibility()
  }

  private fun updateNativeVisibility() {
    facade.setHidden(hostHidden || !positiveFrameApplied || frameTemporarilyInvalid)
  }

  private fun validateFrame(
    host: Component,
    anchor: Component,
    frame: SwingWebViewHostPanel.NativeFrame,
  ): FrameRejectionReason? {
    if (!host.isShowing) return FrameRejectionReason.HOST_NOT_SHOWING
    if (frame.width <= 0.0 || frame.height <= 0.0) return FrameRejectionReason.NON_POSITIVE

    val outsideAnchor =
      frame.x < -FRAME_BOUNDS_EPSILON ||
      frame.y < -FRAME_BOUNDS_EPSILON ||
      frame.x + frame.width > anchor.width + FRAME_BOUNDS_EPSILON ||
      frame.y + frame.height > anchor.height + FRAME_BOUNDS_EPSILON
    return if (outsideAnchor) FrameRejectionReason.OUTSIDE_ANCHOR_BOUNDS else null
  }

  private fun rejectFrame(
    host: Component,
    anchor: Component?,
    frame: SwingWebViewHostPanel.NativeFrame?,
    reason: FrameRejectionReason,
  ) {
    val retryScheduled = reason != FrameRejectionReason.HOST_NOT_SHOWING && scheduleFrameRetry(host)
    val hideRejectedFrame = shouldHideRejectedFrame(reason, retryScheduled)
    if (hideRejectedFrame) {
      frameTemporarilyInvalid = true
      scope.launch(MacMainThreadDispatcher) {
        updateNativeVisibility()
      }
    }
    logRejectedFrame(host, anchor, frame, reason, retryScheduled)
    logParentChainIfNeeded(host, anchor, frame, reason, retryScheduled)
  }

  private fun shouldHideRejectedFrame(reason: FrameRejectionReason, retryScheduled: Boolean): Boolean {
    return !positiveFrameApplied || reason != FrameRejectionReason.OUTSIDE_ANCHOR_BOUNDS || !retryScheduled
  }

  private fun scheduleFrameRetry(host: Component): Boolean {
    if (frameRetryTimer != null) return true
    if (frameRetryCount >= FRAME_RETRY_LIMIT) return false

    frameRetryCount++
    frameRetryTimer = Timer(FRAME_RETRY_DELAY_MS) {
      frameRetryTimer = null
      if (attached) {
        scheduleFrameUpdate(host)
      }
    }.apply {
      isRepeats = false
      start()
    }
    return true
  }

  private fun resetFrameRetries() {
    frameRetryTimer?.stop()
    frameRetryTimer = null
    frameRetryCount = 0
    parentChainDumped = false
  }

  private fun logFrame(frame: SwingWebViewHostPanel.NativeFrame, firstPositiveFrame: Boolean) {
    if (firstPositiveFrame) {
      WebViewLogger.LOG.info("Applying first positive WKWebView frame: $frame")
    }
    else {
      WebViewLogger.LOG.debug("Applying WKWebView frame: $frame")
    }
  }

  private fun logRejectedFrame(
    host: Component,
    anchor: Component?,
    frame: SwingWebViewHostPanel.NativeFrame?,
    reason: FrameRejectionReason,
    retryScheduled: Boolean,
  ) {
    val retryStatus = when {
      reason == FrameRejectionReason.HOST_NOT_SHOWING -> "not scheduled"
      retryScheduled -> "$frameRetryCount/$FRAME_RETRY_LIMIT"
      else -> "exhausted"
    }
    val anchorDescription = anchor?.let(::describeComponent) ?: "<unavailable>"
    WebViewLogger.LOG.info(
      "Rejected WKWebView frame: reason=${reason.logName}, frame=$frame, " +
      "host=${describeComponent(host)}, anchor=$anchorDescription, retry=$retryStatus"
    )
  }

  private fun logParentChainIfNeeded(
    host: Component,
    anchor: Component?,
    frame: SwingWebViewHostPanel.NativeFrame?,
    reason: FrameRejectionReason,
    retryScheduled: Boolean,
  ) {
    val retryExhausted = reason != FrameRejectionReason.HOST_NOT_SHOWING && !retryScheduled
    if (reason != FrameRejectionReason.OUTSIDE_ANCHOR_BOUNDS && !retryExhausted) return
    if (parentChainDumped && !retryExhausted) return

    parentChainDumped = true
    val anchorDescription = anchor?.let(::describeComponent) ?: "<unavailable>"
    WebViewLogger.LOG.warn(
      "WKWebView frame rejection details: reason=${reason.logName}, frame=$frame, " +
      "anchor=$anchorDescription, chain=${describeParentChain(host)}"
    )
  }

  private fun describeParentChain(host: Component): String {
    val components = generateSequence(host as Component?) { it.parent }
      .take(PARENT_CHAIN_LIMIT)
      .toList()
    val suffix = if (components.lastOrNull()?.parent != null) " <- ..." else ""
    return components.joinToString(" <- ") { describeComponent(it) } + suffix
  }

  private fun describeComponent(component: Component): String {
    return "${component.javaClass.name}[bounds=${component.bounds}, preferred=${component.preferredSize}, " +
           "showing=${component.isShowing}, displayable=${component.isDisplayable}]"
  }

  private fun resolveNSWindow(host: Component): ID? {
    val window = SwingUtilities.getWindowAncestor(host) ?: return null
    val nsWindow = MacUtil.getWindowFromJavaWindow(window)
    return if (Foundation.isNil(nsWindow)) null else nsWindow
  }
}

/**
 * Mirrors the Swing-origin (top-left) shape into AppKit's bottom-left coordinate space.
 * Returns null when the shape is degenerate (non-positive bounds or empty polygon).
 *
 * Extracted as a top-level helper so it can be unit-tested without AppKit/JNA.
 */
internal fun flipShapeForAppKit(shape: NativeOverlayClipShape, hostHeight: Int): NativeOverlayClipShape? {
  return when (shape) {
    is NativeOverlayClipShape.Rect -> {
      if (shape.width <= 0 || shape.height <= 0) return null
      NativeOverlayClipShape.Rect(
        x = shape.x,
        y = hostHeight - shape.y - shape.height,
        width = shape.width,
        height = shape.height,
      )
    }
    is NativeOverlayClipShape.RoundedRect -> {
      if (shape.width <= 0 || shape.height <= 0) return null
      NativeOverlayClipShape.RoundedRect(
        x = shape.x,
        y = hostHeight - shape.y - shape.height,
        width = shape.width,
        height = shape.height,
        radius = shape.radius,
      )
    }
    is NativeOverlayClipShape.Polygon -> {
      if (shape.points.isEmpty()) return null
      NativeOverlayClipShape.Polygon(
        points = shape.points.map { p -> NativePoint(p.x, hostHeight - p.y) },
      )
    }
  }
}
