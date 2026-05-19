package io.github.barsia.speqa.editor.ui.primitives

import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownSelectionFormatterTest {

    @Test
    fun `bold wraps selected text`() {
        val result = MarkdownSelectionFormatter.apply(
            text = "run login flow",
            selectionStart = 4,
            selectionEnd = 9,
            action = MarkdownFormatAction.BOLD,
        )

        assertEquals("run **login** flow", result.text)
        assertEquals(4, result.selectionStart)
        assertEquals(13, result.selectionEnd)
    }

    @Test
    fun `bold unwraps selected text when already bold`() {
        val result = MarkdownSelectionFormatter.apply(
            text = "run **login** flow",
            selectionStart = 6,
            selectionEnd = 11,
            action = MarkdownFormatAction.BOLD,
        )

        assertEquals("run login flow", result.text)
        assertEquals(4, result.selectionStart)
        assertEquals(9, result.selectionEnd)
    }

    @Test
    fun `bold unwraps selection that includes delimiters`() {
        val result = MarkdownSelectionFormatter.apply(
            text = "run **login** flow",
            selectionStart = 4,
            selectionEnd = 13,
            action = MarkdownFormatAction.BOLD,
        )

        assertEquals("run login flow", result.text)
        assertEquals(4, result.selectionStart)
        assertEquals(9, result.selectionEnd)
    }

    @Test
    fun `italic wraps selected text`() {
        val result = MarkdownSelectionFormatter.apply("before text after", 7, 11, MarkdownFormatAction.ITALIC)

        assertEquals("before _text_ after", result.text)
    }

    @Test
    fun `italic unwraps selected text when already italic`() {
        val result = MarkdownSelectionFormatter.apply("before _text_ after", 8, 12, MarkdownFormatAction.ITALIC)

        assertEquals("before text after", result.text)
        assertEquals(7, result.selectionStart)
        assertEquals(11, result.selectionEnd)
    }

    @Test
    fun `strike wraps selected text`() {
        val result = MarkdownSelectionFormatter.apply("before text after", 7, 11, MarkdownFormatAction.STRIKE)

        assertEquals("before ~~text~~ after", result.text)
    }

    @Test
    fun `strike unwraps selected text when already struck`() {
        val result = MarkdownSelectionFormatter.apply("before ~~text~~ after", 9, 13, MarkdownFormatAction.STRIKE)

        assertEquals("before text after", result.text)
        assertEquals(7, result.selectionStart)
        assertEquals(11, result.selectionEnd)
    }

    @Test
    fun `inline code wraps selected text`() {
        val result = MarkdownSelectionFormatter.apply("click button", 6, 12, MarkdownFormatAction.INLINE_CODE)

        assertEquals("click `button`", result.text)
    }

    @Test
    fun `inline code unwraps selected text when already code`() {
        val result = MarkdownSelectionFormatter.apply("click `button`", 7, 13, MarkdownFormatAction.INLINE_CODE)

        assertEquals("click button", result.text)
        assertEquals(6, result.selectionStart)
        assertEquals(12, result.selectionEnd)
    }

    @Test
    fun `code block fences selected text`() {
        val result = MarkdownSelectionFormatter.apply("payload", 0, 7, MarkdownFormatAction.CODE_BLOCK)

        assertEquals("```\npayload\n```", result.text)
    }

    @Test
    fun `code block expands partial selection to whole line`() {
        val result = MarkdownSelectionFormatter.apply(
            text = "before\nselect this line\nafter",
            selectionStart = "before\nselect".length,
            selectionEnd = "before\nselect this".length,
            action = MarkdownFormatAction.CODE_BLOCK,
        )

        assertEquals("before\n```\nselect this line\n```\nafter", result.text)
        assertEquals("before\n".length, result.selectionStart)
        assertEquals("before\n```\nselect this line\n```".length, result.selectionEnd)
    }

    @Test
    fun `code block unwraps when selection is inside fenced block`() {
        val text = "before\n```\nselect this line\n```\nafter"

        val result = MarkdownSelectionFormatter.apply(
            text = text,
            selectionStart = "before\n```\nselect".length,
            selectionEnd = "before\n```\nselect this".length,
            action = MarkdownFormatAction.CODE_BLOCK,
        )

        assertEquals("before\nselect this line\nafter", result.text)
        assertEquals("before\n".length, result.selectionStart)
        assertEquals("before\nselect this line".length, result.selectionEnd)
    }

    @Test
    fun `bullet list prefixes each selected line and preserves indentation`() {
        val result = MarkdownSelectionFormatter.apply(
            text = "first\n  second",
            selectionStart = 0,
            selectionEnd = "first\n  second".length,
            action = MarkdownFormatAction.BULLET_LIST,
        )

        assertEquals("- first\n  - second", result.text)
    }

    @Test
    fun `bullet list removes bullet markers when all selected lines are bullets`() {
        val result = MarkdownSelectionFormatter.apply(
            text = "- first\n  - second",
            selectionStart = 0,
            selectionEnd = "- first\n  - second".length,
            action = MarkdownFormatAction.BULLET_LIST,
        )

        assertEquals("first\n  second", result.text)
    }

    @Test
    fun `bullet list expands partial selection to whole line`() {
        val result = MarkdownSelectionFormatter.apply(
            text = "before\nselect this line\nafter",
            selectionStart = "before\nselect".length,
            selectionEnd = "before\nselect this".length,
            action = MarkdownFormatAction.BULLET_LIST,
        )

        assertEquals("before\n- select this line\nafter", result.text)
        assertEquals("before\n".length, result.selectionStart)
        assertEquals("before\n- select this line".length, result.selectionEnd)
    }

    @Test
    fun `numbered list prefixes each selected line`() {
        val result = MarkdownSelectionFormatter.apply(
            text = "first\nsecond",
            selectionStart = 0,
            selectionEnd = "first\nsecond".length,
            action = MarkdownFormatAction.NUMBERED_LIST,
        )

        assertEquals("1. first\n2. second", result.text)
    }

    @Test
    fun `numbered list removes number markers when all selected lines are numbered`() {
        val result = MarkdownSelectionFormatter.apply(
            text = "1. first\n2. second",
            selectionStart = 0,
            selectionEnd = "1. first\n2. second".length,
            action = MarkdownFormatAction.NUMBERED_LIST,
        )

        assertEquals("first\nsecond", result.text)
    }
}
