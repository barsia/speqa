package io.github.barsia.speqa.editor

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent
import io.github.barsia.speqa.model.SpeqaDefaults
import io.github.barsia.speqa.settings.SpeqaSettings

class AttachmentRefactoringListener(private val project: Project) : BulkFileListener {
    override fun after(events: List<VFileEvent>) {
        for (event in events) {
            if (event !is VFilePropertyChangeEvent) continue
            if (event.propertyName != "name") continue
            val file = event.file
            val oldName = event.oldValue as? String ?: continue
            val newName = event.newValue as? String ?: continue
            if (!oldName.endsWith(".${SpeqaDefaults.TEST_CASE_EXTENSION}")) continue

            val folder = SpeqaSettings.getInstance(project).defaultAttachmentsFolder
            val oldStem = oldName.removeSuffix(".${SpeqaDefaults.TEST_CASE_EXTENSION}")
            val newStem = newName.removeSuffix(".${SpeqaDefaults.TEST_CASE_EXTENSION}")
            if (oldStem == newStem) continue

            val parent = file.parent ?: continue
            val oldDir = parent.findFileByRelativePath("$folder/$oldStem")

            // Rename attachments folder
            if (oldDir != null && oldDir.isDirectory) {
                runWriteAction { oldDir.rename(this, newStem) }
            }

            // Update paths in the markdown file
            val document = FileDocumentManager.getInstance().getDocument(file) ?: continue
            val oldPrefix = "$folder/$oldStem/"
            val newPrefix = "$folder/$newStem/"
            val oldText = document.text
            val newText = oldText.replace(oldPrefix, newPrefix)
            if (newText != oldText) {
                CommandProcessor.getInstance().executeCommand(project, {
                    runWriteAction { document.setText(newText) }
                }, "Speqa: Update attachment paths", null)
            }
        }
    }
}
