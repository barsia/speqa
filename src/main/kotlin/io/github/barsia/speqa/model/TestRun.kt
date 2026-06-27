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
    val tickets: List<String> = emptyList(),
    val links: List<Link> = emptyList(),
    val verdict: StepVerdict = StepVerdict.NONE,
    val comment: String = "",
    val attachments: List<Attachment> = emptyList(),
)

data class RunCase(
    val caseId: Int,
    val title: String = "",
    val priority: Priority? = null,
    val tags: List<String> = emptyList(),
    val environment: List<String> = emptyList(),
    val bodyBlocks: List<TestCaseBodyBlock> = emptyList(),
    val links: List<Link> = emptyList(),
    val attachments: List<Attachment> = emptyList(),
    val stepResults: List<StepResult> = emptyList(),
    val result: RunResult = RunResult.NOT_STARTED,
    val manualResult: Boolean = false,
)

data class TestRun(
    val id: Int? = null,
    val title: String = "",
    val runner: String = "",
    val startedAt: LocalDateTime? = null,
    val finishedAt: LocalDateTime? = null,
    val result: RunResult = RunResult.NOT_STARTED,
    val manualResult: Boolean = false,
    val cases: List<RunCase> = emptyList(),
    val comment: String = "",
) {
    /** Single-case compatibility view: the lone case, or null when not exactly one. */
    val singleCase: RunCase? get() = cases.singleOrNull()

    // Compat read accessors used by the existing single-case editor/patcher (Plan 2 migrates them).
    val tags: List<String> get() = singleCase?.tags ?: emptyList()
    val priority: Priority? get() = singleCase?.priority
    val environment: List<String> get() = singleCase?.environment ?: emptyList()
    val bodyBlocks: List<TestCaseBodyBlock> get() = singleCase?.bodyBlocks ?: emptyList()
    val links: List<Link> get() = singleCase?.links ?: emptyList()
    val attachments: List<Attachment> get() = singleCase?.attachments ?: emptyList()

    // Unlike the other per-case getters (which delegate to the single case), this intentionally
    // flattens step results across all cases so multi-case runs still expose every step.
    val stepResults: List<StepResult> get() = cases.flatMap { it.stepResults }

    /**
     * Single-case compatibility mutator: transforms the lone case (creating one from run-level
     * id/title when there is none) and returns a copy with exactly that one case. Used by the
     * existing single-case editor that still edits per-case fields via the run; Plan 2 migrates it.
     *
     * Single-case runs only: a run with more than one case is not supported here - [singleOrNull]
     * yields null, so the fallback synthesizes a fresh case and drops the existing ones.
     */
    fun withSingleCase(transform: (RunCase) -> RunCase): TestRun {
        val base = cases.singleOrNull() ?: RunCase(caseId = id ?: 0, title = title)
        return copy(cases = listOf(transform(base)))
    }
}
