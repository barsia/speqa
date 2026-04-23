package io.github.barsia.speqa.editor.ui.steps

import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Rectangle
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.Timer
import kotlin.math.roundToInt

/**
 * Pure decision for the per-sibling live-preview offset.
 *
 * While dragging a card from [draggedIndex] toward [dropTargetIndex], every
 * other card (at [siblingIndex]) shifts by:
 *
 *  - `-slotSize` when dragging **down** and the sibling sits between the
 *    vacated slot and the new slot: `draggedIndex < siblingIndex <= dropTargetIndex`.
 *  - `+slotSize` when dragging **up** and the sibling sits between the new
 *    slot and the vacated slot: `dropTargetIndex <= siblingIndex < draggedIndex`.
 *  - `0` otherwise.
 *
 * Callers pass `slotSize = cardHeight + interSlotGap`. The decorator animates
 * the returned target with an exponential lerp so the visual motion is smooth.
 */
fun livePreviewTargetOffset(
    siblingIndex: Int,
    draggedIndex: Int,
    dropTargetIndex: Int,
    slotSize: Int,
): Int {
    if (siblingIndex == draggedIndex) return 0
    if (dropTargetIndex == draggedIndex) return 0
    return when {
        dropTargetIndex > draggedIndex &&
            siblingIndex in (draggedIndex + 1)..dropTargetIndex -> -slotSize

        dropTargetIndex < draggedIndex &&
            siblingIndex in dropTargetIndex until draggedIndex -> slotSize

        else -> 0
    }
}

enum class LivePreviewPhase { IDLE, DRAGGING, RETURNING }

fun livePreviewShouldPaintCard(
    cardIndex: Int,
    draggedIndex: Int,
    phase: LivePreviewPhase,
): Boolean {
    return !(cardIndex == draggedIndex && phase != LivePreviewPhase.IDLE)
}

internal class LivePreviewWrapper(child: JComponent) : JPanel(BorderLayout()) {
    private var visualOffsetY: Float = 0f
    private var contentVisible: Boolean = true
    private var baseBounds: Rectangle? = null

    init {
        isOpaque = false
        border = null
        alignmentX = Component.LEFT_ALIGNMENT
        add(child, BorderLayout.CENTER)
    }

    fun setVisualOffset(value: Float) {
        if (value == visualOffsetY) return
        visualOffsetY = value
        applyVisualBounds()
    }

    fun setContentVisible(value: Boolean) {
        if (value == contentVisible) return
        contentVisible = value
        repaint()
    }

    override fun getPreferredSize(): Dimension = getComponent(0).preferredSize
    override fun getMinimumSize(): Dimension = getComponent(0).minimumSize
    override fun getMaximumSize(): Dimension = getComponent(0).maximumSize

    override fun setBounds(x: Int, y: Int, width: Int, height: Int) {
        baseBounds = Rectangle(x, y, width, height)
        super.setBounds(x, y + visualOffsetY.roundToInt(), width, height)
    }

    override fun paintChildren(g: Graphics) {
        if (!contentVisible) return
        super.paintChildren(g)
    }

    private fun applyVisualBounds() {
        val bounds = baseBounds ?: return
        super.setBounds(
            bounds.x,
            bounds.y + visualOffsetY.roundToInt(),
            bounds.width,
            bounds.height,
        )
        parent?.repaint()
    }
}

/**
 * Live-preview reorder decorator. Wraps each step card in a thin translating
 * panel; while a drag is active, neighbour wrappers animate toward a target
 * `translateY` so the landing slot visually opens up.
 *
 * Implementation choice: **wrapper `JPanel` with a transform in
 * `paintComponent`**, not `JLayer + LayerUI`. Rationale: Swing `BoxLayout`
 * already works naturally with plain `JPanel` children, the wrapper reports
 * its preferred size from the delegate child, and we only need a single
 * `g2.translate(0, y)` step — `LayerUI` adds boilerplate (glass-pane paint
 * pipeline, generic type parameters) for no gain here.
 *
 * Drop math, auto-scroll, and reorder commit remain owned by
 * [DragReorderSupport]. This decorator only animates `translateY` on wrappers.
 */
class LivePreviewReorderDecorator(
    @Suppress("unused") private val container: JPanel,
) {
    private val wrappers = LinkedHashMap<JComponent, LivePreviewWrapper>()
    // Per-wrapper [current, target] translateY in pixels.
    private val offsets = LinkedHashMap<LivePreviewWrapper, FloatArray>()

    private var draggedIndex: Int = -1
    private var slotSize: Int = 0
    private var phase: LivePreviewPhase = LivePreviewPhase.IDLE

    private val timer: Timer = Timer(FRAME_MS) { tick() }.apply { isRepeats = true }

    /**
     * Wrap [cards] in translating panels suitable for insertion into the
     * container. Resets any prior wrappers — callers must call this from their
     * `rebuild()` path so we never hold stale component references after a
     * reorder commit.
     */
    fun install(cards: List<JComponent>): List<JComponent> {
        wrappers.clear()
        offsets.clear()
        draggedIndex = -1
        phase = LivePreviewPhase.IDLE
        return cards.map { card ->
            val wrapper = LivePreviewWrapper(card)
            wrappers[card] = wrapper
            offsets[wrapper] = FloatArray(2) // [current, target]
            wrapper
        }
    }

    fun isActive(): Boolean = phase != LivePreviewPhase.IDLE

    fun onDragStart(draggedIndex: Int, cardHeight: Int, interSlotGap: Int) {
        this.draggedIndex = draggedIndex
        this.slotSize = cardHeight + interSlotGap
        this.phase = LivePreviewPhase.DRAGGING
        syncContentVisibility()
        // All targets start at 0; they'll be populated on the first update.
        offsets.values.forEach { slot ->
            slot[1] = 0f
        }
        if (!timer.isRunning) timer.start()
    }

    fun onDragUpdate(dropTargetIndex: Int) {
        if (phase != LivePreviewPhase.DRAGGING || draggedIndex < 0) return
        val keys = wrappers.keys.toList()
        keys.forEachIndexed { index, card ->
            val wrapper = wrappers[card] ?: return@forEachIndexed
            val slot = offsets[wrapper] ?: return@forEachIndexed
            slot[1] = livePreviewTargetOffset(
                siblingIndex = index,
                draggedIndex = draggedIndex,
                dropTargetIndex = dropTargetIndex,
                slotSize = slotSize,
            ).toFloat()
        }
        if (!timer.isRunning) timer.start()
    }

    fun onDragEnd() {
        // Animate everything back to 0; the timer will stop itself when at rest.
        offsets.values.forEach { slot -> slot[1] = 0f }
        phase = LivePreviewPhase.IDLE
        syncContentVisibility()
        if (!timer.isRunning) timer.start()
    }

    fun onDragCancelStart() {
        offsets.values.forEach { slot -> slot[1] = 0f }
        phase = LivePreviewPhase.RETURNING
        syncContentVisibility()
        if (!timer.isRunning) timer.start()
    }

    fun onDragCancelComplete() {
        phase = LivePreviewPhase.IDLE
        syncContentVisibility()
    }

    private fun tick() {
        var allAtRest = true
        offsets.forEach { (wrapper, slot) ->
            val current = slot[0]
            val target = slot[1]
            val delta = target - current
            val next = if (target == 0f && kotlin.math.abs(delta) < SNAP_EPSILON) {
                0f
            } else {
                current + delta * EASE_FACTOR
            }
            if (next != current) {
                slot[0] = next
                wrapper.setVisualOffset(next)
            }
            if (!(target == 0f && next == 0f)) {
                allAtRest = false
            }
        }
        if (allAtRest && phase == LivePreviewPhase.IDLE) {
            timer.stop()
        }
    }

    private fun syncContentVisibility() {
        wrappers.keys.forEachIndexed { index, card ->
            val wrapper = wrappers[card] ?: return@forEachIndexed
            wrapper.setContentVisible(
                livePreviewShouldPaintCard(
                    cardIndex = index,
                    draggedIndex = draggedIndex,
                    phase = phase,
                )
            )
        }
    }

    companion object {
        private const val FRAME_MS: Int = 16
        private const val EASE_FACTOR: Float = 0.25f
        private const val SNAP_EPSILON: Float = 0.5f
    }
}
