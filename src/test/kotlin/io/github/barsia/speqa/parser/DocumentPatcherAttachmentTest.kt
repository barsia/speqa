package io.github.barsia.speqa.parser

import io.github.barsia.speqa.model.Attachment
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentPatcherAttachmentTest {

    private fun applyEdits(text: String, edits: List<DocumentEdit>): String {
        val sb = StringBuilder(SpeqaMarkdown.normalizeLineEndings(text))
        for (edit in edits) {
            sb.replace(edit.offset, edit.offset + edit.length, edit.replacement)
        }
        return sb.toString()
    }

    // ── 1. Edit document-level attachments ──────────────────────

    @Test
    fun `edit document-level attachments - replace existing`() {
        val doc = """
            |---
            |title: "Test"
            |---
            |
            |Attachments:
            |
            |[old-file.png]
            |
            |Scenario:
            |
            |1. Do something
        """.trimMargin() + "\n"

        val edits = DocumentPatcher.patch(
            doc,
            PatchOperation.SetAttachments(listOf(Attachment("new-file.png"), Attachment("another.pdf"))),
        )

        val result = applyEdits(doc, edits)

        assertTrue(result.contains("[new-file.png]"))
        assertTrue(result.contains("[another.pdf]"))
        assertFalse(result.contains("[old-file.png]"))
        assertTrue(result.contains("Attachments:"))
        assertTrue(result.contains("Scenario:"))
    }

    // ── 2. Add attachments section to document without one ──────

    @Test
    fun `add attachments section to document without one`() {
        val doc = """
            |---
            |title: "Test"
            |---
            |
            |Scenario:
            |
            |1. Do something
        """.trimMargin() + "\n"

        val edits = DocumentPatcher.patch(
            doc,
            PatchOperation.SetAttachments(listOf(Attachment("screenshot.png"))),
        )

        val result = applyEdits(doc, edits)

        assertTrue(result.contains("Attachments:"))
        assertTrue(result.contains("[screenshot.png]"))
        // Attachments section should appear before Scenario
        val attachmentsIdx = result.indexOf("Attachments:")
        val stepsIdx = result.indexOf("Scenario:")
        assertTrue("Attachments should be before Scenario", attachmentsIdx < stepsIdx)
    }

    // ── 3. Remove attachments section ───────────────────────────

    @Test
    fun `remove attachments section entirely`() {
        val doc = """
            |---
            |title: "Test"
            |---
            |
            |Attachments:
            |
            |[file.png]
            |
            |Scenario:
            |
            |1. Do something
        """.trimMargin() + "\n"

        val edits = DocumentPatcher.patch(
            doc,
            PatchOperation.SetAttachments(emptyList()),
        )

        val result = applyEdits(doc, edits)

        assertFalse(result.contains("Attachments:"))
        assertFalse(result.contains("[file.png]"))
        assertTrue(result.contains("Scenario:"))
        assertTrue(result.contains("1. Do something"))
    }

    // ── 4. Add step attachment to step without one ─────────────

    @Test
    fun `add step attachment to step without one`() {
        val doc = """
            |---
            |title: "Test"
            |---
            |
            |Scenario:
            |
            |1. Click the button
            |   > Button is highlighted
        """.trimMargin() + "\n"

        val edits = DocumentPatcher.patch(
            doc,
            PatchOperation.SetStepAttachments(0, listOf(Attachment("click-screenshot.png"))),
        )

        val result = applyEdits(doc, edits)

        assertTrue(result.contains("   ![click-screenshot.png](click-screenshot.png)"))
        assertTrue(result.contains("1. Click the button"))
        assertTrue(result.contains("   > Button is highlighted"))
        // Attachment should be after expected
        val attachmentIdx = result.indexOf("   ![click-screenshot.png](click-screenshot.png)")
        val expectedIdx = result.indexOf("   > Button is highlighted")
        assertTrue("Attachment after expected", attachmentIdx > expectedIdx)
    }

    // ── 5. Remove step attachment from step ────────────────────

    @Test
    fun `remove step attachment from step`() {
        val doc = """
            |---
            |title: "Test"
            |---
            |
            |Scenario:
            |
            |1. Click the button
            |   > Button is highlighted
            |   ![click-screenshot.png](click-screenshot.png)
        """.trimMargin() + "\n"

        val edits = DocumentPatcher.patch(
            doc,
            PatchOperation.SetStepAttachments(0, emptyList()),
        )

        val result = applyEdits(doc, edits)

        assertFalse(result.contains("[click-screenshot.png]"))
        assertTrue(result.contains("1. Click the button"))
        assertTrue(result.contains("   > Button is highlighted"))
    }
}
