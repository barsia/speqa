package io.github.barsia.speqa.run

import io.github.barsia.speqa.model.DescriptionBlock
import io.github.barsia.speqa.model.Priority
import io.github.barsia.speqa.model.RunCase
import io.github.barsia.speqa.model.TestRun
import io.github.barsia.speqa.parser.TestRunParser
import io.github.barsia.speqa.parser.TestRunSerializer
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins the serializer round-trip contract that makes full re-serialization the safe save path for
 * sectioned runs (the editor no longer patches runs surgically).
 */
class RunFullReserializeEditTest {
    @Test
    fun `editing tags then re-serializing preserves the edit for a single-case run`() {
        val run = TestRun(
            id = 7, title = "Login", runner = "alice",
            cases = listOf(RunCase(caseId = 5, title = "Sign in",
                priority = Priority.MAJOR, tags = listOf("smoke"))),
        )
        val edited = run.withSingleCase { it.copy(tags = listOf("smoke", "regression")) }
        val reloaded = TestRunParser.parse(TestRunSerializer.serialize(edited))
        assertEquals(listOf("smoke", "regression"), reloaded.cases.single().tags)
    }

    @Test
    fun `editing description then re-serializing keeps the case marker and metadata`() {
        val run = TestRun(id = 7, title = "Login",
            cases = listOf(RunCase(caseId = 5, title = "Sign in", priority = Priority.MAJOR,
                tags = listOf("smoke"),
                bodyBlocks = listOf(DescriptionBlock("Original.")))))
        val edited = run.withSingleCase {
            it.copy(bodyBlocks = listOf(DescriptionBlock("Edited.")))
        }
        val reloaded = TestRunParser.parse(TestRunSerializer.serialize(edited))
        val case = reloaded.cases.single()
        assertEquals(5, case.caseId)
        assertEquals(Priority.MAJOR, case.priority)
        assertEquals("Edited.", (case.bodyBlocks.single() as DescriptionBlock).markdown)
    }
}
