package io.github.barsia.speqa.registry

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vfs.VirtualFileManager
import io.github.barsia.speqa.editor.AttachmentRefactoringListener

class SpeqaIdRegistryStartup : ProjectActivity {
    override suspend fun execute(project: Project) {
        SpeqaIdRegistry.getInstance(project).ensureInitialized()
        project.messageBus.connect().subscribe(
            VirtualFileManager.VFS_CHANGES,
            AttachmentRefactoringListener(project),
        )
    }
}
