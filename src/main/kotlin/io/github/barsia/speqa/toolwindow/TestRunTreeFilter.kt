package io.github.barsia.speqa.toolwindow

import io.github.barsia.speqa.model.RunResult

/**
 * Test-run filter for the TRs tab. The primary facet is [result]; priority,
 * tags, and environment are inherited from [SpeqaFilter]. An empty filter does
 * not constrain results.
 */
class TestRunTreeFilter : SpeqaFilter() {
    @Volatile
    var result: RunResult? = null

    override fun primaryActive(): Boolean = result != null

    override fun clearPrimary() {
        result = null
    }
}

/**
 * True when [summary] satisfies every active facet of [filter]. Mirrors
 * [matchesFilter] for test cases: the primary facet is the run [RunResult], and a
 * run with no priority is excluded when the priority facet is set.
 */
fun matchesRunFilter(summary: TestRunSummary, filter: TestRunTreeFilter): Boolean {
    val result = filter.result
    val priority = filter.priority
    val tags = filter.tags
    val environments = filter.environments
    if (result != null && summary.result != result) return false
    if (priority != null && summary.priority != priority) return false
    if (tags.isNotEmpty() && summary.tags.none { it in tags }) return false
    if (environments.isNotEmpty() && summary.environments.none { it in environments }) return false
    return true
}
