package io.github.barsia.speqa.validation

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import io.github.barsia.speqa.SpeqaBundle
import io.github.barsia.speqa.model.SpeqaDefaults
import io.github.barsia.speqa.model.Status
import io.github.barsia.speqa.parser.TestCaseParser
import io.github.barsia.speqa.registry.IdType
import io.github.barsia.speqa.registry.SpeqaIdRegistry

class SpeqaAnnotator : Annotator {

    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        val file = element as? PsiFile ?: return
        if (!file.name.endsWith(".${SpeqaDefaults.TEST_CASE_EXTENSION}")) return
        // Skip injected language fragments (e.g. YAML inside Markdown frontmatter)
        if (InjectedLanguageManager.getInstance(file.project).isInjectedFragment(file)) return

        val text = file.text
        val testCase = try {
            TestCaseParser.parse(text)
        } catch (_: IllegalArgumentException) {
            return
        }

        val len = text.length

        // Warning: title not set
        if (testCase.title.isBlank() || testCase.title == SpeqaBundle.message("label.untitledTestCase")) {
            val titleRange = findFrontmatterValueRange(text, "title")
            if (titleRange != null) {
                holder.warn(SpeqaBundle.message("annotator.titleNotSet"), titleRange, len)
            }
        }

        // Warning: duplicate ID
        testCase.id?.let { id ->
            val registry = SpeqaIdRegistry.getInstance(file.project)
            registry.ensureInitialized()
            if (registry.idSet(IdType.TEST_CASE).isDuplicate(id)) {
                val idRange = findFrontmatterValueRange(text, "id")
                if (idRange != null) {
                    val safeRange = TextRange(idRange.startOffset, idRange.endOffset.coerceAtMost(len))
                    if (!safeRange.isEmpty && safeRange.startOffset < len) {
                        holder.newAnnotation(
                            HighlightSeverity.WARNING,
                            SpeqaBundle.message("annotator.duplicateTestCaseId", id),
                        )
                            .range(safeRange)
                            .withFix(AssignNextFreeIdFix(IdType.TEST_CASE))
                            .create()
                    }
                }
            }
        }

        // Warning: preconditions in frontmatter
        val preconditionsFmRange = findFrontmatterKeyRange(text, "preconditions")
        if (preconditionsFmRange != null) {
            holder.warn(SpeqaBundle.message("annotator.preconditionsInFrontmatter"), preconditionsFmRange, len)
        }

        // Warning: ## Preconditions heading
        val headingMatch = Regex("(?m)^##\\s+Preconditions\\s*$").find(text)
        if (headingMatch != null) {
            holder.warn(SpeqaBundle.message("annotator.useBlocksNotHeadings"), headingMatch.range.toTextRange(), len)
        }

        // Warning: no steps
        if (testCase.steps.isEmpty()) {
            val bodyStart = findBodyStart(text)
            if (bodyStart != null && bodyStart < len) {
                val lineEnd = text.indexOf('\n', bodyStart).takeIf { it >= 0 } ?: len
                holder.warn(SpeqaBundle.message("annotator.noSteps"), TextRange(bodyStart, maxOf(lineEnd, bodyStart + 1)), len)
            }
        }

        // Warning: step with empty action
        testCase.steps.forEachIndexed { index, step ->
            if (step.action.isBlank()) {
                val stepRange = findStepRange(text, index)
                if (stepRange != null) {
                    holder.warn(SpeqaBundle.message("annotator.stepMissingAction", index + 1), stepRange, len)
                }
            }
        }

        // Warning: no expected results
        if (testCase.steps.isNotEmpty() && testCase.steps.none { !it.expected.isNullOrBlank() }) {
            val blockquoteRange = findFirstBlockquoteRange(text)
            if (blockquoteRange != null) {
                holder.warn(SpeqaBundle.message("annotator.noExpectedResults"), blockquoteRange, len)
            } else {
                val lastStepRange = findStepRange(text, testCase.steps.lastIndex)
                if (lastStepRange != null) {
                    holder.warn(SpeqaBundle.message("annotator.noExpectedResults"), lastStepRange, len)
                }
            }
        }

        // Warning: ready but incomplete
        if (testCase.status == Status.READY) {
            val incomplete = testCase.title.isBlank() ||
                testCase.title == SpeqaBundle.message("label.untitledTestCase") ||
                testCase.steps.isEmpty()
            if (incomplete) {
                val statusRange = findFrontmatterValueRange(text, "status")
                if (statusRange != null) {
                    holder.warn(SpeqaBundle.message("annotator.readyButIncomplete"), statusRange, len)
                }
            }
        }
    }

    private fun AnnotationHolder.warn(message: String, range: TextRange, textLength: Int = Int.MAX_VALUE) {
        if (range.startOffset >= textLength) return
        val safe = TextRange(range.startOffset, range.endOffset.coerceAtMost(textLength))
        if (safe.isEmpty) return
        newAnnotation(HighlightSeverity.WARNING, message)
            .range(safe)
            .create()
    }

    private fun findFrontmatterValueRange(text: String, key: String): TextRange? {
        val match = Regex("(?m)^$key:\\s*(.*)$").find(text) ?: return null
        val group = match.groups[1] ?: return null
        return if (group.range.isEmpty()) {
            // Key present but value empty — underline the key
            TextRange(match.range.first, match.range.last + 1)
        } else {
            TextRange(group.range.first, group.range.last + 1)
        }
    }

    private fun findFrontmatterKeyRange(text: String, key: String): TextRange? {
        val match = Regex("(?m)^$key\\s*:").find(text) ?: return null
        return TextRange(match.range.first, match.range.last + 1)
    }

    private fun findBodyStart(text: String): Int? {
        val closingDelimiter = Regex("(?m)^---\\s*$").findAll(text).drop(1).firstOrNull()
            ?: return null
        val afterDelimiter = closingDelimiter.range.last + 1
        return if (afterDelimiter < text.length) afterDelimiter else null
    }

    private fun findStepsMarkerEnd(text: String): Int? {
        val match = Regex("(?m)^[Ss]teps:\\s*$").find(text) ?: return null
        return match.range.last + 1
    }

    private fun findFirstBlockquoteRange(text: String): TextRange? {
        val stepsStart = findStepsMarkerEnd(text) ?: return null
        val match = Regex("(?m)^(\\s*>\\s?)(.*)$").find(text, stepsStart) ?: return null
        val contentGroup = match.groups[2]!!
        val prefixEnd = match.groups[1]!!.range.last + 1
        return if (contentGroup.range.isEmpty()) {
            TextRange(prefixEnd, prefixEnd + 1)
        } else {
            TextRange(prefixEnd, contentGroup.range.last + 1)
        }
    }

    private fun findStepRange(text: String, stepIndex: Int): TextRange? {
        val stepsStart = findStepsMarkerEnd(text) ?: return null
        var found = 0
        for (match in Regex("(?m)^(\\d+\\.\\s?)(.*)$").findAll(text, stepsStart)) {
            if (found == stepIndex) {
                val contentGroup = match.groups[2]!!
                val prefix = match.groups[1]!!
                return if (contentGroup.range.isEmpty()) {
                    TextRange(prefix.range.first, prefix.range.last + 1)
                } else {
                    TextRange(contentGroup.range.first, contentGroup.range.last + 1)
                }
            }
            found++
        }
        return null
    }

    private fun IntRange.toTextRange(): TextRange = TextRange(first, last + 1)
}
