package io.github.barsia.speqa.editor.ui.chips

import com.intellij.icons.AllIcons
import com.intellij.ide.ui.laf.darcula.DarculaUIUtil
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
 * Swing tag chip coloured by [tagChipColor] when [colored] is set. Keyboard
 * activatable, exposes a delete button. Focus ring rendered via
 * [DarculaUIUtil.paintFocusBorder].
 */
class TagChip(
    tag: String,
    colored: Boolean,
    onActivate: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    contextMenu: (() -> javax.swing.JPopupMenu)? = null,
    tooltip: String? = null,
) : JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(4), 0)) {

    private val backgroundColor: Color = if (colored) tagChipColor(tag) else JBUI.CurrentTheme.ActionButton.hoverBackground()
    private var focusedRing = false

    init {
        isOpaque = false
        border = BorderFactory.createEmptyBorder(JBUI.scale(2), JBUI.scale(8), JBUI.scale(2), JBUI.scale(4))
        isFocusable = onActivate != null
        if (onActivate != null || onClick != null) {
            handCursor()
        }
        if (tooltip != null) toolTipText = tooltip
        add(JLabel(tag).apply { if (tooltip != null) toolTipText = tooltip })
        if (onDelete != null) {
            add(
                speqaIconButton(
                    icon = AllIcons.Actions.Close,
                    tooltip = SpeqaBundle.message("tagCloud.removeTag", tag),
                    muted = true,
                    onAction = onDelete,
                ),
            )
        }
        if (onActivate != null || onClick != null || contextMenu != null) {
            addMouseListener(object : MouseAdapter() {
                override fun mousePressed(e: MouseEvent) { maybeShowMenu(e) }
                override fun mouseReleased(e: MouseEvent) { maybeShowMenu(e) }
                override fun mouseClicked(e: MouseEvent) {
                    if (e.isPopupTrigger) return
                    if (e.button != MouseEvent.BUTTON1) return
                    requestFocusInWindow()
                    onClick?.invoke()
                    if (onClick == null) onActivate?.invoke()
                }
                private fun maybeShowMenu(e: MouseEvent) {
                    if (!e.isPopupTrigger) return
                    val menu = contextMenu?.invoke() ?: return
                    menu.show(e.component, e.x, e.y)
                    e.consume()
                }
            })
        }
        if (onActivate != null) {
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
    }

    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.color = backgroundColor
            val arc = JBUI.scale(12)
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
