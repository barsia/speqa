package io.github.barsia.speqa.editor.ui.attachments

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for the pure [LruMap] data structure that backs
 * [AttachmentImageCache]. Threading and platform integration are out of scope
 * for this follow-up — the cache's eviction policy is what matters for
 * correctness under hover churn.
 */
class AttachmentImageCacheTest {

    @Test
    fun `put under capacity retains all entries`() {
        val m = LruMap<String, Int>(capacity = 3)
        m.put("a", 1)
        m.put("b", 2)
        m.put("c", 3)
        assertEquals(3, m.size)
        assertEquals(1, m.get("a"))
        assertEquals(2, m.get("b"))
        assertEquals(3, m.get("c"))
    }

    @Test
    fun `eldest entry evicted when capacity exceeded`() {
        val m = LruMap<String, Int>(capacity = 2)
        m.put("a", 1)
        m.put("b", 2)
        m.put("c", 3)
        assertEquals(2, m.size)
        assertNull("oldest key should have been evicted", m.get("a"))
        assertNotNull(m.get("b"))
        assertNotNull(m.get("c"))
    }

    @Test
    fun `get refreshes access order so recently used entries survive eviction`() {
        val m = LruMap<String, Int>(capacity = 2)
        m.put("a", 1)
        m.put("b", 2)
        // Touch "a" so "b" becomes the eldest in access order.
        assertEquals(1, m.get("a"))
        m.put("c", 3)
        assertNotNull(m.get("a"))
        assertNull("b should have been evicted because a was touched", m.get("b"))
        assertNotNull(m.get("c"))
    }

    @Test
    fun `clear empties the map`() {
        val m = LruMap<String, Int>(capacity = 4)
        m.put("a", 1)
        m.put("b", 2)
        m.clear()
        assertEquals(0, m.size)
        assertNull(m.get("a"))
    }

    @Test
    fun `keysInOrder reflects access order with eldest first`() {
        val m = LruMap<String, Int>(capacity = 3)
        m.put("a", 1)
        m.put("b", 2)
        m.put("c", 3)
        // Touch "a" — now order becomes b, c, a.
        m.get("a")
        assertEquals(listOf("b", "c", "a"), m.keysInOrder())
    }

    @Test
    fun `cache key differentiates by modification stamp`() {
        val k1 = AttachmentImageCache.Key("file:///img.png", modificationStamp = 1L, width = 100, height = 80)
        val k2 = AttachmentImageCache.Key("file:///img.png", modificationStamp = 2L, width = 100, height = 80)
        assertNotNull(k1)
        assertNotNull(k2)
        assert(k1 != k2) { "keys with different modification stamps must not be equal" }
    }

    @Test
    fun `cache key differentiates by target size`() {
        val k1 = AttachmentImageCache.Key("file:///img.png", 1L, width = 100, height = 80)
        val k2 = AttachmentImageCache.Key("file:///img.png", 1L, width = 200, height = 80)
        assert(k1 != k2) { "keys with different target widths must not be equal" }
    }
}
