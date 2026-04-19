package io.github.barsia.speqa.parser

import io.github.barsia.speqa.model.Attachment
import io.github.barsia.speqa.model.Link
import io.github.barsia.speqa.model.Priority
import io.github.barsia.speqa.model.Status
import io.github.barsia.speqa.model.TestCase
import io.github.barsia.speqa.model.TestCaseBodyBlock
import io.github.barsia.speqa.model.TestStep
import io.github.barsia.speqa.model.DescriptionBlock
import io.github.barsia.speqa.model.PreconditionsBlock
import io.github.barsia.speqa.model.PreconditionsMarkerStyle

object TestCaseParser {
    fun parse(content: String): TestCase {
        val normalized = SpeqaMarkdown.normalizeLineEndings(content)
        if (normalized.isBlank()) {
            return TestCase()
        }

        val (frontmatter, body) = try {
            SpeqaMarkdown.splitFrontmatter(normalized)
        } catch (_: IllegalArgumentException) {
            "" to normalized
        }

        val meta = try {
            SpeqaMarkdown.parseYamlMap(frontmatter)
        } catch (_: IllegalArgumentException) {
            emptyMap()
        }

        return TestCase(
            id = if ("id" in meta) {
                val raw = meta["id"]
                (raw as? Number)?.toInt() ?: (raw as? String)?.trim()?.toIntOrNull()
            } else null,
            title = SpeqaMarkdown.parseScalar(meta["title"]).ifBlank { "Untitled Test Case" },
            priority = if ("priority" in meta) Priority.fromString(SpeqaMarkdown.parseScalar(meta["priority"])) else null,
            status = if ("status" in meta) Status.fromString(SpeqaMarkdown.parseScalar(meta["status"])) else null,
            environment = if ("environment" in meta) SpeqaMarkdown.parseStringList(meta["environment"]) else null,
            tags = if ("tags" in meta) SpeqaMarkdown.parseStringList(meta["tags"]) else null,
            attachments = parseGeneralAttachments(body),
            links = parseLinks(body),
            bodyBlocks = parseBodyBlocks(body),
            steps = parseSteps(body),
        )
    }

    private fun parseBodyBlocks(body: String): List<TestCaseBodyBlock> {
        val preStepsBody = bodyBeforeScenarioMarker(body).trim('\n')
        if (preStepsBody.isBlank()) return emptyList()

        val paragraphs = preStepsBody
            .split(Regex("\n\\s*\n+"))
            .mapNotNull { raw ->
                val trimmed = raw.trim('\n').trimEnd()
                if (trimmed.isBlank()) null else trimmed
            }

        // Once a Preconditions marker is seen, all subsequent paragraphs
        // (until the next marker) belong to that preconditions block.
        val result = mutableListOf<TestCaseBodyBlock>()
        var activePreconditions: Pair<PreconditionsMarkerStyle, MutableList<String>>? = null

        for (paragraph in paragraphs) {
            val firstLine = paragraph.lines().firstOrNull().orEmpty().trim()
            val markerStyle = PreconditionsMarkerStyle.fromMarker(firstLine)

            if (markerStyle != null) {
                // Flush any active preconditions
                activePreconditions?.let { (style, parts) ->
                    result += PreconditionsBlock(style, parts.joinToString("\n\n"))
                }
                val contentAfterMarker = paragraph.lines().drop(1).joinToString("\n").trim('\n').trimEnd()
                activePreconditions = markerStyle to mutableListOf<String>().also {
                    if (contentAfterMarker.isNotBlank()) it += contentAfterMarker
                }
            } else if (activePreconditions != null) {
                activePreconditions.second += paragraph
            } else {
                result += DescriptionBlock(paragraph)
            }
        }

        activePreconditions?.let { (style, parts) ->
            result += PreconditionsBlock(style, parts.joinToString("\n\n"))
        }

        return result
    }

    private fun parseBodyBlock(block: String): TestCaseBodyBlock {
        val lines = block.lines()
        val firstLine = lines.firstOrNull().orEmpty().trim()
        val markerStyle = PreconditionsMarkerStyle.fromMarker(firstLine)
        if (markerStyle != null) {
            val markdown = lines.drop(1).joinToString("\n").trim('\n').trimEnd()
            return PreconditionsBlock(markerStyle, markdown)
        }
        return DescriptionBlock(block)
    }

    private fun bodyBeforeScenarioMarker(body: String): String {
        val collected = mutableListOf<String>()
        for (line in body.lines()) {
            val trimmed = line.trim()
            if (SCENARIO_MARKER.matches(trimmed) || ATTACHMENTS_MARKER.matches(trimmed) || LINKS_MARKER.matches(trimmed)) {
                break
            }
            collected += line
        }
        return collected.joinToString("\n")
    }

    private fun parseGeneralAttachments(body: String): List<Attachment> {
        val result = mutableListOf<Attachment>()
        var inAttachmentsSection = false
        for (line in body.lines()) {
            val trimmed = line.trim()
            if (ATTACHMENTS_MARKER.matches(trimmed)) {
                inAttachmentsSection = true
                continue
            }
            if (inAttachmentsSection) {
                if (SCENARIO_MARKER.matches(trimmed) || LINKS_MARKER.matches(trimmed)) break
                if (trimmed.isBlank()) continue
                parseAttachmentLine(trimmed)?.let { result += it }
            }
        }
        return result
    }

    private fun parseAttachmentLine(trimmed: String): Attachment? {
        ATTACHMENT_LINK.matchEntire(trimmed)?.let { match ->
            return Attachment(path = match.groupValues[2].replace("%20", " "))
        }
        ATTACHMENT_BARE.matchEntire(trimmed)?.let { match ->
            return Attachment(path = match.groupValues[1].replace("%20", " "))
        }
        return null
    }

    private fun parseSteps(body: String): List<TestStep> {
        val steps = mutableListOf<TestStep>()
        var actionLines = mutableListOf<String>()
        var expectedAttachments = mutableListOf<Attachment>()
        var currentExpected: MutableList<String>? = null
        var currentExpectedGroupSize = 1
        var groupStartIndex = 0
        var currentTicket: String? = null
        var afterMarker = false
        var inExpected = false

        fun flushAction() {
            if (steps.isNotEmpty() && actionLines.isNotEmpty()) {
                val extra = actionLines.joinToString("\n").trimEnd()
                if (extra.isNotBlank()) {
                    val current = steps.last()
                    steps[steps.lastIndex] = current.copy(
                        action = (current.action + "\n" + extra).trim(),
                    )
                }
            }
            actionLines = mutableListOf()
        }

        fun flushExpectedAttachments() {
            if (steps.isNotEmpty() && expectedAttachments.isNotEmpty()) {
                val current = steps.last()
                steps[steps.lastIndex] = current.copy(
                    expectedAttachments = current.expectedAttachments + expectedAttachments,
                )
            }
            expectedAttachments = mutableListOf()
        }

        for (line in body.lines()) {
            val trimmed = line.trim()

            if (!afterMarker) {
                if (SCENARIO_MARKER.matches(trimmed)) afterMarker = true
                continue
            }

            val isTopLevel = !line.startsWith(" ") && !line.startsWith("\t")
            val stepMatch = if (isTopLevel) STEP_PATTERN.matchEntire(trimmed) else null

            // New step starts
            if (stepMatch != null) {
                flushAction()
                flushExpectedAttachments()
                if (steps.isNotEmpty() && currentTicket != null) {
                    steps[steps.lastIndex] = steps.last().copy(ticket = currentTicket)
                    currentTicket = null
                }
                if (currentExpected != null) {
                    groupStartIndex = steps.size
                    currentExpected = null
                    currentExpectedGroupSize = 1
                }
                inExpected = false
                steps += TestStep(action = stepMatch.groupValues[1].trimEnd().removeSuffix("  "))
                continue
            }

            // Blockquote — expected result
            val quoteMatch = EXPECTED_PATTERN.matchEntire(trimmed)
            if (quoteMatch != null && steps.isNotEmpty()) {
                flushAction()
                if (currentExpected == null) {
                    currentExpected = mutableListOf()
                    currentExpectedGroupSize = steps.size - groupStartIndex
                }
                inExpected = true
                currentExpected += quoteMatch.groupValues[1].trimEnd().removeSuffix("  ")
                steps[steps.lastIndex] = steps.last().copy(
                    expected = currentExpected.joinToString("\n"),
                    expectedGroupSize = currentExpectedGroupSize,
                )
                continue
            }

            // Blank line or heading — separator, not content
            if (trimmed.isBlank() || trimmed.startsWith("## ")) {
                inExpected = false
                continue
            }

            // Attachment line — collect into the appropriate list
            if (steps.isNotEmpty()) {
                val attachment = parseAttachmentLine(trimmed)
                if (attachment != null) {
                    expectedAttachments += attachment
                    continue
                }
            }

            // Ticket line
            val ticketMatch = TICKET_PATTERN.matchEntire(trimmed)
            if (ticketMatch != null && steps.isNotEmpty()) {
                currentTicket = ticketMatch.groupValues[1].trim()
                continue
            }

            // Any other line — append to current step action (if we're not past expected)
            // Strip up to 3 leading spaces (Markdown list continuation indent)
            if (steps.isNotEmpty() && !inExpected) {
                val stripped = line.removeListContinuationIndent()
                actionLines += stripped.trimEnd().removeSuffix("  ")
            }
        }

        flushAction()
        flushExpectedAttachments()
        if (steps.isNotEmpty() && currentTicket != null) {
            steps[steps.lastIndex] = steps.last().copy(ticket = currentTicket)
        }
        return steps
    }

    private fun parseLinks(body: String): List<Link> {
        val result = mutableListOf<Link>()
        var inLinksSection = false
        for (line in body.lines()) {
            val trimmed = line.trim()
            if (LINKS_MARKER.matches(trimmed)) {
                inLinksSection = true
                continue
            }
            if (inLinksSection) {
                if (SCENARIO_MARKER.matches(trimmed)) break
                if (trimmed.isBlank()) continue
                LINK_PATTERN.matchEntire(trimmed)?.let { match ->
                    result += Link(title = match.groupValues[1], url = match.groupValues[2])
                }
            }
        }
        return result
    }

    private val SCENARIO_MARKER = Regex("""^[Ss]cenario:\s*$""")
    private val STEP_PATTERN = Regex("""^\d+\.\s*(.*)$""")
    private val EXPECTED_PATTERN = Regex("""^>\s?(.*)$""")
    private val ATTACHMENTS_MARKER = Regex("""^[Aa]ttachments:\s*$""")
    private val ATTACHMENT_LINK = Regex("""^!?\[([^\]]*)\]\(([^)]+)\)$""")
    private val ATTACHMENT_BARE = Regex("""^\[([^\]]+)\]$""")
    private val LINKS_MARKER = Regex("""^[Ll]inks:\s*$""")
    private val LINK_PATTERN = Regex("""^\[([^\]]+)\]\(([^)]+)\)$""")
    private val TICKET_PATTERN = Regex("""^\s*Ticket:\s*(.+)$""", RegexOption.IGNORE_CASE)

    /** Strip up to 3 leading spaces — Markdown list continuation indent for `N. ` items. */
    private fun String.removeListContinuationIndent(): String {
        var i = 0
        while (i < 3 && i < length && this[i] == ' ') i++
        return substring(i)
    }
}
