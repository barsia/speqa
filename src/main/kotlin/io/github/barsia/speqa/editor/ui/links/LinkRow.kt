package io.github.barsia.speqa.editor.ui.links

import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.ide.ui.laf.darcula.DarculaUIUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.IconLoader
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import io.github.barsia.speqa.SpeqaBundle
import io.github.barsia.speqa.editor.ui.AddEditLinkDialog
import io.github.barsia.speqa.editor.ui.primitives.handCursor
import io.github.barsia.speqa.editor.ui.primitives.speqaIconButton
import io.github.barsia.speqa.model.Link
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.accessibility.AccessibleContext
import javax.accessibility.AccessibleRole
import javax.swing.JPanel
import javax.swing.SwingUtilities

private val HTTP_URL_REGEX = Regex("^https?://.*")

/**
 * Accessible clickable row rendering a single [Link]. Enter/Space/click opens
 * the URL in an external browser via [BrowserUtil] if the URL has an `http(s)`
 * scheme. Trailing edit + delete buttons. Delete shows a confirmation dialog.
 */
internal class LinkRow(
    private val link: Link,
    private val project: Project?,
    private val onEdited: (Link) -> Unit,
    private val onDelete: () -> Unit,
) : JPanel(BorderLayout(JBUI.scale(4), 0)) {

    private var focusedRing: Boolean = false

    init {
        isOpaque = false
        isFocusable = true
        handCursor()

        val linkIcon = IconLoader.getIcon("/icons/chainLink.svg", LinkRow::class.java)
        val iconLabel = JBLabel(linkIcon).apply {
            border = JBUI.Borders.emptyRight(4)
        }
        add(iconLabel, BorderLayout.WEST)

        val title = link.title.ifBlank { link.url }
        val titleLabel = JBLabel(title).apply {
            foreground = JBColor.namedColor("Link.activeForeground", JBColor.BLUE)
            toolTipText = link.url
            minimumSize = Dimension(0, preferredSize.height)
        }
        add(titleLabel, BorderLayout.CENTER)

        val actions = JPanel(FlowLayout(FlowLayout.RIGHT, JBUI.scale(2), 0)).apply { isOpaque = false }
        val editButton = speqaIconButton(
            icon = AllIcons.Actions.Edit,
            tooltip = SpeqaBundle.message("tooltip.edit"),
            onAction = { openEdit() },
        )
        actions.add(editButton)

        val deleteButton = speqaIconButton(
            icon = AllIcons.Actions.GC,
            tooltip = SpeqaBundle.message("tooltip.removeLink"),
            muted = true,
            onAction = { confirmAndDelete() },
        )
        actions.add(deleteButton)
        add(actions, BorderLayout.EAST)

        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    requestFocusInWindow()
                    activate()
                }
            }
        })
        addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if (e.keyCode == KeyEvent.VK_ENTER || e.keyCode == KeyEvent.VK_SPACE) {
                    activate()
                    e.consume()
                }
            }
        })
        addFocusListener(object : FocusAdapter() {
            override fun focusGained(e: FocusEvent) { focusedRing = true; repaint() }
            override fun focusLost(e: FocusEvent) { focusedRing = false; repaint() }
        })
    }

    override fun getMaximumSize(): Dimension {
        val pref = preferredSize
        return Dimension(Integer.MAX_VALUE, pref.height)
    }

    override fun getMinimumSize(): Dimension {
        val pref = preferredSize
        return Dimension(0, pref.height)
    }

    override fun getAccessibleContext(): AccessibleContext {
        if (accessibleContext == null) {
            accessibleContext = object : AccessibleJPanel() {
                override fun getAccessibleRole(): AccessibleRole = AccessibleRole.PUSH_BUTTON
            }
        }
        return accessibleContext
    }

    private fun activate() {
        if (link.url.matches(HTTP_URL_REGEX)) {
            BrowserUtil.browse(link.url)
        }
    }

    private fun openEdit() {
        ApplicationManager.getApplication().invokeLater {
            val edited = AddEditLinkDialog.show(project, editLink = link)
            if (edited != null) {
                onEdited(edited)
            }
        }
    }

    private fun confirmAndDelete() {
        val result = Messages.showOkCancelDialog(
            SpeqaBundle.message("dialog.removeLink.message"),
            SpeqaBundle.message("dialog.removeLink.title"),
            Messages.getOkButton(),
            Messages.getCancelButton(),
            Messages.getWarningIcon(),
        )
        if (result == Messages.OK) {
            onDelete()
        }
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        if (focusedRing) {
            val g2 = g.create() as Graphics2D
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                val arc = JBUI.scale(4).toFloat()
                DarculaUIUtil.paintFocusBorder(g2, width, height, arc, true)
            } finally {
                g2.dispose()
            }
        }
    }
}
