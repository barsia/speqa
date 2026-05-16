// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package io.github.barsia.speqa.webview.internal.host

import java.awt.Dimension
import java.awt.Rectangle
import javax.swing.JComponent
import javax.swing.JPanel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BalloonShapeProbeTest {

  @Test
  fun `probe returns null for plain JPanel`() {
    val shape = BalloonShapeProbe.probe(JPanel(), Rectangle(0, 0, 100, 80))
    assertNull(shape)
  }

  @Test
  fun `probe returns RoundedRect when myShowPointer is false`() {
    val balloon = BalloonImpl(showPointer = false, position = FakePosition.above, cornerRadius = 12)
    val component = balloon.MyComponent()
    val rect = Rectangle(10, 20, 100, 80)

    val shape = BalloonShapeProbe.probe(component, rect)

    assertNotNull(shape)
    assertTrue("expected RoundedRect, got $shape", shape is NativeOverlayClipShape.RoundedRect)
    val rounded = shape as NativeOverlayClipShape.RoundedRect
    assertEquals(10, rounded.x)
    assertEquals(20, rounded.y)
    assertEquals(100, rounded.width)
    assertEquals(80, rounded.height)
    assertEquals(12.0, rounded.radius, 0.0)
  }

  @Test
  fun `probe returns Polygon when myShowPointer is true`() {
    val balloon = BalloonImpl(showPointer = true, position = FakePosition.above)
    val component = balloon.MyComponent()
    val rect = Rectangle(0, 0, 200, 100)

    val shape = BalloonShapeProbe.probe(component, rect)

    assertNotNull(shape)
    assertTrue("expected Polygon, got $shape", shape is NativeOverlayClipShape.Polygon)
    val polygon = shape as NativeOverlayClipShape.Polygon
    assertTrue("polygon should have many points", polygon.points.size >= 6)
  }

  @Test
  fun `probe handles all four arrow directions and tip lies at expected coordinates`() {
    val rect = Rectangle(0, 0, 200, 100)
    val pointerSize = Dimension(10, 8)

    // For each direction, the arrow's apex must be exactly at the centre of the rect on the
    // outer edge of the painted region. Asserting the exact (x, y) pair — not just an edge
    // match — catches regressions where the arrow triangle is silently omitted (corner-arc
    // points alone happen to satisfy a single-coordinate assertion).
    assertHasArrowTip(balloonPolygon(FakePosition.above, rect, pointerSize), NativePoint(100.0, 100.0))
    assertHasArrowTip(balloonPolygon(FakePosition.below, rect, pointerSize), NativePoint(100.0, 0.0))
    assertHasArrowTip(balloonPolygon(FakePosition.atLeft, rect, pointerSize), NativePoint(200.0, 50.0))
    assertHasArrowTip(balloonPolygon(FakePosition.atRight, rect, pointerSize), NativePoint(0.0, 50.0))
  }

  @Test
  fun `probe falls back to RoundedRect when body too narrow to hold arrow base between corners`() {
    // Body width (40) minus 2 * cornerRadius (16) = 24, smaller than the pointer base (30): the
    // arrow triangle cannot be inscribed between the corner arcs without self-intersecting, so
    // the probe must fall back to a plain RoundedRect.
    val balloon = BalloonImpl(
      showPointer = true,
      position = FakePosition.above,
      pointerSize = Dimension(30, 8),
      cornerRadius = 8,
    )
    val rect = Rectangle(0, 0, 40, 50)

    val shape = BalloonShapeProbe.probe(balloon.MyComponent(), rect)

    assertNotNull(shape)
    assertTrue("expected RoundedRect fallback, got $shape", shape is NativeOverlayClipShape.RoundedRect)
  }

  private fun assertHasArrowTip(polygon: NativeOverlayClipShape.Polygon, expected: NativePoint) {
    val match = polygon.points.any { it.x == expected.x && it.y == expected.y }
    assertTrue("polygon $polygon missing expected arrow tip $expected", match)
  }

  @Test
  fun `probe gracefully handles balloon with missing fields`() {
    val balloon = BareBalloonScope.BalloonImpl()
    val shape = BalloonShapeProbe.probe(balloon.MyComponent(), Rectangle(0, 0, 100, 80))
    // The bare BalloonImpl matches the class-name predicate but lacks all expected fields. The
    // probe should treat missing fields as defaults (no pointer) and produce a RoundedRect.
    assertNotNull(shape)
    assertTrue(shape is NativeOverlayClipShape.RoundedRect)
  }

  @Test
  fun `probe ignores components that are not enclosed by a BalloonImpl`() {
    val unrelated = NotABalloon()
    val shape = BalloonShapeProbe.probe(unrelated.MyComponent(), Rectangle(0, 0, 100, 80))
    assertNull(shape)
  }

  private fun balloonPolygon(
    position: FakePosition,
    rect: Rectangle,
    pointerSize: Dimension,
  ): NativeOverlayClipShape.Polygon {
    val balloon = BalloonImpl(showPointer = true, position = position, pointerSize = pointerSize)
    val shape = BalloonShapeProbe.probe(balloon.MyComponent(), rect)
    assertNotNull(shape)
    assertTrue(shape is NativeOverlayClipShape.Polygon)
    return shape as NativeOverlayClipShape.Polygon
  }

  @Suppress("unused")
  private enum class FakePosition { above, below, atLeft, atRight }

  /**
   * Test fixture mimicking `com.intellij.ui.BalloonImpl`. Field names match the reflection
   * targets in [BalloonShapeProbe]. The inner class plays the role of `BalloonImpl$MyComponent`.
   */
  @Suppress("unused")
  private class BalloonImpl(
    showPointer: Boolean,
    position: FakePosition,
    pointerSize: Dimension = Dimension(8, 6),
    calloutShift: Int = 0,
    cornerRadius: Int = 8,
  ) {
    @JvmField val myShowPointer: Boolean = showPointer
    @JvmField val myPosition: FakePosition = position
    @JvmField val myPointerSize: Dimension = pointerSize
    @JvmField val myCalloutShift: Int = calloutShift
    @JvmField val myCornerRadius: Int = cornerRadius

    inner class MyComponent : JComponent()
  }

  /** Nested scope to host a second class also named [BalloonImpl] (matches probe predicate). */
  private object BareBalloonScope {
    class BalloonImpl {
      inner class MyComponent : JComponent()
    }
  }

  private class NotABalloon {
    inner class MyComponent : JComponent()
  }
}
