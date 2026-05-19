package io.github.barsia.speqa.editor.ui.chips

import com.intellij.ide.ui.laf.darcula.DarculaUIUtil
import com.intellij.openapi.util.IconLoader
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import io.github.barsia.speqa.SpeqaBundle
import io.github.barsia.speqa.editor.ui.primitives.RemovableRowActionSlot
import io.github.barsia.speqa.editor.ui.primitives.handCursor
import io.github.barsia.speqa.editor.ui.primitives.installRemovableRowActionVisibility
import io.github.barsia.speqa.editor.ui.primitives.installRowHover
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JPanel
import javax.swing.SwingUtilities

private val KEYBOARD_FOCUS_CAUSES = setOf(
    FocusEvent.Cause.TRAVERSAL,
    FocusEvent.Cause.TRAVERSAL_FORWARD,
    FocusEvent.Cause.TRAVERSAL_BACKWARD,
    FocusEvent.Cause.TRAVERSAL_UP,
    FocusEvent.Cause.TRAVERSAL_DOWN,
)

/**
 * Row representing one ticket reference. Visually mirrors `LinkRow`
 * (icon + clickable blue label + trailing delete button) so step-level
 * tickets read the same way as step-level links, only with a ticket icon
 * instead of a chain icon. Keyboard-focusable; Enter/Space/click activates
 * the [onActivate] callback.
 */
class TicketChip(
    ticket: String,
    readOnly: Boolean = false,
    onActivate: () -> Unit,
    onDelete: () -> Unit,
) : JPanel(BorderLayout(JBUI.scale(4), 0)) {

    private var focusedRing: Boolean = false
    private var removeSlot: RemovableRowActionSlot? = null

    init {
        isOpaque = false
        isFocusable = true
        toolTipText = SpeqaBundle.message("tooltip.openTicket")
        handCursor()

        val ticketIcon = IconLoader.getIcon("/icons/ticket.svg", TicketChip::class.java)
        val iconLabel = JBLabel().apply {
            border = JBUI.Borders.emptyRight(4)
            toolTipText = SpeqaBundle.message("tooltip.openTicket")
        }
        add(iconLabel, BorderLayout.WEST)

        val titleLabel = object : JBLabel(ticket) {
            override fun getToolTipText(event: MouseEvent?): String =
                ticketTooltipForWidth(
                    preferredWidth = preferredSize.width,
                    actualWidth = width,
                    normal = SpeqaBundle.message("tooltip.openTicket"),
                    overflow = SpeqaBundle.message("tooltip.openTicketWithId", ticket),
                )
        }.apply {
            toolTipText = SpeqaBundle.message("tooltip.openTicket")
            minimumSize = Dimension(0, preferredSize.height)
        }
        add(titleLabel, BorderLayout.CENTER)

        if (!readOnly) {
            val actions = JPanel(FlowLayout(FlowLayout.RIGHT, JBUI.scale(2), 0)).apply { isOpaque = false }
            removeSlot = RemovableRowActionSlot(
                tooltip = SpeqaBundle.message("tooltip.removeTicket"),
                onAction = onDelete,
            ).also { actions.add(it) }
            add(actions, BorderLayout.EAST)
        }

        // Listen on the panel AND its non-action children. Swing does not
        // bubble mouse events to the parent when a child has no listener of
        // its own, so clicks on the icon or label would otherwise be
        // swallowed and the chip would feel un-clickable.
        val clickListener = object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                requestFocusInWindow()
                onActivate()
            }
        }
        addMouseListener(clickListener)
        iconLabel.addMouseListener(clickListener)
        iconLabel.handCursor()
        titleLabel.addMouseListener(clickListener)
        titleLabel.handCursor()
        addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if (e.keyCode == KeyEvent.VK_ENTER || e.keyCode == KeyEvent.VK_SPACE) {
                    onActivate()
                    e.consume()
                }
            }
        })
        addFocusListener(object : FocusAdapter() {
            override fun focusGained(e: FocusEvent) {
                // Only paint the ring for keyboard navigation. Mouse clicks
                // also bring focus, but a mouse-driven ring around the chip
                // after activation looks like a stuck selection.
                focusedRing = e.cause in KEYBOARD_FOCUS_CAUSES
                removeSlot?.setRowInteraction(focused = focusedRing)
                repaint()
            }
            override fun focusLost(e: FocusEvent) {
                focusedRing = false
                val keepRemoveVisible = removeSlot?.let { slot ->
                    e.oppositeComponent == slot || SwingUtilities.isDescendingFrom(e.oppositeComponent, slot)
                } == true
                removeSlot?.setRowInteraction(focused = keepRemoveVisible)
                repaint()
            }
        })

        installRowHover(this, titleLabel, iconLabel, ticketIcon)
        removeSlot?.let { installRemovableRowActionVisibility(this, it) { focusedRing } }
    }

    override fun getMaximumSize(): Dimension {
        val pref = preferredSize
        return Dimension(Integer.MAX_VALUE, pref.height)
    }

    override fun getMinimumSize(): Dimension {
        val pref = preferredSize
        return Dimension(0, pref.height)
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        if (focusedRing) {
            val g2 = g.create() as Graphics2D
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                val arc = JBUI.scale(4).toFloat()
                DarculaUIUtil.paintFocusBorder(g2, width, height, arc, true)
            } finally {
                g2.dispose()
            }
        }
    }
}
