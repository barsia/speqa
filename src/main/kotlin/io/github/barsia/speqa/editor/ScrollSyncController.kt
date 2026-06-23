package io.github.barsia.speqa.editor

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.VisibleAreaEvent
import com.intellij.openapi.editor.event.VisibleAreaListener
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBScrollPane
import io.github.barsia.speqa.editor.ui.steps.StepsSection
import io.github.barsia.speqa.settings.SpeqaSettings
import java.awt.event.AdjustmentEvent
import java.awt.event.AdjustmentListener
import javax.swing.JScrollBar
import javax.swing.Timer
import javax.swing.event.ChangeListener

private const val SUPPRESS_MS = 220L
private const val PANEL_RESTORE_SETTLE_MS = 220
private const val EDITOR_MUTATION_SCROLL_SETTLE_MS = 160
private const val EDITOR_MUTATION_SCROLL_MAX_MS = 2_000
private const val BOTTOM_ANCHOR_TOLERANCE_PX = 2

/**
 * Proportional scroll synchronization between the IntelliJ text editor (left)
 * and the Swing visual editor (right). Both surfaces sync by scroll fraction
 * (0.0–1.0).
 *
 * The right-hand panel is a [JBScrollPane]; callers attach it via
 * [attachScrollPane]. The controller drives its `verticalScrollBar` whenever
 * the left editor scrolls, and mirrors the opposite direction via an
 * [AdjustmentListener] it installs on the bar.
 *
 * 220 ms suppression windows short-circuit both directions around programmatic
 * changes, so in particular:
 *  - [suppressBothDirections] must be called before editor-driven preview
 *    refreshes and preview-initiated document patches. Both paths can trigger
 *    programmatic Swing scrollbar/model changes and IntelliJ caret-follow
 *    scrolls while preserving offsets.
 *  - [preservedVerticalPosition] / [restoreVerticalPosition] capture and re-apply the
 *    right-panel scroll position around an external re-layout; bottom-aligned
 *    viewports stay bottom-aligned when content is appended below them.
 */
class ScrollSyncController(
    private val project: Project,
    private val textEditor: Editor,
) : Disposable {

    internal class SuppressionState {
        private var suppressEditorUntil = 0L
        private var suppressPanelUntil = 0L

        fun suppressEditorToPanel(nowMillis: Long) {
            suppressEditorUntil = nowMillis + SUPPRESS_MS
        }

        fun suppressPanelToEditor(nowMillis: Long) {
            suppressPanelUntil = nowMillis + SUPPRESS_MS
        }

        fun suppressBothDirections(nowMillis: Long) {
            suppressEditorToPanel(nowMillis)
            suppressPanelToEditor(nowMillis)
        }

        fun isEditorToPanelSuppressed(nowMillis: Long): Boolean = nowMillis < suppressEditorUntil

        fun isPanelToEditorSuppressed(nowMillis: Long): Boolean = nowMillis < suppressPanelUntil
    }

    internal class EditorMutationScrollGuard {
        private var active = false

        fun onDocumentMutation() {
            active = true
        }

        fun shouldSuppressEditorVisibleAreaChange(oldY: Int?, newY: Int): Boolean = active && oldY != newY

        fun clear() {
            active = false
        }
    }

    internal data class PanelScrollPosition(
        val value: Int,
        val bottomGap: Int,
        val preserveBottomGap: Boolean,
    )

    private val isEnabled: Boolean
        get() = SpeqaSettings.getInstance(project).scrollSyncEnabled

    private val suppressionState = SuppressionState()
    private val editorMutationScrollGuard = EditorMutationScrollGuard()
    private var programmaticPanelSuppressionActive = false

    private var scrollPane: JBScrollPane? = null
    private var adjustmentListener: AdjustmentListener? = null
    private var stepsSection: StepsSection? = null
    private var pendingPanelPositionRestore: PendingPanelPositionRestore? = null
    private val clearEditorMutationScrollAfterSettleTimer = Timer(EDITOR_MUTATION_SCROLL_SETTLE_MS) {
        editorMutationScrollGuard.clear()
    }.apply {
        isRepeats = false
    }
    private val clearEditorMutationScrollMaxTimer = Timer(EDITOR_MUTATION_SCROLL_MAX_MS) {
        editorMutationScrollGuard.clear()
    }.apply {
        isRepeats = false
    }

    private val visibleAreaListener = VisibleAreaListener { event: VisibleAreaEvent ->
        if (!isEnabled) return@VisibleAreaListener
        val now = System.currentTimeMillis()
        if (suppressionState.isEditorToPanelSuppressed(now)) return@VisibleAreaListener
        if (editorMutationScrollGuard.shouldSuppressEditorVisibleAreaChange(
                event.oldRectangle?.y,
                event.newRectangle.y,
            )
        ) {
            clearEditorMutationScrollAfterSettleTimer.restart()
            return@VisibleAreaListener
        }
        if (!shouldMirrorEditorVisibleAreaChange(event.oldRectangle?.y, event.newRectangle.y)) {
            return@VisibleAreaListener
        }
        val pane = scrollPane ?: return@VisibleAreaListener
        cancelPendingPanelPositionRestore()
        suppressionState.suppressPanelToEditor(System.currentTimeMillis())
        ApplicationManager.getApplication().invokeLater {
            syncEditorToPanel(pane)
        }
    }

    init {
        textEditor.scrollingModel.addVisibleAreaListener(visibleAreaListener)
    }

    /** Wire the steps section for anchor-based scroll sync. */
    fun attachStepsSection(section: StepsSection) {
        stepsSection = section
    }

    /** Wire the right-hand Swing scroll pane; safe to call once per lifetime. */
    fun attachScrollPane(pane: JBScrollPane) {
        detachScrollPaneInternal()
        scrollPane = pane
        val listener = AdjustmentListener { _: AdjustmentEvent ->
            if (!isEnabled) return@AdjustmentListener
            val now = System.currentTimeMillis()
            if (suppressionState.isPanelToEditorSuppressed(now)) return@AdjustmentListener
            if (programmaticPanelSuppressionActive) return@AdjustmentListener
            if (pendingPanelPositionRestore != null) return@AdjustmentListener
            cancelPendingPanelPositionRestore()
            onPanelScroll(pane)
        }
        pane.verticalScrollBar.addAdjustmentListener(listener)
        adjustmentListener = listener
    }

    private fun detachScrollPaneInternal() {
        val pane = scrollPane
        val listener = adjustmentListener
        if (pane != null && listener != null) {
            pane.verticalScrollBar.removeAdjustmentListener(listener)
        }
        scrollPane = null
        adjustmentListener = null
    }

    /**
     * Called before any operation that can programmatically re-layout or scroll
     * both surfaces. This covers editor-driven preview refreshes and
     * preview-driven document writes whose local Swing rebuild may fire
     * scrollbar adjustment events before the deferred document patch runs.
     */
    fun suppressBothDirections() {
        programmaticPanelSuppressionActive = true
        suppressionState.suppressBothDirections(System.currentTimeMillis())
    }

    fun suppressEditorToPanelForDocumentMutation() {
        cancelPendingPanelPositionRestore()
        editorMutationScrollGuard.onDocumentMutation()
        clearEditorMutationScrollAfterSettleTimer.stop()
        clearEditorMutationScrollMaxTimer.restart()
    }

    /** Current vertical scroll position of the right panel. */
    internal fun preservedVerticalPosition(): PanelScrollPosition {
        val bar = scrollPane?.verticalScrollBar
            ?: return PanelScrollPosition(value = 0, bottomGap = 0, preserveBottomGap = true)
        val max = maxRepresentableVerticalOffset(bar.maximum, bar.visibleAmount)
        val value = bar.value.coerceIn(0, max)
        val bottomGap = (max - value).coerceAtLeast(0)
        val position = PanelScrollPosition(
            value = value,
            bottomGap = bottomGap,
            preserveBottomGap = shouldPreserveBottomGap(bottomGap),
        )
        return position
    }

    /** Restore the right panel's vertical scroll position captured by [preservedVerticalPosition]. */
    internal fun restoreVerticalPosition(position: PanelScrollPosition) {
        val pane = scrollPane ?: return
        val bar = pane.verticalScrollBar
        cancelPendingPanelPositionRestore()
        suppressionState.suppressPanelToEditor(System.currentTimeMillis())
        restoreVerticalPositionIfAvailable(bar, position)

        lateinit var listener: ChangeListener
        listener = ChangeListener {
            restoreVerticalPositionIfAvailable(bar, position)
        }
        val timer = Timer(PANEL_RESTORE_SETTLE_MS) {
            programmaticPanelSuppressionActive = false
            cancelPendingPanelPositionRestore()
        }.apply {
            isRepeats = false
        }
        pendingPanelPositionRestore = PendingPanelPositionRestore(bar, listener, timer)
        bar.model.addChangeListener(listener)
        timer.start()
    }

    private fun syncEditorToPanel(pane: JBScrollPane) {
        val section = stepsSection
        if (section == null || !hasAnchors(section)) {
            val fraction = computeEditorFraction()
            applyPanelFraction(pane, fraction)
            return
        }
        // When the editor is at its maximum scroll position it cannot move further
        // even though logically more content exists below the viewport. Map this
        // directly to the panel maximum so the preview always reaches its end.
        if (isEditorAtMaxScroll()) {
            applyPanelY(pane, (pane.verticalScrollBar.maximum - pane.verticalScrollBar.visibleAmount).coerceAtLeast(0))
            return
        }
        val currentLine = editorTopLine()
        val firstStepLine = section.stepSourceLine(0)
        if (currentLine < firstStepLine) {
            val firstCardY = section.cardAbsoluteY(0) ?: run {
                applyPanelFraction(pane, computeEditorFraction())
                return
            }
            val fraction = (currentLine.toFloat() / firstStepLine.coerceAtLeast(1)).coerceIn(0f, 1f)
            applyPanelY(pane, (fraction * firstCardY).toInt())
        } else {
            val idx = anchorStepForLine(section, currentLine)
            val stepLine = section.stepSourceLine(idx)
            val totalLines = textEditor.document.lineCount.coerceAtLeast(stepLine + 1)
            val nextLine = if (idx + 1 < section.stepCount) section.stepSourceLine(idx + 1) else totalLines
            val intra = ((currentLine - stepLine).toFloat() / (nextLine - stepLine).coerceAtLeast(1)).coerceIn(0f, 1f)
            val cardY = section.cardAbsoluteY(idx) ?: run { applyPanelFraction(pane, computeEditorFraction()); return }
            val nextCardY = section.cardAbsoluteY(idx + 1)
            val targetY = if (nextCardY != null) {
                cardY + (intra * (nextCardY - cardY)).toInt()
            } else {
                val max = (pane.verticalScrollBar.maximum - pane.verticalScrollBar.visibleAmount).coerceAtLeast(0)
                cardY + (intra * (max - cardY).coerceAtLeast(0)).toInt()
            }
            applyPanelY(pane, targetY)
        }
    }

    private fun onPanelScroll(pane: JBScrollPane) {
        if (!isEnabled) return
        suppressionState.suppressEditorToPanel(System.currentTimeMillis())
        val section = stepsSection
        val panelY = pane.verticalScrollBar.value
        ApplicationManager.getApplication().invokeLater {
            if (textEditor.isDisposed) return@invokeLater
            if (section == null || !hasAnchors(section)) {
                val bar = pane.verticalScrollBar
                val max = (bar.maximum - bar.visibleAmount).coerceAtLeast(1)
                val fraction = (panelY.toFloat() / max).coerceIn(0f, 1f)
                scrollEditorToFraction(fraction)
                return@invokeLater
            }
            val firstCardY = section.cardAbsoluteY(0)
            if (firstCardY == null || panelY < firstCardY) {
                val headerEnd = firstCardY ?: 1
                val fraction = (panelY.toFloat() / headerEnd.coerceAtLeast(1)).coerceIn(0f, 1f)
                val targetLine = (fraction * section.stepSourceLine(0)).toInt()
                scrollEditorToLine(targetLine)
            } else {
                val idx = anchorStepForY(section, panelY)
                val cardY = section.cardAbsoluteY(idx) ?: run { scrollEditorToFraction(computeEditorFraction()); return@invokeLater }
                val nextCardY = section.cardAbsoluteY(idx + 1)
                val intra = if (nextCardY != null && nextCardY > cardY) {
                    ((panelY - cardY).toFloat() / (nextCardY - cardY)).coerceIn(0f, 1f)
                } else {
                    val bar = pane.verticalScrollBar
                    val max = (bar.maximum - bar.visibleAmount).coerceAtLeast(1)
                    ((panelY - cardY).toFloat() / (max - cardY).coerceAtLeast(1)).coerceIn(0f, 1f)
                }
                val stepLine = section.stepSourceLine(idx)
                val totalLines = textEditor.document.lineCount.coerceAtLeast(stepLine + 1)
                val nextLine = if (idx + 1 < section.stepCount) section.stepSourceLine(idx + 1) else totalLines
                scrollEditorToLine(stepLine + (intra * (nextLine - stepLine)).toInt())
            }
        }
    }

    private fun computeEditorFraction(): Float {
        val visibleArea = textEditor.scrollingModel.visibleArea
        val contentHeight = textEditor.contentComponent.height
        val maxScroll = (contentHeight - visibleArea.height).coerceAtLeast(1)
        return (visibleArea.y.toFloat() / maxScroll).coerceIn(0f, 1f)
    }

    private fun editorTopLine(): Int {
        val y = textEditor.scrollingModel.visibleArea.y
        return textEditor.xyToLogicalPosition(java.awt.Point(0, y)).line
    }

    private fun isEditorAtMaxScroll(): Boolean {
        val visibleArea = textEditor.scrollingModel.visibleArea
        val contentHeight = textEditor.contentComponent.height
        return visibleArea.y + visibleArea.height >= contentHeight - textEditor.lineHeight
    }

    private fun hasAnchors(section: StepsSection): Boolean {
        if (section.stepCount == 0 || section.stepSourceLine(0) <= 0) return false
        // If the first card Y is 0 the component tree hasn't been laid out yet.
        // Fall back to proportional sync rather than snapping the panel to the top.
        val firstCardY = section.cardAbsoluteY(0) ?: return false
        return firstCardY > 0
    }

    private fun anchorStepForLine(section: StepsSection, line: Int): Int {
        var result = 0
        for (i in 1 until section.stepCount) {
            if (section.stepSourceLine(i) <= line) result = i else break
        }
        return result
    }

    private fun anchorStepForY(section: StepsSection, panelY: Int): Int {
        var result = 0
        for (i in 1 until section.stepCount) {
            val y = section.cardAbsoluteY(i) ?: break
            if (y <= panelY) result = i else break
        }
        return result
    }

    private fun applyPanelY(pane: JBScrollPane, targetY: Int) {
        val bar = pane.verticalScrollBar
        val max = (bar.maximum - bar.visibleAmount).coerceAtLeast(0)
        bar.value = targetY.coerceIn(0, max)
    }

    private fun applyPanelFraction(pane: JBScrollPane, fraction: Float) {
        val bar = pane.verticalScrollBar
        val max = (bar.maximum - bar.visibleAmount).coerceAtLeast(0)
        bar.value = (fraction * max).toInt()
    }

    private fun scrollEditorToFraction(fraction: Float) {
        val contentHeight = textEditor.contentComponent.height
        val viewportHeight = textEditor.scrollingModel.visibleArea.height
        val maxScroll = (contentHeight - viewportHeight).coerceAtLeast(0)
        scrollEditorToPixel((fraction * maxScroll).toInt())
    }

    private fun scrollEditorToLine(line: Int) {
        val y = textEditor.logicalPositionToXY(
            com.intellij.openapi.editor.LogicalPosition(line.coerceAtLeast(0), 0)
        ).y
        scrollEditorToPixel(y)
    }

    private fun scrollEditorToPixel(targetY: Int) {
        // Skip smooth-scroll animation: each panel scrollbar tick fires a
        // fresh AdjustmentEvent, so animated transitions stack up and produce
        // choppy editor scroll. Direct positioning matches editor-to-panel path.
        textEditor.scrollingModel.disableAnimation()
        try {
            textEditor.scrollingModel.scrollVertically(targetY)
        } finally {
            textEditor.scrollingModel.enableAnimation()
        }
    }

    override fun dispose() {
        clearEditorMutationScrollAfterSettleTimer.stop()
        clearEditorMutationScrollMaxTimer.stop()
        cancelPendingPanelPositionRestore()
        textEditor.scrollingModel.removeVisibleAreaListener(visibleAreaListener)
        detachScrollPaneInternal()
    }

    private fun restoreVerticalPositionIfAvailable(bar: JScrollBar, position: PanelScrollPosition): Boolean {
        val target = restoredVerticalOffset(position, bar.maximum, bar.visibleAmount)
        if (!canRepresentVerticalOffset(target, bar.maximum, bar.visibleAmount)) {
            return false
        }
        suppressionState.suppressPanelToEditor(System.currentTimeMillis())
        bar.value = target
        return true
    }

    private fun cancelPendingPanelPositionRestore() {
        pendingPanelPositionRestore?.let {
            it.timer.stop()
            it.bar.model.removeChangeListener(it.listener)
        }
        pendingPanelPositionRestore = null
    }

    private data class PendingPanelPositionRestore(
        val bar: JScrollBar,
        val listener: ChangeListener,
        val timer: Timer,
    )

    companion object {
        internal fun shouldMirrorEditorVisibleAreaChange(oldY: Int?, newY: Int): Boolean =
            oldY == null || oldY != newY

        internal fun canRepresentVerticalOffset(value: Int, maximum: Int, visibleAmount: Int): Boolean =
            value <= maxRepresentableVerticalOffset(maximum, visibleAmount)

        internal fun maxRepresentableVerticalOffset(maximum: Int, visibleAmount: Int): Int =
            (maximum - visibleAmount).coerceAtLeast(0)

        internal fun shouldPreserveBottomGap(bottomGap: Int): Boolean = bottomGap <= BOTTOM_ANCHOR_TOLERANCE_PX

        internal fun restoredVerticalOffset(
            position: PanelScrollPosition,
            maximum: Int,
            visibleAmount: Int,
        ): Int {
            val max = maxRepresentableVerticalOffset(maximum, visibleAmount)
            val target = if (position.preserveBottomGap) {
                max - position.bottomGap
            } else {
                position.value
            }
            return target.coerceAtLeast(0)
        }
    }
}
