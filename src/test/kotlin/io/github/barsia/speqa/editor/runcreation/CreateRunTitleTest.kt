package io.github.barsia.speqa.editor.runcreation

import org.junit.Assert.assertEquals
import org.junit.Test

class CreateRunTitleTest {
    @Test fun `single active facet value prefixes the title`() {
        assertEquals("High - 2026-06-27 14:30", CreateRunTitle.defaultTitle(activeLabel = "High", timestamp = "2026-06-27 14:30"))
    }
    @Test fun `no single active facet uses generic prefix`() {
        assertEquals("Test Run - 2026-06-27 14:30", CreateRunTitle.defaultTitle(activeLabel = null, timestamp = "2026-06-27 14:30"))
    }
}
