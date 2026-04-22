package io.github.barsia.speqa.editor.ui.chips

import com.intellij.icons.AllIcons
import com.intellij.ide.ui.laf.darcula.DarculaUIUtil
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import io.github.barsia.speqa.SpeqaBundle
import io.github.barsia.speqa.editor.ui.primitives.handCursor
import io.github.barsia.speqa.editor.ui.primitives.speqaIconButton
import java.awt.Color
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
import javax.swing.BorderFactory
import javax.swing.JLabel
import javax.swing.JPanel

/**
 * Swing chip representing one ticket. Shows "$prefix$id"-style label, activates on
 * Enter/Space/click and exposes a trailing delete button. Keyboard-focusable.
 */
class TicketChip(
    ticket: String,
    onActivate: () -> Unit,
    onDelete: () -> Unit,
) : JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(4), 0)) {

    private var focusedRing: Boolean = false

    init {
        isOpaque = false
        border = BorderFactory.createEmptyBorder(JBUI.scale(2), JBUI.scale(4), JBUI.scale(2), JBUI.scale(2))
        isFocusable = true
        handCursor()

        val label = JLabel(ticket).apply {
            foreground = JBColor.namedColor("Link.activeForeground", JBColor.BLUE)
            toolTipText = ticket
        }
        add(label)

        val deleteButton = speqaIconButton(
            icon = AllIcons.Actions.Close,
            tooltip = SpeqaBundle.message("tooltip.removeTicket"),
            muted = true,
            onAction = onDelete,
        )
        add(deleteButton)

        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                requestFocusInWindow()
                onActivate()
            }
        })
        addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if (e.keyCode == KeyEvent.VK_ENTER || e.keyCode == KeyEvent.VK_SPACE) {
                    onActivate()
                    e.consume()
                }
            }
        })
        addFocusListener(object : FocusAdapter() {
            override fun focusGained(e: FocusEvent) { focusedRing = true; repaint() }
            override fun focusLost(e: FocusEvent) { focusedRing = false; repaint() }
        })
    }

    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            val bg: Color = JBUI.CurrentTheme.ActionButton.hoverBackground()
            g2.color = bg
            val arc = JBUI.scale(6)
            g2.fillRoundRect(0, 0, width - 1, height - 1, arc, arc)
            if (focusedRing) {
                DarculaUIUtil.paintFocusBorder(g2, width, height, arc.toFloat(), true)
            }
        } finally {
            g2.dispose()
        }
        super.paintComponent(g)
    }
}
