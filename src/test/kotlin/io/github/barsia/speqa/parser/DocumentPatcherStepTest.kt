package io.github.barsia.speqa.parser

import io.github.barsia.speqa.model.TestStep
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentPatcherStepTest {

    private fun applyEdits(text: String, edits: List<DocumentEdit>): String {
        val sb = StringBuilder(SpeqaMarkdown.normalizeLineEndings(text))
        for (edit in edits) {
            sb.replace(edit.offset, edit.offset + edit.length, edit.replacement)
        }
        return sb.toString()
    }

    // ── 1. Edit step action (single line) ───────────────────────

    @Test
    fun `edit step action - single line`() {
        val doc = """
            |---
            |title: "Test"
            |---
            |
            |Scenario:
            |
            |1. Click the login button
            |   > User sees the dashboard
        """.trimMargin()

        val edits = DocumentPatcher.patch(doc, PatchOperation.SetStepAction(0, "Click the logout button"))

        val result = applyEdits(doc, edits)

        assertTrue(result.contains("1. Click the logout button"))
        assertFalse(result.contains("Click the login button"))
        assertTrue(result.contains("   > User sees the dashboard"))
    }

    // ── 2. Edit step action (multiline) ─────────────────────────

    @Test
    fun `edit step action - multiline with 3-space indent on continuation`() {
        val doc = """
            |---
            |title: "Test"
            |---
            |
            |Scenario:
            |
            |1. Click the button
            |   > Some expected result
        """.trimMargin()

        val edits = DocumentPatcher.patch(
            doc,
            PatchOperation.SetStepAction(0, "Fill in the form:\n- username: admin\n- password: secret"),
        )

        val result = applyEdits(doc, edits)

        assertTrue(result.contains("1. Fill in the form:\n"))
        assertTrue(result.contains("   - username: admin\n"))
        assertTrue(result.contains("   - password: secret\n"))
        assertTrue(result.contains("   > Some expected result"))
    }

    // ── 3. Add expected to step without one ─────────────────────

    @Test
    fun `add expected to step without one`() {
        val doc = "---\ntitle: \"Test\"\n---\n\nScenario:\n\n1. Click the button\n"

        val edits = DocumentPatcher.patch(doc, PatchOperation.SetStepExpected(0, "Button is highlighted"))

        val result = applyEdits(doc, edits)

        assertTrue(result.contains("1. Click the button\n"))
        assertTrue(result.contains("   > Button is highlighted\n"))
    }

    // ── 4. Edit existing expected ───────────────────────────────

    @Test
    fun `edit existing expected`() {
        val doc = """
            |---
            |title: "Test"
            |---
            |
            |Scenario:
            |
            |1. Click the button
            |   > Old expected text
        """.trimMargin()

        val edits = DocumentPatcher.patch(doc, PatchOperation.SetStepExpected(0, "New expected text"))

        val result = applyEdits(doc, edits)

        assertTrue(result.contains("   > New expected text"))
        assertFalse(result.contains("Old expected text"))
        assertTrue(result.contains("1. Click the button"))
    }

    // ── 5. Remove expected from step ────────────────────────────

    @Test
    fun `remove expected from step`() {
        val doc = """
            |---
            |title: "Test"
            |---
            |
            |Scenario:
            |
            |1. Click the button
            |   > Expected to be removed
        """.trimMargin()

        val edits = DocumentPatcher.patch(doc, PatchOperation.SetStepExpected(0, null))

        val result = applyEdits(doc, edits)

        assertTrue(result.contains("1. Click the button"))
        assertFalse(result.contains("> Expected to be removed"))
    }

    // ── 6. Add new step to end ──────────────────────────────────

    @Test
    fun `add new step to end of existing steps section`() {
        val doc = """
            |---
            |title: "Test"
            |---
            |
            |Scenario:
            |
            |1. First step
            |   > First expected
        """.trimMargin()

        val edits = DocumentPatcher.patch(
            doc,
            PatchOperation.AddStep(TestStep(action = "Second step", expected = "Second expected")),
        )

        val result = applyEdits(doc, edits)

        assertTrue(result.contains("1. First step"))
        assertTrue(result.contains("2. Second step\n"))
        assertTrue(result.contains("   > Second expected\n"))
    }

    // ── 7. Add step when no steps section exists ────────────────

    @Test
    fun `add step when no steps section exists`() {
        val doc = """
            |---
            |title: "Test"
            |---
            |
            |Some description.
        """.trimMargin()

        val edits = DocumentPatcher.patch(
            doc,
            PatchOperation.AddStep(TestStep(action = "Do something", expected = "Something happens")),
        )

        val result = applyEdits(doc, edits)

        assertTrue(result.contains("Scenario:\n"))
        assertTrue(result.contains("1. Do something\n"))
        assertTrue(result.contains("   > Something happens\n"))
        assertTrue(result.contains("Some description."))
    }

    // ── 8. Delete middle step - verify renumbering ──────────────

    @Test
    fun `delete middle step - renumber subsequent steps`() {
        val doc = """
            |---
            |title: "Test"
            |---
            |
            |Scenario:
            |
            |1. First step
            |   > First expected
            |
            |2. Second step
            |   > Second expected
            |
            |3. Third step
            |   > Third expected
        """.trimMargin()

        val edits = DocumentPatcher.patch(doc, PatchOperation.DeleteStep(1))

        val result = applyEdits(doc, edits)

        assertTrue(result.contains("1. First step"))
        assertTrue(result.contains("   > First expected"))
        assertFalse(result.contains("Second step"))
        assertTrue(result.contains("2. Third step"))
        assertTrue(result.contains("   > Third expected"))
    }

    // ── 9. Delete first step - verify renumbering ───────────────

    @Test
    fun `delete first step - renumber subsequent steps`() {
        val doc = """
            |---
            |title: "Test"
            |---
            |
            |Scenario:
            |
            |1. First step
            |   > First expected
            |
            |2. Second step
            |   > Second expected
            |
            |3. Third step
            |   > Third expected
        """.trimMargin()

        val edits = DocumentPatcher.patch(doc, PatchOperation.DeleteStep(0))

        val result = applyEdits(doc, edits)

        assertFalse(result.contains("First step"))
        assertTrue(result.contains("1. Second step"))
        assertTrue(result.contains("2. Third step"))
    }

    // ── 10. Reorder steps - verify numbers update ───────────────

    @Test
    fun `reorder steps - swap first and third, verify numbers`() {
        val doc = """
            |---
            |title: "Test"
            |---
            |
            |Scenario:
            |
            |1. First step
            |   > First expected
            |
            |2. Second step
            |   > Second expected
            |
            |3. Third step
            |   > Third expected
        """.trimMargin()

        val edits = DocumentPatcher.patch(doc, PatchOperation.ReorderSteps(0, 2))

        val result = applyEdits(doc, edits)

        assertTrue(result.contains("1. Third step"))
        assertTrue(result.contains("2. Second step"))
        assertTrue(result.contains("3. First step"))
    }

    // ── 11. Edit step preserves surrounding blank lines ─────────

    @Test
    fun `edit step preserves surrounding blank lines`() {
        val doc = """
            |---
            |title: "Test"
            |---
            |
            |Scenario:
            |
            |1. First step
            |   > First expected
            |
            |2. Second step
            |   > Second expected
            |
            |3. Third step
            |   > Third expected
        """.trimMargin()

        val edits = DocumentPatcher.patch(doc, PatchOperation.SetStepAction(1, "Updated second step"))

        val result = applyEdits(doc, edits)

        assertTrue(result.contains("   > First expected\n\n2. Updated second step"))
        assertTrue(result.contains("   > Second expected\n\n3. Third step"))
    }

    // ── 12. Multiline expected (multiple > lines) ───────────────

    @Test
    fun `multiline expected produces multiple blockquote lines`() {
        val doc = "---\ntitle: \"Test\"\n---\n\nScenario:\n\n1. Click the button\n"

        val edits = DocumentPatcher.patch(doc, PatchOperation.SetStepExpected(0, "Line one\nLine two\nLine three"))

        val result = applyEdits(doc, edits)

        assertTrue(result.contains("   > Line one\n"))
        assertTrue(result.contains("   > Line two\n"))
        assertTrue(result.contains("   > Line three\n"))
    }
}
