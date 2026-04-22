package io.github.barsia.speqa.editor.ui.steps

import io.github.barsia.speqa.model.DescriptionBlock
import io.github.barsia.speqa.model.PreconditionsBlock
import io.github.barsia.speqa.model.TestCaseBodyBlock

internal fun <T : TestCaseBodyBlock> mergeBodyBlocks(
    blocks: List<TestCaseBodyBlock>,
    type: Class<T>,
): String {
    return blocks
        .filter { type.isInstance(it) }
        .map { it.markdown.trim() }
        .filter { it.isNotBlank() }
        .joinToString("\n\n")
}

internal fun <T : TestCaseBodyBlock> replaceBodyBlocks(
    blocks: List<TestCaseBodyBlock>,
    type: Class<T>,
    factory: () -> TestCaseBodyBlock,
): List<TestCaseBodyBlock> {
    val hasExisting = blocks.any { type.isInstance(it) }
    val newBlocks = if (hasExisting) {
        var replaced = false
        blocks.mapNotNull {
            if (type.isInstance(it)) {
                if (!replaced) {
                    replaced = true
                    factory()
                } else {
                    null
                }
            } else {
                it
            }
        }
    } else {
        blocks + factory()
    }
    return canonicalBodyBlockOrder(newBlocks)
}

internal fun canonicalBodyBlockOrder(blocks: List<TestCaseBodyBlock>): List<TestCaseBodyBlock> {
    return blocks.filterIsInstance<DescriptionBlock>() + blocks.filterIsInstance<PreconditionsBlock>()
}
