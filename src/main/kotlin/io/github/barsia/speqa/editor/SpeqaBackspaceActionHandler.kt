package io.github.barsia.speqa.editor

import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiFile
import io.github.barsia.speqa.model.SpeqaDefaults

/**
 * Wraps the platform's BackSpace action handler for `.tc.md` / `.tr.md` files.
 *
 * When the cursor is right after the `> ` prefix of an indented step blockquote
 * (e.g. `   > <caret>content`), a single Backspace must remove the entire `> `
 * (two characters), not just the space. Without this, the platform deletes only
 * the space, leaving `   >content` — a form that IntelliJ no longer recognises as
 * a blockquote prefix, so subsequent Backspace can no longer un-quote the line.
 *
 * This handler fires BEFORE any [com.intellij.codeInsight.editorActions.BackspaceHandlerDelegate],
 * including IntelliJ's Markdown plugin delegates. It intercepts the relevant case
 * and delegates everything else to [original].
 *
 * Registered via [SpeqaBackspaceActionSetup] at application startup.
 */
class SpeqaBackspaceActionHandler(private val original: EditorActionHandler) : EditorActionHandler() {

    override fun isEnabledForCaret(editor: Editor, caret: Caret, dataContext: DataContext): Boolean =
        original.isEnabled(editor, caret, dataContext)

    override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext) {
        val file = CommonDataKeys.PSI_FILE.getData(dataContext)
        if (file != null && isSpeqaFile(file) && handleBlockquotePrefix(editor)) return
        original.execute(editor, caret, dataContext)
    }

    private fun handleBlockquotePrefix(editor: Editor): Boolean {
        val caretOffset = editor.caretModel.offset
        if (caretOffset == 0) return false
        val text = editor.document.charsSequence
        val len = text.length

        // Case 1: cursor right AFTER `> `  →  `   > <caret>content`
        if (caretOffset >= 2 && text[caretOffset - 1] == ' ' && text[caretOffset - 2] == '>') {
            val lineStart = text.lastIndexOf('\n', (caretOffset - 3).coerceAtLeast(0))
                .let { if (it < 0) 0 else it + 1 }
            if ((lineStart until caretOffset - 2).all { text[it] == ' ' }) {
                WriteCommandAction.runWriteCommandAction(editor.project) {
                    editor.document.deleteString(caretOffset - 2, caretOffset)
                }
                editor.caretModel.moveToOffset(caretOffset - 2)
                return true
            }
        }

        // Case 2: cursor right BEFORE `>`  →  `   <caret>> content`
        // Delete `>` plus one following space if present.
        if (caretOffset < len && text[caretOffset] == '>') {
            val lineStart = text.lastIndexOf('\n', (caretOffset - 1).coerceAtLeast(0))
                .let { if (it < 0) 0 else it + 1 }
            if ((lineStart until caretOffset).all { text[it] == ' ' }) {
                val deleteEnd = if (caretOffset + 1 < len && text[caretOffset + 1] == ' ')
                    caretOffset + 2 else caretOffset + 1
                WriteCommandAction.runWriteCommandAction(editor.project) {
                    editor.document.deleteString(caretOffset, deleteEnd)
                }
                return true
            }
        }

        return false
    }

    private fun isSpeqaFile(file: PsiFile): Boolean {
        val name = file.name
        return name.endsWith(".${SpeqaDefaults.TEST_CASE_EXTENSION}") ||
            name.endsWith(".${SpeqaDefaults.TEST_RUN_EXTENSION}")
    }
}
