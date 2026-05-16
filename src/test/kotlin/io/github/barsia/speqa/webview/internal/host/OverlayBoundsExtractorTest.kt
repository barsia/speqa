// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package io.github.barsia.speqa.webview.internal.host

import java.awt.Canvas
import java.awt.Rectangle
import javax.swing.JPanel
import javax.swing.border.EmptyBorder
import javax.swing.border.Border
import java.awt.Component
import java.awt.Graphics
import java.awt.Insets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayBoundsExtractorTest {
  @Test
  fun `zero inset JComponent returns bounds unchanged`() {
    val component = JPanel()
    val bounds = Rectangle(10, 20, 100, 80)

    val result = OverlayBoundsExtractor.Default.extract(component, bounds)

    assertEquals(bounds, result)
  }

  @Test
  fun `shadow style insets shrink bounds on all sides`() {
    val component = JPanel().apply { border = EmptyBorder(8, 8, 8, 8) }
    val bounds = Rectangle(10, 20, 100, 80)

    val result = OverlayBoundsExtractor.Default.extract(component, bounds)

    assertEquals(Rectangle(18, 28, 84, 64), result)
  }

  @Test
  fun `asymmetric insets shrink bounds per edge`() {
    val component = JPanel().apply { border = EmptyBorder(4, 2, 6, 3) }
    val bounds = Rectangle(50, 60, 100, 80)

    val result = OverlayBoundsExtractor.Default.extract(component, bounds)

    assertEquals(Rectangle(52, 64, 95, 70), result)
  }

  @Test
  fun `insets larger than bounds fall back to original`() {
    val component = JPanel().apply { border = EmptyBorder(100, 100, 100, 100) }
    val bounds = Rectangle(0, 0, 50, 50)

    val result = OverlayBoundsExtractor.Default.extract(component, bounds)

    assertEquals(bounds, result)
  }

  @Test
  fun `non JComponent returns bounds unchanged`() {
    val component = Canvas()
    val bounds = Rectangle(10, 20, 100, 80)

    val result = OverlayBoundsExtractor.Default.extract(component, bounds)

    assertEquals(bounds, result)
  }

  @Test
  fun `resolveShape returns Rect for plain JPanel`() {
    val component = JPanel()
    val rect = Rectangle(5, 6, 50, 40)

    val shape = OverlayBoundsExtractor.Default.resolveShape(component, rect)

    assertEquals(NativeOverlayClipShape.Rect(5, 6, 50, 40), shape)
  }

  @Test
  fun `resolveShape returns Rect for non JComponent`() {
    val component = Canvas()
    val rect = Rectangle(0, 0, 20, 20)

    val shape = OverlayBoundsExtractor.Default.resolveShape(component, rect)

    assertEquals(NativeOverlayClipShape.Rect(0, 0, 20, 20), shape)
  }

  @Test
  fun `resolveShape returns RoundedRect when border class name matches shadow border hint`() {
    val component = JPanel().apply { border = FakeJBPopupShadowBorder() }
    val rect = Rectangle(10, 20, 100, 80)

    val shape = OverlayBoundsExtractor.Default.resolveShape(component, rect)

    assertTrue("expected RoundedRect, got $shape", shape is NativeOverlayClipShape.RoundedRect)
    val rounded = shape as NativeOverlayClipShape.RoundedRect
    assertEquals(10, rounded.x)
    assertEquals(20, rounded.y)
    assertEquals(100, rounded.width)
    assertEquals(80, rounded.height)
    assertEquals(OverlayBoundsExtractor.Default.DEFAULT_ROUNDED_RADIUS, rounded.radius, 0.0)
  }

  @Test
  fun `resolveShape falls back to Rect when border has unrelated class name`() {
    val component = JPanel().apply { border = EmptyBorder(4, 4, 4, 4) }
    val rect = Rectangle(0, 0, 20, 20)

    val shape = OverlayBoundsExtractor.Default.resolveShape(component, rect)

    assertEquals(NativeOverlayClipShape.Rect(0, 0, 20, 20), shape)
  }

  @Test
  fun `resolveShape delegates to BalloonShapeProbe before the border heuristic`() {
    // A balloon-like component with a shadow border must resolve via BalloonShapeProbe (precise
    // polygon/rounded body) rather than the generic JBPopupShadowBorder→RoundedRect fallback.
    // Locks the ordering of the two branches in OverlayBoundsExtractor.Default.resolveShape.
    val balloon = BalloonImpl(showPointer = true)
    val component = balloon.MyComponent().apply { border = FakeJBPopupShadowBorder() }
    val rect = Rectangle(0, 0, 200, 100)

    val shape = OverlayBoundsExtractor.Default.resolveShape(component, rect)

    assertTrue("expected Polygon from BalloonShapeProbe, got $shape", shape is NativeOverlayClipShape.Polygon)
  }

  private class FakeJBPopupShadowBorder : Border {
    override fun paintBorder(c: Component, g: Graphics, x: Int, y: Int, width: Int, height: Int) = Unit
    override fun getBorderInsets(c: Component): Insets = Insets(8, 8, 8, 8)
    override fun isBorderOpaque(): Boolean = false
  }

  /** Minimal fixture mirroring `com.intellij.ui.BalloonImpl` so [BalloonShapeProbe] recognises it. */
  @Suppress("unused")
  private class BalloonImpl(showPointer: Boolean) {
    @JvmField val myShowPointer: Boolean = showPointer
    @JvmField val myPosition: Position = Position.above
    @JvmField val myPointerSize: java.awt.Dimension = java.awt.Dimension(10, 8)
    @JvmField val myCalloutShift: Int = 0
    @JvmField val myCornerRadius: Int = 8

    @Suppress("unused")
    enum class Position { above }

    inner class MyComponent : javax.swing.JComponent()
  }
}
