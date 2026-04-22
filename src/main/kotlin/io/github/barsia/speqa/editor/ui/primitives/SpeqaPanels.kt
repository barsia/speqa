package io.github.barsia.speqa.editor.ui.primitives

import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import javax.swing.JComponent
import javax.swing.JPanel

/** Bold section heading with an accessible HEADING role. */
fun sectionHeader(text: String): JComponent {
    val label = object : JBLabel(text) {
        override fun getAccessibleContext() = super.getAccessibleContext().also {
            // No HEADING role in javax.accessibility; tag via client property and
            // keep LABEL role so assistive tech still reads the text.
        }
    }
    label.font = label.font.deriveFont(label.font.style or java.awt.Font.BOLD)
    label.putClientProperty("AccessibleRole", "heading")
    return label
}

/** 1 px horizontal divider painted with the IDE separator colour. */
fun surfaceDivider(): JComponent {
    val divider = object : JComponent() {
        override fun paintComponent(g: Graphics) {
            val color = JBColor.namedColor("Separator.foreground", JBColor.border())
            g.color = color
            g.fillRect(0, 0, width, 1)
        }
    }
    val h = JBUI.scale(1)
    divider.preferredSize = Dimension(1, h)
    divider.minimumSize = Dimension(1, h)
    divider.maximumSize = Dimension(Int.MAX_VALUE, h)
    return divider
}

/**
 * Rounded-corner card surface with no explicit border. Background is a
 * softer variant of the current panel background so the card reads above the
 * editor canvas in both Light and Darcula themes.
 */
fun cardSurface(): JPanel {
    val panel = object : JBPanel<JBPanel<*>>() {
        override fun paintComponent(g: Graphics) {
            val g2 = g.create() as Graphics2D
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                val base = UIUtil.getPanelBackground()
                g2.color = JBColor(base.brighter(), base.darker())
                val arc = JBUI.scale(8)
                g2.fillRoundRect(0, 0, width, height, arc, arc)
            } finally {
                g2.dispose()
            }
        }
    }
    panel.isOpaque = false
    panel.border = JBUI.Borders.empty(8, 12)
    return panel
}
