package io.github.barsia.speqa.editor.ui.primitives

import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Reproduces the bug: pressing Enter inside a numbered list in the Description /
 * Preconditions editor (a [MarkdownEditablePane], backed by an EditorEx) does
 * nothing, so a Preconditions list cannot be split into a new item.
 */
class MarkdownEnterHandlerTest : BasePlatformTestCase() {

    fun `test Enter continues a numbered list and writes the change to the document`() {
        myFixture.configureByText("a.md", "1. first\n2. second<caret>")

        val handled = MarkdownEnterHandler.apply(myFixture.editor, project)

        assertTrue("Enter should be handled inside a numbered list", handled)
        assertEquals("1. first\n2. second\n3. ", myFixture.editor.document.text)
    }

    fun `test Enter splits a list item at the caret in the middle of a line`() {
        myFixture.configureByText("a.md", "1. first\n2. built<caret>installed")

        val handled = MarkdownEnterHandler.apply(myFixture.editor, project)

        assertTrue(handled)
        assertEquals("1. first\n2. built\n3. installed", myFixture.editor.document.text)
    }

    fun `test Enter outside any list is not handled`() {
        myFixture.configureByText("a.md", "plain text<caret>")

        assertFalse(MarkdownEnterHandler.apply(myFixture.editor, project))
    }
}
