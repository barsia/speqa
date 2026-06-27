package io.github.barsia.speqa.run

import io.github.barsia.speqa.editor.RunImportOptions
import io.github.barsia.speqa.editor.runcreation.CreateMultiCaseRunWriter
import io.github.barsia.speqa.model.TestCase
import io.github.barsia.speqa.model.TestStep
import org.junit.Assert.assertEquals
import org.junit.Test

class CreateMultiCaseRunWiringTest {

    @Test
    fun `builds a multi-case run from selected test cases`() {
        val tcA = TestCase(
            id = 5,
            title = "A",
            steps = listOf(
                TestStep(action = "A step 1", expected = "A expected 1"),
                TestStep(action = "A step 2", expected = "A expected 2"),
            ),
        )
        val tcB = TestCase(
            id = 8,
            title = "B",
            steps = listOf(
                TestStep(action = "B step 1", expected = "B expected 1"),
            ),
        )
        val run = CreateMultiCaseRunWriter.buildMultiCaseRun(
            sources = listOf(
                TestRunSupport.SourceCase(tcA, "test-cases/a.tc.md"),
                TestRunSupport.SourceCase(tcB, "test-cases/b.tc.md"),
            ),
            targetDirectoryPath = "test-runs",
            importOptions = RunImportOptions(importTags = true),
            runner = "alice",
            title = "High - 2026-06-27 14:30",
        )
        assertEquals(2, run.cases.size)
        assertEquals("High - 2026-06-27 14:30", run.title)
        assertEquals(listOf(5, 8), run.cases.map { it.caseId })
        assertEquals(2, run.cases[0].stepResults.size)
    }
}
