package io.github.barsia.speqa.editor.ui.primitives

import java.awt.Container
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.JScrollPane
import javax.swing.SwingUtilities

/**
 * [FlowLayout] subclass that correctly reports `preferredLayoutSize` when its
 * children wrap to multiple lines. The stock `FlowLayout` always computes its
 * preferred height as a single row, so wrapped rows are visually clipped by
 * the enclosing layout. This variant measures the required height for the
 * current target width.
 *
 * Based on the well-known WrapLayout pattern by Rob Camick.
 */
class WrapLayout(align: Int, hgap: Int, vgap: Int) : FlowLayout(align, hgap, vgap) {

    override fun preferredLayoutSize(target: Container): Dimension = layoutSize(target, preferred = true)

    override fun minimumLayoutSize(target: Container): Dimension {
        val dim = layoutSize(target, preferred = false)
        dim.width -= hgap + 1
        return dim
    }

    private fun layoutSize(target: Container, preferred: Boolean): Dimension {
        synchronized(target.treeLock) {
            var targetWidth = target.size.width
            if (targetWidth == 0) {
                val root = SwingUtilities.getAncestorOfClass(JScrollPane::class.java, target)
                    ?: target.parent
                targetWidth = root?.width ?: Integer.MAX_VALUE
            }
            val horizontalInsetsAndGap = target.insets.left + target.insets.right + hgap * 2
            val maxWidth = targetWidth - horizontalInsetsAndGap

            val dim = Dimension(0, 0)
            var rowWidth = 0
            var rowHeight = 0

            for (i in 0 until target.componentCount) {
                val m = target.getComponent(i)
                if (!m.isVisible) continue
                val d = if (preferred) m.preferredSize else m.minimumSize
                if (rowWidth + d.width > maxWidth) {
                    addRow(dim, rowWidth, rowHeight)
                    rowWidth = 0
                    rowHeight = 0
                }
                if (rowWidth != 0) rowWidth += hgap
                rowWidth += d.width
                rowHeight = maxOf(rowHeight, d.height)
            }
            addRow(dim, rowWidth, rowHeight)
            dim.width += horizontalInsetsAndGap
            dim.height += target.insets.top + target.insets.bottom + vgap * 2
            return dim
        }
    }

    private fun addRow(dim: Dimension, rowWidth: Int, rowHeight: Int) {
        dim.width = maxOf(dim.width, rowWidth)
        if (dim.height > 0) dim.height += vgap
        dim.height += rowHeight
    }
}
