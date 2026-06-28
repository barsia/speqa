package io.github.barsia.speqa.registry

/** One test-case file as seen in the current tree snapshot. */
data class TestCaseIdEntry(
    val path: String,
    val id: Int,
    val createdEpochMillis: Long?,
)

/** A single renumbering: [path] moves from [oldId] to [newId]. */
data class IdRenumber(
    val path: String,
    val oldId: Int,
    val newId: Int,
)

/**
 * Pure, origin-agnostic batch resolution for duplicate test-case ids. It reasons only
 * about the given snapshot, never about git branches.
 *
 * Within each group of entries that share an id, the earliest-created entry keeps the
 * id (createdEpochMillis ascending, nulls last, then path ascending as a stable tie
 * break so two machines resolving an identical tree produce an identical result). Every
 * other entry is a loser. Losers, ordered by the same key, are each assigned the first
 * id that is free across the whole snapshot and across assignments already made in this
 * pass, so the result contains no new duplicate and no non-duplicate file is moved.
 */
fun computeDuplicateIdRenumberPlan(entries: List<TestCaseIdEntry>): List<IdRenumber> {
    val occupied = HashSet<Int>()
    entries.forEach { occupied.add(it.id) }

    val order = compareBy<TestCaseIdEntry>(
        { it.createdEpochMillis ?: Long.MAX_VALUE },
        { it.path },
    )

    val losers = entries
        .groupBy { it.id }
        .values
        .filter { it.size > 1 }
        .flatMap { group -> group.sortedWith(order).drop(1) }
        .sortedWith(order)

    var candidate = 1
    val plan = ArrayList<IdRenumber>(losers.size)
    for (loser in losers) {
        while (candidate in occupied) candidate++
        occupied.add(candidate)
        plan.add(IdRenumber(loser.path, loser.id, candidate))
    }
    return plan
}

/** One row of the duplicate-id review: a file that shares its id with at least one other file. */
data class DuplicateIdReviewRow(
    val path: String,
    val oldId: Int,
    val newId: Int,
) {
    /** True for the single file in each group that keeps the contested id (it is not renumbered). */
    val keepsId: Boolean get() = newId == oldId
}

/**
 * Every file that participates in a duplicate-id group, each annotated with the id it ends up with.
 * Unlike [computeDuplicateIdRenumberPlan] - which lists only the files being renumbered - this also
 * includes the kept file of each group, so the UI can show the whole collision rather than only the
 * files that change. Rows are ordered by id, and within a group earliest-first so the kept file leads.
 */
fun computeDuplicateIdReview(entries: List<TestCaseIdEntry>): List<DuplicateIdReviewRow> {
    val newIdByPath = computeDuplicateIdRenumberPlan(entries).associate { it.path to it.newId }
    val duplicatedIds = entries.groupingBy { it.id }.eachCount().filterValues { it > 1 }.keys
    val withinGroup = compareBy<TestCaseIdEntry>(
        { it.createdEpochMillis ?: Long.MAX_VALUE },
        { it.path },
    )
    return entries
        .filter { it.id in duplicatedIds }
        .sortedWith(compareBy<TestCaseIdEntry> { it.id }.then(withinGroup))
        .map { DuplicateIdReviewRow(it.path, it.id, newIdByPath[it.path] ?: it.id) }
}
