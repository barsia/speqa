package io.github.barsia.speqa.toolwindow

import com.intellij.openapi.util.text.NaturalComparator
import io.github.barsia.speqa.model.SpeqaDefaults

/**
 * A child of a tree node, independent of the VFS layer so the ordering rules
 * can be unit-tested. [payload] carries the concrete element (a VirtualFile at
 * runtime); [sortKey] is the folder name or the test case title.
 */
sealed class SpeqaTreeItem<T> {
    abstract val payload: T
    abstract val sortKey: String

    class Folder<T>(override val payload: T, override val sortKey: String) : SpeqaTreeItem<T>()
    class TestCase<T>(override val payload: T, override val sortKey: String) : SpeqaTreeItem<T>()
}

/** True when [name] is a SpeQA test case file (`*.tc.md`). */
fun isTestCaseFileName(name: String): Boolean =
    SpeqaDefaults.speqaExtension(name) == SpeqaDefaults.TEST_CASE_EXTENSION

/**
 * Folders first (by name), then test cases (by title); both case-insensitive
 * natural order so "Step 2" precedes "Step 10".
 */
fun <T> orderChildren(items: List<SpeqaTreeItem<T>>): List<SpeqaTreeItem<T>> {
    val byKey = Comparator<SpeqaTreeItem<T>> { a, b ->
        NaturalComparator.INSTANCE.compare(a.sortKey, b.sortKey)
    }
    val folders = items.filterIsInstance<SpeqaTreeItem.Folder<T>>().sortedWith(byKey)
    val cases = items.filterIsInstance<SpeqaTreeItem.TestCase<T>>().sortedWith(byKey)
    return folders + cases
}
