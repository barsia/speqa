package io.github.barsia.speqa.run

import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.TextEditorWithPreview
import io.github.barsia.speqa.SpeqaBundle

class TestRunSplitEditor(
    textEditor: TextEditor,
    previewEditor: TestRunEditor,
) : TextEditorWithPreview(textEditor, previewEditor, SpeqaBundle.message("editor.splitTestRun.name")) {
    override val isShowFloatingToolbar: Boolean
        get() = false

    override fun setState(state: FileEditorState) = Unit
}
