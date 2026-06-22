package io.github.barsia.speqa.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpeqaBlockquoteBackspaceTest {

    // ── Trigger conditions ────────────────────────────────────────────────────

    @Test
    fun `removes arrow when space of blockquote prefix was just deleted`() {
        // Original line: "   > 1. AI is available"
        // User pressed Backspace at "> <caret>1. AI..." — platform deleted the space.
        // Text is now "   >1. AI is available", caret right after `>`.
        val text = "1. Click\n   >1. AI is available"
        val caret = text.indexOf(">") + 1

        val d = SpeqaBlockquoteBackspace.decide(text, caret, ' ') ?: error("expected decision")

        assertEquals(caret - 1, d.deleteStart)   // position of `>`
        assertEquals(caret, d.deleteEnd)
        assertEquals(caret - 1, d.caretOffset)   // cursor ends up before content
    }

    @Test
    fun `decision positions produce correct text when applied`() {
        val text = "1. Click\n   >content"
        val caret = text.indexOf(">") + 1

        val d = SpeqaBlockquoteBackspace.decide(text, caret, ' ') ?: error("expected decision")

        val result = text.removeRange(d.deleteStart, d.deleteEnd)
        assertEquals("1. Click\n   content", result)
        assertEquals(text.indexOf(">"), d.caretOffset)
    }

    @Test
    fun `works with 4-space indent for a two-digit step`() {
        val text = "10. Click\n    >content"
        val caret = text.indexOf(">") + 1

        val d = SpeqaBlockquoteBackspace.decide(text, caret, ' ') ?: error("expected decision")

        assertEquals(caret - 1, d.deleteStart)
        assertEquals(caret - 1, d.caretOffset)
    }

    @Test
    fun `works on an empty blockquote line (only arrow remained after space deletion)`() {
        // Original: "1. Click\n   > " — user Backspaced the trailing space.
        // Text is now "1. Click\n   >", caret right after `>`.
        val text = "1. Click\n   >"
        val caret = text.length

        val d = SpeqaBlockquoteBackspace.decide(text, caret, ' ') ?: error("expected decision")

        val result = text.removeRange(d.deleteStart, d.deleteEnd)
        assertEquals("1. Click\n   ", result)
    }

    // ── Case 2: cursor before `>` (platform deleted an indent space) ─────────

    @Test
    fun `Case2 removes arrow and space when cursor is before arrow with space after it`() {
        // Platform deleted the third indent space: `   <caret>> 1.` → `  <caret>> 1.`
        val text = "1. Click\n  > 1. AI shown"
        val caret = text.indexOf(">")   // cursor right before `>`

        val d = SpeqaBlockquoteBackspace.decide(text, caret, ' ') ?: error("expected decision")

        assertEquals(caret, d.deleteStart)
        assertEquals(caret + 2, d.deleteEnd)   // deletes `> `
        assertEquals(caret, d.caretOffset)
        val result = text.removeRange(d.deleteStart, d.deleteEnd)
        assertEquals("1. Click\n  1. AI shown", result)
    }

    @Test
    fun `Case2 removes only arrow when no space follows (platform already ate the space)`() {
        // IntelliJ Markdown deleted the indent space AND the space after `>` in one go.
        // Post-deletion state: `  <caret>>1.` — cursor before `>`, content directly follows.
        val text = "1. Click\n  >1. AI shown"
        val caret = text.indexOf(">")

        val d = SpeqaBlockquoteBackspace.decide(text, caret, ' ') ?: error("expected decision")

        assertEquals(caret, d.deleteStart)
        assertEquals(caret + 1, d.deleteEnd)   // deletes only `>`
        assertEquals(caret, d.caretOffset)
        val result = text.removeRange(d.deleteStart, d.deleteEnd)
        assertEquals("1. Click\n  1. AI shown", result)
    }

    @Test
    fun `Case2 defers when non-space chars precede the arrow`() {
        val text = "1. Click\n  foo >content"
        val caret = text.indexOf(">")

        assertNull(SpeqaBlockquoteBackspace.decide(text, caret, ' '))
    }

    // ── Guard conditions — must return null ───────────────────────────────────

    @Test
    fun `defers when deleted char was not a space`() {
        val text = "1. Click\n   >1. AI"
        val caret = text.indexOf(">") + 1

        assertNull(SpeqaBlockquoteBackspace.decide(text, caret, '>'))
    }

    @Test
    fun `defers when char before cursor is not the blockquote arrow`() {
        // Cursor is inside the content, not right after `>`
        val text = "1. Click\n   > AI is available"
        val caret = text.indexOf("AI")   // well inside content

        assertNull(SpeqaBlockquoteBackspace.decide(text, caret, ' '))
    }

    @Test
    fun `defers when non-space chars precede the arrow on the line`() {
        val text = "1. Click\n   foo >content"
        val caret = text.indexOf(">") + 1

        assertNull(SpeqaBlockquoteBackspace.decide(text, caret, ' '))
    }

    @Test
    fun `defers when caret is at offset 0`() {
        val text = "content"
        assertNull(SpeqaBlockquoteBackspace.decide(text, 0, ' '))
    }

    @Test
    fun `defers when only arrow is on the line but char before cursor is a space not arrow`() {
        // Cursor after a space that is not adjacent to `>`
        val text = "1. Click\n   > some text "
        val caret = text.length   // after trailing space inside content

        assertNull(SpeqaBlockquoteBackspace.decide(text, caret, ' '))
    }
}
