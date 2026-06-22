package io.github.barsia.speqa.editor

import com.intellij.ide.AppLifecycleListener
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.editor.actionSystem.EditorActionManager

/**
 * Registers [SpeqaBackspaceActionHandler] at application startup, wrapping the
 * platform's BackSpace action handler. This ensures our handler fires before any
 * [com.intellij.codeInsight.editorActions.BackspaceHandlerDelegate] (including
 * IntelliJ's Markdown plugin delegates), so the `> ` blockquote prefix is removed
 * in a single Backspace keystroke for `.tc.md` / `.tr.md` files.
 */
class SpeqaBackspaceActionSetup : AppLifecycleListener {
    override fun appStarted() {
        val manager = EditorActionManager.getInstance()
        val original = manager.getActionHandler(IdeActions.ACTION_EDITOR_BACKSPACE)
        if (original !is SpeqaBackspaceActionHandler) {
            manager.setActionHandler(IdeActions.ACTION_EDITOR_BACKSPACE, SpeqaBackspaceActionHandler(original))
        }
    }
}
