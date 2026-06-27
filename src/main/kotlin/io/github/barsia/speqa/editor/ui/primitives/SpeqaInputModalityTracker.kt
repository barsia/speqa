package io.github.barsia.speqa.editor.ui.primitives

import com.intellij.ide.IdeEventQueue
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent

/**
 * Application service that tracks whether the most recent user interaction was
 * the keyboard or the mouse. The result is stored in [SpeqaInputModality] and
 * consumed by [isKeyboardFocusCause] to implement the `:focus-visible` model:
 * programmatic focus restored after a keyboard action (Esc, Delete, reorder)
 * shows the ring; focus restored after a mouse action does not.
 *
 * The dispatcher is registered with [this] as the parent [Disposable] so it is
 * removed automatically when the plugin is unloaded (dynamic plugin safety).
 */
@Service(Service.Level.APP)
class SpeqaInputModalityTracker : Disposable {
    init {
        IdeEventQueue.getInstance().addDispatcher(
            IdeEventQueue.EventDispatcher { e ->
                when (e) {
                    is KeyEvent -> SpeqaInputModality.lastInteractionWasKeyboard = true
                    is MouseEvent -> if (e.id == MouseEvent.MOUSE_PRESSED) {
                        SpeqaInputModality.lastInteractionWasKeyboard = false
                    }
                }
                false // never consume the event
            },
            this,
        )
    }

    override fun dispose() {}
}
