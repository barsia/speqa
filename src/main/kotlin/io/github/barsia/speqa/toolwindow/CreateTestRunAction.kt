package io.github.barsia.speqa.toolwindow

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VirtualFile
import io.github.barsia.speqa.SpeqaBundle
import io.github.barsia.speqa.editor.runcreation.CandidateCase
import io.github.barsia.speqa.editor.runcreation.CaseFacets
import io.github.barsia.speqa.editor.runcreation.CreateMultiCaseRunWriter
import io.github.barsia.speqa.editor.runcreation.CreateTestRunDialog
import io.github.barsia.speqa.wizard.SpeqaProjectScaffold
import com.intellij.ui.treeStructure.Tree
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

internal class CreateTestRunAction(private val tree: Tree? = null) : DumbAwareAction(
    SpeqaBundle.message("action.createTestRun.text"),
    SpeqaBundle.message("action.createTestRun.description"),
    AllIcons.General.Add,
) {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val project = e.project
        if (project == null) {
            e.presentation.isEnabledAndVisible = false
            return
        }
        e.presentation.isVisible = true
        val hasTestCases = tcLeafsExist(project)
        e.presentation.isEnabled = hasTestCases
        e.presentation.description = if (hasTestCases) {
            SpeqaBundle.message("action.createTestRun.description")
        } else {
            SpeqaBundle.message("action.createTestRun.noTestCases")
        }
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val target = tree?.let { t ->
            project.guessProjectDir()
                ?.findChild(SpeqaProjectScaffold.TEST_RUNS_DIR)
                ?.takeIf { it.isDirectory }
                ?.let { trRoot -> resolveCreationDir(t, trRoot) }
        }
        openCreateTestRunDialog(project, target)
    }
}

/** True when the test-cases directory contains at least one `.tc.md` file. */
internal fun tcLeafsExist(project: Project): Boolean {
    val tcDir = project.guessProjectDir()
        ?.findChild(SpeqaProjectScaffold.TEST_CASES_DIR)
        ?.takeIf { it.isDirectory } ?: return false
    return anyTcLeaf(tcDir)
}

internal fun anyTcLeaf(dir: VirtualFile): Boolean =
    dir.children.any { child ->
        if (child.isDirectory) anyTcLeaf(child) else isTestCaseFileName(child.name)
    }

/**
 * Core flow: collect candidates, show dialog, write the run file. [targetDir], when
 * given, is the selection-derived destination folder; otherwise the run is created in
 * the test-runs root. [candidateScopeDir], when given, limits the offered test cases to
 * that subtree (used by the TCs-tab folder context menu); otherwise all project test
 * cases are offered.
 */
internal fun openCreateTestRunDialog(
    project: Project,
    targetDir: VirtualFile? = null,
    candidateScopeDir: VirtualFile? = null,
) {
    val projectDir = project.guessProjectDir() ?: return
    val tcDir = projectDir.findChild(SpeqaProjectScaffold.TEST_CASES_DIR)
        ?.takeIf { it.isDirectory } ?: return

    val cache = TestCaseSummaryCache()
    val filesByKey = mutableMapOf<String, VirtualFile>()
    val candidates = mutableListOf<CandidateCase>()

    collectTcFiles(candidateScopeDir ?: tcDir) { file ->
        val summary = cache.summaryFor(file)
        filesByKey[file.path] = file
        candidates.add(
            CandidateCase(
                key = file.path,
                title = summary.title,
                facets = CaseFacets(
                    status = summary.status,
                    priority = summary.priority,
                    tags = summary.tags,
                    environments = summary.environments,
                ),
            ),
        )
    }
    if (candidates.isEmpty()) return

    val trDir = targetDir?.path
        ?: projectDir.findChild(SpeqaProjectScaffold.TEST_RUNS_DIR)?.path
        ?: projectDir.path
    val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
    val fileName = "Run-${timestamp.replace(":", "-").replace(" ", "-")}.tr.md"

    val dialog = CreateTestRunDialog(
        project = project,
        candidates = candidates,
        filesByKey = filesByKey,
        destinationRelativePath = trDir,
        fileName = fileName,
        timestamp = timestamp,
    )
    if (!dialog.showAndGet()) return

    val req = dialog.request
    CreateMultiCaseRunWriter.createMultiCaseRunFile(
        project = project,
        selectedFiles = req.selectedFiles,
        destinationRelativePath = req.destinationRelativePath,
        fileName = req.fileName,
        importOptions = req.importOptions,
        title = req.title,
    )
}

private fun collectTcFiles(dir: VirtualFile, into: (VirtualFile) -> Unit) {
    dir.children.forEach { child ->
        if (child.isDirectory) collectTcFiles(child, into)
        else if (isTestCaseFileName(child.name)) into(child)
    }
}
