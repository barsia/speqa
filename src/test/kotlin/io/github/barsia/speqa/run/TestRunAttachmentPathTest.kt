package io.github.barsia.speqa.run

import io.github.barsia.speqa.model.Attachment
import io.github.barsia.speqa.model.TestCase
import io.github.barsia.speqa.model.TestStep
import org.junit.Assert.assertEquals
import org.junit.Test

class TestRunAttachmentPathTest {

    @Test
    fun `rebaseAttachmentPath rewrites case relative path for run directory`() {
        val rebased = TestRunSupport.rebaseAttachmentPath(
            sourcePath = "attachments/sample-login/screenshot.png",
            sourceFilePath = "test-cases/mcp/sample-login.tc.md",
            targetDirectoryPath = "test-runs",
        )

        assertEquals("../test-cases/mcp/attachments/sample-login/screenshot.png", rebased)
    }

    @Test
    fun `createInitialRun rebases top level and step attachment paths for run file location`() {
        val run = TestRunSupport.createInitialRun(
            testCase = TestCase(
                title = "Sample",
                attachments = listOf(Attachment("attachments/sample-login/spec.pdf")),
                steps = listOf(
                    TestStep(
                        action = "Do thing",
                        expected = "Done",
                        actionAttachments = listOf(Attachment("attachments/sample-login/action.png")),
                        expectedAttachments = listOf(Attachment("attachments/sample-login/expected.png")),
                    ),
                ),
            ),
            sourceFilePath = "test-cases/mcp/sample-login.tc.md",
            targetDirectoryPath = "test-runs",
            runner = "qa",
        )

        assertEquals(listOf(Attachment("../test-cases/mcp/attachments/sample-login/spec.pdf")), run.attachments)
        assertEquals(
            listOf(Attachment("../test-cases/mcp/attachments/sample-login/action.png")),
            run.stepResults.single().actionAttachments,
        )
        assertEquals(
            listOf(Attachment("../test-cases/mcp/attachments/sample-login/expected.png")),
            run.stepResults.single().expectedAttachments,
        )
    }
}
