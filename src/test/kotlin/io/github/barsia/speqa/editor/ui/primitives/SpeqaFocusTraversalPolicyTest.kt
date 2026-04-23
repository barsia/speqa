package io.github.barsia.speqa.editor.ui.primitives

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.FlowLayout
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.JTextField

class SpeqaFocusTraversalPolicyTest {

    private fun newMountedPanel(): Triple<JFrame, JPanel, SpeqaFocusTraversalPolicy> {
        val frame = JFrame()
        val panel = JPanel(FlowLayout())
        panel.isFocusCycleRoot = true
        frame.contentPane.add(panel)
        val policy = SpeqaFocusTraversalPolicy()
        panel.focusTraversalPolicy = policy
        return Triple(frame, panel, policy)
    }

    @Test
    fun `accepts a plain visible focusable text field`() {
        val (frame, panel, policy) = newMountedPanel()
        try {
            val a = JTextField("a")
            panel.add(a)
            frame.pack()
            assertTrue(policy.accept(a))
        } finally {
            frame.dispose()
        }
    }

    @Test
    fun `rejects an invisible component`() {
        val (frame, panel, policy) = newMountedPanel()
        try {
            val b = JTextField("b").apply { isVisible = false }
            panel.add(b)
            frame.pack()
            assertFalse(policy.accept(b))
        } finally {
            frame.dispose()
        }
    }

    @Test
    fun `rejects a component flagged with speqa_exclude_from_tab_chain`() {
        val (frame, panel, policy) = newMountedPanel()
        try {
            val c = JTextField("c").apply {
                putClientProperty(SPEQA_EXCLUDE_FROM_TAB_CHAIN, true)
            }
            panel.add(c)
            frame.pack()
            assertFalse(policy.accept(c))
        } finally {
            frame.dispose()
        }
    }

    @Test
    fun `accepts when flag is not true`() {
        val (frame, panel, policy) = newMountedPanel()
        try {
            val d = JTextField("d").apply {
                putClientProperty(SPEQA_EXCLUDE_FROM_TAB_CHAIN, false)
            }
            panel.add(d)
            frame.pack()
            assertTrue(policy.accept(d))
        } finally {
            frame.dispose()
        }
    }
}
