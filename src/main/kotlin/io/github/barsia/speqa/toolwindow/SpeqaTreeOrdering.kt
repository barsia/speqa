package io.github.barsia.speqa.toolwindow

import com.intellij.openapi.util.text.NaturalComparator
import io.github.barsia.speqa.model.SpeqaDefaults

/**
 * A child of a tree node, independent of the VFS layer so the ordering rules
 * can be unit-tested. [payload] carries the concrete element (a VirtualFile at
 * runtime); [sortKey] is the folder name or the leaf title.
 */
sealed class SpeqaTreeItem<T> {
    abstract val payload: T
    abstract val sortKey: String

    class Folder<T>(override val payload: T, override val sortKey: String) : SpeqaTreeItem<T>()
    class Leaf<T>(override val payload: T, override val sortKey: String) : SpeqaTreeItem<T>()
}

/** True when [name] is a SpeQA test case file (`*.tc.md`). */
fun isTestCaseFileName(name: String): Boolean =
    SpeqaDefaults.speqaExtension(name) == SpeqaDefaults.TEST_CASE_EXTENSION

/** True when [name] is a SpeQA test run file (`*.tr.md`). */
fun isTestRunFileName(name: String): Boolean =
    SpeqaDefaults.speqaExtension(name) == SpeqaDefaults.TEST_RUN_EXTENSION

/**
 * Folders first (by name), then leaves (by title); both case-insensitive
 * natural order so "Step 2" precedes "Step 10".
 */
fun <T> orderChildren(items: List<SpeqaTreeItem<T>>): List<SpeqaTreeItem<T>> {
    val byKey = Comparator<SpeqaTreeItem<T>> { a, b ->
        NaturalComparator.INSTANCE.compare(a.sortKey, b.sortKey)
    }
    val folders = items.filterIsInstance<SpeqaTreeItem.Folder<T>>().sortedWith(byKey)
    val leaves = items.filterIsInstance<SpeqaTreeItem.Leaf<T>>().sortedWith(byKey)
    return folders + leaves
}
