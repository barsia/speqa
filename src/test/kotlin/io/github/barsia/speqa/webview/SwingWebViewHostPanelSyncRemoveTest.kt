package io.github.barsia.speqa.webview

import io.github.barsia.speqa.webview.internal.host.NativeOverlayClipShape
import io.github.barsia.speqa.webview.internal.host.NativeWebViewHostPeer
import java.awt.Component
import java.awt.event.ComponentEvent
import java.awt.event.ContainerEvent
import javax.swing.JLayeredPane
import javax.swing.JPanel
import javax.swing.JRootPane
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Coverage for the hide-transition fast path added in Task 2 of the refine-overlay-mask plan.
 * When an overlay component becomes invisible or is removed from the AWT hierarchy, the host
 * panel must update the native mask synchronously (no 40 ms debounce) so the residual mask hole
 * does not outlive the overlay on screen.
 */
class SwingWebViewHostPanelSyncRemoveTest {

  @Test
  fun `overlay COMPONENT_HIDDEN triggers synchronous mask update bypassing the debounce timer`() {
    val (panel, peer, job) = newHostedPanel()
    val overlay = installOverlay(panel)
    // Seed lastOverlayClipShapes with the visible overlay so the subsequent hide
    // produces a real transition (non-empty → empty) that must reach the peer.
    panel.flushDebouncedOverlayClipUpdateForTest()
    peer.calls.clear()

    overlay.isVisible = false
    val hidden = ComponentEvent(overlay, ComponentEvent.COMPONENT_HIDDEN)
    panel.routeOverlayEventForSource(hidden, overlay)

    assertFalse(
      "hide transition must skip the 40 ms debounce timer",
      panel.isOverlayClipTimerRunningForTest(),
    )
    assertEquals(
      listOf("updateOverlayClipRects:0:await"),
      peer.calls.filter { it.startsWith("updateOverlayClipRects:") },
    )
    job.cancel()
  }

  @Test
  fun `overlay COMPONENT_REMOVED triggers synchronous mask update bypassing the debounce timer`() {
    val (panel, peer, job) = newHostedPanel()
    val overlay = installOverlay(panel)
    panel.flushDebouncedOverlayClipUpdateForTest()
    peer.calls.clear()

    val layeredPane = panel.rootPane.layeredPane
    val removed = ContainerEvent(layeredPane, ContainerEvent.COMPONENT_REMOVED, overlay)
    layeredPane.remove(overlay)
    panel.routeOverlayEventForSource(removed, overlay)

    assertFalse(panel.isOverlayClipTimerRunningForTest())
    assertEquals(
      listOf("updateOverlayClipRects:0:await"),
      peer.calls.filter { it.startsWith("updateOverlayClipRects:") },
    )
    job.cancel()
  }

  @Test
  fun `overlay COMPONENT_SHOWN routes through the debounced timer instead of the sync path`() {
    val (panel, peer, job) = newHostedPanel()
    val overlay = installOverlay(panel)
    val callsBeforeShow = peer.calls.size

    val shown = ComponentEvent(overlay, ComponentEvent.COMPONENT_SHOWN)
    panel.routeOverlayEventForSource(shown, overlay)

    assertTrue(
      "show transition must arm the 40 ms debounce timer",
      panel.isOverlayClipTimerRunningForTest(),
    )
    // No synchronous peer call must occur in the same tick.
    assertEquals(callsBeforeShow, peer.calls.size)
    job.cancel()
  }

  @Test
  fun `recomputed overlay shape list unchanged from previous tick yields a no-op sync path`() {
    val (panel, peer, job) = newHostedPanel()
    val overlay = installOverlay(panel)
    panel.flushDebouncedOverlayClipUpdateForTest()
    peer.calls.clear()

    // Initial hide event commits the (now empty) shape list.
    overlay.isVisible = false
    val firstHide = ComponentEvent(overlay, ComponentEvent.COMPONENT_HIDDEN)
    panel.routeOverlayEventForSource(firstHide, overlay)
    val callsAfterFirstHide = peer.calls.toList()

    // Second hide event with the same Swing state must not push another peer call.
    val secondHide = ComponentEvent(overlay, ComponentEvent.COMPONENT_HIDDEN)
    panel.routeOverlayEventForSource(secondHide, overlay)

    assertEquals(callsAfterFirstHide, peer.calls)
    job.cancel()
  }

  // region helpers

  private data class HostFixture(
    val panel: SwingWebViewHostPanel,
    val peer: RecordingPeer,
    val job: Job,
  )

  private fun newHostedPanel(): HostFixture {
    val job = Job()
    val peer = RecordingPeer()
    val panel = SwingWebViewHostPanel(CoroutineScope(job), NoopFacade(), peer)
    val root = JRootPane().apply {
      setBounds(0, 0, 500, 300)
      layeredPane.setBounds(0, 0, 500, 300)
      contentPane.layout = null
      contentPane.setBounds(0, 0, 500, 300)
    }
    panel.setBounds(100, 60, 200, 120)
    root.contentPane.add(panel)
    panel.addNotify()
    // Drop the "attach" entry so each test asserts only the calls it triggers.
    peer.calls.clear()
    return HostFixture(panel, peer, job)
  }

  private fun installOverlay(panel: SwingWebViewHostPanel): JPanel {
    val overlay = JPanel().apply { setBounds(180, 80, 100, 40) }
    val layeredPane = panel.rootPane.layeredPane
    layeredPane.add(overlay)
    layeredPane.setLayer(overlay, JLayeredPane.POPUP_LAYER)
    return overlay
  }

  // endregion

  private class NoopFacade : WebViewFacade {
    override fun loadUrl(url: String) = Unit
    override fun loadHtml(html: String, baseUrl: String?) = Unit
    override suspend fun evaluateJavaScript(script: String): String? = null
    override fun close() = Unit
  }

  private class RecordingPeer : NativeWebViewHostPeer {
    val calls = mutableListOf<String>()

    override fun attach(host: Component): Boolean {
      calls += "attach"
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
  }
}
