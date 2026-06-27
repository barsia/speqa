package io.github.barsia.speqa.toolwindow

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.barsia.speqa.model.Priority
import io.github.barsia.speqa.model.Status
import io.github.barsia.speqa.model.TestCase
import io.github.barsia.speqa.parser.TestCaseSerializer

/**
 * Covers the parsing/default/precedence contract of [TestCaseSummaryCache], which feeds the
 * tool-window leaf labels and filters. Content is produced via [TestCaseSerializer] so the
 * fixtures stay valid as the file format evolves.
 */
class TestCaseSummaryCacheTest : BasePlatformTestCase() {

    private fun tcFile(path: String, testCase: TestCase): VirtualFile =
        myFixture.addFileToProject(path, TestCaseSerializer.serialize(testCase)).virtualFile

    private fun setDocumentText(file: VirtualFile, text: String) {
        val document = FileDocumentManager.getInstance().getDocument(file)!!
        WriteCommandAction.runWriteCommandAction(project) { document.setText(text) }
    }

    fun testParsesMetadataIntoSummary() {
        val file = tcFile(
            "tc/Login.tc.md",
            TestCase(
                title = "Login works",
                status = Status.READY,
                priority = Priority.MAJOR,
                tags = listOf("auth", "smoke"),
                environment = listOf("Chrome 120"),
            ),
        )
        val summary = TestCaseSummaryCache().summaryFor(file)

        assertEquals("Login works", summary.title)
        assertEquals(Status.READY, summary.status)
        assertEquals(Priority.MAJOR, summary.priority)
        assertEquals(setOf("auth", "smoke"), summary.tags)
        assertEquals(setOf("Chrome 120"), summary.environments)
    }

    fun testMissingStatusAndPriorityFallBackToDefaults() {
        val file = tcFile("tc/Bare.tc.md", TestCase(title = "Bare case"))
        val summary = TestCaseSummaryCache().summaryFor(file)

        assertEquals(Status.DRAFT, summary.status)
        assertEquals(Priority.NORMAL, summary.priority)
        assertTrue(summary.tags.isEmpty())
        assertTrue(summary.environments.isEmpty())
    }

    fun testUnsavedDocumentEditsTakePrecedenceOverDisk() {
        val file = tcFile("tc/Live.tc.md", TestCase(title = "Live", status = Status.READY))
        val cache = TestCaseSummaryCache()
        assertEquals(Status.READY, cache.summaryFor(file).status)

        // Edit only the in-memory document (never saved); the cache must reflect the live edit.
        setDocumentText(file, TestCaseSerializer.serialize(TestCase(title = "Live", status = Status.DEPRECATED)))

        assertEquals(Status.DEPRECATED, cache.summaryFor(file).status)
    }
}
