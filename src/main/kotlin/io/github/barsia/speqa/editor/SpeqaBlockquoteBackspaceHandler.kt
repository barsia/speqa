package io.github.barsia.speqa.editor

import com.intellij.codeInsight.editorActions.BackspaceHandlerDelegate
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile
import io.github.barsia.speqa.model.SpeqaDefaults

/**
 * Smart Backspace inside `.tc.md` / `.tr.md` step blockquotes.
 *
 * When the cursor is right after the `> ` prefix of an indented blockquote line
 * (e.g. `   > <caret>content`), the platform's default Backspace deletes only
 * the space, producing `   >content` — a state where IntelliJ's blockquote
 * un-indent no longer works correctly. This handler detects that the deleted
 * character was the space of a `> ` prefix and removes the `>` as well, so a
 * single Backspace fully un-quotes the blockquote line.
 *
 * Defers to the platform's default Backspace in every other case.
 */
class SpeqaBlockquoteBackspaceHandler : BackspaceHandlerDelegate() {

    override fun beforeCharDeleted(c: Char, file: PsiFile, editor: Editor) = Unit

    override fun charDeleted(c: Char, file: PsiFile, editor: Editor): Boolean {
        if (!isSpeqaFile(file)) return false
        val caret = editor.caretModel.offset
        val text = editor.document.charsSequence
        val decision = SpeqaBlockquoteBackspace.decide(text, caret, c) ?: return false
        ApplicationManager.getApplication().runWriteAction {
            editor.document.deleteString(decision.deleteStart, decision.deleteEnd)
        }
        editor.caretModel.moveToOffset(decision.caretOffset)
        return true
    }

    private fun isSpeqaFile(file: PsiFile): Boolean {
        val name = file.name
        return name.endsWith(".${SpeqaDefaults.TEST_CASE_EXTENSION}") ||
            name.endsWith(".${SpeqaDefaults.TEST_RUN_EXTENSION}")
    }

}
