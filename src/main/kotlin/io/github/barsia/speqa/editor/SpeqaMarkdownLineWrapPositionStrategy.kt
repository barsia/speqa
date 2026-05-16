package io.github.barsia.speqa.editor

import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.GenericLineWrapPositionStrategy
import com.intellij.openapi.editor.LineWrapPositionStrategy
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import io.github.barsia.speqa.model.SpeqaDefaults
import org.intellij.plugins.markdown.editor.MarkdownLineWrapPositionStrategy

class SpeqaMarkdownLineWrapPositionStrategy : LineWrapPositionStrategy {
    private val generic = GenericLineWrapPositionStrategy()
    private val markdown = MarkdownLineWrapPositionStrategy()

    override fun calculateWrapPosition(
        document: Document,
        project: Project?,
        startOffset: Int,
        endOffset: Int,
        maxPreferredOffset: Int,
        allowToBeyondMaxPreferredOffset: Boolean,
        isSoftWrap: Boolean,
    ): Int {
        return strategyFor(document).calculateWrapPosition(
            document,
            project,
            startOffset,
            endOffset,
            maxPreferredOffset,
            allowToBeyondMaxPreferredOffset,
            isSoftWrap,
        )
    }

    override fun canWrapLineAtOffset(text: CharSequence, offset: Int): Boolean {
        return markdown.canWrapLineAtOffset(text, offset)
    }

    private fun strategyFor(document: Document): LineWrapPositionStrategy {
        val fileName = FileDocumentManager.getInstance().getFile(document)?.name ?: return markdown
        return if (SpeqaDefaults.speqaExtension(fileName) != null) generic else markdown
    }
}
