package io.github.barsia.speqa.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TestCaseTest {
    @Test
    fun `default test case has null id`() {
        val testCase = TestCase()
        assertNull(testCase.id)
    }

    @Test
    fun `default test case has expected defaults`() {
        val testCase = TestCase()

        assertEquals("Untitled Test Case", testCase.title)
        assertNull(testCase.priority)
        assertNull(testCase.status)
        assertNull(testCase.environment)
        assertNull(testCase.tags)
        assertTrue(testCase.bodyBlocks.isEmpty())
        assertTrue(testCase.steps.isEmpty())
    }

    @Test
    fun `test case with body blocks`() {
        val testCase = TestCase(
            bodyBlocks = listOf(
                DescriptionBlock(markdown = "This test verifies login functionality."),
                PreconditionsBlock(markdown = "- User account exists\n- User is on login page"),
            ),
        )

        assertEquals(2, testCase.bodyBlocks.size)
        assertTrue(testCase.bodyBlocks[0] is DescriptionBlock)
        assertEquals("This test verifies login functionality.", testCase.bodyBlocks[0].markdown)
        assertTrue(testCase.bodyBlocks[1] is PreconditionsBlock)
        assertEquals("- User account exists\n- User is on login page", testCase.bodyBlocks[1].markdown)
    }

    @Test
    fun `step with expected result`() {
        val step = TestStep(action = "Click button", expected = "Page loads")

        assertEquals("Click button", step.action)
        assertEquals("Page loads", step.expected)
        assertEquals(1, step.expectedGroupSize)
    }

    @Test
    fun `step without expected result`() {
        val step = TestStep(action = "Click button")

        assertEquals("Click button", step.action)
        assertNull(step.expected)
        assertEquals(1, step.expectedGroupSize)
    }

    @Test
    fun `priority ordering`() {
        assertTrue(Priority.CRITICAL.ordinal < Priority.MAJOR.ordinal)
        assertTrue(Priority.MAJOR.ordinal < Priority.NORMAL.ordinal)
        assertTrue(Priority.NORMAL.ordinal < Priority.LOW.ordinal)
    }

    @Test
    fun `priority fromString is case insensitive`() {
        assertEquals(Priority.MAJOR, Priority.fromString("major"))
        assertEquals(Priority.MAJOR, Priority.fromString("MAJOR"))
        assertEquals(Priority.MAJOR, Priority.fromString("Major"))
        assertEquals(Priority.NORMAL, Priority.fromString("unknown"))
    }

    @Test
    fun `status fromString is case insensitive`() {
        assertEquals(Status.DRAFT, Status.fromString("draft"))
        assertEquals(Status.READY, Status.fromString("ready"))
        assertEquals(Status.DEPRECATED, Status.fromString("deprecated"))
        assertEquals(Status.DRAFT, Status.fromString("unknown"))
    }

    @Test
    fun `stepVerdict NONE has empty label`() {
        assertEquals("", StepVerdict.NONE.label)
    }

    @Test
    fun `stepVerdict BLOCKED has blocked label`() {
        assertEquals("blocked", StepVerdict.BLOCKED.label)
    }

    @Test
    fun `stepVerdict fromString returns NONE for empty`() {
        assertEquals(StepVerdict.NONE, StepVerdict.fromString(""))
    }

    @Test
    fun `stepVerdict fromString returns NONE for unknown`() {
        assertEquals(StepVerdict.NONE, StepVerdict.fromString("garbage"))
    }

    @Test
    fun `stepVerdict fromString returns BLOCKED`() {
        assertEquals(StepVerdict.BLOCKED, StepVerdict.fromString("blocked"))
    }
}
