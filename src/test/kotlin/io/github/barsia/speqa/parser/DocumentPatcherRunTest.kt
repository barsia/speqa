package io.github.barsia.speqa.parser

import io.github.barsia.speqa.model.Attachment
import io.github.barsia.speqa.model.Link
import io.github.barsia.speqa.model.RunResult
import io.github.barsia.speqa.model.StepVerdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentPatcherRunTest {

    private fun applyEdits(text: String, edits: List<DocumentEdit>): String {
        val sb = StringBuilder(SpeqaMarkdown.normalizeLineEndings(text))
        for (edit in edits.sortedByDescending { it.offset }) {
            sb.replace(edit.offset, edit.offset + edit.length, edit.replacement)
        }
        return sb.toString()
    }

    private fun runDoc(): String = """
        |---
        |title: "Smoke"
        |result: in_progress
        |environment:
        |  - "Chrome 120"
        |runner: "alice"
        |tags:
        |  - auth
        |---
        |
        |Scenario:
        |
        |1. Open login
        |   > Form is visible
        |
        |2. Submit credentials
        |   > Redirected to home
    """.trimMargin()

    @Test
    fun `SetRunVerdict replaces result scalar`() {
        val doc = runDoc()
        val edits = DocumentPatcher.patch(doc, PatchOperation.SetRunVerdict(RunResult.PASSED))
        val result = applyEdits(doc, edits)
        assertTrue(result.contains("result: passed"))
        assertFalse(result.contains("result: in_progress"))
    }

    @Test
    fun `SetRunVerdict with null removes result field`() {
        val doc = runDoc()
        val edits = DocumentPatcher.patch(doc, PatchOperation.SetRunVerdict(null))
        val result = applyEdits(doc, edits)
        assertFalse(result.contains("result:"))
    }

    @Test
    fun `SetRunner rewrites runner scalar with YAML quoting`() {
        val doc = runDoc()
        val edits = DocumentPatcher.patch(doc, PatchOperation.SetRunner("bob"))
        val result = applyEdits(doc, edits)
        assertTrue(result.contains("runner: bob") || result.contains("runner: \"bob\""))
        assertFalse(result.contains("alice"))
    }

    @Test
    fun `SetRunTags replaces tags list`() {
        val doc = runDoc()
        val edits = DocumentPatcher.patch(doc, PatchOperation.SetRunTags(listOf("smoke", "regression")))
        val result = applyEdits(doc, edits)
        assertTrue("got: $result", Regex("""-\s+"?smoke"?""").containsMatchIn(result))
        assertTrue("got: $result", Regex("""-\s+"?regression"?""").containsMatchIn(result))
        assertFalse("got: $result", result.contains("auth"))
    }

    @Test
    fun `SetRunEnvironment replaces environment list`() {
        val doc = runDoc()
        val edits = DocumentPatcher.patch(doc, PatchOperation.SetRunEnvironment(listOf("Firefox 121")))
        val result = applyEdits(doc, edits)
        assertTrue(result.contains("- Firefox 121") || result.contains("- \"Firefox 121\""))
        assertFalse(result.contains("Chrome 120"))
    }

    @Test
    fun `SetRunStepVerdict appends verdict line to first step`() {
        val doc = runDoc()
        val edits = DocumentPatcher.patch(doc, PatchOperation.SetRunStepVerdict(0, StepVerdict.PASSED))
        val result = applyEdits(doc, edits)
        // Step 1 now has a trailing verdict
        val firstStep = result.substringAfter("1. Open login").substringBefore("2. Submit credentials")
        assertTrue("expected verdict in first step, got: $firstStep", firstStep.contains("- passed"))
        // Step 2 should still lack its own verdict
        assertFalse(result.substringAfter("2. Submit credentials").contains("- passed"))
    }

    @Test
    fun `SetRunStepVerdict NONE removes existing verdict line`() {
        val doc = """
            |---
            |title: "Smoke"
            |runner: "alice"
            |---
            |
            |Scenario:
            |
            |1. Open login
            |   > Form is visible
            |   - passed
        """.trimMargin()
        val edits = DocumentPatcher.patch(doc, PatchOperation.SetRunStepVerdict(0, StepVerdict.NONE))
        val result = applyEdits(doc, edits)
        assertFalse(result.contains("- passed"))
        assertTrue(result.contains("1. Open login"))
    }

    @Test
    fun `SetRunStepVerdict rewrites existing verdict line`() {
        val doc = """
            |---
            |title: "Smoke"
            |runner: "alice"
            |---
            |
            |Scenario:
            |
            |1. Open login
            |   > Form is visible
            |   - passed
        """.trimMargin()
        val edits = DocumentPatcher.patch(doc, PatchOperation.SetRunStepVerdict(0, StepVerdict.FAILED))
        val result = applyEdits(doc, edits)
        assertTrue(result.contains("- failed"))
        assertFalse(result.contains("- passed"))
    }

    @Test
    fun `SetRunStepComment inserts a Comment block when absent`() {
        val doc = runDoc()
        val edits = DocumentPatcher.patch(doc, PatchOperation.SetRunStepComment(0, "Looks good"))
        val result = applyEdits(doc, edits)
        assertTrue(result.contains("Comment:"))
        assertTrue(result.contains("Looks good"))
    }

    @Test
    fun `SetRunStepComment with blank removes existing Comment block`() {
        val doc = """
            |---
            |title: "Smoke"
            |runner: "alice"
            |---
            |
            |Scenario:
            |
            |1. Open login
            |   > Form is visible
            |
            |   Comment:
            |   Looks good
        """.trimMargin()
        val edits = DocumentPatcher.patch(doc, PatchOperation.SetRunStepComment(0, ""))
        val result = applyEdits(doc, edits)
        assertFalse(result.contains("Comment:"))
        assertFalse(result.contains("Looks good"))
        assertTrue(result.contains("1. Open login"))
    }

    @Test
    fun `SetRunLinks reuses the links section edit path`() {
        val doc = runDoc()
        val links = listOf(Link(title = "Spec", url = "https://example.com"))
        val edits = DocumentPatcher.patch(doc, PatchOperation.SetRunLinks(links))
        val result = applyEdits(doc, edits)
        assertTrue(result.contains("Links:"))
        assertTrue(result.contains("[Spec](https://example.com)"))
    }

    @Test
    fun `SetRunAttachments reuses the attachments section edit path`() {
        val doc = runDoc()
        val atts = listOf(Attachment(path = "attachments/log.txt"))
        val edits = DocumentPatcher.patch(doc, PatchOperation.SetRunAttachments(atts))
        val result = applyEdits(doc, edits)
        assertTrue(result.contains("Attachments:"))
        assertTrue(result.contains("log.txt"))
    }
}
