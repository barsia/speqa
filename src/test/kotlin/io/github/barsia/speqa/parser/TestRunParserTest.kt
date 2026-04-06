package io.github.barsia.speqa.parser

import io.github.barsia.speqa.model.Priority
import io.github.barsia.speqa.model.RunResult
import io.github.barsia.speqa.model.StepResult
import io.github.barsia.speqa.model.StepVerdict
import io.github.barsia.speqa.model.TestRun
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TestRunParserTest {

    @Test
    fun `parses title from frontmatter`() {
        val content = "---\ntitle: \"Login test\"\nstarted_at: 2026-04-11T10:00:00\nresult: passed\n---"
        val run = TestRunParser.parse(content)
        assertEquals("Login test", run.title)
    }

    @Test
    fun `parses manual_result flag`() {
        val content = "---\ntitle: \"Test\"\nstarted_at: 2026-04-11T10:00:00\nresult: passed\nmanual_result: true\n---"
        val run = TestRunParser.parse(content)
        assertTrue(run.manualResult)
    }

    @Test
    fun `manual_result defaults to false`() {
        val content = "---\ntitle: \"Test\"\nstarted_at: 2026-04-11T10:00:00\nresult: passed\n---"
        val run = TestRunParser.parse(content)
        assertFalse(run.manualResult)
    }

    @Test
    fun `parses step with expected and verdict`() {
        val content = "---\ntitle: \"Test\"\nstarted_at: 2026-04-11T10:00:00\nresult: passed\n---\n\nScenario:\n\n1. Click button\n   > Page loads\n   - passed"
        val run = TestRunParser.parse(content)
        assertEquals(1, run.stepResults.size)
        assertEquals("Click button", run.stepResults[0].action)
        assertEquals("Page loads", run.stepResults[0].expected)
        assertEquals(StepVerdict.PASSED, run.stepResults[0].verdict)
    }

    @Test
    fun `step without verdict line gets NONE`() {
        val content = "---\ntitle: \"Test\"\nstarted_at: 2026-04-11T10:00:00\nresult: failed\n---\n\nScenario:\n\n1. Click button\n   > Page loads"
        val run = TestRunParser.parse(content)
        assertEquals(StepVerdict.NONE, run.stepResults[0].verdict)
        assertEquals("Page loads", run.stepResults[0].expected)
    }

    @Test
    fun `parses comment lines after verdict`() {
        val content = "---\ntitle: \"Test\"\nstarted_at: 2026-04-11T10:00:00\nresult: failed\n---\n\nScenario:\n\n1. Click button\n   > Page loads\n   - failed\n   Comment:\n   Got 500 error  \n   Server timeout"
        val run = TestRunParser.parse(content)
        assertEquals("Got 500 error\nServer timeout", run.stepResults[0].comment)
    }

    @Test
    fun `parses blocked verdict`() {
        val content = "---\ntitle: \"Test\"\nstarted_at: 2026-04-11T10:00:00\nresult: blocked\n---\n\nScenario:\n\n1. Click button\n   - blocked"
        val run = TestRunParser.parse(content)
        assertEquals(StepVerdict.BLOCKED, run.stepResults[0].verdict)
    }

    @Test
    fun `parses multiple steps`() {
        val content = "---\ntitle: \"Test\"\nstarted_at: 2026-04-11T10:00:00\nresult: passed\n---\n\nScenario:\n\n1. Step one\n   > Result one\n   - passed\n\n2. Step two\n   > Result two\n   - failed\n   Comment:\n   Error occurred"
        val run = TestRunParser.parse(content)
        assertEquals(2, run.stepResults.size)
        assertEquals("Step one", run.stepResults[0].action)
        assertEquals("Result one", run.stepResults[0].expected)
        assertEquals(StepVerdict.PASSED, run.stepResults[0].verdict)
        assertEquals("Step two", run.stepResults[1].action)
        assertEquals("Result two", run.stepResults[1].expected)
        assertEquals(StepVerdict.FAILED, run.stepResults[1].verdict)
        assertEquals("Error occurred", run.stepResults[1].comment)
    }

    @Test
    fun `parse empty run returns defaults`() {
        val run = TestRunParser.parse("")
        assertFalse(run.manualResult)
        assertTrue(run.stepResults.isEmpty())
        assertEquals("", run.environment)
        assertEquals("", run.runner)
        assertNull(run.startedAt)
        assertEquals(RunResult.NOT_STARTED, run.result)
    }

    @Test
    fun `parses tags from frontmatter`() {
        val content = "---\ntitle: \"Test\"\nstarted_at: 2026-04-11T10:00:00\nresult: passed\ntags:\n  - auth\n  - smoke\n---"
        val run = TestRunParser.parse(content)
        assertEquals(listOf("auth", "smoke"), run.tags)
    }

    @Test
    fun `tags default to empty when absent`() {
        val content = "---\ntitle: \"Test\"\nstarted_at: 2026-04-11T10:00:00\nresult: passed\n---"
        val run = TestRunParser.parse(content)
        assertTrue(run.tags.isEmpty())
    }

    @Test
    fun `parses current bullet verdict format`() {
        val content = "---\ntitle: \"Test\"\nstarted_at: 2026-04-11T10:00:00\nresult: passed\n---\n\nScenario:\n\n1. Click button\n   > Page loads\n   - passed"
        val run = TestRunParser.parse(content)
        assertEquals(StepVerdict.PASSED, run.stepResults[0].verdict)
    }

    @Test
    fun `startedAt defaults to null when absent`() {
        val content = "---\ntitle: \"Test\"\nresult: passed\n---"
        val run = TestRunParser.parse(content)
        assertNull(run.startedAt)
    }

    @Test
    fun `result defaults to NOT_STARTED when absent`() {
        val content = "---\ntitle: \"Test\"\n---"
        val run = TestRunParser.parse(content)
        assertEquals(RunResult.NOT_STARTED, run.result)
    }

    @Test
    fun `distinguishes expected from step comment`() {
        val content = "---\ntitle: \"Test\"\nresult: passed\n---\n\nScenario:\n\n1. Click button\n   > Page loads\n   - passed\n   Comment:\n   Looks good"
        val run = TestRunParser.parse(content)
        assertEquals("Page loads", run.stepResults[0].expected)
        assertEquals(StepVerdict.PASSED, run.stepResults[0].verdict)
        assertEquals("Looks good", run.stepResults[0].comment)
    }

    @Test
    fun `plain indented line without marker remains action continuation`() {
        val content = "---\ntitle: \"Test\"\nresult: not_started\n---\n\nScenario:\n\n1. Click button\n   My note"
        val run = TestRunParser.parse(content)
        assertEquals("Click button\nMy note", run.stepResults[0].action)
        assertEquals(StepVerdict.NONE, run.stepResults[0].verdict)
        assertEquals("", run.stepResults[0].comment)
    }

    @Test
    fun `parses explicit comment block after verdict`() {
        val content = "---\ntitle: \"Test\"\nresult: passed\n---\n\nScenario:\n\n1. Click button\n   > Page loads\n   - passed\n   Comment:\n   Looks good"
        val run = TestRunParser.parse(content)
        assertEquals("Looks good", run.stepResults[0].comment)
    }

    @Test
    fun `parses multiline comment`() {
        val content = "---\ntitle: \"Test\"\nresult: passed\n---\n\nScenario:\n\n1. Click button\n   - passed\n   Comment:\n   Line one  \n   Line two"
        val run = TestRunParser.parse(content)
        assertEquals("Line one\nLine two", run.stepResults[0].comment)
    }


    @Test
    fun `parses priority`() {
        val content = "---\ntitle: \"Test\"\npriority: major\nresult: passed\n---"
        val run = TestRunParser.parse(content)
        assertEquals(Priority.MAJOR, run.priority)
    }

    @Test
    fun `parses overall comment after steps`() {
        val content = "---\ntitle: \"Test\"\nresult: passed\n---\n\nScenario:\n\n1. Click\n   - passed\n\nOverall comment here."
        val run = TestRunParser.parse(content)
        assertEquals("Overall comment here.", run.comment)
    }

    @Test
    fun `overall comment after step comment does not leak into last step`() {
        val content = "---\ntitle: \"Test\"\nresult: failed\n---\n\nScenario:\n\n1. Click cancel\n   - failed\n\n   Comment:\n   Step-specific note\n\nOverall run note"
        val run = TestRunParser.parse(content)
        assertEquals("Step-specific note", run.stepResults[0].comment)
        assertEquals("Overall run note", run.comment)
    }

    @Test
    fun `parses links section`() {
        val content = "---\ntitle: \"Test\"\nresult: passed\n---\n\nLinks:\n\n[Jira](https://jira.example.com/123)\n\nScenario:\n\n1. Click\n   - passed"
        val run = TestRunParser.parse(content)
        assertEquals(1, run.links.size)
        assertEquals("Jira", run.links[0].title)
        assertEquals("https://jira.example.com/123", run.links[0].url)
    }

    @Test
    fun `parses multiline action`() {
        val content = "---\ntitle: \"Test\"\nresult: passed\n---\n\nScenario:\n\n1. Line one  \n   Line two  \n   Line three\n   > Expected result\n   - passed"
        val run = TestRunParser.parse(content)
        assertEquals(1, run.stepResults.size)
        assertEquals("Line one\nLine two\nLine three", run.stepResults[0].action)
        assertEquals("Expected result", run.stepResults[0].expected)
        assertEquals(StepVerdict.PASSED, run.stepResults[0].verdict)
    }

    @Test
    fun `multiline action roundtrip preserves data`() {
        val original = TestRun(
            title = "Roundtrip test",
            result = RunResult.PASSED,
            stepResults = listOf(
                StepResult(
                    action = "First line\nSecond line\nThird line",
                    expected = "Something happens",
                    verdict = StepVerdict.PASSED,
                    comment = "All good",
                ),
            ),
        )
        val serialized = TestRunSerializer.serialize(original)
        val parsed = TestRunParser.parse(serialized)
        assertEquals(1, parsed.stepResults.size)
        assertEquals("First line\nSecond line\nThird line", parsed.stepResults[0].action)
        assertEquals("Something happens", parsed.stepResults[0].expected)
        assertEquals(StepVerdict.PASSED, parsed.stepResults[0].verdict)
        assertEquals("All good", parsed.stepResults[0].comment)
    }

    @Test
    fun `step attachments are parsed without leaking into text fields`() {
        val content = "---\ntitle: \"Test\"\nresult: passed\n---\n\nScenario:\n\n1. Click button\n   > Page loads\n   ![screenshot.png](attachments/screenshot.png)\n   [report.pdf]\n   - passed"
        val run = TestRunParser.parse(content)
        assertEquals(1, run.stepResults.size)
        assertEquals("Click button", run.stepResults[0].action)
        assertEquals("Page loads", run.stepResults[0].expected)
        assertEquals(StepVerdict.PASSED, run.stepResults[0].verdict)
        assertEquals("", run.stepResults[0].comment)
        assertTrue(run.stepResults[0].actionAttachments.isEmpty())
        assertEquals(listOf("attachments/screenshot.png", "report.pdf"), run.stepResults[0].expectedAttachments.map { it.path })
    }

    @Test
    fun `comment without marker stays in action even after formatted text`() {
        val content = "---\ntitle: \"Test\"\nresult: passed\n---\n\nScenario:\n\n1. Type \"testuser@example.com\" ~~into~~ _**~~the~~**_ **_email_** field\n   **wwwwwwww**\n   - passed"
        val run = TestRunParser.parse(content)
        assertEquals(
            "Type \"testuser@example.com\" ~~into~~ _**~~the~~**_ **_email_** field\n**wwwwwwww**",
            run.stepResults[0].action,
        )
        assertEquals("", run.stepResults[0].comment)
    }

    @Test
    fun `does not parse steps without Scenario marker`() {
        val content = "---\ntitle: \"Test\"\nresult: passed\n---\n\n1. Click button\n   > Page loads\n   - passed"
        val run = TestRunParser.parse(content)
        assertTrue(run.stepResults.isEmpty())
    }
}
