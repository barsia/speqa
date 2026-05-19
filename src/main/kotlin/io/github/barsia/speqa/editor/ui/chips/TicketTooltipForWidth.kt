package io.github.barsia.speqa.editor.ui.chips

/**
 * Picks the ticket activation tooltip based on whether Swing has clipped the
 * visible ticket label with ellipsis.
 */
fun ticketTooltipForWidth(preferredWidth: Int, actualWidth: Int, normal: String, overflow: String): String {
    return if (actualWidth < preferredWidth) overflow else normal
}
