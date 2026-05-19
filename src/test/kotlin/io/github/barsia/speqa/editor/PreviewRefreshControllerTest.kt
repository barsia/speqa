package io.github.barsia.speqa.editor

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewRefreshControllerTest {

    @Test
    fun `preview undo requests immediate refresh and force focused text sync`() {
        val controller = PreviewRefreshController()

        val timing = controller.requestRefresh(fromPreviewUndoRedo = true)

        assertEquals(PreviewRefreshTiming.IMMEDIATE, timing)
        assertTrue(controller.consumeForceFocusedTextSync())
        assertFalse(controller.consumeForceFocusedTextSync())
    }

    @Test
    fun `normal document change requests debounced refresh`() {
        val controller = PreviewRefreshController()

        val timing = controller.requestRefresh(fromPreviewUndoRedo = false)

        assertEquals(PreviewRefreshTiming.DEBOUNCED, timing)
        assertFalse(controller.consumeForceFocusedTextSync())
    }

    @Test
    fun `second change while immediate refresh is pending does not schedule duplicate work`() {
        val controller = PreviewRefreshController()

        assertEquals(PreviewRefreshTiming.IMMEDIATE, controller.requestRefresh(fromPreviewUndoRedo = true))
        assertEquals(PreviewRefreshTiming.NONE, controller.requestRefresh(fromPreviewUndoRedo = true))
        assertEquals(PreviewRefreshTiming.NONE, controller.requestRefresh(fromPreviewUndoRedo = false))
        controller.markRefreshCompleted()
        assertEquals(PreviewRefreshTiming.DEBOUNCED, controller.requestRefresh(fromPreviewUndoRedo = false))
    }
}
