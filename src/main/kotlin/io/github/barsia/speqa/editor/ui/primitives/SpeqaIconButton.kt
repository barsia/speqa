package io.github.barsia.speqa.editor.ui.primitives

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.NlsActions
import com.intellij.util.ui.JBUI
import javax.swing.Icon
import javax.swing.JComponent

/**
 * Compact icon-only action button rendered as an IntelliJ [ActionButton]
 * wrapping a [DumbAwareAction]. Includes a hand cursor and native tooltip.
 *
 * @param icon button icon.
 * @param tooltip accessible / hover text; used as action template presentation text.
 * @param onAction invoked on click / keyboard activation.
 */
fun speqaIconButton(
    icon: Icon,
    @NlsActions.ActionText tooltip: String,
    muted: Boolean = false,
    onAction: () -> Unit,
): JComponent {
    val baseIcon = if (muted) IconLoader.getTransparentIcon(icon, 0.6f) else icon
    val action = object : DumbAwareAction(tooltip, null, baseIcon) {
        override fun actionPerformed(e: AnActionEvent) {
            onAction()
        }
    }
    val presentation = Presentation(tooltip).apply {
        this.icon = baseIcon
        description = tooltip
    }
    val button = ActionButton(
        action,
        presentation,
        ActionPlaces.UNKNOWN,
        JBUI.size(22, 22),
    )
    button.toolTipText = tooltip
    button.isFocusable = true
    button.handCursor()
    // Register action so keyboard shortcuts (if later added) can find it.
    ActionManager.getInstance()
    return button
}
