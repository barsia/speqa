package io.github.barsia.speqa.editor.ui.steps

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.barsia.speqa.editor.ui.primitives.MarkdownEditablePane

class EditableBodyBlockSectionTest : BasePlatformTestCase() {

    fun `test forced setText sync updates text without emitting onCommit`() {
        var commitCount = 0
        val section = EditableBodyBlockSection(
            project = project,
            emptyLabel = "Description",
            onCommit = { commitCount++ },
        )
        section.setText("Preview edit")

        section.setText("Undo restored description", forceFocusedTextSync = true)

        assertEquals("Undo restored description", (section.flashTarget() as MarkdownEditablePane).text)
        assertEquals(0, commitCount)
    }
}
