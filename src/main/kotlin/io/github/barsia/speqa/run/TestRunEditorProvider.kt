package io.github.barsia.speqa.run

import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.impl.text.PsiAwareTextEditorProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

class TestRunEditorProvider : FileEditorProvider, DumbAware {

    override fun accept(project: Project, file: VirtualFile): Boolean = TestRunSupport.isTestRunFile(file)

    override fun createEditor(project: Project, file: VirtualFile): FileEditor {
        val document = FileDocumentManager.getInstance().getDocument(file)
            ?: return PsiAwareTextEditorProvider().createEditor(project, file)

        val initialRun = TestRunSupport.parseTestRunOrNull(document.text)
            ?: return PsiAwareTextEditorProvider().createEditor(project, file)

        val textEditor = PsiAwareTextEditorProvider().createEditor(project, file) as TextEditor
        return TestRunSplitEditor(
            textEditor = textEditor,
            previewEditor = TestRunEditor(project, file, document, initialRun, textEditor.editor),
        )
    }

    override fun getEditorTypeId(): String = "speqa-test-run-editor"

    override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_OTHER_EDITORS
}
