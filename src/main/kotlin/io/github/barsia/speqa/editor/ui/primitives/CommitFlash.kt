package io.github.barsia.speqa.editor.ui.primitives

import java.awt.Color
import javax.swing.JComponent
import javax.swing.Timer
import javax.swing.UIManager

/**
 * Short background-colour pulse used to highlight a component that just
 * received an external commit. Interpolates from [flashColor] back to the
 * component's current background over ~500 ms at ~60 fps.
 */
object CommitFlash {
    var enabled: Boolean = false

    private const val DURATION_MS = 500
    private const val FRAME_MS = 16

    fun flash(
        target: JComponent,
        flashColor: Color = UIManager.getColor("Component.focusColor") ?: target.background,
    ) {
        if (!enabled) return
        val baseBackground = target.background ?: return
        val wasOpaque = target.isOpaque
        target.isOpaque = true
        target.background = flashColor

        val startTime = System.currentTimeMillis()
        lateinit var timer: Timer
        timer = Timer(FRAME_MS) {
            val elapsed = (System.currentTimeMillis() - startTime).toFloat()
            val t = (elapsed / DURATION_MS).coerceIn(0f, 1f)
            target.background = interpolate(flashColor, baseBackground, t)
            target.repaint()
            if (t >= 1f) {
                timer.stop()
                target.background = baseBackground
                target.isOpaque = wasOpaque
                target.repaint()
            }
        }
        timer.isRepeats = true
        timer.start()
    }
}

/** Linear RGBA interpolation between [start] and [end] at [t] in [0, 1]. */
internal fun interpolate(start: Color, end: Color, t: Float): Color {
    val clamped = t.coerceIn(0f, 1f)
    val r = (start.red + (end.red - start.red) * clamped).toInt()
    val g = (start.green + (end.green - start.green) * clamped).toInt()
    val b = (start.blue + (end.blue - start.blue) * clamped).toInt()
    val a = (start.alpha + (end.alpha - start.alpha) * clamped).toInt()
    return Color(r, g, b, a)
}
