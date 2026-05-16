// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package io.github.barsia.speqa.webview.internal.mac

import io.github.barsia.speqa.webview.internal.host.NativeOverlayClipShape
import io.github.barsia.speqa.webview.internal.host.NativePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the Swing-top-left → AppKit-bottom-left coordinate flip used by
 * [io.github.barsia.speqa.webview.internal.mac.flipShapeForAppKit] before shapes are handed off
 * to the WKWebViewBridge. JNA-free: the native CAShapeLayer/NSBezierPath path itself
 * is exercised by manual macOS smoke checks, not these tests.
 */
class MacOverlayShapeFlipTest {

  @Test
  fun `Rect origin is mirrored across the host height`() {
    val flipped = flipShapeForAppKit(NativeOverlayClipShape.Rect(10, 20, 100, 30), hostHeight = 200)

    assertEquals(NativeOverlayClipShape.Rect(10, 150, 100, 30), flipped)
  }

  @Test
  fun `RoundedRect origin is mirrored and radius preserved`() {
    val flipped = flipShapeForAppKit(
      NativeOverlayClipShape.RoundedRect(5, 40, 80, 60, radius = 12.0),
      hostHeight = 200,
    )

    assertEquals(NativeOverlayClipShape.RoundedRect(5, 100, 80, 60, radius = 12.0), flipped)
  }

  @Test
  fun `Polygon points are mirrored individually`() {
    val flipped = flipShapeForAppKit(
      NativeOverlayClipShape.Polygon(
        listOf(NativePoint(0.0, 0.0), NativePoint(10.0, 0.0), NativePoint(5.0, 10.0)),
      ),
      hostHeight = 100,
    )

    assertTrue(flipped is NativeOverlayClipShape.Polygon)
    val polygon = flipped as NativeOverlayClipShape.Polygon
    assertEquals(listOf(NativePoint(0.0, 100.0), NativePoint(10.0, 100.0), NativePoint(5.0, 90.0)), polygon.points)
  }

  @Test
  fun `non-positive Rect is dropped`() {
    assertNull(flipShapeForAppKit(NativeOverlayClipShape.Rect(0, 0, 0, 10), hostHeight = 100))
    assertNull(flipShapeForAppKit(NativeOverlayClipShape.Rect(0, 0, 10, -1), hostHeight = 100))
  }

  @Test
  fun `non-positive RoundedRect is dropped`() {
    assertNull(
      flipShapeForAppKit(NativeOverlayClipShape.RoundedRect(0, 0, 0, 0, radius = 4.0), hostHeight = 100),
    )
  }

  @Test
  fun `empty Polygon is dropped`() {
    assertNull(flipShapeForAppKit(NativeOverlayClipShape.Polygon(emptyList()), hostHeight = 100))
  }
}
