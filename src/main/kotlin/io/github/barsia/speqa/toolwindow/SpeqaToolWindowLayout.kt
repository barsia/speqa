package io.github.barsia.speqa.toolwindow

import com.intellij.toolWindow.DefaultToolWindowDescriptorBuilder
import com.intellij.toolWindow.DefaultToolWindowLayoutBuilder
import com.intellij.toolWindow.DefaultToolWindowLayoutExtension
import com.intellij.toolWindow.ToolWindowDescriptor

/**
 * Places the SpeQA tool window first on the left stripe in the default layout.
 *
 * First position (order 0) is deliberate and the only stable one: conditional tool
 * windows like Commit register after startup and reclaim their slot, so any
 * non-first position gets reshuffled (a second-place SpeQA is pushed to third once
 * Commit appears). Nothing can reclaim a slot ahead of order 0.
 *
 * The default layout is built from a single insertion-ordered map, and
 * `addOrUpdate` keeps an existing id at its position (there is no insert-at-index).
 * To land SpeQA first we lift every left window out of the layout, add SpeQA, then
 * re-add the lifted windows after it, preserving their settings.
 *
 * This affects only the default layout (first run and Restore Default Layout);
 * once the user rearranges the stripe, their saved layout takes over.
 */
class SpeqaToolWindowLayout : DefaultToolWindowLayoutExtension {

    override fun buildV1Layout(builder: DefaultToolWindowLayoutBuilder) = placeSpeqaFirst(builder)

    override fun buildV2Layout(builder: DefaultToolWindowLayoutBuilder) = placeSpeqaFirst(builder)

    private fun placeSpeqaFirst(builder: DefaultToolWindowLayoutBuilder) {
        val speqaId = SpeqaToolWindowFactory.TOOL_WINDOW_ID

        val others = mutableListOf<LeftWindow>()
        builder.removeAll { descriptor ->
            if (descriptor.anchor != ToolWindowDescriptor.ToolWindowAnchor.LEFT) return@removeAll false
            if (descriptor.id != speqaId) {
                others += capture(descriptor)
            }
            true
        }

        builder.left.addOrUpdate(speqaId)
        others.forEach { window ->
            builder.left.addOrUpdate(window.id) { restore(window, this) }
        }
    }

    private fun capture(descriptor: DefaultToolWindowDescriptorBuilder) = LeftWindow(
        id = descriptor.id,
        isVisible = descriptor.isVisible,
        weight = descriptor.weight,
        contentUiType = descriptor.contentUiType,
        isSplit = descriptor.isSplit,
        sideWeight = descriptor.sideWeight,
    )

    private fun restore(window: LeftWindow, descriptor: DefaultToolWindowDescriptorBuilder) {
        descriptor.isVisible = window.isVisible
        descriptor.weight = window.weight
        descriptor.contentUiType = window.contentUiType
        descriptor.isSplit = window.isSplit
        descriptor.sideWeight = window.sideWeight
    }

    private data class LeftWindow(
        val id: String,
        val isVisible: Boolean,
        val weight: Float,
        val contentUiType: ToolWindowDescriptor.ToolWindowContentUiType,
        val isSplit: Boolean,
        val sideWeight: Float,
    )
}
