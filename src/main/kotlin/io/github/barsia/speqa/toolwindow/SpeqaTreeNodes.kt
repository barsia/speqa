package io.github.barsia.speqa.toolwindow

import com.intellij.icons.AllIcons
import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import io.github.barsia.speqa.filetype.SpeqaIcons

/** Folder section: its children are subfolders (all of them) plus `.tc.md` leaves. */
class SpeqaFolderNode(
    project: Project,
    dir: VirtualFile,
    private val cache: TestCaseSummaryCache,
) : AbstractTreeNode<VirtualFile>(project, dir) {

    override fun getChildren(): Collection<AbstractTreeNode<*>> {
        val dir = value
        if (!dir.isValid || !dir.isDirectory) return emptyList()

        val items = dir.children.mapNotNull { child ->
            when {
                child.isDirectory -> SpeqaTreeItem.Folder(child, child.name)
                isTestCaseFileName(child.name) ->
                    SpeqaTreeItem.TestCase(child, cache.summaryFor(child).title)
                else -> null
            }
        }

        val project = project ?: return emptyList()
        return orderChildren(items).map { item ->
            when (item) {
                is SpeqaTreeItem.Folder -> SpeqaFolderNode(project, item.payload, cache)
                is SpeqaTreeItem.TestCase -> SpeqaTestCaseNode(project, item.payload, cache)
            }
        }
    }

    override fun update(presentation: PresentationData) {
        presentation.presentableText = value.name
        presentation.setIcon(AllIcons.Nodes.Folder)
    }
}

/** Leaf test case: labeled by parsed title, navigates to the file on open. */
class SpeqaTestCaseNode(
    project: Project,
    file: VirtualFile,
    private val cache: TestCaseSummaryCache,
) : AbstractTreeNode<VirtualFile>(project, file) {

    override fun getChildren(): Collection<AbstractTreeNode<*>> = emptyList()

    override fun update(presentation: PresentationData) {
        val summary = cache.summaryFor(value)
        presentation.presentableText = summary.title
        presentation.setIcon(SpeqaIcons.forStatus(summary.status))
    }

    override fun canNavigate(): Boolean = value.isValid

    override fun canNavigateToSource(): Boolean = value.isValid

    override fun navigate(requestFocus: Boolean) {
        val project = project ?: return
        if (value.isValid) {
            FileEditorManager.getInstance(project).openFile(value, requestFocus)
        }
    }
}
