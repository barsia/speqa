package io.github.barsia.speqa.editor.ui.primitives

import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import java.awt.Component
import java.awt.Dimension
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.SwingUtilities

fun inlineMetadataRowHeight(): Int = JBUI.scale(22)

/**
 * A compact inline action button rendered as a single muted-foreground label
 * with an optional leading icon. Unlike a wrapper-panel implementation, the
 * hit area matches the label's natural size (icon + text + small gap), so
 * clicks far from the text don't trigger the action - which was a bug in
 * the earlier JPanel-based add-button pattern. Keyboard activation via
 * Enter/Space when focused.
 *
 * On hover the foreground and icon tint switch from the muted help colour
 * to the IDE link accent, matching the Add step button.
 */
fun mutedActionLabel(
    text: String,
    icon: Icon? = null,
    onClick: () -> Unit,
): JComponent {
    val mutedFg = speqaMutedIconColor()
    val accentFg = JBColor.namedColor("Link.activeForeground", JBColor.BLUE)
    // `replaceIconColor` now renders into a HiDPI-aware image and uses an
    // SrcAtop composite to recolour every pixel solidly. That gives a sharp
    // tint at the same saturation as the label text, unlike
    // `IconUtil.colorize` which preserved the SVG's gray luminance and made
    // the icons look paler than the surrounding text.
    val mutedIcon = icon?.let { replaceIconColor(it, mutedFg) }
    val accentIcon = icon?.let { replaceIconColor(it, accentFg) }

    val label = JBLabel(text, mutedIcon, JBLabel.LEFT)
    val rowHeight = inlineMetadataRowHeight()
    label.iconTextGap = JBUI.scale(4)
    label.foreground = mutedFg
    label.alignmentX = Component.LEFT_ALIGNMENT
    label.preferredSize = Dimension(label.preferredSize.width, rowHeight)
    label.minimumSize = Dimension(0, rowHeight)
    label.maximumSize = Dimension(label.preferredSize.width, rowHeight)
    label.handCursor()
    label.isFocusable = true

    fun applyMuted() {
        label.foreground = mutedFg
        if (mutedIcon != null) label.icon = mutedIcon
    }
    fun applyAccent() {
        label.foreground = accentFg
        if (accentIcon != null) label.icon = accentIcon
    }

    label.addMouseListener(object : MouseAdapter() {
        override fun mouseEntered(e: MouseEvent) { applyAccent() }
        override fun mouseExited(e: MouseEvent) { applyMuted() }
        override fun mouseClicked(e: MouseEvent) {
            if (SwingUtilities.isLeftMouseButton(e)) {
                // onClick typically removes this label from its container
                // (e.g. swapping the muted "Add X" for an inline input).
                // mouseExited never fires in that case, so when the label
                // is re-added later it still shows the hover accent.
                // Reset to muted explicitly before invoking the action.
                applyMuted()
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
