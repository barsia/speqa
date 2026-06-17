package io.github.barsia.speqa.actions

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import io.github.barsia.speqa.SpeqaBundle
import io.github.barsia.speqa.editor.resolveTestCaseCreatedEpochMillis
import io.github.barsia.speqa.registry.IdRenumber
import io.github.barsia.speqa.registry.IdType
import io.github.barsia.speqa.registry.SpeqaIds
import io.github.barsia.speqa.registry.TestCaseIdEntry
import io.github.barsia.speqa.registry.computeDuplicateIdRenumberPlan

object DuplicateIdResolution {

    private val ID_LINE_REGEX = Regex("""(?m)^id:\s*\d+\s*$""")

    fun reviewAndResolve(project: Project) {
        val plan = computePlan(project) ?: return
        if (plan.isEmpty()) {
            showNone(project)
            return
        }
        if (!ResolveDuplicateIdsDialog(project, plan).showAndGet()) return
        applyPlan(project, plan)
    }

    fun resolveDirectly(project: Project) {
        val plan = computePlan(project) ?: return
        if (plan.isEmpty()) {
            showNone(project)
            return
        }
        applyPlan(project, plan)
    }

    private fun computePlan(project: Project): List<IdRenumber>? {
        var plan: List<IdRenumber> = emptyList()
        val completed = ProgressManager.getInstance().runProcessWithProgressSynchronously(
            Runnable {
                val entries = runReadAction<List<Pair<VirtualFile, Int>>> {
                    SpeqaIds.allEntries(project, IdType.TEST_CASE)
                }
                val duplicateIds = entries.groupingBy { it.second }.eachCount()
                    .filterValues { it > 1 }.keys
                val tcEntries = entries.map { (file, id) ->
                    val created = if (id in duplicateIds) {
                        resolveTestCaseCreatedEpochMillis(project, file)
                    } else {
                        null
                    }
                    TestCaseIdEntry(file.path, id, created)
                }
                plan = computeDuplicateIdRenumberPlan(tcEntries)
            },
            SpeqaBundle.message("resolveDuplicateIds.progress"),
            true,
            project,
        )
        return if (completed) plan else null
    }

    private fun applyPlan(project: Project, plan: List<IdRenumber>) {
        WriteCommandAction.runWriteCommandAction(
            project,
            SpeqaBundle.message("resolveDuplicateIds.dialog.title"),
            null,
            Runnable {
                val fileDocumentManager = FileDocumentManager.getInstance()
                for (renumber in plan) {
                    val file = LocalFileSystem.getInstance().findFileByPath(renumber.path) ?: continue
                    val document = fileDocumentManager.getDocument(file) ?: continue
                    val match = ID_LINE_REGEX.find(document.text) ?: continue
                    document.replaceString(match.range.first, match.range.last + 1, "id: ${renumber.newId}")
                }
            },
        )
        FileDocumentManager.getInstance().saveAllDocuments()
    }

    private fun showNone(project: Project) {
        Messages.showInfoMessage(
            project,
            SpeqaBundle.message("resolveDuplicateIds.none.message"),
            SpeqaBundle.message("resolveDuplicateIds.none.title"),
        )
    }
}
