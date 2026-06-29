package io.github.barsia.speqa.toolwindow

import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.Disposable
import com.intellij.ui.tree.TreeVisitor
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.tree.TreeUtil
import javax.swing.SwingUtilities
import javax.swing.tree.TreePath

/** Per-node decision for walking a SpeQA tree down to a target file. */
internal enum class RevealStep {
    /** This node IS the target file: select it. */
    SELECT,

    /** This node is a directory that contains the target: keep descending. */
    DESCEND,

    /** This node is neither the target nor an ancestor of it: prune. */
    SKIP,
}

/**
 * Pure decision used while walking a SpeQA tab tree toward a freshly created file. Given the
 * file path of a visited node (and whether it is a directory) and the [targetPath], it says
 * whether to select the node, descend into it, or skip it. Kept pure so the tree-reveal logic
 * is unit-testable without the platform tree.
 */
internal fun revealStepFor(candidatePath: String, candidateIsDirectory: Boolean, targetPath: String): RevealStep =
    when {
        candidatePath == targetPath -> RevealStep.SELECT
        candidateIsDirectory && targetPath.startsWith("$candidatePath/") -> RevealStep.DESCEND
        else -> RevealStep.SKIP
    }

/**
 * Tree visitor that drives [TreeUtil.promiseSelect] to the node backing [target], using the
 * pure [revealStepFor] decision at each visited node. Nodes without a [VirtualFile] value
 * (e.g. the async model's transient loading node) are descended through.
 */
internal class SpeqaFileTreeVisitor(private val target: VirtualFile) : TreeVisitor {
    override fun visit(path: TreePath): TreeVisitor.Action {
        val node = TreeUtil.getLastUserObject(AbstractTreeNode::class.java, path)
        val file = node?.value as? VirtualFile ?: return TreeVisitor.Action.CONTINUE
        return when (revealStepFor(file.path, file.isDirectory, target.path)) {
            RevealStep.SELECT -> TreeVisitor.Action.INTERRUPT
            RevealStep.DESCEND -> TreeVisitor.Action.CONTINUE
            RevealStep.SKIP -> TreeVisitor.Action.SKIP_CHILDREN
        }
    }
}

/** Reveals (selects + scrolls to) a test-run file inside the currently built TRs tab. */
internal fun interface TestRunRevealer {
    fun reveal(file: VirtualFile)
}

/**
 * Bridges the test-run creation flow (which has no reference to the tool-window UI) and the
 * tool window's TRs tab. The tool window registers a [TestRunRevealer] each time it (re)builds
 * its content, scoped to that build's disposable; the creation flow calls [reveal] after the
 * `.tr.md` file is written and opened. If the tool window has not been built yet (or the TRs
 * tab has no tree), the call is a no-op.
 */
@Service(Service.Level.PROJECT)
internal class SpeqaTestRunRevealService {
    @Volatile
    private var revealer: TestRunRevealer? = null

    /** Installs the active build's revealer, clearing it automatically when that build is disposed. */
    fun register(revealer: TestRunRevealer, parentDisposable: Disposable) {
        this.revealer = revealer
        Disposer.register(parentDisposable) {
            if (this.revealer === revealer) this.revealer = null
        }
    }

    /** Selects [file] in the TRs tab, if the tool window is currently built. EDT-only. */
    fun reveal(file: VirtualFile) {
        revealer?.reveal(file)
    }

    companion object {
        fun getInstance(project: Project): SpeqaTestRunRevealService = project.service()
    }
}

/**
 * Selects [target] in [tree] once its node exists. The model is invalidated first so a row
 * for a just-created file is present before the visitor walks the tree; selection then runs on
 * the EDT without requesting focus (so the editor opened by the caller keeps the caret).
 */
internal fun selectFileInTree(tree: Tree, target: VirtualFile) {
    TreeUtil.promiseSelect(tree, SpeqaFileTreeVisitor(target))
}

/**
 * Opens a freshly created `.tr.md` run in the editor with focus, then reveals it in the SpeQA
 * tool window's TRs tab (activating the tab and selecting the row) without stealing focus back
 * from the editor. Shared by the single-case and multi-case run creation flows. EDT-only.
 */
internal fun openAndRevealTestRun(project: Project, runFile: VirtualFile) {
    FileEditorManager.getInstance(project).openFile(runFile, true)
    val reveal = { SpeqaTestRunRevealService.getInstance(project).reveal(runFile) }
    if (ApplicationManager.getApplication().isDispatchThread) reveal() else SwingUtilities.invokeLater(reveal)
}
