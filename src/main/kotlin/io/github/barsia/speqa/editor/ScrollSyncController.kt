package io.github.barsia.speqa.editor

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.VisibleAreaEvent
import com.intellij.openapi.editor.event.VisibleAreaListener
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBScrollPane
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
        val fraction = computeEditorFraction()
        suppressionState.suppressPanelToEditor(System.currentTimeMillis())
        ApplicationManager.getApplication().invokeLater {
            val bar = pane.verticalScrollBar
            val max = (bar.maximum - bar.visibleAmount).coerceAtLeast(0)
            val target = (fraction * max).toInt()
            bar.value = target
        }
    }

    init {
        textEditor.scrollingModel.addVisibleAreaListener(visibleAreaListener)
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
            val bar = pane.verticalScrollBar
            val max = (bar.maximum - bar.visibleAmount).coerceAtLeast(1)
            val fraction = (bar.value.toFloat() / max).coerceIn(0f, 1f)
            cancelPendingPanelPositionRestore()
            onPanelScroll(fraction)
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

    private fun onPanelScroll(fraction: Float) {
        if (!isEnabled) return
        suppressionState.suppressEditorToPanel(System.currentTimeMillis())
        ApplicationManager.getApplication().invokeLater {
            if (textEditor.isDisposed) return@invokeLater
            val contentHeight = textEditor.contentComponent.height
            val viewportHeight = textEditor.scrollingModel.visibleArea.height
            val maxScroll = (contentHeight - viewportHeight).coerceAtLeast(0)
            val targetY = (fraction * maxScroll).toInt()
            // Skip the editor's smooth-scroll animation. Each panel
            // scrollbar tick fires a fresh AdjustmentListener event,
            // so animated transitions stack up and produce a visibly
            // choppy editor scroll. Direct positioning matches the
            // editor → panel path which writes the scrollbar value
            // synchronously without animation.
            textEditor.scrollingModel.disableAnimation()
            try {
                textEditor.scrollingModel.scrollVertically(targetY)
            } finally {
                textEditor.scrollingModel.enableAnimation()
            }
        }
    }

    private fun computeEditorFraction(): Float {
        val visibleArea = textEditor.scrollingModel.visibleArea
        val contentHeight = textEditor.contentComponent.height
        val maxScroll = (contentHeight - visibleArea.height).coerceAtLeast(1)
        return (visibleArea.y.toFloat() / maxScroll).coerceIn(0f, 1f)
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
