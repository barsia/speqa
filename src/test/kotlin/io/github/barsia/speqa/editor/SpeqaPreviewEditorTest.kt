package io.github.barsia.speqa.editor

import io.github.barsia.speqa.model.TestStep
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeqaPreviewEditorTest {
    @Test
    fun `defers first structural step shrink for a new document text`() {
        assertTrue(
            SpeqaPreviewEditor.shouldDeferStepShrink(
                currentSteps = List(6) { TestStep(action = "Step ${it + 1}") },
                nextSteps = List(5) { TestStep(action = "Step ${it + 1}") },
                documentText = "Scenario:\n\n7.",
            ),
        )
    }

    @Test
    fun `applies structural step shrink when document does not end with incomplete marker`() {
        assertFalse(
            SpeqaPreviewEditor.shouldDeferStepShrink(
                currentSteps = listOf(TestStep(action = "Existing"), TestStep(action = "Removed")),
                nextSteps = listOf(TestStep(action = "Existing")),
                documentText = "Scenario:\n\n1. Existing",
            ),
        )
    }

    @Test
    fun `does not defer step append`() {
        assertFalse(
            SpeqaPreviewEditor.shouldDeferStepShrink(
                currentSteps = List(5) { TestStep(action = "Step ${it + 1}") },
                nextSteps = List(6) { TestStep(action = "Step ${it + 1}") },
                documentText = "Scenario:\n\n6.",
            ),
        )
    }

    @Test
    fun `detects trailing incomplete top level step marker`() {
        assertTrue(SpeqaPreviewEditor.hasTrailingIncompleteTopLevelStepMarker("Scenario:\n\n7. "))
    }

    @Test
    fun `detects trailing step number before dot is typed`() {
        assertTrue(SpeqaPreviewEditor.hasTrailingIncompleteTopLevelStepMarker("Scenario:\n\n7"))
    }

    @Test
    fun `detects trailing step marker typed on russian layout`() {
        assertTrue(SpeqaPreviewEditor.hasTrailingIncompleteTopLevelStepMarker("Scenario:\n\n7ю"))
    }

    @Test
    fun `ignores trailing nested numbered list marker`() {
        assertFalse(SpeqaPreviewEditor.hasTrailingIncompleteTopLevelStepMarker("Scenario:\n\n6. Existing\n   2. "))
    }

    @Test
    fun `defers transient empty tail step shrink near document end`() {
        val text = "Scenario:\n\n1. Existing"
        val current = listOf(TestStep(action = "Existing"), TestStep(action = ""))
        val next = listOf(TestStep(action = "Existing"))

        assertTrue(
            SpeqaPreviewEditor.isTransientEmptyTailStepShrink(
                currentSteps = current,
                nextSteps = next,
                lastMutationOffset = text.length,
                documentText = text,
            ),
        )
    }

    @Test
    fun `defers transient empty tail step shrink when marker fragment is appended to previous action`() {
        val text = "Scenario:\n\n1. Existing\n6ю"
        val current = listOf(TestStep(action = "Existing"), TestStep(action = ""))
        val next = listOf(TestStep(action = "Existing\n6ю"))

        assertTrue(
            SpeqaPreviewEditor.isTransientEmptyTailStepShrink(
                currentSteps = current,
                nextSteps = next,
                lastMutationOffset = text.length,
                documentText = text,
            ),
        )
    }

    @Test
    fun `defers transient empty tail step shrink when punctuation fragment is appended to previous action`() {
        val text = "Scenario:\n\n1. Existing\n/"
        val current = listOf(TestStep(action = "Existing"), TestStep(action = ""))
        val next = listOf(TestStep(action = "Existing\n/"))

        assertTrue(
            SpeqaPreviewEditor.isTransientEmptyTailStepShrink(
                currentSteps = current,
                nextSteps = next,
                lastMutationOffset = text.length,
                documentText = text,
            ),
        )
    }

    @Test
    fun `does not defer non-empty tail step shrink`() {
        val text = "Scenario:\n\n1. Existing"
        val current = listOf(TestStep(action = "Existing"), TestStep(action = "Removed"))
        val next = listOf(TestStep(action = "Existing"))

        assertFalse(
            SpeqaPreviewEditor.isTransientEmptyTailStepShrink(
                currentSteps = current,
                nextSteps = next,
                lastMutationOffset = text.length,
                documentText = text,
            ),
        )
    }

    @Test
    fun `does not defer empty tail step shrink away from document end`() {
        val text = "Description ".repeat(20) + "\n\nScenario:\n\n1. Existing"
        val current = listOf(TestStep(action = "Existing"), TestStep(action = ""))
        val next = listOf(TestStep(action = "Existing"))

        assertFalse(
            SpeqaPreviewEditor.isTransientEmptyTailStepShrink(
                currentSteps = current,
                nextSteps = next,
                lastMutationOffset = 0,
                documentText = text,
            ),
        )
    }
}
