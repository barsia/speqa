package io.github.barsia.speqa.editor

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownLinkDestination
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownLinkLabel

class SpeqaAttachmentPathReferenceTest : BasePlatformTestCase() {

    fun `test attachment path in speqa markdown uses segmented references`() {
        val file = myFixture.configureByText(
            "sample.tc.md",
            """
            ---
            title: "Sample"
            ---
            
            Attachments:
            
            [attachments/screenshots/login/failure.png]
            """.trimIndent()
        )

        val offset = file.text.indexOf("attachments/screenshots/login/failure.png")
        assertTrue("Expected attachment path in test document", offset >= 0)

        val elementAtOffset = file.findElementAt(offset) ?: error("Expected PSI element at attachment path offset")
        val referenceHost = PsiTreeUtil.getParentOfType(
            elementAtOffset,
            MarkdownLinkLabel::class.java,
            MarkdownLinkDestination::class.java,
        ) ?: error("Expected Markdown reference host for attachment path")

        val referenceRanges = referenceHost.references
            .filterNotNull()
            .map { it.rangeInElement }
            .distinct()

        assertEquals(
            "Expected segmented path references for native Markdown hover/navigation",
            listOf("attachments", "screenshots", "login", "failure.png"),
            referenceRanges.map { referenceHost.text.substring(it.startOffset, it.endOffset) }
        )
    }

    fun `test standard markdown link destination keeps segmented references without duplicates`() {
        val file = myFixture.configureByText(
            "sample.tc.md",
            """
            ---
            title: "Sample"
            ---

            Attachments:

            [design spec](attachments/screenshots/login/failure.png)
            """.trimIndent()
        )

        val offset = file.text.indexOf("attachments/screenshots/login/failure.png")
        assertTrue("Expected markdown link destination in test document", offset >= 0)

        val elementAtOffset = file.findElementAt(offset) ?: error("Expected PSI element at link destination offset")
        val referenceHost = PsiTreeUtil.getParentOfType(
            elementAtOffset,
            MarkdownLinkDestination::class.java,
        ) ?: error("Expected MarkdownLinkDestination host for standard markdown link")

        val references = referenceHost.references.filterNotNull()
        val referenceRanges = references.map { it.rangeInElement }
        val distinctRanges = referenceRanges.distinct()

        assertEquals(
            "Standard markdown link destination should expose one reference per visible path segment",
            listOf("attachments", "screenshots", "login", "failure.png"),
            distinctRanges.map { referenceHost.text.substring(it.startOffset, it.endOffset) }
        )
        assertEquals(
            "SpeQA must not duplicate native Markdown destination references",
            distinctRanges.size,
            referenceRanges.size,
        )
    }

    fun `test markdown image destination keeps segmented references without duplicates`() {
        val file = myFixture.configureByText(
            "sample.tc.md",
            """
            ---
            title: "Sample"
            ---

            Attachments:

            ![screenshot](attachments/screenshots/login/failure.png)
            """.trimIndent()
        )

        val offset = file.text.indexOf("attachments/screenshots/login/failure.png")
        assertTrue("Expected markdown image destination in test document", offset >= 0)

        val elementAtOffset = file.findElementAt(offset) ?: error("Expected PSI element at image destination offset")
        val referenceHost = PsiTreeUtil.getParentOfType(
            elementAtOffset,
            MarkdownLinkDestination::class.java,
        ) ?: error("Expected MarkdownLinkDestination host for markdown image")

        val references = referenceHost.references.filterNotNull()
        val referenceRanges = references.map { it.rangeInElement }
        val distinctRanges = referenceRanges.distinct()

        assertEquals(
            "Markdown image destination should expose one reference per visible path segment",
            listOf("attachments", "screenshots", "login", "failure.png"),
            distinctRanges.map { referenceHost.text.substring(it.startOffset, it.endOffset) }
        )
        assertEquals(
            "SpeQA must not duplicate native Markdown image destination references",
            distinctRanges.size,
            referenceRanges.size,
        )
    }

    fun `test file relative markdown image destination resolves from test run file without speqa supplementation`() {
        myFixture.addFileToProject(
            "test-cases/mcp/attachments/add-cancel/Screenshot 2026-04-12 at 11.39.21.png",
            "binary-placeholder"
        )
        myFixture.addFileToProject(
            "test-runs/sample.tr.md",
            """
            ---
            title: "Sample"
            ---

            Scenario:

            1. Observe screenshot
               ![Screenshot 2026-04-12 at 11.39.21.png](../test-cases/mcp/attachments/add-cancel/Screenshot%202026-04-12%20at%2011.39.21.png)
            """.trimIndent()
        )
        val file = myFixture.configureFromTempProjectFile("test-runs/sample.tr.md")

        val offset = file.text.indexOf("../test-cases/mcp/attachments/add-cancel")
        assertTrue("Expected file-relative image destination in test run document", offset >= 0)

        val elementAtOffset = file.findElementAt(offset) ?: error("Expected PSI element at image destination offset")
        val referenceHost = PsiTreeUtil.getParentOfType(
            elementAtOffset,
            MarkdownLinkDestination::class.java,
        ) ?: error("Expected MarkdownLinkDestination host for file-relative markdown image")

        val references = referenceHost.references.filterNotNull()
        val referenceRanges = references.map { it.rangeInElement }
        val distinctRanges = referenceRanges.distinct()

        assertEquals(
            listOf("..", "test-cases", "mcp", "attachments", "add-cancel", "Screenshot%202026-04-12%20at%2011.39.21.png"),
            distinctRanges.map { referenceHost.text.substring(it.startOffset, it.endOffset) }
        )
        assertEquals(
            "Standard markdown destination should remain fully native with no duplicate SpeQA refs",
            distinctRanges.size,
            referenceRanges.size,
        )
    }

    fun `test external link label does not become local file reference`() {
        val file = myFixture.configureByText(
            "sample.tc.md",
            """
            ---
            title: "Sample"
            ---
            
            Links:
            
            [report.pdf](https://example.com/report.pdf)
            """.trimIndent()
        )

        val offset = file.text.indexOf("report.pdf")
        assertTrue("Expected link label in test document", offset >= 0)

        val reference = file.findReferenceAt(offset)
        assertEquals(
            "External link label must not expose a local file reference at label offset",
            null,
            reference,
        )
    }

    fun `test image alt text does not become local file reference`() {
        val file = myFixture.configureByText(
            "sample.tc.md",
            """
            ---
            title: "Sample"
            ---
            
            Attachments:
            
            ![screenshot.png](attachments/screenshots/login/failure.png)
            """.trimIndent()
        )

        val offset = file.text.indexOf("screenshot.png")
        assertTrue("Expected image alt text in test document", offset >= 0)

        val reference = file.findReferenceAt(offset)
        assertEquals(
            "Image alt text must not expose a local file reference at alt-text offset",
            null,
            reference,
        )
    }
}
