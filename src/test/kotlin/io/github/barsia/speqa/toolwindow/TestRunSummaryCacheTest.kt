package io.github.barsia.speqa.toolwindow

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.barsia.speqa.model.Priority
import io.github.barsia.speqa.model.RunCase
import io.github.barsia.speqa.model.RunResult
import io.github.barsia.speqa.model.TestRun
import io.github.barsia.speqa.parser.TestRunSerializer

/**
 * Mirror of [TestCaseSummaryCacheTest] for [TestRunSummaryCache]: parsing into a summary and the
 * live-document-over-disk precedence that keeps the TRs tab in sync with unsaved result edits.
 */
class TestRunSummaryCacheTest : BasePlatformTestCase() {

    private fun run(
        title: String,
        result: RunResult,
        priority: Priority? = null,
        tags: List<String> = emptyList(),
        env: List<String> = emptyList(),
    ) = TestRun(
        title = title,
        result = result,
        cases = listOf(
            RunCase(caseId = 1, title = title, priority = priority, tags = tags, environment = env, result = result),
        ),
    )

    private fun trFile(path: String, testRun: TestRun): VirtualFile =
        myFixture.addFileToProject(path, TestRunSerializer.serialize(testRun)).virtualFile

    private fun setDocumentText(file: VirtualFile, text: String) {
        val document = FileDocumentManager.getInstance().getDocument(file)!!
        WriteCommandAction.runWriteCommandAction(project) { document.setText(text) }
    }

    fun testParsesMetadataIntoSummary() {
        val file = trFile(
            "tr/Login.tr.md",
            run("Login run", RunResult.PASSED, Priority.MAJOR, listOf("auth"), listOf("Chrome 120")),
        )
        val summary = TestRunSummaryCache().summaryFor(file)

        assertEquals("Login run", summary.title)
        assertEquals(RunResult.PASSED, summary.result)
        assertEquals(Priority.MAJOR, summary.priority)
        assertEquals(setOf("auth"), summary.tags)
        assertEquals(setOf("Chrome 120"), summary.environments)
    }

    fun testUnsavedDocumentEditsTakePrecedenceOverDisk() {
        val file = trFile("tr/Live.tr.md", run("Live", RunResult.PASSED))
        val cache = TestRunSummaryCache()
        assertEquals(RunResult.PASSED, cache.summaryFor(file).result)

        setDocumentText(file, TestRunSerializer.serialize(run("Live", RunResult.FAILED)))

        assertEquals(RunResult.FAILED, cache.summaryFor(file).result)
    }
}
