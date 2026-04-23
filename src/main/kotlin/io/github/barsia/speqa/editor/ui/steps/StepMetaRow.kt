package io.github.barsia.speqa.editor.ui.steps

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import io.github.barsia.speqa.SpeqaBundle
import io.github.barsia.speqa.editor.ui.attachments.AttachmentList
import io.github.barsia.speqa.editor.ui.chips.TicketChip
import io.github.barsia.speqa.editor.ui.chips.ticketInput
import io.github.barsia.speqa.editor.ui.links.LinkList
import io.github.barsia.speqa.editor.ui.primitives.DeleteFocusRestorer
import io.github.barsia.speqa.editor.ui.primitives.handCursor
import io.github.barsia.speqa.model.Attachment
import io.github.barsia.speqa.model.Link
import java.awt.Component
import java.awt.FlowLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.GridLayout
import java.awt.Insets
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingUtilities

/** Which surface hosts this meta row. Run mode reserves space for a verdict control (ported in Step 5). */
enum class StepMetaMode { CASE, RUN }

/**
 * Three-column compact row: tickets / links / attachments, each with its own
 * "+ add" button. The three reusable row widgets (`TicketChip`, `LinkList`,
 * `AttachmentList`) from the chip/row tier are used verbatim.
 */
class StepMetaRow(
    private val project: Project?,
    private val tcFile: VirtualFile?,
    private val mode: StepMetaMode = StepMetaMode.CASE,
    private val onTicketsChange: (List<String>) -> Unit,
    private val onLinksChange: (List<Link>) -> Unit,
    private val onAttachmentsChange: (List<Attachment>) -> Unit,
) : JPanel(GridLayout(1, 2, JBUI.scale(12), 0)) {

    private val ticketBlock = TicketBlock()
    private val linkBlock: JComponent = buildLinkBlock()
    private val attachmentBlock: JComponent = buildAttachmentBlock()

    init {
        isOpaque = false
        // Strict 50/50 outer split mirrors the Action/Expected geometry in
        // StepCard. GridLayout (not GridBag) guarantees equal halves
        // regardless of child pref widths. Inside the left half, tickets
        // and links share another strict 50/50 — so ticket-col, link-col,
        // attach-col all land at the same x as Action's first char, the
        // Action/Expected split, and Expected's left edge respectively.
        val innerGap = JBUI.scale(12)
        val leftHalf = JPanel(GridLayout(1, 2, innerGap, 0)).apply { isOpaque = false }
        leftHalf.add(ticketBlock)
        leftHalf.add(linkBlock)

        add(leftHalf)
        add(attachmentBlock)
    }

    // BoxLayout.Y_AXIS (the parent in StepCard.contentPanel) distributes any
    // extra vertical slack to children whose maximumSize.height exceeds
    // preferredSize.height. JPanel's default maximum is unbounded, so the
    // meta-row would stretch — and because we use GridLayout, the stretch
    // propagates to every cell, making the ticket input visibly tall with
    // an "oversized" placeholder. Clamping max height to pref height keeps
    // the row compact regardless of how tall the parent card gets.
    override fun getMaximumSize(): java.awt.Dimension {
        val pref = preferredSize
        return java.awt.Dimension(Int.MAX_VALUE, pref.height)
    }

    fun setData(tickets: List<String>, links: List<Link>, attachments: List<Attachment>) {
        ticketBlock.setTickets(tickets)
        (linkBlock as? LinkList)?.setLinks(links)
        (attachmentBlock as? AttachmentList)?.setAttachments(attachments)
    }

    private fun buildLinkBlock(): JComponent {
        if (project == null) return emptyColumn()
        return LinkList(project) { onLinksChange(it) }
    }

    private fun buildAttachmentBlock(): JComponent {
        if (project == null || tcFile == null) return emptyColumn()
        return AttachmentList(project, tcFile) { onAttachmentsChange(it) }
    }

    private fun emptyColumn(): JComponent {
        val p = JPanel()
        p.isOpaque = false
        return p
    }

    // Run-mode verdict control lands in Step 5; for now we simply honour the param.
    @Suppress("unused")
    internal fun runMode(): Boolean = mode == StepMetaMode.RUN

    private inner class TicketBlock : JPanel() {
        private var tickets: List<String> = emptyList()
        private val chipRequesters = mutableListOf<TicketChip>()
        private val addButton: JComponent = buildAddButton()
        private val restorer = DeleteFocusRestorer(
            itemProvider = { chipRequesters.getOrNull(it) },
            addButton = addButton,
        )
        private var editing: Boolean = false

        init {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
            rebuild()
        }

        fun setTickets(next: List<String>) {
            tickets = next.toList()
            editing = false
            rebuild()
        }

        private fun rebuild() {
            removeAll()
            chipRequesters.clear()
            tickets.forEachIndexed { index, ticket ->
                val sizeBefore = tickets.size
                val chip = TicketChip(
                    ticket = ticket,
                    onActivate = {},
                    onDelete = {
                        onTicketsChange(tickets.toMutableList().also { it.removeAt(index) })
                        restorer.onDeleted(index, sizeBefore)
                    },
                )
                chip.alignmentX = Component.LEFT_ALIGNMENT
                chipRequesters.add(chip)
                add(chip)
            }
            if (editing) {
                val input = ticketInput(
                    onCommit = { value ->
                        val cleaned = value.split(Regex("[,;\\s]+")).filter { it.isNotBlank() }
                        if (cleaned.isNotEmpty()) onTicketsChange(tickets + cleaned)
                        editing = false
                        rebuild()
                        SwingUtilities.invokeLater { addButton.requestFocusInWindow() }
                    },
                    onCancel = {
                        editing = false
                        rebuild()
                        SwingUtilities.invokeLater { addButton.requestFocusInWindow() }
                    },
                )
                input.alignmentX = Component.LEFT_ALIGNMENT
                add(input)
                SwingUtilities.invokeLater { input.requestFocusInWindow() }
            } else {
                addButton.alignmentX = Component.LEFT_ALIGNMENT
                add(addButton)
            }
            revalidate()
            repaint()
        }

        private fun buildAddButton(): JComponent {
            val icon = IconLoader.getIcon("/icons/ticket.svg", StepMetaRow::class.java)
            return io.github.barsia.speqa.editor.ui.primitives.mutedActionLabel(
                text = SpeqaBundle.message("step.meta.addTicket"),
                icon = icon,
                onClick = {
                    editing = true
                    rebuild()
                },
            )
        }

    }
}
