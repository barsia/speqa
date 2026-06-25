package io.github.barsia.speqa.toolwindow

import io.github.barsia.speqa.model.Priority
import io.github.barsia.speqa.model.Status

/**
 * Session-only multi-facet filter for the tool window tree. An empty filter
 * (no status, no priority, no tags, no environments) does not constrain results.
 *
 * The tree builds its children on a background thread while the UI mutates this
 * filter on the EDT, so [tags] and [environments] are stored as immutable sets
 * that are swapped atomically (the `@Volatile` reference is replaced, never
 * mutated in place). Off-thread readers therefore always see a consistent set.
 */
class SpeqaTreeFilter {
    @Volatile
    var status: Status? = null

    @Volatile
    var priority: Priority? = null

    @Volatile
    var tags: Set<String> = emptySet()
        private set

    @Volatile
    var environments: Set<String> = emptySet()
        private set

    fun addTag(tag: String) {
        tags = tags + tag
    }

    fun removeTag(tag: String) {
        tags = tags - tag
    }

    fun addEnvironment(environment: String) {
        environments = environments + environment
    }

    fun removeEnvironment(environment: String) {
        environments = environments - environment
    }

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
        tags = emptySet()
        environments = emptySet()
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
