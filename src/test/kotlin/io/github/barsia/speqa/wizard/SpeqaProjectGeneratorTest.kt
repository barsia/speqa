package io.github.barsia.speqa.wizard

import com.intellij.platform.DirectoryProjectGenerator
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeqaProjectGeneratorTest {
    @Test
    fun `legacy project generator is assignable to directory project generator extension point`() {
        assertTrue(DirectoryProjectGenerator::class.java.isAssignableFrom(SpeqaProjectGenerator::class.java))
    }
}
