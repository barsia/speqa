package io.github.barsia.speqa.editor

import com.intellij.openapi.ui.Divider
import com.intellij.openapi.ui.Splittable
import com.intellij.ui.JBColor
import com.intellij.ui.JBSplitter
import com.intellij.util.ui.JBUI
import java.awt.Color
import java.awt.Cursor
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.SwingUtilities

internal class SpeqaSplitHandleSplitter : JBSplitter() {
    companion object {
        const val HANDLE_WIDTH: Int = 7
        private const val RESTING_LINE_WIDTH: Int = 1
        const val ACTIVE_LINE_WIDTH: Int = 2
        private const val ACTIVE_FILL_ALPHA: Int = 42
    }

    override fun createDivider(): Divider = SpeqaSplitHandleDivider(this)

    override fun setDividerWidth(width: Int) {
        super.setDividerWidth(maxOf(width, JBUI.scale(HANDLE_WIDTH)))
    }

    private class SpeqaSplitHandleDivider(
        private val splitter: Splittable,
    ) : Divider(null) {
        private var vertical = splitter.orientation
        private var resizeEnabled = true
        private var switchOrientationEnabled = false
        private var active = false
        private var dragging = false

        init {
            isOpaque = false
            isFocusable = false
            setOrientation(vertical)

            val listener = object : MouseAdapter() {
                override fun mouseEntered(e: MouseEvent) {
                    setActive(resizeEnabled)
                }

                override fun mouseMoved(e: MouseEvent) {
                    setActive(resizeEnabled)
                }

                override fun mouseExited(e: MouseEvent) {
                    if (!dragging) {
                        setActive(false)
                    }
                }

                override fun mousePressed(e: MouseEvent) {
                    if (!resizeEnabled || !SwingUtilities.isLeftMouseButton(e)) return
                    if (switchOrientationEnabled && e.isControlDown) {
                        splitter.orientation = !splitter.orientation
                        e.consume()
                        return
                    }
                    dragging = true
                    splitter.setDragging(true)
                    setActive(true)
                    e.consume()
                }

                override fun mouseDragged(e: MouseEvent) {
                    if (!dragging) return
                    updateProportion(e)
                    e.consume()
                }

                override fun mouseReleased(e: MouseEvent) {
                    if (!dragging) return
                    updateProportion(e)
                    dragging = false
                    splitter.setDragging(false)
                    setActive(containsEvent(e))
                    e.consume()
                }

                override fun mouseClicked(e: MouseEvent) {
                    if (!resizeEnabled || !SwingUtilities.isLeftMouseButton(e)) return
                    if (e.clickCount == 2) {
                        splitter.setProportion(0.5f)
                        e.consume()
                    }
                }
            }
            addMouseListener(listener)
            addMouseMotionListener(listener)
        }

        override fun setResizeEnabled(enabled: Boolean) {
            resizeEnabled = enabled
            if (!enabled) {
                dragging = false
                active = false
            }
            updateCursor()
            repaint()
        }

        override fun setSwitchOrientationEnabled(enabled: Boolean) {
            switchOrientationEnabled = enabled
        }

        override fun setOrientation(vertical: Boolean) {
            this.vertical = vertical
            updateCursor()
            repaint()
        }

        override fun paintComponent(g: Graphics) {
            super.paintComponent(g)
            val g2 = g.create() as Graphics2D
            try {
                val highlighted = active || dragging
                val lineColor = if (highlighted) ACTIVE_COLOR else background ?: JBColor.border()
                if (highlighted) {
                    g2.color = lineColor.withAlpha(ACTIVE_FILL_ALPHA)
                    g2.fillRect(0, 0, width, height)
                }
                g2.color = lineColor
                paintCenterLine(g2, if (highlighted) ACTIVE_LINE_WIDTH else RESTING_LINE_WIDTH)
            }
            finally {
                g2.dispose()
            }
        }

        private fun paintCenterLine(g: Graphics2D, unscaledLineWidth: Int) {
            val lineWidth = JBUI.scale(unscaledLineWidth)
            if (vertical) {
                val y = (height - lineWidth) / 2
                g.fillRect(0, y, width, lineWidth)
            }
            else {
                val x = (width - lineWidth) / 2
                g.fillRect(x, 0, lineWidth, height)
            }
        }

        private fun updateProportion(e: MouseEvent) {
            val component = splitter.asComponent()
            val total = if (vertical) component.height else component.width
            if (total <= 0) return

            val point = SwingUtilities.convertPoint(this, e.point, component)
            val position = if (vertical) point.y else point.x
            val raw = position.toFloat() / total.toFloat()
            val min = maxOf(0f, splitter.getMinProportion(true))
            val max = minOf(1f, 1f - splitter.getMinProportion(false))
            splitter.setProportion(if (min <= max) raw.coerceIn(min, max) else raw.coerceIn(0f, 1f))
        }

        private fun updateCursor() {
            cursor = if (!resizeEnabled) {
                Cursor.getDefaultCursor()
            }
            else {
                Cursor.getPredefinedCursor(if (vertical) Cursor.S_RESIZE_CURSOR else Cursor.E_RESIZE_CURSOR)
            }
        }

        private fun setActive(next: Boolean) {
            if (active == next) return
            active = next
            repaint()
        }

        private fun containsEvent(e: MouseEvent): Boolean {
            return e.x >= 0 && e.x < width && e.y >= 0 && e.y < height
        }

        private fun Color.withAlpha(alpha: Int): Color = Color(red, green, blue, alpha)
    }
}

private val ACTIVE_COLOR = JBColor(0x3574F0, 0x548AF7)
