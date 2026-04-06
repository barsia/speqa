package io.github.barsia.speqa.parser

/**
 * Character-offset range in normalized text. [start] inclusive, [end] exclusive.
 */
data class TextRange(val start: Int, val end: Int) {
    init {
        require(start >= 0) { "start must be >= 0, got $start" }
        require(end >= start) { "end must be >= start, got start=$start end=$end" }
    }

    val length: Int get() = end - start
}

data class FrontmatterFieldRange(
    val key: String,
    val wholeRange: TextRange,
)

data class FrontmatterLayout(
    val openDelimiter: TextRange,
    val closeDelimiter: TextRange,
    val fields: List<FrontmatterFieldRange>,
    val bodyStart: Int,
)

data class StepLayout(
    val wholeRange: TextRange,
    val numberRange: TextRange,
    val actionRange: TextRange,
    val actionAttachmentsRange: TextRange?,
    val expectedRange: TextRange?,
    val expectedAttachmentsRange: TextRange?,
)

data class DocumentLayout(
    val frontmatter: FrontmatterLayout?,
    val descriptionRange: TextRange?,
    val preconditionsMarkerRange: TextRange?,
    val preconditionsBodyRange: TextRange?,
    val attachmentsMarkerRange: TextRange?,
    val attachmentsBodyRange: TextRange?,
    val linksMarkerRange: TextRange?,
    val linksBodyRange: TextRange?,
    val stepsMarkerRange: TextRange?,
    val steps: List<StepLayout>,
)

object DocumentRangeLocator {

    private val SCENARIO_MARKER = Regex("""^[Ss]cenario:\s*$""")
    private val ATTACHMENTS_MARKER = Regex("""^[Aa]ttachments:\s*$""")
    private val LINKS_MARKER = Regex("""^[Ll]inks:\s*$""")
    private val PRECONDITIONS_MARKER = Regex("""^[Pp]reconditions:\s*$""")
    private val STEP_PATTERN = Regex("""^(\d+)\.\s(.*)$""")
    private val EXPECTED_PATTERN = Regex("""^>\s?(.*)$""")
    private val ATTACHMENT_LINE = Regex("""^\[.+]\(.+\)$|^!\[.*]\(.+\)$|^\[.+]$""")

    fun locate(rawText: String): DocumentLayout {
        val text = SpeqaMarkdown.normalizeLineEndings(rawText)
        val lines = text.lines()
        val lineStarts = buildLineStartOffsets(text, lines)

        var lineIndex = 0

        // --- Frontmatter ---
        var frontmatter: FrontmatterLayout? = null
        var bodyStartLine = 0

        if (lines.isNotEmpty() && lines[0].trim() == "---") {
            val openStart = 0
            val openEnd = lineStarts.endOfLine(0, text)
            var closeLineIdx = -1
            for (i in 1 until lines.size) {
                if (lines[i].trim() == "---") {
                    closeLineIdx = i
                    break
                }
            }
            if (closeLineIdx != -1) {
                val closeStart = lineStarts[closeLineIdx]
                val closeEnd = lineStarts.endOfLine(closeLineIdx, text)
                val fields = parseFrontmatterFields(lines, lineStarts, text, 1, closeLineIdx)
                frontmatter = FrontmatterLayout(
                    openDelimiter = TextRange(openStart, openEnd),
                    closeDelimiter = TextRange(closeStart, closeEnd),
                    fields = fields,
                    bodyStart = closeEnd,
                )
                bodyStartLine = closeLineIdx + 1
            }
        }

        lineIndex = bodyStartLine

        // --- Body sections ---
        // We scan lines to find: description paragraphs, preconditions, attachments, steps
        var descriptionRange: TextRange? = null
        var preconditionsMarkerRange: TextRange? = null
        var preconditionsBodyRange: TextRange? = null
        var attachmentsMarkerRange: TextRange? = null
        var attachmentsBodyRange: TextRange? = null
        var linksMarkerRange: TextRange? = null
        var linksBodyRange: TextRange? = null
        var stepsMarkerRange: TextRange? = null
        val steps = mutableListOf<StepLayout>()

        // Collect body line groups
        // Strategy: scan for markers, everything before the first marker is description
        // (excluding leading/trailing blank lines)

        // Find where each section starts
        data class SectionStart(val type: String, val lineIdx: Int)

        val sectionStarts = mutableListOf<SectionStart>()
        for (i in bodyStartLine until lines.size) {
            val trimmed = lines[i].trim()
            when {
                PRECONDITIONS_MARKER.matches(trimmed) -> sectionStarts += SectionStart("preconditions", i)
                ATTACHMENTS_MARKER.matches(trimmed) -> sectionStarts += SectionStart("attachments", i)
                LINKS_MARKER.matches(trimmed) -> sectionStarts += SectionStart("links", i)
                SCENARIO_MARKER.matches(trimmed) -> sectionStarts += SectionStart("steps", i)
            }
        }

        // Description = non-blank lines between bodyStartLine and first section marker,
        // excluding surrounding blank lines
        val firstMarkerLine = sectionStarts.minOfOrNull { it.lineIdx }

        val descRegionEnd = (firstMarkerLine ?: lines.size) - 1
        if (descRegionEnd >= bodyStartLine) {
            // Find first and last non-blank lines in [bodyStartLine, descRegionEnd]
            var firstNonBlank: Int? = null
            var lastNonBlank: Int? = null
            for (i in bodyStartLine..descRegionEnd) {
                if (lines[i].isNotBlank()) {
                    if (firstNonBlank == null) firstNonBlank = i
                    lastNonBlank = i
                }
            }
            if (firstNonBlank != null && lastNonBlank != null) {
                // Check that these lines are not a section marker themselves
                val firstTrimmed = lines[firstNonBlank].trim()
                val isMarker = PRECONDITIONS_MARKER.matches(firstTrimmed) ||
                        ATTACHMENTS_MARKER.matches(firstTrimmed) ||
                        LINKS_MARKER.matches(firstTrimmed) ||
                        SCENARIO_MARKER.matches(firstTrimmed)
                if (!isMarker) {
                    descriptionRange = TextRange(
                        lineStarts[firstNonBlank],
                        lineStarts.endOfLine(lastNonBlank, text),
                    )
                }
            }
        }

        // Parse each section
        for ((idx, section) in sectionStarts.withIndex()) {
            val nextSectionLine = if (idx + 1 < sectionStarts.size) sectionStarts[idx + 1].lineIdx else lines.size
            when (section.type) {
                "preconditions" -> {
                    preconditionsMarkerRange = TextRange(
                        lineStarts[section.lineIdx],
                        lineStarts.endOfLine(section.lineIdx, text),
                    )
                    // Body = non-blank lines after marker, before next section
                    val bodyRange = findBodyRange(lines, lineStarts, text, section.lineIdx + 1, nextSectionLine)
                    preconditionsBodyRange = bodyRange
                }
                "attachments" -> {
                    attachmentsMarkerRange = TextRange(
                        lineStarts[section.lineIdx],
                        lineStarts.endOfLine(section.lineIdx, text),
                    )
                    val bodyRange = findBodyRange(lines, lineStarts, text, section.lineIdx + 1, nextSectionLine)
                    attachmentsBodyRange = bodyRange
                }
                "links" -> {
                    linksMarkerRange = TextRange(
                        lineStarts[section.lineIdx],
                        lineStarts.endOfLine(section.lineIdx, text),
                    )
                    val bodyRange = findBodyRange(lines, lineStarts, text, section.lineIdx + 1, nextSectionLine)
                    linksBodyRange = bodyRange
                }
                "steps" -> {
                    stepsMarkerRange = TextRange(
                        lineStarts[section.lineIdx],
                        lineStarts.endOfLine(section.lineIdx, text),
                    )
                    parseSteps(lines, lineStarts, text, section.lineIdx + 1, nextSectionLine, steps)
                }
            }
        }

        return DocumentLayout(
            frontmatter = frontmatter,
            descriptionRange = descriptionRange,
            preconditionsMarkerRange = preconditionsMarkerRange,
            preconditionsBodyRange = preconditionsBodyRange,
            attachmentsMarkerRange = attachmentsMarkerRange,
            attachmentsBodyRange = attachmentsBodyRange,
            linksMarkerRange = linksMarkerRange,
            linksBodyRange = linksBodyRange,
            stepsMarkerRange = stepsMarkerRange,
            steps = steps,
        )
    }

    private fun buildLineStartOffsets(text: String, lines: List<String>): IntArray {
        val offsets = IntArray(lines.size)
        var offset = 0
        for (i in lines.indices) {
            offsets[i] = offset
            offset += lines[i].length
            if (offset < text.length) offset++ // skip \n
        }
        return offsets
    }

    /**
     * Returns the exclusive end offset of the given line, including its trailing \n if present.
     */
    private fun IntArray.endOfLine(lineIdx: Int, text: String): Int {
        val lineStart = this[lineIdx]
        return if (lineIdx + 1 < this.size) {
            this[lineIdx + 1]
        } else {
            text.length
        }
    }

    private fun parseFrontmatterFields(
        lines: List<String>,
        lineStarts: IntArray,
        text: String,
        fromLine: Int,
        toLine: Int, // exclusive
    ): List<FrontmatterFieldRange> {
        val fields = mutableListOf<FrontmatterFieldRange>()
        var i = fromLine
        while (i < toLine) {
            val line = lines[i]
            val colonIdx = line.indexOf(':')
            if (colonIdx > 0 && !line.startsWith(" ") && !line.startsWith("\t")) {
                val key = line.substring(0, colonIdx).trim()
                val fieldStartLine = i
                // Consume continuation lines (indented)
                i++
                while (i < toLine && (lines[i].startsWith("  ") || lines[i].startsWith("\t"))) {
                    i++
                }
                val fieldEndLine = i - 1
                fields += FrontmatterFieldRange(
                    key = key,
                    wholeRange = TextRange(
                        lineStarts[fieldStartLine],
                        lineStarts.endOfLine(fieldEndLine, text),
                    ),
                )
            } else {
                i++
            }
        }
        return fields
    }

    /**
     * Finds the range of non-blank content lines within [fromLine, toLine).
     * Returns null if there are no non-blank lines.
     */
    private fun findBodyRange(
        lines: List<String>,
        lineStarts: IntArray,
        text: String,
        fromLine: Int,
        toLine: Int,
    ): TextRange? {
        var firstNonBlank: Int? = null
        var lastNonBlank: Int? = null
        for (i in fromLine until toLine) {
            if (lines[i].isNotBlank()) {
                if (firstNonBlank == null) firstNonBlank = i
                lastNonBlank = i
            }
        }
        if (firstNonBlank == null || lastNonBlank == null) return null
        return TextRange(
            lineStarts[firstNonBlank],
            lineStarts.endOfLine(lastNonBlank, text),
        )
    }

    private fun parseSteps(
        lines: List<String>,
        lineStarts: IntArray,
        text: String,
        fromLine: Int,
        toLine: Int,
        result: MutableList<StepLayout>,
    ) {
        // Find all step-start lines first
        data class StepStart(val lineIdx: Int, val match: MatchResult)

        val stepStarts = mutableListOf<StepStart>()
        for (i in fromLine until toLine) {
            val line = lines[i]
            val isTopLevel = !line.startsWith(" ") && !line.startsWith("\t")
            if (!isTopLevel) continue
            val trimmed = line.trim()
            val match = STEP_PATTERN.matchEntire(trimmed)
            if (match != null) {
                stepStarts += StepStart(i, match)
            }
        }

        for ((idx, stepStart) in stepStarts.withIndex()) {
            val stepEndLine = if (idx + 1 < stepStarts.size) {
                // Step ends just before the next step start.
                // But we need to exclude trailing blank lines.
                stepStarts[idx + 1].lineIdx
            } else {
                toLine
            }

            // Find the actual last non-blank line of this step
            var lastContentLine = stepStart.lineIdx
            for (i in stepStart.lineIdx until stepEndLine) {
                if (lines[i].isNotBlank()) lastContentLine = i
            }

            val stepLine = stepStart.lineIdx
            val wholeRange = TextRange(
                lineStarts[stepLine],
                lineStarts.endOfLine(lastContentLine, text),
            )

            // Number range: just the digits
            val lineText = lines[stepLine]
            val lineOffset = lineStarts[stepLine]
            val trimmedLine = lineText.trim()
            val leadingSpaces = lineText.length - lineText.trimStart().length
            val dotIdx = trimmedLine.indexOf('.')
            val numberRange = TextRange(
                lineOffset + leadingSpaces,
                lineOffset + leadingSpaces + dotIdx,
            )

            // Now parse sub-ranges within [stepLine, lastContentLine]
            // Action starts after "N. " on the first line
            val afterDotSpace = trimmedLine.substring(dotIdx + 1).let {
                if (it.startsWith(" ")) dotIdx + 2 else dotIdx + 1
            }
            val actionFirstLineStart = lineOffset + leadingSpaces + afterDotSpace

            // Scan lines to determine action, action-attachments, expected, expected-attachments
            var phase = "action" // action -> action-attachments -> expected -> expected-attachments
            var actionEndLine = stepLine
            var actionEndOffset = lineStarts.endOfLine(stepLine, text) // initial: end of first line

            var actionAttStart: Int? = null
            var actionAttEnd: Int? = null
            var expectedStart: Int? = null
            var expectedEnd: Int? = null
            var expectedAttStart: Int? = null
            var expectedAttEnd: Int? = null

            for (i in (stepLine + 1)..lastContentLine) {
                if (lines[i].isBlank()) continue
                val trimmed = lines[i].trim()
                val isExpected = EXPECTED_PATTERN.matches(trimmed)
                val isAttachment = ATTACHMENT_LINE.matches(trimmed)

                when (phase) {
                    "action" -> {
                        if (isExpected) {
                            phase = "expected"
                            expectedStart = lineStarts[i]
                            expectedEnd = lineStarts.endOfLine(i, text)
                        } else if (isAttachment) {
                            phase = "action-attachments"
                            actionAttStart = lineStarts[i]
                            actionAttEnd = lineStarts.endOfLine(i, text)
                        } else {
                            // Continuation line for action
                            actionEndLine = i
                            actionEndOffset = lineStarts.endOfLine(i, text)
                        }
                    }
                    "action-attachments" -> {
                        if (isExpected) {
                            phase = "expected"
                            expectedStart = lineStarts[i]
                            expectedEnd = lineStarts.endOfLine(i, text)
                        } else if (isAttachment) {
                            actionAttEnd = lineStarts.endOfLine(i, text)
                        } else {
                            // Unexpected non-attachment, non-expected line — treat as action continuation
                            // (shouldn't happen in well-formed docs, but be robust)
                            actionEndLine = i
                            actionEndOffset = lineStarts.endOfLine(i, text)
                        }
                    }
                    "expected" -> {
                        if (isExpected) {
                            expectedEnd = lineStarts.endOfLine(i, text)
                        } else if (isAttachment) {
                            phase = "expected-attachments"
                            expectedAttStart = lineStarts[i]
                            expectedAttEnd = lineStarts.endOfLine(i, text)
                        }
                    }
                    "expected-attachments" -> {
                        if (isAttachment) {
                            expectedAttEnd = lineStarts.endOfLine(i, text)
                        }
                    }
                }
            }

            val actionRange = TextRange(actionFirstLineStart, actionEndOffset)

            result += StepLayout(
                wholeRange = wholeRange,
                numberRange = numberRange,
                actionRange = actionRange,
                actionAttachmentsRange = if (actionAttStart != null && actionAttEnd != null) {
                    TextRange(actionAttStart, actionAttEnd)
                } else null,
                expectedRange = if (expectedStart != null && expectedEnd != null) {
                    TextRange(expectedStart, expectedEnd)
                } else null,
                expectedAttachmentsRange = if (expectedAttStart != null && expectedAttEnd != null) {
                    TextRange(expectedAttStart, expectedAttEnd)
                } else null,
            )
        }
    }
}
