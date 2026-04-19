package io.github.barsia.speqa.refactoring

import com.intellij.psi.PsiFile
import com.intellij.refactoring.rename.RenamePsiElementProcessor
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class SpeqaRenameTest : BasePlatformTestCase() {

    // --- SpeqaRenamePsiFileProcessor.canProcessElement ---

    fun `test processor accepts tc_md file`() {
        val file = myFixture.configureByText("login-flow.tc.md", "# test")
        val processor = findSpeqaProcessor(file)
        assertNotNull("SpeqaRenamePsiFileProcessor should handle .tc.md", processor)
    }

    fun `test processor accepts tr_md file`() {
        val file = myFixture.configureByText("login-flow_run.tr.md", "# run")
        val processor = findSpeqaProcessor(file)
        assertNotNull("SpeqaRenamePsiFileProcessor should handle .tr.md", processor)
    }

    fun `test processor ignores plain markdown`() {
        val file = myFixture.configureByText("readme.md", "# readme")
        val processor = findSpeqaProcessor(file)
        assertNull("SpeqaRenamePsiFileProcessor should not handle .md", processor)
    }

    fun `test processor ignores non-markdown`() {
        val file = myFixture.configureByText("data.json", "{}")
        val processor = findSpeqaProcessor(file)
        assertNull("SpeqaRenamePsiFileProcessor should not handle .json", processor)
    }

    // --- SpeqaRenameInputValidator ---

    fun `test validator accepts valid tc_md rename`() {
        val file = myFixture.configureByText("login.tc.md", "# test")
        val validator = SpeqaRenameInputValidator()
        assertTrue(validator.isInputValid("new-name.tc.md", file, com.intellij.util.ProcessingContext()))
    }

    fun `test validator rejects tc_md renamed to plain md`() {
        val file = myFixture.configureByText("login.tc.md", "# test")
        val validator = SpeqaRenameInputValidator()
        assertFalse(validator.isInputValid("new-name.md", file, com.intellij.util.ProcessingContext()))
    }

    fun `test validator accepts valid tr_md rename`() {
        val file = myFixture.configureByText("run.tr.md", "# run")
        val validator = SpeqaRenameInputValidator()
        assertTrue(validator.isInputValid("new-run.tr.md", file, com.intellij.util.ProcessingContext()))
    }

    fun `test validator rejects tr_md renamed to plain md`() {
        val file = myFixture.configureByText("run.tr.md", "# run")
        val validator = SpeqaRenameInputValidator()
        assertFalse(validator.isInputValid("new-run.md", file, com.intellij.util.ProcessingContext()))
    }

    fun `test validator ignores non-speqa files`() {
        val file = myFixture.configureByText("readme.md", "# readme")
        val validator = SpeqaRenameInputValidator()
        assertTrue(validator.isInputValid("anything.txt", file, com.intellij.util.ProcessingContext()))
    }

    fun `test getErrorMessage returns null for valid tc_md name`() {
        val validator = SpeqaRenameInputValidator()
        assertNull(validator.getErrorMessage("new.tc.md", project))
    }

    fun `test getErrorMessage returns null for valid tr_md name`() {
        val validator = SpeqaRenameInputValidator()
        assertNull(validator.getErrorMessage("new.tr.md", project))
    }

    fun `test getErrorMessage returns error for invalid name`() {
        val validator = SpeqaRenameInputValidator()
        val msg = validator.getErrorMessage("new.md", project)
        assertNotNull("Should return error message", msg)
        assertTrue("Should mention extension", msg!!.contains(".tr.md"))
    }

    private fun findSpeqaProcessor(file: PsiFile): SpeqaRenamePsiFileProcessor? {
        return RenamePsiElementProcessor.allForElement(file)
            .filterIsInstance<SpeqaRenamePsiFileProcessor>()
            .firstOrNull()
    }
}
