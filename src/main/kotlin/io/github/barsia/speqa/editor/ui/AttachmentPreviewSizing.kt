package io.github.barsia.speqa.editor.ui

import androidx.compose.ui.unit.IntSize

internal fun calculateAttachmentPreviewSize(
    imageSize: IntSize,
    maxWidthPx: Int,
    maxHeightPx: Int,
): IntSize {
    if (imageSize.width <= 0 || imageSize.height <= 0) {
        return IntSize(maxWidthPx, maxHeightPx)
    }

    val widthScale = maxWidthPx.toFloat() / imageSize.width
    val heightScale = maxHeightPx.toFloat() / imageSize.height
    val scale = minOf(1f, widthScale, heightScale)

    return IntSize(
        width = (imageSize.width * scale).toInt().coerceAtLeast(1),
        height = (imageSize.height * scale).toInt().coerceAtLeast(1),
    )
}
