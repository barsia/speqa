package io.github.barsia.speqa.registry

import org.junit.Assert.assertEquals
import org.junit.Test

class SpeqaTagRegistryParseTest {

    @Test
    fun `parseYamlList handles scalar value`() {
        assertEquals(listOf("smoke"), SpeqaTagRegistry.parseYamlList("tags: smoke", "tags"))
    }

    @Test
    fun `parseYamlList handles quoted scalar`() {
        assertEquals(listOf("smoke test"), SpeqaTagRegistry.parseYamlList("tags: \"smoke test\"", "tags"))
    }

    @Test
    fun `parseYamlList keeps quoted comma scalar as one environment`() {
        assertEquals(
            listOf("Chrome 120, macOS 14"),
            SpeqaTagRegistry.parseYamlList("environment: \"Chrome 120, macOS 14\"", "environment"),
        )
    }

    @Test
    fun `parseYamlList keeps unquoted comma scalar as one environment`() {
        assertEquals(
            listOf("browser, staging"),
            SpeqaTagRegistry.parseYamlList("environment: browser, staging", "environment"),
        )
    }

    @Test
    fun `parseYamlList handles inline list`() {
        assertEquals(listOf("a", "b", "c"), SpeqaTagRegistry.parseYamlList("tags: [a, b, c]", "tags"))
    }

    @Test
    fun `parseYamlList handles block list`() {
        val fm = "tags:\n  - alpha\n  - beta"
        assertEquals(listOf("alpha", "beta"), SpeqaTagRegistry.parseYamlList(fm, "tags"))
    }

    @Test
    fun `parseYamlList handles quoted block list`() {
        val fm = "tags:\n  - \"smoke test\"\n  - \"regression\""
        assertEquals(listOf("smoke test", "regression"), SpeqaTagRegistry.parseYamlList(fm, "tags"))
    }

    @Test
    fun `parseYamlList handles comma-separated quoted values`() {
        assertEquals(
            listOf("a", "b"),
            SpeqaTagRegistry.parseYamlList("tags: \"a\", \"b\"", "tags"),
        )
    }

    @Test
    fun `parseYamlList handles comma-separated unquoted values`() {
        assertEquals(
            listOf("a", "b"),
            SpeqaTagRegistry.parseYamlList("tags: a, b", "tags"),
        )
    }

    @Test
    fun `parseYamlList returns empty for missing field`() {
        assertEquals(emptyList<String>(), SpeqaTagRegistry.parseYamlList("priority: major", "tags"))
    }

    @Test
    fun `parseYamlList returns empty for empty inline list`() {
        assertEquals(emptyList<String>(), SpeqaTagRegistry.parseYamlList("tags: []", "tags"))
    }

    @Test
    fun `parseYamlList skips blank items`() {
        assertEquals(
            listOf("a"),
            SpeqaTagRegistry.parseYamlList("tags: a, , ", "tags"),
        )
    }
}
