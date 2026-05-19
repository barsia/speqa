package io.github.barsia.speqa.editor.ui.chips

import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import io.github.barsia.speqa.SpeqaBundle
import io.github.barsia.speqa.editor.ui.primitives.inlineMetadataRowHeight
import io.github.barsia.speqa.editor.ui.primitives.singleLineInput
import java.awt.Dimension
import java.awt.Insets
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
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
    // Match the visual footprint of the sibling "Add ticket ID" muted
    // label exactly so toggling between read and edit modes does not
    // shift the row. Plain BasicTextFieldUI bypasses Darcula's
    // border/insets (which add ~6 px of vertical padding), no rounded
    // frame, transparent background. The caret + selection give enough
    // editing affordance on their own.
    field.ui = javax.swing.plaf.basic.BasicTextFieldUI()
    field.border = BorderFactory.createEmptyBorder()
    field.margin = Insets(0, 0, 0, 0)
    field.isOpaque = false
    field.background = null

    // Pin the field to the same height the "Add ticket ID" label would
    // occupy in this slot; otherwise JBTextField's natural height (font
    // height + LAF padding) is still a few pixels taller than a plain
    // JBLabel and the row jumps when switching.
    val rowHeight = inlineMetadataRowHeight()
    val w = JBLabel("X").preferredSize.height * 6
    field.preferredSize = Dimension(w, rowHeight)
    field.minimumSize = Dimension(w, rowHeight)
    field.maximumSize = Dimension(w, rowHeight)
    // Guard so that the same gesture (Enter / Escape) does not fire both
    // through the KeyListener and through the FocusListener once Swing
    // moves focus out of the about-to-be-removed input.
    var handled = false
    fun finish(commit: Boolean) {
        if (handled) return
        handled = true
        val trimmed = field.text.trim()
        if (commit && trimmed.isNotEmpty()) onCommit(trimmed) else onCancel()
    }

    field.addKeyListener(object : KeyAdapter() {
        override fun keyPressed(e: KeyEvent) {
            when (e.keyCode) {
                KeyEvent.VK_ENTER -> { finish(commit = true); e.consume() }
                KeyEvent.VK_ESCAPE -> { finish(commit = false); e.consume() }
            }
        }
    })
    field.addFocusListener(object : FocusAdapter() {
        override fun focusLost(e: FocusEvent) {
            if (e.isTemporary) return
            finish(commit = true)
        }
    })
    return field
}
