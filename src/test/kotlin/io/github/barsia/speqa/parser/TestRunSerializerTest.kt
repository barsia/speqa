package io.github.barsia.speqa.parser

import io.github.barsia.speqa.model.Attachment
import io.github.barsia.speqa.model.StepResult
import io.github.barsia.speqa.model.StepVerdict
import io.github.barsia.speqa.model.TestRun
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
        assertTrue(result.contains("  - auth"))
        assertTrue(result.contains("  - smoke"))
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
                    actionAttachments = listOf(Attachment("attachments/screenshot.png")),
                    expectedAttachments = listOf(Attachment("attachments/report.pdf")),
                ),
            ),
        )
        val result = TestRunSerializer.serialize(run)
        assertTrue("Merged attachment block present", result.contains("   ![screenshot.png](attachments/screenshot.png)"))
        assertTrue("Merged attachment block present", result.contains("   [report.pdf](attachments/report.pdf)"))
        assertTrue(result.indexOf("   ![screenshot.png](attachments/screenshot.png)") > result.indexOf("   > Page loads"))
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
