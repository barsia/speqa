package io.github.barsia.speqa.wizard

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.DirectoryProjectGeneratorBase
import io.github.barsia.speqa.SpeqaBundle
import io.github.barsia.speqa.filetype.SpeqaIcons
import javax.swing.Icon

class SpeqaProjectGenerator : DirectoryProjectGeneratorBase<Any>() {

    override fun getName(): String = SpeqaBundle.message("wizard.projectName")

    override fun getDescription(): String = SpeqaBundle.message("wizard.projectDescription")

    override fun getLogo(): Icon = SpeqaIcons.TestCaseReady

    override fun generateProject(
        project: Project,
        baseDir: VirtualFile,
        settings: Any,
        module: Module,
    ) {
        val sampleTestCase = runWriteAction { SpeqaProjectScaffold.generate(baseDir) } ?: return
        openInitialTestCase(project, sampleTestCase)
    }
}

internal fun openInitialTestCase(project: Project, file: VirtualFile) {
    com.intellij.openapi.startup.StartupManager.getInstance(project).runWhenProjectIsInitialized {
        ApplicationManager.getApplication().invokeLater({
            if (project.isDisposed || !file.isValid) return@invokeLater
            val editorManager = FileEditorManager.getInstance(project)
            editorManager.openFile(file, true)
            editorManager.openFiles
                .filter { it != file }
                .forEach(editorManager::closeFile)
            val psiFile = com.intellij.psi.PsiManager.getInstance(project).findFile(file)
            if (psiFile != null) {
                com.intellij.ide.projectView.ProjectView.getInstance(project).selectPsiElement(psiFile, true)
            }
        }, com.intellij.openapi.application.ModalityState.nonModal(), project.disposed)
    }
}
