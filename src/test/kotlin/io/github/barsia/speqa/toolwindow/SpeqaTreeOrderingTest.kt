package io.github.barsia.speqa.toolwindow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeqaTreeOrderingTest {

    private fun folder(name: String) = SpeqaTreeItem.Folder(name, name)
    private fun case(title: String) = SpeqaTreeItem.TestCase(title, title)

    @Test
    fun `folders come before test cases`() {
        val ordered = orderChildren(listOf(case("zzz"), folder("alpha")))
        assertTrue(ordered[0] is SpeqaTreeItem.Folder)
        assertTrue(ordered[1] is SpeqaTreeItem.TestCase)
    }

    @Test
    fun `folders sorted case-insensitively by name`() {
        val ordered = orderChildren(listOf(folder("Beta"), folder("alpha"), folder("Gamma")))
        assertEquals(listOf("alpha", "Beta", "Gamma"), ordered.map { it.payload })
    }

    @Test
    fun `test cases sorted by title with natural order`() {
        val ordered = orderChildren(listOf(case("Step 10"), case("Step 2"), case("Step 1")))
        assertEquals(listOf("Step 1", "Step 2", "Step 10"), ordered.map { it.payload })
    }

    @Test
    fun `test case file names recognized by tc-md suffix`() {
        assertTrue(isTestCaseFileName("login.tc.md"))
        assertFalse(isTestCaseFileName("notes.md"))
        assertFalse(isTestCaseFileName("run.tr.md"))
    }
}
