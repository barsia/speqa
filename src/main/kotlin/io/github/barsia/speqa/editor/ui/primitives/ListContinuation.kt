package io.github.barsia.speqa.editor.ui.primitives

/**
 * Pure helper for auto-continuing markdown lists on Enter inside a
 * plain-text editor (JBTextArea). Given the document [text] and the
 * caret [cursor] position, returns a new document state + caret
 * position to apply, or `null` if Enter should fall through to the
 * default behaviour.
 *
 * Supported:
 *  - Unordered list (`-` / `*` marker, optional indent).
 *  - Ordered list (`N.` marker, optional indent — next number is
 *    incremented by 1 regardless of source numbering).
 *
 * Behaviour:
 *  - Enter on a non-empty list item: insert a new line with the next
 *    marker and place the caret after it.
 *  - Enter on an empty list item (marker only, no content): remove the
 *    marker entirely so the user exits the list.
 */
internal object ListContinuation {

    data class Result(val text: String, val cursor: Int)

    private val BULLET_REGEX = Regex("""^(\s*)([-*])\s""")
    private val ORDERED_REGEX = Regex("""^(\s*)(\d+)\.\s""")

    fun onEnter(text: String, cursor: Int): Result? {
        if (cursor == 0) return null
        val lineStart = text.lastIndexOf('\n', cursor - 1) + 1
        val currentLine = text.substring(lineStart, cursor)

        return tryBullet(text, cursor, lineStart, currentLine)
            ?: tryOrdered(text, cursor, lineStart, currentLine)
    }

    private fun tryBullet(text: String, cursor: Int, lineStart: Int, currentLine: String): Result? {
        val match = BULLET_REGEX.find(currentLine) ?: return null
        val indent = match.groupValues[1]
        val marker = match.groupValues[2]
        val prefix = "$indent$marker "
        val contentAfterMarker = currentLine.removePrefix(prefix)

        if (contentAfterMarker.isBlank()) {
            val before = text.substring(0, lineStart)
            val after = text.substring(cursor)
            return Result(before + after, lineStart)
        }
        val before = text.substring(0, cursor)
        val after = text.substring(cursor)
        return Result("$before\n$prefix$after", cursor + 1 + prefix.length)
    }

    private fun tryOrdered(text: String, cursor: Int, lineStart: Int, currentLine: String): Result? {
        val match = ORDERED_REGEX.find(currentLine) ?: return null
        val indent = match.groupValues[1]
        val number = match.groupValues[2].toInt()
        val currentPrefix = "$indent$number. "
        val contentAfterMarker = currentLine.removePrefix(currentPrefix)

        if (contentAfterMarker.isBlank()) {
            val before = text.substring(0, lineStart)
            val after = text.substring(cursor)
            return Result(before + after, lineStart)
        }
        val nextPrefix = "$indent${number + 1}. "
        val before = text.substring(0, cursor)
        val after = text.substring(cursor)
        return Result("$before\n$nextPrefix$after", cursor + 1 + nextPrefix.length)
    }
}
