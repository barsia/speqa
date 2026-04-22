package io.github.barsia.speqa.editor.ui.steps

/**
 * Bounds of a sibling card (one that is not being dragged) in the container's
 * vertical coordinate space. [top] is the card's upper edge, [height] is the
 * card's vertical size; [originalIndex] is where the card sits in the
 * non-dragged list (0-based, ascending).
 */
data class SiblingBounds(
    val originalIndex: Int,
    val top: Int,
    val height: Int,
)

/**
 * Pure drag target index calculator for a vertical reorder container.
 *
 * Flip rule: for every sibling, the dragged card must cross a [thresholdFraction]
 * of the sibling's height past the sibling's leading edge before the neighbor
 * flip commits.
 *   - For a sibling **above** the dragged card, the dragged center must move
 *     above `sibling.top + sibling.height * (1 - thresholdFraction)` to flip past it.
 *   - For a sibling **below** the dragged card, the dragged center must move
 *     below `sibling.top + sibling.height * thresholdFraction` to flip past it.
 *
 * @param draggedCenterY center Y of the dragged card in container coordinates.
 * @param siblings bounds of every card in the container except the dragged one.
 *   Order within the list does not matter; [SiblingBounds.originalIndex] carries
 *   the position.
 * @param originalIndex index of the dragged card in the pre-drag list.
 * @param thresholdFraction how deep into a neighbor's bounds the dragged center
 *   must travel to flip past it. Default 0.7.
 * @return the target index at which the dragged card would land if released now.
 *   Range: 0..(siblings.size).
 */
fun calculateTargetIndex(
    draggedCenterY: Float,
    siblings: List<SiblingBounds>,
    originalIndex: Int,
    thresholdFraction: Float = 0.7f,
): Int {
    if (siblings.isEmpty()) return originalIndex

    var target = originalIndex

    for (sibling in siblings) {
        if (sibling.originalIndex < originalIndex) {
            val flipBoundary = sibling.top + sibling.height * (1f - thresholdFraction)
            if (draggedCenterY < flipBoundary) {
                target = minOf(target, sibling.originalIndex)
            }
        } else if (sibling.originalIndex > originalIndex) {
            val flipBoundary = sibling.top + sibling.height * thresholdFraction
            if (draggedCenterY > flipBoundary) {
                target = maxOf(target, sibling.originalIndex)
            }
        }
    }

    return target
}
