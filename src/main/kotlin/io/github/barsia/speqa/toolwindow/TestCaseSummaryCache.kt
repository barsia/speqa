package io.github.barsia.speqa.toolwindow

import com.intellij.openapi.fileEditor.FileDocumentManager
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
    private data class Entry(val stamp: Long, val fromDocument: Boolean, val summary: TestCaseSummary)

    private val cache = ConcurrentHashMap<String, Entry>()

    fun summaryFor(file: VirtualFile): TestCaseSummary {
        // Prefer the live in-memory document so unsaved edits (status changes) are
        // reflected immediately; fall back to the saved file when none is open.
        val document = FileDocumentManager.getInstance().getCachedDocument(file)
        val stamp = document?.modificationStamp ?: file.modificationStamp
        val fromDocument = document != null
        cache[file.path]?.let { if (it.stamp == stamp && it.fromDocument == fromDocument) return it.summary }
        val text = document?.text ?: VfsUtilCore.loadText(file)
        val summary = readSummary(text)
        cache[file.path] = Entry(stamp, fromDocument, summary)
        return summary
    }

    fun invalidate(path: String) {
        cache.remove(path)
    }

    private fun readSummary(text: String): TestCaseSummary = try {
        val testCase = TestCaseParser.parse(text)
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
