package io.github.barsia.speqa.toolwindow

import com.intellij.icons.AllIcons
import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import io.github.barsia.speqa.filetype.SpeqaIcons
import io.github.barsia.speqa.model.Status

/** Folder section: its children are subfolders plus `.tc.md` leaves, honoring the active status filter. */
class SpeqaFolderNode(
    project: Project,
    dir: VirtualFile,
    private val cache: TestCaseSummaryCache,
    private val filter: SpeqaTreeFilter,
) : AbstractTreeNode<VirtualFile>(project, dir) {

    override fun getChildren(): Collection<AbstractTreeNode<*>> {
        val dir = value
        if (!dir.isValid || !dir.isDirectory) return emptyList()

        val activeStatus = filter.status
        val items = dir.children.mapNotNull { child ->
            when {
                child.isDirectory ->
                    if (activeStatus == null || folderHasMatch(child, activeStatus, cache)) {
                        SpeqaTreeItem.Folder(child, child.name)
                    } else {
                        null
                    }
                isTestCaseFileName(child.name) -> {
                    val summary = cache.summaryFor(child)
                    if (matchesStatusFilter(summary.status, activeStatus)) {
                        SpeqaTreeItem.TestCase(child, summary.title)
                    } else {
                        null
                    }
                }
                else -> null
            }
        }

        val project = project ?: return emptyList()
        return orderChildren(items).map { item ->
            when (item) {
                is SpeqaTreeItem.Folder -> SpeqaFolderNode(project, item.payload, cache, filter)
                is SpeqaTreeItem.TestCase -> SpeqaTestCaseNode(project, item.payload, cache)
            }
        }
    }

    override fun update(presentation: PresentationData) {
        presentation.presentableText = value.name
        presentation.setIcon(AllIcons.Nodes.Folder)
    }
}

/**
 * True when [dir] recursively contains at least one `.tc.md` whose status equals
 * [activeStatus]. Used to hide folders that would be empty under an active filter.
 */
private fun folderHasMatch(dir: VirtualFile, activeStatus: Status, cache: TestCaseSummaryCache): Boolean {
    if (!dir.isValid || !dir.isDirectory) return false
    return dir.children.any { child ->
        when {
            child.isDirectory -> folderHasMatch(child, activeStatus, cache)
            isTestCaseFileName(child.name) -> cache.summaryFor(child).status == activeStatus
            else -> false
        }
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
