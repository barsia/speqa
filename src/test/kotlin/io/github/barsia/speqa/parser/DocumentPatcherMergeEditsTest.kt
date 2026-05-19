package io.github.barsia.speqa.parser

import org.junit.Assert.assertEquals
import org.junit.Test

class DocumentPatcherMergeEditsTest {

    private fun applySequentially(text: String, edits: List<DocumentEdit>): String {
        val sb = StringBuilder(text)
        for (edit in edits.sortedByDescending { it.offset }) {
            sb.replace(edit.offset, edit.offset + edit.length, edit.replacement)
        }
        return sb.toString()
    }

    @Test
    fun `mergeEdits applied over minimal span matches sequential application of three edits`() {
        val original = "Hello, brave new world! Foo bar baz."
        val edits = listOf(
            DocumentEdit(offset = 7, length = 5, replacement = "kind"),
            DocumentEdit(offset = 17, length = 5, replacement = "earth"),
            DocumentEdit(offset = 27, length = 3, replacement = "qux"),
        )
        val sequential = applySequentially(original, edits)

        val sorted = edits.sortedBy { it.offset }
        val start = sorted.first().offset
        val end = sorted.last().offset + sorted.last().length
        val merged = DocumentPatcher.mergeEdits(original.substring(start, end), start, sorted)

        val result = original.substring(0, start) + merged + original.substring(end)
        assertEquals(sequential, result)
    }

    @Test
    fun `mergeEdits handles pure insertion at start of span`() {
        val original = "abcdefghij"
        val edits = listOf(
            DocumentEdit(offset = 2, length = 0, replacement = "X"),
            DocumentEdit(offset = 5, length = 1, replacement = "YY"),
        )
        val sorted = edits.sortedBy { it.offset }
        val start = sorted.first().offset
        val end = sorted.last().offset + sorted.last().length
        val merged = DocumentPatcher.mergeEdits(original.substring(start, end), start, sorted)
        val result = original.substring(0, start) + merged + original.substring(end)
        assertEquals(applySequentially(original, edits), result)
    }

    @Test
    fun `mergeEdits handles adjacent edits`() {
        val original = "0123456789"
        val edits = listOf(
            DocumentEdit(offset = 2, length = 2, replacement = "AA"),
            DocumentEdit(offset = 4, length = 2, replacement = "BB"),
        )
        val sorted = edits.sortedBy { it.offset }
        val merged = DocumentPatcher.mergeEdits(
            original.substring(sorted.first().offset, sorted.last().offset + sorted.last().length),
            sorted.first().offset,
            sorted,
        )
        assertEquals("AABB", merged)
    }
}
