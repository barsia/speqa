package io.github.barsia.speqa.editor

import com.intellij.ide.AppLifecycleListener
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.editor.actionSystem.EditorActionManager

/**
 * Registers [SpeqaTabActionHandler] at application startup, wrapping the
 * platform Tab and Shift+Tab handlers. This makes our list-item indent fire
 * before the bundled IntelliJ Markdown plugin's Tab indent for `.tc.md` /
 * `.tr.md` files, so indenting a step does not insert spaces inside its
 * expected-result blockquote.
 */
class SpeqaTabActionSetup : AppLifecycleListener {
    override fun appFrameCreated(commandLineArgs: List<String>) {
        val manager = EditorActionManager.getInstance()

        val tab = manager.getActionHandler(IdeActions.ACTION_EDITOR_TAB)
        if (tab !is SpeqaTabActionHandler) {
            manager.setActionHandler(IdeActions.ACTION_EDITOR_TAB, SpeqaTabActionHandler(tab, indent = true))
        }

        val unindent = manager.getActionHandler(IdeActions.ACTION_EDITOR_UNINDENT_SELECTION)
        if (unindent !is SpeqaTabActionHandler) {
            manager.setActionHandler(
                IdeActions.ACTION_EDITOR_UNINDENT_SELECTION,
                SpeqaTabActionHandler(unindent, indent = false),
            )
        }
    }
}
