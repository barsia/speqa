package io.github.barsia.speqa.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbAware
import io.github.barsia.speqa.model.SpeqaDefaults
import io.github.barsia.speqa.settings.SpeqaSettings

class ToggleScrollSyncAction : ToggleAction(), DumbAware {

    override fun isSelected(e: AnActionEvent): Boolean {
        val project = e.project ?: return true
        return SpeqaSettings.getInstance(project).scrollSyncEnabled
    }

    override fun setSelected(e: AnActionEvent, state: Boolean) {
        val project = e.project ?: return
        SpeqaSettings.getInstance(project).scrollSyncEnabled = state
    }

    override fun update(e: AnActionEvent) {
        super.update(e)
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
            ?: e.project?.let { FileEditorManager.getInstance(it).selectedEditor?.file }
        e.presentation.isVisible = file != null && isSpeqaFile(file.name)
        e.presentation.isEnabled = e.presentation.isVisible
    }

    override fun getActionUpdateThread() = ActionUpdateThread.EDT

    private fun isSpeqaFile(name: String): Boolean =
        name.endsWith(".${SpeqaDefaults.TEST_CASE_EXTENSION}") ||
            name.endsWith(".${SpeqaDefaults.TEST_RUN_EXTENSION}")
}
