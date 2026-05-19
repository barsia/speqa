// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package io.github.barsia.speqa.editor.ui.primitives

import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import java.awt.Dimension
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.MouseEvent
import javax.swing.Icon
import javax.swing.UIManager

/**
 * Single `JBLabel` that paints both the calendar icon and the date text in one
 * component. Tooltips are installed on this single component, so the icon and
 * the date read as one tooltip area - the popover never flickers as the cursor
 * crosses from the icon glyph onto the date text.
 *
 * When the laid-out width is narrower than the natural label width, Swing's
 * `JLabel` clips the text with an ellipsis. While the text is clipped, the
 * tooltip switches from the bare label ("Created") to the full label plus the
 * date value ("Created 2026-05-19") so the user can still read the date.
 */
class DateIconLabel(
    icon: Icon,
    text: String,
    private val normalTooltip: String,
) : JBLabel(text, icon, LEFT) {

    init {
        iconTextGap = JBUI.scale(4)
        val mutedFg = UIManager.getColor("Label.disabledForeground")
            ?: JBColor.namedColor("Label.disabledForeground", JBColor.GRAY)
        foreground = mutedFg
        setIcon(replaceIconColor(icon, mutedFg))
        // Non-null toolTipText registers the component with ToolTipManager so
        // `getToolTipText(MouseEvent)` is consulted; the actual text depends on
        // hover position and truncation (see `getToolTipText(MouseEvent)`).
        toolTipText = normalTooltip
        addComponentListener(object : ComponentAdapter() {
            override fun componentResized(e: ComponentEvent) = repaint()
        })
    }

    /**
     * Shrinks down to icon-only width. The text is allowed to ellipsis-clip via
     * `JLabel`'s native rendering; a horizontal `BoxLayout` container can
     * therefore distribute width to the icon plus a variable amount of text,
     * instead of wrapping the whole label onto a new row.
     */
    override fun getMinimumSize(): Dimension {
        val iconWidth = (icon?.iconWidth ?: 0) + insets.left + insets.right
        return Dimension(iconWidth, preferredSize.height)
    }

    /**
     * Pinned to the natural preferred size so a `BoxLayout` does not grow the
     * label beyond its content width when the row has extra room.
     */
    override fun getMaximumSize(): Dimension = preferredSize

    /**
     * Per-position tooltip:
     * - Date fully visible: icon shows the bare label ("Created"); text region
     *   shows no tooltip (the value is already on screen).
     * - Date ellipsis-clipped: both icon and text show the same combined
     *   tooltip ("Created 2026-05-19"). Keeping the string identical across
     *   the icon and text regions prevents the tooltip from being dismissed
     *   and re-shown as the cursor crosses between them.
     */
    override fun getToolTipText(event: MouseEvent?): String? {
        val currentText = text.orEmpty()
        val truncated = currentText.isNotEmpty() && width in 1 until preferredSize.width
        if (truncated) return "$normalTooltip $currentText"
        val iconW = icon?.iconWidth ?: 0
        val iconRegionEnd = insets.left + iconW
        val overIcon = event != null && event.x < iconRegionEnd
        return if (overIcon) normalTooltip else null
    }
}

/**
 * Compatibility factory for prior call sites.
 */
fun dateIconLabel(icon: Icon, text: String, normalTooltip: String): DateIconLabel {
    return DateIconLabel(icon, text, normalTooltip)
}
