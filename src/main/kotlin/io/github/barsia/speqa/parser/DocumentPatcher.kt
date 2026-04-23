package io.github.barsia.speqa.parser

import io.github.barsia.speqa.model.Attachment
import io.github.barsia.speqa.model.Link
import io.github.barsia.speqa.model.PreconditionsMarkerStyle
import io.github.barsia.speqa.model.RunResult
import io.github.barsia.speqa.model.StepVerdict
import io.github.barsia.speqa.model.TestStep

/**
 * A single text edit: replace [length] characters starting at [offset] with [replacement].
 * When [length] == 0 this is a pure insertion.
 */
data class DocumentEdit(
    val offset: Int,
    val length: Int,
    val replacement: String,
)

/**
 * High-level patch operations on a `.tc.md` document.
 */
sealed interface PatchOperation {
    data class SetFrontmatterField(val key: String, val value: String?) : PatchOperation
    data class SetFrontmatterList(val key: String, val values: List<String>?) : PatchOperation
    data class SetDescription(val markdown: String) : PatchOperation
    data class SetPreconditions(val markerStyle: PreconditionsMarkerStyle, val markdown: String) : PatchOperation
    data class SetStepAction(val stepIndex: Int, val action: String) : PatchOperation
    data class SetStepExpected(val stepIndex: Int, val expected: String?) : PatchOperation
    data class AddStep(val step: TestStep) : PatchOperation
    data class DeleteStep(val stepIndex: Int) : PatchOperation
    data class ReorderSteps(val fromIndex: Int, val toIndex: Int) : PatchOperation
    data class SetAttachments(val attachments: List<Attachment>) : PatchOperation
    data class SetStepAttachments(val stepIndex: Int, val attachments: List<Attachment>) : PatchOperation
    data class SetLinks(val links: List<Link>) : PatchOperation
    data class SetStepTickets(val stepIndex: Int, val tickets: List<String>) : PatchOperation
    data class SetStepLinks(val stepIndex: Int, val links: List<Link>) : PatchOperation

    // Run-side frontmatter ops
    data class SetRunVerdict(val verdict: RunResult?) : PatchOperation
    data class SetRunner(val name: String) : PatchOperation
    data class SetRunTags(val tags: List<String>) : PatchOperation
    data class SetRunEnvironment(val environment: List<String>) : PatchOperation

    // Run-side step-body ops
    data class SetRunStepVerdict(val stepIndex: Int, val verdict: StepVerdict) : PatchOperation
    data class SetRunStepComment(val stepIndex: Int, val comment: String) : PatchOperation

    // Run-side top-level links/attachments reuse the test-case edit paths; separate
    // variants exist so callers stay symmetric with the TC-side naming.
    data class SetRunLinks(val links: List<Link>) : PatchOperation
    data class SetRunAttachments(val attachments: List<Attachment>) : PatchOperation
}

/**
 * Converts a [PatchOperation] into a list of [DocumentEdit] values.
 *
 * Edits are returned in **descending offset order** so they can be applied
 * sequentially without earlier edits shifting later offsets.
 */
object DocumentPatcher {

    /**
     * Canonical field order in frontmatter. Used to determine insertion position
     * when adding a new field.
     */
    private val FIELD_ORDER = listOf(
        "id", "title", "priority", "status",
        "started_at", "finished_at",
        "result", "manual_result",
        "environment", "runner", "tags",
    )

    fun patch(text: String, operation: PatchOperation): List<DocumentEdit> {
        val normalized = SpeqaMarkdown.normalizeLineEndings(text)
        val layout = DocumentRangeLocator.locate(normalized)
        return when (operation) {
            is PatchOperation.SetFrontmatterField -> patchFrontmatterField(layout, operation)
            is PatchOperation.SetFrontmatterList -> patchFrontmatterList(layout, operation)
            is PatchOperation.SetDescription -> buildDescriptionEdits(normalized, layout, operation.markdown)
            is PatchOperation.SetPreconditions -> buildPreconditionsEdits(
                normalized, layout, operation.markerStyle.marker, operation.markdown,
            )
            is PatchOperation.SetStepAction -> buildSetStepActionEdits(layout, operation.stepIndex, operation.action)
            is PatchOperation.SetStepExpected -> buildSetStepExpectedEdits(
                layout, operation.stepIndex, operation.expected,
            )
            is PatchOperation.AddStep -> buildAddStepEdits(normalized, layout, operation.step)
            is PatchOperation.DeleteStep -> buildDeleteStepEdits(normalized, layout, operation.stepIndex)
            is PatchOperation.ReorderSteps -> buildReorderStepsEdits(
                normalized, layout, operation.fromIndex, operation.toIndex,
            )
            is PatchOperation.SetAttachments -> buildSetAttachmentsEdits(
                normalized, layout, operation.attachments,
            )
            is PatchOperation.SetStepAttachments -> buildSetStepAttachmentsEdits(
                layout, operation.stepIndex, operation.attachments,
            )
            is PatchOperation.SetLinks -> buildSetLinksEdits(
                normalized, layout, operation.links,
            )
            is PatchOperation.SetStepTickets -> buildSetStepTicketEdits(
                layout, operation.stepIndex, operation.tickets,
            )
            is PatchOperation.SetStepLinks -> buildSetStepLinksEdits(
                layout, operation.stepIndex, operation.links,
            )
            is PatchOperation.SetRunVerdict -> patchFrontmatterField(
                layout,
                PatchOperation.SetFrontmatterField("result", operation.verdict?.label),
            )
            is PatchOperation.SetRunner -> patchFrontmatterField(
                layout,
                PatchOperation.SetFrontmatterField("runner", operation.name),
            )
            is PatchOperation.SetRunTags -> patchFrontmatterList(
                layout,
                PatchOperation.SetFrontmatterList("tags", operation.tags),
            )
            is PatchOperation.SetRunEnvironment -> patchFrontmatterList(
                layout,
                PatchOperation.SetFrontmatterList("environment", operation.environment),
            )
            is PatchOperation.SetRunStepVerdict -> buildSetRunStepVerdictEdits(
                normalized, layout, operation.stepIndex, operation.verdict,
            )
            is PatchOperation.SetRunStepComment -> buildSetRunStepCommentEdits(
                normalized, layout, operation.stepIndex, operation.comment,
            )
            is PatchOperation.SetRunLinks -> buildSetLinksEdits(
                normalized, layout, operation.links,
            )
            is PatchOperation.SetRunAttachments -> buildSetAttachmentsEdits(
                normalized, layout, operation.attachments,
            )
        }
    }

    // ── Frontmatter Field (scalar) ──────────────────────────────

    private fun patchFrontmatterField(
        layout: DocumentLayout,
        op: PatchOperation.SetFrontmatterField,
    ): List<DocumentEdit> {
        val fm = layout.frontmatter ?: return emptyList()
        val existing = fm.fields.find { it.key == op.key }

        return if (existing != null) {
            if (op.value != null) {
                val formatted = formatScalarField(op.key, op.value)
                listOf(DocumentEdit(existing.wholeRange.start, existing.wholeRange.length, formatted))
            } else {
                listOf(DocumentEdit(existing.wholeRange.start, existing.wholeRange.length, ""))
            }
        } else {
            if (op.value != null) {
                val insertOffset = findInsertionOffset(fm, op.key)
                val formatted = formatScalarField(op.key, op.value)
                listOf(DocumentEdit(insertOffset, 0, formatted))
            } else {
                emptyList()
            }
        }
    }

    // ── Frontmatter List ────────────────────────────────────────

    private fun patchFrontmatterList(
        layout: DocumentLayout,
        op: PatchOperation.SetFrontmatterList,
    ): List<DocumentEdit> {
        val fm = layout.frontmatter ?: return emptyList()
        val existing = fm.fields.find { it.key == op.key }

        return if (existing != null) {
            if (op.values != null) {
                val formatted = formatListField(op.key, op.values)
                listOf(DocumentEdit(existing.wholeRange.start, existing.wholeRange.length, formatted))
            } else {
                listOf(DocumentEdit(existing.wholeRange.start, existing.wholeRange.length, ""))
            }
        } else {
            if (op.values != null) {
                val insertOffset = findInsertionOffset(fm, op.key)
                val formatted = formatListField(op.key, op.values)
                listOf(DocumentEdit(insertOffset, 0, formatted))
            } else {
                emptyList()
            }
        }
    }

    // ── Frontmatter formatting helpers ──────────────────────────

    private fun formatScalarField(key: String, value: String): String {
        val formattedValue = if (key in QUOTED_SCALAR_KEYS) {
            SpeqaMarkdown.quoteYamlScalar(value)
        } else {
            value
        }
        return "$key: $formattedValue\n"
    }

    private val QUOTED_SCALAR_KEYS = setOf("title", "runner", "started_at", "finished_at")

    private fun formatListField(key: String, values: List<String>): String {
        if (values.isEmpty()) {
            return "$key: []\n"
        }
        return buildString {
            append(key)
            append(":\n")
            for (v in values) {
                append("  - ")
                append(SpeqaMarkdown.quoteYamlScalar(v))
                append('\n')
            }
        }
    }

    /**
     * Finds the offset where a new field with [key] should be inserted,
     * respecting [FIELD_ORDER]. The field is inserted before the first
     * existing field that comes after it in canonical order. If no such
     * field exists, it is inserted before the close delimiter.
     */
    private fun findInsertionOffset(fm: FrontmatterLayout, key: String): Int {
        val keyIndex = FIELD_ORDER.indexOf(key)
        if (keyIndex >= 0) {
            for (i in (keyIndex + 1) until FIELD_ORDER.size) {
                val found = fm.fields.find { it.key == FIELD_ORDER[i] }
                if (found != null) {
                    return found.wholeRange.start
                }
            }
        }
        return fm.closeDelimiter.start
    }

    // ── Description ──────────────────────────────────────────────

    private fun buildDescriptionEdits(
        text: String,
        layout: DocumentLayout,
        markdown: String,
    ): List<DocumentEdit> {
        val descRange = layout.descriptionRange

        if (descRange != null && markdown.isNotBlank()) {
            // Replace existing description content
            return listOf(
                DocumentEdit(
                    offset = descRange.start,
                    length = descRange.length,
                    replacement = ensureTrailingNewline(markdown),
                )
            )
        }

        if (descRange != null && markdown.isBlank()) {
            // Delete description and surrounding blank lines
            val deleteStart = findBlankLinesBefore(text, descRange.start)
            val deleteEnd = findBlankLinesAfter(text, descRange.end)
            return listOf(
                DocumentEdit(
                    offset = deleteStart,
                    length = deleteEnd - deleteStart,
                    replacement = sectionDeleteReplacement(text, deleteStart, deleteEnd),
                )
            )
        }

        if (descRange == null && markdown.isNotBlank()) {
            // Insert description after frontmatter close delimiter (before preconditions/steps)
            val insertOffset = findDescriptionInsertOffset(text, layout)
            return listOf(
                DocumentEdit(
                    offset = insertOffset,
                    length = 0,
                    replacement = "\n" + ensureTrailingNewline(markdown) + "\n",
                )
            )
        }

        // No description, blank markdown → no-op
        return emptyList()
    }

    // ── Preconditions ────────────────────────────────────────────

    private fun buildPreconditionsEdits(
        text: String,
        layout: DocumentLayout,
        markerStyle: String,
        markdown: String,
    ): List<DocumentEdit> {
        val markerRange = layout.preconditionsMarkerRange
        val bodyRange = layout.preconditionsBodyRange

        if (markerRange != null && bodyRange != null && markdown.isNotBlank()) {
            // Replace preconditions body only — preserve marker
            return listOf(
                DocumentEdit(
                    offset = bodyRange.start,
                    length = bodyRange.length,
                    replacement = ensureTrailingNewline(markdown),
                )
            )
        }

        if (markerRange != null && markdown.isBlank()) {
            // Delete marker + body + surrounding blank lines
            val sectionStart = markerRange.start
            val sectionEnd = if (bodyRange != null) bodyRange.end else markerRange.end
            val deleteStart = findBlankLinesBefore(text, sectionStart)
            val deleteEnd = findBlankLinesAfter(text, sectionEnd)
            return listOf(
                DocumentEdit(
                    offset = deleteStart,
                    length = deleteEnd - deleteStart,
                    replacement = sectionDeleteReplacement(text, deleteStart, deleteEnd),
                )
            )
        }

        if (markerRange == null && markdown.isNotBlank()) {
            // Insert preconditions before steps/attachments
            val insertOffset = findPreconditionsInsertOffset(text, layout)
            val marker = markerStyle.ifBlank { "Preconditions:" }
            return listOf(
                DocumentEdit(
                    offset = insertOffset,
                    length = 0,
                    replacement = "\n" + marker + "\n\n" + ensureTrailingNewline(markdown) + "\n",
                )
            )
        }

        // No preconditions, blank markdown → no-op
        return emptyList()
    }

    // ── Helpers ──────────────────────────────────────────────────

    /**
     * Returns the offset where a new description should be inserted.
     * This is right after the frontmatter close delimiter line (its end offset),
     * or at the start of the first section marker, or at the document start.
     */
    private fun findDescriptionInsertOffset(text: String, layout: DocumentLayout): Int {
        if (layout.frontmatter != null) {
            return layout.frontmatter.closeDelimiter.end
        }
        val firstSection = listOfNotNull(
            layout.preconditionsMarkerRange,
            layout.attachmentsMarkerRange,
            layout.linksMarkerRange,
            layout.stepsMarkerRange,
        ).minByOrNull { it.start }
        if (firstSection != null) {
            return findBlankLinesBefore(text, firstSection.start)
        }
        return text.length
    }

    /**
     * Returns the offset where preconditions should be inserted.
     * This is after the description (if any), or after frontmatter,
     * and before steps/attachments.
     */
    private fun findPreconditionsInsertOffset(text: String, layout: DocumentLayout): Int {
        if (layout.descriptionRange != null) {
            return layout.descriptionRange.end
        }
        if (layout.frontmatter != null) {
            return layout.frontmatter.closeDelimiter.end
        }
        val firstSection = listOfNotNull(
            layout.attachmentsMarkerRange,
            layout.linksMarkerRange,
            layout.stepsMarkerRange,
        ).minByOrNull { it.start }
        if (firstSection != null) {
            return findBlankLinesBefore(text, firstSection.start)
        }
        return text.length
    }

    /**
     * Walk backwards from [offset] over blank lines (lines containing only whitespace)
     * to find the start of the blank region. Returns the start offset of the first
     * blank line found, or [offset] if the preceding line is not blank.
     */
    private fun findBlankLinesBefore(text: String, offset: Int): Int {
        var pos = offset
        while (pos > 0) {
            // pos points to the start of the content region or just after a \n
            // Check if the character before pos is \n (end of preceding line)
            if (text[pos - 1] != '\n') break

            // Find the start of the preceding line
            val prevLineEnd = pos - 1 // the \n character
            var prevLineStart = prevLineEnd
            while (prevLineStart > 0 && text[prevLineStart - 1] != '\n') {
                prevLineStart--
            }
            // Check if line [prevLineStart, prevLineEnd) is blank
            val lineContent = text.substring(prevLineStart, prevLineEnd)
            if (lineContent.isNotBlank()) break
            pos = prevLineStart
        }
        return pos
    }

    /**
     * Walk forward from [offset] over blank lines to find the end of the blank region.
     * Returns the end offset (exclusive) after consuming trailing blank lines,
     * or [offset] if the next line is not blank.
     */
    private fun findBlankLinesAfter(text: String, offset: Int): Int {
        var pos = offset
        while (pos < text.length) {
            // Find the end of the current line
            val lineEnd = text.indexOf('\n', pos)
            if (lineEnd == -1) {
                // Last line without newline
                val lineContent = text.substring(pos)
                return if (lineContent.isBlank()) text.length else pos
            }
            val lineContent = text.substring(pos, lineEnd)
            if (lineContent.isNotBlank()) break
            pos = lineEnd + 1
        }
        return pos
    }

    /**
     * When deleting a section, determine whether to leave a blank line
     * between the content before and after the deletion.
     */
    private fun sectionDeleteReplacement(text: String, deleteStart: Int, deleteEnd: Int): String {
        val hasBefore = deleteStart > 0 && text.substring(0, deleteStart).any { it != '\n' && it != ' ' }
        val hasAfter = deleteEnd < text.length && text.substring(deleteEnd).any { it != '\n' && it != ' ' }
        return if (hasBefore && hasAfter) "\n" else ""
    }

    // ── Step: SetStepAction ───────────────────────────────────────

    private fun buildSetStepActionEdits(
        layout: DocumentLayout,
        stepIndex: Int,
        action: String,
    ): List<DocumentEdit> {
        val step = layout.steps[stepIndex]
        val formatted = formatActionText(action)
        return listOf(
            DocumentEdit(
                offset = step.actionRange.start,
                length = step.actionRange.length,
                replacement = formatted,
            )
        )
    }

    /**
     * Formats action text: first line as-is followed by newline,
     * continuation lines indented with 3 spaces (matching `N. ` prefix width).
     */
    private fun formatActionText(action: String): String {
        val lines = action.trimEnd('\n').lines()
        return buildString {
            append(lines.first())
            append("\n")
            for (i in 1 until lines.size) {
                append("   ")
                append(lines[i])
                append("\n")
            }
        }
    }

    // ── Step: SetStepExpected ───────────────────────────────────

    private fun buildSetStepExpectedEdits(
        layout: DocumentLayout,
        stepIndex: Int,
        expected: String?,
    ): List<DocumentEdit> {
        val step = layout.steps[stepIndex]
        val existingRange = step.expectedRange

        if (existingRange != null && expected != null) {
            return listOf(
                DocumentEdit(
                    offset = existingRange.start,
                    length = existingRange.length,
                    replacement = formatExpectedText(expected),
                )
            )
        }

        if (existingRange != null && expected == null) {
            return listOf(
                DocumentEdit(
                    offset = existingRange.start,
                    length = existingRange.length,
                    replacement = "",
                )
            )
        }

        if (expected != null) {
            val insertOffset = step.actionRange.end
            return listOf(
                DocumentEdit(
                    offset = insertOffset,
                    length = 0,
                    replacement = formatExpectedText(expected),
                )
            )
        }

        return emptyList()
    }

    /**
     * Formats expected text as blockquote lines: `   > line\n`.
     */
    private fun formatExpectedText(expected: String): String {
        return buildString {
            if (expected.isEmpty()) {
                append("   >\n")
            } else {
                expected.lines().forEach { line ->
                    append("   > ")
                    append(line)
                    append("\n")
                }
            }
        }
    }

    // ── Step: SetStepTicket ──────────────────────────────────────

    private fun buildSetStepTicketEdits(
        layout: DocumentLayout,
        stepIndex: Int,
        tickets: List<String>,
    ): List<DocumentEdit> {
        val step = layout.steps[stepIndex]
        val existingRange = step.ticketRange
        val rendered = tickets.filter { it.isNotBlank() }.joinToString(", ")

        if (existingRange != null && rendered.isNotBlank()) {
            return listOf(
                DocumentEdit(
                    offset = existingRange.start,
                    length = existingRange.length,
                    replacement = "   Ticket: $rendered\n",
                )
            )
        }

        if (existingRange != null && rendered.isBlank()) {
            val removeStart = if (existingRange.start > 0) existingRange.start - 1 else existingRange.start
            return listOf(
                DocumentEdit(
                    offset = removeStart,
                    length = existingRange.end - removeStart,
                    replacement = "",
                )
            )
        }

        if (rendered.isNotBlank()) {
            val insertOffset = step.attachmentsRange?.end
                ?: step.expectedRange?.end
                ?: step.actionRange.end
            return listOf(
                DocumentEdit(
                    offset = insertOffset,
                    length = 0,
                    replacement = "\n   Ticket: $rendered\n",
                )
            )
        }

        return emptyList()
    }

    // ── Step: SetStepLinks ──────────────────────────────────────

    private fun buildSetStepLinksEdits(
        layout: DocumentLayout,
        stepIndex: Int,
        links: List<Link>,
    ): List<DocumentEdit> {
        val step = layout.steps[stepIndex]
        val existingRange = step.linksRange

        if (existingRange != null && links.isNotEmpty()) {
            val rendered = formatStepLinksLine(links)
            return listOf(
                DocumentEdit(
                    offset = existingRange.start,
                    length = existingRange.length,
                    replacement = "   Links: $rendered\n",
                )
            )
        }

        if (existingRange != null && links.isEmpty()) {
            val removeStart = if (existingRange.start > 0) existingRange.start - 1 else existingRange.start
            return listOf(
                DocumentEdit(
                    offset = removeStart,
                    length = existingRange.end - removeStart,
                    replacement = "",
                )
            )
        }

        if (links.isNotEmpty()) {
            val insertOffset = step.ticketRange?.end
                ?: step.attachmentsRange?.end
                ?: step.expectedRange?.end
                ?: step.actionRange.end
            return listOf(
                DocumentEdit(
                    offset = insertOffset,
                    length = 0,
                    replacement = "   Links: ${formatStepLinksLine(links)}\n",
                )
            )
        }

        return emptyList()
    }

    private fun formatStepLinksLine(links: List<Link>): String {
        return links.joinToString(", ") { link ->
            if (link.title.isBlank()) link.url else "[${link.title}](${link.url})"
        }
    }

    // ── Step: AddStep ───────────────────────────────────────────

    private fun buildAddStepEdits(
        text: String,
        layout: DocumentLayout,
        step: TestStep,
    ): List<DocumentEdit> {
        val number = layout.steps.size + 1
        val stepText = formatStepText(number, step)

        if (layout.stepsMarkerRange != null) {
            val insertOffset = if (layout.steps.isNotEmpty()) {
                layout.steps.last().wholeRange.end
            } else {
                layout.stepsMarkerRange.end
            }
            return listOf(
                DocumentEdit(
                    offset = insertOffset,
                    length = 0,
                    replacement = "\n" + stepText,
                )
            )
        }

        // No scenario section — insert Scenario: marker + step before EOF
        val insertOffset = text.length
        val prefix = if (text.isNotEmpty() && !text.endsWith("\n\n")) {
            if (text.endsWith("\n")) "\n" else "\n\n"
        } else {
            ""
        }
        return listOf(
            DocumentEdit(
                offset = insertOffset,
                length = 0,
                replacement = prefix + "Scenario:\n\n" + stepText,
            )
        )
    }

    /**
     * Formats a complete step entry: `N. action\n   > expected\n`
     */
    private fun formatStepText(number: Int, step: TestStep): String {
        return buildString {
            val actionLines = step.action.lines()
            append("$number. ${actionLines.firstOrNull().orEmpty()}\n")
            actionLines.drop(1).forEach { line ->
                append("   $line\n")
            }
            step.expected?.let { exp ->
                if (exp.isEmpty()) {
                    append("   >\n")
                } else {
                    exp.lines().forEach { line -> append("   > $line\n") }
                }
            }
        }
    }

    // ── Step: DeleteStep ────────────────────────────────────────

    private fun buildDeleteStepEdits(
        text: String,
        layout: DocumentLayout,
        stepIndex: Int,
    ): List<DocumentEdit> {
        val edits = mutableListOf<DocumentEdit>()
        val step = layout.steps[stepIndex]

        // Delete the step range plus blank lines before AND after
        val deleteStart = findBlankLinesBefore(text, step.wholeRange.start)
        val deleteEnd = findBlankLinesAfter(text, step.wholeRange.end)

        edits += DocumentEdit(
            offset = deleteStart,
            length = deleteEnd - deleteStart,
            replacement = sectionDeleteReplacement(text, deleteStart, deleteEnd),
        )

        // Renumber subsequent steps: step at index i (0-based) should display as (i)
        // because after deletion, step at original index (stepIndex+1) becomes stepIndex
        for (i in (stepIndex + 1) until layout.steps.size) {
            val s = layout.steps[i]
            val newNumber = i.toString() // was (i+1), now shifted down by 1
            edits += DocumentEdit(
                offset = s.numberRange.start,
                length = s.numberRange.length,
                replacement = newNumber,
            )
        }

        return edits.sortedByDescending { it.offset }
    }

    // ── Step: ReorderSteps ──────────────────────────────────────

    private fun buildReorderStepsEdits(
        text: String,
        layout: DocumentLayout,
        fromIndex: Int,
        toIndex: Int,
    ): List<DocumentEdit> {
        if (fromIndex == toIndex) return emptyList()
        if (fromIndex !in layout.steps.indices || toIndex !in layout.steps.indices) return emptyList()

        val startIndex = minOf(fromIndex, toIndex)
        val endIndex = maxOf(fromIndex, toIndex)
        val affectedSteps = layout.steps.subList(startIndex, endIndex + 1)
        val replacementStart = affectedSteps.first().wholeRange.start
        val replacementEnd = affectedSteps.last().wholeRange.end

        val reordered = (startIndex..endIndex).toMutableList().also { indices ->
            val moved = indices.removeAt(fromIndex - startIndex)
            indices.add(toIndex - startIndex, moved)
        }

        val separators = (startIndex until endIndex).map { index ->
            text.substring(layout.steps[index].wholeRange.end, layout.steps[index + 1].wholeRange.start)
        }

        val replacement = buildString {
            reordered.forEachIndexed { offset, originalIndex ->
                val stepLayout = layout.steps[originalIndex]
                val stepText = text.substring(stepLayout.wholeRange.start, stepLayout.wholeRange.end)
                append(renumberStepText(stepText, startIndex + offset + 1))
                if (offset < separators.size) {
                    append(separators[offset])
                }
            }
        }

        return listOf(
            DocumentEdit(
                offset = replacementStart,
                length = replacementEnd - replacementStart,
                replacement = replacement,
            )
        )
    }

    /**
     * Replace the leading step number in a step text block with [newNumber].
     */
    private fun renumberStepText(stepText: String, newNumber: Int): String {
        val pattern = Regex("""^(\s*)(\d+)\.""")
        val match = pattern.find(stepText) ?: return stepText
        val prefix = match.groupValues[1]
        return stepText.substring(0, match.range.first) +
                "$prefix$newNumber." +
                stepText.substring(match.range.last + 1)
    }

    // ── Attachments: Document-level ───────────────────────────

    private fun buildSetAttachmentsEdits(
        text: String,
        layout: DocumentLayout,
        attachments: List<Attachment>,
    ): List<DocumentEdit> {
        val markerRange = layout.attachmentsMarkerRange
        val bodyRange = layout.attachmentsBodyRange

        if (markerRange != null && attachments.isNotEmpty()) {
            // Section exists, replace body with new attachment lines
            val replacement = formatDocumentAttachments(attachments)
            if (bodyRange != null) {
                return listOf(
                    DocumentEdit(
                        offset = bodyRange.start,
                        length = bodyRange.length,
                        replacement = replacement,
                    )
                )
            }
            // Marker exists but no body — insert after marker
            return listOf(
                DocumentEdit(
                    offset = markerRange.end,
                    length = 0,
                    replacement = "\n" + replacement,
                )
            )
        }

        if (markerRange != null) {
            // Delete entire attachments section (marker + body + surrounding blank lines)
            val sectionStart = markerRange.start
            val sectionEnd = bodyRange?.end ?: markerRange.end
            val deleteStart = findBlankLinesBefore(text, sectionStart)
            val deleteEnd = findBlankLinesAfter(text, sectionEnd)
            return listOf(
                DocumentEdit(
                    offset = deleteStart,
                    length = deleteEnd - deleteStart,
                    replacement = sectionDeleteReplacement(text, deleteStart, deleteEnd),
                )
            )
        }

        if (attachments.isNotEmpty()) {
            // No section — insert before Links/Steps marker or at EOF
            val replacement = formatDocumentAttachments(attachments)
            val insertOffset = findAttachmentsInsertOffset(text, layout)
            return listOf(
                DocumentEdit(
                    offset = insertOffset,
                    length = 0,
                    replacement = "\nAttachments:\n\n" + replacement,
                )
            )
        }

        // No section + empty list → no-op
        return emptyList()
    }

    private val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "gif", "svg", "webp", "bmp", "ico")

    private fun isImagePath(path: String): Boolean {
        val ext = path.substringAfterLast('.', "").lowercase()
        return ext in IMAGE_EXTENSIONS
    }

    private fun formatAttachmentLine(att: Attachment, indent: String = ""): String {
        val fileName = att.path.substringAfterLast('/')
        val encodedPath = att.path.replace(" ", "%20")
        return if (isImagePath(att.path)) {
            "$indent![$fileName]($encodedPath)\n"
        } else {
            "$indent[$fileName]($encodedPath)\n"
        }
    }

    private fun formatDocumentAttachments(attachments: List<Attachment>): String {
        return buildString {
            for (att in attachments) {
                append(formatAttachmentLine(att))
            }
        }
    }

    private fun findAttachmentsInsertOffset(text: String, layout: DocumentLayout): Int {
        val targetStart = layout.linksMarkerRange?.start
            ?: layout.stepsMarkerRange?.start
        if (targetStart != null) {
            return findBlankLinesBefore(text, targetStart)
        }
        return text.length
    }

    // ── Attachments: Step ───────────────────────────────────────

    private fun buildSetStepAttachmentsEdits(
        layout: DocumentLayout,
        stepIndex: Int,
        attachments: List<Attachment>,
    ): List<DocumentEdit> {
        val step = layout.steps[stepIndex]
        val range = step.attachmentsRange

        if (range != null && attachments.isNotEmpty()) {
            return listOf(
                DocumentEdit(
                    offset = range.start,
                    length = range.length,
                    replacement = formatStepAttachments(attachments),
                )
            )
        }

        if (range != null) {
            return listOf(
                DocumentEdit(
                    offset = range.start,
                    length = range.length,
                    replacement = "",
                )
            )
        }

        if (attachments.isNotEmpty()) {
            val insertOffset = step.expectedRange?.end ?: step.actionRange.end
            return listOf(
                DocumentEdit(
                    offset = insertOffset,
                    length = 0,
                    replacement = formatStepAttachments(attachments),
                )
            )
        }

        return emptyList()
    }

    private fun formatStepAttachments(attachments: List<Attachment>): String {
        return buildString {
            for (att in attachments) {
                append(formatAttachmentLine(att, indent = "   "))
            }
        }
    }

    // ── Links: Document-level ────────────────────────────────

    private fun buildSetLinksEdits(
        text: String,
        layout: DocumentLayout,
        links: List<Link>,
    ): List<DocumentEdit> {
        val markerRange = layout.linksMarkerRange
        val bodyRange = layout.linksBodyRange

        if (markerRange != null && links.isNotEmpty()) {
            val replacement = formatDocumentLinks(links)
            if (bodyRange != null) {
                return listOf(
                    DocumentEdit(
                        offset = bodyRange.start,
                        length = bodyRange.length,
                        replacement = replacement,
                    )
                )
            }
            return listOf(
                DocumentEdit(
                    offset = markerRange.end,
                    length = 0,
                    replacement = "\n" + replacement,
                )
            )
        }

        if (markerRange != null) {
            val sectionStart = markerRange.start
            val sectionEnd = bodyRange?.end ?: markerRange.end
            val deleteStart = findBlankLinesBefore(text, sectionStart)
            val deleteEnd = findBlankLinesAfter(text, sectionEnd)
            return listOf(
                DocumentEdit(
                    offset = deleteStart,
                    length = deleteEnd - deleteStart,
                    replacement = sectionDeleteReplacement(text, deleteStart, deleteEnd),
                )
            )
        }

        if (links.isNotEmpty()) {
            val replacement = formatDocumentLinks(links)
            val insertOffset = findLinksInsertOffset(text, layout)
            return listOf(
                DocumentEdit(
                    offset = insertOffset,
                    length = 0,
                    replacement = "\nLinks:\n\n" + replacement,
                )
            )
        }

        return emptyList()
    }

    private fun formatDocumentLinks(links: List<Link>): String {
        return buildString {
            for (link in links) {
                append("[${link.title}](${link.url})\n")
            }
        }
    }

    private fun findLinksInsertOffset(text: String, layout: DocumentLayout): Int {
        if (layout.stepsMarkerRange != null) {
            return findBlankLinesBefore(text, layout.stepsMarkerRange.start)
        }
        return text.length
    }

    private fun ensureTrailingNewline(s: String): String =
        if (s.endsWith("\n")) s else s + "\n"

    // ── Run-side: step verdict ──────────────────────────────────
    //
    // Run step verdict is serialized as a trailing `   - <label>` line inside
    // the step body. `StepVerdict.NONE` suppresses the line entirely.

    private val RUN_STEP_VERDICT_LINE = Regex(
        """(?m)^[ \t]*-\s+(passed|failed|skipped|blocked)\s*$""",
        RegexOption.IGNORE_CASE,
    )

    private fun buildSetRunStepVerdictEdits(
        text: String,
        layout: DocumentLayout,
        stepIndex: Int,
        verdict: StepVerdict,
    ): List<DocumentEdit> {
        if (stepIndex !in layout.steps.indices) return emptyList()
        val step = layout.steps[stepIndex]
        val stepText = text.substring(step.wholeRange.start, step.wholeRange.end)
        val existing = RUN_STEP_VERDICT_LINE.find(stepText)
        val commentOffsetInStep = findRunStepCommentSectionStart(stepText)

        if (existing != null) {
            val absStart = step.wholeRange.start + existing.range.first
            val absEnd = step.wholeRange.start + existing.range.last + 1
            return if (verdict == StepVerdict.NONE) {
                // Remove the line and its trailing \n if present.
                val removeEnd = if (absEnd < text.length && text[absEnd] == '\n') absEnd + 1 else absEnd
                val removeStart = if (absStart > 0 && text[absStart - 1] == '\n') absStart else absStart
                listOf(DocumentEdit(removeStart, removeEnd - removeStart, ""))
            } else {
                listOf(DocumentEdit(absStart, absEnd - absStart, "   - ${verdict.label}"))
            }
        }

        if (verdict == StepVerdict.NONE) return emptyList()

        // Insert just before any Comment: block, else at end of step.
        val insertOffsetInStep = commentOffsetInStep ?: stepText.length
        val absInsert = step.wholeRange.start + insertOffsetInStep
        val needsLeadingNewline = insertOffsetInStep > 0 && stepText[insertOffsetInStep - 1] != '\n'
        val replacement = buildString {
            if (needsLeadingNewline) append('\n')
            append("   - ").append(verdict.label).append('\n')
        }
        return listOf(DocumentEdit(absInsert, 0, replacement))
    }

    // ── Run-side: step comment ──────────────────────────────────
    //
    // Run step comment is serialized as a `   Comment:` marker line followed by
    // indented body lines. Empty comment removes the block entirely.

    private val RUN_STEP_COMMENT_MARKER = Regex("""(?m)^[ \t]*Comment:\s*$""")

    /** Returns the offset (within stepText) where the comment block starts, or null. */
    private fun findRunStepCommentSectionStart(stepText: String): Int? {
        val match = RUN_STEP_COMMENT_MARKER.find(stepText) ?: return null
        // Move back over any immediately preceding blank line so the block
        // boundary covers the separating newline as well.
        var start = match.range.first
        while (start > 0 && stepText[start - 1] == '\n') {
            val prevLineEnd = start - 1
            var prevLineStart = prevLineEnd
            while (prevLineStart > 0 && stepText[prevLineStart - 1] != '\n') {
                prevLineStart--
            }
            val lineContent = stepText.substring(prevLineStart, prevLineEnd)
            if (lineContent.isNotBlank()) break
            start = prevLineStart
        }
        return start
    }

    private fun buildSetRunStepCommentEdits(
        text: String,
        layout: DocumentLayout,
        stepIndex: Int,
        comment: String,
    ): List<DocumentEdit> {
        if (stepIndex !in layout.steps.indices) return emptyList()
        val step = layout.steps[stepIndex]
        val stepText = text.substring(step.wholeRange.start, step.wholeRange.end)

        val existingStart = findRunStepCommentSectionStart(stepText)

        if (existingStart != null) {
            val absStart = step.wholeRange.start + existingStart
            val absEnd = step.wholeRange.end
            if (comment.isBlank()) {
                return listOf(DocumentEdit(absStart, absEnd - absStart, ""))
            }
            return listOf(
                DocumentEdit(
                    offset = absStart,
                    length = absEnd - absStart,
                    replacement = formatRunStepCommentBlock(comment, leadingBlankLine = existingStart > 0),
                )
            )
        }

        if (comment.isBlank()) return emptyList()

        val absInsert = step.wholeRange.end
        val needsLeadingNewline = stepText.isNotEmpty() && !stepText.endsWith("\n")
        val replacement = buildString {
            if (needsLeadingNewline) append('\n')
            append(formatRunStepCommentBlock(comment, leadingBlankLine = true))
        }
        return listOf(DocumentEdit(absInsert, 0, replacement))
    }

    private fun formatRunStepCommentBlock(comment: String, leadingBlankLine: Boolean): String {
        return buildString {
            if (leadingBlankLine) append('\n')
            append("   Comment:\n")
            comment.lines().forEach { line ->
                append("   ").append(line).append('\n')
            }
        }
    }
}
