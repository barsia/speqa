package io.github.barsia.speqa.editor.ui

import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Test

class AttachmentPreviewSizingTest {

    @Test
    fun `landscape image uses full preview width`() {
        assertEquals(
            IntSize(320, 180),
            calculateAttachmentPreviewSize(
                imageSize = IntSize(1600, 900),
                maxWidthPx = 320,
                maxHeightPx = 240,
            ),
        )
    }

    @Test
    fun `portrait image uses full preview height without side gutters from fixed frame`() {
        assertEquals(
            IntSize(120, 240),
            calculateAttachmentPreviewSize(
                imageSize = IntSize(600, 1200),
                maxWidthPx = 320,
                maxHeightPx = 240,
            ),
        )
    }

    @Test
    fun `small image is not upscaled past its intrinsic size`() {
        assertEquals(
            IntSize(80, 40),
            calculateAttachmentPreviewSize(
                imageSize = IntSize(80, 40),
                maxWidthPx = 320,
                maxHeightPx = 240,
            ),
        )
    }
}
