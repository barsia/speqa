package io.github.barsia.speqa.editor.ui

import io.github.barsia.speqa.model.StepResult
import io.github.barsia.speqa.model.TestStep
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TestCasePanelTest {

    // ── isSingleStepAppend ────────────────────────────────────────────────────

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

    // ── stepsStructurallyChanged ──────────────────────────────────────────────

    @Test
    fun `structural change when step added`() {
        val old = listOf(TestStep(action = "Step 1"))
        val new = old + TestStep(action = "Step 2")
        assertTrue(TestCasePanel.stepsStructurallyChanged(old, new))
    }

    @Test
    fun `structural change when step removed`() {
        val old = listOf(TestStep(action = "Step 1"), TestStep(action = "Step 2"))
        val new = listOf(TestStep(action = "Step 1"))
        assertTrue(TestCasePanel.stepsStructurallyChanged(old, new))
    }

    @Test
    fun `no structural change when only action text changes`() {
        val step = TestStep(action = "Original")
        val updated = step.copy(action = "Updated")
        assertFalse(TestCasePanel.stepsStructurallyChanged(listOf(step), listOf(updated)))
    }

    @Test
    fun `no structural change when expected goes from text to null`() {
        // Regression: clearing Expected used to trigger a full rebuild because
        // the expected==null transition was incorrectly treated as structural.
        val step = TestStep(action = "Step", expected = "Expected result")
        val cleared = step.copy(expected = null)
        assertFalse(TestCasePanel.stepsStructurallyChanged(listOf(step), listOf(cleared)))
    }

    @Test
    fun `no structural change when expected goes from null to text`() {
        val step = TestStep(action = "Step", expected = null)
        val filled = step.copy(expected = "New result")
        assertFalse(TestCasePanel.stepsStructurallyChanged(listOf(step), listOf(filled)))
    }

    @Test
    fun `no structural change when uid changes due to copy`() {
        // copy() always creates a new uid; structural detection must not use uid.
        val step = TestStep(action = "Step", expected = "Result")
        val copy = step.copy(action = "Step")  // same content, new uid
        assertFalse(TestCasePanel.stepsStructurallyChanged(listOf(step), listOf(copy)))
    }

    // ── runStepsStructurallyChanged ───────────────────────────────────────────

    @Test
    fun `run structural change when result added`() {
        val old = listOf(StepResult())
        val new = old + StepResult()
        assertTrue(TestCasePanel.runStepsStructurallyChanged(old, new))
    }

    @Test
    fun `run structural change when result removed`() {
        val old = listOf(StepResult(), StepResult())
        val new = listOf(StepResult())
        assertTrue(TestCasePanel.runStepsStructurallyChanged(old, new))
    }

    @Test
    fun `no run structural change when only action text changes`() {
        val old = listOf(StepResult(action = "Original"))
        val new = listOf(StepResult(action = "Updated"))
        assertFalse(TestCasePanel.runStepsStructurallyChanged(old, new))
    }

    @Test
    fun `no run structural change when expected cleared`() {
        // Regression: clearing expected in run mode triggered a full rebuild.
        val old = listOf(StepResult(expected = "Result"))
        val new = listOf(StepResult(expected = ""))
        assertFalse(TestCasePanel.runStepsStructurallyChanged(old, new))
    }
}
