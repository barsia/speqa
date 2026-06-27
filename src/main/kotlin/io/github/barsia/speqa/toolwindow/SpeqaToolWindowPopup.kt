package io.github.barsia.speqa.toolwindow

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.PopupHandler
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.tree.TreeUtil
import io.github.barsia.speqa.SpeqaBundle
import io.github.barsia.speqa.editor.ui.chips.MetadataScope

/** Which kind of SpeQA node is currently selected in a tool-window tree. */
internal enum class SpeqaPopupNodeKind { LEAF, FOLDER, NONE }

internal fun selectedNodeKind(tree: Tree): SpeqaPopupNodeKind =
    when (TreeUtil.getLastUserObject(tree.selectionPath)) {
        is SpeqaLeafNode -> SpeqaPopupNodeKind.LEAF
        is SpeqaFolderNode -> SpeqaPopupNodeKind.FOLDER
        else -> SpeqaPopupNodeKind.NONE
    }

/** Selected folder's directory: a folder node's dir, or null when a leaf/none is selected. */
private fun selectedFolderDir(tree: Tree): VirtualFile? =
    (TreeUtil.getLastUserObject(tree.selectionPath) as? SpeqaFolderNode)
        ?.value?.takeIf { it.isValid && it.isDirectory }

/** Open the selected leaf file. Visible only when a leaf is selected. */
internal class PopupOpenLeafAction(private val tree: Tree) : DumbAwareAction(
    SpeqaBundle.message("toolwindow.speqa.popup.open"),
) {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = selectedNodeKind(tree) == SpeqaPopupNodeKind.LEAF
    }

    override fun actionPerformed(e: AnActionEvent) {
        TreeUtil.getLastUserObject(SpeqaLeafNode::class.java, tree.selectionPath)?.navigate(true)
    }
}

/** "New test case" for a selected folder (TCs tab). Visible only on folders. */
internal class PopupNewTestCaseAction(private val tree: Tree) : DumbAwareAction(
    SpeqaBundle.message("action.createTestCase.text"),
    SpeqaBundle.message("action.createTestCase.description"),
    null,
) {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = selectedNodeKind(tree) == SpeqaPopupNodeKind.FOLDER
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        createTestCaseFromToolWindow(project, tree)
    }
}

/**
 * "Create test run" for a selected TCs-tab folder: candidates are scoped to the folder
 * subtree and all pre-selected. Visible only on folders; disabled when the subtree has
 * no test cases.
 */
internal class PopupCreateRunFromFolderAction(private val tree: Tree) : DumbAwareAction(
    SpeqaBundle.message("action.createTestRun.text"),
    SpeqaBundle.message("action.createTestRun.description"),
    null,
) {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val folder = e.getData(CommonDataKeys.VIRTUAL_FILE)?.takeIf { it.isValid && it.isDirectory }
        e.presentation.isVisible = folder != null
        e.presentation.isEnabled = folder != null && anyTcLeaf(folder)
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val folder = selectedFolderDir(tree) ?: return
        openCreateTestRunDialog(project, candidateScopeDir = folder)
    }
}

/**
 * "Create test run" for a selected TRs-tab folder: the run is written into that folder;
 * candidates are all project test cases, all pre-selected. Visible only on folders;
 * disabled when the project has no test cases.
 */
internal class PopupCreateRunInFolderAction(private val tree: Tree) : DumbAwareAction(
    SpeqaBundle.message("action.createTestRun.text"),
    SpeqaBundle.message("action.createTestRun.description"),
    null,
) {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val project = e.project
        val folder = e.getData(CommonDataKeys.VIRTUAL_FILE)?.takeIf { it.isValid && it.isDirectory }
        e.presentation.isVisible = folder != null
        e.presentation.isEnabled = project != null && folder != null && tcLeafsExist(project)
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val folder = selectedFolderDir(tree) ?: return
        openCreateTestRunDialog(project, targetDir = folder)
    }
}

/** Action ids of the platform actions reused in the popup, added when present. */
private val PLATFORM_POPUP_ACTION_IDS = listOf(
    "RenameElement",
    "\$Delete",
    "SelectInProjectView",
    "RevealGroup",
)

/** Builds the context-menu group for a tab and installs it on [tree]. */
internal fun installToolWindowPopup(tree: Tree, scope: MetadataScope) {
    val group = DefaultActionGroup()
    val actionManager = ActionManager.getInstance()
    group.add(PopupOpenLeafAction(tree))
    if (scope == MetadataScope.TEST_CASES) {
        actionManager.getAction("Speqa.RunTestCase")?.let { group.add(it) }
        group.add(PopupNewTestCaseAction(tree))
        group.add(PopupCreateRunFromFolderAction(tree))
    } else {
        group.add(PopupCreateRunInFolderAction(tree))
    }
    group.addSeparator()
    PLATFORM_POPUP_ACTION_IDS.forEach { id ->
        actionManager.getAction(id)?.let { group.add(it) }
    }
    PopupHandler.installPopupHandler(tree, group, "SpeqaToolWindowPopup")
}
