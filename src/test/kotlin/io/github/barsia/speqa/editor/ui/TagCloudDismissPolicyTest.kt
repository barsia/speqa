package io.github.barsia.speqa.editor.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TagCloudDismissPolicyTest {

    @Test
    fun `keyboard dismiss restores focus to add anchor`() {
        assertTrue(shouldRestoreTagCloudAnchorFocus(TagCloudDismissReason.Keyboard))
    }

    @Test
    fun `pointer dismiss does not restore focus to add anchor`() {
        assertFalse(shouldRestoreTagCloudAnchorFocus(TagCloudDismissReason.Pointer))
    }

    @Test
    fun `pointer focused anchor suppresses visible focus ring`() {
        assertFalse(
            shouldShowTagCloudAnchorFocusRing(
                isFocused = true,
                suppressFocusRing = true,
            ),
        )
    }

    @Test
    fun `keyboard focused anchor keeps visible focus ring`() {
        assertTrue(
            shouldShowTagCloudAnchorFocusRing(
                isFocused = true,
                suppressFocusRing = false,
            ),
        )
    }
}
