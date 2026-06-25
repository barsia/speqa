package io.github.barsia.speqa.toolwindow

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import org.jdom.Element

/**
 * Persists the SpeQA test-cases tool-window tree's expansion and selection state
 * per project (in the workspace file) so it survives project reopen and IDE
 * restart. The stored element is a serialized platform `TreeState`.
 */
@Service(Service.Level.PROJECT)
@State(name = "SpeqaTestCasesToolWindow", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
class SpeqaToolWindowTreeState : PersistentStateComponent<Element> {
    private var treeState: Element = Element(ROOT_TAG)

    override fun getState(): Element = treeState.clone()

    override fun loadState(state: Element) {
        treeState = state.clone()
    }

    /** The last persisted tree state, or null if nothing has been stored yet. */
    fun read(): Element? = treeState.takeIf { !it.children.isEmpty() }?.clone()

    /** Replace the persisted tree state with [element]. */
    fun write(element: Element) {
        treeState = element.clone()
    }

    companion object {
        private const val ROOT_TAG = "state"

        fun getInstance(project: Project): SpeqaToolWindowTreeState = project.service()
    }
}
