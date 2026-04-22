package io.github.barsia.speqa.editor.ui.primitives

/**
 * Pure stack of visited focus-target ids. Intentionally minimal — Swing
 * integration (attaching a `FocusListener` that calls [push]) is deferred
 * to a later migration step.
 */
class FocusTrail {
    private val stack: ArrayDeque<String> = ArrayDeque()

    /** Size, exposed mainly for testing. */
    val size: Int get() = stack.size

    /**
     * Push [id] as the current focus target. If [id] equals the current top,
     * the stack is left untouched — re-focusing the same target is not a
     * new trail entry.
     */
    fun push(id: String) {
        if (stack.lastOrNull() == id) return
        stack.addLast(id)
    }

    /**
     * Return the most recent target that is not the current top and remove
     * the current top. If fewer than two distinct entries exist, returns null
     * and leaves the stack empty.
     */
    fun popPrevious(): String? {
        if (stack.size < 2) {
            stack.clear()
            return null
        }
        stack.removeLast()
        return stack.lastOrNull()
    }
}
