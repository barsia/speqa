package io.github.barsia.speqa.editor.ui.run

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import io.github.barsia.speqa.editor.ui.steps.DragReorderSupport
import io.github.barsia.speqa.editor.ui.steps.LivePreviewReorderDecorator
import io.github.barsia.speqa.editor.ui.steps.stepSlotsFromComponents
import io.github.barsia.speqa.model.TestRun
import io.github.barsia.speqa.run.TestRunSupport
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingUtilities

/** How a run renders: a flat single-case form, or a vertical list of case sections. */
enum class RunLayout { FLAT, SECTIONED }

/**
 * Vertical list of [RunCaseSection]s, one per case in a multi-case [TestRun].
 * Each section edit is folded back into the whole run and
 * reported through [onRunChange] (full reserialize — there is no per-case patch
 * path). Collapse state lives implicitly in each [RunCaseSection] and is
 * preserved across [update] as long as the case count is unchanged.
 *
 * Whole sections can be reordered by dragging a section's [RunCaseSection.dragHandle].
 * The drag-and-drop machinery (ghost overlay, drop indicator, live-preview
 * neighbour shift, auto-scroll) is reused verbatim from the step editor via
 * [DragReorderSupport] + [LivePreviewReorderDecorator]; on drop the run's cases
 * are reordered through [TestRunSupport.moveCase] and the whole run is emitted.
 * Drag-to-reorder is active only in the multi-case view (two or more sections).
 */
class RunCasesContainer(
    private val project: Project,
    private val file: VirtualFile?,
    initial: TestRun,
    private val onRunChange: (TestRun) -> Unit,
    private val onExpandedChanged: () -> Unit = {},
) : JPanel() {

    companion object {
        /** Decide the run layout: two or more cases render as collapsible sections. */
        fun layoutFor(run: TestRun): RunLayout =
            if (run.cases.size >= 2) RunLayout.SECTIONED else RunLayout.FLAT
    }

    private var current: TestRun = initial
    private var sections: List<RunCaseSection> = emptyList()
    private val sectionWrappers = mutableListOf<JComponent>()

    /**
     * Drag machinery, created lazily once the enclosing [JBScrollPane] is known
     * (resolved in [addNotify]). Until then sections render without DnD.
     */
    private var reorder: DragReorderSupport? = null
    private val livePreview = LivePreviewReorderDecorator(this)

    init {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        isOpaque = false
        alignmentX = Component.LEFT_ALIGNMENT
        rebuild(initial)
    }

    /** Expand or collapse every case section at once. */
    fun setAllExpanded(expanded: Boolean) {
        sections.forEach { it.setExpanded(expanded) }
        onExpandedChanged()
    }

    /** True when at least one section is currently collapsed (so "Expand all" can do something). */
    fun hasCollapsed(): Boolean = sections.any { !it.isExpanded() }

    /** True when at least one section is currently expanded (so "Collapse all" can do something). */
    fun hasExpanded(): Boolean = sections.any { it.isExpanded() }

    /** Refresh from [run]; update sections in place when the count is unchanged, else rebuild. */
    fun update(run: TestRun) {
        current = run
        if (run.cases.size == sections.size) {
            run.cases.forEachIndexed { i, case -> sections[i].update(case) }
        } else {
            rebuild(run)
        }
    }

    override fun addNotify() {
        super.addNotify()
        if (reorder == null) {
            val scrollPane = SwingUtilities.getAncestorOfClass(JBScrollPane::class.java, this) as? JBScrollPane
            if (scrollPane != null) {
                reorder = createReorder(scrollPane)
                // Sections built in init/rebuild persist across tab switches (Swing keeps the
                // component tree; removeNotify only detaches DnD). So on (re)show, just re-wire the
                // drag handles onto the existing sections instead of rebuilding the whole editor
                // tree - rebuilding recreates every case body and its EditorFactory markdown
                // editors synchronously on the EDT, which is what made tab switching slow.
                if (sections.isEmpty()) rebuild(current) else attachReorderHandles()
            }
        }
    }

    override fun removeNotify() {
        reorder?.detach()
        // Null it so a later addNotify (e.g. switching editor tabs away and back)
        // re-resolves the scroll pane, recreates the support, and re-attaches the
        // handles; otherwise drag-to-reorder would stay dead after the first detach.
        // The sections themselves are kept (not rebuilt) on re-show, so collapse state and
        // the per-case editors survive the tab switch.
        reorder = null
        super.removeNotify()
    }

    private fun createReorder(scrollPane: JBScrollPane): DragReorderSupport =
        DragReorderSupport(
            container = this,
            scrollPane = scrollPane,
            onReorder = ::performReorder,
            onDragStart = { draggedIndex, cardHeight, gap -> livePreview.onDragStart(draggedIndex, cardHeight, gap) },
            onDragUpdate = { dropTargetIndex -> livePreview.onDragUpdate(dropTargetIndex) },
            onDragEnd = { livePreview.onDragEnd() },
            onDragCancelStart = { livePreview.onDragCancelStart() },
            onDragCancelComplete = { livePreview.onDragCancelComplete() },
        )

    private fun performReorder(from: Int, to: Int) {
        if (from == to) return
        val updated = TestRunSupport.moveCase(current, from, to)
        if (updated === current) return
        current = updated
        onRunChange(updated)
        rebuild(updated)
    }

    private fun rebuild(run: TestRun) {
        reorder?.detach()
        removeAll()
        current = run
        sectionWrappers.clear()
        val built = ArrayList<RunCaseSection>(run.cases.size)
        run.cases.forEachIndexed { index, case ->
            val section = RunCaseSection(
                project = project,
                file = file,
                initial = case,
                onCaseChange = { updatedCase ->
                    val updatedRun = current.copy(
                        cases = current.cases.mapIndexed { idx, c -> if (idx == index) updatedCase else c },
                    )
                    current = updatedRun
                    onRunChange(updatedRun)
                },
                onExpandedChanged = onExpandedChanged,
            )
            section.alignmentX = Component.LEFT_ALIGNMENT
            built.add(section)
        }
        sections = built

        val wrappers = livePreview.install(built)
        wrappers.forEachIndexed { index, wrapper ->
            wrapper.alignmentX = Component.LEFT_ALIGNMENT
            if (index > 0) add(Box.createVerticalStrut(JBUI.scale(16)))
            add(wrapper)
            sectionWrappers.add(wrapper)
        }
        attachReorderHandles()
        revalidate()
        repaint()
        // New sections are expanded by default; let the host refresh its expand/collapse actions.
        onExpandedChanged()
    }

    /**
     * Wire the drag-to-reorder gesture onto the existing sections' handles. Idempotent enough for
     * re-show: [removeNotify] detaches first, so a later [addNotify] re-attaches a single gesture
     * without recreating any section. Drag-to-reorder only makes sense with two or more sections.
     */
    private fun attachReorderHandles() {
        val activeReorder = reorder ?: return
        if (sections.size < 2) return
        sections.forEach { section ->
            activeReorder.attachHandle(
                card = section,
                dragHandle = section.dragHandle,
                index = { sections.indexOf(section) },
                slotProvider = {
                    stepSlotsFromComponents(
                        components = components,
                        originalIndexOf = { component ->
                            sectionWrappers.indexOf(component).takeIf { it >= 0 }
                        },
                    )
                },
            )
        }
    }

    override fun paintChildren(g: Graphics) {
        super.paintChildren(g)
        val dropIndex = reorder?.dropTargetIndex ?: -1
        if (dropIndex < 0 || sectionWrappers.isEmpty()) return
        // Live-preview opens the landing slot itself; the line would be redundant.
        if (livePreview.isActive()) return
        val g2 = g.create() as Graphics2D
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.color = JBColor.namedColor("Link.activeForeground", JBColor.BLUE)
            val y = when {
                dropIndex <= 0 -> sectionWrappers.first().y
                dropIndex >= sectionWrappers.size -> {
                    val last = sectionWrappers.last()
                    last.y + last.height
                }
                else -> sectionWrappers[dropIndex].y
            }
            val thickness = JBUI.scale(2)
            g2.fillRect(0, y - thickness / 2, width, thickness)
        } finally {
            g2.dispose()
        }
    }
}
