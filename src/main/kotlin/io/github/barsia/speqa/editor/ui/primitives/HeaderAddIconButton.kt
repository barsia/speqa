package io.github.barsia.speqa.editor.ui.primitives

import com.intellij.icons.AllIcons
import javax.swing.JComponent

/**
 * Small `+` icon button used inside two-column section headers. Delegates to
 * [speqaIconButton] for hand-cursor, tooltip, and keyboard support.
 */
fun headerAddIconButton(tooltip: String, onClick: () -> Unit): JComponent =
    speqaIconButton(icon = AllIcons.General.Add, tooltip = tooltip, onAction = onClick)
