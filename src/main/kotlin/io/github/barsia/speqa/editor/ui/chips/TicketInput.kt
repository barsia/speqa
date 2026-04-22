package io.github.barsia.speqa.editor.ui.chips

import com.intellij.ui.JBColor
import com.intellij.ui.RoundedLineBorder
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import io.github.barsia.speqa.SpeqaBundle
import io.github.barsia.speqa.editor.ui.primitives.singleLineInput
import java.awt.Dimension
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.BorderFactory

/**
 * Single-line ticket-id input.
 *
 * [onCommit] fires on Enter with the trimmed non-empty draft. [onCancel] fires on
 * Escape. No on-focus-lost commit — the surrounding container decides what to do
 * when focus leaves.
 */
fun ticketInput(
    initial: String = "",
    onCommit: (String) -> Unit,
    onCancel: () -> Unit = {},
): JBTextField {
    val field = singleLineInput(placeholder = SpeqaBundle.message("placeholder.ticketId"))
    field.columns = 10
    field.text = initial
    field.alignmentX = java.awt.Component.LEFT_ALIGNMENT
    // Replace DarculaTextBorder with a compact rounded border so the input
    // matches the TicketChip height (~22px). The default border reserves
    // vertical space for the focus ring which visually towers over the
    // sibling "Add link" / "Attach file" labels in StepMetaRow.
    field.border = BorderFactory.createCompoundBorder(
        RoundedLineBorder(JBColor.border(), JBUI.scale(6), 1),
        BorderFactory.createEmptyBorder(JBUI.scale(1), JBUI.scale(6), JBUI.scale(1), JBUI.scale(6)),
    )
    field.isOpaque = false
    // Lock the field to a compact, chip-sized footprint. GridLayout cells
    // around this input ignore maxSize, but the enclosing TicketBlock uses
    // BoxLayout.Y_AXIS which DOES respect max, so the input stays short
    // there; and preferredSize bounds the GridLayout row height so the
    // surrounding cells don't grow either.
    val w = JBUI.scale(90)
    val h = JBUI.scale(22)
    field.preferredSize = Dimension(w, h)
    field.minimumSize = Dimension(w, h)
    field.maximumSize = Dimension(w, h)
    field.addKeyListener(object : KeyAdapter() {
        override fun keyPressed(e: KeyEvent) {
            when (e.keyCode) {
                KeyEvent.VK_ENTER -> {
                    val trimmed = field.text.trim()
                    if (trimmed.isNotEmpty()) {
                        onCommit(trimmed)
                    } else {
                        onCancel()
                    }
                    e.consume()
                }
                KeyEvent.VK_ESCAPE -> { onCancel(); e.consume() }
            }
        }
    })
    return field
}
