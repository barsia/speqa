package io.github.barsia.speqa.editor.ui.primitives

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ListIndentTest {

    @Test
    fun `tab indents ordered item and its blockquote continuation by leading spaces`() {
        val text = """
            1. Navigate to AI Management > Policies and open the default policy
            2. Click the "General" tab
               > The "AI Provider" field displays "JetBrains AI"
        """.trimIndent()
        // caret at the start of line 2 ("2. Click...")
        val cursor = text.indexOf("2. Click")
        val result = ListIndent.onTab(text, cursor)!!

        val expected = """
            1. Navigate to AI Management > Policies and open the default policy
               2. Click the "General" tab
                  > The "AI Provider" field displays "JetBrains AI"
        """.trimIndent()
        assertEquals(expected, result.text)
        // No spaces inserted after the blockquote marker.
        assertEquals(-1, result.text.indexOf(">    "))
    }

    @Test
    fun `tab caret moves right by the indent unit`() {
        val text = "1. first\n2. second"
        val cursor = text.indexOf("2. second")
        val result = ListIndent.onTab(text, cursor)!!
        assertEquals("1. first\n   2. second", result.text)
        // "2. " is 3 chars wide, so caret shifts right by 3.
        assertEquals(cursor + 3, result.cursor)
    }

    @Test
    fun `bullet item indents by two-space marker width`() {
        val text = "- one\n- two"
        val cursor = text.indexOf("- two")
        val result = ListIndent.onTab(text, cursor)!!
        assertEquals("- one\n  - two", result.text)
    }

    @Test
    fun `shift tab outdents item and continuation by one unit`() {
        val text = "1. first\n   2. second\n      > expected"
        val cursor = text.indexOf("2. second")
        val result = ListIndent.onShiftTab(text, cursor)!!
        assertEquals("1. first\n2. second\n   > expected", result.text)
    }

    @Test
    fun `shift tab on already flush item falls through`() {
        val text = "1. first\n2. second"
        val cursor = text.indexOf("2. second")
        assertNull(ListIndent.onShiftTab(text, cursor))
    }

    @Test
    fun `caret on blockquote continuation indents the owning item`() {
        val text = "1. step\n   > expected"
        val cursor = text.indexOf("> expected")
        val result = ListIndent.onTab(text, cursor)!!
        assertEquals("   1. step\n      > expected", result.text)
    }

    @Test
    fun `non-list paragraph falls through`() {
        val text = "just a paragraph\nsecond line"
        val cursor = text.indexOf("second")
        assertNull(ListIndent.onTab(text, cursor))
    }

    @Test
    fun `blank line terminates the item so a following item is untouched`() {
        val text = "1. first\n\n2. second"
        val cursor = text.indexOf("1. first")
        val result = ListIndent.onTab(text, cursor)!!
        assertEquals("   1. first\n\n2. second", result.text)
    }
}
