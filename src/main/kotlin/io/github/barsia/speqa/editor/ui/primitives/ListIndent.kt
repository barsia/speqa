package io.github.barsia.speqa.editor.ui.primitives

/**
 * Pure helper for indenting / outdenting a markdown list item with
 * Tab / Shift+Tab inside a markdown editor. Given the document [text]
 * and the caret [cursor] position, returns a new document state + caret
 * position to apply, or `null` if the key should fall through to the
 * editor's default behaviour.
 *
 * The whole list item is shifted: the marker line plus every
 * continuation line that belongs to it (expected-result blockquote
 * `>` lines, wrapped continuation text). Indentation is added to the
 * left of each line only - spaces are never inserted after the `>`
 * blockquote marker. This deliberately replaces the bundled IntelliJ
 * Markdown plugin's Tab action, which reindents child blocks by
 * inserting spaces inside the blockquote (`>    text`) and corrupts the
 * shared scenario step shape.
 *
 * Supported markers: unordered (`-` / `*`) and ordered (`N.`), with an
 * optional leading indent. The indent unit equals the item's marker
 * width (prefix length excluding its own leading indent), so one Tab
 * nests the item exactly one level under its list.
 */
internal object ListIndent {

    data class Result(val text: String, val cursor: Int)

    private val BULLET_REGEX = Regex("""^(\s*)([-*])\s""")
    private val ORDERED_REGEX = Regex("""^(\s*)(\d+)\.\s""")

    fun onTab(text: String, cursor: Int): Result? = shift(text, cursor, outdent = false)

    fun onShiftTab(text: String, cursor: Int): Result? = shift(text, cursor, outdent = true)

    private fun shift(text: String, cursor: Int, outdent: Boolean): Result? {
        val item = locateItem(text, cursor) ?: return null
        val unit = item.markerWidth
        if (outdent && item.lines.all { leadingSpaces(text, it) == 0 }) return null

        val builder = StringBuilder(text.length + unit)
        var prev = 0
        var caret = cursor
        for (line in item.lines) {
            builder.append(text, prev, line.start)
            if (outdent) {
                val removed = minOf(unit, leadingSpaces(text, line))
                if (line.start < cursor) caret -= minOf(removed, cursor - line.start)
                builder.append(text, line.start + removed, line.end)
            } else {
                builder.append(" ".repeat(unit))
                if (line.start <= cursor) caret += unit
                builder.append(text, line.start, line.end)
            }
            prev = line.end
        }
        builder.append(text, prev, text.length)
        val result = builder.toString()
        return Result(result, caret.coerceIn(0, result.length))
    }

    private data class LineRange(val start: Int, val end: Int)

    private data class Item(val markerWidth: Int, val lines: List<LineRange>)

    /** Caret line, walking up to the nearest list marker line if needed. */
    private fun locateItem(text: String, cursor: Int): Item? {
        var lineStart = text.lastIndexOf('\n', (cursor - 1).coerceAtLeast(0)) + 1
        while (true) {
            val lineEnd = lineEnd(text, lineStart)
            val line = text.substring(lineStart, lineEnd)
            val match = BULLET_REGEX.find(line) ?: ORDERED_REGEX.find(line)
            if (match != null) {
                val indent = match.groupValues[1].length
                val markerWidth = match.value.length - indent
                return Item(markerWidth, collectItemLines(text, lineStart, lineEnd, indent + markerWidth))
            }
            // Not a marker line: it may be a continuation of a marker above.
            if (line.isBlank() || lineStart == 0) return null
            if (leadingSpaces(text, LineRange(lineStart, lineEnd)) == 0) return null
            lineStart = text.lastIndexOf('\n', lineStart - 2) + 1
        }
    }

    /** Marker line plus following continuation lines at or beyond [contentColumn]. */
    private fun collectItemLines(text: String, markerStart: Int, markerEnd: Int, contentColumn: Int): List<LineRange> {
        val lines = mutableListOf(LineRange(markerStart, markerEnd))
        var start = markerEnd + 1
        while (start <= text.length && markerEnd < text.length) {
            val end = lineEnd(text, start)
            val line = text.substring(start, end)
            // Stop at a blank line or a line less indented than the item's
            // content column; deeper-indented lines (including nested list
            // items and blockquotes) belong to this item's subtree.
            if (line.isBlank() || leadingSpaces(text, LineRange(start, end)) < contentColumn) break
            lines += LineRange(start, end)
            if (end >= text.length) break
            start = end + 1
        }
        return lines
    }

    private fun lineEnd(text: String, start: Int): Int {
        val nl = text.indexOf('\n', start)
        return if (nl < 0) text.length else nl
    }

    private fun leadingSpaces(text: String, line: LineRange): Int {
        var i = line.start
        while (i < line.end && text[i] == ' ') i++
        return i - line.start
    }
}
