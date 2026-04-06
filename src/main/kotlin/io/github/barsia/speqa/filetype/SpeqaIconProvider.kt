package io.github.barsia.speqa.filetype

import com.intellij.ide.IconProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import io.github.barsia.speqa.model.SpeqaDefaults
import io.github.barsia.speqa.model.RunResult
import io.github.barsia.speqa.model.Status
import javax.swing.Icon

class SpeqaIconProvider : IconProvider() {
    override fun getIcon(element: PsiElement, flags: Int): Icon? {
        val file = element.containingFile ?: return null
        val name = file.virtualFile?.name ?: return null

        return when {
            name.endsWith(".${SpeqaDefaults.TEST_CASE_EXTENSION}") -> iconForTestCase(file)
            name.endsWith(".${SpeqaDefaults.TEST_RUN_EXTENSION}") -> iconForTestRun(file)
            else -> null
        }
    }

    private fun iconForTestCase(file: PsiFile): Icon {
        val status = Regex("""^status:\s*([A-Za-z]+)""", RegexOption.MULTILINE)
            .find(file.text)
            ?.groupValues
            ?.getOrNull(1)
            ?.let(Status::fromString)
            ?: Status.DRAFT

        return when (status) {
            Status.DRAFT -> SpeqaIcons.TestCaseDraft
            Status.READY -> SpeqaIcons.TestCaseReady
            Status.DEPRECATED -> SpeqaIcons.TestCaseDeprecated
        }
    }

    private fun iconForTestRun(file: PsiFile): Icon {
        val result = Regex("""^result:\s*([A-Za-z]+)""", RegexOption.MULTILINE)
            .find(file.text)
            ?.groupValues
            ?.getOrNull(1)
            ?.let(RunResult::fromString)
            ?: RunResult.NOT_STARTED

        return when (result) {
            RunResult.NOT_STARTED -> SpeqaIcons.TestRunBlocked
            RunResult.IN_PROGRESS -> SpeqaIcons.TestRunBlocked
            RunResult.PASSED -> SpeqaIcons.TestRunPassed
            RunResult.FAILED -> SpeqaIcons.TestRunFailed
            RunResult.BLOCKED -> SpeqaIcons.TestRunBlocked
        }
    }
}
