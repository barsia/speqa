package io.github.barsia.speqa.editor.ui.primitives

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FocusTrailTest {
    @Test
    fun `empty trail pops null`() {
        val trail = FocusTrail()
        assertNull(trail.popPrevious())
    }

    @Test
    fun `single push pops null because no previous`() {
        val trail = FocusTrail()
        trail.push("a")
        assertNull(trail.popPrevious())
        assertEquals(0, trail.size)
    }

    @Test
    fun `duplicate push collapses`() {
        val trail = FocusTrail()
        trail.push("a")
        trail.push("a")
        assertEquals(1, trail.size)
    }

    @Test
    fun `pop previous returns prior target and drops current top`() {
        val trail = FocusTrail()
        trail.push("a")
        trail.push("b")
        trail.push("c")
        assertEquals("b", trail.popPrevious())
        assertEquals(2, trail.size)
        assertEquals("a", trail.popPrevious())
        assertEquals(1, trail.size)
        assertNull(trail.popPrevious())
    }
}
