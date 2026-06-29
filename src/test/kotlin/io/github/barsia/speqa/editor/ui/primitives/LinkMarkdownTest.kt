package io.github.barsia.speqa.editor.ui.primitives

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LinkMarkdownTest {
    @Test
    fun `applyLink wraps selection in a new link`() {
        val result = LinkMarkdown.applyLink("see x", 4, 5, "x", "https://e.com")
        assertEquals("see [x](https://e.com)", result.text)
        assertEquals("x", result.text.substring(result.selectionStart, result.selectionEnd))
    }

    @Test
    fun `applyLink replaces the whole existing link span`() {
        val result = LinkMarkdown.applyLink("a [old](https://o.com) b", 4, 4, "new", "https://n.com")
        assertEquals("a [new](https://n.com) b", result.text)
        assertEquals("new", result.text.substring(result.selectionStart, result.selectionEnd))
    }

    @Test
    fun `linkSpanAt returns the span covering the inline link`() {
        val span = LinkMarkdown.linkSpanAt("a [x](https://e.com) b", 5)
        assertEquals(2..19, span)
        assertEquals("[x](https://e.com)", "a [x](https://e.com) b".substring(span!!.first, span.last + 1))
    }

    @Test
    fun `linkSpanAt returns null outside any link`() {
        assertNull(LinkMarkdown.linkSpanAt("a [x](https://e.com) b", 0))
    }

    @Test
    fun `linkSpanAt ignores images`() {
        assertNull(LinkMarkdown.linkSpanAt("a ![x](https://e.com)", 5))
    }

    @Test
    fun `linkAt returns span, text and url inside a link`() {
        val text = "a [foo](https://e.com) b"
        val at = LinkMarkdown.linkAt(text, 5)!!
        assertEquals(2..21, at.span)
        assertEquals("[foo](https://e.com)", text.substring(at.span.first, at.span.last + 1))
        assertEquals("foo", at.text)
        assertEquals("https://e.com", at.url)
    }

    @Test
    fun `linkAt returns null outside any link`() {
        assertNull(LinkMarkdown.linkAt("a [foo](https://e.com) b", 0))
    }

    @Test
    fun `linkAt ignores images`() {
        assertNull(LinkMarkdown.linkAt("a ![x](https://e.com)", 5))
    }
}
