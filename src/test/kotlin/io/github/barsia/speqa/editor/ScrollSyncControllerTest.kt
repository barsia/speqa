package io.github.barsia.speqa.editor

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrollSyncControllerTest {
    @Test
    fun `editor reflow without vertical offset change does not mirror to preview`() {
        assertFalse(ScrollSyncController.shouldMirrorEditorVisibleAreaChange(oldY = 240, newY = 240))
    }

    @Test
    fun `editor vertical offset change mirrors to preview`() {
        assertTrue(ScrollSyncController.shouldMirrorEditorVisibleAreaChange(oldY = 240, newY = 280))
    }

    @Test
    fun `initial visible area event mirrors to preview`() {
        assertTrue(ScrollSyncController.shouldMirrorEditorVisibleAreaChange(oldY = null, newY = 0))
    }
}
