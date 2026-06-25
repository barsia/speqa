package io.github.barsia.speqa.toolwindow

import com.intellij.icons.AllIcons
import com.intellij.ide.ActivityTracker
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Toggleable
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.NlsActions
import com.intellij.ui.components.JBList
import com.intellij.util.ui.JBUI
import io.github.barsia.speqa.SpeqaBundle
import io.github.barsia.speqa.editor.ui.chips.MetadataKind
import io.github.barsia.speqa.editor.ui.chips.MetadataScope
import io.github.barsia.speqa.editor.ui.chips.TagChip
import io.github.barsia.speqa.editor.ui.chips.TagCloud
import io.github.barsia.speqa.editor.ui.primitives.WrapLayout
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
 * Owns the multi-facet filter UI for the test-case tool window.
 *
 * The four facet triggers (Status, Priority, Tags, Environment) plus the
 * "clear all" trigger are exposed as [titleActions] and installed into the
 * tool-window title bar by the factory. An active facet renders highlighted
 * natively via [Toggleable]; the clear-all action is hidden while no filter is
 * active. Each facet opens its own scoped popup, anchored under the clicked
 * title-bar button.
 *
 * The [component] is the wrap-layout row of removable chips, one per active
 * selection, hidden when no filter is active, shown just above the tree.
 *
 * Every facet mutation invokes [onChanged] (wired by the factory to
 * `treeModel.invalidateAsync()`) and refreshes the chip row and title actions.
 */
class SpeqaFilterHeader(
    private val project: Project,
    private val filter: SpeqaTreeFilter,
    parentDisposable: com.intellij.openapi.Disposable,
    private val onChanged: () -> Unit,
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
    val titleActions: List<AnAction> = listOf(
        FacetAction(Facet.STATUS, AllIcons.Actions.GroupBy, SpeqaBundle.message("toolwindow.speqa.filter.status")),
        FacetAction(Facet.PRIORITY, AllIcons.Nodes.Favorite, SpeqaBundle.message("toolwindow.speqa.filter.priority")),
        FacetAction(Facet.TAGS, AllIcons.Gutter.ExtAnnotation, SpeqaBundle.message("toolwindow.speqa.filter.tags")),
        FacetAction(Facet.ENVIRONMENT, AllIcons.General.Web, SpeqaBundle.message("toolwindow.speqa.filter.environment")),
        ClearAllAction(),
    )

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
        Facet.STATUS -> filter.status != null
        Facet.PRIORITY -> filter.priority != null
        Facet.TAGS -> filter.tags.isNotEmpty()
        Facet.ENVIRONMENT -> filter.environments.isNotEmpty()
    }

    /** Title-bar trigger for a single facet, highlighted while that facet is active. */
    private inner class FacetAction(
        private val facet: Facet,
        icon: Icon,
        @NlsActions.ActionText tooltip: String,
    ) : DumbAwareAction(tooltip, null, icon), Toggleable {

        override fun actionPerformed(e: AnActionEvent) {
            val anchor = e.inputEvent?.component as? JComponent
            when (facet) {
                Facet.STATUS -> showStatusPopup(e, anchor)
                Facet.PRIORITY -> showPriorityPopup(e, anchor)
                Facet.TAGS -> showTagsPopup(e, anchor)
                Facet.ENVIRONMENT -> showEnvironmentPopup(e, anchor)
            }
        }

        override fun update(e: AnActionEvent) {
            Toggleable.setSelected(e.presentation, isFacetActive(facet))
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

    private fun showStatusPopup(e: AnActionEvent, anchor: JComponent?) {
        if (!shouldOpen(Facet.STATUS)) return
        val options = listOf(
            StatusOption(null, SpeqaBundle.message("toolwindow.speqa.filter.allStatuses"), null),
        ) + Status.entries.map { StatusOption(it, capitalize(it.label), SpeqaIcons.forStatus(it)) }
        showFacetChooser(Facet.STATUS, anchor, e, options, filter.status) { picked ->
            filter.status = picked.value
            refresh()
            onChanged()
        }
    }

    private fun showPriorityPopup(e: AnActionEvent, anchor: JComponent?) {
        if (!shouldOpen(Facet.PRIORITY)) return
        val options = listOf(
            PriorityOption(null, SpeqaBundle.message("toolwindow.speqa.filter.allPriorities"), null),
        ) + Priority.entries.map { PriorityOption(it, capitalize(it.label), null) }
        showFacetChooser(Facet.PRIORITY, anchor, e, options, filter.priority) { picked ->
            filter.priority = picked.value
            refresh()
            onChanged()
        }
    }

    private fun <E : Enum<E>, O : FilterOption<E>> showFacetChooser(
        facet: Facet,
        anchor: JComponent?,
        e: AnActionEvent,
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
        openPopup(facet, newPopup, anchor, e)
    }

    private fun showTagsPopup(e: AnActionEvent, anchor: JComponent?) {
        if (!shouldOpen(Facet.TAGS)) return
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
        showCloudPopup(Facet.TAGS, anchor, e, cloud)
    }

    private fun showEnvironmentPopup(e: AnActionEvent, anchor: JComponent?) {
        if (!shouldOpen(Facet.ENVIRONMENT)) return
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
        showCloudPopup(Facet.ENVIRONMENT, anchor, e, cloud)
    }

    private fun showCloudPopup(facet: Facet, anchor: JComponent?, e: AnActionEvent, cloud: TagCloud) {
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
        openPopup(facet, newPopup, anchor, e)
        cloud.startAdd()
    }

    private fun capitalize(label: String): String = label.replaceFirstChar { it.uppercase() }

    private enum class Facet { STATUS, PRIORITY, TAGS, ENVIRONMENT }

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
