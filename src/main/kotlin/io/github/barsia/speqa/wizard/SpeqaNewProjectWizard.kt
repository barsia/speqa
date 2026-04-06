package io.github.barsia.speqa.wizard

import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.ide.wizard.GeneratorNewProjectWizard
import com.intellij.ide.wizard.NewProjectWizardBaseStep
import com.intellij.ide.wizard.NewProjectWizardChainStep.Companion.nextStep
import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.ide.wizard.RootNewProjectWizardStep
import com.intellij.ide.wizard.AbstractNewProjectWizardStep
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.startup.StartupManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import io.github.barsia.speqa.SpeqaBundle
import io.github.barsia.speqa.filetype.SpeqaIcons
import javax.swing.Icon

class SpeqaNewProjectWizard : GeneratorNewProjectWizard {
    override val id: String = "speqa"
    override val name: String = SpeqaBundle.message("wizard.projectName")
    override val icon: Icon = SpeqaIcons.PluginIcon

    override fun createStep(context: WizardContext): NewProjectWizardStep {
        return RootNewProjectWizardStep(context)
            .nextStep(::NewProjectWizardBaseStep)
            .nextStep(::SpeqaAssetsStep)
    }
}

class SpeqaAssetsStep(parent: NewProjectWizardStep) : AbstractNewProjectWizardStep(parent) {
    override fun setupProject(project: Project) {
        val sampleTestCase = runWriteAction<com.intellij.openapi.vfs.VirtualFile?> {
            val basePath = project.basePath ?: return@runWriteAction null
            val baseDir = VfsUtil.createDirectoryIfMissing(basePath) ?: return@runWriteAction null
            SpeqaProjectScaffold.generate(baseDir)
        }
        if (sampleTestCase != null) {
            openInitialTestCase(project, sampleTestCase)
        }
    }
}
