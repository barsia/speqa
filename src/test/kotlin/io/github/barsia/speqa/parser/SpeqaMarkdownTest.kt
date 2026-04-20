package io.github.barsia.speqa.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeqaMarkdownTest {

    @Test
    fun `parseYamlMap drops bad line keeps rest`() {
        val yaml = "title: \"Test\"\ntags:-\npriority: major"
        val result = SpeqaMarkdown.parseYamlMap(yaml)
        assertEquals("Test", result["title"])
        assertEquals("major", result["priority"])
    }

    @Test
    fun `parseYamlMap returns empty map for all bad lines`() {
        val result = SpeqaMarkdown.parseYamlMap(":::invalid\n{{{broken")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `parseYamlMap handles blank input`() {
        assertEquals(emptyMap<String, Any?>(), SpeqaMarkdown.parseYamlMap(""))
        assertEquals(emptyMap<String, Any?>(), SpeqaMarkdown.parseYamlMap("   "))
    }

    @Test
    fun `parseYamlMap treats bare comma-separated as single string`() {
        // "tags: smoke, regression" is valid YAML — a scalar string
        val result = SpeqaMarkdown.parseYamlMap("tags: smoke, regression")
        assertEquals("smoke, regression", result["tags"])
    }

    @Test
    fun `parseTagList splits bare comma-separated tags`() {
        assertEquals(listOf("smoke", "regression"), SpeqaMarkdown.parseTagList("smoke, regression"))
    }

    @Test
    fun `parseYamlMap normalizes quoted comma-separated values`() {
        val result = SpeqaMarkdown.parseYamlMap("tags: \"1, 2, 3\", \"test\"")
        val tags = result["tags"]
        assertNotNull(tags)
        assertTrue("Expected list, got: $tags", tags is List<*>)
        assertEquals(listOf("1, 2, 3", "test"), tags)
    }

    @Test
    fun `parseYamlMap keeps unquoted comma-separated scalar on first pass`() {
        val result = SpeqaMarkdown.parseYamlMap("environment: test1, env20")
        assertEquals("test1, env20", result["environment"])
    }

    @Test
    fun `parseYamlMap does not normalize single value`() {
        val result = SpeqaMarkdown.parseYamlMap("title: hello world")
        assertEquals("hello world", result["title"])
    }

    @Test
    fun `parseYamlMap does not normalize already bracketed list`() {
        val result = SpeqaMarkdown.parseYamlMap("tags: [a, b, c]")
        val tags = result["tags"]
        assertEquals(listOf("a", "b", "c"), tags)
    }

    @Test
    fun `parseYamlMap handles valid yaml normally`() {
        val yaml = "title: \"Test\"\npriority: major\nstatus: draft"
        val result = SpeqaMarkdown.parseYamlMap(yaml)
        assertEquals("Test", result["title"])
        assertEquals("major", result["priority"])
        assertEquals("draft", result["status"])
    }

    @Test
    fun `splitFrontmatter returns body when no opening delimiter`() {
        val (frontmatter, body) = SpeqaMarkdown.splitFrontmatter("just body text")
        assertEquals("", frontmatter)
        assertEquals("just body text", body)
    }

    @Test
    fun `parseYamlMap drops bad line with indented children`() {
        val yaml = "title: \"Test\"\nenvironment:\n  - chrome\ntags:-\n  - broken child\npriority: major"
        val result = SpeqaMarkdown.parseYamlMap(yaml)
        assertEquals("Test", result["title"])
        assertEquals("major", result["priority"])
        assertNotNull(result["environment"])
    }
}
