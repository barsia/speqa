package io.github.barsia.speqa.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.event.MouseEvent
import javax.swing.JPanel

class SpeqaSplitHandleSplitterTest {

    @Test
    fun `keeps a usable divider width when platform requests one pixel divider`() {
        val splitter = SpeqaSplitHandleSplitter()

        splitter.setDividerWidth(1)

        assertTrue(splitter.dividerWidth >= SpeqaSplitHandleSplitter.HANDLE_WIDTH)
        assertEquals(2, SpeqaSplitHandleSplitter.ACTIVE_LINE_WIDTH)
        assertTrue(SpeqaSplitHandleSplitter.ACTIVE_LINE_WIDTH < SpeqaSplitHandleSplitter.HANDLE_WIDTH)
    }

    @Test
    fun `dragging from the middle of the handle updates the splitter proportion`() {
        val splitter = SpeqaSplitHandleSplitter().apply {
            setFirstComponent(JPanel())
            setSecondComponent(JPanel())
            setDividerWidth(1)
            setBounds(0, 0, 1000, 500)
            doLayout()
        }
        val divider = splitter.divider

        divider.dispatchEvent(mouseEvent(divider, MouseEvent.MOUSE_PRESSED, divider.width / 2, divider.height / 2))
        divider.dispatchEvent(mouseEvent(divider, MouseEvent.MOUSE_DRAGGED, 700 - divider.x, divider.height / 2))
        divider.dispatchEvent(mouseEvent(divider, MouseEvent.MOUSE_RELEASED, 700 - divider.x, divider.height / 2))

        assertEquals(0.7f, splitter.proportion, 0.01f)
    }

    private fun mouseEvent(component: JPanel, id: Int, x: Int, y: Int): MouseEvent {
        return MouseEvent(
            component,
            id,
            System.currentTimeMillis(),
            0,
            x,
            y,
            1,
            false,
            MouseEvent.BUTTON1,
        )
    }
}
