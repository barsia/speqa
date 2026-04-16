package io.github.barsia.speqa.parser

import io.github.barsia.speqa.model.Attachment
import io.github.barsia.speqa.model.DescriptionBlock
import io.github.barsia.speqa.model.Link
import io.github.barsia.speqa.model.PreconditionsBlock
import io.github.barsia.speqa.model.PreconditionsMarkerStyle
import io.github.barsia.speqa.model.Priority
import io.github.barsia.speqa.model.RunResult
import io.github.barsia.speqa.model.StepResult
import io.github.barsia.speqa.model.StepVerdict
import io.github.barsia.speqa.model.TestCaseBodyBlock
import io.github.barsia.speqa.model.TestRun
import java.time.LocalDateTime

object TestRunParser {
    fun parse(content: String): TestRun {
        val normalized = SpeqaMarkdown.normalizeLineEndings(content)
        if (normalized.isBlank()) return TestRun()

        val (frontmatter, body) = SpeqaMarkdown.splitFrontmatter(normalized)
        val meta = SpeqaMarkdown.parseYamlMap(frontmatter)

        return TestRun(
            id = if ("id" in meta) (meta["id"] as? Number)?.toInt() else null,
            title = SpeqaMarkdown.parseScalar(meta["title"]),
            tags = SpeqaMarkdown.parseStringList(meta["tags"]),
            priority = if ("priority" in meta) Priority.fromString(SpeqaMarkdown.parseScalar(meta["priority"])) else null,
            startedAt = parseDateTime(meta["started_at"]),
            finishedAt = parseDateTime(meta["finished_at"]),
            result = RunResult.fromString(SpeqaMarkdown.parseScalar(meta["result"])),
            manualResult = meta["manual_result"]?.toString()?.trim().equals("true", ignoreCase = true),
            environment = SpeqaMarkdown.parseScalar(meta["environment"]),
            runner = SpeqaMarkdown.parseScalar(meta["runner"]),
            bodyBlocks = parseBodyBlocks(body),
            links = parseLinks(body),
            attachments = parseAttachments(body),
            comment = parseOverallComment(body),
            stepResults = parseStepResults(body),
        )
    }

    private fun parseBodyBlocks(body: String): List<TestCaseBodyBlock> {
        val preSection = bodyBeforeSectionMarker(body).trim('\n')
        if (preSection.isBlank()) return emptyList()

        val paragraphs = preSection
            .split(Regex("\n\\s*\n+"))
            .mapNotNull { raw ->
                val trimmed = raw.trim('\n').trimEnd()
                if (trimmed.isBlank()) null else trimmed
            }

        val result = mutableListOf<TestCaseBodyBlock>()
        var activePreconditions: Pair<PreconditionsMarkerStyle, MutableList<String>>? = null

        for (paragraph in paragraphs) {
            val firstLine = paragraph.lines().firstOrNull().orEmpty().trim()
            val markerStyle = PreconditionsMarkerStyle.fromMarker(firstLine)

            if (markerStyle != null) {
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

    private fun bodyBeforeSectionMarker(body: String): String {
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
                if (SCENARIO_MARKER.matches(trimmed) || ATTACHMENTS_MARKER.matches(trimmed)) break
                if (trimmed.isBlank()) continue
                LINK_PATTERN.matchEntire(trimmed)?.let { match ->
                    result += Link(title = match.groupValues[1], url = match.groupValues[2])
                }
            }
        }
        return result
    }

    private fun parseAttachments(body: String): List<Attachment> {
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
        ATTACHMENT_IMAGE.matchEntire(trimmed)?.let { match ->
            return Attachment(path = match.groupValues[2].replace("%20", " "))
        }
        ATTACHMENT_BARE.matchEntire(trimmed)?.let { match ->
            return Attachment(path = match.groupValues[1].replace("%20", " "))
        }
        return null
    }

    private fun parseStepResults(body: String): List<StepResult> {
        val stepSection = extractStepSection(body)
        if (stepSection.isBlank()) return emptyList()

        val steps = mutableListOf<StepResult>()
        var action: String? = null
        var expected = ""
        var verdict = StepVerdict.NONE
        var inExpected = false
        var inComment = false
        var commentLines = mutableListOf<String>()
        var expectedAttachments = mutableListOf<Attachment>()
        var ticket: String? = null

        fun flush() {
            if (action != null) {
                steps += StepResult(
                    action = action!!,
                    expected = expected,
                    verdict = verdict,
                    comment = commentLines.joinToString("\n"),
                    expectedAttachments = expectedAttachments.toList(),
                    ticket = ticket,
                )
            }
        }

        for (line in stepSection.lines()) {
            val trimmed = line.trim()

            val isTopLevel = !line.startsWith(" ") && !line.startsWith("\t")
            val stepMatch = if (isTopLevel) STEP_PATTERN.matchEntire(trimmed) else null
            if (stepMatch != null) {
                flush()
                action = stepMatch.groupValues[1].trimEnd().removeSuffix("  ")
                expected = ""
                verdict = StepVerdict.NONE
                inExpected = false
                inComment = false
                commentLines = mutableListOf()
                expectedAttachments = mutableListOf()
                ticket = null
                continue
            }

            if (action == null) continue

            if (isTopLevel && trimmed.isNotBlank()) {
                flush()
                action = null
                break
            }

            val verdictMatch = VERDICT_PATTERN.matchEntire(trimmed)
            if (verdictMatch != null) {
                verdict = StepVerdict.fromString(verdictMatch.groupValues[1])
                inExpected = false
                inComment = false
                continue
            }

            val ticketMatch = TICKET_PATTERN.matchEntire(trimmed)
            if (ticketMatch != null) {
                ticket = ticketMatch.groupValues[1].trim()
                inExpected = false
                continue
            }

            if (COMMENT_PATTERN.matches(trimmed)) {
                inComment = true
                inExpected = false
                continue
            }

            if (inComment) {
                if (trimmed.isBlank()) continue
                if (line.startsWith(" ") || line.startsWith("\t")) {
                    commentLines += line.removeListContinuationIndent().trimEnd().removeSuffix("  ")
                    continue
                }
                inComment = false
            }

            if (STEP_ATTACHMENT_PATTERN.matchEntire(trimmed) != null) {
                parseAttachmentLine(trimmed)?.let { expectedAttachments += it }
                continue
            }

            val expectedMatch = EXPECTED_PATTERN.matchEntire(line.trimStart())
            if (expectedMatch != null) {
                inExpected = true
                val expLine = expectedMatch.groupValues[1].trimEnd().removeSuffix("  ")
                expected = if (expected.isEmpty()) expLine else "$expected\n$expLine"
                continue
            }

            if (!inExpected && trimmed.isNotBlank()) {
                action = "$action\n${line.removeListContinuationIndent().trimEnd().removeSuffix("  ")}"
                continue
            }

        }
        flush()
        return steps
    }

    private fun extractStepSection(body: String): String {
        val lines = body.lines()
        var startIndex = -1
        for (i in lines.indices) {
            val trimmed = lines[i].trim()
            if (SCENARIO_MARKER.matches(trimmed)) {
                startIndex = i + 1
                break
            }
        }
        if (startIndex == -1) return ""

        // Collect lines until end — the step section goes to the end.
        // The overall comment parsing will handle text after the last step.
        return lines.drop(startIndex).joinToString("\n")
    }

    private fun parseOverallComment(body: String): String {
        val lines = body.lines()
        var stepsStart = -1
        for (i in lines.indices) {
            val trimmed = lines[i].trim()
            if (SCENARIO_MARKER.matches(trimmed)) {
                stepsStart = i + 1
                break
            }
        }
        if (stepsStart == -1) return ""

        // Find the last step-related line (action, expected, verdict, comment — including plain indented lines)
        var lastStepLine = -1
        var inStepBlock = false
        for (i in stepsStart until lines.size) {
            val line = lines[i]
            val trimmed = line.trim()
            val isStepTopLevel = !line.startsWith(" ") && !line.startsWith("\t")
            if (isStepTopLevel && STEP_PATTERN.matchEntire(trimmed) != null) {
                lastStepLine = i
                inStepBlock = true
                continue
            }
            if (VERDICT_PATTERN.matchEntire(trimmed) != null ||
                EXPECTED_PATTERN.matchEntire(line.trimStart()) != null ||
                COMMENT_PATTERN.matchEntire(trimmed) != null
            ) {
                lastStepLine = i
                continue
            }
            if (inStepBlock && (line.startsWith(" ") || line.startsWith("\t") || trimmed.isBlank())) {
                lastStepLine = i
                continue
            }
            inStepBlock = false
        }
        if (lastStepLine == -1) return ""

        val afterSteps = lines.drop(lastStepLine + 1).joinToString("\n").trim()
        return afterSteps
    }

    private fun parseDateTime(value: Any?): LocalDateTime? {
        return when (value) {
            null -> null
            is LocalDateTime -> value
            is java.util.Date -> LocalDateTime.ofInstant(value.toInstant(), java.time.ZoneId.systemDefault())
            else -> {
                val text = value.toString()
                if (text.isBlank()) null
                else runCatching { LocalDateTime.parse(text) }.getOrNull()
            }
        }
    }

    private fun String.removeListContinuationIndent(): String {
        return when {
            startsWith("   ") -> drop(3)
            startsWith("\t") -> drop(1)
            else -> trimStart()
        }
    }

    private val STEP_PATTERN = Regex("""^\d+\.\s+(.+)$""")
    private val VERDICT_PATTERN = Regex("""^-\s*(passed|failed|skipped|blocked)$""", RegexOption.IGNORE_CASE)
    private val COMMENT_PATTERN = Regex("""^Comment:\s*$""", RegexOption.IGNORE_CASE)
    private val EXPECTED_PATTERN = Regex("""^>\s?(.*)$""")
    private val SCENARIO_MARKER = Regex("""^[Ss]cenario:\s*$""")
    private val ATTACHMENTS_MARKER = Regex("""^[Aa]ttachments:\s*$""")
    private val LINKS_MARKER = Regex("""^[Ll]inks:\s*$""")
    private val LINK_PATTERN = Regex("""^\[([^]]+)]\(([^)]+)\)$""")
    private val ATTACHMENT_IMAGE = Regex("""^!?\[([^]]*)]\(([^)]+)\)$""")
    private val ATTACHMENT_BARE = Regex("""^\[([^]]+)]$""")
    private val TICKET_PATTERN = Regex("""^\s*Ticket:\s*(.+)$""", RegexOption.IGNORE_CASE)
    private val STEP_ATTACHMENT_PATTERN = Regex("""^!?\[.*]\(.*\)$|^\[.*]$""")
}
