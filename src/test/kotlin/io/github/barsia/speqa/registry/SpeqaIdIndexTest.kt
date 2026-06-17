package io.github.barsia.speqa.registry

import org.junit.Assert.assertEquals
import org.junit.Test

class SpeqaIdIndexTest {

    @Test
    fun maps_test_case_id_to_tc_key() {
        assertEquals(setOf("TC:73"), SpeqaIdIndex.indexKeysFor("a.tc.md", "---\nid: 73\n---\n"))
    }

    @Test
    fun maps_test_run_id_to_tr_key() {
        assertEquals(setOf("TR:5"), SpeqaIdIndex.indexKeysFor("a.tr.md", "---\nid: 5\n---\n"))
    }

    @Test
    fun no_id_yields_no_keys() {
        assertEquals(emptySet<String>(), SpeqaIdIndex.indexKeysFor("a.tc.md", "---\ntitle: x\n---\n"))
    }

    @Test
    fun non_speqa_file_yields_no_keys() {
        assertEquals(emptySet<String>(), SpeqaIdIndex.indexKeysFor("a.md", "---\nid: 73\n---\n"))
    }

    @Test
    fun key_helper_formats_by_type() {
        assertEquals("TC:73", SpeqaIdIndex.key(IdType.TEST_CASE, 73))
        assertEquals("TR:5", SpeqaIdIndex.key(IdType.TEST_RUN, 5))
    }
}
