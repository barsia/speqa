package io.github.barsia.speqa.refactoring

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.refactoring.rename.RenameDialog
import com.intellij.refactoring.rename.RenamePsiElementProcessor
import io.github.barsia.speqa.model.SpeqaDefaults

class SpeqaRenamePsiFileProcessor : RenamePsiElementProcessor() {

    override fun canProcessElement(element: PsiElement): Boolean {
        val file = (element as? PsiFile)?.virtualFile ?: return false
        return SpeqaDefaults.speqaExtension(file.name) != null
    }

    override fun createRenameDialog(
        project: Project,
        element: PsiElement,
        nameSuggestionContext: PsiElement?,
        editor: Editor?,
    ): RenameDialog {
        return SpeqaRenameDialog(project, element, nameSuggestionContext, editor)
    }
}
