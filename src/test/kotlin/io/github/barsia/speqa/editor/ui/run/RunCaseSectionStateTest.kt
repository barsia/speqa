package io.github.barsia.speqa.editor.ui.run

import io.github.barsia.speqa.model.RunCase
import io.github.barsia.speqa.model.RunResult
import org.junit.Assert.assertEquals
import org.junit.Test

class RunCaseSectionStateTest {
    @Test fun `header label shows TC id and title`() {
        assertEquals("TC-5 Sign in", RunCaseSectionState.headerLabel(RunCase(caseId = 5, title = "Sign in")))
    }
    @Test fun `header label falls back to TC id when title blank`() {
        assertEquals("TC-8", RunCaseSectionState.headerLabel(RunCase(caseId = 8, title = "")))
    }
    @Test fun `result badge text uses the case result label`() {
        assertEquals(RunResult.BLOCKED.label, RunCaseSectionState.resultBadge(RunCase(caseId = 1, result = RunResult.BLOCKED)))
    }

    @Test
    fun `manual hint shown only when overridden`() {
        assertEquals(true, RunCaseSectionState.isManual(RunCase(caseId = 1, result = RunResult.BLOCKED, manualResult = true)))
        assertEquals(false, RunCaseSectionState.isManual(RunCase(caseId = 1, result = RunResult.PASSED)))
    }
}
