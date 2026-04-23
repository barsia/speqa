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
import io.github.barsia.speqa.model.TestStep
import io.github.barsia.speqa.parser.PatchOperation
import java.awt.Component
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
 * Swing port of `editor/ui/StepsSection.kt`. Hosts [StepCard]s plus a trailing
 * "+ Add step" button. Wires `DragReorderSupport` for drag-to-reorder, and
 * paints the drop-indicator line itself in [paintChildren] (the support class
 * exposes [DragReorderSupport.dropTargetIndex]).
 *
 * Live-preview reorder (neighbour shift) is intentionally not ported — see the
 * migration plan §1 non-goals. Drop indicator + auto-scroll only.
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
) : JPanel() {

    private var steps: List<TestStep> = emptyList()
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
    private val deleteRestorer = DeleteFocusRestorer(
        itemProvider = { cards.getOrNull(it)?.actionArea },
        addButton = addButton,
    )

    init {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        isOpaque = false
        add(addButton)
    }

    fun setSteps(newSteps: List<TestStep>) {
        steps = newSteps.toList()
        rebuild()
    }

    fun updateStepsInPlace(newSteps: List<TestStep>) {
        if (newSteps.size != cards.size) {
            setSteps(newSteps)
            return
        }
        steps = newSteps.toList()
        cards.forEachIndexed { i, card -> card.setStep(newSteps[i]) }
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
            val freshCards = steps.mapIndexed { index, step ->
                val sizeBefore = steps.size
                val card = StepCard(
                    initialStep = step,
                    initialIndex = index,
                    project = project,
                    tcFile = tcFile,
                    onChange = { updated -> updateStep(index, updated) },
                    onDelete = {
                        val next = steps.toMutableList().also { it.removeAt(index) }
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
        add(javax.swing.Box.createVerticalStrut(JBUI.scale(6)))
        addButton.alignmentX = Component.LEFT_ALIGNMENT
        add(addButton)
        revalidate()
        repaint()
    }

    private fun updateStep(index: Int, updated: TestStep) {
        val next = steps.toMutableList()
        if (index !in next.indices) return
        val previous = next[index]
        next[index] = updated
        steps = next
        onStepsChange(next)
        emitStepFieldPatches(index, previous, updated)
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
        onStepsChange(next)
        onStepPatch?.invoke(PatchOperation.AddStep(copy))
        rebuild()
        SwingUtilities.invokeLater { cards.getOrNull(index + 1)?.focusAction() }
    }

    private fun performReorder(from: Int, to: Int) {
        if (from == to || from !in steps.indices) return
        val next = steps.toMutableList()
        val item = next.removeAt(from)
        val clamped = to.coerceIn(0, next.size)
        next.add(clamped, item)
        steps = next
        onStepsChange(next)
        onStepPatch?.invoke(PatchOperation.ReorderSteps(from, clamped))
        rebuild()
    }

    private fun buildAddButton(): JComponent {
        val panel = JPanel(java.awt.FlowLayout(java.awt.FlowLayout.LEFT, JBUI.scale(4), 0))
        panel.isOpaque = false
        panel.handCursor()
        panel.isFocusable = true
        panel.add(JBLabel(SpeqaBundle.message("form.addStep")))
        panel.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    panel.requestFocusInWindow()
                    addStep()
                }
            }
        })
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
        SwingUtilities.invokeLater {
            cards.lastOrNull()?.focusAction()
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
