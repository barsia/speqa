package io.github.barsia.speqa.toolwindow

import io.github.barsia.speqa.model.Status
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeqaTreeFilterTest {

    @Test
    fun `null filter (All) passes every status`() {
        Status.entries.forEach { assertTrue(matchesStatusFilter(it, null)) }
    }

    @Test
    fun `specific filter passes only the matching status`() {
        assertTrue(matchesStatusFilter(Status.READY, Status.READY))
        assertFalse(matchesStatusFilter(Status.DRAFT, Status.READY))
        assertFalse(matchesStatusFilter(Status.DEPRECATED, Status.READY))
    }

    @Test
    fun `filter holder defaults to All`() {
        assertTrue(SpeqaTreeFilter().status == null)
    }
}
