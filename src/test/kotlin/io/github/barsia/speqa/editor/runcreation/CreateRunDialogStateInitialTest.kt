package io.github.barsia.speqa.editor.runcreation

import io.github.barsia.speqa.model.Priority
import io.github.barsia.speqa.model.Status
import org.junit.Assert.assertEquals
import org.junit.Test

class CreateRunDialogStateInitialTest {

    private fun candidate(key: String): CandidateCase =
        CandidateCase(
            key = key,
            title = key,
            facets = CaseFacets(
                status = Status.entries.first(),
                priority = Priority.entries.first(),
                tags = emptySet(),
                environments = emptySet(),
            ),
        )

    @Test
    fun `initialCheckedKeys returns every candidate key`() {
        val candidates = listOf(candidate("a"), candidate("b"), candidate("c"))
        assertEquals(setOf("a", "b", "c"), CreateRunDialogState.initialCheckedKeys(candidates))
    }

    @Test
    fun `initialCheckedKeys is empty for no candidates`() {
        assertEquals(emptySet<String>(), CreateRunDialogState.initialCheckedKeys(emptyList()))
    }
}
