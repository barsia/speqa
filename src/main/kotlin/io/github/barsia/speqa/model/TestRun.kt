package io.github.barsia.speqa.model

import java.time.LocalDateTime

enum class StepVerdict(val label: String) {
    NONE(""),
    PASSED("passed"),
    FAILED("failed"),
    SKIPPED("skipped"),
    BLOCKED("blocked");

    companion object {
        fun fromString(value: String): StepVerdict {
            val trimmed = value.trim()
            if (trimmed.isBlank()) return NONE
            return entries.firstOrNull { it.label.equals(trimmed, ignoreCase = true) } ?: NONE
        }
    }
}

enum class RunResult(val label: String) {
    NOT_STARTED("not_started"),
    IN_PROGRESS("in_progress"),
    PASSED("passed"),
    FAILED("failed"),
    BLOCKED("blocked");

    companion object {
        fun fromString(value: String): RunResult {
            return entries.firstOrNull { it.label.equals(value.trim(), ignoreCase = true) } ?: NOT_STARTED
        }
    }
}

data class StepResult(
    val action: String = "",
    val expected: String = "",
    val verdict: StepVerdict = StepVerdict.NONE,
    val comment: String = "",
    val actionAttachments: List<Attachment> = emptyList(),
    val expectedAttachments: List<Attachment> = emptyList(),
    val ticket: String? = null,
)

data class TestRun(
    val id: Int? = null,
    val title: String = "",
    val tags: List<String> = emptyList(),
    val priority: Priority? = null,
    val manualResult: Boolean = false,
    val startedAt: LocalDateTime? = null,
    val finishedAt: LocalDateTime? = null,
    val result: RunResult = RunResult.NOT_STARTED,
    val environment: String = "",
    val runner: String = "",
    val bodyBlocks: List<TestCaseBodyBlock> = emptyList(),
    val links: List<Link> = emptyList(),
    val attachments: List<Attachment> = emptyList(),
    val comment: String = "",
    val stepResults: List<StepResult> = emptyList(),
)
