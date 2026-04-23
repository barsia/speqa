package io.github.barsia.speqa.run

import com.intellij.openapi.ui.ComboBox
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import io.github.barsia.speqa.SpeqaBundle
import io.github.barsia.speqa.model.RunResult
import io.github.barsia.speqa.model.StepResult
import io.github.barsia.speqa.model.StepVerdict
import io.github.barsia.speqa.model.TestRun
import java.awt.Component
import java.awt.Container

class TestRunPanelPlatformTest : BasePlatformTestCase() {

    fun `test updateFrom does not emit onChange during runner sync`() {
        var changeCount = 0
        val panel = TestRunPanel(
            project = project,
            file = null,
            sourceCaseTitle = "Run title",
            onChange = { changeCount++ },
        )

        panel.updateFrom(TestRun(runner = "Alice"))
        panel.updateFrom(TestRun(runner = "Bob"))

        val runnerField = findComponents(panel, JBTextField::class.java)
            .single { it.emptyText.text == SpeqaBundle.message("placeholder.runner") }

        assertEquals(0, changeCount)
        assertEquals("Bob", runnerField.text)
    }

    fun `test updateFrom refreshes step verdict and comment changes from the model`() {
        var changeCount = 0
        val panel = TestRunPanel(
            project = project,
            file = null,
            sourceCaseTitle = "Run title",
            onChange = { changeCount++ },
        )
        val initial = TestRun(
            result = RunResult.IN_PROGRESS,
            stepResults = listOf(StepResult(action = "Do something", expected = "See result")),
        )
        val updated = initial.copy(
            stepResults = listOf(
                initial.stepResults.single().copy(
                    verdict = StepVerdict.PASSED,
                    comment = "Looks good",
                ),
            ),
        )

        panel.updateFrom(initial)
        panel.updateFrom(updated)

        val verdictCombo = findComponents(panel, ComboBox::class.java)
            .first { it.selectedItem is StepVerdict }
        val commentArea = findComponents(panel, JBTextArea::class.java).single()

        assertEquals(0, changeCount)
        assertEquals(StepVerdict.PASSED, verdictCombo.selectedItem)
        assertEquals("Looks good", commentArea.text)
    }

    private fun <T : Component> findComponents(root: Component, type: Class<T>): List<T> {
        val matches = mutableListOf<T>()
        if (type.isInstance(root)) {
            matches += type.cast(root)
        }
        if (root is Container) {
            root.components.forEach { child ->
                matches += findComponents(child, type)
            }
        }
        return matches
    }
}
