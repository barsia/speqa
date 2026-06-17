package io.github.barsia.speqa.editor

import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotifications
import com.intellij.util.Alarm
import io.github.barsia.speqa.model.SpeqaDefaults

class DuplicateIdBannerRefresher : ProjectActivity {
    override suspend fun execute(project: Project) {
        val alarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, project)
        EditorFactory.getInstance().eventMulticaster.addDocumentListener(
            object : DocumentListener {
                override fun documentChanged(event: DocumentEvent) {
                    val file: VirtualFile = FileDocumentManager.getInstance().getFile(event.document) ?: return
                    if (!isSpeqaFile(file)) return
                    alarm.cancelAllRequests()
                    alarm.addRequest({
                        if (!project.isDisposed) EditorNotifications.getInstance(project).updateAllNotifications()
                    }, 300)
                }
            },
            project,
        )
    }

    private fun isSpeqaFile(file: VirtualFile): Boolean =
        file.name.endsWith(".${SpeqaDefaults.TEST_CASE_EXTENSION}") ||
            file.name.endsWith(".${SpeqaDefaults.TEST_RUN_EXTENSION}")
}
