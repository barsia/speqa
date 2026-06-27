package io.github.barsia.speqa.parser

import io.github.barsia.speqa.model.Attachment
import io.github.barsia.speqa.model.DescriptionBlock
import io.github.barsia.speqa.model.Link
import io.github.barsia.speqa.model.PreconditionsBlock
import io.github.barsia.speqa.model.RunCase
import io.github.barsia.speqa.model.RunResult
import io.github.barsia.speqa.model.StepResult
import io.github.barsia.speqa.model.StepVerdict
import io.github.barsia.speqa.model.TestCaseBodyBlock
import io.github.barsia.speqa.model.TestRun
import java.time.format.DateTimeFormatter

object TestRunSerializer {
    private val formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    private val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "gif", "svg", "webp", "bmp", "ico")

    fun serialize(testRun: TestRun): String = buildString {
        appendRunFrontmatter(testRun)
        testRun.cases.forEach { appendCaseSection(it) }
        // Run-level overall comment for the sectioned format is intentionally deferred (later plan);
        // the sectioned parser does not read it, so serialize and parse stay symmetric here.
    }.trimEnd() + "\n"

    private fun StringBuilder.appendRunFrontmatter(testRun: TestRun) {
        appendLine("---")
        testRun.id?.let { appendLine("id: $it") }
        appendLine("title: ${SpeqaMarkdown.quoteYamlScalar(testRun.title)}")
        testRun.startedAt?.let { appendLine("started_at: ${SpeqaMarkdown.quoteYamlScalar(formatter.format(it))}") }
        testRun.finishedAt?.let { appendLine("finished_at: ${SpeqaMarkdown.quoteYamlScalar(formatter.format(it))}") }
        appendLine("result: ${testRun.result.label}")
        if (testRun.manualResult) appendLine("manual_result: true")
        appendLine("runner: ${SpeqaMarkdown.quoteYamlScalar(testRun.runner)}")
        appendLine("---")
    }

    private fun StringBuilder.appendCaseSection(case: RunCase) {
        appendLine()
        append("Test Case: TC-").append(case.caseId)
        if (case.title.isNotBlank()) append(' ').append(case.title)
        appendLine()
        case.priority?.let { appendLine("priority: ${it.label}") }
        if (case.tags.isNotEmpty()) appendLine("tags: ${case.tags.joinToString(", ")}")
        if (case.environment.isNotEmpty()) appendLine("environment: ${case.environment.joinToString(", ")}")
        if (case.manualResult) appendLine("manual_result: true")
        appendCaseBodyBlocks(case.bodyBlocks)
        appendCaseLinks(case.links)
        appendCaseAttachments(case.attachments)
        appendCaseScenario(case.stepResults)
        appendCaseResult(case.result)
    }

    private fun StringBuilder.appendCaseBodyBlocks(blocks: List<TestCaseBodyBlock>) {
        if (blocks.isEmpty()) return
        val orderedBlocks = blocks.sortedBy { block ->
            when (block) {
                is DescriptionBlock -> 0
                is PreconditionsBlock -> 1
            }
        }
        appendLine()
        orderedBlocks.forEachIndexed { index, block ->
            appendBodyBlock(block)
            if (index != orderedBlocks.lastIndex) appendLine()
        }
    }

    private fun StringBuilder.appendCaseLinks(links: List<Link>) {
        if (links.isEmpty()) return
        appendLine()
        appendLine("Links:")
        appendLine()
        links.forEach { link -> appendLine("[${link.title}](${link.url})") }
    }

    private fun StringBuilder.appendCaseAttachments(attachments: List<Attachment>) {
        if (attachments.isEmpty()) return
        appendLine()
        appendLine("Attachments:")
        appendLine()
        attachments.forEach { appendAttachment(it) }
    }

    private fun StringBuilder.appendCaseScenario(stepResults: List<StepResult>) {
        if (stepResults.isEmpty()) return
        appendLine()
        appendLine("Scenario:")
        appendLine()
        stepResults.forEachIndexed { index, step ->
            appendStepResult(index + 1, step)
            if (index != stepResults.lastIndex) appendLine()
        }
    }

    private fun StringBuilder.appendCaseResult(result: RunResult) {
        if (result == RunResult.NOT_STARTED) return
        appendLine()
        appendLine("Result: ${result.label}")
    }

    private fun StringBuilder.appendBodyBlock(block: TestCaseBodyBlock) {
        when (block) {
            is DescriptionBlock -> {
                if (block.markdown.isNotBlank()) {
                    block.markdown.lines().forEach(::appendLine)
                }
            }
            is PreconditionsBlock -> {
                appendLine(block.markerStyle.marker)
                if (block.markdown.isNotBlank()) {
                    appendLine()
                    block.markdown.lines().forEach(::appendLine)
                }
            }
        }
    }

    private fun StringBuilder.appendAttachment(attachment: Attachment) {
        val fileName = attachment.path.substringAfterLast('/')
        val encodedPath = attachment.path.replace(" ", "%20")
        val ext = attachment.path.substringAfterLast('.', "").lowercase()
        if (ext in IMAGE_EXTENSIONS) {
            appendLine("![$fileName]($encodedPath)")
        } else {
            appendLine("[$fileName]($encodedPath)")
        }
    }

    private fun StringBuilder.appendStepResult(number: Int, step: StepResult) {
        val actionLines = step.action.lines()
        appendLine("$number. ${actionLines.firstOrNull().orEmpty()}${if (actionLines.size > 1) "  " else ""}")
        actionLines.drop(1).forEachIndexed { index, line ->
            appendLine("   $line${if (index != actionLines.lastIndex - 1) "  " else ""}")
        }
        val hasExpectedBlock = step.expected.isNotBlank()
        if (hasExpectedBlock) {
            if (step.expected.isBlank()) {
                appendLine("   >")
            } else {
                val expectedLines = step.expected.lines()
                expectedLines.forEachIndexed { index, line ->
                    appendLine("   > $line${if (index != expectedLines.lastIndex) "  " else ""}")
                }
            }
        }
        step.attachments.forEach { att ->
            append("   ")
            appendAttachment(att)
        }
        if (step.tickets.isNotEmpty()) {
            appendLine()
            appendLine("   Ticket: ${step.tickets.joinToString(", ")}")
        }
        if (step.links.isNotEmpty()) {
            val rendered = step.links.joinToString(", ") { link ->
                if (link.title.isBlank()) "[](${link.url})" else "[${link.title}](${link.url})"
            }
            appendLine("   Links: $rendered")
        }
        if (step.verdict != StepVerdict.NONE) {
            appendLine("   - ${step.verdict.label}")
        }
        if (step.comment.isNotBlank()) {
            appendLine()
            appendLine("   Comment:")
            val commentLines = step.comment.lines()
            commentLines.forEachIndexed { index, line ->
                appendLine("   $line${if (index != commentLines.lastIndex) "  " else ""}")
            }
        }
    }
}
