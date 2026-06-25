package io.github.barsia.speqa.toolwindow

import com.intellij.ide.util.treeView.NodeRenderer
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.TreeSpeedSearch
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.tree.AsyncTreeModel
import com.intellij.ui.tree.StructureTreeModel
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.EditSourceOnDoubleClickHandler
import com.intellij.util.EditSourceOnEnterKeyHandler
import com.intellij.util.ui.JBUI
import io.github.barsia.speqa.SpeqaBundle
import io.github.barsia.speqa.filetype.SpeqaIcons
import io.github.barsia.speqa.model.Status
import io.github.barsia.speqa.wizard.SpeqaProjectScaffold
import java.awt.BorderLayout
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JPanel

/** One choice in the status-filter dropdown; `status == null` is the "All" option. */
private data class StatusFilterOption(val status: Status?, val label: String, val icon: Icon?)

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
        EditSourceOnDoubleClickHandler.install(tree)
        EditSourceOnEnterKeyHandler.install(tree)

        subscribeToVfsChanges(project, toolWindow, rootDir, cache, treeModel)

        val panel = JPanel(BorderLayout()).apply {
            add(createFilterHeader(filter, treeModel), BorderLayout.NORTH)
            add(ScrollPaneFactory.createScrollPane(tree), BorderLayout.CENTER)
        }

        val content = ContentFactory.getInstance().createContent(panel, null, false)
        toolWindow.contentManager.addContent(content)
    }

    private fun createFilterHeader(
        filter: SpeqaTreeFilter,
        treeModel: StructureTreeModel<SpeqaTreeStructure>,
    ): JComponent {
        val options = arrayOf(
            StatusFilterOption(null, SpeqaBundle.message("toolwindow.speqa.filter.all"), null),
            StatusFilterOption(Status.DRAFT, SpeqaBundle.message("toolwindow.speqa.filter.draft"), SpeqaIcons.forStatus(Status.DRAFT)),
            StatusFilterOption(Status.READY, SpeqaBundle.message("toolwindow.speqa.filter.ready"), SpeqaIcons.forStatus(Status.READY)),
            StatusFilterOption(Status.DEPRECATED, SpeqaBundle.message("toolwindow.speqa.filter.deprecated"), SpeqaIcons.forStatus(Status.DEPRECATED)),
        )

        val combo = ComboBox(options).apply {
            renderer = object : SimpleListCellRenderer<StatusFilterOption>() {
                override fun customize(
                    list: javax.swing.JList<out StatusFilterOption>,
                    value: StatusFilterOption?,
                    index: Int,
                    selected: Boolean,
                    hasFocus: Boolean,
                ) {
                    if (value != null) {
                        text = value.label
                        icon = value.icon
                    }
                }
            }
            addActionListener {
                filter.status = (selectedItem as? StatusFilterOption)?.status
                treeModel.invalidateAsync()
            }
        }

        return JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(4)
            add(combo, BorderLayout.CENTER)
        }
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
