package io.github.barsia.speqa.editor.ui.primitives

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ListContinuationTest {

    @Test
    fun `enter on a non-empty bullet continues the list with the same marker`() {
        val result = ListContinuation.onEnter("- one", 5)!!
        assertEquals("- one\n- ", result.text)
        // caret lands right after the freshly inserted "- " marker
        assertEquals(8, result.cursor)
    }

    @Test
    fun `enter on a non-empty ordered item inserts the next number`() {
        val result = ListContinuation.onEnter("1. one", 6)!!
        assertEquals("1. one\n2. ", result.text)
        assertEquals(10, result.cursor)
    }

    @Test
    fun `ordered continuation increments the current number rather than renumbering`() {
        // Source number is 7 (not part of a 1..n sequence); the next marker must be 8.
        val result = ListContinuation.onEnter("7. lucky", 8)!!
        assertEquals("7. lucky\n8. ", result.text)
        assertEquals(12, result.cursor)
    }

    @Test
    fun `ordered continuation reads the line the caret is on, not the first line`() {
        val text = "1. a\n2. b"
        val result = ListContinuation.onEnter(text, text.length)!!
        assertEquals("1. a\n2. b\n3. ", result.text)
        assertEquals(13, result.cursor)
    }

    @Test
    fun `enter on an empty bullet item exits the list by removing the marker`() {
        val text = "- one\n- "
        val result = ListContinuation.onEnter(text, text.length)!!
        assertEquals("- one\n", result.text)
        assertEquals(6, result.cursor)
    }

    @Test
    fun `enter on an empty ordered item exits the list by removing the marker`() {
        val text = "1. a\n2. "
        val result = ListContinuation.onEnter(text, text.length)!!
        assertEquals("1. a\n", result.text)
        assertEquals(5, result.cursor)
    }

    @Test
    fun `continuation preserves the leading indentation of the item`() {
        val text = "  - item"
        val result = ListContinuation.onEnter(text, text.length)!!
        assertEquals("  - item\n  - ", result.text)
        assertEquals(13, result.cursor)
    }

    @Test
    fun `enter in the middle of an item splits the remainder onto the new marker`() {
        // caret sits after "- one", before "two"
        val result = ListContinuation.onEnter("- onetwo", 5)!!
        assertEquals("- one\n- two", result.text)
        assertEquals(8, result.cursor)
    }

    @Test
    fun `asterisk bullets are continued too`() {
        val result = ListContinuation.onEnter("* one", 5)!!
        assertEquals("* one\n* ", result.text)
        assertEquals(8, result.cursor)
    }

    @Test
    fun `caret at the very start falls through`() {
        assertNull(ListContinuation.onEnter("- one", 0))
    }

    @Test
    fun `a plain paragraph falls through`() {
        assertNull(ListContinuation.onEnter("hello world", 11))
    }
}
