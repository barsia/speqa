package io.github.barsia.speqa.editor.ui.primitives

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The inline-code span the caret is currently editing must be exempt from delimiter
 * folding, otherwise collapsing the closing-backtick fold ejects the caret past it
 * (the "caret jumps out of inline code while typing" bug).
 *
 * Text "a `code` b": backtick at index 2 and 7, content "code" at 3..6.
 */
class InlineCodeCaretSpanTest {

    private val text = "a `code` b"

    @Test
    fun `caret in the middle of inline code returns that span`() {
        assertEquals(2..7, MarkdownWysiwygRanges.inlineCodeSpanAt(text, 5))
    }

    @Test
    fun `caret right after the opening backtick is inside`() {
        assertEquals(2..7, MarkdownWysiwygRanges.inlineCodeSpanAt(text, 3))
    }

    @Test
    fun `caret right before the closing backtick is inside and must not be ejected`() {
        assertEquals(2..7, MarkdownWysiwygRanges.inlineCodeSpanAt(text, 7))
    }

    @Test
    fun `caret after the closing backtick is outside`() {
        assertNull(MarkdownWysiwygRanges.inlineCodeSpanAt(text, 8))
    }

    @Test
    fun `caret in plain text is outside`() {
        assertNull(MarkdownWysiwygRanges.inlineCodeSpanAt("plain", 3))
    }
}
