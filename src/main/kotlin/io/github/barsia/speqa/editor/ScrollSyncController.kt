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

private const val SUPPRESS_MS = 220L

/**
 * Proportional scroll synchronization between the IntelliJ text editor (left)
 * and the visual preview (right). Both surfaces sync by scroll fraction
 * (0.0–1.0).
 *
 * Swing previews attach a [JBScrollPane] via [attachScrollPane]. Native WebView
 * previews attach a fractional sink via [attachFractionalPanel] and report user
 * scrolls back through [onPanelScrolled].
 *
 * 220 ms suppression windows short-circuit both directions around programmatic
 * changes, so in particular:
 *  - [suppressEditorToPanelSync] must be called before preview-initiated document
 *    patches (e.g. `PatchOperation.ReorderSteps`) so IntelliJ's own caret-follow
 *    scroll does not mirror back onto the right panel.
 *  - [preservedVerticalOffset] / [restoreVerticalOffset] capture and re-apply the
 *    right-panel scroll offset around an external re-layout; reads/writes
 *    `scrollBar.value` directly.
 */
class ScrollSyncController(
    private val project: Project,
    private val textEditor: Editor,
) : Disposable {

    private val isEnabled: Boolean
        get() = SpeqaSettings.getInstance(project).scrollSyncEnabled

    private var suppressEditorUntil = 0L
    private var suppressPanelUntil = 0L

    private var scrollPane: JBScrollPane? = null
    private var adjustmentListener: AdjustmentListener? = null
    private var fractionalPanelSink: ((Float) -> Unit)? = null

    private val visibleAreaListener = VisibleAreaListener { _: VisibleAreaEvent ->
        if (!isEnabled) return@VisibleAreaListener
        if (System.currentTimeMillis() < suppressEditorUntil) return@VisibleAreaListener
        if (scrollPane == null && fractionalPanelSink == null) return@VisibleAreaListener
        syncPanelToEditorFraction(computeEditorFraction())
    }

    private fun syncPanelToEditorFraction(fraction: Float) {
        suppressPanelUntil = System.currentTimeMillis() + SUPPRESS_MS
        ApplicationManager.getApplication().invokeLater {
            val sink = fractionalPanelSink
            if (sink != null) {
                sink(fraction)
            } else {
                val pane = scrollPane ?: return@invokeLater
                val bar = pane.verticalScrollBar
                val max = (bar.maximum - bar.visibleAmount).coerceAtLeast(0)
                bar.value = (fraction * max).toInt()
            }
        }
    }

    init {
        textEditor.scrollingModel.addVisibleAreaListener(visibleAreaListener)
    }

    /** Wire the right-hand Swing scroll pane; safe to call once per lifetime. */
    fun attachScrollPane(pane: JBScrollPane) {
        detachPanelInternal()
        scrollPane = pane
        val listener = AdjustmentListener { _: AdjustmentEvent ->
            if (!isEnabled) return@AdjustmentListener
            if (System.currentTimeMillis() < suppressPanelUntil) return@AdjustmentListener
            val bar = pane.verticalScrollBar
            val max = (bar.maximum - bar.visibleAmount).coerceAtLeast(1)
            val fraction = (bar.value.toFloat() / max).coerceIn(0f, 1f)
            onPanelScrolled(fraction)
        }
        pane.verticalScrollBar.addAdjustmentListener(listener)
        adjustmentListener = listener
    }

    /** Wire a non-Swing preview that accepts a normalized vertical scroll fraction. */
    fun attachFractionalPanel(scrollToFraction: (Float) -> Unit) {
        detachPanelInternal()
        fractionalPanelSink = scrollToFraction
    }

    /** Push the current editor position to the attached preview without waiting for a new scroll event. */
    fun syncPanelToEditor() {
        if (!isEnabled) return
        if (scrollPane == null && fractionalPanelSink == null) return
        syncPanelToEditorFraction(computeEditorFraction())
    }

    /** Report a user-initiated right-panel scroll from non-Swing previews. */
    fun onPanelScrolled(fraction: Float) {
        if (!isEnabled) return
        if (System.currentTimeMillis() < suppressPanelUntil) return
        scrollEditorToPanelFraction(fraction.coerceIn(0f, 1f))
    }

    private fun detachPanelInternal() {
        detachScrollPaneInternal()
        fractionalPanelSink = null
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
     * Called before a preview-initiated document change so any resulting
     * editor layout/caret adjustments are not mirrored onto the right panel.
     */
    fun suppressEditorToPanelSync() {
        suppressEditorUntil = System.currentTimeMillis() + SUPPRESS_MS
    }

    /** Current vertical scroll offset of the right panel, or `0` if unattached. */
    fun preservedVerticalOffset(): Int = scrollPane?.verticalScrollBar?.value ?: 0

    /** Restore the right panel's vertical scroll offset captured by [preservedVerticalOffset]. */
    fun restoreVerticalOffset(value: Int) {
        val pane = scrollPane ?: return
        suppressPanelUntil = System.currentTimeMillis() + SUPPRESS_MS
        pane.verticalScrollBar.value = value
    }

    private fun scrollEditorToPanelFraction(fraction: Float) {
        suppressEditorUntil = System.currentTimeMillis() + SUPPRESS_MS
        ApplicationManager.getApplication().invokeLater {
            if (textEditor.isDisposed) return@invokeLater
            val contentHeight = textEditor.contentComponent.height
            val viewportHeight = textEditor.scrollingModel.visibleArea.height
            val maxScroll = (contentHeight - viewportHeight).coerceAtLeast(0)
            val targetY = (fraction * maxScroll).toInt()
            // disableAnimation: IntelliJ's smooth-scroll animation can outlast our
            // SUPPRESS_MS window; intermediate VisibleAreaListener fires after the
            // window expires then get echoed back to the preview, snapping it
            // backward to the editor's mid-animation position. Instant scroll
            // keeps the entire round-trip inside one suppress window.
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
        textEditor.scrollingModel.removeVisibleAreaListener(visibleAreaListener)
        detachPanelInternal()
    }
}
