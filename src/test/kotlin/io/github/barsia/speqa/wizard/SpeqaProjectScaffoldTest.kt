package io.github.barsia.speqa.wizard

import io.github.barsia.speqa.parser.TestCaseParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeqaProjectScaffoldTest {

    @Test
    fun `bundled sample test case parses into the expected case`() {
        val parsed = TestCaseParser.parse(SpeqaProjectScaffold.SAMPLE_TEST_CASE)

        assertEquals(1, parsed.id)
        assertEquals("Login with valid credentials", parsed.title)
        // The sample's Scenario must round-trip into real steps, not be silently swallowed
        // by a future parser change — this is the new user's very first file.
        assertTrue("sample scenario should parse into steps", parsed.steps.isNotEmpty())
    }
}
