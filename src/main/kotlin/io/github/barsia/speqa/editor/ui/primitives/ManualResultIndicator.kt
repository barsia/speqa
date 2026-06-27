package io.github.barsia.speqa.editor.ui.primitives

import com.intellij.icons.AllIcons
import com.intellij.ui.components.JBLabel
import java.awt.Component

/**
 * Compact icon-only marker placed next to a run-result control to signal that the result was set
 * manually (overriding the value derived from steps / aggregated from cases). Icon-only with a
 * hover tooltip so it never adds a text label that wraps the header row; toggle [JBLabel.isVisible]
 * to show/hide it. A muted note marker (not a pencil, which would read as "edit this field").
 */
fun manualResultIndicator(tooltip: String): JBLabel =
    JBLabel(replaceIconColor(AllIcons.General.Note, speqaMutedIconColor())).apply {
        toolTipText = tooltip
        alignmentY = Component.CENTER_ALIGNMENT
    }
