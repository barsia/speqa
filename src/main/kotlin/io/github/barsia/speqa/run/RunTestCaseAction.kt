package io.github.barsia.speqa.run

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.vfs.VirtualFile
import io.github.barsia.speqa.editor.startTestRun

class RunTestCaseAction : AnAction(), DumbAware {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val testCaseFile = selectedTestCaseFile(e) ?: return
        startTestRun(project, testCaseFile)
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = TestRunSupport.isTestCaseFile(selectedTestCaseFile(e))
    }

    private fun selectedTestCaseFile(event: AnActionEvent): VirtualFile? {
        return event.getData(CommonDataKeys.VIRTUAL_FILE)
            ?: event.getData(CommonDataKeys.PSI_FILE)?.virtualFile
    }
}
