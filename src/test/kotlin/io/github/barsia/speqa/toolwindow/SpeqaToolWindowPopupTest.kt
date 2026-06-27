package io.github.barsia.speqa.toolwindow

import io.github.barsia.speqa.editor.ui.chips.MetadataScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks the SpeQA tool-window context-menu contract that is pure data: which items each tab
 * offers and which node selection each item is shown for. The action wiring itself (platform
 * APIs, Swing) is covered by manual smoke; this guards the parts that silently regress.
 */
class SpeqaToolWindowPopupTest {

    @Test
    fun `test cases tab offers open, run, new test case, and folder-scoped create run in order`() {
        assertEquals(
            listOf(
                SpeqaPopupItem.OPEN_LEAF,
                SpeqaPopupItem.RUN_TEST_CASE,
                SpeqaPopupItem.NEW_TEST_CASE,
                SpeqaPopupItem.CREATE_RUN_FROM_FOLDER,
            ),
            speqaPopupItems(MetadataScope.TEST_CASES),
        )
    }

    @Test
    fun `test runs tab offers only open and create run into folder`() {
        assertEquals(
            listOf(SpeqaPopupItem.OPEN_LEAF, SpeqaPopupItem.CREATE_RUN_IN_FOLDER),
            speqaPopupItems(MetadataScope.TEST_RUNS),
        )
    }

    @Test
    fun `test runs tab never offers run test case or new test case`() {
        val items = speqaPopupItems(MetadataScope.TEST_RUNS)
        assertFalse(SpeqaPopupItem.RUN_TEST_CASE in items)
        assertFalse(SpeqaPopupItem.NEW_TEST_CASE in items)
    }

    @Test
    fun `open and run are leaf-only`() {
        assertEquals(setOf(SpeqaPopupNodeKind.LEAF), SpeqaPopupItem.OPEN_LEAF.visibleFor)
        assertEquals(setOf(SpeqaPopupNodeKind.LEAF), SpeqaPopupItem.RUN_TEST_CASE.visibleFor)
    }

    @Test
    fun `new test case and create run are folder-only`() {
        assertEquals(setOf(SpeqaPopupNodeKind.FOLDER), SpeqaPopupItem.NEW_TEST_CASE.visibleFor)
        assertEquals(setOf(SpeqaPopupNodeKind.FOLDER), SpeqaPopupItem.CREATE_RUN_FROM_FOLDER.visibleFor)
        assertEquals(setOf(SpeqaPopupNodeKind.FOLDER), SpeqaPopupItem.CREATE_RUN_IN_FOLDER.visibleFor)
    }

    @Test
    fun `no item is shown for an empty selection`() {
        assertTrue(SpeqaPopupItem.entries.none { SpeqaPopupNodeKind.NONE in it.visibleFor })
    }
}
