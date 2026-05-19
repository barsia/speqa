package io.github.barsia.speqa.editor.ui.primitives

import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import io.github.barsia.speqa.SpeqaBundle
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.html.HtmlGenerator
import org.intellij.markdown.parser.MarkdownParser
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Cursor
import java.awt.Dimension
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JEditorPane
import javax.swing.JPanel
import javax.swing.ScrollPaneConstants
import javax.swing.event.DocumentEvent
import javax.swing.event.HyperlinkEvent
import javax.swing.text.html.HTMLEditorKit

/**
 * Composite markdown view. Renders the source text as a stack of
 * segments: prose chunks and fenced code blocks. Each fenced block gets
 * a styled chrome (rounded chip, language label, copy button); prose
 * chunks render either as an HTML view (`isEditable = false`) or as a
 * plain editable [JBTextArea] (`isEditable = true`).
 *
 * When [isEditable] is true the user can type into and delete from any
 * segment. Changes are recomposed back to a single markdown source and
 * surfaced through [onTextChange]. List continuation, hyperlink
 * activation, and other "smart" Enter behaviours are deliberately NOT
 * applied here — this is meant to be the dumb live view, with the
 * dedicated editing toolbar added later.
 *
 * `setMarkdown` rebuilds the segment list. If any segment currently has
 * focus the rebuild is skipped (preserves caret position during
 * external refresh cycles).
 */
class MarkdownPane(
    private val isEditable: Boolean = false,
    private val onTextChange: ((String) -> Unit)? = null,
    private val placeholder: String = "",
) : JPanel() {

    private data class SegmentEntry(
        val component: JComponent,
        val getText: () -> String,
        val isCode: Boolean,
        val getLanguage: () -> String,
        val getIndent: () -> String = { "" },
    )

    private val segments = mutableListOf<SegmentEntry>()
    private var suppressTextChange = false
    private var currentMarkdown = ""

    init {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        isOpaque = false
    }

    fun setMarkdown(src: String) {
        if (anySegmentFocused()) return
        if (src == currentMarkdown && segments.isNotEmpty()) return
        suppressTextChange = true
        try {
            currentMarkdown = src
            removeAll()
            segments.clear()
            val parsed = splitFencedSegments(src)
            for ((index, segment) in parsed.withIndex()) {
                val entry = when (segment) {
                    is Segment.Prose -> buildProse(segment.markdown)
                    is Segment.Code -> buildCode(segment.language, segment.code, segment.indent)
                }
                entry.component.alignmentX = Component.LEFT_ALIGNMENT
                add(entry.component)
                segments += entry
                if (index < parsed.lastIndex) add(Box.createVerticalStrut(JBUI.scale(2)))
            }
            revalidate()
            repaint()
        } finally {
            suppressTextChange = false
        }
    }

    private fun anySegmentFocused(): Boolean =
        segments.any { it.component.hasFocusOwnerInside() }

    private fun JComponent.hasFocusOwnerInside(): Boolean {
        val owner = java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner ?: return false
        var c: java.awt.Component? = owner
        while (c != null) {
            if (c === this) return true
            c = c.parent
        }
        return false
    }

    private fun fireTextChange() {
        if (suppressTextChange) return
        val text = segments.joinToString("\n") { entry ->
            if (entry.isCode) {
                val lang = entry.getLanguage()
                val fence = if (lang.isNotBlank()) "```$lang" else "```"
                val indent = entry.getIndent()
                reassembleCodeSegment(indent, fence, entry.getText())
            } else {
                entry.getText()
            }
        }
        currentMarkdown = text
        onTextChange?.invoke(text)
    }

    // --- Prose ---------------------------------------------------------------

    private fun buildProse(markdown: String): SegmentEntry =
        if (isEditable) buildProseEditable(markdown) else buildProseHtml(markdown)

    private fun buildProseEditable(markdown: String): SegmentEntry {
        val placeholderText = this.placeholder
        val pane = object : javax.swing.JTextPane() {
            override fun getPreferredSize(): java.awt.Dimension {
                val natural = super.getPreferredSize()
                val ins = insets
                val rowH = getFontMetrics(font).height
                // Reserve at least one row's worth of height — even on
                // the very first layout pass where width is 0 — so an
                // empty document doesn't collapse to a 1-pixel sliver.
                val minH = rowH + ins.top + ins.bottom
                val w = width
                if (w <= 0) {
                    return java.awt.Dimension(natural.width, maxOf(natural.height, minH))
                }
                val ui = getUI() ?: return natural
                val rootView = ui.getRootView(this) ?: return natural
                val innerW = (w - ins.left - ins.right).coerceAtLeast(1).toFloat()
                rootView.setSize(innerW, Short.MAX_VALUE.toFloat())
                val viewH = rootView.getPreferredSpan(javax.swing.text.View.Y_AXIS).toInt()
                return java.awt.Dimension(w, maxOf(viewH + ins.top + ins.bottom, minH))
            }

            override fun getMinimumSize(): java.awt.Dimension {
                val ins = insets
                val rowH = getFontMetrics(font).height
                return java.awt.Dimension(0, rowH + ins.top + ins.bottom)
            }

            override fun getScrollableTracksViewportWidth(): Boolean = true

            // Inline placeholder when the document is empty AND the pane
            // is not focused, so the user can see what to type. Replaces
            // the JBTextArea.emptyText facility (JTextPane has no
            // built-in equivalent).
            override fun paintComponent(g: java.awt.Graphics) {
                super.paintComponent(g)
                if (placeholderText.isBlank()) return
                if (document.length != 0) return
                if (isFocusOwner) return
                val g2 = g.create() as java.awt.Graphics2D
                try {
                    g2.setRenderingHint(
                        java.awt.RenderingHints.KEY_TEXT_ANTIALIASING,
                        java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON,
                    )
                    g2.color = UIUtil.getInactiveTextColor()
                    g2.font = font
                    val fm = g2.fontMetrics
                    g2.drawString(placeholderText, insets.left, insets.top + fm.ascent)
                } finally {
                    g2.dispose()
                }
            }
        }.apply {
            font = JBFont.label()
            isEditable = true
            isOpaque = false
            background = null
            border = JBUI.Borders.empty()
            margin = java.awt.Insets(0, 0, 0, 0)
        }
        // Apply slight extra line spacing on every text mutation so
        // newly-inserted paragraphs inherit the spacing (StyledDocument
        // doesn't propagate `LineSpacing` to paragraphs created via
        // typing).
        val proseAttrs = javax.swing.text.SimpleAttributeSet().apply {
            javax.swing.text.StyleConstants.setLineSpacing(this, 0.25f)
        }
        fun applyLineSpacing() {
            val doc = pane.styledDocument
            doc.setParagraphAttributes(0, doc.length.coerceAtLeast(1), proseAttrs, false)
        }
        applyLineSpacing()

        // Force JTextPane to wrap to the container's actual width.
        // Without an enclosing JScrollPane, JTextPane never picks up the
        // viewport width from `getScrollableTracksViewportWidth`, so
        // long lines (or wide embedded chips) run past the right edge.
        // Setting the pane's own size to the new width on every resize
        // gives the ParagraphView the wrap-width it needs.
        var lastWidth = 0
        pane.addComponentListener(object : java.awt.event.ComponentAdapter() {
            override fun componentResized(e: java.awt.event.ComponentEvent) {
                if (pane.width != lastWidth && pane.width > 0) {
                    lastWidth = pane.width
                    pane.setSize(pane.width, Short.MAX_VALUE.toInt())
                    pane.revalidate()
                }
            }
        })
        // Programmatic content swaps (initial load + setMarkdown round-
        // trips) must NOT fire onChange — they would push the freshly
        // parsed text back to the document and bounce the native editor.
        var suppressFire = false

        fun rebuildContent(src: String) {
            suppressFire = true
            try {
                renderProseInto(pane, src)
                applyLineSpacing()
            } finally {
                suppressFire = false
            }
        }
        rebuildContent(markdown)

        // Re-style inline-code regions on every text mutation. Guard
        // against re-entry: setCharacterAttributes itself triggers
        // changedUpdate (already filtered out below), but a defensive
        // flag keeps any future expansion of the restyle work safe.
        var styling = false
        fun restyle() {
            if (styling) return
            styling = true
            try {
                applyInlineCodeStyling(pane.styledDocument, pane.text, pane.font)
            } finally {
                styling = false
            }
        }

        pane.document.addDocumentListener(object : javax.swing.event.DocumentListener {
            override fun insertUpdate(e: DocumentEvent) = onMutation()
            override fun removeUpdate(e: DocumentEvent) = onMutation()
            override fun changedUpdate(e: DocumentEvent) { /* attribute-only; skip */ }
            private fun onMutation() {
                if (suppressFire) return
                javax.swing.SwingUtilities.invokeLater {
                    restyle()
                    applyLineSpacing()
                }
                fireTextChange()
            }
        })
        // Auto-continue bullet / ordered lists on Enter.
        pane.addKeyListener(object : java.awt.event.KeyAdapter() {
            override fun keyPressed(e: java.awt.event.KeyEvent) {
                if (e.keyCode != java.awt.event.KeyEvent.VK_ENTER) return
                if (e.isShiftDown || e.isControlDown || e.isMetaDown || e.isAltDown) return
                val plain = readProseMarkdown(pane)
                val caret = pane.caretPosition
                val result = ListContinuation.onEnter(plain, caret) ?: return
                e.consume()
                rebuildContent(result.text)
                pane.caretPosition = result.cursor.coerceIn(0, pane.document.length)
                fireTextChange()
            }
        })

        return SegmentEntry(
            component = pane,
            getText = { readProseMarkdown(pane) },
            isCode = false,
            getLanguage = { "" },
        )
    }

    /**
     * Replace [pane]'s content with [src]: insert the raw markdown as
     * plain text and then apply character attributes to mark up inline
     * `` `code` `` spans (monospace + chip background + invisible
     * backticks). We keep the backticks in the document so the standard
     * text view handles line wrap natively — embedded JComponents would
     * be atomic and ParagraphView can't break them across lines.
     */
    private fun renderProseInto(pane: javax.swing.JTextPane, src: String) {
        val doc = pane.styledDocument
        doc.remove(0, doc.length)
        doc.insertString(0, src, null)
        applyInlineCodeStyling(doc, src, pane.font)
        pane.caretPosition = 0
    }

    /** Read the canonical markdown source straight from the document
     *  text. With the styled-attributes approach (no embedded chips)
     *  the document content IS the markdown source. */
    private fun readProseMarkdown(pane: javax.swing.JTextPane): String =
        pane.text.trimEnd('\n')

    /** Apply chip-style character attributes to inline `code` regions
     *  and "hide" the surrounding backticks by giving them the chip
     *  background colour as foreground + a near-zero font size. */
    private fun applyInlineCodeStyling(doc: javax.swing.text.StyledDocument, text: String, baseFont: java.awt.Font) {
        val prose = javax.swing.text.SimpleAttributeSet().apply {
            javax.swing.text.StyleConstants.setFontFamily(this, baseFont.family)
            javax.swing.text.StyleConstants.setFontSize(this, baseFont.size)
        }
        val code = javax.swing.text.SimpleAttributeSet().apply {
            javax.swing.text.StyleConstants.setFontFamily(this, baseFont.family)
            javax.swing.text.StyleConstants.setFontSize(this, baseFont.size)
            javax.swing.text.StyleConstants.setBackground(this, codeInlineBackground())
            javax.swing.text.StyleConstants.setForeground(this, codeInlineForeground())
        }
        val codeBg = codeInlineBackground()
        val hidden = javax.swing.text.SimpleAttributeSet().apply {
            javax.swing.text.StyleConstants.setFontFamily(this, baseFont.family)
            javax.swing.text.StyleConstants.setFontSize(this, 1)
            javax.swing.text.StyleConstants.setBackground(this, codeBg)
            javax.swing.text.StyleConstants.setForeground(this, codeBg)
        }
        doc.setCharacterAttributes(0, doc.length, prose, true)
        val pattern = Regex("`([^`\\n]+)`")
        for (m in pattern.findAll(text)) {
            doc.setCharacterAttributes(m.range.first, m.range.last - m.range.first + 1, code, true)
            doc.setCharacterAttributes(m.range.first, 1, hidden, true)
            doc.setCharacterAttributes(m.range.last, 1, hidden, true)
        }
    }


    private fun buildProseHtml(markdown: String): SegmentEntry {
        val pane = JEditorPane().apply {
            contentType = "text/html"
            @Suppress("DEPRECATION")
            editorKit = UIUtil.getHTMLEditorKit()
            font = JBFont.label()
            isEditable = false
            isOpaque = false
            applyHtmlColors(this)
            text = rewriteInlineCode(markdownToHtml(markdown))
            caretPosition = 0
            addHyperlinkListener { event ->
                if (event.eventType == HyperlinkEvent.EventType.ACTIVATED) {
                    val url = event.description ?: event.url?.toString() ?: return@addHyperlinkListener
                    if (url.matches(Regex("^https?://.*"))) BrowserUtil.browse(url)
                }
            }
        }
        return SegmentEntry(pane, { markdown }, isCode = false, getLanguage = { "" })
    }

    private fun applyHtmlColors(pane: JEditorPane) {
        val fg = JBColor.foreground()
        pane.foreground = fg
        val kit = pane.editorKit as? HTMLEditorKit ?: return
        val hex = hex(fg)
        val family = pane.font.family
        val size = pane.font.size
        kit.styleSheet.addRule(
            "body { color: $hex; font-family: $family; font-size: ${size}px; margin: 0; padding: 0; }",
        )
        kit.styleSheet.addRule("p { margin: 0; padding: 0; }")
        kit.styleSheet.addRule(
            ".speqa-codeinline { background-color: ${hex(codeInlineBackground())}; " +
                "color: ${hex(codeInlineForeground())}; padding: 1px 3px; " +
                "border: 1px solid ${hex(codeBlockBorder())}; }",
        )
    }

    private fun rewriteInlineCode(html: String): String {
        val pattern = Regex("<code[^>]*>(.*?)</code>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        return pattern.replace(html) { m ->
            "<span class=\"speqa-codeinline\">${m.groupValues[1]}</span>"
        }
    }

    // --- Code block ----------------------------------------------------------

    private fun buildCode(language: String, code: String, indent: String): SegmentEntry {
        val codeFg = codeInlineForeground()
        val codeBg = codeBlockBackground()
        val borderColor = codeBlockBorder()
        val gutterFg = UIUtil.getContextHelpForeground()

        val area = JBTextArea(code).apply {
            isEditable = this@MarkdownPane.isEditable
            lineWrap = false
            font = JBFont.label()
            foreground = codeFg
            background = codeBg
            border = JBUI.Borders.empty(4, 10, 8, 10)
            margin = java.awt.Insets(0, 0, 0, 0)
            document.addDocumentListener(object : DocumentAdapter() {
                override fun textChanged(e: DocumentEvent) = fireTextChange()
            })
        }

        val scrollBarThickness = JBUI.scale(8)
        val scroll = JBScrollPane(area).apply {
            horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED
            verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER
            this.border = BorderFactory.createEmptyBorder()
            viewport.background = codeBg
            background = codeBg
            horizontalScrollBar.preferredSize = Dimension(
                horizontalScrollBar.preferredSize.width,
                scrollBarThickness,
            )
            horizontalScrollBar.isOpaque = true
            horizontalScrollBar.background = codeBg
            preferredSize = Dimension(0, area.preferredSize.height + scrollBarThickness)
        }

        // Editable language field: a borderless transparent JBTextField
        // styled like the previous static label. Empty text is shown via
        // emptyText placeholder. Document changes flow through
        // fireTextChange so the recomposed markdown picks up the new
        // fence language immediately.
        val langField = com.intellij.ui.components.JBTextField(language).apply {
            // Body-size font — matches the Copy icon's visual scale so
            // the two read as the same row of UI chrome.
            font = JBFont.label()
            foreground = gutterFg
            background = codeBg
            isOpaque = false
            border = JBUI.Borders.empty()
            margin = java.awt.Insets(0, 0, 0, 0)
            columns = 8
            emptyText.text = SpeqaBundle.message("toolbar.markdown.codeBlock.languagePlaceholder")
            cursor = Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR)
            alignmentY = Component.CENTER_ALIGNMENT
            document.addDocumentListener(object : DocumentAdapter() {
                override fun textChanged(e: DocumentEvent) {
                    if (this@MarkdownPane.isEditable) fireTextChange()
                }
            })
            if (!this@MarkdownPane.isEditable) isEditable = false
        }

        // BoxLayout X_AXIS lets us pin both child components to the
        // vertical centre (via `alignmentY = CENTER_ALIGNMENT`); a plain
        // BorderLayout would still leave the JBTextField's text baseline
        // a couple of pixels off the icon's center because JBTextField's
        // intrinsic height is taller than the icon's.
        val header = JPanel().apply {
            isOpaque = true
            background = codeBg
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, borderColor),
                JBUI.Borders.empty(2, 10, 2, 10),
            )
            val mutedColor = speqaMutedIconColor()
            val accentColor = JBColor.namedColor("Link.activeForeground", JBColor.BLUE)
            val baseCopy = com.intellij.util.IconUtil.scale(AllIcons.Actions.Copy, null, 0.85f)
            val baseCheck = com.intellij.util.IconUtil.scale(AllIcons.Actions.Checked, null, 0.85f)
            val mutedCopy = replaceIconColor(baseCopy, mutedColor)
            val accentCopy = replaceIconColor(baseCopy, accentColor)
            val accentCheck = replaceIconColor(baseCheck, accentColor)

            val copyLabel = JBLabel(mutedCopy).apply {
                toolTipText = SpeqaBundle.message("toolbar.markdown.copyCode.tooltip")
                cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                alignmentY = Component.CENTER_ALIGNMENT
            }
            var hovering = false
            var checked = false
            fun refreshCopyIcon() {
                copyLabel.icon = when {
                    checked -> accentCheck
                    hovering -> accentCopy
                    else -> mutedCopy
                }
            }
            copyLabel.addMouseListener(object : MouseAdapter() {
                override fun mouseEntered(e: MouseEvent) { hovering = true; refreshCopyIcon() }
                override fun mouseExited(e: MouseEvent) { hovering = false; refreshCopyIcon() }
                override fun mouseClicked(e: MouseEvent) {
                    e.consume()
                    val sel = java.awt.datatransfer.StringSelection(area.text)
                    java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(sel, sel)
                    checked = true
                    refreshCopyIcon()
                    javax.swing.Timer(1000) {
                        checked = false
                        refreshCopyIcon()
                    }.apply { isRepeats = false }.start()
                }
            })
            add(langField)
            add(Box.createHorizontalGlue())
            add(copyLabel)
        }

        // Rounded outline + matching rounded background. JPanel paints
        // a rectangular background regardless of border shape, so we
        // disable opaque and paint the rounded fill manually in
        // paintComponent — same trick we use for the inline chip.
        val blockArc = JBUI.scale(6)
        val container = object : JPanel(BorderLayout()) {
            override fun paintComponent(g: java.awt.Graphics) {
                val g2 = g.create() as java.awt.Graphics2D
                try {
                    g2.setRenderingHint(
                        java.awt.RenderingHints.KEY_ANTIALIASING,
                        java.awt.RenderingHints.VALUE_ANTIALIAS_ON,
                    )
                    g2.color = codeBg
                    g2.fillRoundRect(0, 0, width - 1, height - 1, blockArc, blockArc)
                } finally {
                    g2.dispose()
                }
                super.paintComponent(g)
            }
        }.apply {
            isOpaque = false
            this.border = com.intellij.ui.RoundedLineBorder(borderColor, blockArc, 1)
            add(header, BorderLayout.NORTH)
            add(scroll, BorderLayout.CENTER)
        }
        return SegmentEntry(
            component = container,
            getText = { area.text },
            isCode = true,
            getLanguage = { langField.text.trim() },
            getIndent = { indent },
        )
    }

    // --- Colours -------------------------------------------------------------

    private fun hex(c: Color): String = "#%02x%02x%02x".format(c.red, c.green, c.blue)
    private fun scheme() = EditorColorsManager.getInstance().globalScheme
    private fun markdownAttr(name: String) = scheme().getAttributes(TextAttributesKey.find(name))

    private fun codeInlineBackground(): Color =
        markdownAttr("MARKDOWN_CODE_SPAN")?.backgroundColor
            ?: com.intellij.ui.ColorUtil.mix(
                JBColor.PanelBackground,
                JBColor.namedColor("Component.borderColor", JBColor.border()),
                0.35,
            )

    private fun codeInlineForeground(): Color =
        markdownAttr("MARKDOWN_CODE_SPAN")?.foregroundColor
            ?: markdownAttr("MARKDOWN_CODE_FENCE")?.foregroundColor
            ?: JBColor.foreground()

    private fun codeBlockBorder(): Color =
        JBColor.namedColor("Component.borderColor", JBColor.border())

    private fun codeBlockBackground(): Color =
        markdownAttr("MARKDOWN_CODE_FENCE")?.backgroundColor
            ?: markdownAttr("MARKDOWN_CODE_BLOCK")?.backgroundColor
            ?: com.intellij.ui.ColorUtil.mix(
                JBColor.PanelBackground,
                JBColor.namedColor("Component.borderColor", JBColor.border()),
                0.22,
            )
}

// ---- Segment splitting -----------------------------------------------------

internal sealed interface Segment {
    data class Prose(val markdown: String) : Segment
    data class Code(val language: String, val code: String, val indent: String = "") : Segment
}

internal val FENCED_BLOCK = Regex(
    "(?m)^([ \\t]*)```([^\\n]*)\\n([\\s\\S]*?)(?:\\n[ \\t]*```|\\z)",
)

internal fun splitFencedSegments(src: String): List<Segment> {
    val result = mutableListOf<Segment>()
    var cursor = 0
    for (match in FENCED_BLOCK.findAll(src)) {
        if (match.range.first > cursor) {
            val prose = src.substring(cursor, match.range.first).trim('\n')
            if (prose.isNotBlank()) result += Segment.Prose(prose)
        }
        val indent = match.groupValues[1]
        val language = match.groupValues[2].trim()
        val rawBody = match.groupValues[3]
        val body = if (indent.isEmpty()) {
            rawBody
        } else {
            rawBody.lineSequence()
                .map { if (it.startsWith(indent)) it.removePrefix(indent) else it }
                .joinToString("\n")
        }
        result += Segment.Code(language, body, indent)
        cursor = match.range.last + 1
    }
    if (cursor < src.length) {
        val tail = src.substring(cursor).trim('\n')
        if (tail.isNotBlank()) result += Segment.Prose(tail)
    }
    if (result.isEmpty()) result += Segment.Prose(src)
    return result
}

internal fun reassembleCodeSegment(indent: String, fence: String, body: String): String {
    val prefixed = body.lineSequence().joinToString("\n") { line ->
        if (line.isEmpty() || line.startsWith(indent)) line else indent + line
    }
    return indent + fence + "\n" + prefixed + "\n" + indent + "```"
}

// ---- Markdown -> HTML helpers ----------------------------------------------

internal fun markdownToHtml(src: String): String {
    val flavour = GFMFlavourDescriptor()
    val ast = MarkdownParser(flavour).buildMarkdownTreeFromString(src)
    val raw = HtmlGenerator(src, ast, flavour).generateHtml()
    return flattenLists(raw)
}

private val OL_BLOCK = Regex("<ol[^>]*>(.*?)</ol>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
private val UL_BLOCK = Regex("<ul[^>]*>(.*?)</ul>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
private val LI_ITEM = Regex("<li[^>]*>(.*?)</li>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))

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
        injectPrefix(m.groupValues[1].trim(), prefix)
    }.joinToString("")
}

private val LEADING_P = Regex(
    "^\\s*<p[^>]*>(.*?)</p>(.*)$",
    setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
)
private val LEADING_BLOCK = Regex(
    "^\\s*<(pre|div|ul|ol|table|blockquote|h[1-6])\\b",
    RegexOption.IGNORE_CASE,
)

private fun injectPrefix(body: String, prefix: String): String {
    val match = LEADING_P.matchEntire(body)
    if (match != null) {
        return "<p>$prefix${match.groupValues[1]}</p>${match.groupValues[2]}"
    }
    if (LEADING_BLOCK.containsMatchIn(body)) return "<p>$prefix</p>$body"
    return "<p>$prefix$body</p>"
}
