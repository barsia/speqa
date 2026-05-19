package io.github.barsia.speqa.editor.ui.steps

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.barsia.speqa.model.TestStep

class StepCardTest : BasePlatformTestCase() {

    fun `test setStep does not emit onChange during programmatic action sync`() {
        var changeCount = 0
        val card = StepCard(
            initialStep = TestStep(action = "First action", expected = "First expected"),
            initialIndex = 0,
            project = project,
            tcFile = null,
            onChange = { changeCount++ },
            onDelete = {},
        )

        card.setStep(TestStep(action = "Updated action", expected = "First expected"))

        assertEquals(0, changeCount)
    }

    fun `test forced setStep sync updates action without emitting onChange`() {
        var changeCount = 0
        val card = StepCard(
            initialStep = TestStep(action = "Preview edit", expected = "Preview expected"),
            initialIndex = 0,
            project = project,
            tcFile = null,
            onChange = { changeCount++ },
            onDelete = {},
        )

        card.setStep(
            TestStep(action = "Undo restored action", expected = "Preview expected"),
            forceFocusedTextSync = true,
        )

        assertEquals("Undo restored action", card.actionArea.text)
        assertEquals(0, changeCount)
    }
}
