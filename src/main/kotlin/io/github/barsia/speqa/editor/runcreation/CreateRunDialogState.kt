package io.github.barsia.speqa.editor.runcreation

/**
 * A single test-case candidate row shown in the Create Test Run dialog. [key] is a
 * stable identifier (the source file path in real use) used to track the checked state
 * independently of the currently visible (filtered) subset.
 */
data class CandidateCase(val key: String, val title: String, val facets: CaseFacets)

/**
 * Pure selection logic for the Create Test Run dialog. A candidate is "selected" only
 * when it is both visible under the active filter and present in the checked-set; a case
 * filtered out is never selected, yet its checked state is preserved in the checked-set
 * so re-entering the filter restores it.
 */
object CreateRunDialogState {
    fun visible(all: List<CandidateCase>, filter: CreateRunFilter): List<CandidateCase> =
        all.filter { filter.matches(it.facets) }

    fun selectedKeys(all: List<CandidateCase>, filter: CreateRunFilter, checked: Set<String>): List<String> =
        visible(all, filter).map { it.key }.filter { it in checked }

    fun selectedCount(all: List<CandidateCase>, filter: CreateRunFilter, checked: Set<String>): Int =
        selectedKeys(all, filter, checked).size

    /** Keys that should start checked: every candidate is pre-selected by default. */
    fun initialCheckedKeys(candidates: List<CandidateCase>): Set<String> =
        candidates.mapTo(LinkedHashSet()) { it.key }
}
