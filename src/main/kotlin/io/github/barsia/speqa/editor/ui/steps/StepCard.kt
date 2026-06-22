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
import io.github.barsia.speqa.editor.ui.primitives.MarkdownEditablePane
import io.github.barsia.speqa.editor.ui.primitives.multiLineInput
import io.github.barsia.speqa.editor.ui.primitives.setSpeqaTooltip
import io.github.barsia.speqa.editor.ui.primitives.speqaIconButton
import io.github.barsia.speqa.model.Attachment
import io.github.barsia.speqa.model.Link
import io.github.barsia.speqa.model.StepVerdict
import io.github.barsia.speqa.model.TestStep
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.GridLayout
import java.awt.Insets
import java.awt.RenderingHints
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
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.event.DocumentEvent

/** Run vs case — reserved for Step 5 wiring when verdict controls land. */
enum class StepMode { CASE, RUN }

/**
 * Wraps a `speqaIconButton` for the step-comment toggle and overlays a small
 * 4 px accent dot on the top-right corner when [hasStoredComment] is true.
 * The actual button is created lazily via [setButtonIcon] once the comment
 * section callback is available (after the section panel is built in [StepCard.init]).
 */
private class CommentDotPanel(hasStoredComment: Boolean) : JPanel() {

    var hasStoredComment: Boolean = hasStoredComment
    var tooltip: String = ""
    private var buttonComponent: JComponent? = null

    init {
        layout = null
        isOpaque = false
        preferredSize = JBUI.size(22, 22)
        minimumSize = JBUI.size(22, 22)
        maximumSize = JBUI.size(22, 22)
    }

    fun setButtonIcon(tooltip: String, onAction: () -> Unit) {
        this.tooltip = tooltip
        val btn = speqaIconButton(
            icon = AllIcons.General.Balloon,
            tooltip = tooltip,
            muted = true,
            onAction = onAction,
        )
        btn.setBounds(0, 0, JBUI.scale(22), JBUI.scale(22))
        buttonComponent = btn
        add(btn)
        revalidate()
    }

    fun updateTooltip(tooltip: String) {
        this.tooltip = tooltip
        buttonComponent?.setSpeqaTooltip(tooltip)
    }

    override fun paint(g: Graphics) {
        super.paint(g)
        if (!hasStoredComment) return
        val g2 = g.create() as Graphics2D
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.color = JBColor.namedColor("Link.activeForeground", JBColor.BLUE)
            val dotSize = JBUI.scale(4)
            g2.fillOval(width - dotSize - JBUI.scale(2), JBUI.scale(2), dotSize, dotSize)
        } finally {
            g2.dispose()
        }
    }
}

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
    runVerdict: StepVerdict = StepVerdict.NONE,
    runComment: String = "",
    private val onVerdictChange: ((StepVerdict) -> Unit)? = null,
    private val onCommentChange: ((String) -> Unit)? = null,
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
    val actionArea: MarkdownEditablePane
    private val indexLabel: JBLabel
    private val contentPanel: JPanel
    private val expectedContainer: JPanel
    private var expectedArea: MarkdownEditablePane? = null
    private var expectedAddButton: JComponent? = null
    private var step: TestStep = initialStep
    private var suppressProgrammaticSync = false
    private val metaRow: StepMetaRow
    private var verdictRow: StepVerdictRow? = null
    private var commentArea: MarkdownEditablePane? = null
    private var commentSection: JPanel? = null
    private var commentDotPanel: CommentDotPanel? = null
    // Spec: comment field is expanded by default when the step has a stored
    // comment, collapsed otherwise. The dot indicator on the toggle icon
    // (hasStoredComment) signals saved content even when expanded.
    private var hasStoredComment: Boolean = runComment.isNotBlank()
    private var showComment: Boolean = hasStoredComment
    private var currentVerdictForPaint: StepVerdict = runVerdict

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
        // In RUN mode the card paints a left verdict strip (2 px), so the
        // content-side padding is widened to leave breathing room between the
        // strip and the index gutter / content column.
        border = if (mode == StepMode.RUN) {
            JBUI.Borders.empty(4, 12, 8, 4)
        } else {
            JBUI.Borders.empty(4, 4, 8, 4)
        }

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
            alignmentX = Component.CENTER_ALIGNMENT
            toolTipText = SpeqaBundle.message("tooltip.dragToReorder")
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

        actionArea = MarkdownEditablePane(
            project = requireNotNull(this.project) { "StepCard requires a real Project for inline markdown editors" },
            rows = 1,
            placeholder = SpeqaBundle.message("placeholder.action"),
            onChange = { text ->
                if (!suppressProgrammaticSync) {
                    updateStep(step.copy(action = text))
                }
            },
        )
        actionArea.setTextSuppressing(initialStep.action)

        // Action container with the text area pinned at the TOP (BorderLayout.NORTH).
        // GridLayout stretches both cells to the row's tallest cell height; without
        // this wrapper the action JTextArea would also stretch to match a tall
        // Expected result. NORTH keeps the action at its natural preferred height
        // (one line) and leaves the remaining cell space empty / transparent.
        val actionContainer = JPanel(BorderLayout())
        actionContainer.isOpaque = false
        actionContainer.border = JBUI.Borders.emptyRight(JBUI.scale(8))
        actionContainer.add(actionArea, BorderLayout.NORTH)

        expectedContainer = JPanel(BorderLayout())
        expectedContainer.isOpaque = false

        actionExpected.add(actionContainer)
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

        if (mode == StepMode.RUN) {
            val vRow = StepVerdictRow(runVerdict) { verdict ->
                // Repaint the left progress strip immediately on user click;
                // without this local update, the strip color would only
                // refresh after the patch round-trips through the document
                // parse and `setRunVerdict()` re-pushes it, which adds a
                // visible lag.
                currentVerdictForPaint = verdict
                repaint()
                onVerdictChange?.invoke(verdict)
            }
            verdictRow = vRow

            // Build comment toggle button wrapped in a dot-indicator panel.
            val dotPanel = CommentDotPanel(hasStoredComment)
            commentDotPanel = dotPanel

            // Wrap verdict row + toggle in a horizontal panel so the toggle
            // sits in the same row as the four verdict chips.
            val verdictAndToggleRow = JPanel()
            verdictAndToggleRow.layout = BoxLayout(verdictAndToggleRow, BoxLayout.X_AXIS)
            verdictAndToggleRow.isOpaque = false
            verdictAndToggleRow.alignmentX = Component.LEFT_ALIGNMENT
            verdictAndToggleRow.add(vRow)
            verdictAndToggleRow.add(Box.createHorizontalStrut(JBUI.scale(4)))
            verdictAndToggleRow.add(dotPanel)
            contentPanel.add(verdictAndToggleRow)

            contentPanel.add(Box.createVerticalStrut(JBUI.scale(4)))

            // Build the collapsible comment section (label + textarea).
            val section = JPanel()
            section.layout = BoxLayout(section, BoxLayout.Y_AXIS)
            section.isOpaque = false
            section.alignmentX = Component.LEFT_ALIGNMENT
            commentSection = section

            val commentLabel = JBLabel(SpeqaBundle.message("run.stepComment")).apply {
                font = font.deriveFont(Font.BOLD, 11f)
                foreground = JBColor.namedColor("Label.disabledForeground", JBColor.GRAY)
                alignmentX = Component.LEFT_ALIGNMENT
            }
            section.add(commentLabel)
            section.add(Box.createVerticalStrut(JBUI.scale(2)))

            val cArea = MarkdownEditablePane(
                project = requireNotNull(this.project) { "StepCard requires a real Project for inline markdown editors" },
                rows = 1,
                placeholder = SpeqaBundle.message("panel.run.stepComment.placeholder"),
                onChange = { text ->
                    if (suppressProgrammaticSync) return@MarkdownEditablePane
                    // Reflect the live edit in the dot indicator immediately;
                    // otherwise the dot would only appear after a round-trip
                    // through the document patch + parser + setRunComment,
                    // which is suppressed while the textarea has focus.
                    val nowHas = text.isNotBlank()
                    if (nowHas != hasStoredComment) {
                        hasStoredComment = nowHas
                        commentDotPanel?.hasStoredComment = nowHas
                        commentDotPanel?.updateTooltip(commentToggleTooltip())
                        commentDotPanel?.repaint()
                    }
                    onCommentChange?.invoke(text)
                },
            )
            cArea.setTextSuppressing(runComment)
            cArea.alignmentX = Component.LEFT_ALIGNMENT
            commentArea = cArea
            section.add(cArea)

            section.isVisible = showComment
            contentPanel.add(section)

            // Wire up the toggle button now that section and cArea exist.
            dotPanel.setButtonIcon(
                tooltip = commentToggleTooltip(),
                onAction = {
                    showComment = !showComment
                    commentSection?.isVisible = showComment
                    commentDotPanel?.updateTooltip(commentToggleTooltip())
                    contentPanel.revalidate()
                    contentPanel.repaint()
                    if (showComment) {
                        SwingUtilities.invokeLater { commentArea?.requestFocusInWindow() }
                    }
                },
            )
        }

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

    fun setStep(newStep: TestStep, forceFocusedTextSync: Boolean = false) {
        step = newStep
        val allowFocusedUpdate =
            forceFocusedTextSync ||
                io.github.barsia.speqa.editor.ui.primitives.MarkdownEditablePane.undoInProgress.get()
        if ((allowFocusedUpdate || !actionArea.isFocusOwner) && actionArea.text != newStep.action) {
            actionArea.setTextSuppressing(newStep.action)
            CommitFlash.flash(actionArea)
        }
        val existingExpected = expectedArea
        when {
            newStep.expected == null && existingExpected != null -> rebuildExpected(null)
            newStep.expected != null && existingExpected == null -> rebuildExpected(newStep.expected)
            existingExpected != null &&
                (allowFocusedUpdate || !existingExpected.isFocusOwner) &&
                existingExpected.text != newStep.expected -> {
                existingExpected.setTextSuppressing(newStep.expected.orEmpty())
                CommitFlash.flash(existingExpected)
            }
        }
        metaRow.setData(newStep.tickets, newStep.links, newStep.attachments)
    }

    fun focusAction() {
        SwingUtilities.invokeLater { actionArea.requestFocusInWindow() }
    }

    fun setRunVerdict(verdict: StepVerdict) {
        verdictRow?.setVerdict(verdict)
        currentVerdictForPaint = verdict
        repaint()
    }

    fun setRunComment(text: String, forceFocusedTextSync: Boolean = false) {
        val area = commentArea ?: return
        val allowFocusedUpdate =
            forceFocusedTextSync ||
                io.github.barsia.speqa.editor.ui.primitives.MarkdownEditablePane.undoInProgress.get()
        if (!allowFocusedUpdate && area.isFocusOwner) return
        if (area.text == text) return
        area.setTextSuppressing(text)
        hasStoredComment = text.isNotBlank()
        commentDotPanel?.hasStoredComment = hasStoredComment
        commentDotPanel?.updateTooltip(commentToggleTooltip())
        commentDotPanel?.repaint()
    }

    private fun commentToggleTooltip(): String = when {
        hasStoredComment && !showComment -> SpeqaBundle.message("run.editComment")
        showComment -> SpeqaBundle.message("run.hideCommentField")
        else -> SpeqaBundle.message("run.addComment")
    }

    private fun rebuildExpected(expected: String?) {
        expectedContainer.removeAll()
        expectedAddButton = null
        // Always render a textarea with a placeholder; never an intermediate
        // "Set expected result" button. Empty content shows the placeholder
        // hint and is normalised to `null` in the model so it round-trips out
        // of the serialised frontmatter cleanly.
        val area = MarkdownEditablePane(
            project = requireNotNull(this.project) { "StepCard requires a real Project for inline markdown editors" },
            rows = 1,
            placeholder = SpeqaBundle.message("placeholder.expected"),
            onChange = { text ->
                if (!suppressProgrammaticSync) {
                    val next: String? = if (text.isEmpty()) null else text
                    updateStep(step.copy(expected = next))
                }
            },
        )
        area.setTextSuppressing(expected.orEmpty())
        expectedArea = area
        // NORTH (not CENTER) so the textarea stays at its preferred height
        // instead of stretching when GridLayout makes the cell taller.
        expectedContainer.add(area, BorderLayout.NORTH)
        expectedContainer.revalidate()
        expectedContainer.repaint()
    }

    private fun showHandleMenu(e: MouseEvent) {
        val group = DefaultActionGroup()
        var hasItems = false
        onMoveUp?.let { action ->
            hasItems = true
            group.add(object : AnAction(
                SpeqaBundle.message("step.menu.moveUp"),
                null,
                com.intellij.icons.AllIcons.Actions.MoveUp,
            ) {
                override fun getActionUpdateThread() = ActionUpdateThread.EDT
                override fun update(e: AnActionEvent) { e.presentation.isEnabled = canMoveUp() }
                override fun actionPerformed(e: AnActionEvent) { action() }
            })
        }
        onMoveDown?.let { action ->
            hasItems = true
            group.add(object : AnAction(
                SpeqaBundle.message("step.menu.moveDown"),
                null,
                com.intellij.icons.AllIcons.Actions.MoveDown,
            ) {
                override fun getActionUpdateThread() = ActionUpdateThread.EDT
                override fun update(e: AnActionEvent) { e.presentation.isEnabled = canMoveDown() }
                override fun actionPerformed(e: AnActionEvent) { action() }
            })
        }
        onDuplicate?.let { action ->
            hasItems = true
            group.add(object : AnAction(
                SpeqaBundle.message("step.menu.duplicate"),
                null,
                com.intellij.icons.AllIcons.Actions.Copy,
            ) {
                override fun getActionUpdateThread() = ActionUpdateThread.EDT
                override fun actionPerformed(e: AnActionEvent) { action() }
            })
        }
        if (hasItems) group.addSeparator()
        group.add(object : AnAction(
            SpeqaBundle.message("step.menu.delete"),
            null,
            com.intellij.icons.AllIcons.Actions.GC,
        ) {
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

    /**
     * Paints a thin colored vertical strip on the left edge of the card when
     * the card is in [StepMode.RUN] mode and has a non-NONE verdict. The strip
     * is bounded by the card's content insets (no top / bottom padding) so its
     * height matches the visible step area exactly. Painted in [paint] (after
     * children) so no child component can overdraw it.
     */
    override fun paint(g: Graphics) {
        super.paint(g)
        if (mode != StepMode.RUN) return
        val stripColor = when (currentVerdictForPaint) {
            StepVerdict.PASSED -> COLOR_PASSED_BG
            StepVerdict.FAILED -> COLOR_FAILED_BG
            StepVerdict.SKIPPED -> COLOR_SKIPPED_BG
            StepVerdict.BLOCKED -> COLOR_BLOCKED_BG
            StepVerdict.NONE -> return
        }
        val ins = insets
        val g2 = g.create() as Graphics2D
        try {
            g2.color = stripColor
            g2.fillRect(0, ins.top, JBUI.scale(2), height - ins.top - ins.bottom)
        } finally {
            g2.dispose()
        }
    }

    // Silence unused import of DocumentAdapter / DocumentEvent — they are referenced transitively via
    // multiLineInput; explicit imports kept for clarity in the file header.
    @Suppress("unused")
    private fun dummyReferenceDocumentAdapter(): DocumentAdapter = object : DocumentAdapter() {
        override fun textChanged(e: DocumentEvent) {}
    }
}
