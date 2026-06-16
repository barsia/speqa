package io.github.barsia.speqa

import com.intellij.ide.plugins.DynamicPluginListener
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.VirtualFile
import io.github.barsia.speqa.editor.SpeqaSplitEditor
import io.github.barsia.speqa.model.SpeqaDefaults
import io.github.barsia.speqa.run.TestRunSplitEditor
import io.github.barsia.speqa.run.TestRunSupport

/**
 * Reopens already-open SpeQA files when the plugin is installed/enabled at runtime.
 *
 * When the plugin loads dynamically (no IDE restart), files that were already open stay bound to
 * the editor that handled them before our [com.intellij.openapi.fileEditor.FileEditorProvider]s
 * existed (e.g. the platform Markdown editor, which renders its preview via JCEF). The platform
 * does not re-evaluate open editors on dynamic load, so those tabs never switch to the SpeQA split
 * editor and the plugin appears non-functional until restart. This listener reopens any such tab
 * once our plugin finishes loading.
 *
 * The plugin's own listener receives [pluginLoaded] for its own load: `DynamicPlugins` flushes the
 * message-bus publisher cache after registering the loading plugin's listeners and before
 * broadcasting the event.
 */
class SpeqaDynamicEditorReopener : DynamicPluginListener {
    override fun pluginLoaded(pluginDescriptor: IdeaPluginDescriptor) {
        if (pluginDescriptor.pluginId.idString != PLUGIN_ID) return
        ApplicationManager.getApplication().invokeLater {
            for (project in ProjectManager.getInstance().openProjects) {
                if (project.isDisposed) continue
                val fileEditorManager = FileEditorManager.getInstance(project)
                val stale = fileEditorManager.openFiles.filter {
                    isSpeqaFile(it) && !hasSpeqaEditor(fileEditorManager, it)
                }
                for (file in stale) {
                    fileEditorManager.closeFile(file)
                    fileEditorManager.openFile(file, true)
                }
            }
        }
    }

    private fun isSpeqaFile(file: VirtualFile): Boolean =
        file.name.endsWith(".${SpeqaDefaults.TEST_CASE_EXTENSION}") || TestRunSupport.isTestRunFile(file)

    private fun hasSpeqaEditor(manager: FileEditorManager, file: VirtualFile): Boolean =
        manager.getEditors(file).any { it is SpeqaSplitEditor || it is TestRunSplitEditor }

    private companion object {
        const val PLUGIN_ID = "io.github.barsia.speqa"
    }
}
