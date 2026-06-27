package io.github.barsia.speqa.editor.ui.run

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.JBColor
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import io.github.barsia.speqa.editor.ui.primitives.handCursor
import io.github.barsia.speqa.editor.ui.theme.SpeqaThemeColors
import io.github.barsia.speqa.model.RunResult
import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Point
import java.awt.RenderingHints
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BoxLayout
import javax.swing.JPanel
import javax.swing.KeyStroke

/**
 * Small rounded pill displaying a [RunResult]. Clicking it (or activating it from the keyboard)
 * opens a chooser that overrides the result via [onPick] or re-derives it (per-case from steps, or
 * run-level from the case aggregate) via [onAuto]. Reused for both per-case section results and the
 * overall run result; the caller supplies the [tooltipText], [popupTitle], and [autoLabel] so the
 * wording matches the context (case vs. run).
 */
internal class ResultPill(
    initial: RunResult,
    private val tooltipText: String,
    private val popupTitle: String,
    private val autoLabel: String,
    private val onPick: (RunResult) -> Unit,
    private val onAuto: () -> Unit,
    private val dataContextProject: Project,
) : JPanel() {
    private var result: RunResult = initial
    private val label = JBLabel()

    init {
        isOpaque = false
        layout = BoxLayout(this, BoxLayout.X_AXIS)
        border = JBUI.Borders.empty(2, 8)
        label.foreground = SpeqaThemeColors.verdictSelectedForeground
        add(label)
        applyResult()
        handCursor()
        isFocusable = true
        addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                requestFocusInWindow()
                showChooser(Point(e.x, e.y))
            }
        })
        getInputMap(WHEN_FOCUSED).apply {
            put(KeyStroke.getKeyStroke("SPACE"), ACTIVATE_KEY)
            put(KeyStroke.getKeyStroke("ENTER"), ACTIVATE_KEY)
        }
        actionMap.put(ACTIVATE_KEY, object : javax.swing.AbstractAction() {
            override fun actionPerformed(e: java.awt.event.ActionEvent?) {
                showChooser(Point(0, height))
            }
        })
    }

    fun setResult(value: RunResult) {
        if (result == value) return
        result = value
        applyResult()
        revalidate()
        repaint()
    }

    private fun humanize(value: RunResult): String =
        value.label.replace('_', ' ').replaceFirstChar { it.uppercase() }

    private fun applyResult() {
        label.text = humanize(result)
        toolTipText = tooltipText
    }

    private fun showChooser(at: Point) {
        val group = DefaultActionGroup()
        for (option in CHOOSABLE_RESULTS) {
            group.add(object : AnAction(humanize(option)) {
                override fun getActionUpdateThread() = ActionUpdateThread.EDT
                override fun actionPerformed(e: AnActionEvent) = onPick(option)
            })
        }
        group.addSeparator()
        group.add(object : AnAction(autoLabel, null, AllIcons.General.InspectionsEye) {
            override fun getActionUpdateThread() = ActionUpdateThread.EDT
            override fun actionPerformed(e: AnActionEvent) = onAuto()
        })
        JBPopupFactory.getInstance()
            .createActionGroupPopup(
                popupTitle,
                group,
                SimpleDataContext.getProjectContext(dataContextProject),
                JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
                false,
            )
            .show(RelativePoint(this, at))
    }

    private fun backgroundColor(): Color = when (result) {
        RunResult.PASSED -> SpeqaThemeColors.verdictPassedBackground
        RunResult.FAILED -> SpeqaThemeColors.verdictFailedBackground
        RunResult.BLOCKED -> SpeqaThemeColors.verdictBlockedBackground
        RunResult.IN_PROGRESS -> JBColor.namedColor("Link.activeForeground", JBColor.BLUE)
        RunResult.NOT_STARTED -> SpeqaThemeColors.verdictSkippedBackground
    }

    override fun getMaximumSize(): Dimension = preferredSize

    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.color = backgroundColor()
            val arc = JBUI.scale(10)
            g2.fillRoundRect(0, 0, width, height, arc, arc)
        } finally {
            g2.dispose()
        }
        super.paintComponent(g)
    }

    private companion object {
        const val ACTIVATE_KEY = "speqa.runResult.pill.activate"

        /** Result options offered in the override chooser, in user-facing order. */
        val CHOOSABLE_RESULTS = listOf(
            RunResult.PASSED,
            RunResult.FAILED,
            RunResult.BLOCKED,
            RunResult.IN_PROGRESS,
            RunResult.NOT_STARTED,
        )
    }
}
