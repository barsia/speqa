package io.github.barsia.speqa.actions

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VfsUtil
import io.github.barsia.speqa.SpeqaBundle
import java.nio.charset.StandardCharsets

class InstallSkillAction : AnAction(), DumbAware {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val basePath = project.basePath ?: return

        val skillContent = javaClass.getResourceAsStream("/templates/test-case-writer-skill.md")
            ?.readBytes()
            ?.toString(StandardCharsets.UTF_8) ?: run {
            notify(project, SpeqaBundle.message("skill.add.failed", "Resource not found"), NotificationType.ERROR)
            return
        }

        val targetPath = "$basePath/.claude/skills/test-case-writer"
        val targetFile = "$targetPath/SKILL.md"

        val existingFile = VfsUtil.findFileByIoFile(java.io.File(targetFile), true)
        if (existingFile != null) {
            val result = Messages.showYesNoDialog(
                project,
                SpeqaBundle.message("skill.add.overwrite.message"),
                SpeqaBundle.message("skill.add.overwrite.title"),
                Messages.getQuestionIcon(),
            )
            if (result != Messages.YES) return
        }

        try {
            runWriteAction {
                val dir = VfsUtil.createDirectoryIfMissing(targetPath) ?: error("Cannot create directory")
                val file = dir.findChild("SKILL.md")?.also { VfsUtil.saveText(it, skillContent) }
                    ?: dir.createChildData(this, "SKILL.md").also { VfsUtil.saveText(it, skillContent) }
            }
            val relativePath = ".claude/skills/test-case-writer/SKILL.md"
            notify(project, SpeqaBundle.message("skill.add.success", relativePath), NotificationType.INFORMATION)
        } catch (ex: Exception) {
            notify(project, SpeqaBundle.message("skill.add.failed", ex.message ?: "Unknown error"), NotificationType.ERROR)
        }
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = e.project?.basePath != null
    }

    private fun notify(project: Project, content: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("SpeQA")
            .createNotification(content, type)
            .notify(project)
    }
}
