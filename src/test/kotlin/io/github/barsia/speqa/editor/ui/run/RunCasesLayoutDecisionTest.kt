package io.github.barsia.speqa.editor.ui.run

import io.github.barsia.speqa.model.RunCase
import io.github.barsia.speqa.model.TestRun
import org.junit.Assert.assertEquals
import org.junit.Test

class RunCasesLayoutDecisionTest {
    @Test fun `single case renders flat`() {
        assertEquals(RunLayout.FLAT, RunCasesContainer.layoutFor(TestRun(cases = listOf(RunCase(caseId = 1)))))
        assertEquals(RunLayout.FLAT, RunCasesContainer.layoutFor(TestRun(cases = emptyList())))
    }

    @Test fun `multiple cases render sectioned`() {
        assertEquals(
            RunLayout.SECTIONED,
            RunCasesContainer.layoutFor(TestRun(cases = listOf(RunCase(caseId = 1), RunCase(caseId = 2)))),
        )
    }
}
