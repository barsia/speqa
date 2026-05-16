package io.github.barsia.speqa.editor

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.TextEditorWithPreview
import com.intellij.ui.JBSplitter

class SpeqaSplitEditor(
    textEditor: TextEditor,
    previewEditor: FileEditor,
) : TextEditorWithPreview(textEditor, previewEditor, "SpeQA Split Editor", Layout.SHOW_EDITOR_AND_PREVIEW) {
    override fun createSplitter(): JBSplitter = SpeqaSplitHandleSplitter()

    override val isShowFloatingToolbar: Boolean
        get() = false

    override fun setState(state: FileEditorState) = Unit
}
