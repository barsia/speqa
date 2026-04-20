package io.github.barsia.speqa.editor.ui

import androidx.compose.ui.geometry.Rect
import io.github.barsia.speqa.model.TestCase
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusTrailVisibilityTest {

    @Test
    fun `focus trail stays hidden while title is still visible`() {
        val viewport = Rect(left = 0f, top = 100f, right = 800f, bottom = 900f)
        val title = Rect(left = 16f, top = 120f, right = 500f, bottom = 148f)

        assertFalse(shouldShowFocusTrail(titleBounds = title, viewportBounds = viewport))
    }

    @Test
    fun `focus trail appears after title scrolls above viewport`() {
        val viewport = Rect(left = 0f, top = 100f, right = 800f, bottom = 900f)
        val title = Rect(left = 16f, top = 68f, right = 500f, bottom = 96f)

        assertTrue(shouldShowFocusTrail(titleBounds = title, viewportBounds = viewport))
    }

    @Test
    fun `focus trail stays hidden until both bounds are known`() {
        val viewport = Rect(left = 0f, top = 100f, right = 800f, bottom = 900f)

        assertFalse(shouldShowFocusTrail(titleBounds = null, viewportBounds = viewport))
        assertFalse(shouldShowFocusTrail(titleBounds = Rect(0f, 68f, 500f, 96f), viewportBounds = null))
    }

    @Test
    fun `focus trail title includes test case id when available`() {
        assertEquals(
            "TC-7 · Login flow",
            focusTrailTitle(TestCase(id = 7, title = "Login flow")),
        )
    }
}
