package io.github.barsia.speqa.editor

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.VisibleAreaEvent
import com.intellij.openapi.editor.event.VisibleAreaListener
import com.intellij.openapi.project.Project
import io.github.barsia.speqa.settings.SpeqaSettings
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

private const val SUPPRESS_MS = 220L

/**
 * Proportional scroll synchronization between the IntelliJ text editor (left)
 * and Compose visual editor (right). Both panels sync by scroll fraction (0.0–1.0).
 */
class ScrollSyncController(
    private val project: Project,
    private val textEditor: Editor,
) : Disposable {

    private val isEnabled: Boolean
        get() = SpeqaSettings.getInstance(project).scrollSyncEnabled

    private val _editorScrollFraction = MutableSharedFlow<Float>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** Compose side collects from this flow to receive editor scroll fraction updates. */
    val editorScrollFraction = _editorScrollFraction.asSharedFlow()

    private var suppressEditorUntil = 0L
    private var suppressComposeUntil = 0L

    private val visibleAreaListener = VisibleAreaListener { _: VisibleAreaEvent ->
        if (!isEnabled) return@VisibleAreaListener
        if (System.currentTimeMillis() < suppressEditorUntil) return@VisibleAreaListener
        val fraction = computeEditorFraction()
        suppressComposeUntil = System.currentTimeMillis() + SUPPRESS_MS
        _editorScrollFraction.tryEmit(fraction)
    }

    init {
        textEditor.scrollingModel.addVisibleAreaListener(visibleAreaListener)
    }

    /**
     * Called from the Compose side when it initiates a document change (e.g. the user
     * reorders steps). The editor may react with a visible-area change we do NOT want
     * to mirror back to Compose, so suppress editor→compose sync for a short window.
     */
    fun suppressEditorToComposeSync() {
        // `suppressEditorUntil` gates the VisibleAreaListener — while it is in the future,
        // the listener does not emit new fractions to the Compose side. That is exactly
        // what we want here: Compose just caused the document to change, and any resulting
        // editor layout/caret adjustments should not scroll the Compose preview.
        suppressEditorUntil = System.currentTimeMillis() + SUPPRESS_MS
    }

    /**
     * Called from the Compose side when the user scrolls the visual editor.
     * Scrolls the text editor to the corresponding fraction.
     */
    fun onComposeScroll(fraction: Float) {
        if (!isEnabled) return
        if (System.currentTimeMillis() < suppressComposeUntil) return
        suppressEditorUntil = System.currentTimeMillis() + SUPPRESS_MS
        ApplicationManager.getApplication().invokeLater {
            if (textEditor.isDisposed) return@invokeLater
            val contentHeight = textEditor.contentComponent.height
            val viewportHeight = textEditor.scrollingModel.visibleArea.height
            val maxScroll = (contentHeight - viewportHeight).coerceAtLeast(0)
            val targetY = (fraction * maxScroll).toInt()
            textEditor.scrollingModel.scrollVertically(targetY)
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
    }
}
