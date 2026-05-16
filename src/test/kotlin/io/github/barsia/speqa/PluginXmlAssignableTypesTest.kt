package io.github.barsia.speqa

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.platform.DirectoryProjectGenerator
import com.intellij.refactoring.rename.RenameInputValidator
import com.intellij.refactoring.rename.RenamePsiElementProcessorBase
import io.github.barsia.speqa.actions.CreateTestCaseAction
import io.github.barsia.speqa.refactoring.SpeqaRenameInputValidator
import io.github.barsia.speqa.refactoring.SpeqaRenamePsiFileProcessor
import io.github.barsia.speqa.wizard.SpeqaProjectGenerator
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginXmlAssignableTypesTest {
    @Test
    fun `plugin xml action and extension implementations match their IntelliJ extension types`() {
        assertTrue(AnAction::class.java.isAssignableFrom(CreateTestCaseAction::class.java))
        assertTrue(DirectoryProjectGenerator::class.java.isAssignableFrom(SpeqaProjectGenerator::class.java))
        assertTrue(RenamePsiElementProcessorBase::class.java.isAssignableFrom(SpeqaRenamePsiFileProcessor::class.java))
        assertTrue(RenameInputValidator::class.java.isAssignableFrom(SpeqaRenameInputValidator::class.java))
    }

    @Test
    fun `plugin declares lang module for refactoring templates and project generator APIs`() {
        val pluginXml = javaClass.classLoader.getResource("META-INF/plugin.xml")!!.readText()

        assertTrue(pluginXml.contains("<depends>com.intellij.modules.lang</depends>"))
    }
}
