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

    /**
     * The destination URL of the inline link whose visible text (content) range contains
     * [offset], or null when [offset] falls outside every link's text. Used to follow a
     * rendered inline link on Ctrl/Cmd+click while the link text stays editable. Only
     * `http(s)://` destinations qualify, matching [inlineLinks]; image syntax `![alt](url)`,
     * anchors, and relative paths return null.
     */
    fun linkUrlAt(text: CharSequence, offset: Int): String? {
        for (m in inlineLink.findAll(text)) {
            val content = m.groups[1] ?: continue
            val url = m.groups[2] ?: continue
            if (offset in content.range.first..(content.range.last + 1)) return url.value
        }
        return null
    }

    /**
     * The destination URL of the inline link whose open-link icon sits at [offset]. The icon
     * is an inline inlay anchored at the link's close end (one past the `](url)` tail, i.e. the
     * first visible offset after the folded suffix), so this maps that inlay offset back to the
     * link's `http(s)://` URL, or null when [offset] is not any link's close end. The icon must
     * sit after the folded `](url)` close region, not at the link's content end (the start of
     * that fold), or the collapsed fold would swallow it and it would never paint. Used to open
     * a rendered inline link on a plain click of its open-link icon. Only `http(s)://`
     * destinations qualify, matching [inlineLinks].
     */
    fun linkUrlAtIconOffset(text: CharSequence, offset: Int): String? {
        for (m in inlineLink.findAll(text)) {
            val url = m.groups[2] ?: continue
            if (offset == m.range.last + 1) return url.value
        }
        return null
    }

    /**
     * Classifies a click [offset] against the inline link at that position: the open-link icon,
     * the link's editable visible text, or neither. The icon anchor (the link's close end) wins
     * over the text, so it is checked first.
     */
    sealed interface LinkTarget {
        /** The click landed on the open-link icon; follow [url]. */
        data class OpenUrl(val url: String) : LinkTarget

        /** The click landed inside the link's visible text; keep editing it (link is [url]). */
        data class EditText(val url: String) : LinkTarget

        /** The click is not on any link icon or link text. */
        object None : LinkTarget
    }

    fun linkTargetAt(text: CharSequence, offset: Int): LinkTarget {
        linkUrlAtIconOffset(text, offset)?.let { return LinkTarget.OpenUrl(it) }
        linkUrlAt(text, offset)?.let { return LinkTarget.EditText(it) }
        return LinkTarget.None
    }

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
