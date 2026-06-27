package io.github.barsia.speqa.toolwindow

import io.github.barsia.speqa.model.Priority
import io.github.barsia.speqa.model.RunResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TestRunTreeFilterTest {

    private fun summary(
        result: RunResult = RunResult.NOT_STARTED,
        priority: Priority? = null,
        tags: Set<String> = emptySet(),
        environments: Set<String> = emptySet(),
    ) = TestRunSummary("t", result, priority, tags, environments)

    @Test
    fun `empty filter matches everything`() {
        val f = TestRunTreeFilter()
        assertTrue(f.isEmpty())
        assertEquals(0, f.activeCount())
        assertTrue(matchesRunFilter(summary(result = RunResult.PASSED, priority = Priority.LOW), f))
    }

    @Test
    fun `result facet constrains by equality`() {
        val f = TestRunTreeFilter().apply { result = RunResult.FAILED }
        assertTrue(matchesRunFilter(summary(result = RunResult.FAILED), f))
        assertFalse(matchesRunFilter(summary(result = RunResult.PASSED), f))
    }

    @Test
    fun `priority facet excludes runs with no priority`() {
        val f = TestRunTreeFilter().apply { priority = Priority.CRITICAL }
        assertTrue(matchesRunFilter(summary(priority = Priority.CRITICAL), f))
        assertFalse(matchesRunFilter(summary(priority = null), f))
    }

    @Test
    fun `tags and environment use OR within facet`() {
        val f = TestRunTreeFilter().apply { addTag("smoke"); addEnvironment("Chrome") }
        assertTrue(matchesRunFilter(summary(tags = setOf("smoke"), environments = setOf("Chrome")), f))
        assertFalse(matchesRunFilter(summary(tags = setOf("api"), environments = setOf("Chrome")), f))
    }

    @Test
    fun `facets combine with AND and clear resets`() {
        val f = TestRunTreeFilter().apply {
            result = RunResult.PASSED
            priority = Priority.MAJOR
            addTag("smoke")
            addEnvironment("Chrome")
        }
        assertEquals(4, f.activeCount())
        assertTrue(matchesRunFilter(summary(result = RunResult.PASSED, priority = Priority.MAJOR, tags = setOf("smoke"), environments = setOf("Chrome")), f))
        assertFalse(matchesRunFilter(summary(result = RunResult.FAILED, priority = Priority.MAJOR, tags = setOf("smoke"), environments = setOf("Chrome")), f))
        f.clear()
        assertTrue(f.isEmpty())
        assertEquals(0, f.activeCount())
    }
}
