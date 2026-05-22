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

    @Test
    fun `bidirectional suppression blocks both scroll sync directions during relayout`() {
        val state = ScrollSyncController.SuppressionState()

        state.suppressBothDirections(nowMillis = 1_000L)

        assertTrue(state.isEditorToPanelSuppressed(nowMillis = 1_100L))
        assertTrue(state.isPanelToEditorSuppressed(nowMillis = 1_100L))
    }

    @Test
    fun `bidirectional suppression expires for both scroll sync directions`() {
        val state = ScrollSyncController.SuppressionState()

        state.suppressBothDirections(nowMillis = 1_000L)

        assertFalse(state.isEditorToPanelSuppressed(nowMillis = 1_221L))
        assertFalse(state.isPanelToEditorSuppressed(nowMillis = 1_221L))
    }

    @Test
    fun `editor mutation scroll guard suppresses caret-follow visible area movement`() {
        val guard = ScrollSyncController.EditorMutationScrollGuard()

        guard.onDocumentMutation()

        assertTrue(guard.shouldSuppressEditorVisibleAreaChange(oldY = 565, newY = 578))
    }

    @Test
    fun `editor mutation scroll guard allows sync after it is cleared`() {
        val guard = ScrollSyncController.EditorMutationScrollGuard()

        guard.onDocumentMutation()
        guard.clear()

        assertFalse(guard.shouldSuppressEditorVisibleAreaChange(oldY = 565, newY = 578))
    }

    @Test
    fun `preview offset is representable when it fits scrollbar scroll range`() {
        assertTrue(ScrollSyncController.canRepresentVerticalOffset(value = 810, maximum = 1_409, visibleAmount = 599))
    }

    @Test
    fun `preview offset is not representable during transient scrollbar clamp`() {
        assertFalse(ScrollSyncController.canRepresentVerticalOffset(value = 1_776, maximum = 1_297, visibleAmount = 599))
    }

    @Test
    fun `bottom aligned preview restores to new bottom after content append`() {
        val position = ScrollSyncController.PanelScrollPosition(
            value = 1_334,
            bottomGap = 0,
            preserveBottomGap = true,
        )

        assertTrue(ScrollSyncController.shouldPreserveBottomGap(bottomGap = 0))
        assertTrue(ScrollSyncController.canRepresentVerticalOffset(value = 1_411, maximum = 2_010, visibleAmount = 599))
        org.junit.Assert.assertEquals(
            1_411,
            ScrollSyncController.restoredVerticalOffset(position, maximum = 2_010, visibleAmount = 599),
        )
    }

    @Test
    fun `non-bottom preview restores absolute offset after content append`() {
        val position = ScrollSyncController.PanelScrollPosition(
            value = 522,
            bottomGap = 812,
            preserveBottomGap = false,
        )

        org.junit.Assert.assertEquals(
            522,
            ScrollSyncController.restoredVerticalOffset(position, maximum = 2_010, visibleAmount = 599),
        )
    }
}
