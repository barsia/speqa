package io.github.barsia.speqa.toolwindow

import com.intellij.ide.util.treeView.NodeRenderer
import com.intellij.ide.util.treeView.TreeState
import com.intellij.openapi.actionSystem.CommonShortcuts
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.DoubleClickListener
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.TreeSpeedSearch
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.tree.AsyncTreeModel
import com.intellij.ui.tree.StructureTreeModel
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.tree.TreeUtil
import io.github.barsia.speqa.SpeqaBundle
import io.github.barsia.speqa.wizard.SpeqaProjectScaffold
import java.awt.BorderLayout
import java.awt.event.MouseEvent
import javax.swing.JPanel

class SpeqaToolWindowFactory : ToolWindowFactory, DumbAware {

    override fun shouldBeAvailable(project: Project): Boolean = testCasesDir(project) != null

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val rootDir = testCasesDir(project) ?: return
        val cache = TestCaseSummaryCache()
        val filter = SpeqaTreeFilter()
        val structure = SpeqaTreeStructure(project, rootDir, cache, filter)
        val treeModel = StructureTreeModel(structure, toolWindow.disposable)
        val asyncModel = AsyncTreeModel(treeModel, toolWindow.disposable)

        val tree = Tree(asyncModel).apply {
            isRootVisible = false
            showsRootHandles = true
            cellRenderer = NodeRenderer()
            emptyText.text = SpeqaBundle.message("toolwindow.speqa.empty")
        }
        TreeSpeedSearch.installOn(tree)
        installOpenHandlers(tree)

        subscribeToVfsChanges(project, toolWindow, rootDir, cache, treeModel)

        val header = SpeqaFilterHeader(project, filter, toolWindow.disposable) { treeModel.invalidateAsync() }
        toolWindow.setTitleActions(header.titleActions)
        val panel = JPanel(BorderLayout()).apply {
            add(header.component, BorderLayout.NORTH)
            add(ScrollPaneFactory.createScrollPane(tree), BorderLayout.CENTER)
        }

        val content = ContentFactory.getInstance().createContent(panel, null, false)
        toolWindow.contentManager.addContent(content)

        restoreAndTrackTreeState(project, toolWindow, tree)
    }

    /**
     * Re-applies the persisted expansion/selection state and keeps the persisted
     * snapshot current. State is captured eagerly on every expand/collapse and
     * selection change (and once more on dispose) rather than only on dispose:
     * on project close the platform may serialize the component before the
     * tool-window disposable runs, so a dispose-only capture would persist a
     * stale snapshot and the tree would not restore.
     */
    private fun restoreAndTrackTreeState(project: Project, toolWindow: ToolWindow, tree: Tree) {
        val store = SpeqaToolWindowTreeState.getInstance(project)
        store.read()?.let { TreeState.createFrom(it).applyTo(tree) }

        val capture = {
            val element = org.jdom.Element("state")
            TreeState.createOn(tree).writeExternal(element)
            store.write(element)
        }
        tree.addTreeExpansionListener(object : javax.swing.event.TreeExpansionListener {
            override fun treeExpanded(event: javax.swing.event.TreeExpansionEvent) = capture()
            override fun treeCollapsed(event: javax.swing.event.TreeExpansionEvent) = capture()
        })
        tree.addTreeSelectionListener { capture() }
        Disposer.register(toolWindow.disposable) { capture() }
    }

    /**
     * Opens the test case under a double-click, or the selected one on Enter, by
     * calling the node's own [SpeqaTestCaseNode.navigate]. We do not use
     * `EditSourceOnDoubleClickHandler`/`EditSourceOnEnterKeyHandler`: those resolve
     * the target through `CommonDataKeys.NAVIGATABLE`, which a plain tree over
     * `AbstractTreeNode`s does not publish (only the Project view's pane does), so
     * they would silently open nothing.
     */
    private fun installOpenHandlers(tree: Tree) {
        object : DoubleClickListener() {
            override fun onDoubleClick(event: MouseEvent): Boolean {
                val path = tree.getPathForLocation(event.x, event.y) ?: return false
                val node = TreeUtil.getLastUserObject(SpeqaTestCaseNode::class.java, path) ?: return false
                node.navigate(true)
                return true
            }
        }.installOn(tree)

        DumbAwareAction.create {
            TreeUtil.getLastUserObject(SpeqaTestCaseNode::class.java, tree.selectionPath)?.navigate(true)
        }.registerCustomShortcutSet(CommonShortcuts.ENTER, tree)
    }

    private fun subscribeToVfsChanges(
        project: Project,
        toolWindow: ToolWindow,
        rootDir: VirtualFile,
        cache: TestCaseSummaryCache,
        treeModel: StructureTreeModel<SpeqaTreeStructure>,
    ) {
        project.messageBus.connect(toolWindow.disposable)
            .subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
                override fun after(events: List<VFileEvent>) {
                    val rootPath = rootDir.path
                    // VFS paths use '/' on all platforms; match the directory itself
                    // or a descendant, never a sibling like "test-cases-old".
                    val descendantPrefix = "$rootPath/"
                    val relevant = events.filter {
                        it.path == rootPath || it.path.startsWith(descendantPrefix)
                    }
                    if (relevant.isEmpty()) return
                    relevant.forEach { cache.invalidate(it.path) }
                    treeModel.invalidateAsync()
                }
            })
    }

    private fun testCasesDir(project: Project): VirtualFile? =
        project.guessProjectDir()
            ?.findChild(SpeqaProjectScaffold.TEST_CASES_DIR)
            ?.takeIf { it.isDirectory }
}
