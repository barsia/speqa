package io.github.barsia.speqa.webview

import io.github.barsia.speqa.webview.internal.host.NativeOverlayClipShape
import io.github.barsia.speqa.webview.internal.host.NativePoint
import io.github.barsia.speqa.webview.internal.host.NativeWebViewHostPeer
import io.github.barsia.speqa.webview.internal.host.OverlayBoundsExtractor
import java.awt.Rectangle
import java.awt.Component
import java.awt.event.ComponentEvent
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.awt.image.BufferedImage
import javax.swing.JLayeredPane
import javax.swing.JPanel
import javax.swing.JRootPane
import javax.swing.border.EmptyBorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SwingWebViewHostPanelTest {
  @Test
  fun `focus requests delegate to the native peer`() {
    val peer = RecordingNativePeer()
    val job = Job()
    val panel = SwingWebViewHostPanel(CoroutineScope(job), NoopWebViewFacade(), peer)

    panel.requestWebViewFocus()
    panel.clearWebViewFocus()
    job.cancel()

    assertEquals(listOf("requestFocus", "clearFocus"), peer.calls)
    assertTrue(panel.isFocusable)
  }

  @Test
  fun `addNotify and removeNotify attach and detach the native peer`() {
    val peer = RecordingNativePeer()
    val job = Job()
    val panel = SwingWebViewHostPanel(CoroutineScope(job), NoopWebViewFacade(), peer)

    panel.addNotify()
    panel.removeNotify()
    job.cancel()

    assertEquals(listOf("attach", "detach", "updateOverlayClipRects:0"), peer.calls)
    assertEquals(panel, peer.hosts.single())
  }

  @Test
  fun `component events forward resize and visibility to the native peer`() {
    val peer = RecordingNativePeer()
    val job = Job()
    val panel = SwingWebViewHostPanel(CoroutineScope(job), NoopWebViewFacade(), peer)
    panel.addNotify()

    panel.dispatchEvent(ComponentEvent(panel, ComponentEvent.COMPONENT_RESIZED))
    panel.dispatchEvent(ComponentEvent(panel, ComponentEvent.COMPONENT_MOVED))
    panel.dispatchEvent(ComponentEvent(panel, ComponentEvent.COMPONENT_SHOWN))
    panel.dispatchEvent(ComponentEvent(panel, ComponentEvent.COMPONENT_HIDDEN))
    panel.removeNotify()
    job.cancel()

    assertEquals(
      listOf(
        "attach",
        "scheduleFrameUpdate",
        "scheduleFrameUpdate",
        "updateVisibility:false",
        "updateVisibility:true",
        "detach",
        "updateOverlayClipRects:0",
      ),
      peer.calls,
    )
  }

  @Test
  fun `native text input focus gates shortcut dispatch to the native peer`() {
    val peer = RecordingNativePeer()
    val job = Job()
    val focusStates = mutableListOf<Boolean>()
    val panel = SwingWebViewHostPanel(
      CoroutineScope(job),
      NoopWebViewFacade(),
      peer,
      focusStates::add,
    )
    val shortcut = KeyEvent(
      panel,
      KeyEvent.KEY_PRESSED,
      System.currentTimeMillis(),
      InputEvent.META_DOWN_MASK,
      KeyEvent.VK_V,
      KeyEvent.CHAR_UNDEFINED,
    )

    assertFalse(panel.dispatchNativeTextEditingShortcut(shortcut))
    panel.setNativeTextInputFocusActive(true)

    assertTrue(panel.dispatchNativeTextEditingShortcut(shortcut))
    val afterClear = KeyEvent(
      panel,
      KeyEvent.KEY_PRESSED,
      System.currentTimeMillis(),
      InputEvent.META_DOWN_MASK,
      KeyEvent.VK_V,
      KeyEvent.CHAR_UNDEFINED,
    )
    panel.clearWebViewFocus()

    assertFalse(panel.dispatchNativeTextEditingShortcut(afterClear))
    job.cancel()

    assertEquals(listOf("shortcut:86", "clearFocus"), peer.calls)
    assertEquals(listOf(true, false), focusStates)
    assertTrue(shortcut.isConsumed)
  }

  @Test
  fun `native text input focus gates JS text command dispatch to the native peer`() {
    val peer = RecordingNativePeer()
    val job = Job()
    val panel = SwingWebViewHostPanel(CoroutineScope(job), NoopWebViewFacade(), peer)

    assertFalse(panel.dispatchNativeTextEditingCommand("paste"))
    panel.setNativeTextInputFocusActive(true)

    assertTrue(panel.dispatchNativeTextEditingCommand("paste"))
    job.cancel()

    assertEquals(listOf("command:paste"), peer.calls)
  }

  @Test
  fun `native command suppresses transient focus loss from the web page`() {
    val peer = RecordingNativePeer()
    val job = Job()
    val focusStates = mutableListOf<Boolean>()
    val panel = SwingWebViewHostPanel(
      CoroutineScope(job),
      NoopWebViewFacade(),
      peer,
      focusStates::add,
    )
    val copyShortcut = KeyEvent(
      panel,
      KeyEvent.KEY_PRESSED,
      System.currentTimeMillis(),
      InputEvent.CTRL_DOWN_MASK,
      KeyEvent.VK_C,
      KeyEvent.CHAR_UNDEFINED,
    )
    val pasteShortcut = KeyEvent(
      panel,
      KeyEvent.KEY_PRESSED,
      System.currentTimeMillis(),
      InputEvent.CTRL_DOWN_MASK,
      KeyEvent.VK_V,
      KeyEvent.CHAR_UNDEFINED,
    )

    panel.setNativeTextInputFocusActive(true)

    assertTrue(panel.dispatchNativeTextEditingShortcut(copyShortcut))
    panel.setNativeTextInputFocusActive(false)
    assertTrue(panel.dispatchNativeTextEditingShortcut(pasteShortcut))
    panel.clearWebViewFocus()

    job.cancel()

    assertEquals(listOf("shortcut:67", "shortcut:86", "clearFocus"), peer.calls)
    assertEquals(listOf(true, false), focusStates)
  }


  @Test
  fun `native frame uses host origin and flipped AppKit y coordinate`() {
    val anchor = JPanel(null).apply { setBounds(0, 0, 500, 300) }
    val container = JPanel(null).apply { setBounds(10, 20, 400, 250) }
    val host = JPanel().apply { setBounds(30, 40, 200, 100) }
    anchor.add(container)
    container.add(host)

    val frame = SwingWebViewHostPanel.calculateNativeFrame(host, anchor)

    assertEquals(40.0, frame.x, 0.0)
    assertEquals(140.0, frame.y, 0.0)
    assertEquals(200.0, frame.width, 0.0)
    assertEquals(100.0, frame.height, 0.0)
  }

  @Test
  fun `windows bounds clip a host that extends beyond the anchor`() {
    val anchor = JPanel(null).apply { setBounds(0, 0, 300, 200) }
    val host = JPanel().apply { setBounds(250, 150, 100, 80) }
    anchor.add(host)

    val bounds = SwingWebViewHostPanel.calculateWindowsBounds(host, anchor)

    assertEquals(250, bounds.x)
    assertEquals(150, bounds.y)
    assertEquals(50, bounds.width)
    assertEquals(50, bounds.height)
  }

  @Test
  fun `windows bounds clip at visible trailing siblings in the same parent`() {
    val anchor = JPanel(null).apply { setBounds(0, 0, 500, 300) }
    val host = JPanel().apply { setBounds(50, 20, 300, 220) }
    val rightSibling = JPanel().apply { setBounds(250, 0, 40, 300) }
    val bottomSibling = JPanel().apply { setBounds(0, 160, 500, 40) }
    anchor.add(host)
    anchor.add(rightSibling)
    anchor.add(bottomSibling)

    val bounds = SwingWebViewHostPanel.calculateWindowsBounds(host, anchor)

    assertEquals(50, bounds.x)
    assertEquals(20, bounds.y)
    assertEquals(200, bounds.width)
    assertEquals(140, bounds.height)
  }

  @Test
  fun `windows bounds ignore invisible trailing siblings`() {
    val anchor = JPanel(null).apply { setBounds(0, 0, 500, 300) }
    val host = JPanel().apply { setBounds(50, 20, 300, 220) }
    val rightSibling = JPanel().apply {
      setBounds(250, 0, 40, 300)
      isVisible = false
    }
    anchor.add(host)
    anchor.add(rightSibling)

    val bounds = SwingWebViewHostPanel.calculateWindowsBounds(host, anchor)

    assertEquals(300, bounds.width)
    assertEquals(220, bounds.height)
  }

  @Test
  fun `snapshot image paints the latest offscreen webview pixels`() {
    val job = Job()
    val panel = SwingWebViewHostPanel(CoroutineScope(job), NoopWebViewFacade()).apply {
      setSize(2, 2)
      setSnapshotImage(
        width = 2,
        height = 2,
        pixels = intArrayOf(
          0xffff0000.toInt(),
          0xff00ff00.toInt(),
          0xff0000ff.toInt(),
          0xffffffff.toInt(),
        ),
      )
    }

    val painted = BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB)
    val graphics = painted.createGraphics()
    panel.paint(graphics)
    graphics.dispose()
    job.cancel()

    assertEquals(0xffff0000.toInt(), painted.getRGB(0, 0))
    assertEquals(0xff00ff00.toInt(), painted.getRGB(1, 0))
    assertEquals(0xff0000ff.toInt(), painted.getRGB(0, 1))
    assertEquals(0xffffffff.toInt(), painted.getRGB(1, 1))
  }

  @Test
  fun `invalid snapshot clears the painted image`() {
    val job = Job()
    val panel = SwingWebViewHostPanel(CoroutineScope(job), NoopWebViewFacade()).apply {
      setSize(1, 1)
      setSnapshotImage(1, 1, intArrayOf(0xffff0000.toInt()))
      setSnapshotImage(0, 1, intArrayOf(0xffffffff.toInt()))
    }

    val painted = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)
    val graphics = painted.createGraphics()
    panel.paint(graphics)
    graphics.dispose()
    job.cancel()

    assertEquals(0x00000000, painted.getRGB(0, 0))
  }

  @Test
  fun `calculates overlay clip rects above the native webview host`() {
    val job = Job()
    val root = testRootPane()
    val panel = SwingWebViewHostPanel(CoroutineScope(job), NoopWebViewFacade()).apply {
      setBounds(100, 60, 200, 120)
    }
    root.contentPane.add(panel)
    val overlay = JPanel().apply {
      setBounds(180, 80, 100, 40)
    }
    root.layeredPane.add(overlay)
    root.layeredPane.setLayer(overlay, JLayeredPane.POPUP_LAYER)

    val rects = panel.calculateOverlayClipRects()

    job.cancel()
    assertEquals(listOf(NativeOverlayClipShape.Rect(80, 20, 100, 40)), rects)
  }

  @Test
  fun `overlay clip rects ignore default layer and non overlapping components`() {
    val job = Job()
    val root = testRootPane()
    val panel = SwingWebViewHostPanel(CoroutineScope(job), NoopWebViewFacade()).apply {
      setBounds(100, 60, 200, 120)
    }
    root.contentPane.add(panel)
    val defaultLayerOverlay = JPanel().apply {
      setBounds(120, 80, 50, 40)
    }
    val nonOverlappingOverlay = JPanel().apply {
      setBounds(350, 80, 50, 40)
    }
    root.layeredPane.add(defaultLayerOverlay)
    root.layeredPane.setLayer(defaultLayerOverlay, JLayeredPane.DEFAULT_LAYER)
    root.layeredPane.add(nonOverlappingOverlay)
    root.layeredPane.setLayer(nonOverlappingOverlay, JLayeredPane.POPUP_LAYER)

    val rects = panel.calculateOverlayClipRects()

    job.cancel()
    assertEquals(emptyList<NativeOverlayClipShape>(), rects)
  }

  @Test
  fun `calculates nested overlay clip rects above the native webview host`() {
    val job = Job()
    val root = testRootPane()
    val panel = SwingWebViewHostPanel(CoroutineScope(job), NoopWebViewFacade()).apply {
      setBounds(100, 60, 200, 120)
    }
    root.contentPane.add(panel)
    val transparentSurface = JPanel(null).apply {
      isOpaque = false
      setBounds(0, 0, 500, 300)
    }
    val notification = JPanel().apply {
      setBounds(180, 80, 100, 40)
    }
    notification.add(JPanel().apply {
      setBounds(10, 10, 20, 20)
    })
    transparentSurface.add(notification)
    root.layeredPane.add(transparentSurface)
    root.layeredPane.setLayer(transparentSurface, JLayeredPane.POPUP_LAYER)

    val rects = panel.calculateOverlayClipRects()

    job.cancel()
    assertEquals(listOf(NativeOverlayClipShape.Rect(80, 20, 100, 40)), rects)
  }

  @Test
  fun `calculates glass pane overlay clip rects above the native webview host`() {
    val job = Job()
    val root = testRootPane()
    val panel = SwingWebViewHostPanel(CoroutineScope(job), NoopWebViewFacade()).apply {
      setBounds(100, 60, 200, 120)
    }
    root.contentPane.add(panel)
    val glassPane = JPanel(null).apply {
      isOpaque = false
      setBounds(0, 0, 500, 300)
    }
    val exceptionPanel = JPanel().apply {
      setBounds(90, 100, 140, 50)
    }
    glassPane.add(exceptionPanel)
    root.glassPane = glassPane
    glassPane.isVisible = true

    val rects = panel.calculateOverlayClipRects()

    job.cancel()
    assertEquals(listOf(NativeOverlayClipShape.Rect(0, 40, 130, 50)), rects)
  }

  @Test
  fun `overlay clip rect shrinks by JComponent insets for shadow borders`() {
    val job = Job()
    val root = testRootPane()
    val panel = SwingWebViewHostPanel(CoroutineScope(job), NoopWebViewFacade()).apply {
      setBounds(100, 60, 200, 120)
    }
    root.contentPane.add(panel)
    val overlay = JPanel().apply {
      setBounds(170, 70, 116, 56)
      border = EmptyBorder(8, 8, 8, 8)
    }
    root.layeredPane.add(overlay)
    root.layeredPane.setLayer(overlay, JLayeredPane.POPUP_LAYER)

    val rects = panel.calculateOverlayClipRects()

    job.cancel()
    assertEquals(listOf(NativeOverlayClipShape.Rect(78, 18, 100, 40)), rects)
  }

  @Test
  fun `merges overlapping overlay clip rects before applying native mask`() {
    val job = Job()
    val root = testRootPane()
    val panel = SwingWebViewHostPanel(CoroutineScope(job), NoopWebViewFacade()).apply {
      setBounds(100, 60, 200, 120)
    }
    root.contentPane.add(panel)
    val firstOverlay = JPanel().apply {
      setBounds(120, 80, 100, 40)
    }
    val secondOverlay = JPanel().apply {
      setBounds(180, 90, 100, 40)
    }
    root.layeredPane.add(firstOverlay)
    root.layeredPane.setLayer(firstOverlay, JLayeredPane.POPUP_LAYER)
    root.layeredPane.add(secondOverlay)
    root.layeredPane.setLayer(secondOverlay, JLayeredPane.POPUP_LAYER)

    val rects = panel.calculateOverlayClipRects()

    job.cancel()
    assertEquals(listOf(NativeOverlayClipShape.Rect(20, 20, 160, 50)), rects)
  }

  @Test
  fun `overlapping non-rect shapes collapse to bounding-box rect union`() {
    val job = Job()
    val root = testRootPane()
    val panel = SwingWebViewHostPanel(CoroutineScope(job), NoopWebViewFacade()).apply {
      setBounds(100, 60, 200, 120)
    }
    root.contentPane.add(panel)
    val firstOverlay = JPanel().apply {
      setBounds(120, 80, 100, 40)
    }
    val secondOverlay = JPanel().apply {
      setBounds(180, 90, 100, 40)
    }
    root.layeredPane.add(firstOverlay)
    root.layeredPane.setLayer(firstOverlay, JLayeredPane.POPUP_LAYER)
    root.layeredPane.add(secondOverlay)
    root.layeredPane.setLayer(secondOverlay, JLayeredPane.POPUP_LAYER)

    val roundedExtractor = object : OverlayBoundsExtractor {
      override fun extract(component: Component, boundsInRoot: Rectangle): Rectangle = boundsInRoot
      override fun resolveShape(component: Component, hostRelativeRect: Rectangle): NativeOverlayClipShape {
        return NativeOverlayClipShape.RoundedRect(
          hostRelativeRect.x,
          hostRelativeRect.y,
          hostRelativeRect.width,
          hostRelativeRect.height,
          6.0,
        )
      }
    }

    val rects = panel.calculateOverlayClipRects(roundedExtractor)

    job.cancel()
    // Two overlapping RoundedRect cutouts would otherwise render incorrectly under the macOS
    // even-odd CGPath mask (overlap inverts to "filled" and re-exposes the WKWebView) while
    // the per-shape hit-test still treats the overlap as overlay. Merging to a bounding-box
    // Rect keeps the mask and the hit-test consistent.
    assertEquals(listOf(NativeOverlayClipShape.Rect(20, 20, 160, 50)), rects)
  }

  @Test
  fun `non-rect shapes with overlapping bounding boxes but disjoint geometry are not merged`() {
    val job = Job()
    val root = testRootPane()
    val panel = SwingWebViewHostPanel(CoroutineScope(job), NoopWebViewFacade()).apply {
      setBounds(100, 60, 200, 120)
    }
    root.contentPane.add(panel)
    // Both overlays resolve to Polygon triangles whose bounding boxes overlap (one in the
    // upper-left half of the host-relative rect, one in the lower-right half, with shared bbox
    // coverage), but their actual triangular geometry is disjoint. This exercises the
    // Area-based intersection branch of shapesOverlap (bbox-only would falsely merge them).
    val firstOverlay = JPanel().apply {
      setBounds(120, 80, 100, 40)
    }
    val secondOverlay = JPanel().apply {
      setBounds(120, 80, 100, 40)
    }
    root.layeredPane.add(firstOverlay)
    root.layeredPane.setLayer(firstOverlay, JLayeredPane.POPUP_LAYER)
    root.layeredPane.add(secondOverlay)
    root.layeredPane.setLayer(secondOverlay, JLayeredPane.POPUP_LAYER)

    var resolveCall = 0
    val disjointTriangleExtractor = object : OverlayBoundsExtractor {
      override fun extract(component: Component, boundsInRoot: Rectangle): Rectangle = boundsInRoot
      override fun resolveShape(component: Component, hostRelativeRect: Rectangle): NativeOverlayClipShape {
        val r = hostRelativeRect
        val points = if (resolveCall++ == 0) {
          // Upper-left triangle spanning the full bbox: (r.x, r.y) → (r.x + r.width, r.y) →
          // (r.x, r.y + r.height). Its bbox equals the host-relative rect.
          listOf(
            NativePoint(r.x.toDouble(), r.y.toDouble()),
            NativePoint((r.x + r.width).toDouble(), r.y.toDouble()),
            NativePoint(r.x.toDouble(), (r.y + r.height).toDouble()),
          )
        }
        else {
          // Lower-right triangle shifted 2px off the shared diagonal so the two triangles are
          // geometrically disjoint, while its bbox still overlaps the first triangle's bbox.
          listOf(
            NativePoint((r.x + r.width).toDouble(), (r.y + r.height).toDouble()),
            NativePoint((r.x + r.width).toDouble(), (r.y + 2).toDouble()),
            NativePoint((r.x + 2).toDouble(), (r.y + r.height).toDouble()),
          )
        }
        return NativeOverlayClipShape.Polygon(points)
      }
    }

    val rects = panel.calculateOverlayClipRects(disjointTriangleExtractor)

    job.cancel()
    // Bounding-box-only intersection would falsely merge these into a single bounding-box rect,
    // hiding webview content across the entire union. The geometry-aware overlap check keeps
    // them distinct so the visual mask and hit-test remain accurate.
    assertEquals(2, rects.size)
    assertTrue(rects.all { it is NativeOverlayClipShape.Polygon })
  }

  @Test
  fun `non-overlapping non-rect shape is preserved`() {
    val job = Job()
    val root = testRootPane()
    val panel = SwingWebViewHostPanel(CoroutineScope(job), NoopWebViewFacade()).apply {
      setBounds(100, 60, 200, 120)
    }
    root.contentPane.add(panel)
    val overlay = JPanel().apply {
      setBounds(140, 80, 60, 40)
    }
    root.layeredPane.add(overlay)
    root.layeredPane.setLayer(overlay, JLayeredPane.POPUP_LAYER)

    val roundedExtractor = object : OverlayBoundsExtractor {
      override fun extract(component: Component, boundsInRoot: Rectangle): Rectangle = boundsInRoot
      override fun resolveShape(component: Component, hostRelativeRect: Rectangle): NativeOverlayClipShape {
        return NativeOverlayClipShape.RoundedRect(
          hostRelativeRect.x,
          hostRelativeRect.y,
          hostRelativeRect.width,
          hostRelativeRect.height,
          6.0,
        )
      }
    }

    val rects = panel.calculateOverlayClipRects(roundedExtractor)

    job.cancel()
    assertEquals(listOf(NativeOverlayClipShape.RoundedRect(40, 20, 60, 40, 6.0)), rects)
  }

  private class NoopWebViewFacade : WebViewFacade {
    override fun loadUrl(url: String) = Unit
    override fun loadHtml(html: String, baseUrl: String?) = Unit
    override suspend fun evaluateJavaScript(script: String): String? = null
    override fun close() = Unit
  }

  private class RecordingNativePeer : NativeWebViewHostPeer {
    val calls = mutableListOf<String>()
    val hosts = mutableListOf<Component>()

    override fun attach(host: Component): Boolean {
      calls += "attach"
      hosts += host
      return true
    }

    override fun detach() {
      calls += "detach"
    }

    override fun scheduleFrameUpdate(host: Component) {
      calls += "scheduleFrameUpdate"
    }

    override fun updateVisibility(host: Component, hidden: Boolean) {
      calls += "updateVisibility:$hidden"
    }

    override fun updateOverlayClipRects(
      host: Component,
      shapes: List<NativeOverlayClipShape>,
      awaitNativeCommit: Boolean,
    ) {
      val suffix = if (awaitNativeCommit) ":await" else ""
      calls += "updateOverlayClipRects:${shapes.size}$suffix"
    }

    override fun requestFocus() {
      calls += "requestFocus"
    }

    override fun clearFocus() {
      calls += "clearFocus"
    }

    override fun dispatchNativeTextEditingShortcut(event: KeyEvent): Boolean {
      calls += "shortcut:${event.keyCode}"
      event.consume()
      return true
    }

    override fun dispatchNativeTextEditingCommand(command: String): Boolean {
      calls += "command:$command"
      return true
    }
  }

  private fun testRootPane(): JRootPane = JRootPane().apply {
    setBounds(0, 0, 500, 300)
    layeredPane.setBounds(0, 0, 500, 300)
    contentPane.layout = null
    contentPane.setBounds(0, 0, 500, 300)
  }
}
