package io.github.barsia.speqa.validation

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import io.github.barsia.speqa.SpeqaBundle
import io.github.barsia.speqa.registry.IdType
import io.github.barsia.speqa.registry.SpeqaIdRegistry

class AssignNextFreeIdFix(private val idType: IdType) : IntentionAction {

    override fun getText(): String = SpeqaBundle.message("annotator.fix.assignNextFreeId")

    override fun getFamilyName(): String = SpeqaBundle.message("annotator.fix.assignNextFreeId")

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
        if (file == null) return false
        return ID_LINE_REGEX.containsMatchIn(file.text)
    }

    override fun startInWriteAction(): Boolean = true

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        if (file == null) return
        val document = PsiDocumentManager.getInstance(project).getDocument(file) ?: return
        val match = ID_LINE_REGEX.find(document.text) ?: return
        val registry = SpeqaIdRegistry.getInstance(project)
        registry.ensureInitialized()
        val nextId = registry.idSet(idType).nextFreeId()
        document.replaceString(match.range.first, match.range.last + 1, "id: $nextId")
        PsiDocumentManager.getInstance(project).commitDocument(document)
    }

    private companion object {
        val ID_LINE_REGEX = Regex("""(?m)^id:\s*\d+\s*$""")
    }
}
