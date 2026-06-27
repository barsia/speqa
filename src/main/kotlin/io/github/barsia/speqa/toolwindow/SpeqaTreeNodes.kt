package io.github.barsia.speqa.toolwindow

import com.intellij.icons.AllIcons
import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import javax.swing.Icon

/**
 * Per-tab strategy that adapts the shared tree machinery to a leaf file type.
 * The TCs and TRs tabs differ only in which files are leaves, how a leaf is
 * matched against the active filter, and how a leaf is labeled and stamped.
 */
interface SpeqaLeafSpec {
    /** True when a child file name denotes a leaf of this tab (`.tc.md` / `.tr.md`). */
    fun isLeaf(name: String): Boolean

    /** True when the active filter constrains results (so empty folders are pruned). */
    fun isFiltering(): Boolean

    /** True when [file] satisfies the active filter. */
    fun matches(file: VirtualFile): Boolean

    /** Display title for a leaf [file]. */
    fun title(file: VirtualFile): String

    /** Stamp icon for a leaf [file]. */
    fun icon(file: VirtualFile): Icon
}

/** Folder section: its children are subfolders plus leaf files, honoring the active filter. */
class SpeqaFolderNode(
    project: Project,
    dir: VirtualFile,
    private val spec: SpeqaLeafSpec,
) : AbstractTreeNode<VirtualFile>(project, dir) {

    override fun getChildren(): Collection<AbstractTreeNode<*>> {
        val dir = value
        if (!dir.isValid || !dir.isDirectory) return emptyList()

        val filtering = spec.isFiltering()
        val items = dir.children.mapNotNull { child ->
            when {
                child.isDirectory ->
                    if (!filtering || folderHasMatch(child, spec)) {
                        SpeqaTreeItem.Folder(child, child.name)
                    } else {
                        null
                    }
                spec.isLeaf(child.name) ->
                    if (spec.matches(child)) {
                        SpeqaTreeItem.Leaf(child, spec.title(child))
                    } else {
                        null
                    }
                else -> null
            }
        }

        val project = project ?: return emptyList()
        return orderChildren(items).map { item ->
            when (item) {
                is SpeqaTreeItem.Folder -> SpeqaFolderNode(project, item.payload, spec)
                is SpeqaTreeItem.Leaf -> SpeqaLeafNode(project, item.payload, spec)
            }
        }
    }

    override fun update(presentation: PresentationData) {
        presentation.presentableText = value.name
        presentation.setIcon(AllIcons.Nodes.Folder)
    }
}

/**
 * True when [dir] recursively contains at least one leaf that satisfies the spec's
 * filter. Used to hide folders that would be empty under an active filter.
 */
private fun folderHasMatch(dir: VirtualFile, spec: SpeqaLeafSpec): Boolean {
    if (!dir.isValid || !dir.isDirectory) return false
    return dir.children.any { child ->
        when {
            child.isDirectory -> folderHasMatch(child, spec)
            spec.isLeaf(child.name) -> spec.matches(child)
            else -> false
        }
    }
}

/**
 * True when [dir] (recursively) contains at least one leaf file of the spec's type,
 * regardless of the active filter. Used to hide the filter controls for a tab that
 * has no test cases / test runs at all, since there is nothing to filter.
 */
fun hasAnyLeaf(dir: VirtualFile, spec: SpeqaLeafSpec): Boolean {
    if (!dir.isValid || !dir.isDirectory) return false
    return dir.children.any { child ->
        if (child.isDirectory) hasAnyLeaf(child, spec) else spec.isLeaf(child.name)
    }
}

/** Leaf node: labeled by parsed title, stamped by the spec, navigates to the file on open. */
class SpeqaLeafNode(
    project: Project,
    file: VirtualFile,
    private val spec: SpeqaLeafSpec,
) : AbstractTreeNode<VirtualFile>(project, file) {

    override fun getChildren(): Collection<AbstractTreeNode<*>> = emptyList()

    override fun update(presentation: PresentationData) {
        presentation.presentableText = spec.title(value)
        presentation.setIcon(spec.icon(value))
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
