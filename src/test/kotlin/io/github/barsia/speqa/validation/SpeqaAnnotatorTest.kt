package io.github.barsia.speqa.validation

import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class SpeqaAnnotatorTest : BasePlatformTestCase() {

    override fun getTestDataPath(): String = "src/test/resources/testData/annotator"

    fun `test valid title shows no title warning`() {
        myFixture.configureByFile("validTitle.tc.md")
        val highlights = myFixture.doHighlighting(HighlightSeverity.WARNING)
        val titleWarnings = highlights.filter { it.description?.contains("title is not set") == true }
        assertTrue("Should have no title warning for 'test.tc.md', but got: $titleWarnings", titleWarnings.isEmpty())
    }

    fun `test untitled default shows title warning`() {
        myFixture.configureByFile("untitledDefault.tc.md")
        val highlights = myFixture.doHighlighting(HighlightSeverity.WARNING)
        val titleWarnings = highlights.filter { it.description?.contains("title is not set") == true }
        assertFalse("Should have title warning for 'Untitled Test Case'", titleWarnings.isEmpty())
    }

    fun `test empty title shows title warning`() {
        myFixture.configureByFile("emptyTitle.tc.md")
        val highlights = myFixture.doHighlighting(HighlightSeverity.WARNING)
        val titleWarnings = highlights.filter { it.description?.contains("title is not set") == true }
        assertFalse("Should have title warning for empty title", titleWarnings.isEmpty())
    }

    fun `test no steps shows warning`() {
        myFixture.configureByFile("noSteps.tc.md")
        val highlights = myFixture.doHighlighting(HighlightSeverity.WARNING)
        val stepWarnings = highlights.filter { it.description?.contains("no steps") == true }
        assertFalse("Should warn about missing steps", stepWarnings.isEmpty())
    }

    fun `test complete valid file has no title or step warnings`() {
        myFixture.configureByFile("completeValid.tc.md")
        val highlights = myFixture.doHighlighting(HighlightSeverity.WARNING)
        val titleWarnings = highlights.filter { it.description?.contains("title is not set") == true }
        val stepWarnings = highlights.filter { it.description?.contains("no steps") == true }
        assertTrue("Should have no title warning, but got: $titleWarnings", titleWarnings.isEmpty())
        assertTrue("Should have no step warning, but got: $stepWarnings", stepWarnings.isEmpty())
    }
}
