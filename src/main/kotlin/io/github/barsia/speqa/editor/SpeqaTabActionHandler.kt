package io.github.barsia.speqa.editor

import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.intellij.psi.PsiFile
import io.github.barsia.speqa.editor.ui.primitives.ListIndent
import io.github.barsia.speqa.model.SpeqaDefaults

/**
 * Wraps the platform Tab / Shift+Tab handlers for `.tc.md` / `.tr.md` files.
 *
 * When the caret sits on a markdown list item, [indent] shifts the whole item
 * (marker line plus its blockquote / continuation lines) to the left of each
 * line via [ListIndent], so the `>` blockquote marker keeps its position and no
 * spaces are inserted after it. This replaces the bundled IntelliJ Markdown
 * plugin's Tab indent, which reindents child blocks by inserting spaces inside
 * the blockquote (`>    text`) and corrupts the shared scenario step shape.
 *
 * Everything else (selections, non-list lines, non-Speqa files) defers to
 * [original]. Registered via [SpeqaTabActionSetup] at application startup.
 */
class SpeqaTabActionHandler(
    private val original: EditorActionHandler,
    private val indent: Boolean,
) : EditorActionHandler() {

    override fun isEnabledForCaret(editor: Editor, caret: Caret, dataContext: DataContext): Boolean =
        original.isEnabled(editor, caret, dataContext)

    override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext) {
        val file = CommonDataKeys.PSI_FILE.getData(dataContext)
        if (file != null && isSpeqaFile(file) && handleListIndent(editor)) return
        original.execute(editor, caret, dataContext)
    }

    private fun handleListIndent(editor: Editor): Boolean {
        if (editor.selectionModel.hasSelection()) return false
        if (editor.caretModel.caretCount > 1) return false

        val text = editor.document.charsSequence.toString()
        val caret = editor.caretModel.offset
        val result = if (indent) ListIndent.onTab(text, caret) else ListIndent.onShiftTab(text, caret)
        if (result == null) return false

        WriteCommandAction.runWriteCommandAction(editor.project) {
            editor.document.replaceString(0, editor.document.textLength, result.text)
        }
        editor.caretModel.moveToOffset(result.cursor.coerceIn(0, editor.document.textLength))
        return true
    }

    private fun isSpeqaFile(file: PsiFile): Boolean {
        val name = file.name
        return name.endsWith(".${SpeqaDefaults.TEST_CASE_EXTENSION}") ||
            name.endsWith(".${SpeqaDefaults.TEST_RUN_EXTENSION}")
    }
}
