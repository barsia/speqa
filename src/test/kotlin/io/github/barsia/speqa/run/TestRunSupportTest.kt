package io.github.barsia.speqa.run

import io.github.barsia.speqa.editor.RunImportOptions
import io.github.barsia.speqa.model.Attachment
import io.github.barsia.speqa.model.Link
import io.github.barsia.speqa.model.TestCase
import io.github.barsia.speqa.model.TestStep
import io.github.barsia.speqa.model.RunResult
import io.github.barsia.speqa.model.StepResult
import io.github.barsia.speqa.model.StepVerdict
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Test
    fun `createInitialRun omits deselected import categories`() {
        val testCase = TestCase(
            title = "Login",
            tags = listOf("smoke"),
            environment = listOf("Chrome 122", "Firefox 123"),
            links = listOf(Link(title = "Spec", url = "https://example.com/spec")),
            attachments = listOf(Attachment("attachments/top.png")),
            steps = listOf(
                TestStep(
                    action = "Open login page",
                    expected = "Login page is visible",
                    attachments = listOf(
                        Attachment("attachments/action.png"),
                        Attachment("attachments/expected.png"),
                    ),
                    tickets = listOf("QA-123"),
                    links = listOf(Link(title = "Step Spec", url = "https://example.com/step-spec")),
                ),
            ),
        )

        val run = TestRunSupport.createInitialRun(
            testCase = testCase,
            sourceFilePath = "/repo/test-cases/sample-login.tc.md",
            targetDirectoryPath = "/repo/test-runs",
            importOptions = RunImportOptions(
                importTags = false,
                importEnvironment = false,
                importTickets = false,
                importLinks = false,
                importAttachments = false,
            ),
            runner = "QA Engineer",
        )

        assertEquals(emptyList<String>(), run.tags)
        assertEquals(emptyList<String>(), run.environment)
        assertEquals(emptyList<Link>(), run.links)
        assertEquals(emptyList<Attachment>(), run.attachments)
        assertEquals(emptyList<String>(), run.stepResults.single().tickets)
        assertEquals(emptyList<Link>(), run.stepResults.single().links)
        assertEquals(emptyList<Attachment>(), run.stepResults.single().attachments)
        assertEquals("Open login page", run.stepResults.single().action)
        assertEquals("Login page is visible", run.stepResults.single().expected)
    }

    @Test
    fun `createInitialRun imports top level and step links when selected`() {
        val testCase = TestCase(
            title = "Login",
            environment = listOf("Chrome 122, macOS 14"),
            links = listOf(Link(title = "Spec", url = "https://example.com/spec")),
            attachments = listOf(Attachment("attachments/top.png")),
            steps = listOf(
                TestStep(
                    action = "Open login page",
                    expected = "Login page is visible",
                    attachments = listOf(
                        Attachment("attachments/action.png"),
                        Attachment("attachments/expected.png"),
                    ),
                    tickets = listOf("QA-123"),
                    links = listOf(Link(title = "Step Spec", url = "https://example.com/step-spec")),
                ),
            ),
        )

        val run = TestRunSupport.createInitialRun(
            testCase = testCase,
            sourceFilePath = "/repo/test-cases/sample-login.tc.md",
            targetDirectoryPath = "/repo/test-runs",
            importOptions = RunImportOptions(
                importTags = false,
                importEnvironment = true,
                importTickets = true,
                importLinks = true,
                importAttachments = true,
            ),
            runner = "QA Engineer",
        )

        assertEquals(listOf("Chrome 122, macOS 14"), run.environment)
        assertEquals(listOf(Link(title = "Spec", url = "https://example.com/spec")), run.links)
        assertEquals(listOf(Link(title = "Step Spec", url = "https://example.com/step-spec")), run.stepResults.single().links)
        assertEquals(
            listOf(Attachment("../test-cases/attachments/top.png")),
            run.attachments,
        )
        assertEquals(
            listOf(
                Attachment("../test-cases/attachments/action.png"),
                Attachment("../test-cases/attachments/expected.png"),
            ),
            run.stepResults.single().attachments,
        )
        assertEquals(listOf("QA-123"), run.stepResults.single().tickets)
    }

    @Test
    fun `default import options copy only tags and environment`() {
        val testCase = TestCase(
            title = "Login",
            tags = listOf("smoke"),
            environment = listOf("test1, env20"),
            links = listOf(Link("Spec", "https://example.com/spec")),
            attachments = listOf(Attachment("top.png")),
            steps = listOf(
                TestStep(
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

        val run = TestRunSupport.createInitialRun(
            testCase = testCase,
            sourceFilePath = "/tmp/login.tc.md",
            targetDirectoryPath = "/tmp/runs",
            importOptions = RunImportOptions(),
        )

        assertEquals(listOf("smoke"), run.tags)
        assertEquals(listOf("test1, env20"), run.environment)
        assertTrue(run.links.isEmpty())
        assertTrue(run.attachments.isEmpty())
        assertEquals("Open", run.stepResults.single().action)
        assertEquals("Opened", run.stepResults.single().expected)
        assertTrue(run.stepResults.single().tickets.isEmpty())
        assertTrue(run.stepResults.single().links.isEmpty())
        assertTrue(run.stepResults.single().attachments.isEmpty())
    }
}
