package io.github.barsia.speqa.editor

/**
 * Decides what to do after the platform deletes a character via Backspace in a
 * step blockquote line inside `.tc.md` / `.tr.md`.
 *
 * Two cases are handled (both called from [SpeqaBlockquoteBackspaceHandler.charDeleted]
 * with text and caret **after** the platform already deleted one character):
 *
 * **Case 1** — cursor was after `> ` (`   > <caret>content`), platform deleted the
 * space: text is now `   >content`, caret right after `>`. We remove the `>` too,
 * producing `   <caret>content`.
 *
 * **Case 2** — cursor was before `>` (`   <caret>> content`), platform deleted the
 * last indent space: text is now `  >content` (or `  > content` if the space after
 * `>` survived), caret now before `>`. If `>` is immediately followed by a space we
 * remove both (`> `), producing `  <caret>content`.
 *
 * Returns `null` in every other case; the platform's default handling is used.
 */
object SpeqaBlockquoteBackspace {

    data class Decision(
        val deleteStart: Int,
        val deleteEnd: Int,
        val caretOffset: Int,
    )

    /**
     * @param text        Document text AFTER the platform deleted [deletedChar].
     * @param caretOffset Caret position AFTER the platform deleted [deletedChar].
     * @param deletedChar The character that the platform just deleted (= `' '` to trigger).
     */
    fun decide(text: CharSequence, caretOffset: Int, deletedChar: Char): Decision? {
        if (deletedChar != ' ') return null

        // Case 1: platform deleted the space of `> `, cursor now right after `>`
        // text[caret-1] = '>', all chars before `>` on the line are spaces.
        if (caretOffset > 0 && text[caretOffset - 1] == '>') {
            val lineStart = text.lastIndexOf('\n', (caretOffset - 2).coerceAtLeast(0))
                .let { if (it < 0) 0 else it + 1 }
            if ((lineStart until caretOffset - 1).all { text[it] == ' ' }) {
                val arrowPos = caretOffset - 1
                return Decision(deleteStart = arrowPos, deleteEnd = arrowPos + 1, caretOffset = arrowPos)
            }
        }

        // Case 2: platform deleted an indent space, cursor now before `>`.
        // text[caret] = '>', all chars from line start to caret are spaces.
        // Delete `>` plus one following space if present (handles `>1.`, `> 1.`, `>   2.`).
        if (caretOffset < text.length && text[caretOffset] == '>') {
            val lineStart = text.lastIndexOf('\n', (caretOffset - 1).coerceAtLeast(0))
                .let { if (it < 0) 0 else it + 1 }
            if ((lineStart until caretOffset).all { text[it] == ' ' }) {
                val deleteEnd = if (caretOffset + 1 < text.length && text[caretOffset + 1] == ' ')
                    caretOffset + 2 else caretOffset + 1
                return Decision(deleteStart = caretOffset, deleteEnd = deleteEnd, caretOffset = caretOffset)
            }
        }

        return null
    }
}
