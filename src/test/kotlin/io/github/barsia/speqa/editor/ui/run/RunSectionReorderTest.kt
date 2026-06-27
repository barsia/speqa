package io.github.barsia.speqa.editor.ui.run

import io.github.barsia.speqa.editor.ui.steps.SiblingBounds
import io.github.barsia.speqa.editor.ui.steps.calculateTargetIndex
import io.github.barsia.speqa.model.RunCase
import io.github.barsia.speqa.model.TestRun
import io.github.barsia.speqa.run.TestRunSupport
import org.junit.Assert.assertEquals
import org.junit.Test

class RunSectionReorderTest {
    @Test
    fun `moving a section to a new index reorders the run cases`() {
        val run = TestRun(cases = listOf(RunCase(caseId = 1), RunCase(caseId = 2), RunCase(caseId = 3)))
        assertEquals(listOf(2, 3, 1), TestRunSupport.moveCase(run, 0, 2).cases.map { it.caseId })
        assertEquals(listOf(3, 1, 2), TestRunSupport.moveCase(run, 2, 0).cases.map { it.caseId })
    }

    @Test
    fun `dragging a section past a lower neighbor lands after it`() {
        // Three equal-height sections stacked at y = 0, 100, 200 (height 100).
        // The dragged section (index 0) crosses 70% into the section below
        // (index 1, flip boundary at 100 + 70 = 170) so it lands at index 1.
        val siblings = listOf(
            SiblingBounds(originalIndex = 1, top = 100, height = 100),
            SiblingBounds(originalIndex = 2, top = 200, height = 100),
        )
        assertEquals(0, calculateTargetIndex(draggedCenterY = 169f, siblings = siblings, originalIndex = 0))
        assertEquals(1, calculateTargetIndex(draggedCenterY = 171f, siblings = siblings, originalIndex = 0))
    }
}
