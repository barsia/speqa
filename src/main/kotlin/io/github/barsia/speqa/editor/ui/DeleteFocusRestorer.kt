package io.github.barsia.speqa.editor.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester

/**
 * Where to move keyboard focus after an item is deleted from a list.
 *
 *  - [Item] — focus the row now occupying [index] (caller's surviving list).
 *  - [AddButton] — the list is empty and an add button is the natural landing.
 *  - [None] — nothing to focus (empty list with no add button, or invalid input).
 */
sealed class DeleteFocusTarget {
    data class Item(val index: Int) : DeleteFocusTarget()
    object AddButton : DeleteFocusTarget()
    object None : DeleteFocusTarget()
}

/**
 * Pure decision: after deleting [deletedIndex] from a list of size [sizeBefore],
 * where should focus go?
 *
 * Rules:
 *  - out-of-range index or empty list → None
 *  - list becomes empty → AddButton (or None when [hasAddButton] is false)
 *  - deleted not-last → Item at same index (next item slid up)
 *  - deleted last → Item at previous index
 */
fun nextFocusTargetAfterDelete(
    deletedIndex: Int,
    sizeBefore: Int,
    hasAddButton: Boolean = true,
): DeleteFocusTarget {
    if (sizeBefore <= 0 || deletedIndex < 0 || deletedIndex >= sizeBefore) return DeleteFocusTarget.None
    val sizeAfter = sizeBefore - 1
    return when {
        sizeAfter == 0 -> if (hasAddButton) DeleteFocusTarget.AddButton else DeleteFocusTarget.None
        deletedIndex < sizeAfter -> DeleteFocusTarget.Item(deletedIndex)
        else -> DeleteFocusTarget.Item(sizeAfter - 1)
    }
}

/**
 * Encapsulates delete-focus restoration for a list UI.
 *
 * Usage:
 * ```
 * val restorer = rememberDeleteFocusRestorer(items.size, addRequester)
 * items.forEachIndexed { index, item ->
 *     Row(modifier = Modifier.focusRequester(restorer.itemRequesters[index])) { … }
 *     // in onDelete:
 *     restorer.onDeleted(index)
 * }
 * ```
 *
 * Pass [addRequester] = null if the list has no add button.
 */
class DeleteFocusRestorer internal constructor(
    val itemRequesters: List<FocusRequester>,
    private val setPendingTarget: (DeleteFocusTarget) -> Unit,
) {
    fun onDeleted(deletedIndex: Int, sizeBefore: Int = itemRequesters.size) {
        setPendingTarget(
            nextFocusTargetAfterDelete(
                deletedIndex = deletedIndex,
                sizeBefore = sizeBefore,
                hasAddButton = true,
            )
        )
    }
}

/** Convenience: creates internal [FocusRequester]s sized to [listSize]. */
@Composable
fun rememberDeleteFocusRestorer(
    listSize: Int,
    addRequester: FocusRequester?,
): DeleteFocusRestorer {
    val itemRequesters = remember(listSize) { List(listSize) { FocusRequester() } }
    return rememberDeleteFocusRestorer(itemRequesters, addRequester)
}

/** Use external [itemRequesters] (e.g. already wired for Tab-order navigation). */
@Composable
fun rememberDeleteFocusRestorer(
    itemRequesters: List<FocusRequester>,
    addRequester: FocusRequester?,
): DeleteFocusRestorer {
    var pendingTarget by remember { mutableStateOf<DeleteFocusTarget>(DeleteFocusTarget.None) }

    LaunchedEffect(pendingTarget, itemRequesters.size) {
        val target = pendingTarget
        if (target is DeleteFocusTarget.None) return@LaunchedEffect
        kotlinx.coroutines.yield()
        try {
            when (target) {
                is DeleteFocusTarget.Item ->
                    if (target.index in itemRequesters.indices) itemRequesters[target.index].requestFocus()
                is DeleteFocusTarget.AddButton ->
                    addRequester?.requestFocus()
                is DeleteFocusTarget.None -> {}
            }
        } catch (_: Throwable) {
        }
        pendingTarget = DeleteFocusTarget.None
    }

    return remember(itemRequesters) {
        DeleteFocusRestorer(
            itemRequesters = itemRequesters,
            setPendingTarget = { pendingTarget = it },
        )
    }
}
