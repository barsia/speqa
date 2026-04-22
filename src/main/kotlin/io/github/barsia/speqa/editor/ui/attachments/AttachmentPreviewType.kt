package io.github.barsia.speqa.editor.ui.attachments

/**
 * Pure classifier for hover-preview rendering of an attachment path.
 *
 *  - [IMAGE] — raster formats supported by `ImageIO`, SVG via `ImageLoader.loadFromUrl`, plus the common web set.
 *  - [MARKDOWN] — `.md`, `.tc.md`, `.tr.md` (SpeQA-native formats).
 *  - [TEXT] — any other extension commonly safe to render as plain text.
 *  - [OTHER] — unknown/binary types; preview shows metadata only.
 */
internal enum class AttachmentPreviewType { IMAGE, MARKDOWN, TEXT, OTHER }

private val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "gif", "webp", "bmp", "ico", "svg")

private val MARKDOWN_EXTENSIONS = setOf("md")

private val TEXT_EXTENSIONS = setOf(
    "txt", "log", "csv", "tsv", "json", "xml", "yaml", "yml", "properties",
    "ini", "toml", "html", "htm", "css", "js", "ts", "kt", "java", "py",
    "rb", "go", "rs", "sh", "bat", "ps1", "sql",
)

internal fun previewTypeFor(path: String): AttachmentPreviewType {
    val name = path.substringAfterLast('/')
    val lower = name.lowercase()
    // Treat compound suffixes first.
    if (lower.endsWith(".tc.md") || lower.endsWith(".tr.md") || lower.endsWith(".md")) {
        return AttachmentPreviewType.MARKDOWN
    }
    val ext = lower.substringAfterLast('.', "")
    if (ext.isEmpty()) return AttachmentPreviewType.OTHER
    return when (ext) {
        in IMAGE_EXTENSIONS -> AttachmentPreviewType.IMAGE
        in MARKDOWN_EXTENSIONS -> AttachmentPreviewType.MARKDOWN
        in TEXT_EXTENSIONS -> AttachmentPreviewType.TEXT
        else -> AttachmentPreviewType.OTHER
    }
}
