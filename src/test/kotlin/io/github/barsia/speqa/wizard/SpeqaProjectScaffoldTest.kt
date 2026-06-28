package io.github.barsia.speqa.wizard

import io.github.barsia.speqa.parser.TestCaseParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeqaProjectScaffoldTest {

    /**
     * Guard: the starter test case the New Project wizard installs must be bundled
     * into the plugin jar and must parse. If its source file is renamed, moved, or
     * dropped from the processResources wiring, this classpath lookup returns null
     * and the test fails - catching a broken scaffold before release.
     */
    @Test
    fun `bundled starter test case is on the classpath and parses`() {
        val stream = SpeqaProjectScaffold::class.java
            .getResourceAsStream("/templates/${SpeqaProjectScaffold.BUNDLED_SAMPLE_RESOURCE}")
        assertNotNull(
            "Bundled starter test case must be on the classpath - check the processResources 'from' rule",
            stream,
        )

        val parsed = TestCaseParser.parse(stream!!.readBytes().toString(Charsets.UTF_8))
        assertEquals(1, parsed.id)
        assertTrue("starter scenario should parse into steps", parsed.steps.isNotEmpty())
    }
}
