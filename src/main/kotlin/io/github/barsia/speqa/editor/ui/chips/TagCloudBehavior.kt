package io.github.barsia.speqa.editor.ui.chips

enum class TagCloudDismissReason { Keyboard, Pointer }

/**
 * Keyboard dismissal restores focus to the anchor button; pointer dismissal
 * suppresses the focus ring instead (it would be visually distracting on click).
 */
fun shouldRestoreTagCloudAnchorFocus(reason: TagCloudDismissReason): Boolean {
    return reason == TagCloudDismissReason.Keyboard
}

/**
 * Show the anchor focus ring only when the anchor is focused **and** focus ring
 * is not currently suppressed (e.g. after a pointer-initiated dismissal).
 */
fun shouldShowTagCloudAnchorFocusRing(
    isFocused: Boolean,
    suppressFocusRing: Boolean,
): Boolean {
    return isFocused && !suppressFocusRing
}
