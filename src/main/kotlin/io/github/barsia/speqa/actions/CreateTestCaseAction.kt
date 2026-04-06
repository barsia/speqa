package io.github.barsia.speqa.actions

import com.intellij.ide.actions.CreateFileFromTemplateAction
import com.intellij.ide.actions.CreateFileFromTemplateDialog
import com.intellij.ide.fileTemplates.FileTemplateManager
import com.intellij.ide.fileTemplates.FileTemplateUtil
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiFile
import io.github.barsia.speqa.SpeqaBundle
import io.github.barsia.speqa.filetype.SpeqaIcons
import io.github.barsia.speqa.registry.IdType
import io.github.barsia.speqa.registry.SpeqaIdRegistry

class CreateTestCaseAction : CreateFileFromTemplateAction(
    "SpeQA Test Case",
    "Create a new SpeQA manual test case",
    SpeqaIcons.TestCaseDraft,
), DumbAware {
    override fun buildDialog(
        project: Project,
        directory: PsiDirectory,
        builder: CreateFileFromTemplateDialog.Builder,
    ) {
        builder
            .setTitle(SpeqaBundle.message("dialog.newTestCase.title"))
            .addKind("SpeQA Test Case", SpeqaIcons.TestCaseDraft, "SpeQA Test Case.tc.md")
    }

    override fun createFile(name: String, templateName: String, dir: PsiDirectory): PsiFile {
        val targetDir = dir
        val project = dir.project
        val registry = SpeqaIdRegistry.getInstance(project)
        registry.ensureInitialized()
        val nextId = registry.idSet(IdType.TEST_CASE).nextFreeId()

        val template = FileTemplateManager.getInstance(project)
            .getInternalTemplate("SpeQA Test Case.tc.md")
        val props = FileTemplateManager.getInstance(project).defaultProperties.apply {
            setProperty("ID", nextId.toString())
        }
        val fileName = normalizeFileName(name)
        val psiFile = FileTemplateUtil.createFromTemplate(template, fileName, props, targetDir) as PsiFile
        registry.idSet(IdType.TEST_CASE).register(nextId)
        return psiFile
    }

    override fun getActionName(directory: PsiDirectory, newName: String, templateName: String?): String {
        return "Create Speqa Test Case: ${normalizeFileName(newName)}"
    }

    private fun normalizeFileName(name: String): String {
        val trimmed = name.trim()
        return when {
            trimmed.endsWith(".tc.md", ignoreCase = true) -> trimmed
            else -> "$trimmed.tc.md"
        }
    }
}
