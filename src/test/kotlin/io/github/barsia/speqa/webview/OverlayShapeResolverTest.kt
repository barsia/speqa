// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package io.github.barsia.speqa.webview

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.barsia.speqa.webview.internal.host.NativeOverlayClipShape
import io.github.barsia.speqa.webview.internal.host.NativePoint
import io.github.barsia.speqa.webview.internal.host.OverlayBoundsExtractor
import java.awt.Component
import java.awt.Graphics
import java.awt.Insets
import java.awt.Rectangle
import javax.swing.JPanel
import javax.swing.border.Border
import javax.swing.border.EmptyBorder

class OverlayShapeResolverTest : BasePlatformTestCase() {

  fun `test registered resolver returning RoundedRect is honored`() {
    val resolver = OverlayShapeResolver { _, bounds ->
      OverlayClipShape.RoundedRect(bounds.x, bounds.y, bounds.width, bounds.height, 12.0)
    }
    OverlayShapeResolver.EP_NAME.point.registerExtension(resolver, testRootDisposable)

    val shape = OverlayBoundsExtractor.Default.resolveShape(JPanel(), Rectangle(5, 6, 50, 40))

    assertEquals(NativeOverlayClipShape.RoundedRect(5, 6, 50, 40, 12.0), shape)
  }

  fun `test registered resolver returning Rect is mapped to native rect`() {
    val resolver = OverlayShapeResolver { _, bounds ->
      OverlayClipShape.Rect(bounds.x + 1, bounds.y + 2, bounds.width - 3, bounds.height - 4)
    }
    OverlayShapeResolver.EP_NAME.point.registerExtension(resolver, testRootDisposable)

    val shape = OverlayBoundsExtractor.Default.resolveShape(JPanel(), Rectangle(10, 10, 100, 80))

    assertEquals(NativeOverlayClipShape.Rect(11, 12, 97, 76), shape)
  }

  fun `test registered resolver returning Polygon is mapped to native polygon`() {
    val polyPoints = listOf(
      OverlayClipShape.Point(0.0, 0.0),
      OverlayClipShape.Point(10.0, 0.0),
      OverlayClipShape.Point(5.0, 10.0),
    )
    val resolver = OverlayShapeResolver { _, _ -> OverlayClipShape.Polygon(polyPoints) }
    OverlayShapeResolver.EP_NAME.point.registerExtension(resolver, testRootDisposable)

    val shape = OverlayBoundsExtractor.Default.resolveShape(JPanel(), Rectangle(0, 0, 20, 20))

    val expected = NativeOverlayClipShape.Polygon(
      listOf(NativePoint(0.0, 0.0), NativePoint(10.0, 0.0), NativePoint(5.0, 10.0)),
    )
    assertEquals(expected, shape)
  }

  fun `test resolver returning null falls back to built-in default`() {
    val resolver = OverlayShapeResolver { _, _ -> null }
    OverlayShapeResolver.EP_NAME.point.registerExtension(resolver, testRootDisposable)

    val shape = OverlayBoundsExtractor.Default.resolveShape(
      JPanel().apply { border = EmptyBorder(2, 2, 2, 2) },
      Rectangle(0, 0, 20, 20),
    )

    assertEquals(NativeOverlayClipShape.Rect(0, 0, 20, 20), shape)
  }

  fun `test resolver throwing falls back to built-in default`() {
    val resolver = OverlayShapeResolver { _, _ -> throw RuntimeException("boom") }
    OverlayShapeResolver.EP_NAME.point.registerExtension(resolver, testRootDisposable)

    val component = JPanel().apply { border = FakeJBPopupShadowBorder() }
    val shape = OverlayBoundsExtractor.Default.resolveShape(component, Rectangle(0, 0, 100, 80))

    // Built-in detection kicks in: shadow border class name produces RoundedRect.
    assertTrue("expected RoundedRect from built-in fallback, got $shape", shape is NativeOverlayClipShape.RoundedRect)
    val rounded = shape as NativeOverlayClipShape.RoundedRect
    assertEquals(0, rounded.x)
    assertEquals(0, rounded.y)
    assertEquals(100, rounded.width)
    assertEquals(80, rounded.height)
  }

  fun `test first non-null resolver wins`() {
    val first = OverlayShapeResolver { _, _ -> null }
    val second = OverlayShapeResolver { _, bounds ->
      OverlayClipShape.RoundedRect(bounds.x, bounds.y, bounds.width, bounds.height, 7.0)
    }
    val third = OverlayShapeResolver { _, _ -> OverlayClipShape.Rect(0, 0, 0, 0) }
    OverlayShapeResolver.EP_NAME.point.registerExtension(first, testRootDisposable)
    OverlayShapeResolver.EP_NAME.point.registerExtension(second, testRootDisposable)
    OverlayShapeResolver.EP_NAME.point.registerExtension(third, testRootDisposable)

    val shape = OverlayBoundsExtractor.Default.resolveShape(JPanel(), Rectangle(1, 2, 30, 40))

    assertEquals(NativeOverlayClipShape.RoundedRect(1, 2, 30, 40, 7.0), shape)
  }

  private class FakeJBPopupShadowBorder : Border {
    override fun paintBorder(c: Component, g: Graphics, x: Int, y: Int, width: Int, height: Int) = Unit
    override fun getBorderInsets(c: Component): Insets = Insets(8, 8, 8, 8)
    override fun isBorderOpaque(): Boolean = false
  }
}
