package io.github.barsia.speqa.editor.ui.primitives

internal enum class MarkdownFormatAction {
    BOLD,
    ITALIC,
    STRIKE,
    INLINE_CODE,
    CODE_BLOCK,
    BULLET_LIST,
    NUMBERED_LIST,
}

internal data class MarkdownFormatResult(
    val text: String,
    val selectionStart: Int,
    val selectionEnd: Int,
)

internal object MarkdownSelectionFormatter {
    fun apply(
        text: String,
        selectionStart: Int,
        selectionEnd: Int,
        action: MarkdownFormatAction,
    ): MarkdownFormatResult {
        val start = selectionStart.coerceIn(0, text.length)
        val end = selectionEnd.coerceIn(start, text.length)
        if (action == MarkdownFormatAction.BULLET_LIST || action == MarkdownFormatAction.NUMBERED_LIST) {
            return applyList(text, start, end, ordered = action == MarkdownFormatAction.NUMBERED_LIST)
        }
        return when (action) {
            MarkdownFormatAction.BOLD -> toggleWrap(text, start, end, "**", "**")
            MarkdownFormatAction.ITALIC -> toggleWrap(text, start, end, "_", "_")
            MarkdownFormatAction.STRIKE -> toggleWrap(text, start, end, "~~", "~~")
            MarkdownFormatAction.INLINE_CODE -> toggleWrap(text, start, end, "`", "`")
            MarkdownFormatAction.CODE_BLOCK -> toggleCodeBlock(text, start, end)
            MarkdownFormatAction.BULLET_LIST,
            MarkdownFormatAction.NUMBERED_LIST -> error("List actions are handled before substring formatting")
        }
    }

    private fun toggleCodeBlock(text: String, selectionStart: Int, selectionEnd: Int): MarkdownFormatResult {
        val containingBlock = MarkdownWysiwygRanges.fencedCodeBlocks(text)
            .firstOrNull { range ->
                val selectionTouchesContent = selectionStart >= range.contentStart && selectionEnd <= range.contentEnd
                val selectionTouchesWholeBlock = selectionStart >= range.openStart && selectionEnd <= range.closeEnd
                selectionTouchesContent || selectionTouchesWholeBlock
            }
        if (containingBlock != null) {
            val followingNewlineEnd = if (text.hasRange(containingBlock.closeEnd, containingBlock.closeEnd + 1, "\n")) {
                containingBlock.closeEnd + 1
            } else {
                containingBlock.closeEnd
            }
            val content = text.substring(containingBlock.contentStart, containingBlock.contentEnd)
            val replacement = if (followingNewlineEnd > containingBlock.closeEnd) {
                content
            } else {
                content.removeSuffix("\n")
            }
            return MarkdownFormatResult(
                text = text.replaceRange(containingBlock.openStart, followingNewlineEnd, replacement),
                selectionStart = containingBlock.openStart,
                selectionEnd = containingBlock.openStart + replacement.removeSuffix("\n").length,
            )
        }

        val start = lineStart(text, selectionStart)
        val end = lineEnd(text, selectionEnd)
        val selected = text.substring(start, end)
        val replacement = "```\n$selected\n```"
        return MarkdownFormatResult(
            text = text.replaceRange(start, end, replacement),
            selectionStart = start,
            selectionEnd = start + replacement.length,
        )
    }

    private fun toggleWrap(
        text: String,
        selectionStart: Int,
        selectionEnd: Int,
        prefix: String,
        suffix: String,
    ): MarkdownFormatResult {
        val selected = text.substring(selectionStart, selectionEnd)
        if (selected.startsWith(prefix) && selected.endsWith(suffix) && selected.length >= prefix.length + suffix.length) {
            val replacement = selected.removePrefix(prefix).removeSuffix(suffix)
            return MarkdownFormatResult(
                text = text.replaceRange(selectionStart, selectionEnd, replacement),
                selectionStart = selectionStart,
                selectionEnd = selectionStart + replacement.length,
            )
        }
        if (
            text.hasRange(selectionStart - prefix.length, selectionStart, prefix) &&
            text.hasRange(selectionEnd, selectionEnd + suffix.length, suffix)
        ) {
            val replacement = selected
            return MarkdownFormatResult(
                text = text.replaceRange(selectionStart - prefix.length, selectionEnd + suffix.length, replacement),
                selectionStart = selectionStart - prefix.length,
                selectionEnd = selectionStart - prefix.length + replacement.length,
            )
        }

        val replacement = "$prefix$selected$suffix"
        return MarkdownFormatResult(
            text = text.replaceRange(selectionStart, selectionEnd, replacement),
            selectionStart = selectionStart,
            selectionEnd = selectionStart + replacement.length,
        )
    }

    private fun String.hasRange(start: Int, end: Int, value: String): Boolean =
        start >= 0 && end <= length && substring(start, end) == value

    private fun lineStart(text: String, offset: Int): Int =
        if (offset <= 0) {
            0
        } else {
            text.lastIndexOf('\n', offset - 1).let { if (it < 0) 0 else it + 1 }
        }

    private fun lineEnd(text: String, offset: Int): Int =
        if (offset >= text.length) {
            text.length
        } else {
            text.indexOf('\n', offset).let { if (it < 0) text.length else it }
        }

    private fun applyList(text: String, selectionStart: Int, selectionEnd: Int, ordered: Boolean): MarkdownFormatResult {
        val start = lineStart(text, selectionStart)
        val end = lineEnd(text, selectionEnd)
        val replacement = list(text.substring(start, end), ordered)
        return MarkdownFormatResult(
            text = text.replaceRange(start, end, replacement),
            selectionStart = start,
            selectionEnd = start + replacement.length,
        )
    }

    private fun list(text: String, ordered: Boolean): String {
        if (allLinesHaveListMarker(text, ordered)) {
            return text.split('\n').joinToString("\n") { line -> line.removeListMarker(ordered) }
        }
        var number = 1
        return text.split('\n').joinToString("\n") { line ->
            val indentLength = line.indexOfFirst { !it.isWhitespace() }.let { if (it < 0) line.length else it }
            val indent = line.take(indentLength)
            val content = line.drop(indentLength)
            val marker = if (ordered) "${number++}. " else "- "
            "$indent$marker$content"
        }
    }

    private fun allLinesHaveListMarker(text: String, ordered: Boolean): Boolean =
        text.split('\n').all { line ->
            val content = line.trimStart()
            if (ordered) ORDERED_LIST_MARKER.containsMatchIn(content) else content.startsWith("- ")
        }

    private fun String.removeListMarker(ordered: Boolean): String {
        val indentLength = indexOfFirst { !it.isWhitespace() }.let { if (it < 0) length else it }
        val indent = take(indentLength)
        val content = drop(indentLength)
        return if (ordered) {
            indent + content.replaceFirst(ORDERED_LIST_MARKER, "")
        } else {
            indent + content.removePrefix("- ")
        }
    }

    private val ORDERED_LIST_MARKER = Regex("""^\d+\.\s+""")
}
