package io.github.barsia.speqa.editor.ui.run

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.JBColor
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import io.github.barsia.speqa.SpeqaBundle
import io.github.barsia.speqa.editor.ui.primitives.handCursor
import io.github.barsia.speqa.editor.ui.primitives.speqaIconButton
import io.github.barsia.speqa.editor.ui.theme.SpeqaThemeColors
import io.github.barsia.speqa.model.RunCase
import io.github.barsia.speqa.model.RunResult
import io.github.barsia.speqa.run.TestRunSupport
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Point
import java.awt.RenderingHints
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.KeyStroke

/**
 * Renders a single [RunCase] as a collapsible section: a header row (collapse
 * toggle, `TC-<id> <title>` label, a result pill, and a drag-handle
 * placeholder) above a [RunCaseBody]. A multi-case run renders a vertical list
 * of these (Task 8); drag-to-reorder wiring lands in Task 10. The header result
 * pill is interactive: clicking it opens a chooser that overrides the case
 * result (or re-derives it from steps), and a manual indicator is shown while
 * the result is overridden.
 *
 * Every edit inside the body produces an updated [RunCase] reported through
 * [onCaseChange].
 */
class RunCaseSection(
    project: Project,
    file: VirtualFile?,
    initial: RunCase,
    private val onCaseChange: (RunCase) -> Unit,
) : JPanel() {

    private var case: RunCase = initial
    private var expanded: Boolean = true

    private val body = RunCaseBody(project, file, initial, onCaseChange)

    private val headerLabel = JBLabel(RunCaseSectionState.headerLabel(initial))

    private val resultPill = ResultPill(
        initial = initial.result,
        onPick = { picked -> onCaseChange(TestRunSupport.overrideCaseResult(case, picked)) },
        onAuto = { onCaseChange(TestRunSupport.clearCaseOverride(case)) },
        dataContextProject = project,
    )

    /** Muted marker shown only while the case result is manually overridden. */
    private val manualIndicator = JBLabel(
        SpeqaBundle.message("runCase.result.manual.label"),
        AllIcons.General.Note,
        JBLabel.LEFT,
    ).apply {
        toolTipText = SpeqaBundle.message("runCase.result.manual.tooltip")
        foreground = JBColor.namedColor("Label.infoForeground", JBColor.GRAY)
        alignmentY = Component.CENTER_ALIGNMENT
        isVisible = RunCaseSectionState.isManual(initial)
    }

    /**
     * Drag grip for reordering whole case sections. [RunCasesContainer] attaches
     * the reusable [io.github.barsia.speqa.editor.ui.steps.DragReorderSupport]
     * gesture to this handle in the multi-case view.
     */
    val dragHandle: JComponent = JBLabel(AllIcons.General.Drag).apply {
        toolTipText = SpeqaBundle.message("runCase.dragHandle.tooltip")
        alignmentY = Component.CENTER_ALIGNMENT
        handCursor()
    }

    private val collapseToggle: JComponent = speqaIconButton(
        icon = AllIcons.General.ChevronDown,
        tooltip = SpeqaBundle.message("runCase.collapse.tooltip"),
        onAction = { setExpanded(!expanded) },
    )

    init {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        isOpaque = false
        alignmentX = Component.LEFT_ALIGNMENT
        add(buildHeader())
        add(Box.createVerticalStrut(JBUI.scale(8)))
        body.alignmentX = Component.LEFT_ALIGNMENT
        add(body)
        updateToggleIcon()
    }

    private fun buildHeader(): JComponent {
        val header = Box.createHorizontalBox()
        header.alignmentX = Component.LEFT_ALIGNMENT
        header.add(collapseToggle)
        header.add(Box.createHorizontalStrut(JBUI.scale(6)))
        headerLabel.alignmentY = Component.CENTER_ALIGNMENT
        header.add(headerLabel)
        header.add(Box.createHorizontalStrut(JBUI.scale(8)))
        resultPill.alignmentY = Component.CENTER_ALIGNMENT
        header.add(resultPill)
        header.add(Box.createHorizontalStrut(JBUI.scale(6)))
        header.add(manualIndicator)
        header.add(Box.createHorizontalGlue())
        header.add(dragHandle)
        return header
    }

    /** Refresh the section from [newCase]. */
    fun update(newCase: RunCase) {
        case = newCase
        headerLabel.text = RunCaseSectionState.headerLabel(newCase)
        resultPill.setResult(newCase.result)
        manualIndicator.isVisible = RunCaseSectionState.isManual(newCase)
        body.update(newCase)
    }

    fun setExpanded(value: Boolean) {
        if (expanded == value) return
        expanded = value
        body.isVisible = value
        updateToggleIcon()
        revalidate()
        repaint()
    }

    private fun updateToggleIcon() {
        val icon = if (expanded) AllIcons.General.ChevronDown else AllIcons.General.ChevronRight
        val tooltip = SpeqaBundle.message(
            if (expanded) "runCase.collapse.tooltip" else "runCase.expand.tooltip",
        )
        (collapseToggle as? com.intellij.openapi.actionSystem.impl.ActionButton)?.let { button ->
            button.presentation.icon = icon
            button.presentation.description = tooltip
            button.toolTipText = tooltip
        }
    }

    /**
     * Small rounded pill displaying the case [RunResult]. Clicking it (or
     * activating it from the keyboard) opens a chooser that overrides the result
     * via [onPick] or re-derives it from steps via [onAuto].
     */
    private class ResultPill(
        initial: RunResult,
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
            toolTipText = SpeqaBundle.message("runCase.result.tooltip")
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
            group.add(object : AnAction(
                SpeqaBundle.message("runCase.result.auto"),
                null,
                AllIcons.General.InspectionsEye,
            ) {
                override fun getActionUpdateThread() = ActionUpdateThread.EDT
                override fun actionPerformed(e: AnActionEvent) = onAuto()
            })
            JBPopupFactory.getInstance()
                .createActionGroupPopup(
                    SpeqaBundle.message("runCase.result.popupTitle"),
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
            const val ACTIVATE_KEY = "speqa.runCase.result.activate"

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
}
