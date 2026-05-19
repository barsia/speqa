package io.github.barsia.speqa.run

import io.github.barsia.speqa.model.RunResult
import io.github.barsia.speqa.model.StepResult
import io.github.barsia.speqa.model.StepVerdict
import io.github.barsia.speqa.model.TestRun
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class RunAutoTimestampsTest {

    private val fixedNow = LocalDateTime.of(2026, 5, 21, 10, 0, 0)
    private val now: () -> LocalDateTime = { fixedNow }

    private fun run(
        steps: List<StepVerdict> = emptyList(),
        startedAt: LocalDateTime? = null,
        finishedAt: LocalDateTime? = null,
        result: RunResult = RunResult.NOT_STARTED,
        manualResult: Boolean = false,
    ): TestRun = TestRun(
        stepResults = steps.map { StepResult(verdict = it) },
        startedAt = startedAt,
        finishedAt = finishedAt,
        result = result,
        manualResult = manualResult,
    )

    @Test
    fun `untouched run stays empty`() {
        val out = RunAutoTimestamps.apply(run(steps = listOf(StepVerdict.NONE, StepVerdict.NONE)), now = now)
        assertNull(out.startedAt)
        assertNull(out.finishedAt)
        assertFalse(out.manualResult)
    }

    @Test
    fun `first step verdict sets startedAt`() {
        val out = RunAutoTimestamps.apply(run(steps = listOf(StepVerdict.PASSED, StepVerdict.NONE)), now = now)
        assertEquals(fixedNow, out.startedAt)
        assertNull(out.finishedAt)
    }

    @Test
    fun `startedAt is sticky`() {
        val earlier = fixedNow.minusHours(2)
        val out = RunAutoTimestamps.apply(
            run(steps = listOf(StepVerdict.PASSED, StepVerdict.PASSED), startedAt = earlier),
            now = now,
        )
        assertEquals(earlier, out.startedAt)
    }

    @Test
    fun `last NONE step transition sets finishedAt`() {
        val out = RunAutoTimestamps.apply(
            run(steps = listOf(StepVerdict.PASSED, StepVerdict.FAILED), startedAt = fixedNow.minusHours(1)),
            now = now,
        )
        assertEquals(fixedNow, out.finishedAt)
    }

    @Test
    fun `finishedAt sticks while all evaluated`() {
        val earlier = fixedNow.minusMinutes(10)
        val out = RunAutoTimestamps.apply(
            run(
                steps = listOf(StepVerdict.PASSED, StepVerdict.PASSED),
                startedAt = earlier,
                finishedAt = earlier,
            ),
            now = now,
        )
        assertEquals(earlier, out.finishedAt)
    }

    @Test
    fun `rolling back a step to NONE clears finishedAt`() {
        val out = RunAutoTimestamps.apply(
            run(
                steps = listOf(StepVerdict.PASSED, StepVerdict.NONE),
                startedAt = fixedNow.minusHours(1),
                finishedAt = fixedNow.minusMinutes(5),
            ),
            now = now,
        )
        assertNull(out.finishedAt)
    }

    @Test
    fun `manual terminal result keeps finishedAt even with NONE steps`() {
        val out = RunAutoTimestamps.apply(
            run(
                steps = listOf(StepVerdict.NONE, StepVerdict.NONE),
                result = RunResult.PASSED,
                manualResult = true,
            ),
            now = now,
        )
        assertEquals(fixedNow, out.startedAt)
        assertEquals(fixedNow, out.finishedAt)
    }

    @Test
    fun `manualResultOverride true marks the run as manual`() {
        val out = RunAutoTimestamps.apply(
            run(result = RunResult.FAILED),
            manualResultOverride = true,
            now = now,
        )
        assertTrue(out.manualResult)
        assertEquals(fixedNow, out.finishedAt)
    }

    @Test
    fun `manualResultOverride false on NOT_STARTED clears manual flag`() {
        val out = RunAutoTimestamps.apply(
            run(
                result = RunResult.NOT_STARTED,
                manualResult = true,
                startedAt = fixedNow.minusHours(1),
                finishedAt = fixedNow.minusMinutes(5),
            ),
            manualResultOverride = false,
            now = now,
        )
        assertFalse(out.manualResult)
        // No evaluated steps, no manual terminal -> finishedAt resets.
        assertNull(out.finishedAt)
    }

    @Test
    fun `empty stepResults with no manual result keeps everything null`() {
        val out = RunAutoTimestamps.apply(run(steps = emptyList()), now = now)
        assertNull(out.startedAt)
        assertNull(out.finishedAt)
        assertEquals(RunResult.NOT_STARTED, out.result)
    }

    @Test
    fun `partial evaluation derives IN_PROGRESS`() {
        val out = RunAutoTimestamps.apply(run(steps = listOf(StepVerdict.PASSED, StepVerdict.NONE)), now = now)
        assertEquals(RunResult.IN_PROGRESS, out.result)
    }

    @Test
    fun `all passed derives PASSED`() {
        val out = RunAutoTimestamps.apply(run(steps = listOf(StepVerdict.PASSED, StepVerdict.PASSED)), now = now)
        assertEquals(RunResult.PASSED, out.result)
    }

    @Test
    fun `any failed derives FAILED`() {
        val out = RunAutoTimestamps.apply(
            run(steps = listOf(StepVerdict.PASSED, StepVerdict.FAILED, StepVerdict.BLOCKED)),
            now = now,
        )
        assertEquals(RunResult.FAILED, out.result)
    }

    @Test
    fun `blocked without failed derives BLOCKED`() {
        val out = RunAutoTimestamps.apply(
            run(steps = listOf(StepVerdict.PASSED, StepVerdict.BLOCKED)),
            now = now,
        )
        assertEquals(RunResult.BLOCKED, out.result)
    }

    @Test
    fun `skipped is neutral when others passed`() {
        val out = RunAutoTimestamps.apply(
            run(steps = listOf(StepVerdict.PASSED, StepVerdict.SKIPPED)),
            now = now,
        )
        assertEquals(RunResult.PASSED, out.result)
    }

    @Test
    fun `manual result preserves user choice and ignores step derivation`() {
        val out = RunAutoTimestamps.apply(
            run(
                steps = listOf(StepVerdict.FAILED, StepVerdict.FAILED),
                result = RunResult.PASSED,
                manualResult = true,
            ),
            now = now,
        )
        assertEquals(RunResult.PASSED, out.result)
    }
}
