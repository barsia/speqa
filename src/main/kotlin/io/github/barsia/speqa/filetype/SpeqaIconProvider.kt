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

        return SpeqaIcons.forStatus(status)
    }

    private fun iconForTestRun(file: PsiFile): Icon = SpeqaIcons.forResult(parseRunResult(file.text))

    companion object {
        // Result labels can contain underscores (not_started, in_progress), so the capture group
        // must include them; [A-Za-z]+ would truncate at the underscore and never parse them.
        private val RESULT_REGEX = Regex("""^result:\s*([A-Za-z_]+)""", RegexOption.MULTILINE)

        /** Reads the run result from the `result:` frontmatter line, defaulting to NOT_STARTED. */
        fun parseRunResult(text: String): RunResult =
            RESULT_REGEX.find(text)
                ?.groupValues
                ?.getOrNull(1)
                ?.let(RunResult::fromString)
                ?: RunResult.NOT_STARTED
    }
}
