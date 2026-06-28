package io.github.barsia.speqa.editor.ui.primitives

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project

/**
 * Single Enter handler for the EditorEx-backed Description / Preconditions panes
 * ([MarkdownEditablePane]). Continues a Markdown list at the caret and writes the
 * change inside a [WriteCommandAction] so it actually applies to the document and is
 * undoable. Returns true when Enter was consumed (a list item was continued), false
 * when Enter should fall through to the editor's default behaviour.
 */
internal object MarkdownEnterHandler {

    fun apply(editor: Editor, project: Project): Boolean {
        val document = editor.document
        val text = document.charsSequence.toString()
        val caret = editor.caretModel.offset
        val result = ListContinuation.onEnter(text, caret) ?: return false
        WriteCommandAction.runWriteCommandAction(project) {
            document.replaceString(0, document.textLength, result.text)
        }
        editor.caretModel.moveToOffset(result.cursor.coerceIn(0, document.textLength))
        return true
    }
}
