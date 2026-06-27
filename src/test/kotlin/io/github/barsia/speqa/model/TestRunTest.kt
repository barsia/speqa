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
        assertTrue(testRun.environment.isEmpty())
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
        assertTrue(stepResult.links.isEmpty())
    }

    @Test
    fun `single-case run exposes its one case via cases`() {
        val step = StepResult(action = "do", verdict = StepVerdict.PASSED)
        val case = RunCase(
            caseId = 5,
            title = "Login",
            priority = Priority.MAJOR,
            tags = listOf("smoke"),
            environment = listOf("chrome"),
            stepResults = listOf(step),
            result = RunResult.PASSED,
        )
        val run = TestRun(id = 12, title = "High", cases = listOf(case))

        assertEquals(1, run.cases.size)
        assertEquals(5, run.cases.first().caseId)
        assertEquals(listOf(step), run.cases.first().stepResults)
        // Compat accessor still flattens to all steps:
        assertEquals(listOf(step), run.stepResults)
    }

    @Test
    fun `RunCase carries a manualResult flag defaulting to false`() {
        val auto = RunCase(caseId = 1)
        val manual = RunCase(caseId = 2, result = RunResult.BLOCKED, manualResult = true)
        assertEquals(false, auto.manualResult)
        assertEquals(true, manual.manualResult)
        assertEquals(RunResult.BLOCKED, manual.result)
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
