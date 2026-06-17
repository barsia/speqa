package io.github.barsia.speqa.registry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DuplicateIdResolverTest {

    @Test
    fun no_duplicates_yields_empty_plan() {
        val entries = listOf(
            TestCaseIdEntry("a.tc.md", 1, 10L),
            TestCaseIdEntry("b.tc.md", 2, 20L),
        )
        assertEquals(emptyList<IdRenumber>(), computeDuplicateIdRenumberPlan(entries))
    }

    @Test
    fun earliest_created_keeps_the_number_loser_takes_first_free() {
        val entries = listOf(
            TestCaseIdEntry("a.tc.md", 1, 5L),
            TestCaseIdEntry("b.tc.md", 2, 10L),
            TestCaseIdEntry("c.tc.md", 2, 20L),
            TestCaseIdEntry("d.tc.md", 3, 5L),
        )
        val plan = computeDuplicateIdRenumberPlan(entries)
        // b (older) keeps 2; c (newer) moves to the first free id, which is 4.
        assertEquals(listOf(IdRenumber("c.tc.md", 2, 4)), plan)
    }

    @Test
    fun multiple_groups_each_resolved_losers_get_distinct_free_ids() {
        val entries = listOf(
            TestCaseIdEntry("a.tc.md", 1, 5L),
            TestCaseIdEntry("b.tc.md", 1, 9L),
            TestCaseIdEntry("x.tc.md", 2, 5L),
            TestCaseIdEntry("y.tc.md", 2, 9L),
        )
        val plan = computeDuplicateIdRenumberPlan(entries)
        // Keepers: a (id 1), x (id 2). Losers b and y, ordered by (created, path): b then y.
        // Occupied {1,2}; first free 3 -> b, next free 4 -> y.
        assertEquals(
            listOf(
                IdRenumber("b.tc.md", 1, 3),
                IdRenumber("y.tc.md", 2, 4),
            ),
            plan,
        )
    }

    @Test
    fun missing_created_time_falls_back_to_path_order() {
        val entries = listOf(
            TestCaseIdEntry("z.tc.md", 5, null),
            TestCaseIdEntry("a.tc.md", 5, null),
        )
        val plan = computeDuplicateIdRenumberPlan(entries)
        // Both created-times null -> tie broken by path: "a.tc.md" keeps 5, "z.tc.md" moves.
        assertEquals(listOf(IdRenumber("z.tc.md", 5, 1)), plan)
    }

    @Test
    fun resolution_never_produces_a_new_duplicate() {
        val entries = listOf(
            TestCaseIdEntry("a.tc.md", 7, 1L),
            TestCaseIdEntry("b.tc.md", 7, 2L),
            TestCaseIdEntry("c.tc.md", 7, 3L),
            TestCaseIdEntry("d.tc.md", 8, 1L),
        )
        val plan = computeDuplicateIdRenumberPlan(entries)
        val finalIds = entries.associate { it.path to it.id }.toMutableMap()
        plan.forEach { finalIds[it.path] = it.newId }
        assertTrue(plan.isNotEmpty())
        assertEquals(finalIds.values.toSet().size, finalIds.values.size)
    }
}
