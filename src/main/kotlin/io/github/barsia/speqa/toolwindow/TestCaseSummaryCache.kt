package io.github.barsia.speqa.toolwindow

import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import io.github.barsia.speqa.model.Priority
import io.github.barsia.speqa.model.Status
import io.github.barsia.speqa.model.TestCase
import io.github.barsia.speqa.parser.TestCaseParser
import java.util.concurrent.ConcurrentHashMap

/** Parsed display + filter data for a single test case leaf. */
data class TestCaseSummary(
    val title: String,
    val status: Status,
    val priority: Priority,
    val tags: Set<String>,
    val environments: Set<String>,
)

/**
 * Caches the parsed [TestCaseSummary] for each `.tc.md` file, keyed by path and
 * invalidated when the file's modification stamp changes (or explicitly via
 * [invalidate] on a VFS event).
 */
class TestCaseSummaryCache {
    private data class Entry(val stamp: Long, val summary: TestCaseSummary)

    private val cache = ConcurrentHashMap<String, Entry>()

    fun summaryFor(file: VirtualFile): TestCaseSummary {
        val stamp = file.modificationStamp
        cache[file.path]?.let { if (it.stamp == stamp) return it.summary }
        val summary = readSummary(file)
        cache[file.path] = Entry(stamp, summary)
        return summary
    }

    fun invalidate(path: String) {
        cache.remove(path)
    }

    private fun readSummary(file: VirtualFile): TestCaseSummary = try {
        val testCase = TestCaseParser.parse(VfsUtilCore.loadText(file))
        TestCaseSummary(
            title = testCase.title,
            status = testCase.status ?: Status.DRAFT,
            priority = testCase.priority ?: Priority.NORMAL,
            tags = testCase.tags?.toSet() ?: emptySet(),
            environments = testCase.environment?.toSet() ?: emptySet(),
        )
    } catch (_: Exception) {
        TestCaseSummary(TestCase().title, Status.DRAFT, Priority.NORMAL, emptySet(), emptySet())
    }
}
