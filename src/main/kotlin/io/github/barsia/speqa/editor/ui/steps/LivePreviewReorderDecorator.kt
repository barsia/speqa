package io.github.barsia.speqa.editor.ui.steps

import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.Timer

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
    private val wrappers = LinkedHashMap<JComponent, TranslatingWrapper>()
    // Per-wrapper [current, target] translateY in pixels.
    private val offsets = LinkedHashMap<TranslatingWrapper, FloatArray>()

    private var draggedIndex: Int = -1
    private var slotSize: Int = 0
    private var active: Boolean = false

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
        active = false
        return cards.map { card ->
            val wrapper = TranslatingWrapper(card)
            wrappers[card] = wrapper
            offsets[wrapper] = FloatArray(2) // [current, target]
            wrapper
        }
    }

    fun isActive(): Boolean = active

    fun onDragStart(draggedIndex: Int, cardHeight: Int, interSlotGap: Int) {
        this.draggedIndex = draggedIndex
        this.slotSize = cardHeight + interSlotGap
        this.active = true
        // All targets start at 0; they'll be populated on the first update.
        offsets.values.forEach { slot ->
            slot[1] = 0f
        }
        if (!timer.isRunning) timer.start()
    }

    fun onDragUpdate(dropTargetIndex: Int) {
        if (!active || draggedIndex < 0) return
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
        active = false
        if (!timer.isRunning) timer.start()
    }

    fun onCancel() {
        onDragEnd()
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
                wrapper.setTranslateY(next)
            }
            if (!(target == 0f && next == 0f)) {
                allAtRest = false
            }
        }
        if (allAtRest && !active) {
            timer.stop()
        }
    }

    /**
     * Thin wrapper panel that delegates sizing to its single child but
     * translates painting vertically by [translateY].
     */
    private class TranslatingWrapper(child: JComponent) : JPanel(BorderLayout()) {
        private var translateY: Float = 0f

        init {
            isOpaque = false
            border = null
            alignmentX = Component.LEFT_ALIGNMENT
            add(child, BorderLayout.CENTER)
        }

        fun setTranslateY(value: Float) {
            if (value == translateY) return
            translateY = value
            repaint()
        }

        override fun getPreferredSize(): Dimension = getComponent(0).preferredSize
        override fun getMinimumSize(): Dimension = getComponent(0).minimumSize
        override fun getMaximumSize(): Dimension = getComponent(0).maximumSize

        override fun paintComponent(g: Graphics) {
            // Nothing to paint on the wrapper itself (transparent).
            super.paintComponent(g)
        }

        override fun paintChildren(g: Graphics) {
            if (translateY == 0f) {
                super.paintChildren(g)
                return
            }
            val g2 = g.create() as Graphics2D
            try {
                g2.translate(0, translateY.toInt())
                super.paintChildren(g2)
            } finally {
                g2.dispose()
            }
        }
    }

    companion object {
        private const val FRAME_MS: Int = 16
        private const val EASE_FACTOR: Float = 0.25f
        private const val SNAP_EPSILON: Float = 0.5f
    }
}
