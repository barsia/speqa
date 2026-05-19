package io.github.barsia.speqa.editor.ui.primitives

import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownPaneFencedRoundTripTest {

    private fun roundTrip(src: String): String {
        val segments = splitFencedSegments(src)
        return segments.joinToString("\n") { seg ->
            when (seg) {
                is Segment.Prose -> seg.markdown
                is Segment.Code -> {
                    val fence = if (seg.language.isNotBlank()) "```${seg.language}" else "```"
                    reassembleCodeSegment(seg.indent, fence, seg.code)
                }
            }
        }
    }

    @Test
    fun `unindented fenced block round-trips`() {
        val src = "before\n```json\n{\"a\":1}\n```\nafter"
        assertEquals(src, roundTrip(src))
    }

    @Test
    fun `three-space indented fenced block preserves indent on both fences`() {
        val src = "3. A custom agent exists in the org\n   ```json\n   {\"name\": \"X\"}\n   ```\nafter"
        assertEquals(src, roundTrip(src))
    }

    @Test
    fun `tab-indented fenced block preserves indent`() {
        val src = "1. item\n\t```kotlin\n\tval x = 1\n\t```\ntail"
        assertEquals(src, roundTrip(src))
    }

    @Test
    fun `indented block with multiple body lines round-trips`() {
        val src = "  ```\n  line one\n  line two\n  line three\n  ```"
        assertEquals(src, roundTrip(src))
    }

    @Test
    fun `code segment stores body without baked-in indent`() {
        val src = "   ```json\n   {\"a\":1}\n   ```"
        val segments = splitFencedSegments(src)
        val code = segments.filterIsInstance<Segment.Code>().single()
        assertEquals("   ", code.indent)
        assertEquals("{\"a\":1}", code.code)
        assertEquals("json", code.language)
    }
}
