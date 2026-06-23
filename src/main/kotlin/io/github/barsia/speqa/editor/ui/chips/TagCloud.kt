// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package io.github.barsia.speqa.editor.ui.chips

import com.intellij.icons.AllIcons
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.ui.JBUI
import io.github.barsia.speqa.SpeqaBundle
import io.github.barsia.speqa.editor.ui.MetadataMatchesDialog
import io.github.barsia.speqa.editor.ui.primitives.DeleteFocusRestorer
import io.github.barsia.speqa.editor.ui.primitives.WrapLayout
import io.github.barsia.speqa.editor.ui.primitives.speqaIconButton
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Flow-layout tag cloud with inline add button. The add button opens a text
 * field (Enter commits, Escape cancels) with autocomplete. Clicking an
 * existing tag opens [MetadataMatchesDialog] listing all test cases / runs
 * carrying that tag. Editing a tag is triggered by clicking the pencil icon
 * inside the chip (via [onEditValue]).
 */
class TagCloud(
    private val coloredChips: Boolean = false,
    private val onActivate: (String) -> Unit = {},
    private val onAdd: (String) -> Unit = {},
    private val onRemove: (String) -> Unit = {},
    private val onEditValue: (String) -> Unit = {},
    private val metadataScope: MetadataScope = MetadataScope.TEST_CASES,
    private val metadataKind: MetadataKind = MetadataKind.TAG,
    private val metadataProject: Project? = null,
    private val hideAddButton: Boolean = false,
) : JPanel(WrapLayout(FlowLayout.LEFT, JBUI.scale(4), JBUI.scale(2), gapAround = false)) {

    private var tags: List<String> = emptyList()
    private var allKnownTagsSupplier: () -> Set<String> = { emptySet() }
    private val chips = mutableListOf<TagChip>()
    private val addButton: JComponent = speqaIconButton(
        icon = AllIcons.General.Add,
        tooltip = SpeqaBundle.message("tagCloud.addTag"),
        onAction = { startAdd() },
    )
    private val restorer = DeleteFocusRestorer(
        itemProvider = { chips.getOrNull(it) },
        addButton = addButton,
    )

    private var lastWidthForRelayout: Int = -1
    // Remembers the one-row chip height so we can clamp the empty-state height
    // to the same value and prevent a visual jump when the last tag is deleted.
    private var minPreferredHeight = 0

    override fun getPreferredSize(): Dimension {
        val d = super.getPreferredSize()
        if (tags.isNotEmpty()) minPreferredHeight = d.height
        return if (tags.isEmpty() && minPreferredHeight > 0)
            Dimension(d.width, maxOf(d.height, minPreferredHeight))
        else
            d
    }

    init {
        isOpaque = false
        // Render the initial empty-state ("No tags" / "No environments")
        // immediately. `TestCasePanel.updateFrom` only calls `setTags(...)`
        // when the value changes, so a test case that starts with no tags
        // (null in YAML) would otherwise leave the cloud blank forever.
        rebuild()
        // WrapLayout.preferredLayoutSize() reads target.size.width to decide
        // how many rows the chips need. The first layout pass runs with
        // width==0, so a wrapped row is silently dropped from the reported
        // height and the parent BoxLayout allocates only one row's worth of
        // height -> the second chip is clipped. Once bounds are set, force
        // another revalidate so the layout re-queries with the real width.
        addComponentListener(object : java.awt.event.ComponentAdapter() {
            override fun componentResized(e: java.awt.event.ComponentEvent) {
                if (width > 0 && width != lastWidthForRelayout) {
                    lastWidthForRelayout = width
                    revalidate()
                }
            }
        })
    }

    fun startAdd() {
        // When `hideAddButton` is true the internal add button is never
        // attached to the component tree, so `addButton.isShowing` is
        // false and `RelativePoint.getCenterOf(addButton)` falls back to
        // a bogus origin — the popup ends up at screen (0,0), often on
        // the wrong monitor. Anchor to the cloud itself in that case.
        val anchorComponent: JComponent = if (addButton.isShowing) addButton else this
        val popup = AddTagPopup(
            anchor = anchorComponent,
            allKnown = { allKnownTagsSupplier() },
            currentSelection = { tags.toSet() },
            onPick = { picked ->
                if (picked.isNotEmpty() && tags.none { it.equals(picked, ignoreCase = true) }) {
                    onAdd(picked)
                }
            },
        )
        popup.show()
        // The registry scans the project asynchronously on first access; if
        // the popup opens before the scan finishes, `allKnown` returns an
        // empty set. Re-query once the scan completes so the user sees the
        // real list of tags instead of an empty popup on first invocation.
        metadataProject?.let { project ->
            io.github.barsia.speqa.registry.SpeqaTagRegistry.getInstance(project)
                .whenInitialized { popup.refresh() }
        }
    }

    fun setTags(newTags: List<String>) {
        tags = newTags.toList()
        rebuild()
    }

    fun setAllKnownTags(supplier: () -> Set<String>) {
        allKnownTagsSupplier = supplier
    }

    private fun rebuild() {
        removeAll()
        chips.clear()
        if (tags.isEmpty()) {
            val emptyKey = if (metadataKind == MetadataKind.ENVIRONMENT) "label.noEnvironments" else "label.noTags"
            val emptyLabel = com.intellij.ui.components.JBLabel(SpeqaBundle.message(emptyKey)).apply {
                foreground = com.intellij.ui.JBColor.namedColor(
                    "Label.disabledForeground",
                    com.intellij.ui.JBColor.GRAY,
                )
                // Align text with chip text: chip border.top=2, vgap=2.
                border = javax.swing.BorderFactory.createEmptyBorder(JBUI.scale(2), 0, 0, 0)
            }
            add(emptyLabel)
        } else {
            tags.forEachIndexed { index, tag ->
                val sizeBefore = tags.size
                val project = metadataProject
                val clickCallback: (() -> Unit)? = if (project != null) {
                    { openMatchesDialog(project, tag) }
                } else { { onActivate(tag) } }
                val tooltipText: String? = if (project != null) tooltipFor(tag) else null
                val chip = TagChip(
                    tag = tag,
                    colored = coloredChips,
                    onClick = clickCallback,
                    onEdit = { onEditValue(tag) },
                    onDelete = {
                        onRemove(tag)
                        restorer.onDeleted(index, sizeBefore)
                    },
                    tooltip = tooltipText,
                )
                chips.add(chip)
                add(chip)
            }
        }
        if (!hideAddButton) add(addButton)
        revalidate()
        repaint()
    }

    private fun tooltipFor(value: String): String {
        return when {
            metadataScope == MetadataScope.TEST_RUNS && metadataKind == MetadataKind.TAG ->
                SpeqaBundle.message("metadata.tooltip.showTestRunsWithTag")
            metadataScope == MetadataScope.TEST_RUNS && metadataKind == MetadataKind.ENVIRONMENT ->
                SpeqaBundle.message("metadata.findTestRunsWithEnvironment")
            metadataKind == MetadataKind.ENVIRONMENT ->
                SpeqaBundle.message("metadata.tooltip.showTestCasesWithEnvironment")
            else ->
                SpeqaBundle.message("metadata.tooltip.showTestCasesWithTag")
        }.let { "$it \"$value\"" }
    }

    private fun candidatesFor(project: Project, value: String): List<VirtualFile> {
        val registry = io.github.barsia.speqa.registry.SpeqaTagRegistry.getInstance(project)
        return when (metadataScope) {
            MetadataScope.TEST_CASES -> when (metadataKind) {
                MetadataKind.TAG -> registry.findTestCasesByTag(value)
                MetadataKind.ENVIRONMENT -> registry.findTestCasesByEnvironment(value)
            }
            MetadataScope.TEST_RUNS -> when (metadataKind) {
                MetadataKind.TAG -> registry.findTestRunsByTag(value)
                MetadataKind.ENVIRONMENT -> registry.findTestRunsByEnvironment(value)
            }
        }
    }

    private fun openMatchesDialog(project: Project, value: String) {
        // Defer dialog until the registry's first scan completes; otherwise
        // findTestCasesByTag returns an empty list and the user sees a
        // "no matches" dialog even though candidates exist.
        io.github.barsia.speqa.registry.SpeqaTagRegistry.getInstance(project).whenInitialized {
            if (project.isDisposed) return@whenInitialized
            try {
                val candidates = candidatesFor(project, value)
                val dialog = MetadataMatchesDialog(
                    project = project,
                    scope = metadataScope,
                    kind = metadataKind,
                    value = value,
                    candidates = candidates,
                    onPick = { file -> FileEditorManager.getInstance(project).openFile(file, true) },
                )
                dialog.show()
            } catch (t: Throwable) {
                LOG.warn("TagCloud: failed to open matches dialog for value='$value'", t)
            }
        }
    }

    companion object {
        private val LOG = com.intellij.openapi.diagnostic.Logger.getInstance(TagCloud::class.java)
    }
}
