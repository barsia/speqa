package io.github.barsia.speqa.editor

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vfs.VirtualFileManager

/**
 * Subscribes the attachment-rename refactoring listener for the project. This wiring
 * previously lived in the id-registry startup activity; the id registry was replaced by
 * a FileBasedIndex (which self-builds, needing no startup), but the attachment listener
 * still needs an explicit subscription, so it has its own startup activity here.
 */
class AttachmentRefactoringStartup : ProjectActivity {
    override suspend fun execute(project: Project) {
        project.messageBus.connect().subscribe(
            VirtualFileManager.VFS_CHANGES,
            AttachmentRefactoringListener(project),
        )
    }
}
