// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package io.github.barsia.speqa.editor.ui.chips

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import io.github.barsia.speqa.SpeqaBundle
import io.github.barsia.speqa.parser.TestCaseParser
import io.github.barsia.speqa.parser.TestRunParser
import java.awt.BorderLayout
import java.awt.Component
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListCellRenderer

/**
 * Projects a list of [VirtualFile] candidates into renderer-friendly
 * [IndexedFileMatchDisplay] entries paired with their original file. Reusable
 * across the legacy `JBPopup` path (kept temporarily) and the new
 * `MetadataMatchesDialog`.
 *
 * Filters out files whose title and file name do not contain [lowerQuery]
 * (case-insensitive). Empty query keeps all candidates.
 */
fun projectMatches(
    candidates: List<VirtualFile>,
    project: Project,
    lowerQuery: String,
): List<Pair<IndexedFileMatchDisplay, VirtualFile>> {
    val basePath = project.basePath
    val currentFiles = FileEditorManager.getInstance(project).selectedFiles.toSet()
    return candidates
        .asSequence()
        .filter { it.isValid }
        .mapNotNull { file -> toDisplay(file, basePath, currentFiles, lowerQuery) }
        .toList()
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
        val run = runCatching {
            TestRunParser.parse(file.inputStream.reader().use { it.readText() })
        }.getOrNull()
        idText = run?.id?.let { "TR-$it" }
        titleRaw = run?.title.orEmpty()
    } else {
        val tc = runCatching {
            TestCaseParser.parse(file.inputStream.reader().use { it.readText() })
        }.getOrNull()
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

/**
 * Cell renderer for `JBList<IndexedFileMatchDisplay>`. Mirrors the layout used
 * by the legacy popup: a two-row card with an optional ID prefix, the
 * test-case / run title (truncated), and the relative path on the secondary
 * row; a "(current)" label appears at the right when the row points to a
 * currently-open file.
 */
class MetadataMatchCellRenderer : ListCellRenderer<IndexedFileMatchDisplay> {
    override fun getListCellRendererComponent(
        list: JList<out IndexedFileMatchDisplay>,
        value: IndexedFileMatchDisplay,
        index: Int,
        isSelected: Boolean,
        cellHasFocus: Boolean,
    ): Component {
        // Returns width = 0 so JList's layout state never picks up a wider
        // "natural" width from a long title row. Combined with
        // getScrollableTracksViewportWidth=true on the list, every cell is
        // painted at viewport width; the title JLabel inside then truncates
        // tightly to the available CENTER region instead of being measured
        // against an over-wide cell box (which previously left empty space
        // to the right of the ellipsis).
        val panel = object : JPanel(BorderLayout(JBUI.scale(12), 0)) {
            override fun getPreferredSize(): java.awt.Dimension {
                val natural = super.getPreferredSize()
                return java.awt.Dimension(0, natural.height)
            }
        }.apply {
            border = BorderFactory.createEmptyBorder(
                JBUI.scale(8), JBUI.scale(12), JBUI.scale(8), JBUI.scale(12)
            )
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
                toolTipText = value.titleText
                minimumSize = java.awt.Dimension(0, preferredSize.height)
            },
            BorderLayout.CENTER,
        )
        val secondary = JLabel(value.pathText).apply {
            foreground = if (isSelected) list.selectionForeground else JBColor.GRAY
            alignmentX = Component.LEFT_ALIGNMENT
            toolTipText = value.pathText
            minimumSize = java.awt.Dimension(0, preferredSize.height)
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
