package io.github.barsia.speqa.editor.ui.chips

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.JBColor
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import io.github.barsia.speqa.SpeqaBundle
import io.github.barsia.speqa.parser.TestCaseParser
import io.github.barsia.speqa.parser.TestRunParser
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.Point
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListCellRenderer
import javax.swing.ListSelectionModel

/**
 * File-match picker popup. Renders candidate `.tc.md` / `.tr.md` files with the
 * pure [IndexedFileMatchDisplay] projection, anchors under the supplied
 * component, and invokes [onPick] on keyboard / mouse selection.
 *
 * Matching against a free-form [query] is a simple case-insensitive substring
 * check over the file name and (when resolvable) the test case / run title.
 * Fuzzy ranking and highlighted match spans are deferred — the rest of the
 * contract (renderer shape, current-file marking,
 * keyboard/mouse selection) matches the old UI.
 */
fun showMetadataMatches(
    anchor: Component,
    project: Project,
    query: String,
    candidates: List<VirtualFile>,
    onPick: (VirtualFile) -> Unit,
) {
    val basePath = project.basePath
    val lowerQuery = query.trim().lowercase()
    val currentFiles = FileEditorManager.getInstance(project).selectedFiles.toSet()
    val matches = candidates
        .asSequence()
        .filter { it.isValid }
        .mapNotNull { file -> toDisplay(file, basePath, currentFiles, lowerQuery) }
        .toList()

    lateinit var popupRef: com.intellij.openapi.ui.popup.JBPopup
    val pickAndClose: (VirtualFile) -> Unit = { file ->
        popupRef.cancel()
        onPick(file)
    }

    val content: JPanel = if (matches.isEmpty()) {
        JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(8, 12)
            add(
                JLabel(SpeqaBundle.message("metadata.noMatches")).apply {
                    foreground = JBColor.GRAY
                },
                BorderLayout.CENTER,
            )
            preferredSize = Dimension(JBUI.scale(320), JBUI.scale(36))
        }
    } else {
        val list = JBList(matches.map { it.first }).apply {
            selectionMode = ListSelectionModel.SINGLE_SELECTION
            visibleRowCount = minOf(8, matches.size)
            cellRenderer = MatchRenderer()
            selectedIndex = 0
        }
        val pickAt: (Int) -> Unit = { idx ->
            matches.getOrNull(idx)?.second?.let(pickAndClose)
        }
        list.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if (e.keyCode == KeyEvent.VK_ENTER) {
                    pickAt(list.selectedIndex); e.consume()
                }
            }
        })
        list.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 1 && e.button == MouseEvent.BUTTON1) {
                    val idx = list.locationToIndex(e.point)
                    if (idx >= 0) pickAt(idx)
                }
            }
        })
        JPanel(BorderLayout()).apply {
            add(
                JBScrollPane(list).apply { border = JBUI.Borders.empty() },
                BorderLayout.CENTER,
            )
            preferredSize = Dimension(JBUI.scale(460), JBUI.scale(52 * minOf(8, matches.size) + 4))
        }
    }

    popupRef = JBPopupFactory.getInstance()
        .createComponentPopupBuilder(content, null)
        .setRequestFocus(matches.isNotEmpty())
        .setFocusable(matches.isNotEmpty())
        .setCancelOnClickOutside(true)
        .setCancelOnWindowDeactivation(true)
        .setResizable(false)
        .setMovable(false)
        .createPopup()
    popupRef.show(RelativePoint(anchor, Point(0, anchor.height + JBUI.scale(2))))
}

private fun toDisplay(
    file: VirtualFile,
    basePath: String?,
    currentFiles: Set<VirtualFile>,
    lowerQuery: String,
): Pair<IndexedFileMatchDisplay, VirtualFile>? {
    val relativePath = if (basePath != null) file.path.removePrefix("$basePath/") else file.path
    val isCurrent = file in currentFiles
    val isTestRun = file.name.endsWith(".tr.md")
    val idText: String?
    val titleRaw: String
    if (isTestRun) {
        val run = runCatching { TestRunParser.parse(file.inputStream.reader().use { it.readText() }) }.getOrNull()
        idText = run?.id?.let { "TR-$it" }
        titleRaw = run?.title.orEmpty()
    } else {
        val tc = runCatching { TestCaseParser.parse(file.inputStream.reader().use { it.readText() }) }.getOrNull()
        idText = tc?.id?.let { "TC-$it" }
        titleRaw = tc?.title.orEmpty()
    }
    val haystack = (titleRaw + " " + file.name).lowercase()
    if (lowerQuery.isNotEmpty() && !haystack.contains(lowerQuery)) return null
    val display = indexedFileMatchFrom(
        idText = idText,
        titleRaw = titleRaw,
        fallbackTitle = file.name,
        relativePath = relativePath,
        isCurrent = isCurrent,
    )
    return display to file
}

private class MatchRenderer : ListCellRenderer<IndexedFileMatchDisplay> {
    override fun getListCellRendererComponent(
        list: JList<out IndexedFileMatchDisplay>,
        value: IndexedFileMatchDisplay,
        index: Int,
        isSelected: Boolean,
        cellHasFocus: Boolean,
    ): Component {
        val panel = JPanel(BorderLayout(JBUI.scale(12), 0)).apply {
            border = BorderFactory.createEmptyBorder(JBUI.scale(8), JBUI.scale(12), JBUI.scale(8), JBUI.scale(12))
            isOpaque = true
            background = if (isSelected) list.selectionBackground else list.background
        }
        val textPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
        }
        val primaryRow = JPanel(BorderLayout(JBUI.scale(8), 0)).apply {
            isOpaque = false
            alignmentX = Component.LEFT_ALIGNMENT
        }
        if (!value.idText.isNullOrBlank()) {
            primaryRow.add(
                JLabel(value.idText).apply {
                    foreground = if (isSelected) list.selectionForeground else JBColor.GRAY
                },
                BorderLayout.WEST,
            )
        }
        primaryRow.add(
            JLabel(value.titleText).apply {
                foreground = if (isSelected) list.selectionForeground else list.foreground
            },
            BorderLayout.CENTER,
        )
        val secondary = JLabel(value.pathText).apply {
            foreground = if (isSelected) list.selectionForeground else JBColor.GRAY
            alignmentX = Component.LEFT_ALIGNMENT
        }
        textPanel.add(primaryRow)
        textPanel.add(secondary)
        panel.add(textPanel, BorderLayout.CENTER)
        if (value.isCurrent) {
            panel.add(
                JLabel(SpeqaBundle.message("metadata.current")).apply {
                    foreground = if (isSelected) list.selectionForeground else JBColor.GRAY
                },
                BorderLayout.EAST,
            )
        }
        return panel
    }
}
