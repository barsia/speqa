package io.github.barsia.speqa.editor.ui.primitives

import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownReadOnlyPaneTest {
    @Test
    fun `bold emits strong`() {
        val html = markdownToHtml("**bold**")
        assertTrue(html, html.contains("<strong>"))
    }

    @Test
    fun `em emits em`() {
        val html = markdownToHtml("_em_")
        assertTrue(html, html.contains("<em>"))
    }

    @Test
    fun `code emits code`() {
        val html = markdownToHtml("`code`")
        assertTrue(html, html.contains("<code>"))
    }

    @Test
    fun `strikethrough wraps text`() {
        val html = markdownToHtml("~~del~~")
        assertTrue(html, html.contains("user-del"))
        assertTrue(html, !html.contains("~~"))
    }

    @Test
    fun `nested bold and em both emit`() {
        val html = markdownToHtml("**_nested_**")
        assertTrue(html, html.contains("<strong>"))
        assertTrue(html, html.contains("<em>"))
    }
}
