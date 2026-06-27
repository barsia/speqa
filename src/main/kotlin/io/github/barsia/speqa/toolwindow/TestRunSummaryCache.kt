package io.github.barsia.speqa.toolwindow

import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import io.github.barsia.speqa.model.Priority
import io.github.barsia.speqa.model.RunResult
import io.github.barsia.speqa.parser.TestRunParser
import java.util.concurrent.ConcurrentHashMap

/** Parsed display + filter data for a single test run leaf. */
data class TestRunSummary(
    val title: String,
    val result: RunResult,
    val priority: Priority?,
    val tags: Set<String>,
    val environments: Set<String>,
)

/**
 * Caches the parsed [TestRunSummary] for each `.tr.md` file, keyed by path and
 * invalidated when the file's modification stamp changes (or explicitly via
 * [invalidate] on a VFS event). Mirrors [TestCaseSummaryCache].
 */
class TestRunSummaryCache {
    private data class Entry(val stamp: Long, val fromDocument: Boolean, val summary: TestRunSummary)

    private val cache = ConcurrentHashMap<String, Entry>()

    fun summaryFor(file: VirtualFile): TestRunSummary {
        // Prefer the live in-memory document so unsaved edits (status/result changes)
        // are reflected immediately; fall back to the saved file when none is open.
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

    private fun readSummary(text: String): TestRunSummary = try {
        val run = TestRunParser.parse(text)
        TestRunSummary(
            title = run.title,
            result = run.result,
            priority = run.priority,
            tags = run.tags.toSet(),
            environments = run.environment.toSet(),
        )
    } catch (_: Exception) {
        TestRunSummary("", RunResult.NOT_STARTED, null, emptySet(), emptySet())
    }
}
