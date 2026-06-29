package io.github.barsia.speqa.editor.ui.primitives

/**
 * Pure helper for creating and replacing inline Markdown links of the form `[text](http...)`.
 *
 * The inline-link pattern is duplicated locally on purpose so this helper does not depend on
 * [MarkdownWysiwygRanges]; both files evolve independently.
 */
internal object LinkMarkdown {
    data class Result(val text: String, val selectionStart: Int, val selectionEnd: Int)

    /** Matches `[text](http(s)://url)`, excluding image links (`![text](...)`). */
    private val inlineLink = Regex("""(?<!!)\[([^\]\n]+)\]\((https?://[^)\s]+)\)""")

    /**
     * Returns the full `[text](http...)` span that contains [offset] (inclusive of the whole
     * `[text](url)`), or null when [offset] is not inside an inline link.
     */
    fun linkSpanAt(text: CharSequence, offset: Int): IntRange? =
        inlineLink.findAll(text)
            .map { it.range }
            .firstOrNull { offset >= it.first && offset <= it.last + 1 }

    /**
     * Builds `[linkText](url)`. If `[selStart, selEnd)` lies within an existing link span, that
     * whole span is replaced; otherwise `[selStart, selEnd)` is replaced. The returned selection
     * covers the visible [linkText] (the characters between `[` and `]`).
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
        val replacement = "[$linkText]($url)"
        return Result(
            text = text.replaceRange(replaceStart, replaceEnd, replacement),
            selectionStart = replaceStart + 1,
            selectionEnd = replaceStart + 1 + linkText.length,
        )
    }
}
