// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package io.github.barsia.speqa.editor.ui.steps

import com.intellij.ui.JBColor
import com.intellij.ui.RoundedLineBorder
import com.intellij.util.ui.JBUI
import io.github.barsia.speqa.SpeqaBundle
import io.github.barsia.speqa.editor.ui.primitives.handCursor
import io.github.barsia.speqa.editor.ui.theme.SpeqaThemeColors
import io.github.barsia.speqa.model.StepVerdict
import java.awt.Color
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JLabel
import javax.swing.JPanel

// Verdict tint aliases — re-export `SpeqaThemeColors` tokens so existing
// references (`COLOR_PASSED_BG` etc. inside `StepCard`) keep working without
// embedding raw `Color(...)` literals at the call site. The defaults live in
// `SpeqaThemeColors`; IDE themes can override via UIManager keys.
internal val COLOR_PASSED_BG: JBColor = SpeqaThemeColors.verdictPassedBackground
internal val COLOR_FAILED_BG: JBColor = SpeqaThemeColors.verdictFailedBackground
internal val COLOR_SKIPPED_BG: JBColor = SpeqaThemeColors.verdictSkippedBackground
internal val COLOR_BLOCKED_BG: JBColor = SpeqaThemeColors.verdictBlockedBackground

private val COLOR_SELECTED_FG: JBColor = SpeqaThemeColors.verdictSelectedForeground

/**
 * A [JLabel] that paints an optional rounded filled background before the
 * label content. This avoids the classic Swing corner-leak bug where
 * `isOpaque = true` fills the full bounding rectangle before the
 * [RoundedLineBorder] is drawn, making the corners appear square.
 *
 * When [fillColor] is non-null the rounded rectangle is painted here in
 * [paintComponent]; [isOpaque] stays `false` always so Swing never paints
 * the rectangular background.
 */
private class VerdictButtonLabel(text: String) : JLabel(text) {

    var fillColor: Color? = null

    init {
        isOpaque = false
    }

    override fun paintComponent(g: Graphics) {
        val fill = fillColor
        if (fill != null) {
            val g2 = g.create() as Graphics2D
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g2.color = fill
                // `RoundedLineBorder(c, arcSize, 1)` paints `drawRoundRect(0, 0, w-1, h-1, arcSize, arcSize)`,
                // where `arcSize` is the FULL arc diameter in Java2D semantics. Match the same diameter
                // and the same `(w-1, h-1)` rect here so the painted fill's corners follow exactly the
                // same curve as the border outline drawn on top.
                val arc = JBUI.scale(8)
                g2.fillRoundRect(0, 0, width - 1, height - 1, arc, arc)
            } finally {
                g2.dispose()
            }
        }
        super.paintComponent(g)
    }
}

/**
 * Pure toggle helper. Returns [StepVerdict.NONE] when [clicked] equals [current]
 * (toggle off), otherwise returns [clicked]. Zero Swing dependency — fully unit
 * testable.
 */
fun verdictAfterToggle(current: StepVerdict, clicked: StepVerdict): StepVerdict =
    if (current == clicked) StepVerdict.NONE else clicked

/**
 * Horizontal row of four verdict toggle buttons (Passed / Failed / Skipped /
 * Blocked). Clicking the active button deselects it (returns NONE). Clicking a
 * different button selects it. Uses JLabel-based buttons to match the plugin's
 * chip/toggle UI style (not JButton).
 */
class StepVerdictRow(
    initialVerdict: StepVerdict,
    private val onVerdictChange: (StepVerdict) -> Unit,
) : JPanel() {

    private var currentVerdict: StepVerdict = initialVerdict
    private var suppressProgrammaticSync = false

    private data class VerdictButton(
        val verdict: StepVerdict,
        val label: VerdictButtonLabel,
        val tintBg: Color,
    )

    private val buttons: List<VerdictButton> = listOf(
        VerdictButton(
            StepVerdict.PASSED,
            VerdictButtonLabel(SpeqaBundle.message("panel.run.verdict.passed")),
            COLOR_PASSED_BG,
        ),
        VerdictButton(
            StepVerdict.FAILED,
            VerdictButtonLabel(SpeqaBundle.message("panel.run.verdict.failed")),
            COLOR_FAILED_BG,
        ),
        VerdictButton(
            StepVerdict.SKIPPED,
            VerdictButtonLabel(SpeqaBundle.message("panel.run.verdict.skipped")),
            COLOR_SKIPPED_BG,
        ),
        VerdictButton(
            StepVerdict.BLOCKED,
            VerdictButtonLabel(SpeqaBundle.message("panel.run.verdict.blocked")),
            COLOR_BLOCKED_BG,
        ),
    )

    init {
        layout = BoxLayout(this, BoxLayout.X_AXIS)
        isOpaque = false
        buttons.forEachIndexed { index, btn ->
            btn.label.handCursor()
            btn.label.addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    if (e.button != MouseEvent.BUTTON1) return
                    val next = verdictAfterToggle(currentVerdict, btn.verdict)
                    currentVerdict = next
                    refreshAppearance()
                    if (!suppressProgrammaticSync) onVerdictChange(next)
                }
            })
            if (index > 0) add(Box.createHorizontalStrut(JBUI.scale(4)))
            add(btn.label)
        }
        refreshAppearance()
    }

    /**
     * Updates the displayed verdict without firing [onVerdictChange].
     * Used by the host component to push external state changes.
     */
    fun setVerdict(verdict: StepVerdict) {
        suppressProgrammaticSync = true
        try {
            currentVerdict = verdict
            refreshAppearance()
        } finally {
            suppressProgrammaticSync = false
        }
    }

    private fun refreshAppearance() {
        for (btn in buttons) {
            val selected = btn.verdict == currentVerdict
            if (selected) {
                btn.label.fillColor = btn.tintBg
                btn.label.foreground = COLOR_SELECTED_FG
                btn.label.border = BorderFactory.createCompoundBorder(
                    RoundedLineBorder(btn.tintBg, JBUI.scale(8), 1),
                    BorderFactory.createEmptyBorder(
                        JBUI.scale(4), JBUI.scale(10), JBUI.scale(4), JBUI.scale(10),
                    ),
                )
            } else {
                btn.label.fillColor = null
                // Use the normal Label foreground for unselected chips so they
                // read as clickable. Previously `Label.disabledForeground`
                // made them look greyed-out / disabled.
                btn.label.foreground = JBColor.namedColor("Label.foreground", JBColor.foreground())
                btn.label.border = BorderFactory.createCompoundBorder(
                    RoundedLineBorder(JBColor.border(), JBUI.scale(8), 1),
                    BorderFactory.createEmptyBorder(
                        JBUI.scale(4), JBUI.scale(10), JBUI.scale(4), JBUI.scale(10),
                    ),
                )
            }
        }
        repaint()
    }
}
