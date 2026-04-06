package io.github.barsia.speqa.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TestRunTest {
    @Test
    fun `default test run has expected defaults`() {
        val testRun = TestRun()

        assertEquals("", testRun.title)
        assertEquals(RunResult.NOT_STARTED, testRun.result)
        assertEquals("", testRun.environment)
        assertEquals("", testRun.runner)
        assertTrue(testRun.stepResults.isEmpty())
    }

    @Test
    fun `step result defaults are stable`() {
        val stepResult = StepResult()

        assertEquals("", stepResult.action)
        assertEquals("", stepResult.expected)
        assertEquals(StepVerdict.NONE, stepResult.verdict)
        assertEquals("", stepResult.comment)
    }

    @Test
    fun `result and verdict fromString are case insensitive`() {
        assertEquals(RunResult.PASSED, RunResult.fromString("passed"))
        assertEquals(RunResult.FAILED, RunResult.fromString("FAILED"))
        assertEquals(RunResult.BLOCKED, RunResult.fromString("Blocked"))
        assertEquals(RunResult.NOT_STARTED, RunResult.fromString("unknown"))

        assertEquals(StepVerdict.PASSED, StepVerdict.fromString("passed"))
        assertEquals(StepVerdict.FAILED, StepVerdict.fromString("FAILED"))
        assertEquals(StepVerdict.SKIPPED, StepVerdict.fromString("Skipped"))
        assertEquals(StepVerdict.NONE, StepVerdict.fromString("unknown"))
    }
}
