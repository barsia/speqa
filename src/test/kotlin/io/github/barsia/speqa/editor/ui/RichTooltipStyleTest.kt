package io.github.barsia.speqa.editor.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class RichTooltipStyleTest {

    @Test
    fun `default tooltip keeps standard content padding`() {
        assertEquals(RichTooltipChrome.Default, richTooltipChrome(edgeToEdgeContent = false))
    }

    @Test
    fun `edge to edge tooltip removes inner content padding`() {
        assertEquals(RichTooltipChrome.EdgeToEdge, richTooltipChrome(edgeToEdgeContent = true))
    }
}
