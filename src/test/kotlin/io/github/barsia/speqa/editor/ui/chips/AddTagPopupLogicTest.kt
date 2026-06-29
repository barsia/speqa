package io.github.barsia.speqa.editor.ui.chips

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AddTagPopupLogicTest {
    @Test
    fun `filter picker never offers a create row`() {
        // allowCreate=false is the whole point of the filter use: a value that does not exist in
        // the project can only match nothing, so creating one from the filter is meaningless.
        assertFalse(
            shouldShowCreateRow(allowCreate = false, query = "brand-new", pickable = emptyList(), selected = emptySet()),
        )
    }

    @Test
    fun `authoring offers a create row for a genuinely new value`() {
        assertTrue(
            shouldShowCreateRow(allowCreate = true, query = "brand-new", pickable = listOf("api"), selected = emptySet()),
        )
    }

    @Test
    fun `no create row for a blank query`() {
        assertFalse(
            shouldShowCreateRow(allowCreate = true, query = "   ", pickable = listOf("api"), selected = emptySet()),
        )
    }

    @Test
    fun `no create row when the query already exists as a pickable or selected value`() {
        // Case-insensitive: typing an existing tag must pick it, not duplicate it via Create.
        assertFalse(
            shouldShowCreateRow(allowCreate = true, query = "API", pickable = listOf("api"), selected = emptySet()),
        )
        assertFalse(
            shouldShowCreateRow(allowCreate = true, query = "Ui", pickable = emptyList(), selected = setOf("ui")),
        )
    }

    @Test
    fun `visible row count fits the items but never below one or above the cap`() {
        assertEquals(1, visibleRowCount(itemCount = 0, max = 8))
        assertEquals(3, visibleRowCount(itemCount = 3, max = 8))
        assertEquals(8, visibleRowCount(itemCount = 20, max = 8))
    }
}
