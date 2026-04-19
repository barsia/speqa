package io.github.barsia.speqa.editor.ui

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
            val newText = before + after
            return Result(newText, lineStart)
        }

        val before = text.substring(0, cursor)
        val after = text.substring(cursor)
        val newText = "$before\n$prefix$after"
        return Result(newText, cursor + 1 + prefix.length)
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
            val newText = before + after
            return Result(newText, lineStart)
        }

        val nextPrefix = "$indent${number + 1}. "
        val before = text.substring(0, cursor)
        val after = text.substring(cursor)
        val newText = "$before\n$nextPrefix$after"
        return Result(newText, cursor + 1 + nextPrefix.length)
    }
}
