package io.github.barsia.speqa.editor.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownTextRendererTest {

    private val color = Color.Black

    @Test
    fun `plain text stays unchanged`() {
        val rendered = renderInlineMarkdown("User account exists in the system", color)

        assertEquals("User account exists in the system", rendered.text)
        assertTrue(rendered.spanStyles.isEmpty())
    }

    @Test
    fun `renders bold text without markdown markers`() {
        val rendered = renderInlineMarkdown("User **account** exists", color)

        assertEquals("User account exists", rendered.text)
        val span = rendered.spanStyles.single()
        assertEquals(5, span.start)
        assertEquals(12, span.end)
        assertEquals(FontWeight.Bold, span.item.fontWeight)
    }

    @Test
    fun `renders italic text without markdown markers`() {
        val rendered = renderInlineMarkdown("User *account* exists", color)

        assertEquals("User account exists", rendered.text)
        val span = rendered.spanStyles.single()
        assertEquals(5, span.start)
        assertEquals(12, span.end)
        assertEquals(FontStyle.Italic, span.item.fontStyle)
    }

    @Test
    fun `renders inline code without markdown markers`() {
        val rendered = renderInlineMarkdown("Use `curl` here", color)

        assertEquals("Use curl here", rendered.text)
        val span = rendered.spanStyles.single()
        assertEquals(4, span.start)
        assertEquals(8, span.end)
        assertEquals(FontFamily.Monospace, span.item.fontFamily)
    }

    @Test
    fun `renders strikethrough text without markdown markers`() {
        val rendered = renderInlineMarkdown("Type ~~into~~ field", color)

        assertEquals("Type into field", rendered.text)
        val span = rendered.spanStyles.single()
        assertEquals(5, span.start)
        assertEquals(9, span.end)
        assertEquals(TextDecoration.LineThrough, span.item.textDecoration)
    }

    @Test
    fun `renders nested bold italic text`() {
        val rendered = renderInlineMarkdown("Type into the **_email_** field", color)

        assertEquals("Type into the email field", rendered.text)
        assertEquals(2, rendered.spanStyles.size)
        assertTrue(rendered.spanStyles.any {
            it.start == 14 && it.end == 19 && it.item.fontWeight == FontWeight.Bold
        })
        assertTrue(rendered.spanStyles.any {
            it.start == 14 && it.end == 19 && it.item.fontStyle == FontStyle.Italic
        })
    }

    @Test
    fun `renders nested strikethrough bold italic text`() {
        val rendered = renderInlineMarkdown("~~into~~ _**~~the~~**_", color)

        assertEquals("into the", rendered.text)
        assertTrue(rendered.spanStyles.any {
            it.start == 0 && it.end == 4 && it.item.textDecoration == TextDecoration.LineThrough
        })
        assertTrue(rendered.spanStyles.any {
            it.start == 5 && it.end == 8 && it.item.textDecoration == TextDecoration.LineThrough
        })
        assertTrue(rendered.spanStyles.any {
            it.start == 5 && it.end == 8 && it.item.fontWeight == FontWeight.Bold
        })
        assertTrue(rendered.spanStyles.any {
            it.start == 5 && it.end == 8 && it.item.fontStyle == FontStyle.Italic
        })
    }

    @Test
    fun `renders multiple formatted spans in one line`() {
        val rendered = renderInlineMarkdown("`curl` with **token** and *profile*", color)

        assertEquals("curl with token and profile", rendered.text)
        assertEquals(3, rendered.spanStyles.size)
        assertTrue(rendered.spanStyles.any {
            it.start == 0 && it.end == 4 && it.item.fontFamily == FontFamily.Monospace
        })
        assertTrue(rendered.spanStyles.any {
            it.start == 10 && it.end == 15 && it.item.fontWeight == FontWeight.Bold
        })
        assertTrue(rendered.spanStyles.any {
            it.start == 20 && it.end == 27 && it.item.fontStyle == FontStyle.Italic
        })
    }

    @Test
    fun `keeps unmatched markers as literal text`() {
        val rendered = renderInlineMarkdown("User **account exists", color)

        assertEquals("User **account exists", rendered.text)
        assertFalse(rendered.spanStyles.any { it.item.fontWeight == FontWeight.Bold })
    }
}
