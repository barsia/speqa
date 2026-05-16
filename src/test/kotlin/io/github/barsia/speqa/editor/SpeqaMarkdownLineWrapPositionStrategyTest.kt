package io.github.barsia.speqa.editor

import org.junit.Assert.assertTrue
import org.junit.Test

class SpeqaMarkdownLineWrapPositionStrategyTest {
    @Test
    fun `line wrap strategy keeps soft wraps but avoids markdown psi wrap strategy for speqa files`() {
        val source = source("src/main/kotlin/io/github/barsia/speqa/editor/SpeqaMarkdownLineWrapPositionStrategy.kt")
        val pluginXml = source("src/main/resources/META-INF/plugin.xml")

        assertTrue(pluginXml.contains("<lang.lineWrapStrategy language=\"Markdown\""))
        assertTrue(pluginXml.contains("implementationClass=\"io.github.barsia.speqa.editor.SpeqaMarkdownLineWrapPositionStrategy\""))
        assertTrue(pluginXml.contains("order=\"first\""))
        assertTrue(source.contains("class SpeqaMarkdownLineWrapPositionStrategy : LineWrapPositionStrategy"))
        assertTrue(source.contains("private val generic = GenericLineWrapPositionStrategy()"))
        assertTrue(source.contains("private val markdown = MarkdownLineWrapPositionStrategy()"))
        assertTrue(source.contains("SpeqaDefaults.speqaExtension(fileName) != null"))
        assertTrue(source.contains("generic else markdown"))
        assertTrue(!source.contains("isUseSoftWraps = false"))
    }

    private fun source(path: String): String = java.io.File(path).readText()
}
