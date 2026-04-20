package io.github.barsia.speqa.editor.ui

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize

internal fun calculateRichTooltipPosition(
    windowSize: IntSize,
    anchorBounds: IntRect,
    popupContentSize: IntSize,
    gapPx: Int,
    marginPx: Int,
): IntOffset {
    val below = IntOffset(
        x = clampHorizontal(anchorBounds.left, popupContentSize.width, windowSize.width, marginPx),
        y = anchorBounds.bottom + gapPx,
    )
    if (fitsVertically(below.y, popupContentSize.height, windowSize.height, marginPx) &&
        !rectsOverlap(anchorBounds, below.asRect(popupContentSize))
    ) {
        return below
    }

    val above = IntOffset(
        x = clampHorizontal(anchorBounds.left, popupContentSize.width, windowSize.width, marginPx),
        y = anchorBounds.top - gapPx - popupContentSize.height,
    )
    if (above.y >= marginPx && !rectsOverlap(anchorBounds, above.asRect(popupContentSize))) {
        return above
    }

    val right = IntOffset(
        x = anchorBounds.right + gapPx,
        y = clampVertical(
            desiredTop = anchorBounds.top + (anchorBounds.height - popupContentSize.height) / 2,
            popupHeight = popupContentSize.height,
            windowHeight = windowSize.height,
            marginPx = marginPx,
        ),
    )
    if (fitsHorizontally(right.x, popupContentSize.width, windowSize.width, marginPx) &&
        !rectsOverlap(anchorBounds, right.asRect(popupContentSize))
    ) {
        return right
    }

    val left = IntOffset(
        x = anchorBounds.left - gapPx - popupContentSize.width,
        y = clampVertical(
            desiredTop = anchorBounds.top + (anchorBounds.height - popupContentSize.height) / 2,
            popupHeight = popupContentSize.height,
            windowHeight = windowSize.height,
            marginPx = marginPx,
        ),
    )
    if (left.x >= marginPx && !rectsOverlap(anchorBounds, left.asRect(popupContentSize))) {
        return left
    }

    return IntOffset(
        x = clampHorizontal(anchorBounds.left, popupContentSize.width, windowSize.width, marginPx),
        y = clampVertical(anchorBounds.bottom + gapPx, popupContentSize.height, windowSize.height, marginPx),
    )
}

internal fun rectsOverlap(anchor: IntRect, popup: IntRect): Boolean {
    return anchor.left < popup.right &&
        anchor.right > popup.left &&
        anchor.top < popup.bottom &&
        anchor.bottom > popup.top
}

private fun IntOffset.asRect(size: IntSize): IntRect = IntRect(x, y, x + size.width, y + size.height)

private fun fitsHorizontally(left: Int, popupWidth: Int, windowWidth: Int, marginPx: Int): Boolean {
    return left >= marginPx && left + popupWidth <= windowWidth - marginPx
}

private fun fitsVertically(top: Int, popupHeight: Int, windowHeight: Int, marginPx: Int): Boolean {
    return top >= marginPx && top + popupHeight <= windowHeight - marginPx
}

private fun clampHorizontal(desiredLeft: Int, popupWidth: Int, windowWidth: Int, marginPx: Int): Int {
    return desiredLeft.coerceIn(marginPx, (windowWidth - popupWidth - marginPx).coerceAtLeast(marginPx))
}

private fun clampVertical(desiredTop: Int, popupHeight: Int, windowHeight: Int, marginPx: Int): Int {
    return desiredTop.coerceIn(marginPx, (windowHeight - popupHeight - marginPx).coerceAtLeast(marginPx))
}
