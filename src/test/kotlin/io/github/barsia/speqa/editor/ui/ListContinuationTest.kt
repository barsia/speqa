package io.github.barsia.speqa.editor.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ListContinuationTest {

    // --- Bullet continuation ---

    @Test
    fun `dash bullet continues on Enter`() {
        val result = ListContinuation.onEnter("- first", cursor = 7)
        assertEquals("- first\n- ", result!!.text)
        assertEquals(10, result.cursor)
    }

    @Test
    fun `asterisk bullet continues on Enter`() {
        val result = ListContinuation.onEnter("* item", cursor = 6)
        assertEquals("* item\n* ", result!!.text)
        assertEquals(9, result.cursor)
    }

    @Test
    fun `empty dash bullet exits list`() {
        val result = ListContinuation.onEnter("- first\n- ", cursor = 10)
        assertEquals("- first\n", result!!.text)
        assertEquals(8, result.cursor)
    }

    @Test
    fun `empty asterisk bullet exits list`() {
        val result = ListContinuation.onEnter("* first\n* ", cursor = 10)
        assertEquals("* first\n", result!!.text)
        assertEquals(8, result.cursor)
    }

    @Test
    fun `no list marker returns null`() {
        val result = ListContinuation.onEnter("plain text", cursor = 10)
        assertNull(result)
    }

    @Test
    fun `cursor mid-line splits and continues bullet`() {
        val result = ListContinuation.onEnter("- hello world", cursor = 7)
        assertEquals("- hello\n-  world", result!!.text)
        assertEquals(10, result.cursor)
    }

    @Test
    fun `bullet on second line continues`() {
        val result = ListContinuation.onEnter("title\n- first", cursor = 13)
        assertEquals("title\n- first\n- ", result!!.text)
        assertEquals(16, result.cursor)
    }

    @Test
    fun `bullet with indentation preserves indent`() {
        val result = ListContinuation.onEnter("  - item", cursor = 8)
        assertEquals("  - item\n  - ", result!!.text)
        assertEquals(13, result.cursor)
    }

    // --- Numbered list continuation ---

    @Test
    fun `numbered list continues with next number`() {
        val result = ListContinuation.onEnter("1. first", cursor = 8)
        assertEquals("1. first\n2. ", result!!.text)
        assertEquals(12, result.cursor)
    }

    @Test
    fun `numbered list increments from any number`() {
        val result = ListContinuation.onEnter("1. a\n2. b\n3. c", cursor = 14)
        assertEquals("1. a\n2. b\n3. c\n4. ", result!!.text)
        assertEquals(18, result.cursor)
    }

    @Test
    fun `empty numbered item exits list`() {
        val result = ListContinuation.onEnter("1. first\n2. ", cursor = 12)
        assertEquals("1. first\n", result!!.text)
        assertEquals(9, result.cursor)
    }

    @Test
    fun `numbered list with indentation preserves indent`() {
        val result = ListContinuation.onEnter("  1. item", cursor = 9)
        assertEquals("  1. item\n  2. ", result!!.text)
        assertEquals(15, result.cursor)
    }

    // --- Edge cases ---

    @Test
    fun `cursor at start of line returns null`() {
        val result = ListContinuation.onEnter("- item", cursor = 0)
        assertNull(result)
    }

    @Test
    fun `empty text returns null`() {
        val result = ListContinuation.onEnter("", cursor = 0)
        assertNull(result)
    }

    @Test
    fun `dash without space is not a list`() {
        val result = ListContinuation.onEnter("-compact", cursor = 8)
        assertNull(result)
    }

    @Test
    fun `number without dot-space is not a list`() {
        val result = ListContinuation.onEnter("1 item", cursor = 6)
        assertNull(result)
    }

    @Test
    fun `multi-digit number continues`() {
        val result = ListContinuation.onEnter("10. tenth item", cursor = 14)
        assertEquals("10. tenth item\n11. ", result!!.text)
        assertEquals(19, result.cursor)
    }

    @Test
    fun `text after cursor preserved on numbered split`() {
        val result = ListContinuation.onEnter("1. hello world", cursor = 8)
        assertEquals("1. hello\n2.  world", result!!.text)
        assertEquals(12, result.cursor)
    }
}
