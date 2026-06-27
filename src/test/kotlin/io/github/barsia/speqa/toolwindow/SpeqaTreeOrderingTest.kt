package io.github.barsia.speqa.toolwindow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeqaTreeOrderingTest {

    private fun folder(name: String) = SpeqaTreeItem.Folder(name, name)
    private fun leaf(title: String) = SpeqaTreeItem.Leaf(title, title)

    @Test
    fun `folders come before leaves`() {
        val ordered = orderChildren(listOf(leaf("zzz"), folder("alpha")))
        assertTrue(ordered[0] is SpeqaTreeItem.Folder)
        assertTrue(ordered[1] is SpeqaTreeItem.Leaf)
    }

    @Test
    fun `folders sorted case-insensitively by name`() {
        val ordered = orderChildren(listOf(folder("Beta"), folder("alpha"), folder("Gamma")))
        assertEquals(listOf("alpha", "Beta", "Gamma"), ordered.map { it.payload })
    }

    @Test
    fun `leaves sorted by title with natural order`() {
        val ordered = orderChildren(listOf(leaf("Step 10"), leaf("Step 2"), leaf("Step 1")))
        assertEquals(listOf("Step 1", "Step 2", "Step 10"), ordered.map { it.payload })
    }

    @Test
    fun `test case file names recognized by tc-md suffix`() {
        assertTrue(isTestCaseFileName("login.tc.md"))
        assertFalse(isTestCaseFileName("notes.md"))
        assertFalse(isTestCaseFileName("run.tr.md"))
    }

    @Test
    fun `test run file names recognized by tr-md suffix`() {
        assertTrue(isTestRunFileName("login_2026-01-01_10-00-00.tr.md"))
        assertFalse(isTestRunFileName("notes.md"))
        assertFalse(isTestRunFileName("login.tc.md"))
    }
}
