package io.github.barsia.speqa.editor.ui.chips

import com.intellij.icons.AllIcons
import com.intellij.util.ui.JBUI
import io.github.barsia.speqa.SpeqaBundle
import io.github.barsia.speqa.editor.ui.primitives.DeleteFocusRestorer
import io.github.barsia.speqa.editor.ui.primitives.speqaIconButton
import java.awt.FlowLayout
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Horizontal flow-layout panel rendering existing tickets as [TicketChip]s plus a
 * trailing add button that reveals a [ticketInput]. Uses [DeleteFocusRestorer] to
 * move focus to the next sensible target when an item is removed.
 */
class TicketRow(
    private val onActivate: (String) -> Unit = {},
    private val onAdd: (String) -> Unit = {},
    private val onRemove: (String) -> Unit = {},
) : JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(4), JBUI.scale(2))) {

    private val chips = mutableListOf<TicketChip>()
    private var tickets: List<String> = emptyList()
    private val addButton: JComponent = speqaIconButton(
        icon = AllIcons.General.Add,
        tooltip = SpeqaBundle.message("tooltip.linkTicket"),
        onAction = { showInput() },
    )
    private val restorer = DeleteFocusRestorer(
        itemProvider = { chips.getOrNull(it) },
        addButton = addButton,
    )

    init {
        isOpaque = false
        add(addButton)
    }

    fun setTickets(newTickets: List<String>) {
        tickets = newTickets.toList()
        rebuild()
    }

    private fun rebuild() {
        removeAll()
        chips.clear()
        tickets.forEachIndexed { index, ticket ->
            val sizeBefore = tickets.size
            val chip = TicketChip(
                ticket = ticket,
                onActivate = { onActivate(ticket) },
                onDelete = {
                    onRemove(ticket)
                    restorer.onDeleted(index, sizeBefore)
                },
            )
            chips.add(chip)
            add(chip)
        }
        add(addButton)
        revalidate()
        repaint()
    }

    private fun showInput() {
        val input = ticketInput(
            onCommit = { value ->
                onAdd(value)
            },
            onCancel = { dismissInput() },
        )
        remove(addButton)
        add(input)
        revalidate()
        repaint()
        input.requestFocusInWindow()
    }

    private fun dismissInput() {
        rebuild()
        addButton.requestFocusInWindow()
    }
}
