package io.github.barsia.speqa.parser

import io.github.barsia.speqa.model.PreconditionsMarkerStyle
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Editing the description in the preview can hand back text with a trailing blank line
 * (e.g. after deleting the last paragraph with a triple-click selection). Writing that
 * back must not leak extra blank lines into the `.tc.md` source before the next block.
 */
class DescriptionTrailingBlankTest {

    private fun applyEdits(text: String, edits: List<DocumentEdit>): String {
        val sb = StringBuilder(SpeqaMarkdown.normalizeLineEndings(text))
        for (edit in edits.sortedByDescending { it.offset }) {
            sb.replace(edit.offset, edit.offset + edit.length, edit.replacement)
        }
        return sb.toString()
    }

    @Test
    fun `SetDescription drops trailing blank lines so one blank separates it from Preconditions`() {
        val doc = """
            |---
            |id: 1
            |---
            |
            |para one
            |para two
            |
            |Preconditions:
            |
            |1. ready
        """.trimMargin()

        val edits = DocumentPatcher.patch(doc, PatchOperation.SetDescription("para one\n\n"))

        assertEquals(
            """
                |---
                |id: 1
                |---
                |
                |para one
                |
                |Preconditions:
                |
                |1. ready
            """.trimMargin(),
            applyEdits(doc, edits),
        )
    }

    @Test
    fun `SetPreconditions drops trailing blank lines so one blank separates it from Scenario`() {
        val doc = """
            |---
            |id: 1
            |---
            |
            |Preconditions:
            |
            |1. one
            |2. two
            |
            |Scenario:
            |
            |1. act
            |   > ok
        """.trimMargin()

        val edits = DocumentPatcher.patch(
            doc,
            PatchOperation.SetPreconditions(PreconditionsMarkerStyle.PRECONDITIONS, "1. one\n\n"),
        )

        assertEquals(
            """
                |---
                |id: 1
                |---
                |
                |Preconditions:
                |
                |1. one
                |
                |Scenario:
                |
                |1. act
                |   > ok
            """.trimMargin(),
            applyEdits(doc, edits),
        )
    }
}
