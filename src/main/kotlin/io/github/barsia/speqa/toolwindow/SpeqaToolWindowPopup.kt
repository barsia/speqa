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

/**
 * The SpeQA-owned context-menu items (the reused platform Rename/Delete/Reveal actions are
 * appended separately). [visibleFor] is the node selection the item is shown for. This is pure
 * data so the per-tab menu composition and per-node-kind visibility can be unit-tested without
 * the platform; the actions below and [speqaPopupItems] both derive from it.
 */
internal enum class SpeqaPopupItem(val visibleFor: Set<SpeqaPopupNodeKind>) {
    OPEN_LEAF(setOf(SpeqaPopupNodeKind.LEAF)),
    RUN_TEST_CASE(setOf(SpeqaPopupNodeKind.LEAF)),
    NEW_TEST_CASE(setOf(SpeqaPopupNodeKind.FOLDER)),
    CREATE_RUN_FROM_FOLDER(setOf(SpeqaPopupNodeKind.FOLDER)),
    CREATE_RUN_IN_FOLDER(setOf(SpeqaPopupNodeKind.FOLDER)),
}

/**
 * Ordered SpeQA-owned menu items for a tab. The TCs tab can create test cases and run/scope a
 * run from its folders; the TRs tab only creates a run into the selected folder (no New Test
 * Case, no Run Test Case). Pure function: the single source of truth for menu composition.
 */
internal fun speqaPopupItems(scope: MetadataScope): List<SpeqaPopupItem> = when (scope) {
    MetadataScope.TEST_CASES -> listOf(
        SpeqaPopupItem.OPEN_LEAF,
        SpeqaPopupItem.RUN_TEST_CASE,
        SpeqaPopupItem.NEW_TEST_CASE,
        SpeqaPopupItem.CREATE_RUN_FROM_FOLDER,
    )
    MetadataScope.TEST_RUNS -> listOf(
        SpeqaPopupItem.OPEN_LEAF,
        SpeqaPopupItem.CREATE_RUN_IN_FOLDER,
    )
}

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
        e.presentation.isEnabledAndVisible = selectedNodeKind(tree) in SpeqaPopupItem.OPEN_LEAF.visibleFor
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
        e.presentation.isEnabledAndVisible = selectedNodeKind(tree) in SpeqaPopupItem.NEW_TEST_CASE.visibleFor
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

/** Builds the context-menu group for a tab from [speqaPopupItems] and installs it on [tree]. */
internal fun installToolWindowPopup(tree: Tree, scope: MetadataScope) {
    val group = DefaultActionGroup()
    val actionManager = ActionManager.getInstance()
    speqaPopupItems(scope).forEach { item ->
        val action = when (item) {
            SpeqaPopupItem.OPEN_LEAF -> PopupOpenLeafAction(tree)
            // Reused platform action; manages its own visibility (enabled for a .tc.md leaf).
            SpeqaPopupItem.RUN_TEST_CASE -> actionManager.getAction("Speqa.RunTestCase")
            SpeqaPopupItem.NEW_TEST_CASE -> PopupNewTestCaseAction(tree)
            SpeqaPopupItem.CREATE_RUN_FROM_FOLDER -> PopupCreateRunFromFolderAction(tree)
            SpeqaPopupItem.CREATE_RUN_IN_FOLDER -> PopupCreateRunInFolderAction(tree)
        }
        if (action != null) group.add(action)
    }
    group.addSeparator()
    PLATFORM_POPUP_ACTION_IDS.forEach { id ->
        actionManager.getAction(id)?.let { group.add(it) }
    }
    PopupHandler.installPopupHandler(tree, group, "SpeqaToolWindowPopup")
}
