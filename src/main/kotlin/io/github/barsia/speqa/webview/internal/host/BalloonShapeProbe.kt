// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package io.github.barsia.speqa.webview.internal.host

import com.intellij.util.ui.JBUI
import io.github.barsia.speqa.webview.internal.WebViewLogger
import java.awt.Component
import java.awt.Dimension
import java.awt.Rectangle
import java.lang.reflect.Field
import kotlin.math.cos
import kotlin.math.sin

/**
 * Best-effort precise shape detection for `com.intellij.ui.BalloonImpl` overlays.
 *
 * The visible balloon component in `JLayeredPane` is typically an inner class of `BalloonImpl`
 * (`BalloonImpl$MyComponent`, `BalloonImpl$ActionPanel`, ...). We follow the `this$0` chain to
 * find the owning balloon, then reflect on its private fields (`myShowPointer`, `myPosition`,
 * `myPointerSize`, `myCalloutShift`, `myCornerRadius`) to produce a [NativeOverlayClipShape] that
 * mirrors the painted geometry — rounded body plus arrow pointer when present — instead of the
 * generic shadow-border approximation used by [OverlayBoundsExtractor.Default].
 *
 * All reflection is wrapped in a single try/catch. Any failure returns `null` so the caller falls
 * back to the existing heuristic. This is intentional: `BalloonImpl` is internal IntelliJ Platform
 * API and field names may shift between IDE versions; the probe must degrade silently.
 */
internal object BalloonShapeProbe {
  /** Approximated points per rounded corner of the balloon body polygon. */
  private const val CORNER_SEGMENTS = 8

  /** Maximum depth when walking `this$0` outer-class references to find the BalloonImpl. */
  private const val MAX_OUTER_DEPTH = 8

  /**
   * Returns a precise overlay clip shape for [component] when it is a `BalloonImpl`-owned
   * subcomponent, or `null` when the component is unrelated or reflection fails.
   *
   * [hostBoundsInRoot] is the rectangle of the visible balloon in host-relative coordinates,
   * as already shrunk by [OverlayBoundsExtractor.extract].
   */
  fun probe(component: Component, hostBoundsInRoot: Rectangle): NativeOverlayClipShape? {
    return try {
      val balloon = findEnclosingBalloon(component) ?: return null
      buildShape(balloon, hostBoundsInRoot)
    }
    catch (t: Throwable) {
      WebViewLogger.LOG.debug("BalloonShapeProbe.probe failed; falling back", t)
      null
    }
  }

  private fun findEnclosingBalloon(component: Component): Any? {
    if (isBalloonImplClass(component.javaClass)) return component
    var current: Any = component
    var depth = 0
    while (depth < MAX_OUTER_DEPTH) {
      val outerField = findOuterReferenceField(current.javaClass) ?: return null
      outerField.isAccessible = true
      val next = outerField.get(current) ?: return null
      if (isBalloonImplClass(next.javaClass)) return next
      current = next
      depth++
    }
    return null
  }

  private fun isBalloonImplClass(cls: Class<*>): Boolean {
    // Match by simple name so the probe works against the real
    // `com.intellij.ui.BalloonImpl` and the test fixture interchangeably.
    return cls.simpleName == "BalloonImpl"
  }

  private fun findOuterReferenceField(cls: Class<*>): Field? {
    var c: Class<*>? = cls
    while (c != null) {
      for (field in c.declaredFields) {
        if (field.name.startsWith("this$")) return field
      }
      c = c.superclass
    }
    return null
  }

  private fun buildShape(balloon: Any, rect: Rectangle): NativeOverlayClipShape {
    val showPointer = readBooleanField(balloon, "myShowPointer") ?: false
    // BalloonImpl.myCornerRadius is -1 by default (sentinel meaning "compute from style").
    // Treat any non-positive value as "unknown" and fall back to the platform default radius
    // so the CGPath mask receives a valid, non-negative radius.
    val cornerRadius = readIntField(balloon, "myCornerRadius")
      ?.takeIf { it > 0 }
      ?.toDouble()
      ?: OverlayBoundsExtractor.Default.DEFAULT_ROUNDED_RADIUS

    if (!showPointer) {
      return NativeOverlayClipShape.RoundedRect(
        x = rect.x,
        y = rect.y,
        width = rect.width,
        height = rect.height,
        radius = cornerRadius,
      )
    }

    val direction = readArrowDirection(balloon)
    val pointerSize = readPointerSize(balloon)
    val calloutShift = readIntField(balloon, "myCalloutShift") ?: 0
    val points = buildBalloonPolygon(rect, cornerRadius, direction, pointerSize, calloutShift)
    if (points.size < 3) {
      return NativeOverlayClipShape.RoundedRect(
        x = rect.x,
        y = rect.y,
        width = rect.width,
        height = rect.height,
        radius = cornerRadius,
      )
    }
    return NativeOverlayClipShape.Polygon(points)
  }

  /** Cardinal direction the arrow points toward (i.e. the side of the body it sticks out of). */
  private enum class ArrowDirection { Down, Up, Right, Left }

  private fun readArrowDirection(balloon: Any): ArrowDirection {
    // `Balloon.Position` enum values: above, below, atLeft, atRight. The position names the side
    // of the target the balloon sits on, so the arrow points the opposite direction: a balloon
    // *above* the target has an arrow pointing *down*.
    //
    // Read the enum's `name` rather than `toString()`: an IntelliJ-side override (e.g. for a
    // localized label) would silently break the match and default the direction.
    val position = readField(balloon, "myPosition") ?: return ArrowDirection.Down
    val name = (position as? Enum<*>)?.name ?: return ArrowDirection.Down
    return when (name.lowercase()) {
      "above" -> ArrowDirection.Down
      "below" -> ArrowDirection.Up
      "atleft" -> ArrowDirection.Right
      "atright" -> ArrowDirection.Left
      else -> ArrowDirection.Down
    }
  }

  private fun readPointerSize(balloon: Any): Dimension {
    val raw = readField(balloon, "myPointerSize") as? Dimension
    if (raw != null && raw.width > 0 && raw.height > 0) return Dimension(raw.width, raw.height)
    return Dimension(JBUI.scale(8), JBUI.scale(6))
  }

  private fun readField(target: Any, name: String): Any? {
    var c: Class<*>? = target.javaClass
    while (c != null) {
      try {
        val field = c.getDeclaredField(name)
        field.isAccessible = true
        return field.get(target)
      }
      catch (_: NoSuchFieldException) {
        c = c.superclass
      }
    }
    return null
  }

  private fun readBooleanField(target: Any, name: String): Boolean? = readField(target, name) as? Boolean
  private fun readIntField(target: Any, name: String): Int? = readField(target, name) as? Int

  /**
   * Builds a polygon outlining the balloon body (rounded rectangle) with an arrow triangle
   * extruded on the appropriate edge.
   *
   * The [rect] represents the entire painted region including the arrow extrusion, since
   * [OverlayBoundsExtractor.extract] already shrunk the component bounds by shadow insets. The
   * body therefore occupies a sub-rectangle inset by the pointer size on the arrow side.
   */
  private fun buildBalloonPolygon(
    rect: Rectangle,
    cornerRadius: Double,
    direction: ArrowDirection,
    pointerSize: Dimension,
    calloutShift: Int,
  ): List<NativePoint> {
    val pointerThickness = when (direction) {
      ArrowDirection.Up, ArrowDirection.Down -> pointerSize.height
      ArrowDirection.Left, ArrowDirection.Right -> pointerSize.width
    }.coerceAtLeast(0)
    val pointerBaseWidth = when (direction) {
      ArrowDirection.Up, ArrowDirection.Down -> pointerSize.width
      ArrowDirection.Left, ArrowDirection.Right -> pointerSize.height
    }.coerceAtLeast(0)

    val bodyLeft = rect.x + if (direction == ArrowDirection.Left) pointerThickness else 0
    val bodyTop = rect.y + if (direction == ArrowDirection.Up) pointerThickness else 0
    val bodyRight = rect.x + rect.width - if (direction == ArrowDirection.Right) pointerThickness else 0
    val bodyBottom = rect.y + rect.height - if (direction == ArrowDirection.Down) pointerThickness else 0
    val bodyWidth = bodyRight - bodyLeft
    val bodyHeight = bodyBottom - bodyTop
    if (bodyWidth <= 0 || bodyHeight <= 0) return emptyList()

    val r = cornerRadius.coerceIn(0.0, minOf(bodyWidth, bodyHeight) / 2.0)

    // If the straight section of the arrowed edge cannot hold the triangle base between the two
    // corner arcs, the polygon would self-intersect. Bail out so the caller falls back to a plain
    // RoundedRect.
    val arrowEdgeLength = when (direction) {
      ArrowDirection.Up, ArrowDirection.Down -> bodyWidth
      ArrowDirection.Left, ArrowDirection.Right -> bodyHeight
    }
    if (arrowEdgeLength - 2.0 * r < pointerBaseWidth) return emptyList()

    val points = ArrayList<NativePoint>(CORNER_SEGMENTS * 4 + 8)

    // Body outline, traversed clockwise starting at top-left corner end.
    // Top edge → top-right corner → right edge → bottom-right corner → bottom edge →
    // bottom-left corner → left edge → top-left corner.

    // Top edge start (after top-left corner)
    points += NativePoint(bodyLeft + r, bodyTop.toDouble())

    // If arrow on top edge, inject triangle along the top run.
    if (direction == ArrowDirection.Up) {
      addArrowAlongEdge(
        points = points,
        from = NativePoint(bodyLeft + r, bodyTop.toDouble()),
        to = NativePoint(bodyRight - r, bodyTop.toDouble()),
        tip = arrowTipForEdge(rect, direction, calloutShift, pointerBaseWidth, bodyLeft, bodyTop, bodyRight, bodyBottom, r),
        baseWidth = pointerBaseWidth.toDouble(),
      )
    }

    // Top-right corner arc
    addCornerArc(
      points = points,
      cx = bodyRight - r, cy = bodyTop + r, r = r,
      startDeg = 270.0, endDeg = 360.0,
    )

    // Right edge
    if (direction == ArrowDirection.Right) {
      addArrowAlongEdge(
        points = points,
        from = NativePoint(bodyRight.toDouble(), bodyTop + r),
        to = NativePoint(bodyRight.toDouble(), bodyBottom - r),
        tip = arrowTipForEdge(rect, direction, calloutShift, pointerBaseWidth, bodyLeft, bodyTop, bodyRight, bodyBottom, r),
        baseWidth = pointerBaseWidth.toDouble(),
      )
    }

    // Bottom-right corner arc
    addCornerArc(
      points = points,
      cx = bodyRight - r, cy = bodyBottom - r, r = r,
      startDeg = 0.0, endDeg = 90.0,
    )

    // Bottom edge
    if (direction == ArrowDirection.Down) {
      addArrowAlongEdge(
        points = points,
        from = NativePoint(bodyRight - r, bodyBottom.toDouble()),
        to = NativePoint(bodyLeft + r, bodyBottom.toDouble()),
        tip = arrowTipForEdge(rect, direction, calloutShift, pointerBaseWidth, bodyLeft, bodyTop, bodyRight, bodyBottom, r),
        baseWidth = pointerBaseWidth.toDouble(),
      )
    }

    // Bottom-left corner arc
    addCornerArc(
      points = points,
      cx = bodyLeft + r, cy = bodyBottom - r, r = r,
      startDeg = 90.0, endDeg = 180.0,
    )

    // Left edge
    if (direction == ArrowDirection.Left) {
      addArrowAlongEdge(
        points = points,
        from = NativePoint(bodyLeft.toDouble(), bodyBottom - r),
        to = NativePoint(bodyLeft.toDouble(), bodyTop + r),
        tip = arrowTipForEdge(rect, direction, calloutShift, pointerBaseWidth, bodyLeft, bodyTop, bodyRight, bodyBottom, r),
        baseWidth = pointerBaseWidth.toDouble(),
      )
    }

    // Top-left corner arc
    addCornerArc(
      points = points,
      cx = bodyLeft + r, cy = bodyTop + r, r = r,
      startDeg = 180.0, endDeg = 270.0,
    )

    return points
  }

  /**
   * Returns the apex of the arrow triangle for [direction], with the tip clamped so the triangle
   * base lies inside the straight portion of the body edge (between the two corner arcs). Without
   * this clamp, narrow popups with large corner radius would inject base vertices inside a corner
   * arc and produce a self-intersecting polygon. The caller pre-checks that the straight section
   * is long enough to hold the base (see `arrowEdgeLength - 2*r < pointerBaseWidth` guard above).
   */
  private fun arrowTipForEdge(
    rect: Rectangle,
    direction: ArrowDirection,
    calloutShift: Int,
    pointerBaseWidth: Int,
    bodyLeft: Int,
    bodyTop: Int,
    bodyRight: Int,
    bodyBottom: Int,
    cornerRadius: Double,
  ): NativePoint {
    val halfBase = pointerBaseWidth / 2.0
    return when (direction) {
      ArrowDirection.Up -> {
        val cx = (rect.x + rect.width / 2.0 + calloutShift).coerceIn(
          bodyLeft + cornerRadius + halfBase, bodyRight - cornerRadius - halfBase,
        )
        NativePoint(cx, rect.y.toDouble())
      }
      ArrowDirection.Down -> {
        val cx = (rect.x + rect.width / 2.0 + calloutShift).coerceIn(
          bodyLeft + cornerRadius + halfBase, bodyRight - cornerRadius - halfBase,
        )
        NativePoint(cx, (rect.y + rect.height).toDouble())
      }
      ArrowDirection.Left -> {
        val cy = (rect.y + rect.height / 2.0 + calloutShift).coerceIn(
          bodyTop + cornerRadius + halfBase, bodyBottom - cornerRadius - halfBase,
        )
        NativePoint(rect.x.toDouble(), cy)
      }
      ArrowDirection.Right -> {
        val cy = (rect.y + rect.height / 2.0 + calloutShift).coerceIn(
          bodyTop + cornerRadius + halfBase, bodyBottom - cornerRadius - halfBase,
        )
        NativePoint((rect.x + rect.width).toDouble(), cy)
      }
    }
  }

  /**
   * Injects an arrow triangle between [from] and [to] (assumed colinear along one body edge).
   * The triangle base sits on the edge centred under [tip]; [tip] is the apex of the arrow.
   */
  private fun addArrowAlongEdge(
    points: MutableList<NativePoint>,
    from: NativePoint,
    to: NativePoint,
    tip: NativePoint,
    baseWidth: Double,
  ) {
    val halfBase = baseWidth / 2.0
    val horizontal = from.y == to.y
    if (horizontal) {
      val edgeY = from.y
      val tipX = tip.x
      val dir = if (to.x >= from.x) 1.0 else -1.0
      val baseStart = NativePoint(tipX - dir * halfBase, edgeY)
      val baseEnd = NativePoint(tipX + dir * halfBase, edgeY)
      points += baseStart
      points += NativePoint(tip.x, tip.y)
      points += baseEnd
    }
    else {
      val edgeX = from.x
      val tipY = tip.y
      val dir = if (to.y >= from.y) 1.0 else -1.0
      val baseStart = NativePoint(edgeX, tipY - dir * halfBase)
      val baseEnd = NativePoint(edgeX, tipY + dir * halfBase)
      points += baseStart
      points += NativePoint(tip.x, tip.y)
      points += baseEnd
    }
  }

  /**
   * Appends a quarter-circle arc approximation to [points]. Angles are in degrees with 0° pointing
   * right (positive X) and 90° pointing down (positive Y) — matching AWT's screen-coordinate
   * convention where Y grows downward.
   */
  private fun addCornerArc(
    points: MutableList<NativePoint>,
    cx: Number,
    cy: Number,
    r: Double,
    startDeg: Double,
    endDeg: Double,
  ) {
    if (r <= 0.0) {
      points += NativePoint(cx.toDouble(), cy.toDouble())
      return
    }
    val cxD = cx.toDouble()
    val cyD = cy.toDouble()
    val step = (endDeg - startDeg) / CORNER_SEGMENTS
    var i = 0
    while (i <= CORNER_SEGMENTS) {
      val rad = Math.toRadians(startDeg + step * i)
      points += NativePoint(cxD + r * cos(rad), cyD + r * sin(rad))
      i++
    }
  }
}
