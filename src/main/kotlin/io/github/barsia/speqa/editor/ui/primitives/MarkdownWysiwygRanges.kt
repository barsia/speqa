package io.github.barsia.speqa.editor.ui.primitives

internal data class MarkdownWysiwygRange(
    val openStart: Int,
    val openEnd: Int,
    val contentStart: Int,
    val contentEnd: Int,
    val closeStart: Int,
    val closeEnd: Int,
    val closeFoldEnd: Int,
    val contentIndentFolds: List<MarkdownWysiwygFoldRange> = emptyList(),
)

internal data class MarkdownWysiwygFoldRange(
    val start: Int,
    val end: Int,
)

internal object MarkdownWysiwygRanges {
    private val fencedCodeBlock = Regex("""(?m)^([ \t]*```[^\n]*\n)(.*?)(^[ \t]*```)(?=\n|$)""", RegexOption.DOT_MATCHES_ALL)

    private val inlineCode = Regex("`([^`\\n]+)`")

    // Inline Markdown link `[text](http(s)://...)`. The negative lookbehind on `!`
    // keeps image syntax `![alt](url)` from matching as a plain link. Only http(s)
    // destinations qualify so relative/anchor links stay as literal text.
    private val inlineLink = Regex("""(?<!!)\[([^\]\n]+)\]\((https?://[^)\s]+)\)""")

    /**
     * The inline-code span (`` `...` ``) the caret is currently editing, as the inclusive
     * index range of the whole span (both backticks), or null when the caret is not between
     * the backticks of any inline-code span. The caret counts as inside from just after the
     * opening backtick up to just before the closing one - the span that must be exempt from
     * delimiter folding so collapsing the closing-backtick fold cannot eject the caret.
     */
    fun inlineCodeSpanAt(text: CharSequence, caret: Int): IntRange? {
        for (m in inlineCode.findAll(text)) {
            val openStart = m.range.first
            val closeEnd = m.range.last + 1
            if (caret in (openStart + 1)..(closeEnd - 1)) return openStart..(closeEnd - 1)
        }
        return null
    }

    /**
     * Inline Markdown links `[text](http(s)://...)` as fold ranges: the `[` is folded
     * via [MarkdownWysiwygRange.openStart]..[MarkdownWysiwygRange.openEnd] and the
     * `](url)` tail via [MarkdownWysiwygRange.closeStart]..[MarkdownWysiwygRange.closeEnd],
     * leaving only the link text visible. Image syntax `![alt](url)` is excluded.
     */
    fun inlineLinks(text: CharSequence): List<MarkdownWysiwygRange> =
        inlineLink.findAll(text).map { match ->
            val content = match.groups[1] ?: error("Link text group is required")
            val contentStart = content.range.first
            val contentEnd = content.range.last + 1
            val closeEnd = match.range.last + 1
            MarkdownWysiwygRange(
                openStart = match.range.first,
                openEnd = contentStart,
                contentStart = contentStart,
                contentEnd = contentEnd,
                closeStart = contentEnd,
                closeEnd = closeEnd,
                closeFoldEnd = closeEnd,
            )
        }.toList()

    fun fencedCodeBlocks(text: CharSequence): List<MarkdownWysiwygRange> =
        fencedCodeBlock.findAll(text).map { match ->
            val open = match.groups[1] ?: error("Opening code fence group is required")
            val content = match.groups[2] ?: error("Code block content group is required")
            val close = match.groups[3] ?: error("Closing code fence group is required")
            val fenceIndent = open.value.takeWhile { it == ' ' || it == '\t' }
            MarkdownWysiwygRange(
                openStart = open.range.first,
                openEnd = open.range.last + 1,
                contentStart = content.range.first,
                contentEnd = content.range.last + 1,
                closeStart = close.range.first,
                closeEnd = close.range.last + 1,
                closeFoldEnd = if (close.range.last + 1 < text.length && text[close.range.last + 1] == '\n') {
                    close.range.last + 2
                } else {
                    close.range.last + 1
                },
                contentIndentFolds = commonContentIndentFolds(text, content.range.first, content.range.last + 1, fenceIndent),
            )
        }.toList()

    fun shouldConsumeHiddenCodeBlockEdit(
        text: CharSequence,
        caretOffset: Int,
        backspace: Boolean,
    ): Boolean {
        if (caretOffset < 0 || caretOffset > text.length) return false
        val editOffset = if (backspace) caretOffset - 1 else caretOffset
        if (editOffset < 0 || editOffset >= text.length) return false
        return fencedCodeBlocks(text).any { range ->
            if (backspace && caretOffset == range.contentStart) return@any true
            if (!backspace && caretOffset == range.openStart) return@any true
            range.contentIndentFolds.any { fold ->
                editOffset in fold.start until fold.end
            }
        }
    }

    private fun commonContentIndentFolds(
        text: CharSequence,
        contentStart: Int,
        contentEnd: Int,
        fenceIndent: String,
    ): List<MarkdownWysiwygFoldRange> {
        if (fenceIndent.isEmpty()) return emptyList()
        val folds = mutableListOf<MarkdownWysiwygFoldRange>()
        var lineStart = contentStart
        while (lineStart < contentEnd) {
            val lineEnd = lineEnd(text, lineStart, contentEnd)
            if (lineEnd > lineStart && startsWith(text, lineStart, lineEnd, fenceIndent)) {
                folds += MarkdownWysiwygFoldRange(lineStart, lineStart + fenceIndent.length)
            }
            lineStart = if (lineEnd < contentEnd && text[lineEnd] == '\n') lineEnd + 1 else contentEnd
        }
        return folds
    }

    private fun lineEnd(text: CharSequence, lineStart: Int, contentEnd: Int): Int {
        var i = lineStart
        while (i < contentEnd && text[i] != '\n') i++
        return i
    }

    private fun startsWith(text: CharSequence, start: Int, end: Int, prefix: String): Boolean {
        if (end - start < prefix.length) return false
        for (i in prefix.indices) {
            if (text[start + i] != prefix[i]) return false
        }
        return true
    }
}
