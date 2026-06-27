package io.github.barsia.speqa.toolwindow

import io.github.barsia.speqa.SpeqaBundle
import io.github.barsia.speqa.filetype.SpeqaIcons
import io.github.barsia.speqa.model.RunResult
import io.github.barsia.speqa.model.Status

/** Capitalizes the first character; shared by both primary facets and the chip labels. */
private fun capitalize(label: String): String = label.replaceFirstChar { it.uppercase() }

/** Display order for the run-result facet: terminal results first, then in-progress and not-started. */
private val RESULT_ORDER = listOf(
    RunResult.PASSED,
    RunResult.FAILED,
    RunResult.BLOCKED,
    RunResult.IN_PROGRESS,
    RunResult.NOT_STARTED,
)

/** Localized display label for a [RunResult], reusing the run editor's labels. */
fun runResultLabel(result: RunResult): String = when (result) {
    RunResult.PASSED -> SpeqaBundle.message("run.passed")
    RunResult.FAILED -> SpeqaBundle.message("run.failed")
    RunResult.BLOCKED -> SpeqaBundle.message("run.blocked")
    RunResult.IN_PROGRESS -> SpeqaBundle.message("run.inProgress")
    RunResult.NOT_STARTED -> SpeqaBundle.message("run.notStarted")
}

/** The TCs tab primary facet: single-select test-case [Status]. */
fun statusPrimaryFacet(filter: SpeqaTreeFilter): PrimaryFacet = PrimaryFacet(
    icon = SpeqaIcons.FilterStatus,
    tooltip = SpeqaBundle.message("toolwindow.speqa.filter.status"),
    isActive = { filter.status != null },
    chipLabel = { filter.status?.let { capitalize(it.label) } },
    clear = { filter.status = null },
    options = {
        listOf(
            PrimaryOption(
                label = SpeqaBundle.message("toolwindow.speqa.filter.allStatuses"),
                icon = null,
                selected = filter.status == null,
                apply = { filter.status = null },
            ),
        ) + Status.entries.map { status ->
            PrimaryOption(
                label = capitalize(status.label),
                icon = SpeqaIcons.forStatus(status),
                selected = filter.status == status,
                apply = { filter.status = status },
            )
        }
    },
)

/** The TRs tab primary facet: single-select run [RunResult]. Reuses the funnel icon. */
fun resultPrimaryFacet(filter: TestRunTreeFilter): PrimaryFacet = PrimaryFacet(
    icon = SpeqaIcons.FilterStatus,
    tooltip = SpeqaBundle.message("toolwindow.speqa.filter.result"),
    isActive = { filter.result != null },
    chipLabel = { filter.result?.let { runResultLabel(it) } },
    clear = { filter.result = null },
    options = {
        listOf(
            PrimaryOption(
                label = SpeqaBundle.message("toolwindow.speqa.filter.allResults"),
                icon = null,
                selected = filter.result == null,
                apply = { filter.result = null },
            ),
        ) + RESULT_ORDER.map { result ->
            PrimaryOption(
                label = runResultLabel(result),
                icon = iconForResultOption(result),
                selected = filter.result == result,
                apply = { filter.result = result },
            )
        }
    },
)

/** Stamp icon for a result option; only the three terminal results carry a stamp in the popup. */
private fun iconForResultOption(result: RunResult): javax.swing.Icon? = when (result) {
    RunResult.PASSED -> SpeqaIcons.TestRunPassed
    RunResult.FAILED -> SpeqaIcons.TestRunFailed
    RunResult.BLOCKED -> SpeqaIcons.TestRunBlocked
    RunResult.IN_PROGRESS, RunResult.NOT_STARTED -> null
}
