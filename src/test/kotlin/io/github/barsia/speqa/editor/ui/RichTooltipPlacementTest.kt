package io.github.barsia.speqa.editor.ui

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class RichTooltipPlacementTest {

    @Test
    fun `keeps tooltip below anchor when it fits without overlap`() {
        val position = calculateRichTooltipPosition(
            windowSize = IntSize(900, 700),
            anchorBounds = IntRect(100, 120, 220, 144),
            popupContentSize = IntSize(320, 240),
            gapPx = 8,
            marginPx = 8,
        )

        assertEquals(IntOffset(100, 152), position)
        assertFalse(
            rectsOverlap(
                anchor = IntRect(100, 120, 220, 144),
                popup = IntRect(
                    position.x,
                    position.y,
                    position.x + 320,
                    position.y + 240,
                ),
            ),
        )
    }

    @Test
    fun `uses above placement when below would be clamped into the anchor`() {
        val position = calculateRichTooltipPosition(
            windowSize = IntSize(700, 320),
            anchorBounds = IntRect(120, 210, 240, 234),
            popupContentSize = IntSize(320, 120),
            gapPx = 8,
            marginPx = 8,
        )

        assertEquals(IntOffset(120, 82), position)
        assertFalse(
            rectsOverlap(
                anchor = IntRect(120, 210, 240, 234),
                popup = IntRect(
                    position.x,
                    position.y,
                    position.x + 320,
                    position.y + 120,
                ),
            ),
        )
    }

    @Test
    fun `narrow window clamps horizontally but still avoids overlapping the anchor`() {
        val position = calculateRichTooltipPosition(
            windowSize = IntSize(360, 640),
            anchorBounds = IntRect(250, 140, 320, 164),
            popupContentSize = IntSize(320, 240),
            gapPx = 8,
            marginPx = 8,
        )

        assertEquals(IntOffset(32, 172), position)
        assertFalse(
            rectsOverlap(
                anchor = IntRect(250, 140, 320, 164),
                popup = IntRect(
                    position.x,
                    position.y,
                    position.x + 320,
                    position.y + 240,
                ),
            ),
        )
    }
}
