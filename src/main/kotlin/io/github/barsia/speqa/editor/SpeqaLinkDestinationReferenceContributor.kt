package io.github.barsia.speqa.editor

import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceProvider
import com.intellij.psi.PsiReferenceRegistrar
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceSet
import com.intellij.util.ProcessingContext
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownLinkLabel

class SpeqaLinkDestinationReferenceContributor : PsiReferenceContributor() {
    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        registrar.registerReferenceProvider(psiElement(MarkdownLinkLabel::class.java), SpeqaPathReferenceProvider())
    }
}

private class SpeqaPathReferenceProvider : PsiReferenceProvider() {
    override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
        val label = element as? MarkdownLinkLabel ?: return PsiReference.EMPTY_ARRAY
        val pathText = extractPathText(label) ?: return PsiReference.EMPTY_ARRAY
        return FileReferenceSet(pathText, label, 1, null, true).allReferences as Array<PsiReference>
    }

    private fun extractPathText(element: MarkdownLinkLabel): String? {
        val fileName = element.containingFile?.virtualFile?.name ?: return null
        if (!fileName.endsWith(".tc.md") && !fileName.endsWith(".tr.md")) return null

        val pathText = element.text.removeSurrounding("[", "]")
        return pathText.takeIf(::looksLikePath)
    }

    private fun looksLikePath(path: String): Boolean {
        if (path.isBlank()) return false
        if (path.startsWith("http://") || path.startsWith("https://")) return false
        return path.contains('/') || path.contains('\\') || path.substringAfterLast('.', "").isNotBlank()
    }
}
