package io.github.barsia.speqa.editor.ui.primitives

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.util.ui.UIUtil
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.html.HtmlGenerator
import org.intellij.markdown.parser.MarkdownParser
import java.awt.Color
import javax.swing.JEditorPane
import javax.swing.event.HyperlinkEvent
import javax.swing.text.html.HTMLEditorKit

// JEditorPane + UIUtil.getHTMLEditorKit rather than JBHtmlPane: the latter's public
// constructors are not stable across the 253-263 target range.
class MarkdownReadOnlyPane : JEditorPane() {
    init {
        contentType = "text/html"
        editorKit = UIUtil.getHTMLEditorKit()
        isEditable = false
        isOpaque = false
        applyIdeColors()
        addHyperlinkListener { event ->
            if (event.eventType == HyperlinkEvent.EventType.ACTIVATED) {
                val url = event.description ?: event.url?.toString() ?: return@addHyperlinkListener
                if (url.matches(Regex("^https?://.*"))) {
                    BrowserUtil.browse(url)
                }
            }
        }
    }

    fun setMarkdown(src: String) {
        text = markdownToHtml(src)
        caretPosition = 0
    }

    private fun applyIdeColors() {
        val scheme = EditorColorsManager.getInstance().globalScheme
        val fg: Color = scheme.defaultForeground
        foreground = fg
        val kit = editorKit as? HTMLEditorKit ?: return
        val hex = "#%02x%02x%02x".format(fg.red, fg.green, fg.blue)
        val family = font.family
        val size = font.size
        // margin:0 + padding:0 so the read-mode text starts at the same
        // baseline as the edit-mode JBTextArea — otherwise the default
        // HTMLEditorKit body margins shift content downward on read→edit
        // toggle, visually "jumping" to a different position.
        //
        // font-size MUST be in px, not pt: `font.size` is an int-pixel value
        // and pt rendering enlarges it (1pt ~ 1.33px at 96dpi) which is why
        // read mode previously looked bigger than edit mode.
        kit.styleSheet.addRule(
            "body { color: $hex; font-family: $family; font-size: ${size}px; margin: 0; padding: 0; }"
        )
        kit.styleSheet.addRule("p { margin: 0; padding: 0; }")
        // ol/ul are rewritten to plain <p> lines with literal "N." / "-"
        // prefixes in markdownToHtml (see below), so no list CSS needed.
        // This keeps read-mode text at x=0 inside the content box, matching
        // the raw markdown position in the edit-mode JBTextArea — no
        // horizontal jump when toggling read ↔ edit on body blocks.
        kit.styleSheet.addRule("code { font-family: monospace; }")
        kit.styleSheet.addRule(".user-del { text-decoration: line-through; }")
    }
}

internal fun markdownToHtml(src: String): String {
    val flavour = GFMFlavourDescriptor()
    val ast = MarkdownParser(flavour).buildMarkdownTreeFromString(src)
    val raw = HtmlGenerator(src, ast, flavour).generateHtml()
    return flattenLists(raw)
}

private val OL_BLOCK = Regex("<ol[^>]*>(.*?)</ol>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
private val UL_BLOCK = Regex("<ul[^>]*>(.*?)</ul>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
private val LI_ITEM = Regex("<li[^>]*>(.*?)</li>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))

/**
 * Rewrite `<ol>`/`<ul>` blocks as plain `<p>` paragraphs with literal "N."
 * or "-" prefixes. HTMLEditorKit renders list markers in the padding-left
 * area, which always offsets text from x=0. Collapsing lists into
 * paragraphs makes rendered text line up horizontally with the same raw
 * markdown shown by the edit-mode `JBTextArea`, eliminating the visible
 * left-shift users saw when toggling read ↔ edit on body blocks.
 *
 * Nested lists are handled recursively. Non-`<li>` fragments inside a
 * list block (whitespace, comments) are preserved.
 */
private fun flattenLists(html: String): String {
    var prev: String
    var current = html
    do {
        prev = current
        current = OL_BLOCK.replace(current) { match -> renumber(match.groupValues[1], ordered = true) }
        current = UL_BLOCK.replace(current) { match -> renumber(match.groupValues[1], ordered = false) }
    } while (current != prev)
    return current
}

private fun renumber(body: String, ordered: Boolean): String {
    val parts = LI_ITEM.findAll(body).toList()
    if (parts.isEmpty()) return body
    return parts.mapIndexed { i, m ->
        val prefix = if (ordered) "${i + 1}. " else "- "
        "<p>$prefix${m.groupValues[1].trim()}</p>"
    }.joinToString("")
}
