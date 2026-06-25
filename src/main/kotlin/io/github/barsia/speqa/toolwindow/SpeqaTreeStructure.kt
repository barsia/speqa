package io.github.barsia.speqa.toolwindow

import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.ide.util.treeView.AbstractTreeStructure
import com.intellij.ide.util.treeView.NodeDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/**
 * Tree structure rooted at the `test-cases/` directory. The root node is hidden
 * (the tree is configured with `isRootVisible = false`), so its children form
 * the top level shown to the user.
 */
class SpeqaTreeStructure(
    project: Project,
    rootDir: VirtualFile,
    cache: TestCaseSummaryCache,
    filter: SpeqaTreeFilter,
) : AbstractTreeStructure() {

    private val root = SpeqaFolderNode(project, rootDir, cache, filter)

    override fun getRootElement(): Any = root

    override fun getChildElements(element: Any): Array<Any> =
        (element as AbstractTreeNode<*>).children.toTypedArray()

    override fun getParentElement(element: Any): Any? =
        (element as? AbstractTreeNode<*>)?.parent

    override fun createDescriptor(element: Any, parentDescriptor: NodeDescriptor<*>?): NodeDescriptor<*> =
        element as NodeDescriptor<*>

    override fun commit() = Unit

    override fun hasSomethingToCommit(): Boolean = false
}
