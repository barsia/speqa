package io.github.barsia.speqa.editor.ui.primitives

/**
 * Pure helper for creating and replacing inline Markdown links of the form `[text](http...)`.
 *
 * The inline-link pattern is duplicated locally on purpose so this helper does not depend on
 * [MarkdownWysiwygRanges]; both files evolve independently.
 */
internal object LinkMarkdown {
    data class Result(val text: String, val selectionStart: Int, val selectionEnd: Int)

    /** An inline link's span plus its parsed visible [text] and target [url]. */
    data class LinkAt(val span: IntRange, val text: String, val url: String)

    /** Matches `[text](http(s)://url)`, excluding image links (`![text](...)`). */
    private val inlineLink = Regex("""(?<!!)\[([^\]\n]+)\]\((https?://[^)\s]+)\)""")

    /**
     * Returns the full `[text](http...)` span that contains [offset] (inclusive of the whole
     * `[text](url)`), or null when [offset] is not inside an inline link.
     */
    fun linkSpanAt(text: CharSequence, offset: Int): IntRange? =
        linkMatchAt(text, offset)?.range

    /**
     * Returns the inline link containing [offset] - its [LinkAt.span] plus the parsed visible
     * text and target url - or null when [offset] is not inside an http(s) link (images excluded).
     */
    fun linkAt(text: CharSequence, offset: Int): LinkAt? {
        val match = linkMatchAt(text, offset) ?: return null
        return LinkAt(
            span = match.range,
            text = match.groupValues[1],
            url = match.groupValues[2],
        )
    }

    private fun linkMatchAt(text: CharSequence, offset: Int): MatchResult? =
        inlineLink.findAll(text)
            .firstOrNull { offset >= it.range.first && offset <= it.range.last + 1 }

    /**
     * Builds `[linkText](url)`. If `[selStart, selEnd)` lies within an existing link span, that
     * whole span is replaced; otherwise `[selStart, selEnd)` is replaced. The returned selection
     * covers the visible [linkText] (the characters between `[` and `]`).
     *
     * Spaces and parentheses in [url] are percent-encoded so the produced markdown always
     * matches [inlineLink]; an unencoded `(`/`)`/space would end the URL early (or break the
     * match entirely) and leave raw markup in the preview.
     */
    fun applyLink(text: String, selStart: Int, selEnd: Int, linkText: String, url: String): Result {
        val start = selStart.coerceIn(0, text.length)
        val end = selEnd.coerceIn(start, text.length)
        val existing = linkSpanAt(text, start)
        val replaceStart: Int
        val replaceEnd: Int
        if (existing != null) {
            replaceStart = existing.first
            replaceEnd = existing.last + 1
        } else {
            replaceStart = start
            replaceEnd = end
        }
        val replacement = "[$linkText](${encodeUrl(url)})"
        return Result(
            text = text.replaceRange(replaceStart, replaceEnd, replacement),
            selectionStart = replaceStart + 1,
            selectionEnd = replaceStart + 1 + linkText.length,
        )
    }

    /**
     * Unwraps the link containing [offset] back to its plain visible text, or returns null when
     * [offset] is not inside an inline link. The returned selection covers the unwrapped text.
     */
    fun removeLink(text: String, offset: Int): Result? {
        val link = linkAt(text, offset) ?: return null
        return Result(
            text = text.replaceRange(link.span.first, link.span.last + 1, link.text),
            selectionStart = link.span.first,
            selectionEnd = link.span.first + link.text.length,
        )
    }

    private fun encodeUrl(url: String): String = url
        .replace(" ", "%20")
        .replace("(", "%28")
        .replace(")", "%29")
}
