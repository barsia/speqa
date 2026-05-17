// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package io.github.barsia.speqa.webview.internal.linux

import io.github.barsia.speqa.webview.SwingWebViewHostPanel
import io.github.barsia.speqa.webview.internal.host.NativeWebViewHostPeer
import java.awt.Component
import java.awt.event.InputEvent
import java.awt.event.MouseEvent
import javax.swing.SwingUtilities
import javax.swing.event.MouseInputAdapter

internal class LinuxWaylandSnapshotWebViewHostPeer(
  private val facade: LinuxWebKitWebViewFacade,
) : NativeWebViewHostPeer {

  private var attached = false
  private var lastAppliedFrame: AppliedFrame? = null
  private var snapshotHost: SwingWebViewHostPanel? = null
  private var mouseListener: MouseInputAdapter? = null
  private var wheelListener: java.awt.event.MouseWheelListener? = null
  private var keyDispatcher: java.awt.KeyEventDispatcher? = null
  private var lastScale: Double = 1.0

  override fun attach(host: Component): Boolean {
    val hostPanel = host as? SwingWebViewHostPanel ?: return false
    snapshotHost = hostPanel
    facade.setSnapshotHandler { width, height, pixels ->
      hostPanel.setSnapshotImage(width, height, pixels)
    }
    facade.attachOffscreen()
    attached = true
    lastAppliedFrame = null

    installInputListeners(hostPanel)
    hostPanel.isFocusable = true
    hostPanel.focusTraversalKeysEnabled = false

    scheduleFrameUpdate(host)
    facade.setHidden(false)
    SwingUtilities.invokeLater { scheduleFrameUpdate(host) }
    return true
  }

  override fun detach() {
    if (!attached) return
    val host = snapshotHost
    if (host != null && mouseListener != null) {
      host.removeMouseListener(mouseListener)
      host.removeMouseMotionListener(mouseListener)
    }
    mouseListener = null
    if (host != null && wheelListener != null) {
      host.removeMouseWheelListener(wheelListener)
    }
    wheelListener = null
    keyDispatcher?.let {
      java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(it)
    }
    keyDispatcher = null
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
    val scale = host.graphicsConfiguration?.defaultTransform?.scaleX?.takeIf { it > 0.0 } ?: 1.0
    lastScale = scale
    facade.setBounds(0, 0, host.width, host.height, scale)
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

  private fun installInputListeners(host: SwingWebViewHostPanel) {
    val adapter = object : MouseInputAdapter() {
      override fun mousePressed(e: MouseEvent) {
        facade.dispatchMouseButton(
          x = e.x.toDouble() * lastScale,
          y = e.y.toDouble() * lastScale,
          button = awtButtonToGdk(e.button),
          modifierState = awtModifiersToGdk(e.modifiersEx),
          isPress = true,
        )
        host.requestFocusInWindow()
      }

      override fun mouseReleased(e: MouseEvent) {
        facade.dispatchMouseButton(
          x = e.x.toDouble() * lastScale,
          y = e.y.toDouble() * lastScale,
          button = awtButtonToGdk(e.button),
          modifierState = awtModifiersToGdk(e.modifiersEx),
          isPress = false,
        )
      }

      override fun mouseMoved(e: MouseEvent) {
        facade.dispatchMouseMotion(
          x = e.x.toDouble() * lastScale,
          y = e.y.toDouble() * lastScale,
          modifierState = awtModifiersToGdk(e.modifiersEx),
        )
      }

      override fun mouseDragged(e: MouseEvent) {
        facade.dispatchMouseMotion(
          x = e.x.toDouble() * lastScale,
          y = e.y.toDouble() * lastScale,
          modifierState = awtModifiersToGdk(e.modifiersEx),
        )
      }
    }
    mouseListener = adapter
    host.addMouseListener(adapter)
    host.addMouseMotionListener(adapter)

    val wheelAdapter = java.awt.event.MouseWheelListener { e ->
      // 40 px per notch matches the IDE editor's typical scroll step.
      // Shift+wheel = horizontal scroll, matching browser convention.
      val scrollStep = 40.0
      val deltaX = if (e.isShiftDown) e.preciseWheelRotation * scrollStep else 0.0
      val deltaY = if (e.isShiftDown) 0.0 else e.preciseWheelRotation * scrollStep
      facade.dispatchMouseScroll(
        x = e.x.toDouble() * lastScale,
        y = e.y.toDouble() * lastScale,
        deltaX = deltaX,
        deltaY = deltaY,
        modifierState = awtModifiersToGdk(e.modifiersEx),
      )
    }
    wheelListener = wheelAdapter
    host.addMouseWheelListener(wheelAdapter)

    val keyDispatcher = java.awt.KeyEventDispatcher { event ->
      if (event.component !== host && !javax.swing.SwingUtilities.isDescendingFrom(event.component, host)) {
        return@KeyEventDispatcher false
      }
      val keyval = AwtToGtkKeyMap.gdkKeyval(event.keyCode, event.keyChar)
      if (keyval == 0) return@KeyEventDispatcher false
      val isPress = when (event.id) {
        java.awt.event.KeyEvent.KEY_PRESSED -> true
        java.awt.event.KeyEvent.KEY_RELEASED -> false
        else -> return@KeyEventDispatcher false
      }
      facade.dispatchKey(
        keyval = keyval,
        modifierState = awtModifiersToGdk(event.modifiersEx),
        isPress = isPress,
      )
      true
    }
    this.keyDispatcher = keyDispatcher
    java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(keyDispatcher)
  }

  private fun awtButtonToGdk(awtButton: Int): Int = when (awtButton) {
    MouseEvent.BUTTON1 -> 1
    MouseEvent.BUTTON2 -> 2
    MouseEvent.BUTTON3 -> 3
    else -> 1
  }

  private fun awtModifiersToGdk(modifiersEx: Int): Int {
    var state = 0
    if ((modifiersEx and InputEvent.SHIFT_DOWN_MASK) != 0) state = state or GDK_SHIFT_MASK
    if ((modifiersEx and InputEvent.CTRL_DOWN_MASK) != 0) state = state or GDK_CONTROL_MASK
    if ((modifiersEx and InputEvent.ALT_DOWN_MASK) != 0) state = state or GDK_MOD1_MASK
    if ((modifiersEx and InputEvent.META_DOWN_MASK) != 0) state = state or GDK_META_MASK
    if ((modifiersEx and InputEvent.BUTTON1_DOWN_MASK) != 0) state = state or GDK_BUTTON1_MASK
    if ((modifiersEx and InputEvent.BUTTON2_DOWN_MASK) != 0) state = state or GDK_BUTTON2_MASK
    if ((modifiersEx and InputEvent.BUTTON3_DOWN_MASK) != 0) state = state or GDK_BUTTON3_MASK
    return state
  }

  companion object {
    private const val GDK_SHIFT_MASK = 1 shl 0
    private const val GDK_CONTROL_MASK = 1 shl 2
    private const val GDK_MOD1_MASK = 1 shl 3
    private const val GDK_META_MASK = 1 shl 28
    private const val GDK_BUTTON1_MASK = 1 shl 8
    private const val GDK_BUTTON2_MASK = 1 shl 9
    private const val GDK_BUTTON3_MASK = 1 shl 10
  }

  private data class AppliedFrame(
    val width: Int,
    val height: Int,
  )
}
