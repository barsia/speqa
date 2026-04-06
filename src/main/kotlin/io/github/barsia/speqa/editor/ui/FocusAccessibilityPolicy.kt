package io.github.barsia.speqa.editor.ui

internal object FocusAccessibilityPolicy {
    fun titleTextCanFocus(isEditing: Boolean): Boolean = isEditing
}
