package io.github.barsia.speqa.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpeqaDefaultsTest {

    // --- speqaExtension ---

    @Test
    fun `speqaExtension returns tc_md for test case file`() {
        assertEquals("tc.md", SpeqaDefaults.speqaExtension("login-flow.tc.md"))
    }

    @Test
    fun `speqaExtension returns tr_md for test run file`() {
        assertEquals("tr.md", SpeqaDefaults.speqaExtension("login-flow_2026-04-15.tr.md"))
    }

    @Test
    fun `speqaExtension returns null for plain markdown`() {
        assertNull(SpeqaDefaults.speqaExtension("readme.md"))
    }

    @Test
    fun `speqaExtension returns null for non-markdown`() {
        assertNull(SpeqaDefaults.speqaExtension("data.json"))
    }

    @Test
    fun `speqaExtension returns null for partial match`() {
        assertNull(SpeqaDefaults.speqaExtension("tc.md"))
    }

    @Test
    fun `speqaExtension handles file with dots in stem`() {
        assertEquals("tc.md", SpeqaDefaults.speqaExtension("v2.login-flow.tc.md"))
    }

    // --- speqaStem ---

    @Test
    fun `speqaStem returns base name for test case`() {
        assertEquals("login-flow", SpeqaDefaults.speqaStem("login-flow.tc.md"))
    }

    @Test
    fun `speqaStem returns base name for test run`() {
        assertEquals("login-flow_2026-04-15", SpeqaDefaults.speqaStem("login-flow_2026-04-15.tr.md"))
    }

    @Test
    fun `speqaStem returns null for non-speqa file`() {
        assertNull(SpeqaDefaults.speqaStem("readme.md"))
    }

    @Test
    fun `speqaStem preserves dots in stem`() {
        assertEquals("v2.login-flow", SpeqaDefaults.speqaStem("v2.login-flow.tc.md"))
    }
}
