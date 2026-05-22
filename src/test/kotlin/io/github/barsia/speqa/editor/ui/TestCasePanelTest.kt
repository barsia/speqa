package io.github.barsia.speqa.editor.ui

import io.github.barsia.speqa.model.TestStep
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TestCasePanelTest {
    @Test
    fun `single step append preserves existing steps`() {
        val old = listOf(
            TestStep(action = "First"),
            TestStep(action = "Second"),
        )
        val new = old + TestStep(action = "")

        assertTrue(TestCasePanel.isSingleStepAppend(old, new))
    }

    @Test
    fun `insert in the middle is not single step append`() {
        val old = listOf(
            TestStep(action = "First"),
            TestStep(action = "Third"),
        )
        val new = listOf(
            TestStep(action = "First"),
            TestStep(action = "Second"),
            TestStep(action = "Third"),
        )

        assertFalse(TestCasePanel.isSingleStepAppend(old, new))
    }
}
