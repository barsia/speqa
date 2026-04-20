package io.github.barsia.speqa.editor.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import io.github.barsia.speqa.SpeqaBundle
import io.github.barsia.speqa.parser.TestCaseParser
import io.github.barsia.speqa.parser.TestRunParser
import io.github.barsia.speqa.registry.SpeqaTagRegistry
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.datatransfer.StringSelection
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel

internal data class TagChipContextAction(
    val text: String,
    val icon: javax.swing.Icon? = null,
    val onSelect: () -> Unit,
)

internal enum class MetadataValueKind {
    TAG,
    ENVIRONMENT,
}

internal enum class MetadataSearchTarget {
    TEST_CASES,
    TEST_RUNS,
}

@Composable
internal fun rememberTestCaseMetadataActions(
    project: Project,
    kind: MetadataValueKind,
    onRemove: ((String) -> Unit)? = null,
): Triple<(String) -> Unit, (String) -> String, (String) -> List<TagChipContextAction>> {
    return remember(project, kind, onRemove) {
        val click: (String) -> Unit = { value ->
            showMetadataMatches(project, kind, MetadataSearchTarget.TEST_CASES, value)
        }
        val tooltipText = when (kind) {
            MetadataValueKind.TAG -> SpeqaBundle.message("metadata.tooltip.showTestCasesWithTag")
            MetadataValueKind.ENVIRONMENT -> SpeqaBundle.message("metadata.tooltip.showTestCasesWithEnvironment")
        }
        val tooltip: (String) -> String = { tooltipText }
        val menu: (String) -> List<TagChipContextAction> = { value ->
            buildList {
                add(
                    TagChipContextAction(
                        when (kind) {
                            MetadataValueKind.TAG -> SpeqaBundle.message("metadata.findTestCasesWithTag")
                            MetadataValueKind.ENVIRONMENT -> SpeqaBundle.message("metadata.findTestCasesWithEnvironment")
                        },
                    ) { showMetadataMatches(project, kind, MetadataSearchTarget.TEST_CASES, value) },
                )
                add(
                    TagChipContextAction(
                        when (kind) {
                            MetadataValueKind.TAG -> SpeqaBundle.message("metadata.copyTag")
                            MetadataValueKind.ENVIRONMENT -> SpeqaBundle.message("metadata.copyEnvironment")
                        },
                    ) {
                        CopyPasteManager.getInstance().setContents(StringSelection(value))
                    },
                )
                if (onRemove != null) {
                    add(
                        TagChipContextAction(SpeqaBundle.message("metadata.remove"), com.intellij.icons.AllIcons.Actions.GC) {
                            val message = when (kind) {
                                MetadataValueKind.TAG -> SpeqaBundle.message("metadata.removeTag.message", value)
                                MetadataValueKind.ENVIRONMENT -> SpeqaBundle.message("metadata.removeEnvironment.message", value)
                            }
                            val title = when (kind) {
                                MetadataValueKind.TAG -> SpeqaBundle.message("metadata.removeTag.title")
                                MetadataValueKind.ENVIRONMENT -> SpeqaBundle.message("metadata.removeEnvironment.title")
                            }
                            val result = Messages.showOkCancelDialog(
                                project,
                                message,
                                title,
                                Messages.getOkButton(),
                                Messages.getCancelButton(),
                                null,
                            )
                            if (result == Messages.OK) {
                                onRemove(value)
                            }
                        },
                    )
                }
            }
        }
        Triple(click, tooltip, menu)
    }
}

@Composable
internal fun rememberTestRunMetadataActions(
    project: Project,
    kind: MetadataValueKind,
    onRemove: ((String) -> Unit)? = null,
): Triple<(String) -> Unit, (String) -> String, (String) -> List<TagChipContextAction>> {
    return remember(project, kind, onRemove) {
        val click: (String) -> Unit = { value ->
            showMetadataMatches(project, kind, MetadataSearchTarget.TEST_RUNS, value)
        }
        val tooltipText = SpeqaBundle.message("metadata.tooltip.showTestRunsWithTag")
        val tooltip: (String) -> String = { tooltipText }
        val menu: (String) -> List<TagChipContextAction> = { value ->
            buildList {
                add(
                    TagChipContextAction(
                        when (kind) {
                            MetadataValueKind.TAG -> SpeqaBundle.message("metadata.findTestRunsWithTag")
                            MetadataValueKind.ENVIRONMENT -> SpeqaBundle.message("metadata.findTestRunsWithEnvironment")
                        },
                    ) { showMetadataMatches(project, kind, MetadataSearchTarget.TEST_RUNS, value) },
                )
                add(
                    TagChipContextAction(
                        when (kind) {
                            MetadataValueKind.TAG -> SpeqaBundle.message("metadata.findTestCasesWithTag")
                            MetadataValueKind.ENVIRONMENT -> SpeqaBundle.message("metadata.findTestCasesWithEnvironment")
                        },
                    ) {
                        showMetadataMatches(project, kind, MetadataSearchTarget.TEST_CASES, value)
                    },
                )
                add(
                    TagChipContextAction(
                        when (kind) {
                            MetadataValueKind.TAG -> SpeqaBundle.message("metadata.copyTag")
                            MetadataValueKind.ENVIRONMENT -> SpeqaBundle.message("metadata.copyEnvironment")
                        },
                    ) {
                        CopyPasteManager.getInstance().setContents(StringSelection(value))
                    },
                )
                if (onRemove != null) {
                    add(
                        TagChipContextAction(SpeqaBundle.message("metadata.remove"), com.intellij.icons.AllIcons.Actions.GC) {
                            val message = when (kind) {
                                MetadataValueKind.TAG -> SpeqaBundle.message("metadata.removeTag.message", value)
                                MetadataValueKind.ENVIRONMENT -> SpeqaBundle.message("metadata.removeEnvironment.message", value)
                            }
                            val title = when (kind) {
                                MetadataValueKind.TAG -> SpeqaBundle.message("metadata.removeTag.title")
                                MetadataValueKind.ENVIRONMENT -> SpeqaBundle.message("metadata.removeEnvironment.title")
                            }
                            val result = Messages.showOkCancelDialog(
                                project,
                                message,
                                title,
                                Messages.getOkButton(),
                                Messages.getCancelButton(),
                                null,
                            )
                            if (result == Messages.OK) {
                                onRemove(value)
                            }
                        },
                    )
                }
            }
        }
        Triple(click, tooltip, menu)
    }
}

private fun showMetadataMatches(
    project: Project,
    kind: MetadataValueKind,
    target: MetadataSearchTarget,
    value: String,
) {
    val registry = SpeqaTagRegistry.getInstance(project)
    val files = when (target) {
        MetadataSearchTarget.TEST_CASES -> when (kind) {
            MetadataValueKind.TAG -> registry.findTestCasesByTag(value)
            MetadataValueKind.ENVIRONMENT -> registry.findTestCasesByEnvironment(value)
        }
        MetadataSearchTarget.TEST_RUNS -> when (kind) {
            MetadataValueKind.TAG -> registry.findTestRunsByTag(value)
            MetadataValueKind.ENVIRONMENT -> registry.findTestRunsByEnvironment(value)
        }
    }
    if (files.isEmpty()) return

    val title = truncateWithEllipsis(
        when (target) {
        MetadataSearchTarget.TEST_CASES -> when (kind) {
            MetadataValueKind.TAG -> SpeqaBundle.message("metadata.popupTitle.testCases.tag", value)
            MetadataValueKind.ENVIRONMENT -> SpeqaBundle.message("metadata.popupTitle.testCases.environment", value)
        }
        MetadataSearchTarget.TEST_RUNS -> when (kind) {
            MetadataValueKind.TAG -> SpeqaBundle.message("metadata.popupTitle.testRuns.tag", value)
            MetadataValueKind.ENVIRONMENT -> SpeqaBundle.message("metadata.popupTitle.testRuns.environment", value)
        }
    },
        maxChars = 72,
    )
    val basePath = project.basePath
    val matches = files.map { file ->
        toIndexedFileMatch(project, file, basePath)
    }

    JBPopupFactory.getInstance()
        .createPopupChooserBuilder(matches)
        .setTitle(title)
        .setRenderer(MetadataResultRenderer())
        .setItemChosenCallback { match ->
            FileEditorManager.getInstance(project).openFile(match.file, true)
        }
        .setVisibleRowCount(8)
        .withFixedRendererSize(Dimension(JBUI.scale(440), JBUI.scale(52)))
        .createPopup()
        .showCenteredInCurrentWindow(project)
}

private data class IndexedFileMatch(
    val file: VirtualFile,
    val idText: String?,
    val titleText: String,
    val pathText: String,
    val isCurrent: Boolean,
)

private fun toIndexedFileMatch(project: Project, file: VirtualFile, basePath: String?): IndexedFileMatch {
    val relativePath = if (basePath != null) file.path.removePrefix("$basePath/") else file.path

    return if (file.name.endsWith(".tc.md")) {
        val testCase = runCatching { TestCaseParser.parse(file.inputStream.reader().use { it.readText() }) }.getOrNull()
        val idText = testCase?.id?.let { "TC-$it" }
        IndexedFileMatch(
            file = file,
            idText = idText,
            titleText = truncateWithEllipsis(testCase?.title.orEmpty().ifBlank { file.name }, 56),
            pathText = truncateWithEllipsis(relativePath, 72),
            isCurrent = FileEditorManager.getInstance(project).selectedFiles.any { it == file },
        )
    } else {
        val testRun = runCatching { TestRunParser.parse(file.inputStream.reader().use { it.readText() }) }.getOrNull()
        val idText = testRun?.id?.let { "TR-$it" }
        IndexedFileMatch(
            file = file,
            idText = idText,
            titleText = truncateWithEllipsis(testRun?.title.orEmpty().ifBlank { file.name }, 56),
            pathText = truncateWithEllipsis(relativePath, 72),
            isCurrent = FileEditorManager.getInstance(project).selectedFiles.any { it == file },
        )
    }
}

private class MetadataResultRenderer : javax.swing.ListCellRenderer<IndexedFileMatch> {
    override fun getListCellRendererComponent(
        list: JList<out IndexedFileMatch>,
        value: IndexedFileMatch,
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
        val title = JLabel(value.titleText).apply {
            foreground = if (isSelected) list.selectionForeground else list.foreground
        }
        if (!value.idText.isNullOrBlank()) {
            primaryRow.add(
                JLabel(value.idText).apply {
                    foreground = if (isSelected) list.selectionForeground else JBColor.GRAY
                },
                BorderLayout.WEST,
            )
        }
        primaryRow.add(title, BorderLayout.CENTER)
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

private fun truncateWithEllipsis(text: String, maxChars: Int): String {
    if (text.length <= maxChars) return text
    return text.take(maxChars - 1).trimEnd() + "…"
}
