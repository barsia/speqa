package io.github.barsia.speqa.editor.runcreation

import io.github.barsia.speqa.model.Priority
import io.github.barsia.speqa.model.Status
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CreateRunSelectionTest {
    private fun c(p: Priority, tags: Set<String> = emptySet(), envs: Set<String> = emptySet()) = CaseFacets(status = Status.READY, priority = p, tags = tags, environments = envs)
    @Test fun `no active filter matches all`() { assertTrue(CreateRunFilter().matches(c(Priority.LOW))) }
    @Test fun `priority filter constrains`() {
        val f = CreateRunFilter(priority = Priority.MAJOR)
        assertEquals(true, f.matches(c(Priority.MAJOR))); assertEquals(false, f.matches(c(Priority.LOW)))
    }
    @Test fun `tags match any selected`() {
        val f = CreateRunFilter(tags = setOf("smoke", "auth"))
        assertEquals(true, f.matches(c(Priority.LOW, tags = setOf("auth"))))
        assertEquals(false, f.matches(c(Priority.LOW, tags = setOf("regression"))))
    }
    @Test fun `facets combine with AND`() {
        val f = CreateRunFilter(priority = Priority.MAJOR, environments = setOf("chrome"))
        assertEquals(true, f.matches(c(Priority.MAJOR, envs = setOf("chrome"))))
        assertEquals(false, f.matches(c(Priority.MAJOR, envs = setOf("firefox"))))
    }
}
