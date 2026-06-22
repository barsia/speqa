package io.github.barsia.speqa.editor

/**
 * Decides what Enter should do on a step blockquote line inside `.tc.md` /
 * `.tr.md`. Two behaviours we own:
 *
 *  - **Continue with proper indent.** On `<step-indent>> content`: insert
 *    `\n<step-indent>> ` so the new expected line aligns with the parent
 *    step's number column (3 spaces for `1.`, 4 for `10.`, 5 for `100.`).
 *    The IntelliJ Markdown plugin's own continue handler does NOT preserve
 *    this indent reliably in realistic test-case layouts (multiple steps,
 *    frontmatter): it falls back to `> ` at column 0 because our 3-space
 *    indent is below the CommonMark threshold for list-item children.
 *
 *  - **Exit on empty.** On `<step-indent>>` with no content: drop the
 *    prefix entirely and park the caret at column 0, so a second Enter
 *    closes the expected-result block (the platform would otherwise spawn
 *    another empty `>` line).
 *
 * For everything else this returns `null` and the platform's Enter handler
 * runs as usual.
 */
object SpeqaBlockquoteEnter {
    data class Decision(
        val replaceStart: Int,
        val replaceEnd: Int,
        val replacement: String,
        val caretOffset: Int,
    )

    fun decide(text: String, caretOffset: Int): Decision? {
        val (lineStart, lineEnd) = lineBoundsAt(text, caretOffset)
        val line = text.substring(lineStart, lineEnd)
        if (isInsideFencedCodeBlock(text, lineStart)) return null

        decideSubStep(text, line, lineStart, lineEnd, caretOffset)?.let { return it }
        decideBlockquote(text, line, lineStart, lineEnd, caretOffset)?.let { return it }
        return null
    }

    private fun decideBlockquote(
        text: String,
        line: String,
        lineStart: Int,
        lineEnd: Int,
        caretOffset: Int,
    ): Decision? {
        val prefixMatch = BLOCKQUOTE_PREFIX.find(line) ?: return null
        val caretInLine = caretOffset - lineStart
        if (caretInLine < prefixMatch.range.last + 1) return null

        val stepDigits = stepDigitsAbove(text, lineStart) ?: return null

        if (EMPTY_BLOCKQUOTE_LINE.matches(line)) {
            return Decision(
                replaceStart = lineStart,
                replaceEnd = lineEnd,
                replacement = "",
                caretOffset = lineStart,
            )
        }

        val indent = " ".repeat(stepDigits + 2)
        val content = line.substring(prefixMatch.range.last + 1)
        val listMatch = BLOCKQUOTE_NUMBERED_ITEM.matchEntire(content)

        // Empty numbered item `> N. <caret>` → remove the number, keep `> `.
        // One more Enter on the resulting empty `> ` will exit the blockquote.
        if (listMatch != null && listMatch.groupValues[2].isBlank()) {
            return Decision(
                replaceStart = lineStart,
                replaceEnd = lineEnd,
                replacement = "$indent> ",
                caretOffset = lineStart + indent.length + 2,
            )
        }

        val replacement = if (listMatch != null) {
            "\n$indent> ${listMatch.groupValues[1].toInt() + 1}. "
        } else {
            "\n$indent> "
        }
        return Decision(
            replaceStart = caretOffset,
            replaceEnd = caretOffset,
            replacement = replacement,
            caretOffset = caretOffset + replacement.length,
        )
    }

    /**
     * Handles Enter inside a numbered sub-step:
     *
     *  - Inline form: `<parent>. <sub>. content<caret>` on a step line.
     *  - Indented form: `<step-indent><sub>. content<caret>` on a continuation line.
     *
     * Continue: insert `\n<step-indent><sub+1>. ` at caret.
     * Empty sub-step (indented only, e.g. `   2. <caret>`): replace the prefix
     * with `<step-indent>> ` so a second Enter pivots from the numbered
     * sub-list into the expected-result blockquote.
     */
    private fun decideSubStep(
        text: String,
        line: String,
        lineStart: Int,
        lineEnd: Int,
        caretOffset: Int,
    ): Decision? {
        val caretInLine = caretOffset - lineStart

        // Inline form: parent + sub on the same step line.
        INLINE_SUBSTEP_LINE.matchEntire(line)?.let { m ->
            val parentDigits = m.groupValues[1].length
            val subNumber = m.groupValues[2].toInt()
            val prefixEnd = m.groupValues[1].length + 2 + m.groupValues[2].length + 2
            if (caretInLine < prefixEnd) return null

            val indent = " ".repeat(parentDigits + 2)
            val replacement = "\n$indent${subNumber + 1}. "
            return Decision(
                replaceStart = caretOffset,
                replaceEnd = caretOffset,
                replacement = replacement,
                caretOffset = caretOffset + replacement.length,
            )
        }

        // Indented sub-step form on a continuation line.
        INDENTED_SUBSTEP_LINE.matchEntire(line)?.let { m ->
            val indentText = m.groupValues[1]
            val subNumber = m.groupValues[2].toInt()
            val parentDigits = stepDigitsAbove(text, lineStart) ?: return null
            // Indent must align with the parent step's column width; otherwise
            // this is some other numbered list, not our nested sub-step.
            if (indentText.length != parentDigits + 2) return null

            val prefixEnd = indentText.length + m.groupValues[2].length + 2
            if (caretInLine < prefixEnd) return null

            val isEmpty = m.groupValues[3].isBlank()
            val indent = " ".repeat(parentDigits + 2)

            return if (isEmpty) {
                Decision(
                    replaceStart = lineStart,
                    replaceEnd = lineEnd,
                    replacement = "$indent> ",
                    caretOffset = lineStart + indent.length + 2,
                )
            } else {
                val replacement = "\n$indent${subNumber + 1}. "
                Decision(
                    replaceStart = caretOffset,
                    replaceEnd = caretOffset,
                    replacement = replacement,
                    caretOffset = caretOffset + replacement.length,
                )
            }
        }

        return null
    }

    private fun lineBoundsAt(text: String, offset: Int): Pair<Int, Int> {
        val start = text.lastIndexOf('\n', (offset - 1).coerceAtLeast(0)).let { if (it < 0) 0 else it + 1 }
        val end = text.indexOf('\n', offset).let { if (it < 0) text.length else it }
        return start to end
    }

    private fun stepDigitsAbove(text: String, currentLineStart: Int): Int? {
        var lineEnd = currentLineStart - 1
        while (lineEnd >= 0) {
            val lineStart = text.lastIndexOf('\n', (lineEnd - 1).coerceAtLeast(0)).let { if (it < 0) 0 else it + 1 }
            val line = text.substring(lineStart, lineEnd)
            val m = STEP_LINE.matchEntire(line)
            if (m != null) return m.groupValues[1].length
            lineEnd = lineStart - 1
        }
        return null
    }

    private fun isInsideFencedCodeBlock(text: String, currentLineStart: Int): Boolean {
        var fenceCount = 0
        var lineEnd = currentLineStart - 1
        while (lineEnd >= 0) {
            val lineStart = text.lastIndexOf('\n', (lineEnd - 1).coerceAtLeast(0)).let { if (it < 0) 0 else it + 1 }
            val line = text.substring(lineStart, lineEnd)
            if (CODE_FENCE_LINE.matches(line)) fenceCount++
            lineEnd = lineStart - 1
        }
        return fenceCount % 2 == 1
    }

    private val BLOCKQUOTE_PREFIX = Regex("""^\s*>\s?""")
    private val EMPTY_BLOCKQUOTE_LINE = Regex("""^\s*>\s*$""")
    private val BLOCKQUOTE_NUMBERED_ITEM = Regex("""^(\d+)\.\s(.*)$""")
    private val STEP_LINE = Regex("""^(\d+)\.\s.*$""")
    private val INLINE_SUBSTEP_LINE = Regex("""^(\d+)\. (\d+)\. (.*)$""")
    private val INDENTED_SUBSTEP_LINE = Regex("""^( +)(\d+)\.\s?(.*)$""")
    private val CODE_FENCE_LINE = Regex("""^\s*```.*$""")
}
