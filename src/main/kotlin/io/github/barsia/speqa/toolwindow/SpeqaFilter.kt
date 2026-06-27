package io.github.barsia.speqa.toolwindow

import io.github.barsia.speqa.model.Priority

/**
 * Shared multi-facet filter state for both tool-window tabs. The primary facet
 * (status for test cases, result for test runs) is tab-specific and lives in the
 * concrete subclass; priority, tags, and environment are shared here.
 *
 * The tree builds its children on a background thread while the UI mutates this
 * filter on the EDT, so [tags] and [environments] are stored as immutable sets
 * that are swapped atomically (the `@Volatile` reference is replaced, never
 * mutated in place). Off-thread readers therefore always see a consistent set.
 */
sealed class SpeqaFilter {
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

    /** True when the tab-specific primary facet (status/result) is selected. */
    protected abstract fun primaryActive(): Boolean

    /** Resets the tab-specific primary facet to "no selection". */
    protected abstract fun clearPrimary()

    fun isEmpty(): Boolean =
        !primaryActive() && priority == null && tags.isEmpty() && environments.isEmpty()

    /** Total number of active selections; each selected tag/environment counts individually. */
    fun activeCount(): Int =
        (if (primaryActive()) 1 else 0) +
            (if (priority != null) 1 else 0) +
            tags.size +
            environments.size

    fun clear() {
        clearPrimary()
        priority = null
        tags = emptySet()
        environments = emptySet()
    }
}
