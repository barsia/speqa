package io.github.barsia.speqa.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.intellij.openapi.project.Project
import io.github.barsia.speqa.registry.IdType
import io.github.barsia.speqa.registry.SpeqaIdRegistry
import javax.swing.Timer

class IdStateHolder(
    private val project: Project,
    private val idType: IdType,
    private val currentId: () -> Int?,
) {
    var nextFreeId by mutableStateOf(computeNextFreeId())
        private set
    var isDuplicate by mutableStateOf(computeIsDuplicate())
        private set
    var isEditing by mutableStateOf(false)

    private val refreshTimer = Timer(2000) {
        refresh()
    }.apply {
        isRepeats = true
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
        nextFreeId = computeNextFreeId()
        isDuplicate = computeIsDuplicate()
    }

    fun start() {
        refreshTimer.start()
    }

    fun stop() {
        refreshTimer.stop()
    }
}
