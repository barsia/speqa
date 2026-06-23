// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package io.github.barsia.speqa.editor.ui

import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import java.awt.Dimension
import javax.swing.JPanel
import javax.swing.Timer

/**
 * Overlay container that toggles a [FloatingHeaderBar] on/off based on the
 * scrolled position.
 *
 * Show predicate: scroll value crosses above `anchorYProvider() + showBuffer`.
 * Hide predicate: scroll value drops below `anchorYProvider() - hideBuffer`.
 * Inside the dead-band the previous state is kept, so micro-jitter from
 * programmatic scroll restores (e.g. after a document edit) does not cause
 * flicker.
 *
 * Visibility is a hard toggle on `bar.isVisible`; the previous slide animation
 * was removed because its tick-driven `repaint` ran on every scroll-model
 * event and caused the bar to disappear+reappear on each scrollbar tick.
 * A clean snap is more predictable.
 */
class FloatingHeaderHost(
    val scrollPane: JBScrollPane,
    val bar: FloatingHeaderBar,
    private val anchorYProvider: () -> Int = { JBUI.scale(40) },
    private val showBuffer: Int = JBUI.scale(4),
    private val hideBuffer: Int = JBUI.scale(8),
) : JPanel(null) {

    private val debounceTimer = Timer(50) { applyVisibility() }.apply { isRepeats = false }

    init {
        isOpaque = false
        add(scrollPane)
        add(bar)
        setComponentZOrder(bar, 0)
        bar.isVisible = false
        // JViewport's default BLIT_SCROLL_MODE blits pixels for fast
        // scrolling, which copies the floating bar's overlapping pixels
        // along with the scrolled content and visually wipes the bar. The
        // SIMPLE mode repaints the viewport fully on every scroll - slower
        // for very tall content, but the only correct choice when a
        // component is z-ordered above the viewport.
        scrollPane.viewport.scrollMode = javax.swing.JViewport.SIMPLE_SCROLL_MODE
        // Debounce scroll-model events: SpeqaPreviewEditor.patchFromPreview
        // triggers value=0 momentarily before restoring the preserved offset
        // (e.g. value: 200 -> 0 -> 200 in a few ms). Reacting to each event
        // flickers the bar; debouncing 50ms latches onto the final value.
        scrollPane.verticalScrollBar.model.addChangeListener { debounceTimer.restart() }
    }

    private fun applyVisibility() {
        val value = scrollPane.verticalScrollBar.value
        val anchor = anchorYProvider()
        if (anchor <= 0) return
        val shown = bar.isVisible
        val shouldShow = if (shown) value > anchor - hideBuffer else value > anchor + showBuffer
        if (shouldShow != shown) {
            bar.isVisible = shouldShow
            repaint(0, 0, width, bar.height.coerceAtLeast(JBUI.scale(26)))
        }
    }

    override fun getPreferredSize(): Dimension = scrollPane.preferredSize

    override fun doLayout() {
        val w = width
        val h = height
        scrollPane.setBounds(0, 0, w, h)
        val barHeight = bar.preferredSize.height.coerceAtLeast(JBUI.scale(26))
        bar.setBounds(0, 0, w, barHeight)
    }

}
