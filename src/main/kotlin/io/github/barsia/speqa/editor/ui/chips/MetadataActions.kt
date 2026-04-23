package io.github.barsia.speqa.editor.ui.chips

/**
 * Truncate [text] to at most [maxChars] characters, replacing the tail with an
 * ellipsis when truncation occurs. The ellipsis is counted in the budget, so the
 * result length is strictly ≤ [maxChars]. Trailing whitespace is trimmed before
 * the ellipsis so "abc " → "abc…" rather than "abc …".
 *
 * Edge cases:
 *  - empty string → empty string;
 *  - length ≤ [maxChars] → original string;
 *  - length > [maxChars] → take(maxChars - 1), trimEnd, append "…".
 */
fun truncateWithEllipsis(text: String, maxChars: Int): String {
    if (text.length <= maxChars) return text
    return text.take(maxChars - 1).trimEnd() + "…"
}

/**
 * Pure projection from (optional ID text, raw title, relative path, current flag)
 * to the renderer-friendly display record. The Swing-side wrapper that reads the
 * [com.intellij.openapi.vfs.VirtualFile] (parse test case / run, derive `basePath`,
 * look up `FileEditorManager.selectedFiles`) sits next to the popup code; the
 * pure part is separated here so title/path truncation is unit-testable without
 * spinning up a `Project`.
 *
 * `titleTruncation` and `pathTruncation` default to (56 / 72) so call sites
 * don't need to repeat the magic numbers.
 */
data class IndexedFileMatchDisplay(
    val idText: String?,
    val titleText: String,
    val pathText: String,
    val isCurrent: Boolean,
)

/**
 * Which side of the file pair a metadata chip's click/context-menu entries
 * should search by default. `TagCloud` takes this as a parameter and threads
 * it into the chip callbacks.
 */
enum class MetadataScope { TEST_CASES, TEST_RUNS }

/**
 * The category of metadata value a chip represents. Drives which index on
 * [io.github.barsia.speqa.registry.SpeqaTagRegistry] is queried for candidates.
 */
enum class MetadataKind { TAG, ENVIRONMENT }

fun indexedFileMatchFrom(
    idText: String?,
    titleRaw: String,
    fallbackTitle: String,
    relativePath: String,
    isCurrent: Boolean,
    titleTruncation: Int = 56,
    pathTruncation: Int = 72,
): IndexedFileMatchDisplay {
    val title = titleRaw.ifBlank { fallbackTitle }
    return IndexedFileMatchDisplay(
        idText = idText,
        titleText = truncateWithEllipsis(title, titleTruncation),
        pathText = truncateWithEllipsis(relativePath, pathTruncation),
        isCurrent = isCurrent,
    )
}
