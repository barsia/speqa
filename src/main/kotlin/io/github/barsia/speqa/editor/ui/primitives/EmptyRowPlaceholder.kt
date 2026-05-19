package io.github.barsia.speqa.editor.ui.primitives

import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Empty-state placeholder row used by `LinkList`, `AttachmentList`, etc. when
 * there are no items to display. The placeholder is rendered at the same
 * fixed row height (~22px) as a real row so its baseline lines up with a
 * sibling column's `LinkRow`/`AttachmentRow` (whose height is dominated by
 * their inline 22x22 `ActionButton` trailing icons). A bare `JBLabel` would
 * be ~17px and land a few pixels higher.
 */
fun emptyRowPlaceholder(text: String): JComponent {
    val rowHeight = inlineMetadataRowHeight()
    val label = JBLabel(text).apply {
        foreground = JBColor.namedColor("Label.disabledForeground", JBColor.GRAY)
    }
    return JPanel(BorderLayout()).apply {
        isOpaque = false
        alignmentX = Component.LEFT_ALIGNMENT
        preferredSize = Dimension(preferredSize.width, rowHeight)
        minimumSize = Dimension(0, rowHeight)
        maximumSize = Dimension(Integer.MAX_VALUE, rowHeight)
        add(label, BorderLayout.CENTER)
    }
}
