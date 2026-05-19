package io.github.barsia.speqa.editor.ui.primitives

import com.intellij.openapi.editor.markup.TextAttributes
import java.awt.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownEditablePaneCaretTest {

    @Test
    fun `maps caret back to end when undo removes appended text`() {
        val offset = MarkdownEditablePane.caretOffsetAfterTextSync(
            previousText = "line one\n",
            nextText = "line one",
            previousCaretOffset = "line one\n".length,
        )

        assertEquals("line one".length, offset)
    }

    @Test
    fun `maps caret to edit location when undo removes inserted middle text`() {
        val offset = MarkdownEditablePane.caretOffsetAfterTextSync(
            previousText = "before inserted after",
            nextText = "before after",
            previousCaretOffset = "before inserted".length,
        )

        assertEquals("before ".length, offset)
    }

    @Test
    fun `preserves caret before changed span`() {
        val offset = MarkdownEditablePane.caretOffsetAfterTextSync(
            previousText = "abc inserted",
            nextText = "abc",
            previousCaretOffset = 2,
        )

        assertEquals(2, offset)
    }

    @Test
    fun `skips WYSIWYG refresh for disposed editor`() {
        assertEquals(
            false,
            MarkdownEditablePane.shouldRunMarkdownWysiwygRefresh(
                editorDisposed = true,
                editorIsActive = true,
                refreshAlreadyScheduled = false,
            ),
        )
    }

    @Test
    fun `coalesces scheduled WYSIWYG refreshes`() {
        assertEquals(
            false,
            MarkdownEditablePane.shouldRunMarkdownWysiwygRefresh(
                editorDisposed = false,
                editorIsActive = true,
                refreshAlreadyScheduled = true,
            ),
        )
    }

    @Test
    fun `runs WYSIWYG refresh for live editor when none is scheduled`() {
        assertEquals(
            true,
            MarkdownEditablePane.shouldRunMarkdownWysiwygRefresh(
                editorDisposed = false,
                editorIsActive = true,
                refreshAlreadyScheduled = false,
            ),
        )
    }

    @Test
    fun `skips WYSIWYG refresh for stale embedded editor`() {
        assertEquals(
            false,
            MarkdownEditablePane.shouldRunMarkdownWysiwygRefresh(
                editorDisposed = false,
                editorIsActive = false,
                refreshAlreadyScheduled = false,
            ),
        )
    }

    @Test
    fun `inline code attributes keep markdown colors without boxed text effect`() {
        val source = TextAttributes().apply {
            backgroundColor = Color(12, 34, 56)
            foregroundColor = Color(210, 220, 230)
        }

        val attributes = MarkdownEditablePane.inlineCodeTokenAttributes(source)

        assertEquals(source.backgroundColor, attributes.backgroundColor)
        assertEquals(source.foregroundColor, attributes.foregroundColor)
        assertEquals(source.effectType, attributes.effectType)
        assertEquals(source.effectColor, attributes.effectColor)
    }

    @Test
    fun `inline code fragment boxes do not consume adjacent normal spaces`() {
        val boxes = MarkdownEditablePane.inlineCodeFragmentBoxes(
            characterBoxes = listOf(
                InlineCodeCharacterBox(line = 0, left = 100, right = 128),
                InlineCodeCharacterBox(line = 0, left = 128, right = 160),
                InlineCodeCharacterBox(line = 1, left = 20, right = 44),
                InlineCodeCharacterBox(line = 1, left = 44, right = 80),
            ),
            firstLine = 0,
            firstLineStartGap = MarkdownEditablePane.inlineCodeExternalGap(contentPadding = 2),
            continuationStartPadding = MarkdownEditablePane.inlineCodeLeadingPadding(contentPadding = 2),
            endPadding = MarkdownEditablePane.inlineCodeTrailingPadding(contentPadding = 2),
        )

        assertEquals(
            listOf(
                InlineCodeFragmentBox(line = 0, left = 102, right = 164),
                InlineCodeFragmentBox(line = 1, left = 16, right = 84),
            ),
            boxes,
        )
    }

    @Test
    fun `inline code trailing padding inlay sits after folded closing delimiter`() {
        assertEquals(
            listOf(
                InlinePaddingInlayOffset(offset = 12, relatesToPrecedingText = false, width = 6),
                InlinePaddingInlayOffset(offset = 21, relatesToPrecedingText = true, width = 6),
            ),
            MarkdownEditablePane.inlineCodePaddingInlayOffsets(
                contentStart = 12,
                closeEnd = 21,
                contentPadding = 2,
            ),
        )
    }

    @Test
    fun `code block width hugs content until visible width cap`() {
        assertEquals(
            92,
            MarkdownEditablePane.codeBlockContainerWidth(
                renderedLineRightEdges = listOf(86),
                blockLeft = 0,
                trailingPadding = 6,
                maxWidth = 500,
            ),
        )
        assertEquals(
            120,
            MarkdownEditablePane.codeBlockContainerWidth(
                renderedLineRightEdges = listOf(180),
                blockLeft = 0,
                trailingPadding = 6,
                maxWidth = 120,
            ),
        )
    }
}
