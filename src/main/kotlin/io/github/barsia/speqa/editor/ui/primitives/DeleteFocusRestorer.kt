package io.github.barsia.speqa.editor.ui.primitives

import javax.swing.JComponent
import javax.swing.SwingUtilities

/** Where keyboard focus should move after a list item is deleted. */
internal sealed class FocusTarget {
    data class Item(val index: Int) : FocusTarget()
    object AddButton : FocusTarget()
}

/**
 * Pure decision used by [DeleteFocusRestorer]:
 *  - only item in the list → focus the add-button;
 *  - deleted last item → focus the previous item;
 *  - otherwise → focus the item that has taken the deleted slot.
 */
internal fun nextFocusTargetAfterDelete(deletedIndex: Int, sizeBefore: Int): FocusTarget {
    if (sizeBefore <= 1) return FocusTarget.AddButton
    return if (deletedIndex == sizeBefore - 1) {
        FocusTarget.Item(deletedIndex - 1)
    } else {
        FocusTarget.Item(deletedIndex)
    }
}

/**
 * Swing wrapper over [nextFocusTargetAfterDelete]. Callers register the item
 * components and an add-button, then invoke [onDeleted] from the row-removal
 * handler. Focus is moved via [SwingUtilities.invokeLater] to let layout
 * revalidate before [JComponent.requestFocusInWindow] fires.
 */
class DeleteFocusRestorer(
    private val itemProvider: (Int) -> JComponent?,
    private val addButton: JComponent?,
) {
    fun onDeleted(deletedIndex: Int, sizeBefore: Int) {
        val target = nextFocusTargetAfterDelete(deletedIndex, sizeBefore)
        SwingUtilities.invokeLater {
            when (target) {
                is FocusTarget.Item -> itemProvider(target.index)?.requestFocusInWindow()
                FocusTarget.AddButton -> addButton?.requestFocusInWindow()
            }
        }
    }
}
