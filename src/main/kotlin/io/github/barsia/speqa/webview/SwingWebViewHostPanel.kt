// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package io.github.barsia.speqa.webview

import io.github.barsia.speqa.webview.internal.WebViewLogger
import io.github.barsia.speqa.webview.internal.host.NativeWebViewHostPeer
import io.github.barsia.speqa.webview.internal.host.NativeOverlayClipShape
import io.github.barsia.speqa.webview.internal.host.NativeOverlayClippingPolicy
import io.github.barsia.speqa.webview.internal.host.OverlayBoundsExtractor
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus
import java.awt.AWTEvent
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Container
import java.awt.Graphics
import java.awt.KeyEventDispatcher
import java.awt.KeyboardFocusManager
import java.awt.Point
import java.awt.Rectangle
import java.awt.Toolkit
import java.awt.event.AWTEventListener
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.ContainerEvent
import java.awt.event.HierarchyBoundsAdapter
import java.awt.event.HierarchyBoundsListener
import java.awt.event.HierarchyEvent
import java.awt.event.HierarchyListener
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.awt.geom.Area
import java.awt.geom.Path2D
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.awt.image.DataBufferInt
import javax.swing.JComponent
import javax.swing.JLayeredPane
import javax.swing.JPanel
import javax.swing.RootPaneContainer
import javax.swing.SwingUtilities
import javax.swing.Timer

/**
 * Swing host panel that manages the lifecycle of a native [WebViewFacade].
 *
 * The native WebView is attached in [addNotify] when the panel joins a displayable Swing
 * hierarchy, and detached in [removeNotify] when the panel is removed. Resize and visibility
 * events are forwarded to the native view with coalescing to avoid redundant native calls.
 *
 * **Threading**: Must be created and used on the EDT. The [scope] is used for
 * coroutine-based lifecycle management; native calls are internally dispatched
 * to the owning native UI thread.
 */
@ApiStatus.Experimental
class SwingWebViewHostPanel private constructor(
  val scope: CoroutineScope,
  val facade: WebViewFacade,
  private val nativePeer: NativeWebViewHostPeer?,
  private val onNativeTextInputFocusChanged: (Boolean) -> Unit,
  @Suppress("UNUSED_PARAMETER")
  marker: Unit,
) : JPanel(BorderLayout()), SwingWebViewHost {

  constructor(
    scope: CoroutineScope,
    facade: WebViewFacade,
  ) : this(scope, facade, NativeWebViewHostPeer.create(scope, facade), {}, Unit)

  constructor(
    scope: CoroutineScope,
    facade: WebViewFacade,
    onNativeTextInputFocusChanged: (Boolean) -> Unit,
  ) : this(scope, facade, NativeWebViewHostPeer.create(scope, facade), onNativeTextInputFocusChanged, Unit)

  internal constructor(
    scope: CoroutineScope,
    facade: WebViewFacade,
    nativePeer: NativeWebViewHostPeer?,
  ) : this(scope, facade, nativePeer, {}, Unit)

  internal constructor(
    scope: CoroutineScope,
    facade: WebViewFacade,
    nativePeer: NativeWebViewHostPeer?,
    onNativeTextInputFocusChanged: (Boolean) -> Unit,
  ) : this(scope, facade, nativePeer, onNativeTextInputFocusChanged, Unit)

  internal data class NativeFrame(
    val x: Double,
    val y: Double,
    val width: Double,
    val height: Double,
  )

  internal data class NativeBounds(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
  )

  internal companion object {
    private const val PROBE_MAX_DIM: Int = 128
    private const val ALPHA_THRESHOLD: Int = 8

    fun calculateNativeFrame(host: Component, anchor: Component): NativeFrame {
      val hostOrigin = SwingUtilities.convertPoint(host, 0, 0, anchor)
      val width = host.width.toDouble()
      val height = host.height.toDouble()
      val flippedY = anchor.height.toDouble() - hostOrigin.y.toDouble() - height

      return NativeFrame(
        x = hostOrigin.x.toDouble(),
        y = flippedY,
        width = width,
        height = height,
      )
    }

    fun calculateWindowsBounds(host: Component, anchor: Component): NativeBounds {
      val hostOrigin = SwingUtilities.convertPoint(host, 0, 0, anchor)
      val trailingClip = calculateTrailingClip(host, anchor, hostOrigin)
      return NativeBounds(
        x = hostOrigin.x,
        y = hostOrigin.y,
        width = (trailingClip.right - hostOrigin.x).coerceAtLeast(0),
        height = (trailingClip.bottom - hostOrigin.y).coerceAtLeast(0),
      )
    }

    private data class TrailingClip(
      val right: Int,
      val bottom: Int,
    )

    private fun calculateTrailingClip(host: Component, anchor: Component, hostOrigin: Point): TrailingClip {
      var right = minOf(hostOrigin.x + host.width, anchor.width)
      var bottom = minOf(hostOrigin.y + host.height, anchor.height)

      for (component in host.selfAndAncestorsUntil(anchor)) {
        val parent = component.parent ?: continue
        val parentOrigin = SwingUtilities.convertPoint(parent, 0, 0, anchor)
        right = minOf(right, parentOrigin.x + parent.width)
        bottom = minOf(bottom, parentOrigin.y + parent.height)

        for (sibling in parent.components) {
          if (sibling === component || !sibling.isVisible || sibling.width <= 0 || sibling.height <= 0) continue

          val siblingOrigin = SwingUtilities.convertPoint(sibling, 0, 0, anchor)
          val siblingRight = siblingOrigin.x + sibling.width
          val siblingBottom = siblingOrigin.y + sibling.height

          if (siblingOrigin.x > hostOrigin.x && siblingOrigin.x < right && siblingOrigin.y < bottom && siblingBottom > hostOrigin.y) {
            right = siblingOrigin.x
          }
          if (siblingOrigin.y > hostOrigin.y && siblingOrigin.y < bottom && siblingOrigin.x < right && siblingRight > hostOrigin.x) {
            bottom = siblingOrigin.y
          }
        }
      }
      return TrailingClip(right, bottom)
    }

    private fun Component.selfAndAncestorsUntil(anchor: Component): Sequence<Component> {
      return generateSequence(this) { component -> component.parent }
        .takeWhile { component -> component !== anchor }
    }

    internal fun resolveAnchor(component: Component): Component? {
      val window = SwingUtilities.getWindowAncestor(component) ?: return null
      return if (window is RootPaneContainer) window.contentPane else window
    }

    internal fun resolveWindowsAnchor(component: Component): Component? {
      val window = SwingUtilities.getWindowAncestor(component) ?: return null
      return if (window is RootPaneContainer) window.rootPane else window
    }
  }

  override val component: JComponent
    get() = this

  private var hierarchyListener: HierarchyListener? = null
  private var hierarchyBoundsListener: HierarchyBoundsListener? = null
  private var focusTransferListener: AWTEventListener? = null
  private var overlayClipListener: AWTEventListener? = null
  private var nativeTextShortcutDispatcher: KeyEventDispatcher? = null
  private var listenersInstalled = false
  private var nativeTextInputFocusActive = false
  private var ignoreNextNativeTextInputFocusLoss = false
  private var snapshotImage: BufferedImage? = null
  private var lastOverlayClipShapes: List<NativeOverlayClipShape> = emptyList()
  private val overlayClipTimer = Timer(40) {
    updateOverlayClipRects()
  }.apply {
    isRepeats = false
  }

  init {
    // Native heavyweight WebViews cover the panel; painting the default grey
    // Panel.background would flash through transient gaps during live resize.
    isOpaque = false
    isFocusable = true
  }

  private val resizeListener = object : ComponentAdapter() {
    override fun componentResized(e: ComponentEvent) = scheduleFrameUpdate()
    override fun componentMoved(e: ComponentEvent) = scheduleFrameUpdate()
    override fun componentShown(e: ComponentEvent) = updateVisibility(false)
    override fun componentHidden(e: ComponentEvent) = updateVisibility(true)
  }

  override fun addNotify() {
    super.addNotify()
    val peer = nativePeer ?: return
    installListeners()
    peer.attach(this)
  }

  override fun removeNotify() {
    nativePeer?.detach()
    uninstallListeners()
    super.removeNotify()
  }

  override fun paintComponent(g: Graphics) {
    super.paintComponent(g)
    val image = snapshotImage ?: return
    g.drawImage(image, 0, 0, width, height, null)
  }

  private fun scheduleFrameUpdate() {
    nativePeer?.scheduleFrameUpdate(this)
    scheduleOverlayClipUpdate()
  }

  private fun updateVisibility(hidden: Boolean) {
    if (hidden) {
      setNativeTextInputFocusActive(false, force = true)
    }
    nativePeer?.updateVisibility(this, hidden)
    scheduleOverlayClipUpdate()
  }

  private fun handleOverlayComponentEvent(event: ComponentEvent) {
    val source = event.component ?: return
    val hostWindow = SwingUtilities.getWindowAncestor(this) ?: return
    if (SwingUtilities.getWindowAncestor(source) != hostWindow) return
    routeOverlayEventForSource(event, source)
  }

  /**
   * Routes a recognised overlay [event] from [source] to the right update path.
   * - Hide transitions (`COMPONENT_HIDDEN`, `COMPONENT_REMOVED`) skip the 40 ms debounce timer
   *   and request a synchronous native commit so the residual mask hole disappears in the same
   *   visual frame as the AWT overlay.
   * - Show / move / resize / add transitions use the debounced path because they fire densely
   *   during animations.
   *
   * `internal` so tests can drive routing without requiring a real `Window` ancestor for the
   * host panel.
   */
  internal fun routeOverlayEventForSource(event: ComponentEvent, source: Component) {
    val id = event.id
    if (id != ComponentEvent.COMPONENT_SHOWN &&
        id != ComponentEvent.COMPONENT_HIDDEN &&
        id != ComponentEvent.COMPONENT_MOVED &&
        id != ComponentEvent.COMPONENT_RESIZED &&
        id != ContainerEvent.COMPONENT_ADDED &&
        id != ContainerEvent.COMPONENT_REMOVED
    ) {
      return
    }
    if (source === this || SwingUtilities.isDescendingFrom(source, this)) return
    if (id == ComponentEvent.COMPONENT_HIDDEN || id == ContainerEvent.COMPONENT_REMOVED) {
      updateOverlayClipRectsImmediately()
    }
    else {
      scheduleOverlayClipUpdate()
    }
  }

  internal fun isOverlayClipTimerRunningForTest(): Boolean = overlayClipTimer.isRunning

  /**
   * Synchronously runs the debounced overlay-clip update body. Tests use this to seed
   * `lastOverlayClipShapes` from the current Swing state without waiting for the 40 ms timer.
   */
  internal fun flushDebouncedOverlayClipUpdateForTest() {
    overlayClipTimer.stop()
    updateOverlayClipRects()
  }

  private fun scheduleOverlayClipUpdate() {
    if (!listenersInstalled || !isDisplayable) return
    if (!overlayClipTimer.isRunning) {
      overlayClipTimer.start()
    }
  }

  private fun updateOverlayClipRects() {
    if (!NativeOverlayClippingPolicy.isEnabled()) {
      clearOverlayClipRects()
      return
    }

    val shapes = calculateOverlayClipRects()
    if (shapes == lastOverlayClipShapes) return
    lastOverlayClipShapes = shapes
    WebViewLogger.LOG.debug("Native WebView overlay clip shapes=${shapes.size}")
    NativeOverlayClippingPolicy.logShapes("swing", shapes)
    nativePeer?.updateOverlayClipRects(this, shapes)
  }

  /**
   * Hide-transition fast path. Bypasses the 40 ms debounce timer and asks the native peer to
   * commit the mask update synchronously. Without this, an overlay that has just been hidden
   * at the Swing layer can leave its mask hole visible over the native WebView for ~40 ms +
   * IPC round-trip, which the user perceives as a white frame after a balloon dismissal.
   *
   * The debounced path is still used for SHOW / RESIZE / MOVE events: those fire densely during
   * animations and an over-eager sync mask update would thrash the CALayer mask.
   */
  private fun updateOverlayClipRectsImmediately() {
    if (!listenersInstalled) return
    overlayClipTimer.stop()
    if (!NativeOverlayClippingPolicy.isEnabled()) {
      clearOverlayClipRects(awaitNativeCommit = true)
      return
    }

    val shapes = calculateOverlayClipRects()
    if (shapes == lastOverlayClipShapes) return
    lastOverlayClipShapes = shapes
    WebViewLogger.LOG.debug("Native WebView overlay clip shapes (sync)=${shapes.size}")
    NativeOverlayClippingPolicy.logShapes("swing-sync", shapes)
    nativePeer?.updateOverlayClipRects(this, shapes, awaitNativeCommit = true)
  }

  private fun clearOverlayClipRects(awaitNativeCommit: Boolean = false) {
    if (lastOverlayClipShapes.isEmpty()) return
    lastOverlayClipShapes = emptyList()
    NativeOverlayClippingPolicy.logShapes("swing-clear", emptyList())
    nativePeer?.updateOverlayClipRects(this, emptyList(), awaitNativeCommit = awaitNativeCommit)
  }

  internal fun calculateOverlayClipRects(
    extractor: OverlayBoundsExtractor = OverlayBoundsExtractor.Default,
  ): List<NativeOverlayClipShape> {
    val rootPane = SwingUtilities.getRootPane(this) ?: return emptyList()
    val layeredPane = rootPane.layeredPane ?: return emptyList()
    val parent = parent ?: return emptyList()
    val hostBounds = SwingUtilities.convertRectangle(parent, bounds, layeredPane)
    if (hostBounds.isEmpty) return emptyList()

    val overlays = mutableListOf<OverlayCandidate>()
    for (component in layeredPane.components) {
      if (layeredPane.getLayer(component) <= JLayeredPane.DEFAULT_LAYER) continue
      collectOverlayBounds(component, layeredPane, hostBounds, overlays, extractor, includeSelf = true)
    }

    val glassPane = rootPane.glassPane
    if (glassPane != null && glassPane.isVisible) {
      collectOverlayBounds(glassPane, layeredPane, hostBounds, overlays, extractor, includeSelf = false)
    }

    return overlays
      .map { candidate -> candidate.toHostClipShape(hostBounds, extractor) }
      .distinct()
      .mergeIntersecting()
  }

  private data class OverlayCandidate(val component: Component, val rect: Rectangle)

  private fun collectOverlayBounds(
    component: Component,
    coordinateRoot: Component,
    hostBounds: Rectangle,
    target: MutableList<OverlayCandidate>,
    extractor: OverlayBoundsExtractor,
    includeSelf: Boolean,
  ) {
    if (!component.isVisible || component.width <= 0 || component.height <= 0 || containsHost(component)) return

    val boundsInRoot = component.parent
      ?.let { parent -> SwingUtilities.convertRectangle(parent, component.bounds, coordinateRoot) }
      ?: component.bounds
    val intersectsHost = boundsInRoot.intersects(hostBounds)
    val children = if (component is Container) component.components else emptyArray()
    val hasVisibleChildren = children.any { child -> child.isVisible && child.width > 0 && child.height > 0 }
    val transparentSurfaceContainer =
      hasVisibleChildren &&
        component is JComponent &&
        !component.isOpaque &&
        boundsInRoot.width >= coordinateRoot.width &&
        boundsInRoot.height >= coordinateRoot.height

    if (includeSelf && intersectsHost && !transparentSurfaceContainer) {
      // Store the *unclipped* visual bounds so resolveShape sees the overlay's actual geometry.
      // Clipping the rect to hostBounds here would make BalloonShapeProbe (and the rounded-border
      // heuristic) treat the host edge as a real balloon edge and re-add rounded corners / arrow
      // pointers at the cut, leaving white slivers along the host boundary. The host-side clip is
      // applied later: for plain Rect shapes in toHostClipShape, and for RoundedRect/Polygon by
      // CALayer auto-clipping the mask paint plus the frame-bounded hit-test in WKWebViewBridge.
      val visualBounds = extractor.extract(component, boundsInRoot)
      // Heavyweight popups (own NSWindow) sometimes leave a placeholder component in the IDE's
      // JLayeredPane for tracking/focus while painting their actual content in a separate window.
      // Masking the WebView for such a placeholder punches a hole that the heavyweight content
      // does NOT fill, leaving a visible dark/black rectangle. Probe the component by painting it
      // off-screen: if it produces no visible pixels, it isn't a real visual overlay - skip it.
      if (!paintsAnyVisiblePixels(component)) return
      target += OverlayCandidate(component, visualBounds)
      return
    }

    for (child in children) {
      collectOverlayBounds(child, coordinateRoot, hostBounds, target, extractor, includeSelf = true)
    }
  }

  /**
   * Renders [component] off-screen and reports whether any pixel ends up non-transparent.
   *
   * Used to filter out heavyweight-popup placeholders that live in the IDE's JLayeredPane
   * for tracking purposes while painting their real content in a separate NSWindow. Such
   * placeholders return no pixels here and must not trigger a WebView mask hole.
   *
   * Painting cost is bounded by [PROBE_MAX_DIM]: components larger than that are scaled down
   * before painting so a small mask probe stays cheap on every overlay recomputation tick.
   */
  private fun paintsAnyVisiblePixels(component: Component): Boolean {
    val width = component.width
    val height = component.height
    if (width <= 0 || height <= 0) return false
    val sampleWidth = width.coerceAtMost(PROBE_MAX_DIM)
    val sampleHeight = height.coerceAtMost(PROBE_MAX_DIM)
    val image = BufferedImage(sampleWidth, sampleHeight, BufferedImage.TYPE_INT_ARGB)
    val g = image.createGraphics()
    try {
      if (width > sampleWidth || height > sampleHeight) {
        g.scale(sampleWidth.toDouble() / width, sampleHeight.toDouble() / height)
      }
      component.printAll(g)
    } catch (t: Throwable) {
      // If the off-screen paint throws, assume the component IS visual so we don't accidentally
      // suppress a real overlay. Mask hole is the safer default.
      WebViewLogger.LOG.debug("Overlay paint probe failed for ${component.javaClass.name}", t)
      return true
    } finally {
      g.dispose()
    }
    val pixels = (image.raster.dataBuffer as DataBufferInt).data
    for (px in pixels) {
      if ((px ushr 24) and 0xFF > ALPHA_THRESHOLD) return true
    }
    return false
  }

  private fun OverlayCandidate.toHostClipShape(
    hostBounds: Rectangle,
    extractor: OverlayBoundsExtractor,
  ): NativeOverlayClipShape {
    // Pass the *unclipped* host-relative rect to resolveShape. Coordinates may be negative or
    // extend past hostBounds when the overlay straddles the host edge; this preserves the
    // overlay's true geometry for shape resolvers (BalloonShapeProbe, rounded-border heuristic,
    // third-party OverlayShapeResolver implementations).
    val hostRelative = Rectangle(
      rect.x - hostBounds.x,
      rect.y - hostBounds.y,
      rect.width.coerceAtLeast(0),
      rect.height.coerceAtLeast(0),
    )
    val shape = extractor.resolveShape(component, hostRelative)
    // Plain Rect shapes are clipped here to keep the panel-level contract (host-bounded ints) and
    // existing test expectations. RoundedRect / Polygon shapes flow to the native bridge
    // unclipped: the CALayer mask path is clipped to the WKWebView frame on render, and the
    // shape-accurate hit-test is gated by `frame.containsLocalPoint(...)` so any geometry outside
    // the host has no visible or interactive effect.
    return if (shape is NativeOverlayClipShape.Rect) shape.clippedToHost(hostBounds) else shape
  }

  private fun NativeOverlayClipShape.Rect.clippedToHost(hostBounds: Rectangle): NativeOverlayClipShape.Rect {
    val left = x.coerceAtLeast(0)
    val top = y.coerceAtLeast(0)
    val right = (x + width).coerceAtMost(hostBounds.width)
    val bottom = (y + height).coerceAtMost(hostBounds.height)
    return NativeOverlayClipShape.Rect(
      left,
      top,
      (right - left).coerceAtLeast(0),
      (bottom - top).coerceAtLeast(0),
    )
  }

  private fun containsHost(component: Component): Boolean {
    return component === this || (component is Container && SwingUtilities.isDescendingFrom(this, component))
  }

  private fun List<NativeOverlayClipShape>.mergeIntersecting(): List<NativeOverlayClipShape> {
    val merged = mutableListOf<NativeOverlayClipShape>()
    for (shape in this) {
      val shapeBounds = shape.bounds
      if (shapeBounds.width <= 0 || shapeBounds.height <= 0) continue
      var candidate: NativeOverlayClipShape = shape
      var index = 0
      while (index < merged.size) {
        val existing = merged[index]
        if (shapesOverlap(existing, candidate)) {
          candidate = unionShapes(existing, candidate)
          merged.removeAt(index)
          index = 0
        }
        else {
          index++
        }
      }
      merged += candidate
    }
    return merged
  }

  // For Rect-Rect pairs the bounding box equals the shape, so bbox intersection is exact.
  // For non-rect shapes (RoundedRect transparent corners, disjoint Polygon triangles sharing a
  // bbox) bbox overlap is broader than true geometric overlap, so we fall back to a Path2D/Area
  // intersection test to avoid collapsing visually disjoint shapes into a bounding-box rect.
  private fun shapesOverlap(a: NativeOverlayClipShape, b: NativeOverlayClipShape): Boolean {
    if (!a.bounds.intersects(b.bounds)) return false
    if (a is NativeOverlayClipShape.Rect && b is NativeOverlayClipShape.Rect) return true
    val area = Area(a.toAwtShape())
    area.intersect(Area(b.toAwtShape()))
    return !area.isEmpty
  }

  private fun NativeOverlayClipShape.toAwtShape(): java.awt.Shape = when (this) {
    is NativeOverlayClipShape.Rect -> Rectangle(x, y, width, height)
    is NativeOverlayClipShape.RoundedRect -> {
      val maxRadius = minOf(width, height) / 2.0
      val r = radius.coerceIn(0.0, maxRadius)
      RoundRectangle2D.Double(x.toDouble(), y.toDouble(), width.toDouble(), height.toDouble(), r * 2, r * 2)
    }
    is NativeOverlayClipShape.Polygon -> Path2D.Double().apply {
      if (points.isNotEmpty()) {
        moveTo(points[0].x, points[0].y)
        for (i in 1 until points.size) lineTo(points[i].x, points[i].y)
        closePath()
      }
    }
  }

  // When two non-rect cutouts truly overlap, the macOS CGPath mask (built with even-odd winding
  // from host-rect + per-shape subpaths) inverts the overlap region: 3 stacked subpaths → odd →
  // filled, so the WKWebView shows through there while shape.contains-based hit-testing still
  // treats the same region as overlay. Collapsing a truly-overlapping pair to the bounding-box
  // rect union makes the mask and the hit-test agree at the cost of rounded/polygon precision in
  // the merged area.
  private fun unionShapes(a: NativeOverlayClipShape, b: NativeOverlayClipShape): NativeOverlayClipShape {
    val ab = a.bounds
    val bb = b.bounds
    val left = minOf(ab.x, bb.x)
    val top = minOf(ab.y, bb.y)
    val right = maxOf(ab.x + ab.width, bb.x + bb.width)
    val bottom = maxOf(ab.y + ab.height, bb.y + bb.height)
    return NativeOverlayClipShape.Rect(left, top, right - left, bottom - top)
  }

  override fun requestWebViewFocus() {
    nativePeer?.requestFocus()
  }

  override fun clearWebViewFocus() {
    setNativeTextInputFocusActive(false, force = true)
    nativePeer?.clearFocus()
  }

  internal fun setNativeTextInputFocusActive(active: Boolean, force: Boolean = false) {
    if (!active && !force && ignoreNextNativeTextInputFocusLoss) {
      ignoreNextNativeTextInputFocusLoss = false
      WebViewLogger.LOG.debug("Ignored transient native WebView text focus loss")
      return
    }
    if (active) {
      ignoreNextNativeTextInputFocusLoss = false
      claimJavaFocusForNativeTextInput()
    }
    if (nativeTextInputFocusActive == active) return
    nativeTextInputFocusActive = active
    WebViewLogger.LOG.debug("Native WebView text focus active=$active")
    onNativeTextInputFocusChanged(active)
  }

  internal fun dispatchNativeTextEditingShortcut(event: KeyEvent): Boolean {
    if (!nativeTextInputFocusActive || event.isConsumed) return false
    val handled = nativePeer?.dispatchNativeTextEditingShortcut(event) == true
    if (handled) {
      suppressNextNativeTextInputFocusLoss()
      WebViewLogger.LOG.debug(
        "Native WebView text shortcut consumed: keyCode=${event.keyCode}, modifiers=${event.modifiersEx}",
      )
    }
    return handled
  }

  internal fun dispatchNativeTextEditingCommand(command: String): Boolean {
    if (!nativeTextInputFocusActive) return false
    val handled = nativePeer?.dispatchNativeTextEditingCommand(command) == true
    if (handled) {
      suppressNextNativeTextInputFocusLoss()
      WebViewLogger.LOG.debug("Native WebView text command consumed: command=$command")
    }
    return handled
  }

  private fun suppressNextNativeTextInputFocusLoss() {
    ignoreNextNativeTextInputFocusLoss = true
  }

  private fun claimJavaFocusForNativeTextInput() {
    if (!isShowing) return
    val focusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager()
    if (focusManager.focusOwner === this) return
    requestFocusInWindow()
  }

  internal fun setSnapshotImage(width: Int, height: Int, pixels: IntArray) {
    if (width <= 0 || height <= 0 || pixels.isEmpty()) {
      clearSnapshotImage()
      return
    }

    @Suppress("UndesirableClassUsage")
    val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB_PRE)
    val target = (image.raster.dataBuffer as DataBufferInt).data
    pixels.copyInto(target, endIndex = minOf(target.size, pixels.size))
    snapshotImage = image
    repaint()
  }

  internal fun clearSnapshotImage() {
    snapshotImage = null
    repaint()
  }

  private fun installListeners() {
    if (listenersInstalled) return
    addComponentListener(resizeListener)
    val listener = HierarchyListener { e ->
      if (e.changeFlags and HierarchyEvent.SHOWING_CHANGED.toLong() != 0L) {
        updateVisibility(!isShowing)
      }
    }
    hierarchyListener = listener
    addHierarchyListener(listener)
    val boundsListener = object : HierarchyBoundsAdapter() {
      override fun ancestorMoved(e: HierarchyEvent) = scheduleFrameUpdate()
      override fun ancestorResized(e: HierarchyEvent) = scheduleFrameUpdate()
    }
    hierarchyBoundsListener = boundsListener
    addHierarchyBoundsListener(boundsListener)
    val focusListener = AWTEventListener { event ->
      if (event !is MouseEvent || event.id != MouseEvent.MOUSE_PRESSED) return@AWTEventListener
      val source = event.component ?: return@AWTEventListener
      val hostWindow = SwingUtilities.getWindowAncestor(this) ?: return@AWTEventListener
      if (SwingUtilities.getWindowAncestor(source) != hostWindow) return@AWTEventListener
      if (source === this || SwingUtilities.isDescendingFrom(source, this)) return@AWTEventListener
      setNativeTextInputFocusActive(false, force = true)
      nativePeer?.clearFocus()
    }
    focusTransferListener = focusListener
    Toolkit.getDefaultToolkit().addAWTEventListener(focusListener, AWTEvent.MOUSE_EVENT_MASK)
    val overlayListener = AWTEventListener { event ->
      if (event is ComponentEvent) handleOverlayComponentEvent(event)
    }
    overlayClipListener = overlayListener
    Toolkit.getDefaultToolkit().addAWTEventListener(
      overlayListener,
      AWTEvent.COMPONENT_EVENT_MASK or AWTEvent.CONTAINER_EVENT_MASK,
    )
    val shortcutDispatcher = KeyEventDispatcher { event ->
      if (!isEventFromHostWindow(event)) return@KeyEventDispatcher false
      dispatchNativeTextEditingShortcut(event)
    }
    nativeTextShortcutDispatcher = shortcutDispatcher
    KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(shortcutDispatcher)
    listenersInstalled = true
  }

  private fun uninstallListeners() {
    if (!listenersInstalled) return
    removeComponentListener(resizeListener)
    hierarchyListener?.let {
      removeHierarchyListener(it)
      hierarchyListener = null
    }
    hierarchyBoundsListener?.let {
      removeHierarchyBoundsListener(it)
      hierarchyBoundsListener = null
    }
    focusTransferListener?.let {
      Toolkit.getDefaultToolkit().removeAWTEventListener(it)
      focusTransferListener = null
    }
    overlayClipListener?.let {
      Toolkit.getDefaultToolkit().removeAWTEventListener(it)
      overlayClipListener = null
    }
    overlayClipTimer.stop()
    lastOverlayClipShapes = emptyList()
    nativePeer?.updateOverlayClipRects(this, emptyList())
    nativeTextShortcutDispatcher?.let {
      KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(it)
      nativeTextShortcutDispatcher = null
    }
    setNativeTextInputFocusActive(false, force = true)
    listenersInstalled = false
  }

  private fun isEventFromHostWindow(event: KeyEvent): Boolean {
    val source = event.component ?: return false
    val hostWindow = SwingUtilities.getWindowAncestor(this) ?: return false
    return SwingUtilities.getWindowAncestor(source) == hostWindow
  }
}
