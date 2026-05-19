package io.github.barsia.speqa.editor.ui.primitives

import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import java.awt.Container
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.SwingUtilities

/**
 * Default foreground for clickable saved-row text. Uses the IDE link
 * foreground so rows read as actionable / clickable (not muted-disabled).
 */
fun rowDefaultForeground(): JBColor = JBColor.namedColor("Link.activeForeground", JBColor.BLUE)

/**
 * Accent foreground used while the cursor hovers the row. Uses
 * `Link.hoverForeground` when the LAF provides it, falling back to the
 * regular active link colour so themes without a distinct hover token still
 * look sensible.
 */
fun rowHoverForeground(): JBColor =
    JBColor.namedColor("Link.hoverForeground", rowDefaultForeground())

/**
 * Centralised hover-tint behaviour shared by all clickable preview rows
 * (`TicketChip`, `LinkRow`, `AttachmentRow`). The row text is muted by
 * default and switches to the link-accent colour while the cursor is over
 * any part of [row] or its descendants. If [tintableIcon] is non-null the
 * leading [iconLabel] is repainted with the same colour transition; pass
 * `null` for system file icons that should keep their native multi-colour
 * rendering.
 *
 * Hover state is computed from the cursor's current location on screen so
 * brief mouse-exit into a child component (e.g. the trailing delete button)
 * does NOT flip the row back to muted.
 */
fun installRowHover(
    row: JComponent,
    textLabel: JBLabel,
    iconLabel: JBLabel? = null,
    tintableIcon: Icon? = null,
) {
    val defaultFg = rowDefaultForeground()
    val hoverFg = rowHoverForeground()
    // `replaceIconColor` paints the SVG into a HiDPI-aware buffered image
    // (via UIUtil.createImage) and recolours every rendered pixel with the
    // target color while keeping the original alpha. Unlike
    // `IconUtil.colorize` it does not preserve the source SVG's luminance,
    // so a gray-stroked icon comes out at the same saturation as the text.
    val defaultIcon = tintableIcon?.let { replaceIconColor(it, defaultFg) }
    val hoverIcon = tintableIcon?.let { replaceIconColor(it, hoverFg) }

    fun apply(hovered: Boolean) {
        textLabel.foreground = if (hovered) hoverFg else defaultFg
        if (iconLabel != null && defaultIcon != null && hoverIcon != null) {
            iconLabel.icon = if (hovered) hoverIcon else defaultIcon
        }
    }
    apply(false)

    val listener = object : MouseAdapter() {
        override fun mouseEntered(e: MouseEvent) { apply(true) }
        override fun mouseExited(e: MouseEvent) {
            // A child component receives mouseEntered before the row's
            // mouseExited fires, so check whether the cursor is genuinely
            // outside the row before reverting.
            SwingUtilities.invokeLater {
                if (!isCursorInsideRow(row)) apply(false)
            }
        }
    }
    attachRecursively(row, listener)
}

private fun isCursorInsideRow(row: JComponent): Boolean {
    if (!row.isShowing) return false
    val pi = java.awt.MouseInfo.getPointerInfo() ?: return false
    val origin = row.locationOnScreen
    val p = pi.location
    return p.x in origin.x..(origin.x + row.width) && p.y in origin.y..(origin.y + row.height)
}

private fun attachRecursively(component: java.awt.Component, listener: MouseAdapter) {
    component.addMouseListener(listener)
    if (component is Container) {
        for (child in component.components) attachRecursively(child, listener)
    }
}
