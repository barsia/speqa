// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package io.github.barsia.speqa.webview.internal.host

import io.github.barsia.speqa.webview.internal.WebViewLogger
import java.util.concurrent.atomic.AtomicReference

internal object NativeOverlayClippingPolicy {
  const val ENABLED_PROPERTY = "speqa.webview.overlay.clipping"
  const val DIAGNOSTICS_PROPERTY = "speqa.webview.overlay.clipping.diagnostics"

  private val runtimeDisableReason = AtomicReference<String?>(null)

  fun isEnabled(): Boolean {
    return propertyEnabled(ENABLED_PROPERTY, default = true) && runtimeDisableReason.get() == null
  }

  fun isDiagnosticsEnabled(): Boolean {
    return propertyEnabled(DIAGNOSTICS_PROPERTY, default = false)
  }

  fun disableForSession(reason: String, throwable: Throwable? = null) {
    if (runtimeDisableReason.compareAndSet(null, reason)) {
      if (throwable != null) {
        WebViewLogger.LOG.warn("Disabled native WebView overlay clipping for this session: $reason", throwable)
      }
      else {
        WebViewLogger.LOG.warn("Disabled native WebView overlay clipping for this session: $reason")
      }
    }
  }

  fun runtimeDisabledReason(): String? = runtimeDisableReason.get()

  internal fun resetRuntimeDisableForTests() {
    runtimeDisableReason.set(null)
  }

  fun logShapes(stage: String, shapes: List<NativeOverlayClipShape>) {
    if (!isDiagnosticsEnabled()) return
    val formatted = shapes.joinToString(prefix = "[", postfix = "]") { shape -> describeShape(shape) }
    WebViewLogger.LOG.info("Native WebView overlay clipping $stage: count=${shapes.size}, shapes=$formatted")
  }

  private fun describeShape(shape: NativeOverlayClipShape): String {
    return when (shape) {
      is NativeOverlayClipShape.Rect ->
        "rect(${shape.x},${shape.y} ${shape.width}x${shape.height})"
      is NativeOverlayClipShape.RoundedRect ->
        "roundedRect(${shape.x},${shape.y} ${shape.width}x${shape.height} r=${shape.radius})"
      is NativeOverlayClipShape.Polygon ->
        "polygon(points=${shape.points.size}, bounds=${shape.bounds})"
    }
  }

  private fun propertyEnabled(name: String, default: Boolean): Boolean {
    return System.getProperty(name)?.toBooleanStrictOrNull() ?: default
  }
}
