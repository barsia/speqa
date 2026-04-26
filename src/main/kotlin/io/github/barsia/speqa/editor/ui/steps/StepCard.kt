package io.github.barsia.speqa.editor.ui.steps

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.JBColor
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import io.github.barsia.speqa.SpeqaBundle
import io.github.barsia.speqa.editor.ui.primitives.CommitFlash
import io.github.barsia.speqa.editor.ui.primitives.handCursor
import io.github.barsia.speqa.editor.ui.primitives.multiLineInput
import io.github.barsia.speqa.model.Attachment
import io.github.barsia.speqa.model.Link
import io.github.barsia.speqa.model.TestStep
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.Graphics
import java.awt.FlowLayout
import java.awt.Font
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.GridLayout
import java.awt.Insets
import java.awt.Container
import java.awt.event.ContainerAdapter
import java.awt.event.ContainerEvent
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.awt.event.FocusListener
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseListener
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.event.DocumentEvent

/** Run vs case — reserved for Step 5 wiring when verdict controls land. */
enum class StepMode { CASE, RUN }

/**
 * Swing port of `editor/ui/StepCard.kt`. A single test-scenario step: numeric
 * index + drag handle on the left; action / expected multiline inputs;
 * StepMetaRow across the bottom of the content column; delete button on the
 * right. Tab order follows Swing insertion order — no manual focus-requester
 * chains.
 */
class StepCard(
    initialStep: TestStep,
    initialIndex: Int,
    private val project: Project?,
    private val tcFile: VirtualFile?,
    private val mode: StepMode = StepMode.CASE,
    private val onChange: (TestStep) -> Unit,
    private val onDelete: () -> Unit,
    private val onMoveUp: (() -> Unit)? = null,
    private val onMoveDown: (() -> Unit)? = null,
    private val onDuplicate: (() -> Unit)? = null,
    private val canMoveUp: () -> Boolean = { false },
    private val canMoveDown: () -> Boolean = { false },
) : JPanel(GridBagLayout()) {

    /** Drag handle exposed so `StepsSection` can attach `DragReorderSupport`. */
    val dragHandle: JComponent
    private val dragIcon = IconLoader.getIcon("/icons/dragHandle.svg", StepCard::class.java)
    val actionArea: JBTextArea
    private val indexLabel: JBLabel
    private val contentPanel: JPanel
    private val expectedContainer: JPanel
    private var expectedArea: JBTextArea? = null
    private var expectedAddButton: JComponent? = null
    private var step: TestStep = initialStep
    private var suppressProgrammaticSync = false
    private val metaRow: StepMetaRow

    // Must be declared before init so they are non-null when installDragHandleHoverVisibility() runs.
    private var hoverCount = 0
    private var focusedDescendants = 0
    private var dragHandleVisible = false

    private val hoverListener: MouseListener = object : MouseAdapter() {
        override fun mouseEntered(e: MouseEvent) {
            hoverCount++
            updateDragHandleVisibility()
        }
        override fun mouseExited(e: MouseEvent) {
            if (hoverCount > 0) hoverCount--
            updateDragHandleVisibility()
        }
    }

    private val focusListener: FocusListener = object : FocusAdapter() {
        override fun focusGained(e: FocusEvent) {
            focusedDescendants++
            updateDragHandleVisibility()
        }
        override fun focusLost(e: FocusEvent) {
            if (focusedDescendants > 0) focusedDescendants--
            updateDragHandleVisibility()
        }
    }

    private val containerListener = object : ContainerAdapter() {
        override fun componentAdded(e: ContainerEvent) {
            attachHoverListenersRecursively(e.child)
        }
        override fun componentRemoved(e: ContainerEvent) {
            detachHoverListenersRecursively(e.child)
        }
    }

    init {
        isOpaque = false
        border = JBUI.Borders.empty(4, 4, 8, 4)

        // -------- gutter (index + drag handle) --------
        val gutter = JPanel()
        gutter.layout = javax.swing.BoxLayout(gutter, javax.swing.BoxLayout.Y_AXIS)
        gutter.isOpaque = false

        indexLabel = JBLabel(formatIndex(initialIndex)).apply {
            foreground = JBColor.namedColor("Label.disabledForeground", JBColor.GRAY)
            font = font.deriveFont(Font.BOLD)
            alignmentX = Component.CENTER_ALIGNMENT
        }
        gutter.add(indexLabel)

        val handleSize = Dimension(JBUI.scale(16), JBUI.scale(16))
        dragHandle = object : JPanel() {
            override fun isOpaque() = false
            override fun getPreferredSize() = handleSize
            override fun getMinimumSize() = handleSize
            override fun getMaximumSize() = handleSize
            override fun paintComponent(g: Graphics) {
                if (dragHandleVisible) {
                    val iw = dragIcon.iconWidth
                    val ih = dragIcon.iconHeight
                    if (iw <= 0 || ih <= 0) return
                    val px = ((width - iw) / 2).coerceAtLeast(0)
                    val py = ((height - ih) / 2).coerceAtLeast(0)
                    val img = com.intellij.util.ui.UIUtil.createImage(this, iw, ih, java.awt.image.BufferedImage.TYPE_INT_ARGB)
                    val ig = img.createGraphics()
                    try {
                        dragIcon.paintIcon(this, ig, 0, 0)
                        ig.composite = java.awt.AlphaComposite.SrcAtop
                        ig.color = JBColor.namedColor("Label.disabledForeground", JBColor.GRAY)
                        ig.fillRect(0, 0, iw, ih)
                    } finally {
                        ig.dispose()
                    }
                    g.drawImage(img, px, py, iw, ih, null)
                }
            }
        }.apply {
            toolTipText = SpeqaBundle.message("tooltip.dragToReorder")
            alignmentX = Component.CENTER_ALIGNMENT
            isFocusable = true
            handCursor()
            addMouseListener(object : MouseAdapter() {
                override fun mousePressed(e: MouseEvent) { maybeShowMenu(e) }
                override fun mouseReleased(e: MouseEvent) { maybeShowMenu(e) }
                private fun maybeShowMenu(e: MouseEvent) {
                    if (e.isPopupTrigger) {
                        showHandleMenu(e)
                        e.consume()
                    }
                }
            })
        }
        gutter.add(dragHandle)

        // -------- content column --------
        contentPanel = JPanel()
        contentPanel.layout = javax.swing.BoxLayout(contentPanel, javax.swing.BoxLayout.Y_AXIS)
        contentPanel.isOpaque = false

        // Action + Expected side-by-side. GridLayout (not GridBag) because
        // GridBag with equal weightx distributes EXTRA space equally — it
        // does NOT produce a true 50/50 split when children have different
        // preferred widths. That made the split drift when the user typed
        // (JBTextArea pref width is content-sensitive) and differ between
        // empty-state ("Set expected" button ~80px pref) and filled-state
        // (text area ~20px pref). GridLayout forces strictly equal cells.
        val actionExpected = JPanel(GridLayout(1, 2, JBUI.scale(12), 0))
        actionExpected.isOpaque = false
        actionExpected.alignmentX = Component.LEFT_ALIGNMENT

        actionArea = multiLineInput(
            rows = 1,
            placeholder = SpeqaBundle.message("placeholder.action"),
            onChange = { text ->
                if (!suppressProgrammaticSync) {
                    updateStep(step.copy(action = text))
                }
            },
        )
        actionArea.text = initialStep.action

        expectedContainer = JPanel(BorderLayout())
        expectedContainer.isOpaque = false

        actionExpected.add(actionArea)
        actionExpected.add(expectedContainer)
        rebuildExpected(initialStep.expected)

        contentPanel.add(actionExpected)
        contentPanel.add(javax.swing.Box.createVerticalStrut(JBUI.scale(6)))

        metaRow = StepMetaRow(
            project = project,
            tcFile = tcFile,
            mode = if (mode == StepMode.RUN) StepMetaMode.RUN else StepMetaMode.CASE,
            onTicketsChange = { next -> updateStep(step.copy(tickets = next)) },
            onLinksChange = { next -> updateStep(step.copy(links = next)) },
            onAttachmentsChange = { next -> updateStep(step.copy(attachments = next)) },
        )
        metaRow.setData(initialStep.tickets, initialStep.links, initialStep.attachments)
        metaRow.alignmentX = Component.LEFT_ALIGNMENT
        contentPanel.add(metaRow)

        val gutterCons = GridBagConstraints().apply {
            gridx = 0; gridy = 0; weighty = 1.0
            anchor = GridBagConstraints.NORTH
            insets = Insets(0, 0, 0, JBUI.scale(8))
        }
        val contentCons = GridBagConstraints().apply {
            gridx = 1; gridy = 0; weightx = 1.0; weighty = 1.0
            fill = GridBagConstraints.BOTH
        }
        add(gutter, gutterCons)
        add(contentPanel, contentCons)

        installDragHandleHoverVisibility()
    }

    // --- drag handle hover/focus visibility --------------------------------

    private fun updateDragHandleVisibility() {
        val show = hoverCount > 0 || focusedDescendants > 0
        if (dragHandleVisible != show) {
            dragHandleVisible = show
            dragHandle.repaint()
        }
    }

    private fun installDragHandleHoverVisibility() {
        // Dragging: keep visible while the handle itself is pressed/released
        // (the underlying DragReorderSupport wires mouse events to the handle).
        attachHoverListenersRecursively(this)
    }

    private fun attachHoverListenersRecursively(c: java.awt.Component) {
        // Avoid duplicate listeners.
        c.removeMouseListener(hoverListener)
        c.addMouseListener(hoverListener)
        c.removeFocusListener(focusListener)
        c.addFocusListener(focusListener)
        if (c is Container) {
            c.removeContainerListener(containerListener)
            c.addContainerListener(containerListener)
            for (child in c.components) attachHoverListenersRecursively(child)
        }
    }

    private fun detachHoverListenersRecursively(c: java.awt.Component) {
        c.removeMouseListener(hoverListener)
        c.removeFocusListener(focusListener)
        if (c is Container) {
            c.removeContainerListener(containerListener)
            for (child in c.components) detachHoverListenersRecursively(child)
        }
    }

    fun setIndex(index: Int) {
        indexLabel.text = formatIndex(index)
    }

    fun setStep(newStep: TestStep) {
        step = newStep
        if (!actionArea.isFocusOwner && actionArea.text != newStep.action) {
            syncProgrammaticTextChange {
                actionArea.text = newStep.action
            }
            CommitFlash.flash(actionArea)
        }
        val existingExpected = expectedArea
        when {
            newStep.expected == null && existingExpected != null -> rebuildExpected(null)
            newStep.expected != null && existingExpected == null -> rebuildExpected(newStep.expected)
            existingExpected != null &&
                !existingExpected.isFocusOwner &&
                existingExpected.text != newStep.expected -> {
                syncProgrammaticTextChange {
                    existingExpected.text = newStep.expected
                }
                CommitFlash.flash(existingExpected)
            }
        }
        metaRow.setData(newStep.tickets, newStep.links, newStep.attachments)
    }

    fun focusAction() {
        SwingUtilities.invokeLater { actionArea.requestFocusInWindow() }
    }

    private fun rebuildExpected(expected: String?) {
        expectedContainer.removeAll()
        expectedArea = null
        expectedAddButton = null
        if (expected != null) {
            val area = multiLineInput(
                rows = 1,
                placeholder = SpeqaBundle.message("placeholder.setExpected"),
                onChange = { text ->
                    if (!suppressProgrammaticSync) {
                        updateStep(step.copy(expected = text))
                    }
                },
            )
            syncProgrammaticTextChange {
                area.text = expected
            }
            expectedArea = area
            expectedContainer.add(area, BorderLayout.CENTER)
        } else {
            val addButton = buildExpectedAddButton()
            expectedAddButton = addButton
            expectedContainer.add(addButton, BorderLayout.NORTH)
        }
        expectedContainer.revalidate()
        expectedContainer.repaint()
    }

    private fun buildExpectedAddButton(): JComponent {
        val panel = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(4), 0))
        panel.isOpaque = false
        panel.handCursor()
        panel.isFocusable = true
        val label = JBLabel(SpeqaBundle.message("placeholder.setExpected"))
        label.foreground = JBColor.namedColor("Label.disabledForeground", JBColor.GRAY)
        panel.add(label)
        val spawn: () -> Unit = {
            updateStep(step.copy(expected = ""))
            rebuildExpected("")
            SwingUtilities.invokeLater { expectedArea?.requestFocusInWindow() }
        }
        panel.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    panel.requestFocusInWindow()
                    spawn()
                }
            }
        })
        panel.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if (e.keyCode == KeyEvent.VK_ENTER || e.keyCode == KeyEvent.VK_SPACE) {
                    spawn(); e.consume()
                }
            }
        })
        return panel
    }

    private fun showHandleMenu(e: MouseEvent) {
        val group = DefaultActionGroup()
        var hasItems = false
        onMoveUp?.let { action ->
            hasItems = true
            group.add(object : AnAction(SpeqaBundle.message("step.menu.moveUp")) {
                override fun getActionUpdateThread() = ActionUpdateThread.EDT
                override fun update(e: AnActionEvent) { e.presentation.isEnabled = canMoveUp() }
                override fun actionPerformed(e: AnActionEvent) { action() }
            })
        }
        onMoveDown?.let { action ->
            hasItems = true
            group.add(object : AnAction(SpeqaBundle.message("step.menu.moveDown")) {
                override fun getActionUpdateThread() = ActionUpdateThread.EDT
                override fun update(e: AnActionEvent) { e.presentation.isEnabled = canMoveDown() }
                override fun actionPerformed(e: AnActionEvent) { action() }
            })
        }
        onDuplicate?.let { action ->
            hasItems = true
            group.add(object : AnAction(SpeqaBundle.message("step.menu.duplicate")) {
                override fun getActionUpdateThread() = ActionUpdateThread.EDT
                override fun actionPerformed(e: AnActionEvent) { action() }
            })
        }
        if (hasItems) group.addSeparator()
        group.add(object : AnAction(SpeqaBundle.message("step.menu.delete")) {
            override fun getActionUpdateThread() = ActionUpdateThread.EDT
            override fun actionPerformed(e: AnActionEvent) { confirmAndDelete() }
        })
        JBPopupFactory.getInstance()
            .createActionGroupPopup(null, group, if (project != null) SimpleDataContext.getProjectContext(project) else SimpleDataContext.EMPTY_CONTEXT, JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, false)
            .show(RelativePoint(e.component, java.awt.Point(e.x, e.y)))
    }

    private fun confirmAndDelete() {
        if (!step.expected.isNullOrBlank()) {
            val result = Messages.showOkCancelDialog(
                SpeqaBundle.message("dialog.deleteStep.message"),
                SpeqaBundle.message("dialog.deleteStep.title"),
                Messages.getOkButton(),
                Messages.getCancelButton(),
                Messages.getWarningIcon(),
            )
            if (result != Messages.OK) return
        }
        onDelete()
    }

    private fun updateStep(newStep: TestStep) {
        if (newStep == step) return
        val previous = step
        step = newStep
        // When a meta-row field (tickets/links/attachments) changed locally the
        // document patch is suppressed on re-parse, so StepsSection.updateStep
        // never round-trips a fresh setStep back here. Refresh metaRow directly
        // so the newly added chip / row / file is visible in the preview.
        if (previous.tickets != newStep.tickets ||
            previous.links != newStep.links ||
            previous.attachments != newStep.attachments
        ) {
            metaRow.setData(newStep.tickets, newStep.links, newStep.attachments)
        }
        onChange(newStep)
    }

    private inline fun syncProgrammaticTextChange(block: () -> Unit) {
        suppressProgrammaticSync = true
        try {
            block()
        } finally {
            suppressProgrammaticSync = false
        }
    }

    private fun formatIndex(index: Int): String = (index + 1).toString().padStart(2, '0')

    override fun getPreferredSize(): Dimension {
        val pref = super.getPreferredSize()
        pref.width = maxOf(pref.width, JBUI.scale(360))
        return pref
    }

    // Keep each card at its content height under StepsSection's BoxLayout.
    // Without this, an editor viewport taller than the stack would stretch
    // cards to fill it, pushing the StepMetaRow (and its ticket input cell)
    // far below the action/expected fields.
    override fun getMaximumSize(): Dimension {
        val pref = preferredSize
        return Dimension(Int.MAX_VALUE, pref.height)
    }

    // Silence unused import of DocumentAdapter / DocumentEvent — they are referenced transitively via
    // multiLineInput; explicit imports kept for clarity in the file header.
    @Suppress("unused")
    private fun dummyReferenceDocumentAdapter(): DocumentAdapter = object : DocumentAdapter() {
        override fun textChanged(e: DocumentEvent) {}
    }
}
