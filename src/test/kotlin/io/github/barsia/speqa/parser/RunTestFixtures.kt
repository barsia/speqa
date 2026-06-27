package io.github.barsia.speqa.parser

import io.github.barsia.speqa.model.Attachment
import io.github.barsia.speqa.model.Link
import io.github.barsia.speqa.model.Priority
import io.github.barsia.speqa.model.RunCase
import io.github.barsia.speqa.model.RunResult
import io.github.barsia.speqa.model.StepResult
import io.github.barsia.speqa.model.TestCaseBodyBlock
import io.github.barsia.speqa.model.TestRun
import java.time.LocalDateTime

/**
 * Builds a single-case [TestRun] from flat per-case fields, mirroring the pre-multi-case
 * constructor. Used by these legacy tests until later tasks migrate them to explicit [RunCase]s.
 */
internal fun flatRun(
    id: Int? = null,
    title: String = "",
    tags: List<String> = emptyList(),
    priority: Priority? = null,
    manualResult: Boolean = false,
    startedAt: LocalDateTime? = null,
    finishedAt: LocalDateTime? = null,
    result: RunResult = RunResult.NOT_STARTED,
    environment: List<String> = emptyList(),
    runner: String = "",
    bodyBlocks: List<TestCaseBodyBlock> = emptyList(),
    links: List<Link> = emptyList(),
    attachments: List<Attachment> = emptyList(),
    comment: String = "",
    stepResults: List<StepResult> = emptyList(),
): TestRun = TestRun(
    id = id,
    title = title,
    runner = runner,
    startedAt = startedAt,
    finishedAt = finishedAt,
    result = result,
    manualResult = manualResult,
    comment = comment,
    cases = listOf(
        RunCase(
            caseId = id ?: 0,
            title = title,
            priority = priority,
            tags = tags,
            environment = environment,
            bodyBlocks = bodyBlocks,
            links = links,
            attachments = attachments,
            stepResults = stepResults,
            result = result,
        ),
    ),
)
