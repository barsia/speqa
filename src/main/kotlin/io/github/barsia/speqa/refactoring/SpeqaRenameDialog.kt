package io.github.barsia.speqa.refactoring

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.refactoring.rename.RenameDialog
import io.github.barsia.speqa.model.SpeqaDefaults
import javax.swing.JComponent

class SpeqaRenameDialog(
    project: Project,
    element: PsiElement,
    nameSuggestionContext: PsiElement?,
    editor: Editor?,
) : RenameDialog(project, element, nameSuggestionContext, editor) {

    override fun createCenterPanel(): JComponent? {
        val panel = super.createCenterPanel()
        val fileName = (psiElement as? PsiFile)?.name ?: return panel
        val stem = SpeqaDefaults.speqaStem(fileName) ?: return panel
        preselectExtension(0, stem.length)
        return panel
    }
}
