// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package io.github.barsia.speqa.webview.internal.host

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeOverlayClipShapeContainsTest {
  @Test
  fun `rect contains points inside and excludes outside`() {
    val rect = NativeOverlayClipShape.Rect(10, 20, 100, 80)

    assertTrue(rect.contains(10.0, 20.0))
    assertTrue(rect.contains(50.0, 60.0))
    assertTrue(rect.contains(109.999, 99.999))
    assertFalse(rect.contains(9.999, 60.0))
    assertFalse(rect.contains(110.0, 60.0))
    assertFalse(rect.contains(50.0, 19.999))
    assertFalse(rect.contains(50.0, 100.0))
  }

  @Test
  fun `rounded rect excludes points in cut corners`() {
    val shape = NativeOverlayClipShape.RoundedRect(0, 0, 100, 100, radius = 20.0)

    assertTrue(shape.contains(50.0, 50.0))
    assertTrue(shape.contains(20.0, 5.0))
    assertTrue(shape.contains(5.0, 20.0))
    // Top-left corner: distance from (20,20) inner center to (2,2) = ~25.4 > 20 → excluded
    assertFalse(shape.contains(2.0, 2.0))
    // Top-right corner
    assertFalse(shape.contains(98.0, 2.0))
    // Bottom-right corner
    assertFalse(shape.contains(98.0, 98.0))
    // Bottom-left corner
    assertFalse(shape.contains(2.0, 98.0))
  }

  @Test
  fun `rounded rect with zero radius behaves like rect`() {
    val shape = NativeOverlayClipShape.RoundedRect(0, 0, 50, 50, radius = 0.0)

    assertTrue(shape.contains(0.0, 0.0))
    assertTrue(shape.contains(49.0, 49.0))
    assertFalse(shape.contains(-0.1, 0.0))
    assertFalse(shape.contains(50.0, 49.0))
  }

  @Test
  fun `rounded rect clamps oversized radius to half min dimension`() {
    val shape = NativeOverlayClipShape.RoundedRect(0, 0, 40, 20, radius = 1000.0)

    // With radius clamped to 10 (half of height), full corner curve applies
    assertTrue(shape.contains(20.0, 10.0))
    // (0,0) is inside AABB but corner center is (10,10), distance = ~14.14 > 10
    assertFalse(shape.contains(0.0, 0.0))
  }

  @Test
  fun `polygon contains uses ray cast for concavities`() {
    // L-shape:
    //   (0,0) → (20,0) → (20,10) → (10,10) → (10,20) → (0,20) → close
    val poly = NativeOverlayClipShape.Polygon(
      listOf(
        NativePoint(0.0, 0.0),
        NativePoint(20.0, 0.0),
        NativePoint(20.0, 10.0),
        NativePoint(10.0, 10.0),
        NativePoint(10.0, 20.0),
        NativePoint(0.0, 20.0),
      ),
    )

    assertTrue(poly.contains(5.0, 5.0))
    assertTrue(poly.contains(15.0, 5.0))
    assertTrue(poly.contains(5.0, 15.0))
    // Concavity (15, 15) — inside AABB but outside L-shape
    assertFalse(poly.contains(15.0, 15.0))
    // Outside entirely
    assertFalse(poly.contains(25.0, 5.0))
    assertFalse(poly.contains(-1.0, 5.0))
  }

  @Test
  fun `polygon with fewer than three points is never inside`() {
    val empty = NativeOverlayClipShape.Polygon(emptyList())
    val one = NativeOverlayClipShape.Polygon(listOf(NativePoint(0.0, 0.0)))
    val two = NativeOverlayClipShape.Polygon(listOf(NativePoint(0.0, 0.0), NativePoint(10.0, 10.0)))

    assertFalse(empty.contains(0.0, 0.0))
    assertFalse(one.contains(0.0, 0.0))
    assertFalse(two.contains(5.0, 5.0))
  }
}
