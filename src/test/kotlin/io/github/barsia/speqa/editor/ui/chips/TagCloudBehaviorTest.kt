package io.github.barsia.speqa.editor.ui.chips

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TagCloudBehaviorTest {
    @Test
    fun `keyboard dismissal restores anchor focus`() {
        assertTrue(shouldRestoreTagCloudAnchorFocus(TagCloudDismissReason.Keyboard))
    }

    @Test
    fun `pointer dismissal does not restore anchor focus`() {
        assertFalse(shouldRestoreTagCloudAnchorFocus(TagCloudDismissReason.Pointer))
    }

    @Test
    fun `focus ring shows when anchor focused and not suppressed`() {
        assertTrue(shouldShowTagCloudAnchorFocusRing(isFocused = true, suppressFocusRing = false))
    }

    @Test
    fun `focus ring hidden when suppressed even if focused`() {
        assertFalse(shouldShowTagCloudAnchorFocusRing(isFocused = true, suppressFocusRing = true))
    }

    @Test
    fun `focus ring hidden when unfocused`() {
        assertFalse(shouldShowTagCloudAnchorFocusRing(isFocused = false, suppressFocusRing = false))
    }
}
