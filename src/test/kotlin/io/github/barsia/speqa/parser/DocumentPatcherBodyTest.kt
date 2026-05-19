package io.github.barsia.speqa.parser

import io.github.barsia.speqa.model.PreconditionsMarkerStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentPatcherBodyTest {

    /**
     * Applies edits (assumed sorted by descending offset) to produce the patched text.
     */
    private fun applyEdits(text: String, edits: List<DocumentEdit>): String {
        val sb = StringBuilder(SpeqaMarkdown.normalizeLineEndings(text))
        for (edit in edits) {
            sb.replace(edit.offset, edit.offset + edit.length, edit.replacement)
        }
        return sb.toString()
    }

    // ── 1. Edit existing description ─────────────────────────────

    @Test
    fun `edit existing description - only description text changes`() {
        val doc = """
            |---
            |title: "Login test"
            |---
            |
            |This is the original description.
            |
            |Scenario:
            |
            |1. Do something
        """.trimMargin()

        val edits = DocumentPatcher.patch(doc, PatchOperation.SetDescription("This is the updated description."))

        val result = applyEdits(doc, edits)

        assertTrue(result.contains("This is the updated description."))
        assertFalse(result.contains("This is the original description."))
        assertTrue(result.contains("Scenario:\n"))
        assertTrue(result.contains("1. Do something"))
        assertTrue(result.contains("title: \"Login test\""))
    }

    // ── 2. Add description to document without one ───────────────

    @Test
    fun `add description to document without one`() {
        val doc = """
            |---
            |title: "No description"
            |---
            |
            |Scenario:
            |
            |1. Do something
        """.trimMargin()

        val edits = DocumentPatcher.patch(doc, PatchOperation.SetDescription("New description added."))

        val result = applyEdits(doc, edits)

        assertTrue(result.contains("New description added."))
        val descIdx = result.indexOf("New description added.")
        val stepsIdx = result.indexOf("Scenario:")
        val fmCloseIdx = result.lastIndexOf("---")
        assertTrue("Description should be after frontmatter", descIdx > fmCloseIdx)
        assertTrue("Description should be before steps", descIdx < stepsIdx)
        assertTrue(result.contains("1. Do something"))
    }

    @Test
    fun `add description does not duplicate blank line before next section`() {
        val doc = """
            |---
            |title: "Has links"
            |---
            |
            |Links:
            |
            |[example](https://example.com)
        """.trimMargin()

        val edits = DocumentPatcher.patch(doc, PatchOperation.SetDescription("13"))

        val result = applyEdits(doc, edits)

        assertFalse("must not contain triple newline before Links", result.contains("13\n\n\nLinks:"))
        assertTrue("description and Links must be separated by exactly one blank line", result.contains("13\n\nLinks:"))
    }

    @Test
    fun `add preconditions does not duplicate blank line before next section`() {
        val doc = """
            |---
            |title: "Has links"
            |---
            |
            |Links:
            |
            |[example](https://example.com)
        """.trimMargin()

        val edits = DocumentPatcher.patch(
            doc,
            PatchOperation.SetPreconditions(PreconditionsMarkerStyle.PRECONDITIONS, "1"),
        )

        val result = applyEdits(doc, edits)

        assertFalse("must not contain triple newline before Links", result.contains("1\n\n\nLinks:"))
        assertTrue("preconditions and Links must be separated by exactly one blank line", result.contains("1\n\nLinks:"))
    }

    // ── 3. Clear description (set to blank) ──────────────────────

    @Test
    fun `clear description - set to blank`() {
        val doc = """
            |---
            |title: "Has description"
            |---
            |
            |This description will be removed.
            |
            |Scenario:
            |
            |1. Do something
        """.trimMargin()

        val edits = DocumentPatcher.patch(doc, PatchOperation.SetDescription(""))

        val result = applyEdits(doc, edits)

        assertFalse(result.contains("This description will be removed."))
        assertTrue(result.contains("Scenario:\n"))
        assertTrue(result.contains("1. Do something"))
        assertTrue(result.contains("title: \"Has description\""))
    }

    // ── 4. Edit existing preconditions body ──────────────────────

    @Test
    fun `edit existing preconditions body - marker preserved, only body text changes`() {
        val doc = """
            |---
            |title: "Prec test"
            |---
            |
            |Preconditions:
            |
            |- Original precondition
            |
            |Scenario:
            |
            |1. Do something
        """.trimMargin()

        val edits = DocumentPatcher.patch(
            doc,
            PatchOperation.SetPreconditions(PreconditionsMarkerStyle.PRECONDITIONS, "- Updated precondition\n- Another one"),
        )

        val result = applyEdits(doc, edits)

        assertTrue(result.contains("Preconditions:\n"))
        assertTrue(result.contains("- Updated precondition"))
        assertTrue(result.contains("- Another one"))
        assertFalse(result.contains("- Original precondition"))
        assertTrue(result.contains("1. Do something"))
    }

    // ── 5. Add preconditions to document without them ────────────

    @Test
    fun `add preconditions to document without them`() {
        val doc = """
            |---
            |title: "No prec"
            |---
            |
            |Description text.
            |
            |Scenario:
            |
            |1. Do something
        """.trimMargin()

        val edits = DocumentPatcher.patch(
            doc,
            PatchOperation.SetPreconditions(PreconditionsMarkerStyle.PRECONDITIONS, "- New precondition"),
        )

        val result = applyEdits(doc, edits)

        assertTrue(result.contains("Preconditions:"))
        assertTrue(result.contains("- New precondition"))
        val descIdx = result.indexOf("Description text.")
        val precIdx = result.indexOf("Preconditions:")
        val stepsIdx = result.indexOf("Scenario:")
        assertTrue("Preconditions after description", precIdx > descIdx)
        assertTrue("Preconditions before steps", precIdx < stepsIdx)
        assertTrue(result.contains("Description text."))
    }

    // ── 6. Clear preconditions (set to blank) ────────────────────

    @Test
    fun `clear preconditions - marker and body removed`() {
        val doc = """
            |---
            |title: "Remove prec"
            |---
            |
            |Description text.
            |
            |Preconditions:
            |
            |- Will be removed
            |
            |Scenario:
            |
            |1. Do something
        """.trimMargin()

        val edits = DocumentPatcher.patch(
            doc,
            PatchOperation.SetPreconditions(PreconditionsMarkerStyle.PRECONDITIONS, ""),
        )

        val result = applyEdits(doc, edits)

        assertFalse(result.contains("Preconditions:"))
        assertFalse(result.contains("- Will be removed"))
        assertTrue(result.contains("Description text."))
        assertTrue(result.contains("Scenario:\n"))
        assertTrue(result.contains("1. Do something"))
    }

    // ── 7. Edit description with custom spacing (3 blank lines) ──

    @Test
    fun `edit description in document with custom spacing - spacing preserved`() {
        val doc = """
            |---
            |title: "Custom spacing"
            |---
            |
            |
            |
            |Original description.
            |
            |
            |
            |Scenario:
            |
            |1. Do something
        """.trimMargin()

        val edits = DocumentPatcher.patch(doc, PatchOperation.SetDescription("Updated description."))

        val result = applyEdits(doc, edits)

        assertTrue(result.contains("Updated description."))
        assertFalse(result.contains("Original description."))
        assertTrue(result.contains("Scenario:\n"))
        assertTrue(result.contains("1. Do something"))
        val fmCloseEnd = result.indexOf("---", result.indexOf("---") + 1) + 4
        val descStart = result.indexOf("Updated description.")
        val gap = result.substring(fmCloseEnd, descStart)
        assertEquals("\n\n\n", gap)
    }

    // ── 8. Add description when preconditions already exist ──────

    @Test
    fun `add description when preconditions already exist - description goes BEFORE preconditions`() {
        val doc = """
            |---
            |title: "Prec only"
            |---
            |
            |Preconditions:
            |
            |- Must be logged in
            |
            |Scenario:
            |
            |1. Do something
        """.trimMargin()

        val edits = DocumentPatcher.patch(doc, PatchOperation.SetDescription("Inserted description."))

        val result = applyEdits(doc, edits)

        assertTrue(result.contains("Inserted description."))
        val descIdx = result.indexOf("Inserted description.")
        val precIdx = result.indexOf("Preconditions:")
        assertTrue("Description should be before preconditions", descIdx < precIdx)
        assertTrue(result.contains("Preconditions:"))
        assertTrue(result.contains("- Must be logged in"))
        assertTrue(result.contains("1. Do something"))
    }
}
