package io.github.barsia.speqa.editor.ui.chips

import org.junit.Assert.assertEquals
import org.junit.Test

class TicketTooltipForWidthTest {
    @Test
    fun `uses action tooltip when ticket is fully visible`() {
        assertEquals(
            "Open in browser",
            ticketTooltipForWidth(
                preferredWidth = 80,
                actualWidth = 80,
                normal = "Open in browser",
                overflow = "Open SPEQA-123 in browser",
            ),
        )
    }

    @Test
    fun `uses full ticket tooltip when ticket is clipped`() {
        assertEquals(
            "Open SPEQA-123 in browser",
            ticketTooltipForWidth(
                preferredWidth = 81,
                actualWidth = 80,
                normal = "Open in browser",
                overflow = "Open SPEQA-123 in browser",
            ),
        )
    }
}
