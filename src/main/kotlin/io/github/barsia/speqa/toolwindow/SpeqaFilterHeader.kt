package io.github.barsia.speqa.toolwindow

import com.intellij.icons.AllIcons
import com.intellij.ide.ActivityTracker
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.actionSystem.Toggleable
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.NlsActions
import com.intellij.ui.components.JBList
import com.intellij.util.ui.JBUI
import io.github.barsia.speqa.SpeqaBundle
import io.github.barsia.speqa.editor.ui.chips.AddTagPopup
import io.github.barsia.speqa.editor.ui.chips.TagChip
import io.github.barsia.speqa.editor.ui.primitives.WrapLayout
import io.github.barsia.speqa.filetype.SpeqaIcons
import io.github.barsia.speqa.model.Priority
import io.github.barsia.speqa.registry.SpeqaTagRegistry
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JPanel

/**
 * A single option in the tab-specific primary facet (Status for TCs, Result for
 * TRs). The "All <facet>" entry is modeled as an option whose [apply] clears the
 * selection. [apply] mutates the filter to this option's value.
 */
class PrimaryOption(
    val label: String,
    val icon: Icon?,
    val selected: Boolean,
    val apply: () -> Unit,
)

/**
 * Describes the tab-specific primary facet so [SpeqaFilterHeader] can render it
 * without knowing whether it is a test-case status or a test-run result.
 */
class PrimaryFacet(
    val icon: Icon,
    val tooltip: @NlsActions.ActionText String,
    val isActive: () -> Boolean,
    /** Capitalized label of the current selection, or null when inactive. */
    val chipLabel: () -> String?,
    val clear: () -> Unit,
    /** All selectable options, including the "All <facet>" entry at index 0. */
    val options: () -> List<PrimaryOption>,
)

/**
 * Owns the multi-facet filter UI for one tool-window tab.
 *
 * The four facet triggers (the tab-specific primary facet plus Priority, Tags,
 * Environment) and the "clear all" trigger are exposed as [titleActions]; the
 * factory installs the active tab's actions into the tool-window title bar and
 * swaps them on tab change. An active facet renders highlighted natively via
 * [Toggleable]; the clear-all action is hidden while no filter is active. Each
 * facet opens its own scoped popup, anchored under the clicked title-bar button.
 *
 * The [component] is the wrap-layout row of removable chips, one per active
 * selection, hidden when no filter is active, shown just above the tree.
 *
 * Every facet mutation invokes [onChanged] (wired by the factory to
 * `treeModel.invalidateAsync()`) and refreshes the chip row and title actions.
 */
class SpeqaFilterHeader(
    private val project: Project,
    private val filter: SpeqaFilter,
    private val primary: PrimaryFacet,
    private val knownTags: () -> Set<String>,
    private val knownEnvironments: () -> Set<String>,
    private val hasContent: () -> Boolean,
    parentDisposable: com.intellij.openapi.Disposable,
    private val onChanged: () -> Unit,
    leadingActions: List<AnAction> = emptyList(),
) {

    private val registry = SpeqaTagRegistry.getInstance(project)

    private val chipRow = JPanel(
        WrapLayout(FlowLayout.LEFT, JBUI.scale(4), JBUI.scale(2), gapAround = false),
    ).apply {
        isOpaque = false
        border = JBUI.Borders.empty(0, 4, 2, 4)
    }

    /** The component to add to the tool-window content: the active-chip row. */
    val component: JComponent = chipRow

    /** Actions to install into the tool-window title bar. */
    val titleActions: List<AnAction> = buildList {
        addAll(leadingActions)
        if (leadingActions.isNotEmpty()) add(Separator.create())
        add(FacetAction(Facet.PRIMARY, primary.icon, primary.tooltip))
        add(FacetAction(Facet.PRIORITY, SpeqaIcons.FilterPriority, SpeqaBundle.message("toolwindow.speqa.filter.priority")))
        add(FacetAction(Facet.TAGS, SpeqaIcons.FilterTags, SpeqaBundle.message("toolwindow.speqa.filter.tags")))
        add(FacetAction(Facet.ENVIRONMENT, SpeqaIcons.FilterEnvironment, SpeqaBundle.message("toolwindow.speqa.filter.environment")))
        add(ClearAllAction())
    }

    /** The currently-open facet popup, if any. */
    private var popup: JBPopup? = null

    /** Which facet's popup is currently open, if any. */
    private var openFacet: Facet? = null

    init {
        com.intellij.openapi.util.Disposer.register(parentDisposable) {
            popup?.cancel()
            popup = null
            openFacet = null
        }
        refresh()
    }

    private fun isFacetActive(facet: Facet): Boolean = when (facet) {
        Facet.PRIMARY -> primary.isActive()
        Facet.PRIORITY -> filter.priority != null
        Facet.TAGS -> filter.tags.isNotEmpty()
        Facet.ENVIRONMENT -> filter.environments.isNotEmpty()
    }

    /** Title-bar trigger for a single facet, highlighted while that facet is active. */
    private inner class FacetAction(
        private val facet: Facet,
        icon: Icon,
        @NlsActions.ActionText private val tooltip: String,
    ) : DumbAwareAction(tooltip, null, icon), Toggleable {

        override fun actionPerformed(e: AnActionEvent) {
            val anchor = e.inputEvent?.component as? JComponent
            when (facet) {
                Facet.PRIMARY -> showPrimaryPopup(e, anchor)
                Facet.PRIORITY -> showPriorityPopup(e, anchor)
                Facet.TAGS -> showTagsPopup(e, anchor)
                Facet.ENVIRONMENT -> showEnvironmentPopup(e, anchor)
            }
        }

        override fun update(e: AnActionEvent) {
            // Hide every facet trigger when the tab has no leaves at all - there is
            // nothing to filter until at least one test case / test run exists.
            if (!hasContent()) {
                e.presentation.isEnabledAndVisible = false
                return
            }
            e.presentation.isVisible = true
            // A multi-select tag/environment facet whose picker would be empty (the project has
            // no such values, or all of them are already selected) is disabled with an explaining
            // tooltip instead of opening an empty "Nothing to show" popup.
            val emptyPickerTooltip = emptyPickerTooltip()
            e.presentation.isEnabled = emptyPickerTooltip == null
            e.presentation.text = emptyPickerTooltip ?: tooltip
            Toggleable.setSelected(e.presentation, isFacetActive(facet))
        }

        /**
         * For a tag/environment facet, the tooltip to show while its picker has nothing to offer,
         * or null when the facet is pickable (and for the single-select facets, which always are).
         */
        private fun emptyPickerTooltip(): String? {
            val (known, selected) = when (facet) {
                Facet.TAGS -> knownTags() to filter.tags
                Facet.ENVIRONMENT -> knownEnvironments() to filter.environments
                else -> return null
            }
            val key = when (facetPickState(known, selected)) {
                FacetPickState.PICKABLE -> return null
                FacetPickState.NO_VALUES ->
                    if (facet == Facet.TAGS) "toolwindow.speqa.filter.noTags" else "toolwindow.speqa.filter.noEnvironments"
                FacetPickState.ALL_SELECTED ->
                    if (facet == Facet.TAGS) {
                        "toolwindow.speqa.filter.allTagsSelected"
                    } else {
                        "toolwindow.speqa.filter.allEnvironmentsSelected"
                    }
            }
            return SpeqaBundle.message(key)
        }

        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
    }

    /** Title-bar trigger that clears every active facet; hidden while none is active. */
    private inner class ClearAllAction : DumbAwareAction(
        SpeqaBundle.message("toolwindow.speqa.filter.clearAll"),
        null,
        AllIcons.Actions.Close,
    ) {
        override fun actionPerformed(e: AnActionEvent) {
            filter.clear()
            refresh()
            onChanged()
        }

        override fun update(e: AnActionEvent) {
            e.presentation.isEnabledAndVisible = filter.activeCount() > 0
        }

        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
    }

    /** Re-render the clear-all/facet title-bar state and the chip row. */
    private fun refresh() {
        rebuildChipRow()
        // Nudge the title toolbar to re-run update() so the Toggleable highlight
        // and clear-all visibility refresh immediately.
        ActivityTracker.getInstance().inc()
    }

    private fun rebuildChipRow() {
        chipRow.removeAll()
        chipRow.isVisible = !filter.isEmpty()
        if (!filter.isEmpty()) {
            primary.chipLabel()?.let { label ->
                chipRow.add(removableChip(label, colored = false) {
                    primary.clear()
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
        TagChip(tag = label, colored = colored, onDelete = onRemove, alwaysShowDelete = true, deleteValueGap = 4)

    /** Cancels any open facet popup and clears the open-facet tracking. */
    private fun cancelPopup() {
        popup?.takeIf { !it.isDisposed }?.cancel()
        popup = null
        openFacet = null
    }

    /**
     * Decides whether clicking [facet]'s button should open its popup. Always
     * closes any currently-open popup first; returns true to open [facet] unless
     * that same facet was already open, in which case the click just toggles it shut.
     */
    private fun shouldOpen(facet: Facet): Boolean {
        val sameAlreadyOpen = openFacet == facet
        cancelPopup()
        return !sameAlreadyOpen
    }

    /**
     * Shows [p] as [facet]'s popup, tracking it until it closes. Anchors under
     * [anchor] when available, otherwise at the best-guess location for [e].
     */
    private fun openPopup(facet: Facet, p: JBPopup, anchor: JComponent?, e: AnActionEvent) {
        popup = p
        openFacet = facet
        p.addListener(object : com.intellij.openapi.ui.popup.JBPopupListener {
            override fun onClosed(event: com.intellij.openapi.ui.popup.LightweightWindowEvent) {
                if (popup === p) {
                    popup = null
                    openFacet = null
                }
            }
        })
        if (anchor != null) {
            p.showUnderneathOf(anchor)
        } else {
            p.show(JBPopupFactory.getInstance().guessBestPopupLocation(e.dataContext))
        }
    }

    private fun showPrimaryPopup(e: AnActionEvent, anchor: JComponent?) {
        if (!shouldOpen(Facet.PRIMARY)) return
        val options = primary.options()
        val list = JBList(options).apply {
            selectionMode = javax.swing.ListSelectionModel.SINGLE_SELECTION
            selectedIndex = options.indexOfFirst { it.selected }.coerceAtLeast(0)
            cellRenderer = object : com.intellij.ui.SimpleListCellRenderer<PrimaryOption>() {
                override fun customize(
                    list: JList<out PrimaryOption>,
                    value: PrimaryOption?,
                    index: Int,
                    selected: Boolean,
                    hasFocus: Boolean,
                ) {
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
                picked.apply()
                refresh()
                onChanged()
            }
            .createPopup()
        openPopup(Facet.PRIMARY, newPopup, anchor, e)
    }

    private fun showPriorityPopup(e: AnActionEvent, anchor: JComponent?) {
        if (!shouldOpen(Facet.PRIORITY)) return
        val options = listOf(
            PriorityOption(null, SpeqaBundle.message("toolwindow.speqa.filter.allPriorities")),
        ) + Priority.entries.map { PriorityOption(it, capitalize(it.label)) }
        val list = JBList(options).apply {
            selectionMode = javax.swing.ListSelectionModel.SINGLE_SELECTION
            selectedIndex = options.indexOfFirst { it.value == filter.priority }.coerceAtLeast(0)
            cellRenderer = object : com.intellij.ui.SimpleListCellRenderer<PriorityOption>() {
                override fun customize(
                    list: JList<out PriorityOption>,
                    value: PriorityOption?,
                    index: Int,
                    selected: Boolean,
                    hasFocus: Boolean,
                ) {
                    text = value?.label ?: ""
                }
            }
        }
        val newPopup = JBPopupFactory.getInstance()
            .createListPopupBuilder(list)
            .setRequestFocus(true)
            .setItemChoosenCallback {
                val picked = list.selectedValue ?: return@setItemChoosenCallback
                filter.priority = picked.value
                refresh()
                onChanged()
            }
            .createPopup()
        openPopup(Facet.PRIORITY, newPopup, anchor, e)
    }

    private fun showTagsPopup(e: AnActionEvent, anchor: JComponent?) {
        if (!shouldOpen(Facet.TAGS)) return
        showValuePicker(
            facet = Facet.TAGS,
            anchor = anchor ?: return,
            allKnown = { knownTags() },
            currentSelection = { filter.tags },
            onPick = { value ->
                if (value.isNotBlank() && filter.tags.none { it.equals(value, ignoreCase = true) }) {
                    filter.addTag(value)
                    refresh()
                    onChanged()
                }
            },
        )
    }

    private fun showEnvironmentPopup(e: AnActionEvent, anchor: JComponent?) {
        if (!shouldOpen(Facet.ENVIRONMENT)) return
        showValuePicker(
            facet = Facet.ENVIRONMENT,
            anchor = anchor ?: return,
            allKnown = { knownEnvironments() },
            currentSelection = { filter.environments },
            onPick = { value ->
                if (value.isNotBlank() && filter.environments.none { it.equals(value, ignoreCase = true) }) {
                    filter.addEnvironment(value)
                    refresh()
                    onChanged()
                }
            },
        )
    }

    /**
     * Opens the shared [AddTagPopup] autocomplete picker for a tag/environment facet, anchored
     * under the clicked title-bar button. It lists the tab's known values (minus the already
     * selected ones) with type-to-filter, and picking one adds it to the filter; the already
     * selected values are shown and removable in the chip row above the tree. The picker is
     * tracked as [facet]'s open popup so clicking the same button again toggles it shut, and the
     * known set is re-queried once the registry finishes its background scan.
     */
    private fun showValuePicker(
        facet: Facet,
        anchor: JComponent,
        allKnown: () -> Set<String>,
        currentSelection: () -> Set<String>,
        onPick: (String) -> Unit,
    ) {
        val picker = AddTagPopup(
            anchor = anchor,
            allKnown = allKnown,
            currentSelection = currentSelection,
            onPick = onPick,
            // Filtering by a value that does not exist in the project can only match nothing,
            // so the picker offers existing values only - no "+ Create" row.
            allowCreate = false,
        )
        val shown = picker.show() ?: return
        popup = shown
        openFacet = facet
        shown.addListener(object : com.intellij.openapi.ui.popup.JBPopupListener {
            override fun onClosed(event: com.intellij.openapi.ui.popup.LightweightWindowEvent) {
                if (popup === shown) {
                    popup = null
                    openFacet = null
                }
            }
        })
        registry.whenInitialized { if (!shown.isDisposed) picker.refresh() }
    }

    private fun capitalize(label: String): String = label.replaceFirstChar { it.uppercase() }

    private enum class Facet { PRIMARY, PRIORITY, TAGS, ENVIRONMENT }

    private data class PriorityOption(val value: Priority?, val label: String)
}

/** Whether a multi-select tag/environment facet has anything left to pick. */
internal enum class FacetPickState { PICKABLE, NO_VALUES, ALL_SELECTED }

/**
 * Classifies a multi-select facet for enable/disable gating: `NO_VALUES` when the project has no
 * such values at all, `ALL_SELECTED` when every known value is already in the filter (so the
 * picker would be empty), and `PICKABLE` otherwise. Selection membership is compared
 * case-insensitively to match how [AddTagPopup] filters already-picked values out of its list.
 * Pure so the gating contract is unit-tested without standing up the tool window.
 */
internal fun facetPickState(known: Set<String>, selected: Set<String>): FacetPickState {
    if (known.isEmpty()) return FacetPickState.NO_VALUES
    val hasPickable = known.any { value -> selected.none { it.equals(value, ignoreCase = true) } }
    return if (hasPickable) FacetPickState.PICKABLE else FacetPickState.ALL_SELECTED
}
