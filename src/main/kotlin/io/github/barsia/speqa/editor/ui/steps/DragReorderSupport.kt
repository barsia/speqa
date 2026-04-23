package io.github.barsia.speqa.editor.ui.steps

import com.intellij.openapi.wm.IdeGlassPaneUtil
import com.intellij.ui.components.JBScrollPane
import java.awt.AlphaComposite
import java.awt.Component
import java.awt.GraphicsConfiguration
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Point
import java.awt.RenderingHints
import java.awt.Toolkit
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseMotionAdapter
import java.awt.image.BufferedImage
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.Timer

internal data class DragCardSlot(
    val originalIndex: Int,
    val top: Int,
    val height: Int,
)

internal fun stepSlotsFromComponents(
    components: Array<out Component>,
    originalIndexOf: (Component) -> Int?,
): List<DragCardSlot> {
    return components.mapNotNull { component ->
        val originalIndex = originalIndexOf(component) ?: return@mapNotNull null
        DragCardSlot(
            originalIndex = originalIndex,
            top = component.y,
            height = component.height,
        )
    }.sortedBy { it.originalIndex }
}

internal fun siblingBoundsFromSlots(
    slots: List<DragCardSlot>,
    draggedIndex: Int,
): List<SiblingBounds> {
    return slots
        .filter { it.originalIndex != draggedIndex }
        .map { slot ->
            SiblingBounds(
                originalIndex = slot.originalIndex,
                top = slot.top,
                height = slot.height,
            )
        }
}

internal fun cardPressPointFromHandlePress(
    handlePressPoint: Point,
    handleLocationOnCard: Point,
): Point {
    return Point(
        handleLocationOnCard.x + handlePressPoint.x,
        handleLocationOnCard.y + handlePressPoint.y,
    )
}

internal fun interSlotGapPx(cardTop: Int, cardHeight: Int, nextCardTop: Int): Int {
    return (nextCardTop - (cardTop + cardHeight)).coerceAtLeast(0)
}

internal fun ghostSnapshotRenderingHints(
    desktopHints: Map<*, *>?,
): Map<RenderingHints.Key, Any> {
    val hints = linkedMapOf<RenderingHints.Key, Any>(
        RenderingHints.KEY_TEXT_ANTIALIASING to RenderingHints.VALUE_TEXT_ANTIALIAS_ON,
        RenderingHints.KEY_FRACTIONALMETRICS to RenderingHints.VALUE_FRACTIONALMETRICS_ON,
        RenderingHints.KEY_ANTIALIASING to RenderingHints.VALUE_ANTIALIAS_ON,
        RenderingHints.KEY_RENDERING to RenderingHints.VALUE_RENDER_QUALITY,
        RenderingHints.KEY_STROKE_CONTROL to RenderingHints.VALUE_STROKE_PURE,
    )
    desktopHints?.forEach { (key, value) ->
        if (key is RenderingHints.Key && value != null) {
            hints[key] = value
        }
    }
    return hints
}

internal data class GhostSnapshotGeometry(
    val bufferWidth: Int,
    val bufferHeight: Int,
    val drawWidth: Int,
    val drawHeight: Int,
    val scaleX: Double,
    val scaleY: Double,
)

internal fun ghostSnapshotGeometry(
    logicalWidth: Int,
    logicalHeight: Int,
    scaleX: Double,
    scaleY: Double,
): GhostSnapshotGeometry {
    val safeLogicalWidth = maxOf(logicalWidth, 1)
    val safeLogicalHeight = maxOf(logicalHeight, 1)
    val safeScaleX = scaleX.coerceAtLeast(1.0)
    val safeScaleY = scaleY.coerceAtLeast(1.0)
    return GhostSnapshotGeometry(
        bufferWidth = kotlin.math.ceil(safeLogicalWidth * safeScaleX).toInt(),
        bufferHeight = kotlin.math.ceil(safeLogicalHeight * safeScaleY).toInt(),
        drawWidth = safeLogicalWidth,
        drawHeight = safeLogicalHeight,
        scaleX = safeScaleX,
        scaleY = safeScaleY,
    )
}

private fun ghostSnapshotGeometry(
    logicalWidth: Int,
    logicalHeight: Int,
    graphicsConfiguration: GraphicsConfiguration?,
): GhostSnapshotGeometry {
    val transform = graphicsConfiguration?.defaultTransform
    return ghostSnapshotGeometry(
        logicalWidth = logicalWidth,
        logicalHeight = logicalHeight,
        scaleX = transform?.scaleX ?: 1.0,
        scaleY = transform?.scaleY ?: 1.0,
    )
}

/**
 * Installs drag-to-reorder behaviour on cards laid out vertically inside
 * [container] (a `BoxLayout.Y_AXIS` panel) hosted by [scrollPane].
 *
 * Protocol:
 *  - The caller associates each card with its drag handle via [attachHandle].
 *  - `mousePressed` on a handle snapshots the card into a [BufferedImage],
 *    attaches it as a ghost overlay on the `IdeGlassPane`, and starts a
 *    16 ms [Timer] that auto-scrolls when the pointer enters edge zones.
 *  - `mouseDragged` repositions the ghost and recomputes [dropTargetIndex]
 *    via [calculateTargetIndex]; the container repaints (indicator painting
 *    is the container's responsibility — it reads [dropTargetIndex]).
 *  - `mouseReleased` removes the ghost, stops the timer, and calls
 *    [onReorder] if the target index differs from the origin.
 *  - Pressing `Escape` cancels the drag and animates the ghost back to the
 *    origin over 150 ms before removing it.
 */
class DragReorderSupport(
    private val container: JPanel,
    private val scrollPane: JBScrollPane,
    private val onReorder: (fromIndex: Int, toIndex: Int) -> Unit,
    /**
     * Optional drag-lifecycle observer used by [LivePreviewReorderDecorator].
     * The skeleton works without it; additive so existing call sites keep
     * compiling.
     */
    private val onDragStart: ((draggedIndex: Int, cardHeight: Int, interSlotGap: Int) -> Unit)? = null,
    private val onDragUpdate: ((dropTargetIndex: Int) -> Unit)? = null,
    private val onDragEnd: (() -> Unit)? = null,
    private val onDragCancelStart: (() -> Unit)? = null,
    private val onDragCancelComplete: (() -> Unit)? = null,
) {
    /** Minimum squared distance the mouse must move before drag begins (slop). */
    private companion object {
        const val DRAG_START_SLOP_SQUARED: Int = 9 // 3px
    }

    /** Last computed target index; readable by the container's indicator painter. */
    @Volatile
    var dropTargetIndex: Int = -1
        private set

    private val handleBindings: MutableMap<JComponent, HandleBinding> = HashMap()
    private var active: ActiveDrag? = null

    internal fun attachHandle(
        card: JComponent,
        dragHandle: JComponent,
        index: () -> Int,
        slotProvider: () -> List<DragCardSlot>,
    ) {
        val binding = HandleBinding(card, dragHandle, index, slotProvider)
        handleBindings[dragHandle] = binding
        dragHandle.addMouseListener(binding.mouseListener)
        dragHandle.addMouseMotionListener(binding.motionListener)
    }

    fun detach() {
        handleBindings.values.forEach { it.dispose() }
        handleBindings.clear()
        active?.cancel(animate = false)
        active = null
    }

    private inner class HandleBinding(
        val card: JComponent,
        val handle: JComponent,
        val indexLookup: () -> Int,
        val slotProvider: () -> List<DragCardSlot>,
    ) {
        /** Mouse position captured on press; drag starts only once pointer moves. */
        private var pressStart: Point? = null

        val mouseListener = object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                if (!SwingUtilities.isLeftMouseButton(e)) return
                pressStart = e.point
            }

            override fun mouseReleased(e: MouseEvent) {
                if (!SwingUtilities.isLeftMouseButton(e)) return
                pressStart = null
                active?.finish()
                active = null
            }
        }

        val motionListener = object : MouseMotionAdapter() {
            override fun mouseDragged(e: MouseEvent) {
                val start = pressStart
                if (active == null && start != null) {
                    val dx = e.point.x - start.x
                    val dy = e.point.y - start.y
                    if (dx * dx + dy * dy < DRAG_START_SLOP_SQUARED) return
                    begin(MouseEvent(
                        e.component, e.id, e.`when`, e.modifiersEx,
                        start.x, start.y, e.clickCount, e.isPopupTrigger, e.button,
                    ))
                }
                active?.onMove(e)
            }
        }

        fun dispose() {
            handle.removeMouseListener(mouseListener)
            handle.removeMouseMotionListener(motionListener)
        }

        private fun begin(e: MouseEvent) {
            val originIndex = indexLookup()
            if (originIndex < 0) return
            val handleOriginOnCard = SwingUtilities.convertPoint(e.component, Point(0, 0), card)
            val startMouseOnCard = cardPressPointFromHandlePress(e.point, handleOriginOnCard)
            val snapshot = snapshot(card)
            val glass = IdeGlassPaneUtil.find(container) as? JComponent ?: return
            val ghost = GhostComponent(snapshot)
            ghost.size = card.size
            val originOnGlass = SwingUtilities.convertPoint(card, Point(0, 0), glass)
            ghost.location = originOnGlass
            glass.add(ghost)
            glass.revalidate()
            glass.repaint()

            val drag = ActiveDrag(
                originIndex = originIndex,
                ghost = ghost,
                glass = glass,
                card = card,
                handle = handle,
                startMouseOnCard = startMouseOnCard,
                originOnGlass = originOnGlass,
                slotProvider = slotProvider,
            )
            drag.install()
            active = drag
            dropTargetIndex = originIndex
            val slots = slotProvider()
            val draggedSlot = slots.firstOrNull { it.originalIndex == originIndex }
            val nextSlot = slots.firstOrNull { it.originalIndex == originIndex + 1 }
            val interSlotGap = if (draggedSlot != null && nextSlot != null) {
                interSlotGapPx(
                    cardTop = draggedSlot.top,
                    cardHeight = draggedSlot.height,
                    nextCardTop = nextSlot.top,
                )
            } else {
                0
            }
            onDragStart?.invoke(originIndex, card.height, interSlotGap)
        }
    }

    private inner class ActiveDrag(
        val originIndex: Int,
        val ghost: GhostComponent,
        val glass: JComponent,
        val card: JComponent,
        val handle: JComponent,
        val startMouseOnCard: Point,
        val originOnGlass: Point,
        val slotProvider: () -> List<DragCardSlot>,
    ) {
        private var cancelled: Boolean = false
        private val scrollTimer: Timer = Timer(16) { tickAutoScroll() }
        private val keyListener = object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if (e.keyCode == KeyEvent.VK_ESCAPE) cancel(animate = true)
            }
        }
        private var lastMouseOnGlass: Point = originOnGlass

        fun install() {
            handle.isFocusable = true
            handle.addKeyListener(keyListener)
            handle.requestFocusInWindow()
            scrollTimer.isRepeats = true
            scrollTimer.start()
        }

        fun onMove(e: MouseEvent) {
            if (cancelled) return
            val onGlass = SwingUtilities.convertPoint(e.component, e.point, glass)
            lastMouseOnGlass = onGlass
            val target = Point(
                originOnGlass.x,
                onGlass.y - startMouseOnCard.y,
            )
            ghost.location = target
            glass.repaint()
            recomputeTarget()
            onDragUpdate?.invoke(dropTargetIndex)
            container.repaint()
        }

        private fun recomputeTarget() {
            val ghostCenterInContainer = SwingUtilities.convertPoint(
                glass,
                Point(ghost.x + ghost.width / 2, ghost.y + ghost.height / 2),
                container,
            )
            val siblings = siblingBoundsFromSlots(
                slots = slotProvider(),
                draggedIndex = originIndex,
            )
            dropTargetIndex = calculateTargetIndex(
                draggedCenterY = ghostCenterInContainer.y.toFloat(),
                siblings = siblings,
                originalIndex = originIndex,
            )
        }

        private fun tickAutoScroll() {
            val viewport = scrollPane.viewport ?: return
            val viewRect = viewport.viewRect
            val ghostInViewport = SwingUtilities.convertPoint(
                glass,
                ghost.location,
                viewport.view,
            )
            val delta = DragAutoScroll.computeScrollDelta(
                itemTop = ghostInViewport.y.toFloat(),
                itemBottom = (ghostInViewport.y + ghost.height).toFloat(),
                viewportTop = viewRect.y.toFloat(),
                viewportBottom = (viewRect.y + viewRect.height).toFloat(),
                edgeZonePx = DragAutoScroll.DEFAULT_EDGE_ZONE_DP,
                maxSpeedPxPerFrame = DragAutoScroll.DEFAULT_MAX_SPEED_DP_PER_FRAME,
            )
            if (delta != 0f) {
                val bar = scrollPane.verticalScrollBar ?: return
                bar.valueIsAdjusting = true
                bar.value = (bar.value + delta.toInt()).coerceIn(bar.minimum, bar.maximum - bar.visibleAmount)
                bar.valueIsAdjusting = false
            }
        }

        fun finish() {
            if (cancelled) return
            val commitIndex = dropTargetIndex
            dispose()
            onDragEnd?.invoke()
            if (commitIndex >= 0 && commitIndex != originIndex) {
                onReorder(originIndex, commitIndex)
            }
        }

        fun cancel(animate: Boolean) {
            if (cancelled) return
            cancelled = true
            onDragCancelStart?.invoke()
            if (!animate) {
                dispose()
                onDragCancelComplete?.invoke()
                return
            }
            val from = ghost.location
            val to = originOnGlass
            val startTime = System.currentTimeMillis()
            val duration = 150L
            lateinit var anim: Timer
            anim = Timer(16) {
                val t = ((System.currentTimeMillis() - startTime).toFloat() / duration).coerceIn(0f, 1f)
                ghost.location = Point(
                    (from.x + (to.x - from.x) * t).toInt(),
                    (from.y + (to.y - from.y) * t).toInt(),
                )
                glass.repaint()
                if (t >= 1f) {
                    anim.stop()
                    dispose()
                    onDragCancelComplete?.invoke()
                }
            }
            anim.isRepeats = true
            anim.start()
        }

        private fun dispose() {
            scrollTimer.stop()
            handle.removeKeyListener(keyListener)
            val parent = ghost.parent
            if (parent != null) {
                parent.remove(ghost)
                parent.revalidate()
                parent.repaint()
            }
            dropTargetIndex = -1
            container.repaint()
        }
    }

    private class GhostComponent(private val snapshot: GhostSnapshot) : JComponent() {
        init {
            isOpaque = false
        }

        override fun paintComponent(g: Graphics) {
            val g2 = g.create() as Graphics2D
            try {
                g2.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.85f)
                g2.drawImage(
                    snapshot.image,
                    0,
                    0,
                    snapshot.geometry.drawWidth,
                    snapshot.geometry.drawHeight,
                    null,
                )
            } finally {
                g2.dispose()
            }
        }
    }

    private data class GhostSnapshot(
        val image: BufferedImage,
        val geometry: GhostSnapshotGeometry,
    )

    private fun snapshot(card: JComponent): GhostSnapshot {
        val geometry = ghostSnapshotGeometry(
            logicalWidth = card.width,
            logicalHeight = card.height,
            graphicsConfiguration = card.graphicsConfiguration,
        )
        val image = BufferedImage(
            geometry.bufferWidth,
            geometry.bufferHeight,
            BufferedImage.TYPE_INT_ARGB,
        )
        val g = image.createGraphics()
        try {
            val desktopHints = Toolkit.getDefaultToolkit().getDesktopProperty("awt.font.desktophints") as? Map<*, *>
            ghostSnapshotRenderingHints(desktopHints).forEach { (key, value) ->
                g.setRenderingHint(key, value)
            }
            g.scale(geometry.scaleX, geometry.scaleY)
            card.paint(g)
        } finally {
            g.dispose()
        }
        return GhostSnapshot(image = image, geometry = geometry)
    }
}
