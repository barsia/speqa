// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package io.github.barsia.speqa.webview

import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus
import java.awt.Component
import java.awt.Rectangle

/**
 * Public sealed type describing how an overlay above the native WebView host should be cut
 * out of the WebView's compositing layer. Coordinates are relative to the WebView host's
 * top-left corner (Swing/AWT convention; the native peer applies any AppKit y-flip).
 */
@ApiStatus.Experimental
sealed interface OverlayClipShape {
  data class Rect(val x: Int, val y: Int, val width: Int, val height: Int) : OverlayClipShape

  data class RoundedRect(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val radius: Double,
  ) : OverlayClipShape

  data class Polygon(val points: List<Point>) : OverlayClipShape

  data class Point(val x: Double, val y: Double)
}

/**
 * Extension point for describing how a Swing overlay component overlapping the native
 * WebView host should be clipped. Implementations inspect the overlay and return an
 * [OverlayClipShape] describing the cutout, or `null` to defer to the next resolver / the
 * built-in default (a plain [OverlayClipShape.Rect], or a rounded rectangle for known
 * shadow-painting borders such as `JBPopupShadowBorder`).
 *
 * Register via `<overlayShapeResolver implementation="..."/>` under
 * `<extensions defaultExtensionNs="io.github.barsia.speqa">` in your plugin descriptor.
 *
 * Exceptions thrown from [resolve] are caught and logged - a misbehaving resolver will
 * not break overlay clipping; the host falls back to the built-in detection.
 */
@ApiStatus.Experimental
fun interface OverlayShapeResolver {
  fun resolve(component: Component, boundsInHost: Rectangle): OverlayClipShape?

  companion object {
    val EP_NAME: ExtensionPointName<OverlayShapeResolver> =
      ExtensionPointName.create("io.github.barsia.speqa.overlayShapeResolver")
  }
}
