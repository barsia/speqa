package io.github.barsia.speqa.editor.runcreation

import io.github.barsia.speqa.model.Priority
import io.github.barsia.speqa.model.Status
import org.junit.Assert.assertEquals
import org.junit.Test

class CreateRunDialogStateTest {
    private fun row(key: String, p: Priority, tags: Set<String> = emptySet(), envs: Set<String> = emptySet()) =
        CandidateCase(key = key, title = key, facets = CaseFacets(status = Status.READY, priority = p, tags = tags, environments = envs))

    private val all = listOf(
        row("a", Priority.MAJOR, tags = setOf("smoke")),
        row("b", Priority.LOW, tags = setOf("regression")),
        row("c", Priority.MAJOR),
    )

    @Test fun `visible reflects the active filter`() {
        val visible = CreateRunDialogState.visible(all, CreateRunFilter(priority = Priority.MAJOR))
        assertEquals(listOf("a", "c"), visible.map { it.key })
    }

    @Test fun `selected count is visible and checked`() {
        // all checked by default; filter to MAJOR -> a,c visible -> 2 selected
        val checked = all.map { it.key }.toSet()
        assertEquals(2, CreateRunDialogState.selectedCount(all, CreateRunFilter(priority = Priority.MAJOR), checked))
    }

    @Test fun `unchecking a visible case lowers the count`() {
        val checked = setOf("a")   // only a checked
        assertEquals(1, CreateRunDialogState.selectedCount(all, CreateRunFilter(priority = Priority.MAJOR), checked))
    }

    @Test fun `selected files are visible and checked only`() {
        val checked = setOf("a", "b")
        assertEquals(
            listOf("a"),
            CreateRunDialogState.selectedKeys(all, CreateRunFilter(priority = Priority.MAJOR), checked),
        )
    }
}
