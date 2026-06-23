package io.github.barsia.speqa.editor.ui.steps

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import io.github.barsia.speqa.SpeqaBundle
import io.github.barsia.speqa.editor.ui.primitives.DeleteFocusRestorer
import io.github.barsia.speqa.editor.ui.primitives.handCursor
import io.github.barsia.speqa.model.StepResult
import io.github.barsia.speqa.model.StepVerdict
import io.github.barsia.speqa.model.TestStep
import io.github.barsia.speqa.parser.PatchOperation
import java.awt.Component
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingUtilities

/**
 * Hosts [StepCard]s plus a trailing "+ Add step" button. Wires
 * `DragReorderSupport` for drag-to-reorder, and paints the drop-indicator line
 * itself in [paintChildren] (the support class exposes
 * [DragReorderSupport.dropTargetIndex]).
 *
 * Live-preview reorder (neighbour shift) is intentionally omitted.
 * Drop indicator + auto-scroll only.
 */
class StepsSection(
    private val scrollPane: JBScrollPane,
    private val project: Project?,
    private val tcFile: VirtualFile?,
    private val onStepsChange: (List<TestStep>) -> Unit,
    /**
     * Optional fine-grained patch sink. When set, per-step mutations are also
     * emitted as [PatchOperation] values so `SpeqaPreviewEditor` can route
     * them through `patchFromPreview` for surgical document edits. The
     * [onStepsChange] list callback still fires alongside so the panel's local
     * snapshot stays in sync.
     */
    private val onStepPatch: ((PatchOperation) -> Unit)? = null,
    private val runMode: Boolean = false,
    private val onStepVerdictChange: ((Int, StepVerdict) -> Unit)? = null,
    private val onStepCommentChange: ((Int, String) -> Unit)? = null,
) : JPanel() {

    private var steps: List<TestStep> = emptyList()
    /**
     * Authoritative run-mode mirror of the rendered [StepResult]s. Card
     * callbacks (verdict / comment / per-field edits) and structural ops
     * (delete / reorder / duplicate) must update this list so subsequent
     * structural ops do not snapshot stale `verdict` / `comment` values
     * from an earlier `rebuildRun` capture.
     */
    private var runResults: List<StepResult> = emptyList()
    private val cards = mutableListOf<StepCard>()
    private val cardWrappers = mutableListOf<JComponent>()
    private val addButton: JComponent = buildAddButton()
    private val livePreview = LivePreviewReorderDecorator(this)
    private var livePreviewEnabled: Boolean = true
    private val reorder = DragReorderSupport(
        container = this,
        scrollPane = scrollPane,
        onReorder = ::performReorder,
        onDragStart = { draggedIndex, cardHeight, gap ->
            if (livePreviewEnabled) livePreview.onDragStart(draggedIndex, cardHeight, gap)
        },
        onDragUpdate = { dropTargetIndex ->
            if (livePreviewEnabled) livePreview.onDragUpdate(dropTargetIndex)
        },
        onDragEnd = {
            if (livePreviewEnabled) livePreview.onDragEnd()
        },
        onDragCancelStart = {
            if (livePreviewEnabled) livePreview.onDragCancelStart()
        },
        onDragCancelComplete = {
            if (livePreviewEnabled) livePreview.onDragCancelComplete()
        },
    )

    /**
     * Toggle live-preview neighbour-shift animation. When disabled, the
     * baseline ghost + drop-indicator line is used. Default: enabled.
     */
    fun setLivePreviewEnabled(enabled: Boolean) {
        livePreviewEnabled = enabled
    }

    /** Number of step cards currently rendered. */
    val stepCount: Int get() = cards.size

    /** 1-based source line of the step at [index] in the document, or 0 if unknown. */
    fun stepSourceLine(index: Int): Int = steps.getOrNull(index)?.sourceLine ?: 0

    /**
     * Y position of the step card at [index] relative to the JBScrollPane's
     * content root (the component directly inside the JViewport). Returns null
     * if [index] is out of range or the component is not yet in a scroll pane.
     */
    fun cardAbsoluteY(index: Int): Int? {
        val wrapper = cardWrappers.getOrNull(index) ?: return null
        // Walk up to find the component directly inside a JViewport; that is
        // the coordinate space the scroll bar value refers to.
        var root: java.awt.Component = this
        while (root.parent != null && root.parent !is javax.swing.JViewport) {
            root = root.parent
        }
        if (root.parent == null) return null
        return javax.swing.SwingUtilities.convertPoint(wrapper, 0, 0, root).y
    }
    private val deleteRestorer = DeleteFocusRestorer(
        itemProvider = { cards.getOrNull(it)?.actionArea },
        addButton = addButton,
    )

    // Indent the Add step button so its left edge lines up with the
    // step's action text-field left edge. StepCard left border 4 +
    // gutter (~16px for 2-digit step numbers, capped to drag-handle floor)
    // + 8 gutter-to-content gap = 28px from card left.
    private val addRow = javax.swing.Box.createHorizontalBox().apply {
        alignmentX = Component.LEFT_ALIGNMENT
        add(javax.swing.Box.createHorizontalStrut(JBUI.scale(28)))
        add(addButton)
    }

    init {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        isOpaque = false
        if (!runMode) add(addRow)
    }

    fun setSteps(newSteps: List<TestStep>) {
        steps = newSteps.toList()
        rebuild()
    }

    fun updateStepsInPlace(newSteps: List<TestStep>, forceFocusedTextSync: Boolean = false) {
        if (canAppendCaseStepInPlace(newSteps)) {
            val previousSteps = steps
            steps = newSteps.toList()
            previousSteps.indices.forEach { i ->
                if (previousSteps[i] != newSteps[i]) {
                    cards[i].setStep(newSteps[i], forceFocusedTextSync = forceFocusedTextSync)
                }
            }
            appendCaseStep(newSteps.last())
            return
        }
        if (newSteps.size != cards.size) {
            setSteps(newSteps)
            return
        }
        steps = newSteps.toList()
        cards.forEachIndexed { i, card -> card.setStep(newSteps[i], forceFocusedTextSync = forceFocusedTextSync) }
    }

    private fun canAppendCaseStepInPlace(newSteps: List<TestStep>): Boolean =
        !runMode &&
            !livePreview.isActive() &&
            steps.isNotEmpty() &&
            cards.size == steps.size &&
            newSteps.size == steps.size + 1 &&
            steps.indices.all { i -> steps[i] == newSteps[i] }

    /**
     * Install the run-step list. [runSteps] are the synthetic `TestStep`
     * wrappers owned by the parent panel — their `uid`s identify each row so
     * the parent can correlate edits back to the matching [StepResult] when
     * preserving `verdict` / `comment`. [results] supplies the run-only
     * execution state for each row at the same index.
     */
    fun setRunStepResults(runSteps: List<TestStep>, results: List<StepResult>) {
        require(runSteps.size == results.size) {
            "runSteps and results must have the same size"
        }
        steps = runSteps.toList()
        runResults = results.toList()
        rebuildRun()
    }

    fun updateRunStepResultsInPlace(
        runSteps: List<TestStep>,
        results: List<StepResult>,
        forceFocusedTextSync: Boolean = false,
    ) {
        require(runSteps.size == results.size) {
            "runSteps and results must have the same size"
        }
        if (results.size != cards.size) {
            setRunStepResults(runSteps, results)
            return
        }
        runSteps.forEachIndexed { i, newStep ->
            val card = cards[i]
            if (steps[i] != newStep) card.setStep(newStep, forceFocusedTextSync = forceFocusedTextSync)
            card.setRunVerdict(results[i].verdict)
            card.setRunComment(results[i].comment, forceFocusedTextSync = forceFocusedTextSync)
        }
        steps = runSteps.toList()
        runResults = results.toList()
    }

    private fun rebuildRun() {
        reorder.detach()
        removeAll()
        cards.clear()
        cardWrappers.clear()
        if (runResults.isEmpty()) {
            val empty = JBLabel(SpeqaBundle.message("form.emptySteps"))
            empty.foreground = JBColor.namedColor("Label.disabledForeground", JBColor.GRAY)
            empty.alignmentX = Component.LEFT_ALIGNMENT
            add(empty)
        } else {
            val freshCards = runResults.mapIndexed { index, result ->
                val step = steps[index]
                val card = StepCard(
                    initialStep = step,
                    initialIndex = index,
                    project = project,
                    tcFile = tcFile,
                    mode = StepMode.RUN,
                    runVerdict = result.verdict,
                    runComment = result.comment,
                    onVerdictChange = { verdict ->
                        mutateRunResult(index) { it.copy(verdict = verdict) }
                        onStepVerdictChange?.invoke(index, verdict)
                    },
                    onCommentChange = { comment ->
                        mutateRunResult(index) { it.copy(comment = comment) }
                        onStepCommentChange?.invoke(index, comment)
                    },
                    // Route every per-field edit (action/expected/tickets/links/attachments)
                    // through `updateStep` so it emits the appropriate
                    // PatchOperation via `onStepPatch`. Without this, edits in
                    // run mode were silently dropped because the callback was
                    // a no-op.
                    onChange = { updated -> updateStep(index, updated) },
                    onDelete = {
                        val next = steps.toMutableList().also { it.removeAt(index) }
                        val sizeBefore = steps.size
                        steps = next
                        // Fire onStepsChange before the patch so the parent
                        // (TestCasePanel) refreshes currentRun.stepResults
                        // first; otherwise DocumentPatcher's fallback
                        // serializes the stale snapshot and the deleted step
                        // pops back. CASE-mode delete does the same.
                        onStepsChange(next)
                        onStepPatch?.invoke(PatchOperation.DeleteStep(index))
                        deleteRestorer.onDeleted(index, sizeBefore)
                        // Rebuild from the authoritative runResults snapshot,
                        // not from a value captured at original rebuildRun
                        // time — verdict/comment edits on neighbouring rows
                        // would otherwise revert here.
                        setRunStepResults(
                            next,
                            runResults.toMutableList().also { it.removeAt(index) },
                        )
                    },
                    onMoveUp = { performReorder(index, index - 1) },
                    onMoveDown = { performReorder(index, index + 1) },
                    onDuplicate = { duplicateStep(index) },
                    canMoveUp = { index > 0 },
                    canMoveDown = { index < runResults.lastIndex },
                )
                card.alignmentX = Component.LEFT_ALIGNMENT
                card
            }
            cards.addAll(freshCards)
            val wrapped = livePreview.install(freshCards)
            cardWrappers.addAll(wrapped)
            wrapped.forEachIndexed { index, wrapper ->
                wrapper.alignmentX = Component.LEFT_ALIGNMENT
                if (index > 0) add(javax.swing.Box.createVerticalStrut(JBUI.scale(6)))
                add(wrapper)
                val card = freshCards[index]
                reorder.attachHandle(
                    card = card,
                    dragHandle = card.dragHandle,
                    index = { cards.indexOf(card) },
                    slotProvider = {
                        stepSlotsFromComponents(
                            components = components,
                            originalIndexOf = { component ->
                                cardWrappers.indexOf(component).takeIf { it >= 0 }
                            },
                        )
                    },
                )
            }
        }
        revalidate()
        repaint()
    }

    private fun rebuild() {
        reorder.detach()
        removeAll()
        cards.clear()
        cardWrappers.clear()
        if (steps.isEmpty()) {
            val empty = JBLabel(SpeqaBundle.message("form.emptySteps"))
            empty.foreground = JBColor.namedColor("Label.disabledForeground", JBColor.GRAY)
            empty.alignmentX = Component.LEFT_ALIGNMENT
            add(empty)
        } else {
            val freshCards = steps.mapIndexed(::createCaseCard)
            cards.addAll(freshCards)
            val wrapped = livePreview.install(freshCards)
            cardWrappers.addAll(wrapped)
            wrapped.forEachIndexed { index, wrapper ->
                wrapper.alignmentX = Component.LEFT_ALIGNMENT
                if (index > 0) add(javax.swing.Box.createVerticalStrut(JBUI.scale(6)))
                add(wrapper)
                val card = freshCards[index]
                reorder.attachHandle(
                    card = card,
                    dragHandle = card.dragHandle,
                    index = { cards.indexOf(card) },
                    slotProvider = {
                        stepSlotsFromComponents(
                            components = components,
                            originalIndexOf = { component ->
                                cardWrappers.indexOf(component).takeIf { it >= 0 }
                            },
                        )
                    },
                )
            }
        }
        if (!runMode) {
            add(javax.swing.Box.createVerticalStrut(JBUI.scale(6)))
            add(addRow)
        }
        revalidate()
        repaint()
    }

    private fun appendCaseStep(step: TestStep) {
        val index = cards.size
        val card = createCaseCard(index, step)
        val wrapper = livePreview.append(card).apply {
            alignmentX = Component.LEFT_ALIGNMENT
        }
        cards.add(card)
        cardWrappers.add(wrapper)

        remove(addRow)
        add(wrapper)
        add(javax.swing.Box.createVerticalStrut(JBUI.scale(6)))
        add(addRow)
        attachReorderHandle(card)
        revalidate()
        repaint()
    }

    private fun createCaseCard(index: Int, step: TestStep): StepCard {
        val card = StepCard(
            initialStep = step,
            initialIndex = index,
            project = project,
            tcFile = tcFile,
            mode = StepMode.CASE,
            onChange = { updated -> updateStep(index, updated) },
            onDelete = {
                val next = steps.toMutableList().also { it.removeAt(index) }
                val sizeBefore = steps.size
                steps = next
                onStepsChange(next)
                onStepPatch?.invoke(PatchOperation.DeleteStep(index))
                deleteRestorer.onDeleted(index, sizeBefore)
                rebuild()
            },
            onMoveUp = { performReorder(index, index - 1) },
            onMoveDown = { performReorder(index, index + 1) },
            onDuplicate = { duplicateStep(index) },
            canMoveUp = { index > 0 },
            canMoveDown = { index < steps.size - 1 },
        )
        card.alignmentX = Component.LEFT_ALIGNMENT
        return card
    }

    private fun attachReorderHandle(card: StepCard) {
        reorder.attachHandle(
            card = card,
            dragHandle = card.dragHandle,
            index = { cards.indexOf(card) },
            slotProvider = {
                stepSlotsFromComponents(
                    components = components,
                    originalIndexOf = { component ->
                        cardWrappers.indexOf(component).takeIf { it >= 0 }
                    },
                )
            },
        )
    }

    private fun updateStep(index: Int, updated: TestStep) {
        val next = steps.toMutableList()
        if (index !in next.indices) return
        val previous = next[index]
        next[index] = updated
        steps = next
        if (runMode) {
            // Mirror the action/expected/tickets/links/attachments edits into
            // the authoritative run-mode snapshot, preserving verdict/comment.
            mutateRunResult(index) { prev ->
                prev.copy(
                    action = updated.action,
                    expected = updated.expected.orEmpty(),
                    tickets = updated.tickets,
                    links = updated.links,
                    attachments = updated.attachments,
                )
            }
        }
        onStepsChange(next)
        emitStepFieldPatches(index, previous, updated)
    }

    /**
     * Update the local [runResults] entry at [index]. No-op in case mode or
     * if [index] is out of range.
     */
    private fun mutateRunResult(index: Int, transform: (StepResult) -> StepResult) {
        if (!runMode) return
        if (index !in runResults.indices) return
        val next = runResults.toMutableList()
        next[index] = transform(next[index])
        runResults = next
    }

    /**
     * Emit one [PatchOperation] per mutated leaf field on the step at [index].
     * Only one field typically changes per user interaction (e.g. typing in the
     * action text area) — but the diff-and-emit loop handles multi-field edits
     * safely (e.g. structural events that change tickets + links at once).
     */
    private fun emitStepFieldPatches(index: Int, previous: TestStep, updated: TestStep) {
        val sink = onStepPatch ?: return
        if (previous.action != updated.action) {
            sink(PatchOperation.SetStepAction(index, updated.action))
        }
        if (previous.expected != updated.expected) {
            sink(PatchOperation.SetStepExpected(index, updated.expected))
        }
        if (previous.tickets != updated.tickets) {
            sink(PatchOperation.SetStepTickets(index, updated.tickets))
        }
        if (previous.links != updated.links) {
            sink(PatchOperation.SetStepLinks(index, updated.links))
        }
        if (previous.attachments != updated.attachments) {
            sink(PatchOperation.SetStepAttachments(index, updated.attachments))
        }
    }

    private fun duplicateStep(index: Int) {
        val source = steps.getOrNull(index) ?: return
        val next = steps.toMutableList()
        val copy = source.copy(uid = TestStep.nextUid())
        next.add(index + 1, copy)
        steps = next
        if (runMode) {
            // Duplicates get a fresh run result (verdict=NONE, comment="");
            // action/expected/meta are populated from the wrapper TestStep.
            val nextResults = runResults.toMutableList()
            nextResults.add(
                index + 1,
                StepResult(
                    action = copy.action,
                    expected = copy.expected.orEmpty(),
                    tickets = copy.tickets,
                    links = copy.links,
                    attachments = copy.attachments,
                ),
            )
            runResults = nextResults
        }
        onStepsChange(next)
        val sink = onStepPatch
        if (sink != null) {
            // `AddStep` always appends to the end of the scenario. To land the
            // copy directly after the source, follow up with a `ReorderSteps`
            // that moves the appended step (now at the last index of the
            // post-add document) to `index + 1`. The ops are applied
            // sequentially against the live document so the reorder sees the
            // post-add layout.
            val appendedIndex = steps.size - 1
            val targetIndex = index + 1
            sink(PatchOperation.AddStep(copy))
            if (appendedIndex != targetIndex) {
                sink(PatchOperation.ReorderSteps(appendedIndex, targetIndex))
            }
        }
        if (runMode) rebuildRun() else rebuild()
        SwingUtilities.invokeLater { cards.getOrNull(index + 1)?.focusAction() }
    }

    private fun performReorder(from: Int, to: Int) {
        if (from == to || from !in steps.indices) return
        val next = steps.toMutableList()
        val item = next.removeAt(from)
        val clamped = to.coerceIn(0, next.size)
        next.add(clamped, item)
        steps = next
        if (runMode && from in runResults.indices) {
            val nextResults = runResults.toMutableList()
            val movedResult = nextResults.removeAt(from)
            nextResults.add(clamped.coerceIn(0, nextResults.size), movedResult)
            runResults = nextResults
        }
        onStepsChange(next)
        onStepPatch?.invoke(PatchOperation.ReorderSteps(from, clamped))
        if (runMode) rebuildRun() else rebuild()
    }

    private fun buildAddButton(): JComponent {
        val mutedFg = com.intellij.ui.JBColor.namedColor(
            "Label.disabledForeground",
            com.intellij.ui.JBColor.GRAY,
        )
        val accentFg = com.intellij.ui.JBColor.namedColor("Link.activeForeground", com.intellij.ui.JBColor.BLUE)

        // Scale the default 16x16 add icon down by 0.75 -> ~12x12 logical
        // pixels (visually balanced with the label text). Tint to
        // the same colour as the label text so the icon and text read as a
        // single typographic unit.
        val basePlus = com.intellij.util.IconUtil.scale(
            com.intellij.icons.AllIcons.General.Add,
            null,
            0.75f,
        )
        val mutedPlus = com.intellij.util.IconUtil.colorize(basePlus, mutedFg)
        val accentPlus = com.intellij.util.IconUtil.colorize(basePlus, accentFg)
        val label = JBLabel(SpeqaBundle.message("form.addStep"), mutedPlus, javax.swing.SwingConstants.LEFT).apply {
            iconTextGap = JBUI.scale(6)
            foreground = mutedFg
        }

        val panel = JPanel(java.awt.FlowLayout(java.awt.FlowLayout.LEFT, JBUI.scale(4), 0))
        panel.isOpaque = false
        panel.handCursor()
        panel.isFocusable = true
        panel.add(label)

        val clickAction: () -> Unit = {
            panel.requestFocusInWindow()
            addStep()
        }

        val hoverListener = object : MouseAdapter() {
            override fun mouseEntered(e: MouseEvent) {
                label.foreground = accentFg
                label.icon = accentPlus
            }
            override fun mouseExited(e: MouseEvent) {
                label.foreground = mutedFg
                label.icon = mutedPlus
            }
            override fun mouseClicked(e: MouseEvent) {
                if (SwingUtilities.isLeftMouseButton(e)) clickAction()
            }
        }
        panel.addMouseListener(hoverListener)
        // JBLabel doesn't propagate mouse events to its parent, so a click on
        // the label text would be silently dropped without this duplicate
        // listener.
        label.addMouseListener(hoverListener)

        panel.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if (e.keyCode == KeyEvent.VK_ENTER || e.keyCode == KeyEvent.VK_SPACE) {
                    addStep(); e.consume()
                }
            }
        })
        return panel
    }

    private fun addStep() {
        val newStep = TestStep()
        val next = steps + newStep
        steps = next
        onStepsChange(next)
        onStepPatch?.invoke(PatchOperation.AddStep(newStep))
        rebuild()
        // Scroll to the new card on the NEXT-next EDT tick (double-deferred).
        // SpeqaPreviewEditor.patchFromPreview also defers its document-write
        // + scrollSync.restoreVerticalPosition via invokeLater; if we focus the
        // new card before that, focus-traversal races with the scroll restore
        // and the viewport jumps to the top of the panel. Deferring past
        // both ticks keeps the new step in view without grabbing focus.
        SwingUtilities.invokeLater {
            SwingUtilities.invokeLater {
                cards.lastOrNull()?.let { card ->
                    card.scrollRectToVisible(java.awt.Rectangle(0, 0, card.width, card.height))
                }
            }
        }
    }

    override fun paintChildren(g: Graphics) {
        super.paintChildren(g)
        val dropIndex = reorder.dropTargetIndex
        if (dropIndex < 0 || cardWrappers.isEmpty()) return
        // Live-preview opens the landing slot itself; the line would be redundant.
        if (livePreviewEnabled && livePreview.isActive()) return
        val g2 = g.create() as Graphics2D
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.color = JBColor.namedColor("Link.activeForeground", JBColor.BLUE)
            val y = when {
                dropIndex <= 0 -> cardWrappers.first().y
                dropIndex >= cardWrappers.size -> {
                    val last = cardWrappers.last()
                    last.y + last.height
                }
                else -> cardWrappers[dropIndex].y
            }
            val thickness = JBUI.scale(2)
            g2.fillRect(0, y - thickness / 2, width, thickness)
        } finally {
            g2.dispose()
        }
    }

    override fun removeNotify() {
        reorder.detach()
        super.removeNotify()
    }
}
