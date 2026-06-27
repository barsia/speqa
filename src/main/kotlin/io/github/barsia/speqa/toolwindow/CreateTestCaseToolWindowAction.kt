package io.github.barsia.speqa.toolwindow

import com.intellij.icons.AllIcons
import com.intellij.ide.fileTemplates.FileTemplateManager
import com.intellij.ide.fileTemplates.FileTemplateUtil
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.tree.TreeUtil
import io.github.barsia.speqa.SpeqaBundle
import io.github.barsia.speqa.registry.IdType
import io.github.barsia.speqa.registry.SpeqaIds
import io.github.barsia.speqa.wizard.SpeqaProjectScaffold

/**
 * Title-bar "+" action for the TCs tab: creates a new test case, placed by the
 * shared selection rule [resolveCreationDir] relative to the TCs tree selection.
 */
internal class CreateTestCaseToolWindowAction(private val tree: Tree) : DumbAwareAction(
    SpeqaBundle.message("action.createTestCase.text"),
    SpeqaBundle.message("action.createTestCase.description"),
    AllIcons.General.Add,
) {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        createTestCaseFromToolWindow(project, tree)
    }
}

/**
 * Resolves the directory a new leaf should be created in from the tab's tree
 * selection: a selected leaf -> its parent folder (sibling); a selected folder ->
 * that folder; nothing selected (or an invalid target) -> [rootDir].
 */
internal fun resolveCreationDir(tree: Tree, rootDir: VirtualFile): VirtualFile {
    val path = tree.selectionPath ?: return rootDir
    return when (val node = TreeUtil.getLastUserObject(path)) {
        is SpeqaLeafNode -> node.value.parent?.takeIf { it.isValid && it.isDirectory } ?: rootDir
        is SpeqaFolderNode -> node.value.takeIf { it.isValid && it.isDirectory } ?: rootDir
        else -> rootDir
    }
}

internal fun createTestCaseFromToolWindow(project: Project, tree: Tree) {
    val name = Messages.showInputDialog(
        project,
        SpeqaBundle.message("dialog.newTestCase.prompt"),
        SpeqaBundle.message("dialog.newTestCase.title"),
        null,
    )?.trim()
    if (name.isNullOrEmpty()) return
    val fileName = if (name.endsWith(".tc.md", ignoreCase = true)) name else "$name.tc.md"

    val created = WriteCommandAction.writeCommandAction(project)
        .withName(SpeqaBundle.message("action.createTestCase.text"))
        .compute<PsiFile?, Exception> {
            val projectDir = project.guessProjectDir() ?: return@compute null
            val tcRoot = projectDir.findChild(SpeqaProjectScaffold.TEST_CASES_DIR)?.takeIf { it.isDirectory }
                ?: VfsUtil.createDirectoryIfMissing(projectDir, SpeqaProjectScaffold.TEST_CASES_DIR)
                ?: return@compute null
            val targetDir = resolveCreationDir(tree, tcRoot)
            val psiDir = PsiManager.getInstance(project).findDirectory(targetDir) ?: return@compute null
            val templateManager = FileTemplateManager.getInstance(project)
            val template = templateManager.getInternalTemplate("SpeQA Test Case.tc.md")
            val props = templateManager.defaultProperties.apply {
                setProperty("ID", SpeqaIds.nextFreeId(project, IdType.TEST_CASE).toString())
            }
            FileTemplateUtil.createFromTemplate(template, fileName, props, psiDir) as? PsiFile
        }
    created?.virtualFile?.let { FileEditorManager.getInstance(project).openFile(it, true) }
}
