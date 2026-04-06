package io.github.barsia.speqa.parser

import io.github.barsia.speqa.model.Link
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentPatcherTest {

    private fun applyEdits(text: String, edits: List<DocumentEdit>): String {
        val sb = StringBuilder(SpeqaMarkdown.normalizeLineEndings(text))
        for (edit in edits.sortedByDescending { it.offset }) {
            sb.replace(edit.offset, edit.offset + edit.length, edit.replacement)
        }
        return sb.toString()
    }

    // ── 1. Edit existing title ──────────────────────────────────

    @Test
    fun `edit existing title - only title line changes, everything else byte-identical`() {
        val doc = """
            |---
            |title: "Login test"
            |priority: major
            |status: draft
            |---
            |
            |Description text.
            |
            |Scenario:
            |
            |1. Do something
        """.trimMargin()

        val edits = DocumentPatcher.patch(doc, PatchOperation.SetFrontmatterField("title", "Logout test"))
        val result = applyEdits(doc, edits)

        assertTrue(result.contains("title: \"Logout test\""))
        assertTrue(result.contains("priority: major"))
        assertTrue(result.contains("status: draft"))
        assertTrue(result.contains("Description text."))
        assertTrue(result.contains("1. Do something"))
        val expected = doc.replace("title: \"Login test\"", "title: \"Logout test\"")
        assertEquals(expected, result)
    }

    // ── 2. Edit existing priority ───────────────────────────────

    @Test
    fun `edit existing priority - only priority line changes`() {
        val doc = """
            |---
            |title: "Test"
            |priority: major
            |status: draft
            |---
            |
            |Description.
        """.trimMargin()

        val edits = DocumentPatcher.patch(doc, PatchOperation.SetFrontmatterField("priority", "low"))
        val result = applyEdits(doc, edits)

        assertTrue(result.contains("priority: low"))
        val expected = doc.replace("priority: major", "priority: low")
        assertEquals(expected, result)
    }

    // ── 3. Add priority to document without it ──────────────────

    @Test
    fun `add priority to document without it - inserted at correct position`() {
        val doc = """
            |---
            |title: "Test"
            |status: draft
            |---
            |
            |Description.
        """.trimMargin()

        val edits = DocumentPatcher.patch(doc, PatchOperation.SetFrontmatterField("priority", "normal"))
        val result = applyEdits(doc, edits)

        assertTrue(result.contains("priority: normal"))
        val priorityIdx = result.indexOf("priority: normal")
        val statusIdx = result.indexOf("status: draft")
        assertTrue("priority should be before status", priorityIdx < statusIdx)
        val titleIdx = result.indexOf("title: \"Test\"")
        assertTrue("title should be before priority", titleIdx < priorityIdx)
        assertTrue(result.contains("Description."))
    }

    // ── 4. Remove status field ──────────────────────────────────

    @Test
    fun `remove status field - only that line removed`() {
        val doc = """
            |---
            |title: "Test"
            |priority: major
            |status: draft
            |---
            |
            |Description.
        """.trimMargin()

        val edits = DocumentPatcher.patch(doc, PatchOperation.SetFrontmatterField("status", null))
        val result = applyEdits(doc, edits)

        assertTrue(!result.contains("status:"))
        assertTrue(result.contains("title: \"Test\""))
        assertTrue(result.contains("priority: major"))
        assertTrue(result.contains("Description."))
        val expected = """
            |---
            |title: "Test"
            |priority: major
            |---
            |
            |Description.
        """.trimMargin()
        assertEquals(expected, result)
    }

    // ── 5. Add tags list to document without it ─────────────────

    @Test
    fun `add tags list to document without it`() {
        val doc = """
            |---
            |title: "Test"
            |priority: major
            |---
            |
            |Description.
        """.trimMargin()

        val edits = DocumentPatcher.patch(
            doc,
            PatchOperation.SetFrontmatterList("tags", listOf("smoke", "auth")),
        )
        val result = applyEdits(doc, edits)

        assertTrue(result.contains("tags:\n"))
        assertTrue(result.contains("  - \"smoke\""))
        assertTrue(result.contains("  - \"auth\""))
        val tagsIdx = result.indexOf("tags:")
        val closeIdx = result.lastIndexOf("---")
        assertTrue("tags should be before close delimiter", tagsIdx < closeIdx)
        assertTrue(result.contains("title: \"Test\""))
        assertTrue(result.contains("priority: major"))
        assertTrue(result.contains("Description."))
    }

    // ── 6. Edit existing tags list ──────────────────────────────

    @Test
    fun `edit existing tags list - replace all list lines`() {
        val doc = """
            |---
            |title: "Test"
            |tags:
            |  - "smoke"
            |  - "auth"
            |---
            |
            |Description.
        """.trimMargin()

        val edits = DocumentPatcher.patch(
            doc,
            PatchOperation.SetFrontmatterList("tags", listOf("regression", "critical")),
        )
        val result = applyEdits(doc, edits)

        assertTrue(result.contains("  - \"regression\""))
        assertTrue(result.contains("  - \"critical\""))
        assertTrue(!result.contains("\"smoke\""))
        assertTrue(!result.contains("\"auth\""))
        assertTrue(result.contains("title: \"Test\""))
        assertTrue(result.contains("Description."))
    }

    // ── 7. Remove environment field including continuation lines ─

    @Test
    fun `remove environment field with continuation lines`() {
        val doc = """
            |---
            |title: "Test"
            |environment:
            |  - "Chrome"
            |  - "Firefox"
            |status: draft
            |---
            |
            |Description.
        """.trimMargin()

        val edits = DocumentPatcher.patch(
            doc,
            PatchOperation.SetFrontmatterList("environment", null),
        )
        val result = applyEdits(doc, edits)

        assertTrue(!result.contains("environment:"))
        assertTrue(!result.contains("Chrome"))
        assertTrue(!result.contains("Firefox"))
        assertTrue(result.contains("title: \"Test\""))
        assertTrue(result.contains("status: draft"))
        assertTrue(result.contains("Description."))
        val expected = """
            |---
            |title: "Test"
            |status: draft
            |---
            |
            |Description.
        """.trimMargin()
        assertEquals(expected, result)
    }

    // ── 8. Add id field - inserted before title ─────────────────

    @Test
    fun `add id field - inserted before title`() {
        val doc = """
            |---
            |title: "Test"
            |priority: major
            |---
            |
            |Description.
        """.trimMargin()

        val edits = DocumentPatcher.patch(doc, PatchOperation.SetFrontmatterField("id", "42"))
        val result = applyEdits(doc, edits)

        assertTrue(result.contains("id: 42"))
        val idIdx = result.indexOf("id: 42")
        val titleIdx = result.indexOf("title: \"Test\"")
        assertTrue("id should be before title", idIdx < titleIdx)
        assertTrue(result.contains("priority: major"))
        assertTrue(result.contains("Description."))
    }

    // ── 9. Set title to value with special chars ────────────────

    @Test
    fun `set title with special chars - quotes and backslashes escaped`() {
        val doc = """
            |---
            |title: "Simple title"
            |---
            |
            |Description.
        """.trimMargin()

        val edits = DocumentPatcher.patch(
            doc,
            PatchOperation.SetFrontmatterField("title", "Test with \"quotes\" and \\backslash"),
        )
        val result = applyEdits(doc, edits)

        assertTrue(result.contains("title: \"Test with \\\"quotes\\\" and \\\\backslash\""))
        assertTrue(result.contains("Description."))
    }

    // ── 10. No-op: set field to null that doesn't exist ─────────

    @Test
    fun `no-op - set non-existent field to null returns no edits`() {
        val doc = """
            |---
            |title: "Test"
            |---
            |
            |Description.
        """.trimMargin()

        val edits = DocumentPatcher.patch(doc, PatchOperation.SetFrontmatterField("status", null))

        assertTrue("Should produce no edits", edits.isEmpty())
        val result = applyEdits(doc, edits)
        assertEquals(SpeqaMarkdown.normalizeLineEndings(doc), result)
    }

    // ── 11. SetLinks: replace existing links ────────────────────

    @Test
    fun `replace existing links - body updated`() {
        val doc = """
            |---
            |title: "Test"
            |---
            |
            |Links:
            |
            |[Old link](https://old.example.com)
            |
            |Scenario:
            |
            |1. Do something
        """.trimMargin()

        val newLinks = listOf(
            Link("New link 1", "https://new1.example.com"),
            Link("New link 2", "https://new2.example.com"),
        )
        val edits = DocumentPatcher.patch(doc, PatchOperation.SetLinks(newLinks))
        val result = applyEdits(doc, edits)

        assertTrue(result.contains("[New link 1](https://new1.example.com)"))
        assertTrue(result.contains("[New link 2](https://new2.example.com)"))
        assertFalse(result.contains("Old link"))
        assertTrue(result.contains("Links:"))
        assertTrue(result.contains("Scenario:"))
        assertTrue(result.contains("1. Do something"))
    }

    // ── 12. SetLinks: delete links section ──────────────────────

    @Test
    fun `delete links section when setting empty links`() {
        val doc = """
            |---
            |title: "Test"
            |---
            |
            |Links:
            |
            |[Some link](https://example.com)
            |
            |Scenario:
            |
            |1. Do something
        """.trimMargin()

        val edits = DocumentPatcher.patch(doc, PatchOperation.SetLinks(emptyList()))
        val result = applyEdits(doc, edits)

        assertFalse(result.contains("Links:"))
        assertFalse(result.contains("Some link"))
        assertTrue(result.contains("Scenario:"))
        assertTrue(result.contains("1. Do something"))
    }

    // ── 13. SetLinks: insert links into document without them ───

    @Test
    fun `insert links into document without links section`() {
        val doc = """
            |---
            |title: "Test"
            |---
            |
            |Scenario:
            |
            |1. Do something
        """.trimMargin()

        val links = listOf(
            Link("Jira", "https://jira.example.com/TC-1"),
        )
        val edits = DocumentPatcher.patch(doc, PatchOperation.SetLinks(links))
        val result = applyEdits(doc, edits)

        assertTrue(result.contains("Links:"))
        assertTrue(result.contains("[Jira](https://jira.example.com/TC-1)"))
        assertTrue(result.contains("Scenario:"))
        val linksIdx = result.indexOf("Links:")
        val stepsIdx = result.indexOf("Scenario:")
        assertTrue("Links should be before Scenario", linksIdx < stepsIdx)
    }

    // ── 14. SetLinks: no-op when no section and empty links ─────

    @Test
    fun `no-op when no links section and empty links`() {
        val doc = """
            |---
            |title: "Test"
            |---
            |
            |Scenario:
            |
            |1. Do something
        """.trimMargin()

        val edits = DocumentPatcher.patch(doc, PatchOperation.SetLinks(emptyList()))
        assertTrue("Should produce no edits", edits.isEmpty())
    }
}
