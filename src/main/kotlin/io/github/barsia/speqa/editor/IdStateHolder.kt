package io.github.barsia.speqa.editor

import com.intellij.openapi.project.Project
import io.github.barsia.speqa.registry.IdType
import io.github.barsia.speqa.registry.SpeqaIdRegistry
import java.util.concurrent.CopyOnWriteArrayList
import javax.swing.Timer

/**
 * Pure Swing/Kotlin state holder backing the preview's inline-editable ID row.
 * Exposes `nextFreeId`, `isDuplicate`, and `isEditing`. Callers register a
 * [Listener]; the holder fires it whenever any of those fields change, so UI
 * layers can re-render incrementally.
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
    var nextFreeId: Int = computeNextFreeId()
        private set

    @Volatile
    var isDuplicate: Boolean = computeIsDuplicate()
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

    private fun computeNextFreeId(): Int {
        val registry = SpeqaIdRegistry.getInstance(project)
        registry.ensureInitialized()
        return registry.idSet(idType).nextFreeId()
    }

    private fun computeIsDuplicate(): Boolean {
        val registry = SpeqaIdRegistry.getInstance(project)
        registry.ensureInitialized()
        return currentId()?.let { registry.idSet(idType).isDuplicate(it) } ?: false
    }

    fun refresh() {
        val newNextFree = computeNextFreeId()
        val newDuplicate = computeIsDuplicate()
        val changed = newNextFree != nextFreeId || newDuplicate != isDuplicate
        nextFreeId = newNextFree
        isDuplicate = newDuplicate
        if (changed) fire()
    }

    fun start() {
        refreshTimer.start()
    }

    fun stop() {
        refreshTimer.stop()
    }

    private fun fire() {
        listeners.forEach { runCatching { it.onChanged(this) } }
    }
}
