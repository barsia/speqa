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

    @Test
    fun `linkUrlAt returns url when offset is inside the link text`() {
        val text = "The [SpeQA plugin](https://plugins.jetbrains.com/x) is installed."
        val range = MarkdownWysiwygRanges.inlineLinks(text).single()

        assertEquals("https://plugins.jetbrains.com/x", MarkdownWysiwygRanges.linkUrlAt(text, range.contentStart))
        val mid = (range.contentStart + range.contentEnd) / 2
        assertEquals("https://plugins.jetbrains.com/x", MarkdownWysiwygRanges.linkUrlAt(text, mid))
        assertEquals("https://plugins.jetbrains.com/x", MarkdownWysiwygRanges.linkUrlAt(text, range.contentEnd))
    }

    @Test
    fun `linkUrlAt returns null outside any link text`() {
        val text = "The [SpeQA plugin](https://plugins.jetbrains.com/x) is installed."

        assertEquals(null, MarkdownWysiwygRanges.linkUrlAt(text, 0))
        assertEquals(null, MarkdownWysiwygRanges.linkUrlAt(text, text.length))
    }

    @Test
    fun `linkUrlAt resolves the correct url among multiple links`() {
        val text = "see [a](http://a.com) and [b](https://b.com)"
        val ranges = MarkdownWysiwygRanges.inlineLinks(text)

        assertEquals("http://a.com", MarkdownWysiwygRanges.linkUrlAt(text, ranges[0].contentStart))
        assertEquals("https://b.com", MarkdownWysiwygRanges.linkUrlAt(text, ranges[1].contentStart))
    }

    @Test
    fun `linkUrlAt ignores image and non-http destinations`() {
        val image = "![alt](https://img.example.com/x.png)"
        assertEquals(null, MarkdownWysiwygRanges.linkUrlAt(image, 4))

        val relative = "[rel](./path/page.md)"
        assertEquals(null, MarkdownWysiwygRanges.linkUrlAt(relative, 2))
    }

    @Test
    fun `linkUrlAtIconOffset returns url at the close end where the open-link icon sits`() {
        val text = "The [SpeQA plugin](https://plugins.jetbrains.com/x) is installed."
        val range = MarkdownWysiwygRanges.inlineLinks(text).single()

        assertEquals(
            "https://plugins.jetbrains.com/x",
            MarkdownWysiwygRanges.linkUrlAtIconOffset(text, range.closeEnd),
        )
    }

    @Test
    fun `linkUrlAtIconOffset does not resolve at the content end inside the folded close region`() {
        val text = "The [SpeQA plugin](https://plugins.jetbrains.com/x) is installed."
        val range = MarkdownWysiwygRanges.inlineLinks(text).single()

        // contentEnd is the start of the collapsed `](url)` fold, where the icon would be hidden.
        assertEquals(null, MarkdownWysiwygRanges.linkUrlAtIconOffset(text, range.contentEnd))
    }

    @Test
    fun `linkUrlAtIconOffset resolves the correct url among multiple links`() {
        val text = "see [a](http://a.com) and [b](https://b.com)"
        val ranges = MarkdownWysiwygRanges.inlineLinks(text)

        assertEquals("http://a.com", MarkdownWysiwygRanges.linkUrlAtIconOffset(text, ranges[0].closeEnd))
        assertEquals("https://b.com", MarkdownWysiwygRanges.linkUrlAtIconOffset(text, ranges[1].closeEnd))
    }

    @Test
    fun `linkUrlAtIconOffset returns null when the offset is not a link close end`() {
        val text = "The [SpeQA plugin](https://plugins.jetbrains.com/x) is installed."
        val range = MarkdownWysiwygRanges.inlineLinks(text).single()

        assertEquals(null, MarkdownWysiwygRanges.linkUrlAtIconOffset(text, range.contentStart))
        assertEquals(null, MarkdownWysiwygRanges.linkUrlAtIconOffset(text, 0))
        assertEquals(null, MarkdownWysiwygRanges.linkUrlAtIconOffset(text, text.length))
    }

    @Test
    fun `linkUrlAtIconOffset ignores image and non-http destinations`() {
        assertEquals(null, MarkdownWysiwygRanges.linkUrlAtIconOffset("![alt](https://img.example.com/x.png)", 5))
        assertEquals(null, MarkdownWysiwygRanges.linkUrlAtIconOffset("[rel](./path/page.md)", 4))
    }
}
