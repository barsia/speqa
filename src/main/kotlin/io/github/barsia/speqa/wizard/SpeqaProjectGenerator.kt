package io.github.barsia.speqa.wizard

import com.intellij.ide.util.projectWizard.SettingsStep
import com.intellij.ide.util.projectWizard.WebProjectTemplate
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.GeneratorPeerImpl
import com.intellij.platform.ProjectGeneratorPeer
import com.intellij.ui.components.JBCheckBox
import io.github.barsia.speqa.SpeqaBundle
import io.github.barsia.speqa.filetype.SpeqaIcons
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.BorderFactory
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JPanel

class SpeqaProjectSettings {
    var installSkill: Boolean = true
    var initGit: Boolean = true
}

class SpeqaProjectGenerator : WebProjectTemplate<SpeqaProjectSettings>() {

    override fun getName(): String = SpeqaBundle.message("wizard.projectName")

    override fun getDescription(): String = SpeqaBundle.message("wizard.projectDescription")

    override fun getIcon(): Icon = SpeqaIcons.PluginIcon

    override fun generateProject(
        project: Project,
        baseDir: VirtualFile,
        settings: SpeqaProjectSettings,
        module: Module,
    ) {
        val sampleTestCase = runWriteAction<VirtualFile?> {
            if (settings.installSkill) {
                SpeqaProjectScaffold.installSkill(baseDir)
            }
            SpeqaProjectScaffold.generate(baseDir)
        } ?: return
        if (settings.initGit) {
            initializeGitRepository(project, baseDir)
        }
        openInitialTestCase(project, sampleTestCase)
    }

    private fun initializeGitRepository(project: Project, baseDir: VirtualFile) {
        if (baseDir.findChild(".git") != null) return
        // git4idea.commands.Git.runCommand asserts it is not on EDT (HTTP auth
        // helper waits for the built-in server; OSProcessHandler forbids
        // synchronous waits on the UI thread). generateProject runs on EDT, so
        // the whole init sequence is dispatched to a pooled thread, then the
        // VCS mapping + deferred staging are scheduled back on EDT.
        com.intellij.openapi.application.ApplicationManager.getApplication()
            .executeOnPooledThread {
                try {
                    ensureGitignore(java.io.File(baseDir.path))
                    val initHandler = git4idea.commands.GitLineHandler(
                        project, baseDir, git4idea.commands.GitCommand.INIT,
                    )
                    val initResult = git4idea.commands.Git.getInstance().runCommand(initHandler)
                    if (!initResult.success()) return@executeOnPooledThread
                    com.intellij.openapi.application.ApplicationManager.getApplication()
                        .invokeLater({
                            if (project.isDisposed) return@invokeLater
                            baseDir.refresh(false, true)
                            registerVcsMapping(project, baseDir)
                            stageAfterProjectReady(project, baseDir)
                        }, com.intellij.openapi.application.ModalityState.nonModal(), project.disposed)
                } catch (_: Throwable) {
                    // git4idea unavailable or init failed — skip silently, user can init manually
                }
            }
    }

    private fun stageAfterProjectReady(project: Project, baseDir: VirtualFile) {
        // Use DumbService.runWhenSmart instead of StartupManager.runAfterOpened:
        // runAfterOpened is @ApiStatus.Internal and trips Plugin Verifier.
        // runWhenSmart is public and fires after indexing finishes — at that
        // point the IDE has created `.idea/` with all shareable settings, so
        // `git add -A` picks up the full initial snapshot.
        com.intellij.openapi.project.DumbService.getInstance(project).runWhenSmart {
            com.intellij.openapi.application.ApplicationManager.getApplication()
                .executeOnPooledThread {
                    try {
                        runGitAdd(project, baseDir, listOf("-A"))
                        git4idea.GitUtil.getRepositoryManager(project)
                            .getRepositoryForRoot(baseDir)
                            ?.update()
                        com.intellij.openapi.vcs.changes.VcsDirtyScopeManager
                            .getInstance(project)
                            .markEverythingDirty()
                    } catch (_: Throwable) {
                        // git add failed post-open — ignore, scaffold still exists on disk
                    }
                }
        }
    }

    private fun runGitAdd(project: Project, root: VirtualFile, params: List<String>) {
        val handler = git4idea.commands.GitLineHandler(project, root, git4idea.commands.GitCommand.ADD)
        handler.addParameters(params)
        git4idea.commands.Git.getInstance().runCommand(handler)
    }

    private fun registerVcsMapping(project: Project, baseDir: VirtualFile) {
        val vcsManager = com.intellij.openapi.vcs.ProjectLevelVcsManager.getInstance(project)
        val existing = vcsManager.directoryMappings
        if (existing.any { it.directory == baseDir.path && it.vcs == "Git" }) return
        val mapping = com.intellij.openapi.vcs.VcsDirectoryMapping(baseDir.path, "Git")
        vcsManager.directoryMappings = existing + mapping
    }

    private fun ensureGitignore(dir: java.io.File) {
        val gitignore = java.io.File(dir, ".gitignore")
        if (gitignore.exists()) return
        gitignore.writeText(
            """
                # IDE
                .idea

                # OS
                .DS_Store
                Thumbs.db

                # Secrets and credentials
                .env
                .env.*
                *.pem
                *.key
                *.p12
                *.jks
                local.properties

                # Logs
                *.log
            """.trimIndent() + "\n",
        )
    }

    override fun createPeer(): ProjectGeneratorPeer<SpeqaProjectSettings> = SpeqaProjectGeneratorPeer()
}

private class SpeqaProjectGeneratorPeer : GeneratorPeerImpl<SpeqaProjectSettings>() {

    private val settings = SpeqaProjectSettings()

    private val installSkillCheckBox = JBCheckBox(
        SpeqaBundle.message("action.Speqa.InstallSkill.text"),
        true,
    )

    private val initGitCheckBox = JBCheckBox(
        SpeqaBundle.message("wizard.initGit"),
        true,
    )

    override fun getSettings(): SpeqaProjectSettings {
        settings.installSkill = installSkillCheckBox.isSelected
        settings.initGit = initGitCheckBox.isSelected
        return settings
    }

    override fun getComponent(): JComponent {
        val panel = JPanel(GridBagLayout()).apply {
            border = BorderFactory.createEmptyBorder(6, 0, 6, 0)
        }
        val gbc = GridBagConstraints().apply {
            gridx = 0
            gridy = 0
            anchor = GridBagConstraints.WEST
            fill = GridBagConstraints.HORIZONTAL
            weightx = 1.0
        }
        panel.add(initGitCheckBox, gbc)
        gbc.gridy = 1
        panel.add(installSkillCheckBox, gbc)
        return panel
    }

    override fun buildUI(settingsStep: SettingsStep) {
        settingsStep.addSettingsComponent(initGitCheckBox)
        settingsStep.addSettingsComponent(installSkillCheckBox)
    }

    override fun isBackgroundJobRunning(): Boolean = false
}
