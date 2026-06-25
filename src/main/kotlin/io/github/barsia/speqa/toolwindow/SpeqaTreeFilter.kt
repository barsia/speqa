package io.github.barsia.speqa.toolwindow

import io.github.barsia.speqa.model.Status

/**
 * Session-only status filter for the tool window tree. A `null` [status] means
 * "All" (no filtering); a specific value shows only test cases with that status.
 */
class SpeqaTreeFilter(var status: Status? = null)

/**
 * A test case with [status] passes the active filter when no status is selected
 * ([filter] is null, meaning "All") or its status equals the selection.
 */
fun matchesStatusFilter(status: Status, filter: Status?): Boolean =
    filter == null || status == filter
