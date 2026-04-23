package io.github.barsia.speqa.editor.ui.chips

import com.intellij.icons.AllIcons
import com.intellij.util.ui.JBUI
import io.github.barsia.speqa.SpeqaBundle
import io.github.barsia.speqa.editor.ui.primitives.DeleteFocusRestorer
import io.github.barsia.speqa.editor.ui.primitives.speqaIconButton
import java.awt.FlowLayout
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Flow-layout tag cloud with inline add button. The add button opens a text field
 * (Enter commits, Escape cancels). Autocomplete popup is deferred.
 */
class TagCloud(
    private val coloredChips: Boolean = false,
    private val onActivate: (String) -> Unit = {},
    private val onAdd: (String) -> Unit = {},
    private val onRemove: (String) -> Unit = {},
    private val metadataScope: MetadataScope = MetadataScope.TEST_CASES,
    private val metadataKind: MetadataKind = MetadataKind.TAG,
    private val metadataProject: com.intellij.openapi.project.Project? = null,
    /**
     * When true, the cloud suppresses its own internal `+` button — the caller
     * provides an external add button (e.g. a section-header `+`) and drives
     * the inline editor via [startAdd].
     */
    private val hideAddButton: Boolean = false,
) : JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(4), JBUI.scale(4))) {

    private var tags: List<String> = emptyList()
    private var allKnownTagsSupplier: () -> Set<String> = { emptySet() }
    private val chips = mutableListOf<TagChip>()
    private val addButton: JComponent = speqaIconButton(
        icon = AllIcons.General.Add,
        tooltip = SpeqaBundle.message("tagCloud.addTag"),
        onAction = { showInput() },
    )
    private val restorer = DeleteFocusRestorer(
        itemProvider = { chips.getOrNull(it) },
        addButton = addButton,
    )

    init {
        isOpaque = false
        if (!hideAddButton) add(addButton)
    }

    /**
     * Open the inline tag input. Callable from a section-header `+` button when
     * [hideAddButton] is true.
     */
    fun startAdd() {
        showInput()
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
        tags.forEachIndexed { index, tag ->
            val sizeBefore = tags.size
            val project = metadataProject
            val chipRef = arrayOfNulls<TagChip>(1)
            val clickCallback: (() -> Unit)? = if (project != null) {
                { chipRef[0]?.let { openMetadataPopup(it, project, tag) } }
            } else null
            val menuCallback: (() -> javax.swing.JPopupMenu)? = if (project != null) {
                { buildContextMenu(project, tag) }
            } else null
            val tooltipText: String? = if (project != null) tooltipFor(tag) else null
            val chip = TagChip(
                tag = tag,
                colored = coloredChips,
                onActivate = { onActivate(tag) },
                onDelete = {
                    onRemove(tag)
                    restorer.onDeleted(index, sizeBefore)
                },
                onClick = clickCallback,
                contextMenu = menuCallback,
                tooltip = tooltipText,
            )
            chipRef[0] = chip
            chips.add(chip)
            add(chip)
        }
        if (!hideAddButton) add(addButton)
        revalidate()
        repaint()
    }

    private fun showInput() {
        val field = io.github.barsia.speqa.editor.ui.primitives.singleLineInput(
            placeholder = SpeqaBundle.message("tagCloud.inputPlaceholder"),
        )
        val popup = TagAutocompletePopup(
            anchor = field,
            input = field,
            allTags = { allKnownTagsSupplier() },
            currentTags = { tags.toSet() },
            onPick = { picked ->
                if (picked.isNotEmpty() && picked !in tags) {
                    onAdd(picked)
                }
                // rebuild happens via setTags callback from the host; keep input up for rapid entry
                field.text = ""
            },
        )
        field.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                when (e.keyCode) {
                    KeyEvent.VK_ENTER -> {
                        // Only fall through to free-form add when the popup did not consume Enter
                        // (popup's KeyListener runs first and consumes when it has a selection).
                        if (!e.isConsumed) {
                            val trimmed = field.text.trim()
                            if (trimmed.isNotEmpty() && trimmed !in tags) {
                                onAdd(trimmed)
                                field.text = ""
                            } else {
                                dismissInput(popup)
                            }
                            e.consume()
                        }
                    }
                    KeyEvent.VK_ESCAPE -> {
                        if (!e.isConsumed) { dismissInput(popup); e.consume() }
                    }
                }
            }
        })
        field.addFocusListener(object : FocusAdapter() {
            override fun focusLost(e: FocusEvent) {
                // Close popup on focus loss; the popup itself is non-focusable so this fires
                // only when the user tabs/clicks away from the input entirely.
                if (e.oppositeComponent !== field) popup.hide()
            }
        })
        if (!hideAddButton) remove(addButton)
        add(field)
        revalidate()
        repaint()
        field.requestFocusInWindow()
        popup.install()
    }

    private fun dismissInput(popup: TagAutocompletePopup) {
        popup.hide()
        rebuild()
        addButton.requestFocusInWindow()
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

    private fun candidatesFor(
        project: com.intellij.openapi.project.Project,
        scope: MetadataScope,
        value: String,
    ): List<com.intellij.openapi.vfs.VirtualFile> {
        val registry = io.github.barsia.speqa.registry.SpeqaTagRegistry.getInstance(project)
        return when (scope) {
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

    private fun openMetadataPopup(
        anchor: java.awt.Component,
        project: com.intellij.openapi.project.Project,
        value: String,
    ) {
        val candidates = candidatesFor(project, metadataScope, value)
        showMetadataMatches(
            anchor = anchor,
            project = project,
            query = "",
            candidates = candidates,
            onPick = { file ->
                com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project)
                    .openFile(file, true)
            },
        )
    }

    private fun buildContextMenu(
        project: com.intellij.openapi.project.Project,
        value: String,
    ): javax.swing.JPopupMenu {
        val menu = javax.swing.JPopupMenu()
        val menuRef = menu
        val tcLabel = when (metadataKind) {
            MetadataKind.TAG -> SpeqaBundle.message("metadata.findTestCasesWithTag")
            MetadataKind.ENVIRONMENT -> SpeqaBundle.message("metadata.findTestCasesWithEnvironment")
        }
        val trLabel = when (metadataKind) {
            MetadataKind.TAG -> SpeqaBundle.message("metadata.findTestRunsWithTag")
            MetadataKind.ENVIRONMENT -> SpeqaBundle.message("metadata.findTestRunsWithEnvironment")
        }
        menu.add(javax.swing.JMenuItem(tcLabel).apply {
            addActionListener {
                val files = candidatesFor(project, MetadataScope.TEST_CASES, value)
                showMetadataMatches(
                    anchor = menuRef.invoker ?: this@TagCloud,
                    project = project,
                    query = "",
                    candidates = files,
                    onPick = { file ->
                        com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project)
                            .openFile(file, true)
                    },
                )
            }
        })
        menu.add(javax.swing.JMenuItem(trLabel).apply {
            addActionListener {
                val files = candidatesFor(project, MetadataScope.TEST_RUNS, value)
                showMetadataMatches(
                    anchor = menuRef.invoker ?: this@TagCloud,
                    project = project,
                    query = "",
                    candidates = files,
                    onPick = { file ->
                        com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project)
                            .openFile(file, true)
                    },
                )
            }
        })
        return menu
    }
}
