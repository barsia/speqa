package io.github.barsia.speqa.toolwindow

import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.components.JBList
import com.intellij.util.ui.JBUI
import io.github.barsia.speqa.SpeqaBundle
import io.github.barsia.speqa.editor.ui.chips.MetadataKind
import io.github.barsia.speqa.editor.ui.chips.MetadataScope
import io.github.barsia.speqa.editor.ui.chips.TagChip
import io.github.barsia.speqa.editor.ui.chips.TagCloud
import io.github.barsia.speqa.editor.ui.primitives.WrapLayout
import io.github.barsia.speqa.editor.ui.primitives.speqaIconButton
import io.github.barsia.speqa.filetype.SpeqaIcons
import io.github.barsia.speqa.model.Priority
import io.github.barsia.speqa.model.Status
import io.github.barsia.speqa.registry.SpeqaTagRegistry
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JPanel

/**
 * Header above the test-case tool-window tree owning the multi-facet filter UI.
 *
 * Two rows:
 *  - a toolbar with four facet icon-buttons (Status, Priority, Tags,
 *    Environment), each opening its own scoped popup, plus a "clear all" button
 *    visible only while a filter is active. A facet button whose facet is active
 *    is rendered in a highlighted state;
 *  - a wrap-layout row of removable chips, one per active selection, hidden when
 *    no filter is active.
 *
 * Every facet mutation invokes [onChanged] (wired by the factory to
 * `treeModel.invalidateAsync()`) and refreshes the header.
 */
class SpeqaFilterHeader(
    private val project: Project,
    private val filter: SpeqaTreeFilter,
    parentDisposable: com.intellij.openapi.Disposable,
    private val onChanged: () -> Unit,
) {

    private val registry = SpeqaTagRegistry.getInstance(project)

    private val statusButton = facetButton(
        icon = AllIcons.Actions.GroupBy,
        tooltip = SpeqaBundle.message("toolwindow.speqa.filter.status"),
        onAction = { showStatusPopup() },
    )

    private val priorityButton = facetButton(
        icon = AllIcons.Nodes.Favorite,
        tooltip = SpeqaBundle.message("toolwindow.speqa.filter.priority"),
        onAction = { showPriorityPopup() },
    )

    private val tagsButton = facetButton(
        icon = AllIcons.Gutter.ExtAnnotation,
        tooltip = SpeqaBundle.message("toolwindow.speqa.filter.tags"),
        onAction = { showTagsPopup() },
    )

    private val environmentButton = facetButton(
        icon = AllIcons.General.Web,
        tooltip = SpeqaBundle.message("toolwindow.speqa.filter.environment"),
        onAction = { showEnvironmentPopup() },
    )

    private val clearAllButton: JComponent = speqaIconButton(
        icon = AllIcons.Actions.Close,
        tooltip = SpeqaBundle.message("toolwindow.speqa.filter.clearAll"),
        danger = true,
        onAction = {
            filter.clear()
            refresh()
            onChanged()
        },
    )

    private val toolbarRow = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(4), JBUI.scale(2))).apply {
        isOpaque = false
        add(statusButton.wrapper)
        add(priorityButton.wrapper)
        add(tagsButton.wrapper)
        add(environmentButton.wrapper)
        add(clearAllButton)
    }

    private val chipRow = JPanel(
        WrapLayout(FlowLayout.LEFT, JBUI.scale(4), JBUI.scale(2), gapAround = false),
    ).apply {
        isOpaque = false
        border = JBUI.Borders.empty(0, 4, 2, 4)
    }

    /** The component to add to the tool-window content. */
    val component: JComponent = JPanel(BorderLayout()).apply {
        isOpaque = false
        border = JBUI.Borders.empty(2)
        add(toolbarRow, BorderLayout.NORTH)
        add(chipRow, BorderLayout.SOUTH)
    }

    /** The currently-open facet popup, if any. */
    private var popup: JBPopup? = null

    init {
        com.intellij.openapi.util.Disposer.register(parentDisposable) {
            popup?.cancel()
            popup = null
        }
        refresh()
    }

    /** Re-render facet active-state, the clear-all visibility, and the chip row. */
    private fun refresh() {
        statusButton.setActive(filter.status != null)
        priorityButton.setActive(filter.priority != null)
        tagsButton.setActive(filter.tags.isNotEmpty())
        environmentButton.setActive(filter.environments.isNotEmpty())
        clearAllButton.isVisible = filter.activeCount() > 0
        rebuildChipRow()
    }

    private fun rebuildChipRow() {
        chipRow.removeAll()
        chipRow.isVisible = !filter.isEmpty()
        if (!filter.isEmpty()) {
            filter.status?.let { status ->
                chipRow.add(removableChip(capitalize(status.label), colored = false) {
                    filter.status = null
                    refresh()
                    onChanged()
                })
            }
            filter.priority?.let { priority ->
                chipRow.add(removableChip(capitalize(priority.label), colored = false) {
                    filter.priority = null
                    refresh()
                    onChanged()
                })
            }
            filter.tags.toList().forEach { tag ->
                chipRow.add(removableChip(tag, colored = true) {
                    filter.removeTag(tag)
                    refresh()
                    onChanged()
                })
            }
            filter.environments.toList().forEach { environment ->
                chipRow.add(removableChip(environment, colored = true) {
                    filter.removeEnvironment(environment)
                    refresh()
                    onChanged()
                })
            }
        }
        chipRow.revalidate()
        chipRow.repaint()
    }

    private fun removableChip(label: String, colored: Boolean, onRemove: () -> Unit): JComponent =
        TagChip(tag = label, colored = colored, onDelete = onRemove, alwaysShowDelete = true)

    /** Cancel any open popup; returns true if a popup was open (toggle-off case). */
    private fun closeOpenPopup(): Boolean {
        popup?.let {
            if (!it.isDisposed) {
                it.cancel()
            }
        }
        val wasOpen = popup != null
        popup = null
        return wasOpen
    }

    private fun showStatusPopup() {
        if (closeOpenPopup()) return
        val options = listOf(
            StatusOption(null, SpeqaBundle.message("toolwindow.speqa.filter.allStatuses"), null),
        ) + Status.entries.map { StatusOption(it, capitalize(it.label), SpeqaIcons.forStatus(it)) }
        showFacetChooser(statusButton.wrapper, options, filter.status) { picked ->
            filter.status = picked.value
            refresh()
            onChanged()
        }
    }

    private fun showPriorityPopup() {
        if (closeOpenPopup()) return
        val options = listOf(
            PriorityOption(null, SpeqaBundle.message("toolwindow.speqa.filter.allPriorities"), null),
        ) + Priority.entries.map { PriorityOption(it, capitalize(it.label), null) }
        showFacetChooser(priorityButton.wrapper, options, filter.priority) { picked ->
            filter.priority = picked.value
            refresh()
            onChanged()
        }
    }

    private fun <E : Enum<E>, O : FilterOption<E>> showFacetChooser(
        anchor: JComponent,
        options: List<O>,
        selected: E?,
        onPick: (O) -> Unit,
    ) {
        val list = JBList(options).apply {
            selectionMode = javax.swing.ListSelectionModel.SINGLE_SELECTION
            selectedIndex = options.indexOfFirst { it.value == selected }.coerceAtLeast(0)
            cellRenderer = object : com.intellij.ui.SimpleListCellRenderer<O>() {
                override fun customize(list: JList<out O>, value: O?, index: Int, selected: Boolean, hasFocus: Boolean) {
                    text = value?.label ?: ""
                    icon = value?.icon
                }
            }
        }
        val newPopup = JBPopupFactory.getInstance()
            .createListPopupBuilder(list)
            .setRequestFocus(true)
            .setItemChoosenCallback {
                val picked = list.selectedValue ?: return@setItemChoosenCallback
                onPick(picked)
            }
            .createPopup()
        popup = newPopup
        newPopup.showUnderneathOf(anchor)
    }

    private fun showTagsPopup() {
        if (closeOpenPopup()) return
        val cloud = TagCloud(
            coloredChips = true,
            metadataKind = MetadataKind.TAG,
            metadataScope = MetadataScope.TEST_CASES,
            metadataProject = project,
            onActivate = {},
            onAdd = { value -> filter.addTag(value); refresh(); onChanged() },
            onRemove = { value -> filter.removeTag(value); refresh(); onChanged() },
        )
        cloud.setAllKnownTags { registry.allTags.toSet() }
        cloud.setTags(filter.tags.toList())
        registry.whenInitialized { cloud.setAllKnownTags { registry.allTags.toSet() } }
        showCloudPopup(tagsButton.wrapper, cloud)
    }

    private fun showEnvironmentPopup() {
        if (closeOpenPopup()) return
        val cloud = TagCloud(
            coloredChips = true,
            metadataKind = MetadataKind.ENVIRONMENT,
            metadataScope = MetadataScope.TEST_CASES,
            metadataProject = project,
            onActivate = {},
            onAdd = { value -> filter.addEnvironment(value); refresh(); onChanged() },
            onRemove = { value -> filter.removeEnvironment(value); refresh(); onChanged() },
        )
        cloud.setAllKnownTags { registry.allEnvironments.toSet() }
        cloud.setTags(filter.environments.toList())
        registry.whenInitialized { cloud.setAllKnownTags { registry.allEnvironments.toSet() } }
        showCloudPopup(environmentButton.wrapper, cloud)
    }

    private fun showCloudPopup(anchor: JComponent, cloud: TagCloud) {
        val panel = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(8)
            add(cloud, BorderLayout.CENTER)
            preferredSize = JBUI.size(260, 120)
        }
        val newPopup = JBPopupFactory.getInstance()
            .createComponentPopupBuilder(panel, cloud)
            .setRequestFocus(true)
            .setResizable(false)
            .setMovable(false)
            .createPopup()
        popup = newPopup
        newPopup.showUnderneathOf(anchor)
        cloud.startAdd()
    }

    private fun capitalize(label: String): String = label.replaceFirstChar { it.uppercase() }

    /**
     * Wraps a [speqaIconButton] in a non-opaque panel that paints a highlighted
     * background when its facet is active, so active facets read at a glance.
     */
    private fun facetButton(icon: Icon, tooltip: String, onAction: () -> Unit): FacetButton {
        val button = speqaIconButton(icon = icon, tooltip = tooltip, onAction = onAction)
        return FacetButton(button)
    }

    private class FacetButton(button: JComponent) {
        val wrapper: JPanel = object : JPanel(BorderLayout()) {
            init {
                isOpaque = false
                add(button, BorderLayout.CENTER)
            }
        }

        fun setActive(active: Boolean) {
            if (active) {
                wrapper.isOpaque = true
                wrapper.background = JBUI.CurrentTheme.ActionButton.pressedBackground()
            } else {
                wrapper.isOpaque = false
            }
            wrapper.repaint()
        }
    }

    private sealed interface FilterOption<E : Enum<E>> {
        val value: E?
        val label: String
        val icon: Icon?
    }

    private data class StatusOption(
        override val value: Status?,
        override val label: String,
        override val icon: Icon?,
    ) : FilterOption<Status>

    private data class PriorityOption(
        override val value: Priority?,
        override val label: String,
        override val icon: Icon?,
    ) : FilterOption<Priority>
}
