package io.github.barsia.speqa.editor.ui.steps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.RenderingHints

class DragGhostRenderingHintsTest {

    @Test
    fun `fallback hints enable sharp text rendering when desktop hints are unavailable`() {
        val hints = ghostSnapshotRenderingHints(desktopHints = null)

        assertEquals(RenderingHints.VALUE_TEXT_ANTIALIAS_ON, hints[RenderingHints.KEY_TEXT_ANTIALIASING])
        assertEquals(RenderingHints.VALUE_FRACTIONALMETRICS_ON, hints[RenderingHints.KEY_FRACTIONALMETRICS])
        assertEquals(RenderingHints.VALUE_ANTIALIAS_ON, hints[RenderingHints.KEY_ANTIALIASING])
        assertEquals(RenderingHints.VALUE_RENDER_QUALITY, hints[RenderingHints.KEY_RENDERING])
        assertEquals(RenderingHints.VALUE_STROKE_PURE, hints[RenderingHints.KEY_STROKE_CONTROL])
    }

    @Test
    fun `desktop text hints win over fallback defaults`() {
        val desktop = mapOf(
            RenderingHints.KEY_TEXT_ANTIALIASING to RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB,
            RenderingHints.KEY_FRACTIONALMETRICS to RenderingHints.VALUE_FRACTIONALMETRICS_OFF,
        )

        val hints = ghostSnapshotRenderingHints(desktopHints = desktop)

        assertEquals(
            RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB,
            hints[RenderingHints.KEY_TEXT_ANTIALIASING],
        )
        assertEquals(
            RenderingHints.VALUE_FRACTIONALMETRICS_OFF,
            hints[RenderingHints.KEY_FRACTIONALMETRICS],
        )
        assertEquals(RenderingHints.VALUE_ANTIALIAS_ON, hints[RenderingHints.KEY_ANTIALIASING])
        assertTrue(hints.isNotEmpty())
    }
}
