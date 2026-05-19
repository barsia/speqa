package io.github.barsia.speqa.editor.ui.primitives

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.NlsActions
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import javax.swing.Icon
import javax.swing.JComponent

/**
 * Updates the tooltip of any `SpeqaIconButton` (`ActionButton`) so the IDE
 * tooltip rendering picks up the change. IntelliJ's `IdeTooltipManager`
 * renders tooltips for `ActionButton` from its `Presentation.description` -
 * setting only `JComponent.toolTipText` updates the Swing fallback tooltip
 * but NOT the IDE-rendered one. Always go through this helper instead of
 * touching `toolTipText` directly when changing a Speqa icon-button tooltip.
 */
fun JComponent.setSpeqaTooltip(text: String) {
    toolTipText = text
    if (this is ActionButton) {
        presentation.text = text
        presentation.description = text
    }
}

/**
 * Shared muted colour for all secondary/inline action icons across Speqa
 * (edit pencils, delete trash/close, header "+", "Add …" leading icons, etc.)
 * so they render at the same visual weight as muted text labels.
 */
fun speqaMutedIconColor(): Color =
    JBColor.namedColor("Label.disabledForeground", JBColor.GRAY)

/**
 * Compact icon-only action button rendered as an IntelliJ [ActionButton]
 * wrapping a [DumbAwareAction]. Includes a hand cursor and native tooltip.
 *
 * @param icon button icon.
 * @param tooltip accessible / hover text; used as action template presentation text.
 * @param onAction invoked on click / keyboard activation.
 */
fun speqaIconButton(
    icon: Icon,
    @NlsActions.ActionText tooltip: String,
    muted: Boolean = true,
    danger: Boolean = false,
    onAction: () -> Unit,
): JComponent {
    // Keep icons at their native size (16x16 logical for AllIcons). Earlier
    // attempts to shrink via IconUtil.toSize clipped trash icon bodies, and
    // IconUtil.scale with a null ancestor produced render glitches that
    // caused sibling chips to fail to paint. The ActionButton 22x22 slot
    // already provides visual breathing room around the 16x16 icon.
    val sized = icon
    val baseIcon = when {
        danger -> replaceIconColor(sized, dangerIconColor())
        muted -> replaceIconColor(sized, speqaMutedIconColor())
        else -> sized
    }
    val action = object : DumbAwareAction(tooltip, null, baseIcon) {
        override fun actionPerformed(e: AnActionEvent) {
            onAction()
        }
    }
    val presentation = Presentation(tooltip).apply {
        this.icon = baseIcon
        description = tooltip
    }
    val button = ActionButton(
        action,
        presentation,
        ActionPlaces.UNKNOWN,
        JBUI.size(22, 22),
    )
    button.toolTipText = tooltip
    button.isFocusable = true
    button.handCursor()
    // Register action so keyboard shortcuts (if later added) can find it.
    ActionManager.getInstance()
    return button
}

/**
 * Color used to tint destructive icons (delete trash, remove X). Returns a
 * dual-tone JBColor so the icon stays vivid in both light and dark themes:
 * - Light theme: a deep red (#CC4646) that contrasts against light backgrounds.
 * - Dark theme: a brighter coral red (#FF7373) that contrasts against dark
 *   chip backgrounds. The previous theme-key lookup
 *   (`Notifications.errorIcon`) resolved to colors that washed out under
 *   60% alpha in dark mode; this explicit pair avoids that.
 */
private fun dangerIconColor(): java.awt.Color {
    return JBColor(java.awt.Color(0xCC4646), java.awt.Color(0xFF7373))
}

internal fun replaceIconColor(source: Icon, color: Color): Icon {
    // Repaint the SVG live each time the icon is asked to paint itself,
    // applying an SrcAtop fill so every rendered pixel takes the target
    // `color` (keeping its alpha). Doing this in `paintIcon` instead of
    // pre-baking a BufferedImage means the SVG re-rasterises at the
    // active display / JBUI scale, so it stays sharp on Retina / 200%
    // screens. Reports its size as the source icon's logical size so
    // JLabel reserves the correct width/height and the icon aligns with
    // the label baseline like any other IntelliJ icon.
    return object : Icon {
        override fun getIconWidth(): Int = source.iconWidth
        override fun getIconHeight(): Int = source.iconHeight

        override fun paintIcon(c: java.awt.Component?, g: java.awt.Graphics, x: Int, y: Int) {
            val w = source.iconWidth.coerceAtLeast(1)
            val h = source.iconHeight.coerceAtLeast(1)
            val img = com.intellij.util.ui.UIUtil.createImage(c, w, h, BufferedImage.TYPE_INT_ARGB)
            val ig = img.createGraphics()
            try {
                ig.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                source.paintIcon(c, ig, 0, 0)
                ig.composite = java.awt.AlphaComposite.SrcAtop
                ig.color = color
                ig.fillRect(0, 0, w, h)
            } finally {
                ig.dispose()
            }
            com.intellij.util.ui.UIUtil.drawImage(g, img, x, y, c)
        }
    }
}
