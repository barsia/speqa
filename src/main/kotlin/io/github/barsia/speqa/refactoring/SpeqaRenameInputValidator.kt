package io.github.barsia.speqa.refactoring

import com.intellij.openapi.project.Project
import com.intellij.patterns.ElementPattern
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.refactoring.rename.RenameInputValidatorEx
import com.intellij.util.ProcessingContext
import io.github.barsia.speqa.SpeqaBundle
import io.github.barsia.speqa.model.SpeqaDefaults

class SpeqaRenameInputValidator : RenameInputValidatorEx {

    override fun getPattern(): ElementPattern<out PsiElement> =
        PlatformPatterns.psiElement(PsiFile::class.java)

    override fun isInputValid(newName: String, element: PsiElement, context: ProcessingContext): Boolean {
        val file = (element as? PsiFile)?.virtualFile ?: return true
        val ext = SpeqaDefaults.speqaExtension(file.name) ?: return true
        return newName.endsWith(".$ext")
    }

    override fun getErrorMessage(newName: String, project: Project): String? {
        if (newName.endsWith(".${SpeqaDefaults.TEST_CASE_EXTENSION}")) return null
        if (newName.endsWith(".${SpeqaDefaults.TEST_RUN_EXTENSION}")) return null
        val ext = if (newName.contains(".tc")) SpeqaDefaults.TEST_CASE_EXTENSION
                  else SpeqaDefaults.TEST_RUN_EXTENSION
        return SpeqaBundle.message("rename.error.extensionChanged", ext)
    }
}
