package io.github.barsia.speqa.toolwindow

import io.github.barsia.speqa.model.Priority
import io.github.barsia.speqa.model.Status
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeqaTreeFilterTest {

    private fun summary(
        status: Status = Status.DRAFT,
        priority: Priority = Priority.NORMAL,
        tags: Set<String> = emptySet(),
        environments: Set<String> = emptySet(),
    ) = TestCaseSummary("t", status, priority, tags, environments)

    @Test
    fun `empty filter matches everything`() {
        val f = SpeqaTreeFilter()
        assertTrue(f.isEmpty())
        assertEquals(0, f.activeCount())
        assertTrue(matchesFilter(summary(status = Status.READY, priority = Priority.LOW), f))
    }

    @Test
    fun `status facet constrains by equality`() {
        val f = SpeqaTreeFilter().apply { status = Status.READY }
        assertTrue(matchesFilter(summary(status = Status.READY), f))
        assertFalse(matchesFilter(summary(status = Status.DRAFT), f))
    }

    @Test
    fun `priority facet constrains by equality`() {
        val f = SpeqaTreeFilter().apply { priority = Priority.CRITICAL }
        assertTrue(matchesFilter(summary(priority = Priority.CRITICAL), f))
        assertFalse(matchesFilter(summary(priority = Priority.NORMAL), f))
    }

    @Test
    fun `tags match when test case has at least one selected tag (OR)`() {
        val f = SpeqaTreeFilter().apply { addTag("smoke"); addTag("api") }
        assertTrue(matchesFilter(summary(tags = setOf("smoke")), f))
        assertTrue(matchesFilter(summary(tags = setOf("api", "regression")), f))
        assertFalse(matchesFilter(summary(tags = setOf("regression")), f))
        assertFalse(matchesFilter(summary(tags = emptySet()), f))
    }

    @Test
    fun `environment matches with OR semantics`() {
        val f = SpeqaTreeFilter().apply { addEnvironment("Chrome") }
        assertTrue(matchesFilter(summary(environments = setOf("Chrome", "macOS")), f))
        assertFalse(matchesFilter(summary(environments = setOf("Firefox")), f))
    }

    @Test
    fun `facets combine with AND`() {
        val f = SpeqaTreeFilter().apply { status = Status.READY; addTag("smoke") }
        assertTrue(matchesFilter(summary(status = Status.READY, tags = setOf("smoke")), f))
        assertFalse(matchesFilter(summary(status = Status.READY, tags = setOf("api")), f))
        assertFalse(matchesFilter(summary(status = Status.DRAFT, tags = setOf("smoke")), f))
    }

    @Test
    fun `activeCount sums facets and individual tags, clear resets`() {
        val f = SpeqaTreeFilter().apply {
            status = Status.READY
            priority = Priority.MAJOR
            addTag("smoke"); addTag("api")
            addEnvironment("Chrome")
        }
        assertEquals(5, f.activeCount())
        assertFalse(f.isEmpty())
        f.clear()
        assertTrue(f.isEmpty())
        assertEquals(0, f.activeCount())
    }
}
