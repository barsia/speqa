package io.github.barsia.speqa.editor

import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegate
import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegate.Result
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiFile
import io.github.barsia.speqa.model.SpeqaDefaults

/**
 * Smart Enter inside `.tc.md` / `.tr.md` step blockquotes.
 *
 *  - On a `<indent>> content` line: insert a continuation `\n<step-indent>> `
 *    so the new expected line aligns with the step number's column width
 *    (3 spaces for `1.`, 4 for `10.`, 5 for `100.`).
 *  - On an empty `<indent>> ` line (caret right after the marker, nothing
 *    else on the line): drop the prefix and park the caret at column 0,
 *    so a second Enter exits the expected-result block.
 *
 * Defers to the platform's default Enter handler in every other case.
 */
class SpeqaBlockquoteEnterHandler : EnterHandlerDelegate {

    override fun preprocessEnter(
        file: PsiFile,
        editor: Editor,
        caretOffset: Ref<Int>,
        caretAdvance: Ref<Int>,
        dataContext: DataContext,
        originalHandler: EditorActionHandler?,
    ): Result {
        if (!isSpeqaFile(file)) return Result.Continue

        val document = editor.document
        val text = document.text
        val caret = caretOffset.get()
        val decision = SpeqaBlockquoteEnter.decide(text, caret) ?: return Result.Continue

        document.replaceString(decision.replaceStart, decision.replaceEnd, decision.replacement)
        // After replaceString the caret model sticks to the insertion point;
        // move it to the position the decision wants. Update the Ref too so
        // the framework's post-delegates bookkeeping stays consistent.
        editor.caretModel.moveToOffset(decision.caretOffset)
        caretOffset.set(decision.caretOffset)
        return Result.Stop
    }

    private fun isSpeqaFile(file: PsiFile): Boolean {
        val name = file.name
        return name.endsWith(".${SpeqaDefaults.TEST_CASE_EXTENSION}") ||
            name.endsWith(".${SpeqaDefaults.TEST_RUN_EXTENSION}")
    }
}
