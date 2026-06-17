package io.github.barsia.speqa.editor

import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.AppExecutorUtil
import io.github.barsia.speqa.registry.IdType
import io.github.barsia.speqa.registry.SpeqaIds
import java.util.concurrent.Callable
import java.util.concurrent.CopyOnWriteArrayList
import javax.swing.Timer

/**
 * Swing/Kotlin state holder backing the preview's inline-editable ID row. Exposes
 * `nextFreeId`, `isDuplicate`, and `isEditing`. The index-backed values are computed
 * on a background thread (FileBasedIndex queries are slow operations forbidden on the
 * EDT) and applied back on the EDT; listeners fire when a value actually changes.
 */
class IdStateHolder(
    private val project: Project,
    private val idType: IdType,
    private val currentId: () -> Int?,
) {
    fun interface Listener {
        fun onChanged(state: IdStateHolder)
    }

    @Volatile
    var nextFreeId: Int = 1
        private set

    @Volatile
    var isDuplicate: Boolean = false
        private set

    @Volatile
    var isEditing: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                fire()
            }
        }

    private val listeners = CopyOnWriteArrayList<Listener>()

    private val refreshTimer = Timer(2000) { refresh() }.apply {
        isRepeats = true
    }

    fun addListener(listener: Listener) {
        listeners.add(listener)
    }

    fun removeListener(listener: Listener) {
        listeners.remove(listener)
    }

    fun refresh() {
        ReadAction.nonBlocking(
            Callable {
                val next = SpeqaIds.nextFreeId(project, idType)
                val dup = currentId()?.let { SpeqaIds.isDuplicate(project, idType, it) } ?: false
                next to dup
            },
        )
            .finishOnUiThread(ModalityState.any()) { (next, dup) ->
                val changed = next != nextFreeId || dup != isDuplicate
                nextFreeId = next
                isDuplicate = dup
                if (changed) fire()
            }
            .submit(AppExecutorUtil.getAppExecutorService())
    }

    fun start() {
        refresh()
        refreshTimer.start()
    }

    fun stop() {
        refreshTimer.stop()
    }

    private fun fire() {
        listeners.forEach { runCatching { it.onChanged(this) } }
    }
}
