package io.github.barsia.speqa.editor.ui.steps

import io.github.barsia.speqa.model.DescriptionBlock
import io.github.barsia.speqa.model.PreconditionsBlock
import io.github.barsia.speqa.model.TestCaseBodyBlock
import org.junit.Assert.assertEquals
import org.junit.Test

class BodyBlockEditingTest {

    // --- mergeBodyBlocks ---

    @Test
    fun `mergeBodyBlocks trims each block and joins with a blank line`() {
        val blocks = listOf(DescriptionBlock("  a  "), DescriptionBlock("b"))
        assertEquals("a\n\nb", mergeBodyBlocks(blocks, DescriptionBlock::class.java))
    }

    @Test
    fun `mergeBodyBlocks ignores blocks of other types`() {
        val blocks = listOf(DescriptionBlock("desc"), PreconditionsBlock(markdown = "pre"))
        assertEquals("desc", mergeBodyBlocks(blocks, DescriptionBlock::class.java))
    }

    @Test
    fun `mergeBodyBlocks drops blank blocks without leaving separators`() {
        val blocks = listOf(DescriptionBlock("a"), DescriptionBlock("   "), DescriptionBlock("b"))
        assertEquals("a\n\nb", mergeBodyBlocks(blocks, DescriptionBlock::class.java))
    }

    @Test
    fun `mergeBodyBlocks returns empty string when no block matches the type`() {
        val blocks = listOf(PreconditionsBlock(markdown = "pre"))
        assertEquals("", mergeBodyBlocks(blocks, DescriptionBlock::class.java))
    }

    // --- replaceBodyBlocks ---

    @Test
    fun `replaceBodyBlocks appends a new block when none of the type exists`() {
        val blocks = listOf<TestCaseBodyBlock>(PreconditionsBlock(markdown = "pre"))
        val result = replaceBodyBlocks(blocks, DescriptionBlock::class.java) { DescriptionBlock("new") }
        // appended, then reordered into canonical order (description first)
        assertEquals(listOf(DescriptionBlock("new"), PreconditionsBlock(markdown = "pre")), result)
    }

    @Test
    fun `replaceBodyBlocks replaces the first match and drops any further blocks of that type`() {
        val blocks = listOf(
            DescriptionBlock("d1"),
            DescriptionBlock("d2"),
            PreconditionsBlock(markdown = "pre"),
        )
        val result = replaceBodyBlocks(blocks, DescriptionBlock::class.java) { DescriptionBlock("merged") }
        assertEquals(listOf(DescriptionBlock("merged"), PreconditionsBlock(markdown = "pre")), result)
    }

    @Test
    fun `replaceBodyBlocks invokes the factory exactly once`() {
        val blocks = listOf(DescriptionBlock("a"), DescriptionBlock("b"))
        var calls = 0
        val result = replaceBodyBlocks(blocks, DescriptionBlock::class.java) {
            calls++
            DescriptionBlock("x")
        }
        assertEquals(1, calls)
        assertEquals(listOf(DescriptionBlock("x")), result)
    }

    // --- canonicalBodyBlockOrder ---

    @Test
    fun `canonicalBodyBlockOrder moves description before preconditions`() {
        val blocks = listOf(PreconditionsBlock(markdown = "pre"), DescriptionBlock("desc"))
        assertEquals(
            listOf(DescriptionBlock("desc"), PreconditionsBlock(markdown = "pre")),
            canonicalBodyBlockOrder(blocks),
        )
    }

    @Test
    fun `canonicalBodyBlockOrder keeps descriptions in their original order`() {
        val blocks = listOf(
            DescriptionBlock("d1"),
            PreconditionsBlock(markdown = "p1"),
            DescriptionBlock("d2"),
        )
        assertEquals(
            listOf(DescriptionBlock("d1"), DescriptionBlock("d2"), PreconditionsBlock(markdown = "p1")),
            canonicalBodyBlockOrder(blocks),
        )
    }
}
