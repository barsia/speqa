package io.github.barsia.speqa.editor.ui.primitives

import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownWysiwygRangesTest {
    @Test
    fun `fenced code block range folds opening and closing fences but keeps body`() {
        val text = "before\n```json\n{\"ok\": true}\n```\nafter"

        val range = MarkdownWysiwygRanges.fencedCodeBlocks(text).single()

        assertEquals("```json\n", text.substring(range.openStart, range.openEnd))
        assertEquals("{\"ok\": true}\n", text.substring(range.contentStart, range.contentEnd))
        assertEquals("```", text.substring(range.closeStart, range.closeEnd))
        assertEquals("```\n", text.substring(range.closeStart, range.closeFoldEnd))
    }

    @Test
    fun `fenced code block range supports indented fences`() {
        val text = "1. step\n   ```\n   code\n   ```"

        val range = MarkdownWysiwygRanges.fencedCodeBlocks(text).single()

        assertEquals("   ```\n", text.substring(range.openStart, range.openEnd))
        assertEquals("   code\n", text.substring(range.contentStart, range.contentEnd))
        assertEquals("   ```", text.substring(range.closeStart, range.closeEnd))
        assertEquals(range.closeEnd, range.closeFoldEnd)
    }

    @Test
    fun `fenced code block range folds common fence indent inside body lines`() {
        val text = "1. step\n   ```json\n   same-level\n       extra-indent\n   ```"

        val range = MarkdownWysiwygRanges.fencedCodeBlocks(text).single()

        val folded = range.contentIndentFolds.map { text.substring(it.start, it.end) }
        assertEquals(listOf("   ", "   "), folded)
        assertEquals("same-level", text.substring(range.contentIndentFolds[0].end, range.contentIndentFolds[0].end + "same-level".length))
        assertEquals("    extra-indent", text.substring(range.contentIndentFolds[1].end, range.contentEnd).trimEnd())
    }

    @Test
    fun `backspace at visible code line start does not delete folded indent`() {
        val text = "1. step\n   ```json\n   same-level\n   ```"
        val range = MarkdownWysiwygRanges.fencedCodeBlocks(text).single()
        val caret = range.contentIndentFolds.single().end

        assertEquals(true, MarkdownWysiwygRanges.shouldConsumeHiddenCodeBlockEdit(text, caret, backspace = true))
    }

    @Test
    fun `delete before visible code line start does not delete folded indent`() {
        val text = "1. step\n   ```json\n   same-level\n   ```"
        val range = MarkdownWysiwygRanges.fencedCodeBlocks(text).single()
        val caret = range.contentIndentFolds.single().start

        assertEquals(true, MarkdownWysiwygRanges.shouldConsumeHiddenCodeBlockEdit(text, caret, backspace = false))
    }

    @Test
    fun `backspace after extra code indent stays editable`() {
        val text = "1. step\n   ```json\n       extra-indent\n   ```"
        val range = MarkdownWysiwygRanges.fencedCodeBlocks(text).single()
        val caret = range.contentIndentFolds.single().end + 4

        assertEquals(false, MarkdownWysiwygRanges.shouldConsumeHiddenCodeBlockEdit(text, caret, backspace = true))
    }

    @Test
    fun `backspace at unindented code block start does not delete opening fence`() {
        val text = "```json\nsame-level\n```"
        val range = MarkdownWysiwygRanges.fencedCodeBlocks(text).single()

        assertEquals(true, MarkdownWysiwygRanges.shouldConsumeHiddenCodeBlockEdit(text, range.contentStart, backspace = true))
    }

    @Test
    fun `fenced code block range excludes following paragraph`() {
        val text = "```\ncode\n```\nafter"

        val range = MarkdownWysiwygRanges.fencedCodeBlocks(text).single()

        assertEquals("code\n", text.substring(range.contentStart, range.contentEnd))
        assertEquals("```", text.substring(range.closeStart, range.closeEnd))
        assertEquals("```\n", text.substring(range.closeStart, range.closeFoldEnd))
        assertEquals("after", text.substring(range.closeFoldEnd))
    }

    @Test
    fun `fenced code block does not match closing fence with trailing content on same line`() {
        val text = "```\ncode\n```after"

        assertEquals(emptyList<MarkdownWysiwygRange>(), MarkdownWysiwygRanges.fencedCodeBlocks(text))
    }

    @Test
    fun `inline link range folds brackets and url but keeps link text`() {
        val text = "The [SpeQA plugin](https://plugins.jetbrains.com/x) is installed."

        val range = MarkdownWysiwygRanges.inlineLinks(text).single()

        assertEquals("[", text.substring(range.openStart, range.openEnd))
        assertEquals("SpeQA plugin", text.substring(range.contentStart, range.contentEnd))
        assertEquals("](https://plugins.jetbrains.com/x)", text.substring(range.closeStart, range.closeEnd))
        assertEquals(range.closeEnd, range.closeFoldEnd)
    }

    @Test
    fun `inline link range supports http and multiple links on one line`() {
        val text = "see [a](http://a.com) and [b](https://b.com)"

        val ranges = MarkdownWysiwygRanges.inlineLinks(text)

        assertEquals(listOf("a", "b"), ranges.map { text.substring(it.contentStart, it.contentEnd) })
        assertEquals("](http://a.com)", text.substring(ranges[0].closeStart, ranges[0].closeEnd))
        assertEquals("](https://b.com)", text.substring(ranges[1].closeStart, ranges[1].closeEnd))
    }

    @Test
    fun `inline link range ignores image syntax`() {
        val text = "![alt](https://img.example.com/x.png)"

        assertEquals(emptyList<MarkdownWysiwygRange>(), MarkdownWysiwygRanges.inlineLinks(text))
    }

    @Test
    fun `inline link range ignores non-http destinations`() {
        val text = "[anchor](#section) and [rel](./path/page.md)"

        assertEquals(emptyList<MarkdownWysiwygRange>(), MarkdownWysiwygRanges.inlineLinks(text))
    }
}
