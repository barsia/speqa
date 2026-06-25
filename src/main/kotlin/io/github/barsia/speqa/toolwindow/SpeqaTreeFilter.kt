package io.github.barsia.speqa.toolwindow

import io.github.barsia.speqa.model.Priority
import io.github.barsia.speqa.model.Status

/**
 * Session-only multi-facet filter for the tool window tree. An empty filter
 * (no status, no priority, no tags, no environments) does not constrain results.
 */
class SpeqaTreeFilter {
    var status: Status? = null
    var priority: Priority? = null
    val tags: MutableSet<String> = linkedSetOf()
    val environments: MutableSet<String> = linkedSetOf()

    fun isEmpty(): Boolean =
        status == null && priority == null && tags.isEmpty() && environments.isEmpty()

    /** Total number of active selections; each selected tag/environment counts individually. */
    fun activeCount(): Int =
        (if (status != null) 1 else 0) +
            (if (priority != null) 1 else 0) +
            tags.size +
            environments.size

    fun clear() {
        status = null
        priority = null
        tags.clear()
        environments.clear()
    }
}

/**
 * True when [summary] satisfies every active facet of [filter]. A facet with no
 * selection does not constrain. Tags and environments match when the test case has
 * at least one of the selected values (OR within a facet); facets combine with AND.
 */
fun matchesFilter(summary: TestCaseSummary, filter: SpeqaTreeFilter): Boolean {
    if (filter.status != null && summary.status != filter.status) return false
    if (filter.priority != null && summary.priority != filter.priority) return false
    if (filter.tags.isNotEmpty() && summary.tags.none { it in filter.tags }) return false
    if (filter.environments.isNotEmpty() && summary.environments.none { it in filter.environments }) return false
    return true
}
