package io.github.barsia.speqa.editor.ui.primitives

import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import javax.swing.Icon
import javax.swing.UIManager

/**
 * Small icon-plus-text label used in the panel header utility row for the
 * created / updated / started / finished timestamps. The icon and text share
 * a single [JBLabel] via `setIconTextGap`.
 */
fun dateIconLabel(icon: Icon, text: String, tooltip: String? = null): JBLabel {
    val label = JBLabel(text, icon, JBLabel.LEFT)
    label.iconTextGap = JBUI.scale(4)
    label.foreground = UIManager.getColor("Label.disabledForeground")
        ?: JBColor.namedColor("Label.disabledForeground", JBColor.GRAY)
    label.toolTipText = tooltip
    return label
}
