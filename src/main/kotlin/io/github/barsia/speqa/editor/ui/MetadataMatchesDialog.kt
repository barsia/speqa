// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package io.github.barsia.speqa.editor.ui

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import io.github.barsia.speqa.SpeqaBundle
import io.github.barsia.speqa.editor.ui.chips.IndexedFileMatchDisplay
import io.github.barsia.speqa.editor.ui.chips.MetadataKind
import io.github.barsia.speqa.editor.ui.chips.MetadataMatchCellRenderer
import io.github.barsia.speqa.editor.ui.chips.MetadataScope
import io.github.barsia.speqa.editor.ui.chips.projectMatches
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.ListSelectionModel

/**
 * Modal dialog listing all test cases (or test runs) that carry a given tag
 * or environment value. Replaces the previous JBPopup-based
 * `showMetadataMatches` for the chip-click path. Centered in the IDE frame.
 */
class MetadataMatchesDialog(
    private val project: Project,
    private val scope: MetadataScope,
    private val kind: MetadataKind,
    private val value: String,
    private val candidates: List<VirtualFile>,
    private val onPick: (VirtualFile) -> Unit,
) : DialogWrapper(project, true) {

    private val matches: List<Pair<IndexedFileMatchDisplay, VirtualFile>> =
        projectMatches(candidates, project, lowerQuery = "")

    private val list: JBList<IndexedFileMatchDisplay> = object : JBList<IndexedFileMatchDisplay>(matches.map { it.first }) {
        override fun getScrollableTracksViewportWidth(): Boolean = true
    }.apply {
        selectionMode = ListSelectionModel.SINGLE_SELECTION
        cellRenderer = MetadataMatchCellRenderer()
        val currentFiles = FileEditorManager.getInstance(project).selectedFiles.toSet()
        val currentIdx = matches.indexOfFirst { it.second in currentFiles }
        selectedIndex = if (currentIdx >= 0) currentIdx else 0
        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2 && e.button == MouseEvent.BUTTON1) {
                    doOKAction()
                }
            }
        })
    }

    init {
        title = composeTitle()
        setOKButtonText(SpeqaBundle.message("metadata.matchesDialog.open"))
        isOKActionEnabled = matches.isNotEmpty()
        init()
        setSize(JBUI.scale(480), JBUI.scale(420))
    }

    private fun composeTitle(): String = when (scope) {
        MetadataScope.TEST_CASES -> when (kind) {
            MetadataKind.TAG -> SpeqaBundle.message("metadata.matchesDialog.testCases.tag", value)
            MetadataKind.ENVIRONMENT -> SpeqaBundle.message("metadata.matchesDialog.testCases.environment", value)
        }
        MetadataScope.TEST_RUNS -> when (kind) {
            MetadataKind.TAG -> SpeqaBundle.message("metadata.matchesDialog.testRuns.tag", value)
            MetadataKind.ENVIRONMENT -> SpeqaBundle.message("metadata.matchesDialog.testRuns.environment", value)
        }
    }

    override fun createCenterPanel(): JComponent {
        if (matches.isEmpty()) {
            return JPanel(BorderLayout()).apply {
                preferredSize = Dimension(JBUI.scale(440), JBUI.scale(360))
                add(
                    JLabel(SpeqaBundle.message("metadata.noMatches"), JLabel.CENTER).apply {
                        foreground = JBColor.GRAY
                    },
                    BorderLayout.CENTER,
                )
            }
        }
        return JBScrollPane(list).apply {
            border = JBUI.Borders.empty()
            horizontalScrollBarPolicy = JBScrollPane.HORIZONTAL_SCROLLBAR_NEVER
            preferredSize = Dimension(JBUI.scale(440), JBUI.scale(360))
        }
    }

    override fun doOKAction() {
        val idx = list.selectedIndex
        val picked = matches.getOrNull(idx)?.second
        super.doOKAction()
        if (picked != null) onPick(picked)
    }
}
