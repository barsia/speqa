package io.github.barsia.speqa.parser

import io.github.barsia.speqa.model.Attachment
import io.github.barsia.speqa.model.Link
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
        val run = TestRun(title = "Login test", startedAt = LocalDateTime.of(2026, 4, 11, 10, 0))
        val result = TestRunSerializer.serialize(run)
        assertTrue(result.contains("title: \"Login test\""))
        assertFalse("Must not contain test_case", result.contains("test_case"))
    }

    @Test
    fun `serializes manual_result only when true`() {
        val runTrue = TestRun(title = "Test", manualResult = true, startedAt = LocalDateTime.of(2026, 4, 11, 10, 0))
        assertTrue(TestRunSerializer.serialize(runTrue).contains("manual_result: true"))

        val runFalse = TestRun(title = "Test", manualResult = false, startedAt = LocalDateTime.of(2026, 4, 11, 10, 0))
        assertFalse(TestRunSerializer.serialize(runFalse).contains("manual_result"))
    }

    @Test
    fun `serializes step with expected and verdict`() {
        val run = TestRun(
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
        val run = TestRun(
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
        val run = TestRun(
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
        val run = TestRun(
            title = "Test",
            startedAt = LocalDateTime.of(2026, 4, 11, 10, 0),
            stepResults = listOf(StepResult(action = "Click", verdict = StepVerdict.BLOCKED)),
        )
        val serialized = TestRunSerializer.serialize(run)
        assertTrue(serialized.contains("- blocked"))
        assertFalse("No bold markers", serialized.contains("**"))
    }

    @Test
    fun `serializes tags in frontmatter`() {
        val run = TestRun(
            title = "Test",
            tags = listOf("auth", "smoke"),
            startedAt = LocalDateTime.of(2026, 4, 11, 10, 0),
        )
        val result = TestRunSerializer.serialize(run)
        assertTrue(result.contains("tags:"))
        assertTrue(result.contains("  - \"auth\""))
        assertTrue(result.contains("  - \"smoke\""))
    }

    @Test
    fun `omits tags when empty`() {
        val run = TestRun(
            title = "Test",
            tags = emptyList(),
            startedAt = LocalDateTime.of(2026, 4, 11, 10, 0),
        )
        assertFalse(TestRunSerializer.serialize(run).contains("tags:"))
    }

    @Test
    fun `serializes single environment value as scalar`() {
        val run = TestRun(
            title = "Test",
            environment = listOf("Chrome 122, macOS 14"),
            startedAt = LocalDateTime.of(2026, 4, 11, 10, 0),
        )
        val result = TestRunSerializer.serialize(run)
        assertTrue(result.contains("environment: \"Chrome 122, macOS 14\""))
        assertFalse(result.contains("environment:\n"))
    }

    @Test
    fun `serializes multiple environment values as list`() {
        val run = TestRun(
            title = "Test",
            environment = listOf("Chrome 122", "Firefox 123"),
            startedAt = LocalDateTime.of(2026, 4, 11, 10, 0),
        )
        val result = TestRunSerializer.serialize(run)
        assertTrue(result.contains("environment:"))
        assertTrue(result.contains("  - \"Chrome 122\""))
        assertTrue(result.contains("  - \"Firefox 123\""))
    }

    @Test
    fun `writes Scenario marker before steps`() {
        val run = TestRun(
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
        val run = TestRun(
            title = "Test",
            startedAt = LocalDateTime.of(2026, 4, 11, 10, 0),
        )
        val result = TestRunSerializer.serialize(run)
        assertFalse("No Summary heading", result.contains("## Summary"))
    }

    @Test
    fun `omits started_at when null`() {
        val run = TestRun(title = "Test", startedAt = null)
        val result = TestRunSerializer.serialize(run)
        assertFalse(result.contains("started_at"))
    }

    @Test
    fun `writes started_at when present`() {
        val run = TestRun(title = "Test", startedAt = LocalDateTime.of(2026, 4, 11, 10, 0))
        val result = TestRunSerializer.serialize(run)
        assertTrue(result.contains("started_at"))
    }

    @Test
    fun `serializes multiline action`() {
        val run = TestRun(
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
        val run = TestRun(
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
        val run = TestRun(
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
        val run = TestRun(
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
        val original = TestRun(
            title = "Round trip",
            environment = listOf("Chrome 122, macOS 14"),
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
        val original = TestRun(
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
    fun `writes only one blank line before overall comment`() {
        val run = TestRun(
            title = "Test",
            startedAt = LocalDateTime.of(2026, 4, 11, 10, 0),
            stepResults = listOf(
                StepResult(
                    action = "Click",
                    verdict = StepVerdict.FAILED,
                    comment = "Step note",
                ),
            ),
            comment = "Overall note",
        )
        val result = TestRunSerializer.serialize(run)
        assertTrue(result.contains("   Step note\n\nOverall note"))
        assertFalse(result.contains("   Step note\n\n\nOverall note"))
    }
}
