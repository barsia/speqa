package io.github.barsia.speqa.editor

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import io.github.barsia.speqa.model.Attachment
import io.github.barsia.speqa.model.SpeqaDefaults
import io.github.barsia.speqa.settings.SpeqaSettings
import java.io.IOException

object AttachmentSupport {

    fun resolveAttachmentsDir(project: Project, tcFile: VirtualFile): String {
        val folder = SpeqaSettings.getInstance(project).defaultAttachmentsFolder
        val tcName = tcFile.name.removeSuffix(".${SpeqaDefaults.TEST_CASE_EXTENSION}")
        return "$folder/$tcName"
    }

    fun resolveFile(project: Project, contextFile: VirtualFile, attachment: Attachment): VirtualFile? {
        @Suppress("UNUSED_PARAMETER")
        val unusedProject = project
        return contextFile.parent?.findFileByRelativePath(attachment.path)
    }

    fun copyFileToAttachments(
        project: Project,
        tcFile: VirtualFile,
        sourceFile: VirtualFile,
    ): Attachment? {
        val parent = tcFile.parent ?: return null
        val dirPath = resolveAttachmentsDir(project, tcFile)
        return try {
            val dir = VfsUtil.createDirectoryIfMissing(parent, dirPath) ?: return null
            val target = if (dir.findChild(sourceFile.name) != null) {
                val baseName = sourceFile.nameWithoutExtension
                val ext = sourceFile.extension?.let { ".$it" } ?: ""
                var counter = 1
                var candidate: String
                do {
                    candidate = "${baseName}_$counter$ext"
                    counter++
                } while (dir.findChild(candidate) != null)
                sourceFile.copy(null, dir, candidate)
            } else {
                sourceFile.copy(null, dir, sourceFile.name)
            }
            val relativePath = VfsUtilCore.getRelativePath(target, parent, '/')
                ?: return null
            Attachment(path = relativePath)
        } catch (_: IOException) {
            null
        }
    }

    fun deleteFile(project: Project, contextFile: VirtualFile, attachment: Attachment): Boolean {
        val file = resolveFile(project, contextFile, attachment) ?: return false
        return try {
            WriteCommandAction.runWriteCommandAction(project) {
                file.delete(AttachmentSupport)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    fun isImage(attachment: Attachment): Boolean {
        val ext = attachment.path.substringAfterLast('.', "").lowercase()
        return ext in IMAGE_EXTENSIONS
    }

    private val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "gif", "svg", "webp", "bmp", "ico")
}
