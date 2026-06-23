// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package io.github.barsia.speqa.editor.ui

import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import io.github.barsia.speqa.SpeqaBundle
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.BorderFactory
import javax.swing.JPanel
import javax.swing.UIManager

/**
 * Floating header bar shown at the top of the preview viewport when the
 * primary `HeaderUtilityRow` scrolls off-screen. Single line containing the
 * composed title ("TC-12 . Login flow" / "Untitled") on the left and an
 * optional progress label ("Progress: 2/5") on the right.
 *
 * The bar is opaque and paints its own background; `FloatingHeaderHost`
 * positions it absolutely above the scroll pane via z-order.
 */
class FloatingHeaderBar : JPanel(BorderLayout(JBUI.scale(12), 0)) {

    private val titleLabel = JBLabel("").apply {
        foreground = UIManager.getColor("Label.foreground") ?: JBColor.foreground()
        border = JBUI.Borders.empty(0, JBUI.scale(14), 0, JBUI.scale(8))
    }

    private val progressLabel = JBLabel("").apply {
        foreground = UIManager.getColor("Label.disabledForeground")
            ?: JBColor.namedColor("Label.disabledForeground", JBColor.GRAY)
        border = JBUI.Borders.empty(0, 0, 0, JBUI.scale(14))
        isVisible = false
    }

    init {
        isOpaque = true
        applyThemeColors()
        add(titleLabel, BorderLayout.CENTER)
        add(progressLabel, BorderLayout.EAST)
    }

    override fun getPreferredSize(): Dimension {
        val superPref = super.getPreferredSize()
        return Dimension(superPref.width, JBUI.scale(26))
    }

    /**
     * Update the displayed title.
     */
    fun setTitle(idPrefix: String, id: String, title: String) {
        titleLabel.text = floatingHeaderText(
            idPrefix = idPrefix,
            id = id,
            title = title,
            untitled = SpeqaBundle.message("floatingHeader.untitled"),
        )
    }

    /**
     * Update the trailing progress text. Passing `null` or blank hides the
     * label entirely; non-blank shows it.
     */
    fun setProgress(text: String?) {
        if (text.isNullOrBlank()) {
            progressLabel.isVisible = false
            progressLabel.text = ""
        } else {
            progressLabel.text = text
            progressLabel.isVisible = true
        }
    }

    fun refreshTheme() {
        applyThemeColors()
        repaint()
    }

    private fun applyThemeColors() {
        titleLabel.foreground = UIManager.getColor("Label.foreground") ?: JBColor.foreground()
        progressLabel.foreground = UIManager.getColor("Label.disabledForeground")
            ?: JBColor.namedColor("Label.disabledForeground", JBColor.GRAY)
        background = UIManager.getColor("Panel.background") ?: JBColor.PanelBackground
        // Subtle bottom separator. Don't use Separator.foreground - it's a
        // heavy panel-divider colour and reads as a thick band against the
        // editor background. JBColor.border() picks the same hairline shade
        // IDE menus/tooltips use.
        border = BorderFactory.createMatteBorder(0, 0, 1, 0, JBColor.border())
    }
}
