package io.github.barsia.speqa.editor.runcreation

import io.github.barsia.speqa.model.Priority
import io.github.barsia.speqa.model.Status

data class CreateRunFilter(
    val status: Status? = null,
    val priority: Priority? = null,
    val tags: Set<String> = emptySet(),
    val environments: Set<String> = emptySet(),
) {
    fun matches(facets: CaseFacets): Boolean {
        if (status != null && facets.status != status) return false
        if (priority != null && facets.priority != priority) return false
        if (tags.isNotEmpty() && facets.tags.none { it in tags }) return false
        if (environments.isNotEmpty() && facets.environments.none { it in environments }) return false
        return true
    }
}
