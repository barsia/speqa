package io.github.barsia.speqa.parser

import io.github.barsia.speqa.model.Attachment
import io.github.barsia.speqa.model.DescriptionBlock
import io.github.barsia.speqa.model.Link
import io.github.barsia.speqa.model.PreconditionsBlock
import io.github.barsia.speqa.model.TestCase
import io.github.barsia.speqa.model.TestCaseBodyBlock
import io.github.barsia.speqa.model.TestStep

object TestCaseSerializer {
    fun serialize(testCase: TestCase): String {
        return buildString {
            appendLine("---")
            testCase.id?.let { appendLine("id: $it") }
            appendLine("title: ${SpeqaMarkdown.quoteYamlScalar(testCase.title)}")
            testCase.priority?.let { appendLine("priority: ${it.label}") }
            testCase.status?.let { appendLine("status: ${it.label}") }
            testCase.environment?.let { appendListField("environment", it) }
            testCase.tags?.let { appendListField("tags", it) }
            appendLine("---")
            appendLine()
            val orderedBlocks = testCase.bodyBlocks.sortedBy { block ->
                when (block) {
                    is DescriptionBlock -> 0
                    is PreconditionsBlock -> 1
                }
            }
            if (orderedBlocks.isNotEmpty()) {
                orderedBlocks.forEachIndexed { index, block ->
                    appendBodyBlock(block)
                    if (index != orderedBlocks.lastIndex || testCase.attachments.isNotEmpty() || testCase.links.isNotEmpty() || testCase.steps.isNotEmpty()) {
                        appendLine()
                    }
                }
            }
            if (testCase.attachments.isNotEmpty()) {
                appendLine("Attachments:")
                appendLine()
                testCase.attachments.forEach { appendAttachment(it) }
                if (testCase.links.isNotEmpty() || testCase.steps.isNotEmpty()) {
                    appendLine()
                }
            }
            if (testCase.links.isNotEmpty()) {
                appendLine("Links:")
                appendLine()
                testCase.links.forEach { link -> appendLink(link) }
                if (testCase.steps.isNotEmpty()) {
                    appendLine()
                }
            }
            if (testCase.steps.isNotEmpty()) {
                appendLine("Scenario:")
                appendLine()
                testCase.steps.forEachIndexed { index, step ->
                    appendStep(index + 1, step)
                    if (index != testCase.steps.lastIndex) {
                        appendLine()
                    }
                }
            }
        }.trimEnd() + "\n"
    }

    private fun StringBuilder.appendAttachment(attachment: Attachment, indent: String = "") {
        if (isImagePath(attachment.path)) {
            val fileName = attachment.path.substringAfterLast('/')
            appendLine("$indent![$fileName](${attachment.path})")
        } else {
            appendLine("$indent[${attachment.path}]")
        }
    }

    private val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "gif", "svg", "webp", "bmp", "ico")

    private fun isImagePath(path: String): Boolean {
        val ext = path.substringAfterLast('.', "").lowercase()
        return ext in IMAGE_EXTENSIONS
    }

    private fun StringBuilder.appendLink(link: Link) {
        appendLine("[${link.title}](${link.url})")
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

    private fun StringBuilder.appendListField(name: String, values: List<String>) {
        val filtered = values.filter { it.isNotBlank() }
        if (filtered.isEmpty()) {
            appendLine("$name: []")
            return
        }

        appendLine("$name:")
        filtered.forEach { value ->
            appendLine("  - ${SpeqaMarkdown.quoteYamlScalar(value)}")
        }
    }

    private fun StringBuilder.appendStep(number: Int, step: TestStep) {
        val actionLines = step.action.lines()
        appendLine("$number. ${actionLines.firstOrNull().orEmpty()}${if (actionLines.size > 1) "  " else ""}")
        actionLines.drop(1).forEachIndexed { index, line ->
            appendLine("   $line${if (index != actionLines.lastIndex - 1) "  " else ""}")
        }
        step.expected?.let { exp ->
            if (exp.isEmpty()) {
                appendLine("   >")
            } else {
                exp.lines().forEachIndexed { index, line ->
                    appendLine("   > $line${if (index != exp.lines().lastIndex) "  " else ""}")
                }
            }
        }
        (step.actionAttachments + step.expectedAttachments).forEach { appendAttachment(it, indent = "   ") }
        step.ticket?.let { ticket ->
            if (ticket.isNotBlank()) {
                appendLine()
                appendLine("   Ticket: $ticket")
            }
        }
    }
}
