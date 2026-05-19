package io.github.barsia.speqa.editor.ui.steps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.swing.JPanel

class LivePreviewWrapperTest {

    @Test
    fun `visual offset moves wrapper relative to layout assigned base bounds`() {
        val wrapper = LivePreviewWrapper(JPanel())

        wrapper.setBounds(10, 100, 200, 80)
        wrapper.setVisualOffset(-86f)

        assertEquals(10, wrapper.x)
        assertEquals(14, wrapper.y)
        assertEquals(200, wrapper.width)
        assertEquals(80, wrapper.height)
    }

    @Test
    fun `restoring visual offset returns wrapper to its layout assigned position`() {
        val wrapper = LivePreviewWrapper(JPanel())

        wrapper.setBounds(10, 100, 200, 80)
        wrapper.setVisualOffset(-86f)
        wrapper.setVisualOffset(0f)

        assertEquals(100, wrapper.y)
    }

    @Test
    fun `hiding content does not change wrapper sizing`() {
        val child = JPanel().apply { setSize(180, 60) }
        val wrapper = LivePreviewWrapper(child)

        wrapper.setBounds(0, 0, 180, 60)
        wrapper.setContentVisible(false)

        assertEquals(180, wrapper.width)
        assertEquals(60, wrapper.height)
        assertTrue(wrapper.isVisible)
    }
}
