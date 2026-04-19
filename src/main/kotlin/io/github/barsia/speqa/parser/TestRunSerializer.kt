package io.github.barsia.speqa.parser

import io.github.barsia.speqa.model.Attachment
import io.github.barsia.speqa.model.DescriptionBlock
import io.github.barsia.speqa.model.PreconditionsBlock
import io.github.barsia.speqa.model.StepResult
import io.github.barsia.speqa.model.StepVerdict
import io.github.barsia.speqa.model.TestCaseBodyBlock
import io.github.barsia.speqa.model.TestRun
import java.time.format.DateTimeFormatter

object TestRunSerializer {
    private val formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    private val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "gif", "svg", "webp", "bmp", "ico")

    fun serialize(testRun: TestRun): String {
        return buildString {
            appendLine("---")
            testRun.id?.let { appendLine("id: $it") }
            appendLine("title: ${SpeqaMarkdown.quoteYamlScalar(testRun.title)}")
            testRun.priority?.let { appendLine("priority: ${it.label}") }
            testRun.startedAt?.let { appendLine("started_at: ${SpeqaMarkdown.quoteYamlScalar(formatter.format(it))}") }
            testRun.finishedAt?.let { appendLine("finished_at: ${SpeqaMarkdown.quoteYamlScalar(formatter.format(it))}") }
            appendLine("result: ${testRun.result.label}")
            if (testRun.manualResult) appendLine("manual_result: true")
            appendLine("environment: ${SpeqaMarkdown.quoteYamlScalar(testRun.environment)}")
            appendLine("runner: ${SpeqaMarkdown.quoteYamlScalar(testRun.runner)}")
            if (testRun.tags.isNotEmpty()) {
                appendLine("tags:")
                testRun.tags.forEach { tag -> appendLine("  - $tag") }
            }
            appendLine("---")
            appendLine()

            // Body blocks
            val orderedBlocks = testRun.bodyBlocks.sortedBy { block ->
                when (block) {
                    is DescriptionBlock -> 0
                    is PreconditionsBlock -> 1
                }
            }
            if (orderedBlocks.isNotEmpty()) {
                orderedBlocks.forEachIndexed { index, block ->
                    appendBodyBlock(block)
                    if (index != orderedBlocks.lastIndex || testRun.links.isNotEmpty() || testRun.attachments.isNotEmpty() || testRun.stepResults.isNotEmpty()) {
                        appendLine()
                    }
                }
            }

            // Links section
            if (testRun.links.isNotEmpty()) {
                appendLine("Links:")
                appendLine()
                testRun.links.forEach { link -> appendLine("[${link.title}](${link.url})") }
                if (testRun.attachments.isNotEmpty() || testRun.stepResults.isNotEmpty()) {
                    appendLine()
                }
            }

            // Attachments section
            if (testRun.attachments.isNotEmpty()) {
                appendLine("Attachments:")
                appendLine()
                testRun.attachments.forEach { appendAttachment(it) }
                if (testRun.stepResults.isNotEmpty()) {
                    appendLine()
                }
            }

            // Scenario
            if (testRun.stepResults.isNotEmpty()) {
                appendLine("Scenario:")
                appendLine()
                testRun.stepResults.forEachIndexed { index, step ->
                    appendStepResult(index + 1, step)
                    if (index != testRun.stepResults.lastIndex) {
                        appendLine()
                    }
                }
            }

            // Overall comment
            if (testRun.comment.isNotBlank()) {
                appendLine()
                testRun.comment.lines().forEach(::appendLine)
            }
        }.trimEnd() + "\n"
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
        if (step.expected.isNotBlank()) {
            val expectedLines = step.expected.lines()
            expectedLines.forEachIndexed { index, line ->
                appendLine("   > $line${if (index != expectedLines.lastIndex) "  " else ""}")
            }
        }
        (step.actionAttachments + step.expectedAttachments).forEach { att ->
            append("   ")
            appendAttachment(att)
        }
        if (!step.ticket.isNullOrBlank()) {
            appendLine()
            appendLine("   Ticket: ${step.ticket}")
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
