package io.github.barsia.speqa.filetype

import io.github.barsia.speqa.model.RunResult
import org.junit.Assert.assertEquals
import org.junit.Test

class SpeqaIconProviderTest {
    @Test
    fun `parses underscored result labels`() {
        assertEquals(RunResult.NOT_STARTED, parse("result: not_started"))
        assertEquals(RunResult.IN_PROGRESS, parse("result: in_progress"))
    }

    @Test
    fun `parses single-word result labels`() {
        assertEquals(RunResult.PASSED, parse("result: passed"))
        assertEquals(RunResult.FAILED, parse("result: failed"))
        assertEquals(RunResult.BLOCKED, parse("result: blocked"))
    }

    @Test
    fun `reads the frontmatter result line within a full document`() {
        val text =
            """
            |---
            |id: 1
            |title: "Smoke run"
            |result: in_progress
            |runner: "qa"
            |---
            |
            |Test Case: TC-1 Login
            |Result: passed
            """.trimMargin()
        assertEquals(RunResult.IN_PROGRESS, parse(text))
    }

    @Test
    fun `defaults to not started when result is absent`() {
        assertEquals(RunResult.NOT_STARTED, parse("title: no result here"))
    }

    private fun parse(text: String): RunResult = SpeqaIconProvider.parseRunResult(text)
}
