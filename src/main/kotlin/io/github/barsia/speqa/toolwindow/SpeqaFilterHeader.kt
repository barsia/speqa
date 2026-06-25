package io.github.barsia.speqa.toolwindow

import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import io.github.barsia.speqa.SpeqaBundle
import io.github.barsia.speqa.editor.ui.chips.MetadataKind
import io.github.barsia.speqa.editor.ui.chips.MetadataScope
import io.github.barsia.speqa.editor.ui.chips.TagChip
import io.github.barsia.speqa.editor.ui.chips.TagCloud
import io.github.barsia.speqa.editor.ui.primitives.WrapLayout
import io.github.barsia.speqa.editor.ui.primitives.handCursor
import io.github.barsia.speqa.editor.ui.primitives.speqaIconButton
import io.github.barsia.speqa.filetype.SpeqaIcons
import io.github.barsia.speqa.model.Priority
import io.github.barsia.speqa.model.Status
import io.github.barsia.speqa.registry.SpeqaTagRegistry
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JPanel

/**
 * Header above the test-case tool-window tree owning the multi-facet filter UI.
 *
 * Two rows:
 *  - a toolbar with a funnel "Filter" control (showing the active-selection
 *    count) that opens a popup with the four facet controls, plus a "clear all"
 *    button visible only while a filter is active;
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

    private val filterButton = JButton().apply {
        icon = AllIcons.General.Filter
        toolTipText = SpeqaBundle.message("toolwindow.speqa.filter.tooltip")
        handCursor()
        addActionListener { showPopup() }
    }

    private val clearAllButton: JComponent = speqaIconButton(
        icon = AllIcons.Actions.Close,
        tooltip = SpeqaBundle.message("toolwindow.speqa.filter.clearAll"),
        danger = true,
        onAction = {
            filter.clear()
            popup?.let { resetPopupControls() }
            refresh()
            onChanged()
        },
    )

    private val toolbarRow = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(4), JBUI.scale(2))).apply {
        isOpaque = false
        add(filterButton)
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

    // Popup controls, created lazily and reset on clear.
    private var popup: JBPopup? = null
    private var statusCombo: ComboBox<StatusOption>? = null
    private var priorityCombo: ComboBox<PriorityOption>? = null
    private var tagCloud: TagCloud? = null
    private var environmentCloud: TagCloud? = null

    init {
        com.intellij.openapi.util.Disposer.register(parentDisposable) {
            popup?.cancel()
            popup = null
        }
        refresh()
    }

    /** Re-render the funnel label, the clear-all visibility, and the chip row. */
    private fun refresh() {
        val count = filter.activeCount()
        filterButton.text = if (count > 0) {
            SpeqaBundle.message("toolwindow.speqa.filter.titleCount", count)
        } else {
            SpeqaBundle.message("toolwindow.speqa.filter.title")
        }
        clearAllButton.isVisible = count > 0
        rebuildChipRow()
    }

    private fun rebuildChipRow() {
        chipRow.removeAll()
        chipRow.isVisible = !filter.isEmpty()
        if (!filter.isEmpty()) {
            filter.status?.let { status ->
                chipRow.add(removableChip(capitalize(status.label), colored = false) {
                    filter.status = null
                    statusCombo?.let { it.selectedItem = it.allOption() }
                    refresh()
                    onChanged()
                })
            }
            filter.priority?.let { priority ->
                chipRow.add(removableChip(capitalize(priority.label), colored = false) {
                    filter.priority = null
                    priorityCombo?.let { it.selectedItem = it.allOption() }
                    refresh()
                    onChanged()
                })
            }
            filter.tags.toList().forEach { tag ->
                chipRow.add(removableChip(tag, colored = true) {
                    filter.removeTag(tag)
                    tagCloud?.setTags(filter.tags.toList())
                    refresh()
                    onChanged()
                })
            }
            filter.environments.toList().forEach { environment ->
                chipRow.add(removableChip(environment, colored = true) {
                    filter.removeEnvironment(environment)
                    environmentCloud?.setTags(filter.environments.toList())
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

    private fun showPopup() {
        popup?.let {
            if (!it.isDisposed) {
                it.cancel()
                popup = null
                return
            }
        }
        val panel = buildPopupPanel()
        val newPopup = JBPopupFactory.getInstance()
            .createComponentPopupBuilder(panel, statusCombo)
            .setRequestFocus(true)
            .setResizable(false)
            .setMovable(false)
            .createPopup()
        popup = newPopup
        newPopup.showUnderneathOf(filterButton)
    }

    private fun buildPopupPanel(): JComponent {
        val status = buildStatusCombo().also { statusCombo = it }
        val priority = buildPriorityCombo().also { priorityCombo = it }

        val tags = TagCloud(
            coloredChips = true,
            metadataKind = MetadataKind.TAG,
            metadataScope = MetadataScope.TEST_CASES,
            metadataProject = project,
            onActivate = {},
            onAdd = { value -> filter.addTag(value); syncTagCloud(); refresh(); onChanged() },
            onRemove = { value -> filter.removeTag(value); syncTagCloud(); refresh(); onChanged() },
        ).also { tagCloud = it }
        tags.setAllKnownTags { registry.allTags.toSet() }
        tags.setTags(filter.tags.toList())
        registry.whenInitialized { tags.setAllKnownTags { registry.allTags.toSet() } }

        val environments = TagCloud(
            coloredChips = true,
            metadataKind = MetadataKind.ENVIRONMENT,
            metadataScope = MetadataScope.TEST_CASES,
            metadataProject = project,
            onActivate = {},
            onAdd = { value -> filter.addEnvironment(value); syncEnvironmentCloud(); refresh(); onChanged() },
            onRemove = { value -> filter.removeEnvironment(value); syncEnvironmentCloud(); refresh(); onChanged() },
        ).also { environmentCloud = it }
        environments.setAllKnownTags { registry.allEnvironments.toSet() }
        environments.setTags(filter.environments.toList())
        registry.whenInitialized { environments.setAllKnownTags { registry.allEnvironments.toSet() } }

        val clearButton = JButton(SpeqaBundle.message("toolwindow.speqa.filter.clear")).apply {
            handCursor()
            addActionListener {
                filter.clear()
                resetPopupControls()
                refresh()
                onChanged()
            }
        }
        val doneButton = JButton(SpeqaBundle.message("toolwindow.speqa.filter.done")).apply {
            handCursor()
            addActionListener { popup?.cancel() }
        }
        val buttons = JPanel(FlowLayout(FlowLayout.RIGHT, JBUI.scale(4), 0)).apply {
            isOpaque = false
            add(clearButton)
            add(doneButton)
        }

        val form = FormBuilder.createFormBuilder()
            .addLabeledComponent(SpeqaBundle.message("toolwindow.speqa.filter.status"), status)
            .addLabeledComponent(SpeqaBundle.message("toolwindow.speqa.filter.priority"), priority)
            .addLabeledComponent(SpeqaBundle.message("toolwindow.speqa.filter.tags"), tags, true)
            .addLabeledComponent(SpeqaBundle.message("toolwindow.speqa.filter.environment"), environments, true)
            .addComponent(buttons)
            .panel
        return JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(8)
            add(form, BorderLayout.CENTER)
        }
    }

    private fun syncTagCloud() {
        tagCloud?.setTags(filter.tags.toList())
    }

    private fun syncEnvironmentCloud() {
        environmentCloud?.setTags(filter.environments.toList())
    }

    private fun resetPopupControls() {
        statusCombo?.let { it.selectedItem = it.allOption() }
        priorityCombo?.let { it.selectedItem = it.allOption() }
        syncTagCloud()
        syncEnvironmentCloud()
    }

    private fun buildStatusCombo(): ComboBox<StatusOption> {
        val options = arrayOf(
            StatusOption(null, SpeqaBundle.message("toolwindow.speqa.filter.allStatuses"), null),
            *Status.entries.map {
                StatusOption(it, capitalize(it.label), SpeqaIcons.forStatus(it))
            }.toTypedArray(),
        )
        return ComboBox(options).apply {
            handCursor()
            renderer = object : SimpleListCellRenderer<StatusOption>() {
                override fun customize(list: JList<out StatusOption>, value: StatusOption?, index: Int, selected: Boolean, hasFocus: Boolean) {
                    text = value?.label ?: ""
                    icon = value?.icon
                }
            }
            selectedItem = options.first { it.value == filter.status }
            addActionListener {
                val picked = selectedItem as? StatusOption ?: return@addActionListener
                filter.status = picked.value
                refresh()
                onChanged()
            }
        }
    }

    private fun buildPriorityCombo(): ComboBox<PriorityOption> {
        val options = arrayOf(
            PriorityOption(null, SpeqaBundle.message("toolwindow.speqa.filter.allPriorities"), null),
            *Priority.entries.map {
                PriorityOption(it, capitalize(it.label), null)
            }.toTypedArray(),
        )
        return ComboBox(options).apply {
            handCursor()
            renderer = object : SimpleListCellRenderer<PriorityOption>() {
                override fun customize(list: JList<out PriorityOption>, value: PriorityOption?, index: Int, selected: Boolean, hasFocus: Boolean) {
                    text = value?.label ?: ""
                    icon = value?.icon
                }
            }
            selectedItem = options.first { it.value == filter.priority }
            addActionListener {
                val picked = selectedItem as? PriorityOption ?: return@addActionListener
                filter.priority = picked.value
                refresh()
                onChanged()
            }
        }
    }

    private fun ComboBox<StatusOption>.allOption(): StatusOption =
        (0 until itemCount).map { getItemAt(it) }.first { it.value == null }

    @JvmName("allOptionPriority")
    private fun ComboBox<PriorityOption>.allOption(): PriorityOption =
        (0 until itemCount).map { getItemAt(it) }.first { it.value == null }

    private fun capitalize(label: String): String = label.replaceFirstChar { it.uppercase() }

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
