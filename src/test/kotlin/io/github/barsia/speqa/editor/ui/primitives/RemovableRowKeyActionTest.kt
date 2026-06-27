package io.github.barsia.speqa.editor.ui.primitives

import java.awt.event.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Test

class RemovableRowKeyActionTest {
    @Test fun `enter and space activate`() {
        val c = mutableListOf<String>()
        assertEquals(true, removableRowKeyAction(KeyEvent.VK_ENTER, { c.add("a") }, { c.add("e") }, { c.add("d") }))
        assertEquals(true, removableRowKeyAction(KeyEvent.VK_SPACE, { c.add("a") }, { c.add("e") }, { c.add("d") }))
        assertEquals(listOf("a", "a"), c)
    }
    @Test fun `f2 edits only when an edit handler is present`() {
        val c = mutableListOf<String>()
        assertEquals(true, removableRowKeyAction(KeyEvent.VK_F2, { c.add("a") }, { c.add("e") }, { c.add("d") }))
        assertEquals(false, removableRowKeyAction(KeyEvent.VK_F2, { c.add("a") }, null, { c.add("d") }))
        assertEquals(listOf("e"), c)
    }
    @Test fun `delete and backspace remove`() {
        val c = mutableListOf<String>()
        assertEquals(true, removableRowKeyAction(KeyEvent.VK_DELETE, { c.add("a") }, { c.add("e") }, { c.add("d") }))
        assertEquals(true, removableRowKeyAction(KeyEvent.VK_BACK_SPACE, { c.add("a") }, { c.add("e") }, { c.add("d") }))
        assertEquals(listOf("d", "d"), c)
    }
    @Test fun `unhandled key returns false`() {
        assertEquals(false, removableRowKeyAction(KeyEvent.VK_A, {}, {}, {}))
    }
}
