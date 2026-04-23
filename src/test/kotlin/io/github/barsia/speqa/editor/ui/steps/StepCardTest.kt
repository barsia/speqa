package io.github.barsia.speqa.editor.ui.steps

import io.github.barsia.speqa.model.TestStep
import org.junit.Assert.assertEquals
import org.junit.Test

class StepCardTest {

    @Test
    fun `setStep does not emit onChange during programmatic action sync`() {
        var changeCount = 0
        val card = StepCard(
            initialStep = TestStep(action = "First action", expected = "First expected"),
            initialIndex = 0,
            project = null,
            tcFile = null,
            onChange = { changeCount++ },
            onDelete = {},
        )

        card.setStep(TestStep(action = "Updated action", expected = "First expected"))

        assertEquals(0, changeCount)
    }
}
