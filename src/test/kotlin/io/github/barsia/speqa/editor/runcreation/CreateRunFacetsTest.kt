package io.github.barsia.speqa.editor.runcreation

import io.github.barsia.speqa.model.Priority
import io.github.barsia.speqa.model.Status
import org.junit.Assert.assertEquals
import org.junit.Test

class CreateRunFacetsTest {
    private fun case(p: Priority, tags: Set<String> = emptySet(), envs: Set<String> = emptySet()) =
        CaseFacets(status = Status.READY, priority = p, tags = tags, environments = envs)

    @Test fun `priority facet shown only when multiple distinct priorities`() {
        assertEquals(false, CreateRunFacets.present(listOf(case(Priority.MAJOR), case(Priority.MAJOR))).priority)
        assertEquals(true, CreateRunFacets.present(listOf(case(Priority.MAJOR), case(Priority.LOW))).priority)
    }
    @Test fun `tags facet shown when any case has a tag`() {
        assertEquals(false, CreateRunFacets.present(listOf(case(Priority.LOW))).tags)
        assertEquals(true, CreateRunFacets.present(listOf(case(Priority.LOW, tags = setOf("smoke")))).tags)
    }
    @Test fun `environment facet shown when any case has an environment`() {
        assertEquals(false, CreateRunFacets.present(listOf(case(Priority.LOW))).environments)
        assertEquals(true, CreateRunFacets.present(listOf(case(Priority.LOW, envs = setOf("chrome")))).environments)
    }
    @Test fun `empty input shows no facets`() {
        val p = CreateRunFacets.present(emptyList())
        assertEquals(false, p.priority); assertEquals(false, p.tags); assertEquals(false, p.environments)
    }
}
