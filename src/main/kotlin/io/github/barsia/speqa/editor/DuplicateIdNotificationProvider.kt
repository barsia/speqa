package io.github.barsia.speqa.editor

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import io.github.barsia.speqa.SpeqaBundle
import io.github.barsia.speqa.actions.DuplicateIdResolution
import io.github.barsia.speqa.model.SpeqaDefaults
import io.github.barsia.speqa.registry.IdType
import io.github.barsia.speqa.registry.SpeqaIds
import java.util.function.Function
import javax.swing.JComponent

/**
 * Project-level banner shown on any open test-case / test-run file when the project
 * contains two or more duplicated IDs of that type. A single duplicated ID is already
 * handled by the per-file underline and its quick fix, so the banner stays out of the
 * way there; it appears only when batch resolution across the project is warranted, and
 * it stays visible while editing any test case (not just the duplicated ones), so the
 * problem is discoverable where the inline inspection is not.
 */
class DuplicateIdNotificationProvider : EditorNotificationProvider {

    override fun collectNotificationData(
        project: Project,
        file: VirtualFile,
    ): Function<in FileEditor, out JComponent?>? {
        val type = when {
            file.name.endsWith(".${SpeqaDefaults.TEST_CASE_EXTENSION}") -> IdType.TEST_CASE
            file.name.endsWith(".${SpeqaDefaults.TEST_RUN_EXTENSION}") -> IdType.TEST_RUN
            else -> return null
        }
        val duplicateGroups = SpeqaIds.allEntries(project, type)
            .groupingBy { it.second }.eachCount()
            .count { it.value > 1 }
        if (duplicateGroups < 1) return null

        return Function { _ ->
            EditorNotificationPanel(EditorNotificationPanel.Status.Warning).apply {
                text = SpeqaBundle.message("notification.duplicateIds")
                createActionLabel(SpeqaBundle.message("notification.duplicateId.review")) {
                    DuplicateIdResolution.reviewAndResolve(project)
                }
                createActionLabel(SpeqaBundle.message("notification.duplicateId.resolve")) {
                    DuplicateIdResolution.resolveDirectly(project)
                }
            }
        }
    }
}
