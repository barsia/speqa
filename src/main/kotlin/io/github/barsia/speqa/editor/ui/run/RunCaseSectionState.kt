package io.github.barsia.speqa.editor.ui.run

import io.github.barsia.speqa.model.RunCase

/**
 * Pure, UI-free decisions for a single [RunCase] section. Kept separate from
 * the Swing [RunCaseSection] so the header/label logic is unit-testable.
 */
object RunCaseSectionState {
    /** Header text: `TC-<id> <title>`, or just `TC-<id>` when the title is blank. */
    fun headerLabel(case: RunCase): String =
        if (case.title.isBlank()) "TC-${case.caseId}" else "TC-${case.caseId} ${case.title}"

    /** Result-pill text for the case. */
    fun resultBadge(case: RunCase): String = case.result.label

    /** Whether the case result was manually overridden (vs. derived from steps). */
    fun isManual(case: RunCase): Boolean = case.manualResult
}
