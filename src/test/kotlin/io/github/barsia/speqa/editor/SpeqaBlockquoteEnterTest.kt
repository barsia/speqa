package io.github.barsia.speqa.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpeqaBlockquoteEnterTest {

    @Test
    fun `continues blockquote with 3-space indent for single-digit step`() {
        val text = "1. Click\n   > Result"
        val caret = text.length

        val d = SpeqaBlockquoteEnter.decide(text, caret) ?: error("expected non-null")

        assertEquals(caret, d.replaceStart)
        assertEquals(caret, d.replaceEnd)
        assertEquals("\n   > ", d.replacement)
        assertEquals(caret + "\n   > ".length, d.caretOffset)
    }

    @Test
    fun `continues blockquote with 4-space indent for two-digit step`() {
        val text = "10. Click\n    > Result"
        val caret = text.length

        val d = SpeqaBlockquoteEnter.decide(text, caret) ?: error("expected non-null")

        assertEquals("\n    > ", d.replacement)
    }

    @Test
    fun `continues blockquote with 5-space indent for three-digit step`() {
        val text = "100. Click\n     > Result"
        val caret = text.length

        val d = SpeqaBlockquoteEnter.decide(text, caret) ?: error("expected non-null")

        assertEquals("\n     > ", d.replacement)
    }

    @Test
    fun `splits blockquote line in the middle by inserting continuation at caret`() {
        val text = "1. Click\n   > Foo Bar"
        val caret = text.indexOf("Bar")

        val d = SpeqaBlockquoteEnter.decide(text, caret) ?: error("expected non-null")

        assertEquals(caret, d.replaceStart)
        assertEquals(caret, d.replaceEnd)
        assertEquals("\n   > ", d.replacement)
    }

    @Test
    fun `Enter on empty blockquote line erases the prefix and parks caret at column 0`() {
        val text = "10. Click\n    > "
        val caret = text.length
        val lineStart = text.indexOf('\n') + 1

        val d = SpeqaBlockquoteEnter.decide(text, caret) ?: error("expected non-null")

        assertEquals(lineStart, d.replaceStart)
        assertEquals(text.length, d.replaceEnd)
        assertEquals("", d.replacement)
        assertEquals(lineStart, d.caretOffset)
    }

    @Test
    fun `Enter on blockquote line with only marker and no trailing space also exits`() {
        val text = "1. Click\n   >"
        val caret = text.length
        val lineStart = text.indexOf('\n') + 1

        val d = SpeqaBlockquoteEnter.decide(text, caret) ?: error("expected non-null")

        assertEquals("", d.replacement)
        assertEquals(lineStart, d.caretOffset)
    }

    @Test
    fun `step indent is taken from the nearest preceding numbered step`() {
        val text = """
            |1. First step
            |   > Done
            |
            |10. Tenth step
            |    > Result
        """.trimMargin()
        val caret = text.length

        val d = SpeqaBlockquoteEnter.decide(text, caret) ?: error("expected non-null")

        assertEquals("\n    > ", d.replacement)
    }

    @Test
    fun `defers to default Enter when current line is not a blockquote`() {
        val text = "1. Click\n   regular continuation"
        val caret = text.length

        assertNull(SpeqaBlockquoteEnter.decide(text, caret))
    }

    @Test
    fun `defers to default Enter when blockquote has no preceding numbered step`() {
        val text = "Some description\n> a quote in description"
        val caret = text.length

        assertNull(SpeqaBlockquoteEnter.decide(text, caret))
    }

    @Test
    fun `defers to default Enter when caret sits inside the blockquote prefix`() {
        val text = "1. Click\n   > Foo"
        val lineStart = text.indexOf('\n') + 1
        val caret = lineStart + 1

        assertNull(SpeqaBlockquoteEnter.decide(text, caret))
    }

    @Test
    fun `continues inline sub-step on parent line by inserting next number with parent-aligned indent`() {
        val text = "2. 1. Click in the search field"
        val caret = text.length

        val d = SpeqaBlockquoteEnter.decide(text, caret) ?: error("expected non-null")

        assertEquals(caret, d.replaceStart)
        assertEquals(caret, d.replaceEnd)
        assertEquals("\n   2. ", d.replacement)
        assertEquals(caret + "\n   2. ".length, d.caretOffset)
    }

    @Test
    fun `continues indented sub-step on its own line by inserting next number`() {
        val text = "2. 1. Click\n   2. Type something"
        val caret = text.length

        val d = SpeqaBlockquoteEnter.decide(text, caret) ?: error("expected non-null")

        assertEquals("\n   3. ", d.replacement)
    }

    @Test
    fun `Enter on empty indented sub-step replaces the prefix with a blockquote prefix`() {
        // 2. 1. Click...
        //    2. <caret>          ← user just typed `2. ` and hits Enter
        val text = "2. 1. Click\n   2. "
        val caret = text.length
        val lineStart = text.indexOf('\n') + 1

        val d = SpeqaBlockquoteEnter.decide(text, caret) ?: error("expected non-null")

        // Replace the whole "   2. " line with "   > ", caret right after `> `.
        assertEquals(lineStart, d.replaceStart)
        assertEquals(text.length, d.replaceEnd)
        assertEquals("   > ", d.replacement)
        assertEquals(lineStart + "   > ".length, d.caretOffset)
    }

    @Test
    fun `sub-step continue uses 4-space indent under a two-digit parent`() {
        val text = "10. 1. First<caret>"
        val caret = text.indexOf("<caret>")
        val cleaned = text.replace("<caret>", "")

        val d = SpeqaBlockquoteEnter.decide(cleaned, caret) ?: error("expected non-null")

        assertEquals("\n    2. ", d.replacement)
    }

    @Test
    fun `defers to default Enter when blockquote line lives inside a fenced code block`() {
        val text = """
            |1. Click
            |   ```
            |   > sample json line
            |   ```
        """.trimMargin()
        val caret = text.indexOf("sample json line") + "sample json line".length

        assertNull(SpeqaBlockquoteEnter.decide(text, caret))
    }
}
