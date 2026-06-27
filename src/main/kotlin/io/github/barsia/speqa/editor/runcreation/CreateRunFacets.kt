package io.github.barsia.speqa.editor.runcreation

import io.github.barsia.speqa.model.Priority
import io.github.barsia.speqa.model.Status

data class CaseFacets(
    val status: Status,
    val priority: Priority,
    val tags: Set<String>,
    val environments: Set<String>,
)

data class PresentFacets(
    val status: Boolean,
    val priority: Boolean,
    val tags: Boolean,
    val environments: Boolean,
)

object CreateRunFacets {
    fun present(cases: List<CaseFacets>): PresentFacets = PresentFacets(
        status = cases.map { it.status }.distinct().size >= 2,
        priority = cases.map { it.priority }.distinct().size >= 2,
        tags = cases.any { it.tags.isNotEmpty() },
        environments = cases.any { it.environments.isNotEmpty() },
    )
}
