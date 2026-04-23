package io.github.barsia.speqa.editor.ui.chips

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChipColorsTest {
    @Test
    fun `same input yields same colour`() {
        assertEquals(tagChipColor("smoke").rgb, tagChipColor("smoke").rgb)
        assertEquals(tagChipColor("auth").rgb, tagChipColor("auth").rgb)
    }

    @Test
    fun `a handful of typical tags produce multiple distinct colours`() {
        val tags = listOf(
            "smoke", "auth", "regression", "perf", "api", "ui", "ios", "android",
            "flaky", "slow", "fast", "critical",
        )
        val distinct = tags.map { tagChipColor(it).rgb }.toSet()
        // With 12 inputs and a 16-entry palette we expect substantial spread.
        assertTrue(
            "expected at least 6 distinct chip colours across common tags, got ${distinct.size}",
            distinct.size >= 6,
        )
    }

    @Test
    fun `palette is non-empty`() {
        assertTrue(tagChipPaletteSize > 0)
    }
}
