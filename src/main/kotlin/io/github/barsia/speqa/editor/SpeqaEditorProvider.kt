package io.github.barsia.speqa.editor

import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.impl.text.PsiAwareTextEditorProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import io.github.barsia.speqa.model.SpeqaDefaults

class SpeqaEditorProvider : FileEditorProvider, DumbAware {

    override fun accept(project: Project, file: VirtualFile): Boolean {
        return file.name.endsWith(".${SpeqaDefaults.TEST_CASE_EXTENSION}")
    }

    override fun createEditor(project: Project, file: VirtualFile): FileEditor {
        val document = FileDocumentManager.getInstance().getDocument(file)
            ?: return PsiAwareTextEditorProvider().createEditor(project, file)

        val textEditor = PsiAwareTextEditorProvider().createEditor(project, file) as TextEditor
        return SpeqaSplitEditor(textEditor, SpeqaPreviewEditor(project, file, document, textEditor.editor))
    }

    override fun getEditorTypeId(): String = "speqa-test-case-editor"

    override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_OTHER_EDITORS
}
