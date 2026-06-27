package io.github.barsia.speqa.parser

import io.github.barsia.speqa.model.Attachment
import io.github.barsia.speqa.model.Priority
import io.github.barsia.speqa.model.RunCase
import io.github.barsia.speqa.model.RunResult
import io.github.barsia.speqa.model.Link
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
        assertTrue(run.environment.isEmpty())
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
    fun `parses scalar environment as one entry even with commas`() {
        val content = "---\ntitle: \"Test\"\nenvironment: \"Chrome 122, macOS 14\"\nresult: passed\n---"
        val run = TestRunParser.parse(content)
        assertEquals(listOf("Chrome 122, macOS 14"), run.environment)
    }

    @Test
    fun `parses unquoted scalar environment with commas as one entry`() {
        val content = "---\ntitle: \"Test\"\nenvironment: test1, env20\nresult: passed\n---"
        val run = TestRunParser.parse(content)
        assertEquals(listOf("test1, env20"), run.environment)
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
    fun `parses step links and step attachments separately`() {
        val content = """
            ---
            title: "Test"
            result: passed
            ---

            Scenario:

            1. Open login page
               ![action.png](attachments/action.png)
               > Login page is visible
               Links: [Spec](https://example.com/spec)
               [report.pdf](attachments/report.pdf)
               - passed
        """.trimIndent()

        val run = TestRunParser.parse(content)
        assertEquals(1, run.stepResults.size)
        assertEquals("Open login page", run.stepResults[0].action)
        assertEquals("Login page is visible", run.stepResults[0].expected)
        assertEquals(
            listOf(
                Attachment("attachments/action.png"),
                Attachment("attachments/report.pdf"),
            ),
            run.stepResults[0].attachments,
        )
        assertEquals(
            listOf(Link("Spec", "https://example.com/spec")),
            run.stepResults[0].links,
        )
    }

    @Test
    fun `parses step links with commas inside url`() {
        val content = """
            ---
            title: "Test"
            result: passed
            ---

            Scenario:

            1. Open login page
               > Login page is visible
               Links: [Spec](https://example.com/spec?labels=a,b), [](https://example.com/raw?a=1,b=2)
               - passed
        """.trimIndent()

        val run = TestRunParser.parse(content)
        assertEquals(
            listOf(
                Link("Spec", "https://example.com/spec?labels=a,b"),
                Link("", "https://example.com/raw?a=1,b=2"),
            ),
            run.stepResults.single().links,
        )
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
        val original = flatRun(
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
    fun `run round trip preserves editable metadata and readonly scenario`() {
        val original = flatRun(
            title = "Login",
            tags = listOf("smoke"),
            environment = listOf("test1", "env20"),
            links = listOf(Link("Spec", "https://example.com/spec")),
            attachments = listOf(Attachment("top.png")),
            stepResults = listOf(
                StepResult(
                    action = "Open",
                    expected = "Opened",
                    tickets = listOf("QA-1"),
                    links = listOf(Link("Step", "https://example.com/step")),
                    attachments = listOf(
                        Attachment("action.png"),
                        Attachment("expected.png"),
                    ),
                ),
            ),
        )

        val parsed = TestRunParser.parse(TestRunSerializer.serialize(original))
        assertEquals(original, parsed)
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
        assertEquals(listOf("attachments/screenshot.png", "report.pdf"), run.stepResults[0].attachments.map { it.path })
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
    fun `parses two case sections with metadata and per-case result`() {
        val text = """
            ---
            id: 7
            title: "Run"
            result: failed
            ---

            Test Case: TC-5 Sign in
            priority: major
            tags: smoke, auth

            Scenario:

            1. Open page
               > Visible
               - passed

            Result: passed

            Test Case: TC-8 Wrong password

            Scenario:

            1. Enter wrong password
               - failed

            Result: failed
        """.trimIndent()

        val run = TestRunParser.parse(text)

        assertEquals(2, run.cases.size)
        assertEquals(5, run.cases[0].caseId)
        assertEquals("Sign in", run.cases[0].title)
        assertEquals(Priority.MAJOR, run.cases[0].priority)
        assertEquals(listOf("smoke", "auth"), run.cases[0].tags)
        assertEquals(RunResult.PASSED, run.cases[0].result)
        assertEquals(1, run.cases[0].stepResults.size)
        assertEquals(StepVerdict.PASSED, run.cases[0].stepResults[0].verdict)
        assertEquals(8, run.cases[1].caseId)
        assertEquals(RunResult.FAILED, run.cases[1].result)
    }

    @Test
    fun `legacy marker-less file parses as one implicit case`() {
        val text = """
            ---
            id: 3
            title: "Legacy"
            priority: normal
            tags: smoke
            result: passed
            ---

            Scenario:

            1. Do thing
               - passed
        """.trimIndent()

        val run = TestRunParser.parse(text)

        assertEquals(1, run.cases.size)
        assertEquals(3, run.cases[0].caseId)            // falls back to run id
        assertEquals(Priority.NORMAL, run.cases[0].priority)
        assertEquals(listOf("smoke"), run.cases[0].tags)
        assertEquals(1, run.cases[0].stepResults.size)
    }

    @Test
    fun `does not parse steps without Scenario marker`() {
        val content = "---\ntitle: \"Test\"\nresult: passed\n---\n\n1. Click button\n   > Page loads\n   - passed"
        val run = TestRunParser.parse(content)
        assertTrue(run.stepResults.isEmpty())
    }

    @Test
    fun `parses manual_result and round-trips it`() {
        val text = """
            ---
            id: 1
            ---

            Test Case: TC-5 A
            manual_result: true

            Scenario:

            1. Do
               - passed

            Result: blocked
        """.trimIndent()
        val run = TestRunParser.parse(text)
        assertEquals(true, run.cases.single().manualResult)
        assertEquals(RunResult.BLOCKED, run.cases.single().result)

        val original = TestRun(id = 1, cases = listOf(RunCase(caseId = 5, title = "A",
            stepResults = listOf(StepResult(action = "Do", verdict = StepVerdict.PASSED)),
            result = RunResult.BLOCKED, manualResult = true)))
        assertEquals(original.cases, TestRunParser.parse(TestRunSerializer.serialize(original)).cases)
    }
}
