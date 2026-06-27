package io.github.barsia.speqa.editor.ui.run

import com.intellij.ide.ui.LafManagerListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.colors.EditorColorsListener
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.messages.MessageBusConnection
import com.intellij.util.ui.JBUI
import io.github.barsia.speqa.SpeqaBundle
import io.github.barsia.speqa.editor.ui.InlineEditableTitleRow
import io.github.barsia.speqa.editor.ui.primitives.handCursor
import io.github.barsia.speqa.editor.ui.primitives.manualResultIndicator
import io.github.barsia.speqa.editor.ui.primitives.singleLineInput
import io.github.barsia.speqa.editor.ui.primitives.twoColumnRow
import io.github.barsia.speqa.model.RunResult
import io.github.barsia.speqa.model.TestRun
import io.github.barsia.speqa.run.RunProgressText
import io.github.barsia.speqa.run.TestRunSupport
import java.awt.Component
import java.awt.Dimension
import java.awt.Rectangle
import java.awt.FlowLayout
import javax.swing.BoxLayout
import javax.swing.DefaultListCellRenderer
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.UIManager
import javax.swing.Scrollable
import javax.swing.SwingConstants
import javax.swing.SwingUtilities

/**
 * Isolated editor for a multi-case `.tr.md` run. Renders a minimal run header
 * (editable title, runner, and a basic aggregate-result indicator) above a
 * [RunCasesContainer] listing one collapsible [RunCaseSection] per case.
 *
 * Single-case (and empty) runs do NOT use this panel — they keep the flat
 * `TestCasePanel` editor. Every edit funnels through [onRunChange] as a whole
 * updated [TestRun] (full reserialize); there is no per-case patch path. The
 * header also shows a de-emphasized "N / M cases done" progress count derived
 * from [io.github.barsia.speqa.run.RunProgressText.caseProgress].
 */
class RunMultiCasePanel(
    private val project: Project,
    private val file: VirtualFile?,
    initial: TestRun,
    private val onRunChange: (TestRun) -> Unit,
    private val onHeaderStateChanged: (idPrefix: String, id: String, title: String) -> Unit = { _, _, _ -> },
) : JPanel(), Scrollable {

    private var current: TestRun = initial
    private var displayed: TestRun = initial
    private var suppressProgrammaticSync: Boolean = false

    private val titleRow = InlineEditableTitleRow(
        initialTitle = initial.title,
        placeholder = SpeqaBundle.message("panel.run.title.placeholder"),
        onCommit = { newTitle ->
            if (newTitle != current.title) emitRun(current.copy(title = newTitle))
        },
    )

    // Overall-result dropdown, same control as the single-case run. It shows the effective result
    // (computed aggregate by default). Picking a real result pins a manual run-level override that
    // holds regardless of the per-case results and never touches the per-case pills; picking
    // "Not started" clears the override back to the computed aggregate.
    private val resultCombo = ComboBox(RunResult.entries.toTypedArray()).apply {
        toolTipText = SpeqaBundle.message("panel.run.verdict")
        handCursor()
        renderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: JList<*>?, value: Any?, index: Int, isSelected: Boolean, cellHasFocus: Boolean,
            ): Component {
                val text = (value as? RunResult)?.label?.replace('_', ' ')?.replaceFirstChar { it.uppercase() } ?: ""
                return super.getListCellRendererComponent(list, text, index, isSelected, cellHasFocus)
            }
        }
        selectedItem = TestRunSupport.effectiveRunResult(initial)
        addActionListener {
            if (suppressProgrammaticSync) return@addActionListener
            val picked = selectedItem as? RunResult ?: return@addActionListener
            if (picked == TestRunSupport.effectiveRunResult(current)) return@addActionListener
            val updated = if (picked == RunResult.NOT_STARTED) {
                TestRunSupport.clearRunOverride(current)
            } else {
                TestRunSupport.overrideRunResult(current, picked)
            }
            emitRun(updated)
        }
    }

    /** Muted icon-only marker shown only while the overall run result is manually overridden. */
    private val runManualIndicator = manualResultIndicator(
        SpeqaBundle.message("runResult.manual.tooltip"),
    ).apply { isVisible = initial.manualResult }

    private val progressLabel = JBLabel(progressText(initial)).apply {
        foreground = UIManager.getColor("Label.disabledForeground")
    }

    private val resultBody = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(8), 0)).apply {
        isOpaque = false
        add(resultCombo)
        add(runManualIndicator)
        add(progressLabel)
    }

    private val runnerField: JBTextField = singleLineInput(
        placeholder = SpeqaBundle.message("placeholder.runner"),
        onChange = { text ->
            if (suppressProgrammaticSync) return@singleLineInput
            if (text != current.runner) emitRun(current.copy(runner = text))
        },
    ).apply { text = initial.runner }

    private val container = RunCasesContainer(project, file, initial) { updated -> emitRun(updated) }

    init {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        border = JBUI.Borders.empty(12)
        applyBackground()
        buildLayout()
        onHeaderStateChanged("TR-", initial.id?.toString() ?: "", initial.title)
    }

    private fun buildLayout() {
        val sectionGap = JBUI.scale(10)

        titleRow.alignmentX = Component.LEFT_ALIGNMENT
        add(titleRow)
        add(javax.swing.Box.createVerticalStrut(sectionGap))

        val headerRow = twoColumnRow(
            leftCaption = SpeqaBundle.message("label.runResult"),
            rightCaption = SpeqaBundle.message("panel.run.runner"),
            leftBody = resultBody,
            rightBody = runnerField,
        )
        headerRow.alignmentX = Component.LEFT_ALIGNMENT
        add(headerRow)
        add(javax.swing.Box.createVerticalStrut(JBUI.scale(16)))

        container.alignmentX = Component.LEFT_ALIGNMENT
        add(container)
    }

    private fun emitRun(updated: TestRun) {
        // Keep the stored run result aligned with the aggregate unless the user pinned a manual
        // override; an override is preserved across case edits.
        val normalized = TestRunSupport.syncAggregateResult(updated)
        current = normalized
        onRunChange(normalized)
    }

    /** Refresh header fields and delegate per-case refresh to the container. */
    fun updateFromRun(run: TestRun, forceFocusedTextSync: Boolean = false) {
        val previous = displayed
        displayed = run
        current = run

        if (previous.title != run.title) titleRow.setTitle(run.title, flash = false)
        onHeaderStateChanged("TR-", run.id?.toString() ?: "", run.title)

        if (previous.runner != run.runner) {
            syncProgrammaticUiChange {
                if (runnerField.text != run.runner) runnerField.text = run.runner
            }
        }

        syncProgrammaticUiChange {
            val effective = TestRunSupport.effectiveRunResult(run)
            if (resultCombo.selectedItem != effective) resultCombo.selectedItem = effective
        }
        runManualIndicator.isVisible = run.manualResult
        progressLabel.text = progressText(run)

        container.update(run)
    }

    /** Y of the header bottom (runner/result row), used by the floating-header host. */
    fun titleRowBottomY(): Int {
        if (titleRow.height <= 0) return 0
        return titleRow.y + titleRow.height
    }

    fun refreshTheme() {
        SwingUtilities.updateComponentTreeUI(this)
        applyBackground()
        revalidate()
        repaint()
    }

    private fun progressText(run: TestRun): String {
        val (done, total) = RunProgressText.caseProgress(run.cases)
        return SpeqaBundle.message("run.progress.casesDone", done, total)
    }

    private inline fun syncProgrammaticUiChange(block: () -> Unit) {
        suppressProgrammaticSync = true
        try {
            block()
        } finally {
            suppressProgrammaticSync = false
        }
    }

    private var connection: MessageBusConnection? = null

    override fun addNotify() {
        super.addNotify()
        connection?.disconnect()
        val bus = ApplicationManager.getApplication().messageBus.connect()
        bus.subscribe(LafManagerListener.TOPIC, LafManagerListener { refreshTheme() })
        bus.subscribe(EditorColorsManager.TOPIC, EditorColorsListener { refreshTheme() })
        connection = bus
    }

    override fun removeNotify() {
        connection?.disconnect()
        connection = null
        super.removeNotify()
    }

    private fun applyBackground() {
        background = EditorColorsManager.getInstance().let { manager ->
            (manager.activeVisibleScheme ?: manager.globalScheme).defaultBackground
        }
        isOpaque = true
    }

    // --- Scrollable: stretch to viewport width like TestCasePanel ----------
    override fun getPreferredScrollableViewportSize(): Dimension = preferredSize
    override fun getScrollableUnitIncrement(visibleRect: Rectangle, orientation: Int, direction: Int): Int =
        JBUI.scale(16)
    override fun getScrollableBlockIncrement(visibleRect: Rectangle, orientation: Int, direction: Int): Int =
        if (orientation == SwingConstants.VERTICAL) visibleRect.height else visibleRect.width
    override fun getScrollableTracksViewportWidth(): Boolean = true
    override fun getScrollableTracksViewportHeight(): Boolean = false
}
