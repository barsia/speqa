// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package io.github.barsia.speqa.webview.internal.linux

import com.intellij.ui.jcef.JBCefOSRHandlerFactory
import org.cef.browser.CefBrowser
import org.cef.callback.CefDragData
import org.cef.handler.CefRenderHandler
import org.cef.handler.CefScreenInfo
import org.cef.misc.CefRange
import java.awt.Cursor
import java.awt.Point
import java.awt.Rectangle
import java.nio.ByteBuffer
import javax.swing.JComponent
import javax.swing.SwingUtilities
import org.jetbrains.annotations.ApiStatus

/**
 * Wraps the platform's default OSR render handler so that `onCursorChange` actually applies
 * the requested cursor to the JCEF Swing component. JBR's stock `JBCefOsrHandler` accepts
 * cursor change signals from Chromium but never propagates them to the AWT component, so
 * CSS `cursor: pointer` (and friends) have no visible effect.
 *
 * Drag-and-drop is intentionally NOT intercepted here — it requires more than cursor work
 * (drag-source state machine, dragSourceDragOver/dragSourceEndedAt calls) and is the subject
 * of a follow-up plan.
 */
@ApiStatus.Internal
internal class JcefCursorOsrHandlerFactory : JBCefOSRHandlerFactory {
  override fun createCefRenderHandler(component: JComponent): CefRenderHandler {
    val delegate = JBCefOSRHandlerFactory.DEFAULT.createCefRenderHandler(component)
    return DelegatingRenderHandler(delegate, component)
  }
}

private class DelegatingRenderHandler(
  private val delegate: CefRenderHandler,
  private val component: JComponent,
) : CefRenderHandler {

  override fun getViewRect(browser: CefBrowser): Rectangle = delegate.getViewRect(browser)

  override fun getScreenInfo(browser: CefBrowser, screenInfo: CefScreenInfo): Boolean =
    delegate.getScreenInfo(browser, screenInfo)

  override fun getScreenPoint(browser: CefBrowser, viewPoint: Point): Point =
    delegate.getScreenPoint(browser, viewPoint)

  override fun getDeviceScaleFactor(browser: CefBrowser): Double =
    delegate.getDeviceScaleFactor(browser)

  override fun onPopupShow(browser: CefBrowser, show: Boolean) {
    delegate.onPopupShow(browser, show)
  }

  override fun onPopupSize(browser: CefBrowser, size: Rectangle) {
    delegate.onPopupSize(browser, size)
  }

  override fun onPaint(
    browser: CefBrowser,
    popup: Boolean,
    dirtyRects: Array<Rectangle>,
    buffer: ByteBuffer,
    width: Int,
    height: Int,
  ) {
    delegate.onPaint(browser, popup, dirtyRects, buffer, width, height)
  }

  override fun onCursorChange(browser: CefBrowser, cursorType: Int): Boolean {
    val cursor = mapCefCursorToAwt(cursorType)
    SwingUtilities.invokeLater { component.cursor = cursor }
    // Returning true tells CEF "the host handled the cursor change" — JBR's default returns
    // false (or no-op true) anyway; we don't need to call delegate for this signal.
    return true
  }

  override fun startDragging(
    browser: CefBrowser,
    dragData: CefDragData,
    mask: Int,
    x: Int,
    y: Int,
  ): Boolean = delegate.startDragging(browser, dragData, mask, x, y)

  override fun updateDragCursor(browser: CefBrowser, operation: Int) {
    delegate.updateDragCursor(browser, operation)
  }

  override fun OnImeCompositionRangeChanged(
    browser: CefBrowser,
    selectedRange: CefRange,
    characterBounds: Array<Rectangle>,
  ) {
    delegate.OnImeCompositionRangeChanged(browser, selectedRange, characterBounds)
  }

  override fun OnTextSelectionChanged(
    browser: CefBrowser,
    selectedText: String,
    selectedRange: CefRange,
  ) {
    delegate.OnTextSelectionChanged(browser, selectedText, selectedRange)
  }
}

private fun mapCefCursorToAwt(cursorType: Int): Cursor {
  // cef_cursor_type_t values mirror Chromium's WebCursor. We map only the cursors that
  // actually appear in the SpeQA preview; anything else falls back to the default arrow.
  val awtType = when (cursorType) {
    0 -> Cursor.DEFAULT_CURSOR     // CT_POINTER
    1 -> Cursor.CROSSHAIR_CURSOR   // CT_CROSS
    2 -> Cursor.HAND_CURSOR        // CT_HAND
    3 -> Cursor.TEXT_CURSOR        // CT_IBEAM
    4 -> Cursor.WAIT_CURSOR        // CT_WAIT
    6 -> Cursor.E_RESIZE_CURSOR    // CT_EASTRESIZE
    7 -> Cursor.N_RESIZE_CURSOR    // CT_NORTHRESIZE
    8 -> Cursor.NE_RESIZE_CURSOR   // CT_NORTHEASTRESIZE
    9 -> Cursor.NW_RESIZE_CURSOR   // CT_NORTHWESTRESIZE
    10 -> Cursor.S_RESIZE_CURSOR   // CT_SOUTHRESIZE
    11 -> Cursor.SE_RESIZE_CURSOR  // CT_SOUTHEASTRESIZE
    12 -> Cursor.SW_RESIZE_CURSOR  // CT_SOUTHWESTRESIZE
    13 -> Cursor.W_RESIZE_CURSOR   // CT_WESTRESIZE
    14 -> Cursor.N_RESIZE_CURSOR   // CT_NORTHSOUTHRESIZE (closest predefined match)
    15 -> Cursor.E_RESIZE_CURSOR   // CT_EASTWESTRESIZE (closest predefined match)
    18 -> Cursor.E_RESIZE_CURSOR   // CT_COLUMNRESIZE
    19 -> Cursor.N_RESIZE_CURSOR   // CT_ROWRESIZE
    34 -> Cursor.MOVE_CURSOR       // CT_GRAB
    35 -> Cursor.MOVE_CURSOR       // CT_GRABBING
    else -> Cursor.DEFAULT_CURSOR
  }
  return Cursor.getPredefinedCursor(awtType)
}
