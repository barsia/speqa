package io.github.barsia.speqa.toolwindow

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import org.jdom.Element

/** Read/write contract for a per-tab tool-window tree state, implemented by the per-tab services. */
interface SpeqaTreeStateStore {
    /** The last persisted tree state, or null if nothing has been stored yet. */
    fun read(): Element?

    /** Replace the persisted tree state with [element]. */
    fun write(element: Element)
}

/**
 * Persists the SpeQA TCs tab tree's expansion and selection state per project
 * (in the workspace file) so it survives project reopen and IDE restart. The
 * stored element is a serialized platform `TreeState`.
 */
@Service(Service.Level.PROJECT)
@State(name = "SpeqaTestCasesToolWindow", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
class SpeqaToolWindowTreeState : PersistentStateComponent<Element>, SpeqaTreeStateStore {
    private var treeState: Element = Element(ROOT_TAG)

    override fun getState(): Element = treeState.clone()

    override fun loadState(state: Element) {
        treeState = state.clone()
    }

    override fun read(): Element? = treeState.takeIf { !it.children.isEmpty() }?.clone()

    override fun write(element: Element) {
        treeState = element.clone()
    }

    companion object {
        private const val ROOT_TAG = "state"

        fun getInstance(project: Project): SpeqaToolWindowTreeState = project.service()
    }
}

/**
 * Persists the SpeQA TRs tab tree's expansion and selection state per project,
 * in a separate workspace slot from the TCs tab so each tab restores its own tree.
 */
@Service(Service.Level.PROJECT)
@State(name = "SpeqaTestRunsToolWindow", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
class SpeqaTestRunsToolWindowTreeState : PersistentStateComponent<Element>, SpeqaTreeStateStore {
    private var treeState: Element = Element(ROOT_TAG)

    override fun getState(): Element = treeState.clone()

    override fun loadState(state: Element) {
        treeState = state.clone()
    }

    override fun read(): Element? = treeState.takeIf { !it.children.isEmpty() }?.clone()

    override fun write(element: Element) {
        treeState = element.clone()
    }

    companion object {
        private const val ROOT_TAG = "state"

        fun getInstance(project: Project): SpeqaTestRunsToolWindowTreeState = project.service()
    }
}

/**
 * Persists which SpeQA tab (TCs/TRs) was last selected, per project (in the workspace
 * file), so the tool window reopens on the tab that was active before the project was
 * closed or the IDE restarted. Defaults to the TCs tab.
 */
@Service(Service.Level.PROJECT)
@State(name = "SpeqaToolWindowSelection", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
class SpeqaToolWindowSelection : PersistentStateComponent<SpeqaToolWindowSelection.SelectionState> {
    class SelectionState {
        var selectedTab: String = TAB_TEST_CASES
    }

    private var state = SelectionState()

    override fun getState(): SelectionState = state

    override fun loadState(state: SelectionState) {
        this.state = state
    }

    var selectedTab: String
        get() = state.selectedTab
        set(value) {
            state.selectedTab = value
        }

    companion object {
        const val TAB_TEST_CASES = "testCases"
        const val TAB_TEST_RUNS = "testRuns"

        fun getInstance(project: Project): SpeqaToolWindowSelection = project.service()
    }
}
