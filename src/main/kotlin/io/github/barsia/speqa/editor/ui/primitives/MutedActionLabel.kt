package io.github.barsia.speqa.editor.ui.primitives

import com.intellij.openapi.util.IconLoader
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Component
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.SwingUtilities

/**
 * A compact inline action button rendered as a single muted-foreground label
 * with an optional leading icon. Unlike a wrapper-panel implementation, the
 * hit area matches the label's natural size (icon + text + small gap), so
 * clicks far from the text don't trigger the action — which was a bug in
 * the earlier JPanel-based add-button pattern. Keyboard activation via
 * Enter/Space when focused.
 */
fun mutedActionLabel(
    text: String,
    icon: Icon? = null,
    onClick: () -> Unit,
): JComponent {
    val mutedIcon = icon?.let { IconLoader.getTransparentIcon(it, 0.7f) }
    val label = JBLabel(text, mutedIcon, JBLabel.LEFT)
    label.iconTextGap = JBUI.scale(4)
    label.foreground = UIUtil.getContextHelpForeground()
    label.alignmentX = Component.LEFT_ALIGNMENT
    // Align the label's left edge with the enclosing column's left edge.
    // Previously we offset by 7px (field border + inner padding) to match
    // the first character of the field above, but that made "+ Attach",
    // "+ Add link", "+ Add ticket" look indented relative to the column.
    label.handCursor()
    label.isFocusable = true
    label.addMouseListener(object : MouseAdapter() {
        override fun mouseClicked(e: MouseEvent) {
            if (SwingUtilities.isLeftMouseButton(e)) {
                label.requestFocusInWindow()
                onClick()
            }
        }
    })
    label.addKeyListener(object : KeyAdapter() {
        override fun keyPressed(e: KeyEvent) {
            if (e.keyCode == KeyEvent.VK_ENTER || e.keyCode == KeyEvent.VK_SPACE) {
                onClick()
                e.consume()
            }
        }
    })
    return label
}
