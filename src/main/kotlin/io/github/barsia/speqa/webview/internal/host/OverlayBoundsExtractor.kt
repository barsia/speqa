// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package io.github.barsia.speqa.webview.internal.host

import com.intellij.openapi.application.ApplicationManager
import com.intellij.util.ui.JBUI
import io.github.barsia.speqa.webview.OverlayClipShape
import io.github.barsia.speqa.webview.OverlayShapeResolver
import io.github.barsia.speqa.webview.internal.WebViewLogger
import java.awt.Component
import java.awt.Rectangle
import javax.swing.JComponent

/**
 * Extracts the visually meaningful bounds of an overlay component for native clip mask
 * computation. Swing borders (notably `JBPopupShadowBorder`) report their shadow padding via
 * [JComponent.getInsets]; treating the full bounding box as the cutout produces a white
 * rectangular halo around the shadow. Shrinking by the reported insets aligns the cutout with
 * the painted content.
 */
internal interface OverlayBoundsExtractor {
  fun extract(component: Component, boundsInRoot: Rectangle): Rectangle

  /**
   * Returns a native clip shape for the given component at [hostRelativeRect]. Defaults to a
   * pure rectangle; rounded-border components (e.g. `JBPopupShadowBorder`) produce a
   * [NativeOverlayClipShape.RoundedRect] so the AppKit cutout mirrors the painted rounded corners.
   */
  fun resolveShape(component: Component, hostRelativeRect: Rectangle): NativeOverlayClipShape {
    return NativeOverlayClipShape.Rect(
      hostRelativeRect.x,
      hostRelativeRect.y,
      hostRelativeRect.width,
      hostRelativeRect.height,
    )
  }

  object Default : OverlayBoundsExtractor {
    /** Visual corner radius (in pixels) used as a sane default for shadow-bordered popups. */
    val DEFAULT_ROUNDED_RADIUS: Double get() = JBUI.scale(8).toDouble()

    /**
     * Border class names that paint a rounded popup body with shadow insets. Detected by name
     * via reflection so we do not hard-pin to a specific IntelliJ platform version. Extend this
     * set sparingly; third-party callers should register an `OverlayShapeResolver` instead.
     */
    private val ROUNDED_BORDER_CLASS_HINTS = listOf(
      "JBPopupShadowBorder",
      "PopupShadowBorder",
      "BalloonShadowBorder",
    )

    override fun extract(component: Component, boundsInRoot: Rectangle): Rectangle {
      val insets = (component as? JComponent)?.insets ?: return boundsInRoot
      if (insets.top == 0 && insets.left == 0 && insets.bottom == 0 && insets.right == 0) {
        return boundsInRoot
      }
      val shrunkWidth = boundsInRoot.width - insets.left - insets.right
      val shrunkHeight = boundsInRoot.height - insets.top - insets.bottom
      if (shrunkWidth <= 0 || shrunkHeight <= 0) return boundsInRoot
      return Rectangle(
        boundsInRoot.x + insets.left,
        boundsInRoot.y + insets.top,
        shrunkWidth,
        shrunkHeight,
      )
    }

    override fun resolveShape(component: Component, hostRelativeRect: Rectangle): NativeOverlayClipShape {
      val rect = NativeOverlayClipShape.Rect(
        hostRelativeRect.x,
        hostRelativeRect.y,
        hostRelativeRect.width,
        hostRelativeRect.height,
      )

      val resolved = resolveFromExtensions(component, hostRelativeRect)
      if (resolved != null) return resolved

      // Precise BalloonImpl probe runs before the generic border heuristic so balloons with an
      // arrow pointer return a polygon (rounded body + arrow triangle) instead of the rectangular
      // approximation that leaves white halos around the painted shadow.
      val balloonShape = BalloonShapeProbe.probe(component, hostRelativeRect)
      if (balloonShape != null) return balloonShape

      if (component !is JComponent) return rect
      val borderClassName = try {
        component.border?.javaClass?.simpleName
      }
      catch (t: Throwable) {
        WebViewLogger.LOG.debug("Failed to inspect overlay border class for shape resolution", t)
        null
      } ?: return rect

      val isRoundedShadowBorder = ROUNDED_BORDER_CLASS_HINTS.any { hint -> borderClassName.contains(hint) }
      if (!isRoundedShadowBorder) return rect

      return NativeOverlayClipShape.RoundedRect(
        x = hostRelativeRect.x,
        y = hostRelativeRect.y,
        width = hostRelativeRect.width,
        height = hostRelativeRect.height,
        radius = DEFAULT_ROUNDED_RADIUS,
      )
    }

    private fun resolveFromExtensions(component: Component, hostRelativeRect: Rectangle): NativeOverlayClipShape? {
      // The EP only resolves when the IntelliJ platform is initialized (i.e. not during plain
      // JUnit tests of the extractor). Outside the platform we silently skip the lookup.
      val app = ApplicationManager.getApplication() ?: return null
      if (!app.extensionArea.hasExtensionPoint(OverlayShapeResolver.EP_NAME)) return null
      val resolvers = try {
        OverlayShapeResolver.EP_NAME.extensionList
      }
      catch (t: Throwable) {
        WebViewLogger.LOG.debug("OverlayShapeResolver extension list unavailable", t)
        return null
      }
      for (resolver in resolvers) {
        val shape = try {
          resolver.resolve(component, hostRelativeRect)
        }
        catch (t: Throwable) {
          WebViewLogger.LOG.warn("OverlayShapeResolver ${resolver.javaClass.name} threw; ignoring", t)
          null
        } ?: continue
        return shape.toNative()
      }
      return null
    }

    private fun OverlayClipShape.toNative(): NativeOverlayClipShape = when (this) {
      is OverlayClipShape.Rect -> NativeOverlayClipShape.Rect(x, y, width, height)
      is OverlayClipShape.RoundedRect -> NativeOverlayClipShape.RoundedRect(x, y, width, height, radius)
      is OverlayClipShape.Polygon -> NativeOverlayClipShape.Polygon(
        points.map { NativePoint(it.x, it.y) },
      )
    }
  }
}
