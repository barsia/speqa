package io.github.barsia.speqa.parser

import io.github.barsia.speqa.model.Attachment
import io.github.barsia.speqa.model.Link
import io.github.barsia.speqa.model.Priority
import io.github.barsia.speqa.model.RunCase
import io.github.barsia.speqa.model.RunResult
import io.github.barsia.speqa.model.StepResult
import io.github.barsia.speqa.model.StepVerdict
import io.github.barsia.speqa.model.TestRun
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class TestRunSerializerTest {

    @Test
    fun `serializes title in frontmatter`() {
        val run = flatRun(title = "Login test", startedAt = LocalDateTime.of(2026, 4, 11, 10, 0))
        val result = TestRunSerializer.serialize(run)
        assertTrue(result.contains("title: \"Login test\""))
        assertFalse("Must not contain test_case", result.contains("test_case"))
    }

    @Test
    fun `serializes manual_result only when true`() {
        val runTrue = flatRun(title = "Test", manualResult = true, startedAt = LocalDateTime.of(2026, 4, 11, 10, 0))
        assertTrue(TestRunSerializer.serialize(runTrue).contains("manual_result: true"))

        val runFalse = flatRun(title = "Test", manualResult = false, startedAt = LocalDateTime.of(2026, 4, 11, 10, 0))
        assertFalse(TestRunSerializer.serialize(runFalse).contains("manual_result"))
    }

    @Test
    fun `serializes step with expected and verdict`() {
        val run = flatRun(
            title = "Test",
            startedAt = LocalDateTime.of(2026, 4, 11, 10, 0),
            stepResults = listOf(StepResult(action = "Click button", expected = "Page loads", verdict = StepVerdict.PASSED)),
        )
        val result = TestRunSerializer.serialize(run)
        assertTrue(result.contains("Scenario:"))
        assertTrue(result.contains("1. Click button"))
        assertTrue(result.contains("> Page loads"))
        assertFalse("No Expected: prefix", result.contains("Expected:"))
        assertTrue("Verdict line present", result.contains("- passed"))
        assertFalse("No bold markers", result.contains("**"))
    }

    @Test
    fun `omits verdict line for NONE`() {
        val run = flatRun(
            title = "Test",
            startedAt = LocalDateTime.of(2026, 4, 11, 10, 0),
            stepResults = listOf(StepResult(action = "Click button", expected = "Page loads", verdict = StepVerdict.NONE)),
        )
        val result = TestRunSerializer.serialize(run)
        assertTrue(result.contains("1. Click button"))
        assertFalse("No verdict marker", result.contains("\n   - "))
    }

    @Test
    fun `serializes step comment as explicit Comment block`() {
        val run = flatRun(
            title = "Test",
            startedAt = LocalDateTime.of(2026, 4, 11, 10, 0),
            stepResults = listOf(StepResult(action = "Click", expected = "", verdict = StepVerdict.FAILED, comment = "Got 500 error")),
        )
        val result = TestRunSerializer.serialize(run)
        assertTrue(result.contains("- failed"))
        assertFalse("No bold markers", result.contains("**"))
        assertTrue("Comment is explicit", result.contains("\n\n   Comment:\n   Got 500 error"))
    }

    @Test
    fun `serializes blocked verdict`() {
        val run = flatRun(
            title = "Test",
            startedAt = LocalDateTime.of(2026, 4, 11, 10, 0),
            stepResults = listOf(StepResult(action = "Click", verdict = StepVerdict.BLOCKED)),
        )
        val serialized = TestRunSerializer.serialize(run)
        assertTrue(serialized.contains("- blocked"))
        assertFalse("No bold markers", serialized.contains("**"))
    }

    @Test
    fun `serializes tags in the case section`() {
        val run = flatRun(
            title = "Test",
            tags = listOf("auth", "smoke"),
            startedAt = LocalDateTime.of(2026, 4, 11, 10, 0),
        )
        val result = TestRunSerializer.serialize(run)
        // tags now live in the case section, not the run frontmatter
        assertFalse("No tags in frontmatter", result.substringBefore("Test Case:").contains("tags:"))
        assertTrue(result.substringAfter("Test Case:").contains("tags: auth, smoke"))
    }

    @Test
    fun `omits tags when empty`() {
        val run = flatRun(
            title = "Test",
            tags = emptyList(),
            startedAt = LocalDateTime.of(2026, 4, 11, 10, 0),
        )
        assertFalse(TestRunSerializer.serialize(run).contains("tags:"))
    }

    @Test
    fun `serializes environment values in the case section`() {
        // environment now lives in the case section as a single comma-joined line, never in frontmatter
        val single = TestRunSerializer.serialize(
            flatRun(
                title = "Test",
                environment = listOf("chrome"),
                startedAt = LocalDateTime.of(2026, 4, 11, 10, 0),
            ),
        )
        assertFalse("No environment in frontmatter", single.substringBefore("Test Case:").contains("environment:"))
        assertTrue(single.substringAfter("Test Case:").contains("environment: chrome"))

        val multiple = TestRunSerializer.serialize(
            flatRun(
                title = "Test",
                environment = listOf("chrome", "firefox"),
                startedAt = LocalDateTime.of(2026, 4, 11, 10, 0),
            ),
        )
        assertFalse("No environment in frontmatter", multiple.substringBefore("Test Case:").contains("environment:"))
        assertTrue(multiple.substringAfter("Test Case:").contains("environment: chrome, firefox"))
    }

    @Test
    fun `writes Scenario marker before steps`() {
        val run = flatRun(
            title = "Test",
            startedAt = LocalDateTime.of(2026, 4, 11, 10, 0),
            stepResults = listOf(StepResult(action = "Click button", verdict = StepVerdict.PASSED)),
        )
        val result = TestRunSerializer.serialize(run)
        assertTrue("Scenario marker present", result.contains("Scenario:"))
        assertFalse("No Step Results heading", result.contains("## Step Results"))
    }

    @Test
    fun `never writes Summary section`() {
        val run = flatRun(
            title = "Test",
            startedAt = LocalDateTime.of(2026, 4, 11, 10, 0),
        )
        val result = TestRunSerializer.serialize(run)
        assertFalse("No Summary heading", result.contains("## Summary"))
    }

    @Test
    fun `omits started_at when null`() {
        val run = flatRun(title = "Test", startedAt = null)
        val result = TestRunSerializer.serialize(run)
        assertFalse(result.contains("started_at"))
    }

    @Test
    fun `writes started_at when present`() {
        val run = flatRun(title = "Test", startedAt = LocalDateTime.of(2026, 4, 11, 10, 0))
        val result = TestRunSerializer.serialize(run)
        assertTrue(result.contains("started_at"))
    }

    @Test
    fun `serializes multiline action`() {
        val run = flatRun(
            title = "Test",
            startedAt = LocalDateTime.of(2026, 4, 11, 10, 0),
            stepResults = listOf(
                StepResult(
                    action = "First line\nSecond line\nThird line",
                    expected = "Result",
                    verdict = StepVerdict.PASSED,
                ),
            ),
        )
        val result = TestRunSerializer.serialize(run)
        assertTrue(result.contains("1. First line  "))
        assertTrue(result.contains("   Second line  "))
        assertTrue(result.contains("   Third line"))
        assertTrue(result.contains("   > Result"))
    }

    @Test
    fun `serializes formatted continuation without turning it into comment`() {
        val run = flatRun(
            title = "Test",
            startedAt = LocalDateTime.of(2026, 4, 11, 10, 0),
            stepResults = listOf(
                StepResult(
                    action = "Type \"testuser@example.com\" ~~into~~ _**~~the~~**_ **_email_** field\n**wwwwwwww**",
                    verdict = StepVerdict.PASSED,
                ),
            ),
        )
        val result = TestRunSerializer.serialize(run)
        assertTrue(result.contains("1. Type \"testuser@example.com\" ~~into~~ _**~~the~~**_ **_email_** field  "))
        assertTrue(result.contains("   **wwwwwwww**"))
        assertFalse(result.contains("   Comment:\n   **wwwwwwww**"))
    }

    @Test
    fun `serializes step-level attachments`() {
        val run = flatRun(
            title = "Test",
            startedAt = LocalDateTime.of(2026, 4, 11, 10, 0),
            stepResults = listOf(
                StepResult(
                    action = "Click button",
                    expected = "Page loads",
                    verdict = StepVerdict.PASSED,
                    attachments = listOf(
                        Attachment("attachments/screenshot.png"),
                        Attachment("attachments/report.pdf"),
                    ),
                ),
            ),
        )
        val result = TestRunSerializer.serialize(run)
        assertTrue(result.contains("   ![screenshot.png](attachments/screenshot.png)"))
        assertTrue(result.contains("   [report.pdf](attachments/report.pdf)"))
        assertTrue(result.indexOf("   > Page loads") < result.indexOf("   ![screenshot.png](attachments/screenshot.png)"))
        assertTrue(result.indexOf("   ![screenshot.png](attachments/screenshot.png)") < result.indexOf("   [report.pdf](attachments/report.pdf)"))
    }

    @Test
    fun `serializes step links separately from attachments`() {
        val run = flatRun(
            title = "Test",
            startedAt = LocalDateTime.of(2026, 4, 11, 10, 0),
            stepResults = listOf(
                StepResult(
                    action = "Open login page",
                    expected = "Login page is visible",
                    verdict = StepVerdict.PASSED,
                    links = listOf(Link("Spec", "https://example.com/spec")),
                    attachments = listOf(
                        Attachment("attachments/action.png"),
                        Attachment("attachments/report.pdf"),
                    ),
                ),
            ),
        )
        val result = TestRunSerializer.serialize(run)
        assertTrue(result.contains("   Links: [Spec](https://example.com/spec)"))
        assertTrue(result.contains("   ![action.png](attachments/action.png)"))
        assertTrue(result.contains("   [report.pdf](attachments/report.pdf)"))
    }

    @Test
    fun `round trip preserves step links and attachments`() {
        val original = flatRun(
            title = "Round trip",
            environment = listOf("Chrome 122", "macOS 14"),
            startedAt = LocalDateTime.of(2026, 4, 11, 10, 0),
            stepResults = listOf(
                StepResult(
                    action = "Open",
                    expected = "Opened",
                    verdict = StepVerdict.PASSED,
                    tickets = listOf("QA-1"),
                    links = listOf(Link("Spec", "https://example.com/spec")),
                    attachments = listOf(
                        Attachment("a.png"),
                        Attachment("b.png"),
                    ),
                ),
            ),
        )

        val parsed = TestRunParser.parse(TestRunSerializer.serialize(original))
        assertTrue(parsed.environment == original.environment)
        assertTrue(parsed.stepResults == original.stepResults)
    }

    @Test
    fun `round trip preserves step links with commas inside url`() {
        val original = flatRun(
            title = "Round trip",
            startedAt = LocalDateTime.of(2026, 4, 11, 10, 0),
            stepResults = listOf(
                StepResult(
                    action = "Open",
                    expected = "Opened",
                    links = listOf(
                        Link("Spec", "https://example.com/spec?labels=a,b"),
                        Link("", "https://example.com/raw?a=1,b=2"),
                    ),
                ),
            ),
        )

        val serialized = TestRunSerializer.serialize(original)
        assertTrue(serialized.contains("Links: [Spec](https://example.com/spec?labels=a,b), [](https://example.com/raw?a=1,b=2)"))

        val parsed = TestRunParser.parse(serialized)
        assertEquals(original.stepResults.single().links, parsed.stepResults.single().links)
    }

    @Test
    fun `serializes one case section with marker, metadata and result`() {
        val run = TestRun(
            id = 7, title = "Login test", runner = "alice", result = RunResult.PASSED,
            cases = listOf(RunCase(
                caseId = 5, title = "Sign in", priority = Priority.MAJOR,
                tags = listOf("smoke", "auth"), environment = listOf("chrome"),
                stepResults = listOf(StepResult(action = "Open page", expected = "Visible",
                    verdict = StepVerdict.PASSED)),
                result = RunResult.PASSED,
            )),
        )
        val out = TestRunSerializer.serialize(run)

        assertTrue(out.contains("Test Case: TC-5 Sign in"))
        assertTrue(out.contains("priority: major"))
        assertTrue(out.contains("tags: smoke, auth"))
        assertTrue(out.contains("environment: chrome"))
        assertTrue(out.contains("Scenario:"))
        assertTrue(out.contains("1. Open page"))
        assertTrue(out.contains("- passed"))
        assertTrue(out.contains("Result: passed"))
        // run-level tags/priority must NOT be in frontmatter anymore
        assertFalse(out.substringBefore("Test Case:").contains("tags:"))
    }

    @Test
    fun `round trip preserves multi-case run`() {
        val original = TestRun(
            id = 12, title = "High", runner = "alice", result = RunResult.FAILED,
            cases = listOf(
                RunCase(caseId = 5, title = "Sign in", priority = Priority.MAJOR,
                    tags = listOf("smoke"), environment = listOf("chrome"),
                    stepResults = listOf(StepResult(action = "Open", expected = "Visible",
                        verdict = StepVerdict.PASSED)), result = RunResult.PASSED),
                RunCase(caseId = 8, title = "Wrong password",
                    stepResults = listOf(StepResult(action = "Enter wrong",
                        verdict = StepVerdict.FAILED)), result = RunResult.FAILED),
            ),
        )
        val reparsed = TestRunParser.parse(TestRunSerializer.serialize(original))
        assertEquals(original.cases, reparsed.cases)
    }

    @Test
    fun `serializes manual_result in the case section only when manual`() {
        val manual = TestRun(id = 1, cases = listOf(RunCase(caseId = 5, title = "A",
            result = RunResult.BLOCKED, manualResult = true)))
        val auto = TestRun(id = 1, cases = listOf(RunCase(caseId = 5, title = "A",
            result = RunResult.PASSED, manualResult = false)))
        val outManual = TestRunSerializer.serialize(manual)
        val outAuto = TestRunSerializer.serialize(auto)
        assertTrue(outManual.contains("manual_result: true"))
        assertTrue(outManual.contains("Result: blocked"))
        assertFalse(outAuto.contains("manual_result"))
    }
}
