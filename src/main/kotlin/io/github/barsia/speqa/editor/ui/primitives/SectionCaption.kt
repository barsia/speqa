package io.github.barsia.speqa.editor.ui.primitives

import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.UIUtil
import java.awt.Font
import java.awt.font.TextAttribute

/**
 * Small uppercase muted caption used as the header of a content section
 * (PRIORITY, STATUS, TAGS, LINKS, DESCRIPTION, etc.).
 * 11sp, SemiBold, 0.8sp letter-spacing, muted foreground.
 */
fun sectionCaption(text: String): JBLabel {
    val label = JBLabel(text.uppercase())
    val base = label.font
    val attrs = HashMap<TextAttribute, Any?>()
    attrs[TextAttribute.WEIGHT] = TextAttribute.WEIGHT_SEMIBOLD
    attrs[TextAttribute.TRACKING] = 0.06f
    label.font = base.deriveFont(Font.PLAIN, base.size2D - 2f).deriveFont(attrs)
    label.foreground = UIUtil.getContextHelpForeground()
        ?: JBColor.namedColor("Component.infoForeground", JBColor.GRAY)
    return label
}
