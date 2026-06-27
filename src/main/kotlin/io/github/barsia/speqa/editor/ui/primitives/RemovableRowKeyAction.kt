package io.github.barsia.speqa.editor.ui.primitives

import java.awt.event.KeyEvent

/**
 * Pure key-to-action mapping for a Model-A removable row/chip: Enter/Space run the primary
 * action, F2 edits (when [onEdit] is present), Delete/Backspace remove. Returns true when the
 * key was handled (caller should consume the event).
 */
fun removableRowKeyAction(
    keyCode: Int,
    onActivate: (() -> Unit)?,
    onEdit: (() -> Unit)?,
    onDelete: (() -> Unit)?,
): Boolean = when (keyCode) {
    KeyEvent.VK_ENTER, KeyEvent.VK_SPACE -> { onActivate?.invoke(); onActivate != null }
    KeyEvent.VK_F2 -> { onEdit?.invoke(); onEdit != null }
    KeyEvent.VK_DELETE, KeyEvent.VK_BACK_SPACE -> { onDelete?.invoke(); onDelete != null }
    else -> false
}
