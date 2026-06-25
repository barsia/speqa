package io.github.barsia.speqa.toolwindow

import com.intellij.ide.util.treeView.NodeRenderer
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.TreeSpeedSearch
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.tree.AsyncTreeModel
import com.intellij.ui.tree.StructureTreeModel
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.EditSourceOnDoubleClickHandler
import com.intellij.util.EditSourceOnEnterKeyHandler
import io.github.barsia.speqa.SpeqaBundle
import io.github.barsia.speqa.wizard.SpeqaProjectScaffold

class SpeqaToolWindowFactory : ToolWindowFactory, DumbAware {

    override fun shouldBeAvailable(project: Project): Boolean = testCasesDir(project) != null

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val rootDir = testCasesDir(project) ?: return
        val cache = TestCaseSummaryCache()
        val structure = SpeqaTreeStructure(project, rootDir, cache)
        val treeModel = StructureTreeModel(structure, toolWindow.disposable)
        val asyncModel = AsyncTreeModel(treeModel, toolWindow.disposable)

        val tree = Tree(asyncModel).apply {
            isRootVisible = false
            showsRootHandles = true
            cellRenderer = NodeRenderer()
            emptyText.text = SpeqaBundle.message("toolwindow.speqa.empty")
        }
        TreeSpeedSearch.installOn(tree)
        EditSourceOnDoubleClickHandler.install(tree)
        EditSourceOnEnterKeyHandler.install(tree)

        subscribeToVfsChanges(project, toolWindow, rootDir, cache, treeModel)

        val content = ContentFactory.getInstance()
            .createContent(ScrollPaneFactory.createScrollPane(tree), null, false)
        toolWindow.contentManager.addContent(content)
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
                    val relevant = events.filter { it.path.startsWith(rootPath) }
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
