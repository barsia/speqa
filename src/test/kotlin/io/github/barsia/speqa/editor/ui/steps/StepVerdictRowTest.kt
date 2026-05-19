package io.github.barsia.speqa.editor.ui.steps

import io.github.barsia.speqa.model.StepVerdict
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Plain JUnit 4 test for [verdictAfterToggle]. No Swing or IntelliJ platform
 * bootstrap — the function under test has zero UI dependencies.
 */
class StepVerdictRowTest {

    @Test
    fun `NONE + PASSED returns PASSED`() {
        assertEquals(StepVerdict.PASSED, verdictAfterToggle(StepVerdict.NONE, StepVerdict.PASSED))
    }

    @Test
    fun `PASSED + PASSED returns NONE (toggle off)`() {
        assertEquals(StepVerdict.NONE, verdictAfterToggle(StepVerdict.PASSED, StepVerdict.PASSED))
    }

    @Test
    fun `PASSED + FAILED returns FAILED (switch)`() {
        assertEquals(StepVerdict.FAILED, verdictAfterToggle(StepVerdict.PASSED, StepVerdict.FAILED))
    }

    @Test
    fun `round-trip through all four verdicts`() {
        // NONE -> PASSED -> NONE -> FAILED -> NONE -> SKIPPED -> NONE -> BLOCKED -> NONE
        var state = StepVerdict.NONE

        state = verdictAfterToggle(state, StepVerdict.PASSED)
        assertEquals(StepVerdict.PASSED, state)

        state = verdictAfterToggle(state, StepVerdict.PASSED)
        assertEquals(StepVerdict.NONE, state)

        state = verdictAfterToggle(state, StepVerdict.FAILED)
        assertEquals(StepVerdict.FAILED, state)

        state = verdictAfterToggle(state, StepVerdict.FAILED)
        assertEquals(StepVerdict.NONE, state)

        state = verdictAfterToggle(state, StepVerdict.SKIPPED)
        assertEquals(StepVerdict.SKIPPED, state)

        state = verdictAfterToggle(state, StepVerdict.SKIPPED)
        assertEquals(StepVerdict.NONE, state)

        state = verdictAfterToggle(state, StepVerdict.BLOCKED)
        assertEquals(StepVerdict.BLOCKED, state)

        state = verdictAfterToggle(state, StepVerdict.BLOCKED)
        assertEquals(StepVerdict.NONE, state)
    }
}
