package io.github.barsia.speqa.editor.runcreation

import org.junit.Assert.assertEquals
import org.junit.Test

class CreateRunSummaryTest {
    @Test fun `selection count maps to section count`() {
        assertEquals(3 to 3, CreateRunSummary.selectionCount(3))
        assertEquals(0 to 0, CreateRunSummary.selectionCount(0))
    }
}
