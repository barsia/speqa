package io.github.barsia.speqa.toolwindow

import org.junit.Assert.assertEquals
import org.junit.Test

class FacetPickStateTest {
    @Test
    fun `no known values means the facet has nothing to offer`() {
        assertEquals(FacetPickState.NO_VALUES, facetPickState(known = emptySet(), selected = emptySet()))
    }

    @Test
    fun `some known and none selected is pickable`() {
        assertEquals(FacetPickState.PICKABLE, facetPickState(known = setOf("api", "ui"), selected = emptySet()))
    }

    @Test
    fun `every known value already selected leaves nothing to pick`() {
        assertEquals(
            FacetPickState.ALL_SELECTED,
            facetPickState(known = setOf("api", "ui"), selected = setOf("api", "ui")),
        )
    }

    @Test
    fun `a value still pickable when only some are selected`() {
        assertEquals(
            FacetPickState.PICKABLE,
            facetPickState(known = setOf("api", "ui", "auth"), selected = setOf("api")),
        )
    }

    @Test
    fun `selection is matched case-insensitively so a casing-only difference is still all-selected`() {
        // The picker filters out already-selected values case-insensitively; the gate must agree,
        // otherwise the facet would stay enabled and open an empty "Nothing to show" popup.
        assertEquals(
            FacetPickState.ALL_SELECTED,
            facetPickState(known = setOf("API", "Ui"), selected = setOf("api", "ui")),
        )
    }
}
