package io.github.barsia.speqa.run

import io.github.barsia.speqa.model.RunResult
import io.github.barsia.speqa.model.StepResult
import io.github.barsia.speqa.model.StepVerdict
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class TestRunSupportTest {

    @Test
    fun `nextRunFileName includes test case stem`() {
        val name = TestRunSupport.nextRunFileName(
            testCaseFileName = "sample-login.tc.md",
            now = LocalDateTime.of(2026, 4, 11, 17, 7, 8),
            existingNames = emptySet(),
        )
        assertEquals("sample-login_2026-04-11_17-07-08.tr.md", name)
    }

    @Test
    fun `nextRunFileName avoids duplicate`() {
        val name = TestRunSupport.nextRunFileName(
            testCaseFileName = "sample-login.tc.md",
            now = LocalDateTime.of(2026, 4, 11, 17, 7, 8),
            existingNames = setOf("sample-login_2026-04-11_17-07-08.tr.md"),
        )
        assertEquals("sample-login_2026-04-11_17-07-08-2.tr.md", name)
    }

    @Test
    fun `normalizeRunFileName appends speqa extension when missing`() {
        val name = TestRunSupport.normalizeRunFileName(
            requestedFileName = "custom-run",
            existingNames = emptySet(),
        )
        assertEquals("custom-run.tr.md", name)
    }

    @Test
    fun `normalizeRunFileName preserves existing speqa extension`() {
        val name = TestRunSupport.normalizeRunFileName(
            requestedFileName = "custom-run.tr.md",
            existingNames = emptySet(),
        )
        assertEquals("custom-run.tr.md", name)
    }

    @Test
    fun `normalizeRunFileName avoids duplicate after normalization`() {
        val name = TestRunSupport.normalizeRunFileName(
            requestedFileName = "custom-run",
            existingNames = setOf("custom-run.tr.md"),
        )
        assertEquals("custom-run-2.tr.md", name)
    }

    @Test
    fun `all passed steps produce passed result`() {
        val steps = listOf(StepResult(verdict = StepVerdict.PASSED), StepResult(verdict = StepVerdict.PASSED))
        assertEquals(RunResult.PASSED, TestRunSupport.deriveRunResult(steps))
    }

    @Test
    fun `one failed step produces failed result`() {
        val steps = listOf(StepResult(verdict = StepVerdict.PASSED), StepResult(verdict = StepVerdict.FAILED))
        assertEquals(RunResult.FAILED, TestRunSupport.deriveRunResult(steps))
    }

    @Test
    fun `one blocked step without failed produces blocked result`() {
        val steps = listOf(StepResult(verdict = StepVerdict.PASSED), StepResult(verdict = StepVerdict.BLOCKED))
        assertEquals(RunResult.BLOCKED, TestRunSupport.deriveRunResult(steps))
    }

    @Test
    fun `failed takes precedence over blocked`() {
        val steps = listOf(StepResult(verdict = StepVerdict.FAILED), StepResult(verdict = StepVerdict.BLOCKED))
        assertEquals(RunResult.FAILED, TestRunSupport.deriveRunResult(steps))
    }

    @Test
    fun `skipped steps are ignored`() {
        val steps = listOf(StepResult(verdict = StepVerdict.PASSED), StepResult(verdict = StepVerdict.SKIPPED))
        assertEquals(RunResult.PASSED, TestRunSupport.deriveRunResult(steps))
    }

    @Test
    fun `NONE with passed produces IN_PROGRESS`() {
        val steps = listOf(StepResult(verdict = StepVerdict.PASSED), StepResult(verdict = StepVerdict.NONE))
        assertEquals(RunResult.IN_PROGRESS, TestRunSupport.deriveRunResult(steps))
    }

    @Test
    fun `all NONE steps produce NOT_STARTED result`() {
        val steps = listOf(StepResult(verdict = StepVerdict.NONE), StepResult(verdict = StepVerdict.NONE))
        assertEquals(RunResult.NOT_STARTED, TestRunSupport.deriveRunResult(steps))
    }

    @Test
    fun `all skipped steps produce PASSED result`() {
        val steps = listOf(StepResult(verdict = StepVerdict.SKIPPED), StepResult(verdict = StepVerdict.SKIPPED))
        assertEquals(RunResult.PASSED, TestRunSupport.deriveRunResult(steps))
    }

    @Test
    fun `empty steps produce NOT_STARTED result`() {
        assertEquals(RunResult.NOT_STARTED, TestRunSupport.deriveRunResult(emptyList()))
    }

    @Test
    fun `mixed NONE skipped and passed produces IN_PROGRESS`() {
        val steps = listOf(
            StepResult(verdict = StepVerdict.NONE),
            StepResult(verdict = StepVerdict.SKIPPED),
            StepResult(verdict = StepVerdict.PASSED),
        )
        assertEquals(RunResult.IN_PROGRESS, TestRunSupport.deriveRunResult(steps))
    }
}
