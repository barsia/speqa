package io.github.barsia.speqa.editor.ui.steps

import org.junit.Assert.assertEquals
import org.junit.Test

class DragGhostSnapshotGeometryTest {

    @Test
    fun `hidpi ghost uses device pixel buffer and logical draw size`() {
        val geometry = ghostSnapshotGeometry(
            logicalWidth = 120,
            logicalHeight = 48,
            scaleX = 2.0,
            scaleY = 2.0,
        )

        assertEquals(240, geometry.bufferWidth)
        assertEquals(96, geometry.bufferHeight)
        assertEquals(120, geometry.drawWidth)
        assertEquals(48, geometry.drawHeight)
    }

    @Test
    fun `non hidpi ghost keeps 1x buffer`() {
        val geometry = ghostSnapshotGeometry(
            logicalWidth = 120,
            logicalHeight = 48,
            scaleX = 1.0,
            scaleY = 1.0,
        )

        assertEquals(120, geometry.bufferWidth)
        assertEquals(48, geometry.bufferHeight)
        assertEquals(120, geometry.drawWidth)
        assertEquals(48, geometry.drawHeight)
    }
}
