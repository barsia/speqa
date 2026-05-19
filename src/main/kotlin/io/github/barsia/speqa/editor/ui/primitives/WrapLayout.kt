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
 * When [gapAround] is `false` (default `true` for backwards compatibility),
 * `hgap` is treated as the gap BETWEEN children only - the first child of
 * every row sits flush with `insets.left` and the last child has no trailing
 * gap. This lets the cloud line up flush with a sibling caption above it
 * without resorting to a negative-inset Border hack.
 *
 * Based on the WrapLayout pattern by Rob Camick.
 */
class WrapLayout(
    align: Int,
    hgap: Int,
    vgap: Int,
    private val gapAround: Boolean = true,
) : FlowLayout(align, hgap, vgap) {

    override fun preferredLayoutSize(target: Container): Dimension = layoutSize(target, preferred = true)

    override fun minimumLayoutSize(target: Container): Dimension {
        val dim = layoutSize(target, preferred = false)
        dim.width -= hgap + 1
        return dim
    }

    override fun layoutContainer(target: Container) {
        super.layoutContainer(target)
        if (gapAround) return
        // FlowLayout positions the first child of each row at insets.left + hgap.
        // Shift every child left by hgap so the first column aligns with
        // insets.left and the gap remains only BETWEEN children.
        synchronized(target.treeLock) {
            for (i in 0 until target.componentCount) {
                val c = target.getComponent(i)
                if (!c.isVisible) continue
                c.setLocation(c.x - hgap, c.y)
            }
        }
    }

    private fun layoutSize(target: Container, preferred: Boolean): Dimension {
        synchronized(target.treeLock) {
            var targetWidth = target.size.width
            if (targetWidth == 0) {
                val root = SwingUtilities.getAncestorOfClass(JScrollPane::class.java, target)
                    ?: target.parent
                targetWidth = root?.width ?: Integer.MAX_VALUE
            }
            // `FlowLayout.layoutContainer` always reserves `hgap*2` from the
            // target width when computing where each row breaks (it positions
            // the first child at `insets.left + hgap` and treats `hgap` after
            // the last child too). If our preferred-size math used a wider
            // bound (e.g. when `gapAround = false`), we would report
            // "single row fits" while the actual layout pass still wraps the
            // last chip into a second row that the parent never allocated
            // height for - the chip becomes invisible.
            //
            // Keep the wrap-decision width in sync with the super class even
            // when `gapAround = false`; the visual gap-around offset is still
            // handled by the post-shift in `layoutContainer`.
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
            // Report width using the requested gap-around model (so a sibling
            // column doesn't get pushed wider by phantom padding when
            // `gapAround = false`), but the wrap-decision used the stricter
            // bound above so the reported height is always correct.
            val reportedHorizontalPadding = target.insets.left + target.insets.right +
                if (gapAround) hgap * 2 else 0
            dim.width += reportedHorizontalPadding
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
