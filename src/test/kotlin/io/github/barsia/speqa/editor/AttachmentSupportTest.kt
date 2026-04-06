package io.github.barsia.speqa.editor

import com.intellij.openapi.application.runWriteAction
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.barsia.speqa.model.Attachment

class AttachmentSupportTest : BasePlatformTestCase() {

    fun `test resolveFile uses file relative attachment path`() {
        myFixture.addFileToProject(
            "test-cases/mcp/attachments/sample-login/screenshot.png",
            "binary-placeholder",
        )
        val tcFile = myFixture.addFileToProject(
            "test-cases/mcp/sample-login.tc.md",
            """
            ---
            title: "Sample"
            ---
            """.trimIndent(),
        ).virtualFile

        val resolved = AttachmentSupport.resolveFile(
            project,
            tcFile,
            Attachment("attachments/sample-login/screenshot.png"),
        )

        assertNotNull("File-relative attachment path should resolve from the markdown file directory", resolved)
        assertEquals("screenshot.png", resolved?.name)
    }

    fun `test resolveFile does not treat attachment path as project root relative`() {
        myFixture.addFileToProject(
            "test-cases/mcp/attachments/sample-login/screenshot.png",
            "binary-placeholder",
        )
        val tcFile = myFixture.addFileToProject(
            "test-cases/mcp/sample-login.tc.md",
            """
            ---
            title: "Sample"
            ---
            """.trimIndent(),
        ).virtualFile

        val resolved = AttachmentSupport.resolveFile(
            project,
            tcFile,
            Attachment("test-cases/mcp/attachments/sample-login/screenshot.png"),
        )

        assertNull("Project-root-relative attachment paths are not supported", resolved)
    }

    fun `test copyFileToAttachments returns path relative to test case file`() {
        val source = myFixture.addFileToProject("fixtures/upload.png", "binary-placeholder").virtualFile
        val tcFile = myFixture.addFileToProject(
            "test-cases/mcp/sample-login.tc.md",
            """
            ---
            title: "Sample"
            ---
            """.trimIndent(),
        ).virtualFile

        val attachment = runWriteAction {
            AttachmentSupport.copyFileToAttachments(project, tcFile, source)
        }

        assertNotNull("Attachment copy should succeed", attachment)
        assertEquals(
            "attachments/sample-login/upload.png",
            attachment?.path,
        )
    }
}
