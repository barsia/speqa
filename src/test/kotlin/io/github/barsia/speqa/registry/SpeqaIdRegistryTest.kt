package io.github.barsia.speqa.registry

import org.junit.Assert.*
import org.junit.Test

class SpeqaIdRegistryTest {

    @Test
    fun `nextFreeId returns 1 when empty`() {
        val set = IdSet()
        assertEquals(1, set.nextFreeId())
    }

    @Test
    fun `nextFreeId fills gaps`() {
        val set = IdSet()
        set.register(1)
        set.register(3)
        assertEquals(2, set.nextFreeId())
    }

    @Test
    fun `nextFreeId returns max plus 1 when no gaps`() {
        val set = IdSet()
        set.register(1)
        set.register(2)
        set.register(3)
        assertEquals(4, set.nextFreeId())
    }

    @Test
    fun `isDuplicate false for single use`() {
        val set = IdSet()
        set.register(5)
        assertFalse(set.isDuplicate(5))
    }

    @Test
    fun `isDuplicate true for multiple uses`() {
        val set = IdSet()
        set.register(5)
        set.register(5)
        assertTrue(set.isDuplicate(5))
    }

    @Test
    fun `unregister decrements count`() {
        val set = IdSet()
        set.register(5)
        set.register(5)
        set.unregister(5)
        assertFalse(set.isDuplicate(5))
        assertTrue(set.isUsed(5))
    }

    @Test
    fun `unregister removes when count reaches zero`() {
        val set = IdSet()
        set.register(5)
        set.unregister(5)
        assertFalse(set.isUsed(5))
        assertEquals(1, set.nextFreeId())
    }

    @Test
    fun `clear removes all`() {
        val set = IdSet()
        set.register(1)
        set.register(2)
        set.clear()
        assertEquals(1, set.nextFreeId())
        assertFalse(set.isUsed(1))
    }
}
