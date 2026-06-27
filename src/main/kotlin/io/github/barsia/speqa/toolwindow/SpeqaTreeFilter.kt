package io.github.barsia.speqa.toolwindow

import io.github.barsia.speqa.model.Status

/**
 * Test-case filter for the TCs tab. The primary facet is [status]; priority,
 * tags, and environment are inherited from [SpeqaFilter]. An empty filter does
 * not constrain results.
 */
class SpeqaTreeFilter : SpeqaFilter() {
    @Volatile
    var status: Status? = null

    override fun primaryActive(): Boolean = status != null

    override fun clearPrimary() {
        status = null
    }
}

/**
 * True when [summary] satisfies every active facet of [filter]. A facet with no
 * selection does not constrain. Tags and environments match when the test case has
 * at least one of the selected values (OR within a facet); facets combine with AND.
 *
 * Each volatile facet is read exactly once into a local so the predicate evaluates
 * against a single consistent snapshot even if the EDT mutates the filter meanwhile.
 */
fun matchesFilter(summary: TestCaseSummary, filter: SpeqaTreeFilter): Boolean {
    val status = filter.status
    val priority = filter.priority
    val tags = filter.tags
    val environments = filter.environments
    if (status != null && summary.status != status) return false
    if (priority != null && summary.priority != priority) return false
    if (tags.isNotEmpty() && summary.tags.none { it in tags }) return false
    if (environments.isNotEmpty() && summary.environments.none { it in environments }) return false
    return true
}
