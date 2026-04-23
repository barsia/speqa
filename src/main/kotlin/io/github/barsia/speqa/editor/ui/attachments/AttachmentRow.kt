@file:Suppress("DEPRECATION")

package io.github.barsia.speqa.editor.ui.attachments

import com.intellij.icons.AllIcons
import com.intellij.ide.ui.laf.darcula.DarculaUIUtil
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.Alarm
import com.intellij.util.IconUtil
import com.intellij.util.ui.JBUI
import io.github.barsia.speqa.SpeqaBundle
import io.github.barsia.speqa.editor.AttachmentSupport
import io.github.barsia.speqa.editor.ui.primitives.handCursor
import io.github.barsia.speqa.editor.ui.primitives.speqaIconButton
import io.github.barsia.speqa.model.Attachment
import java.awt.BorderLayout
import java.awt.Color
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

/** Hover delay before preview popover appears. */
private const val HOVER_DELAY_MS: Int = 250

/** Shared slot that ensures at most one attachment preview popup is visible at a time. */
internal class PopupSlot {
    var popup: com.intellij.openapi.ui.popup.JBPopup? = null
    fun cancelAndClear() {
        popup?.cancel()
        popup = null
    }
}

/**
 * Accessible clickable row rendering a single [Attachment]. The row opens the
 * referenced file on Enter/Space/click (or triggers [onRelink] when
 * [isMissing]) and exposes a trailing delete button.
 *
 * Hover for [HOVER_DELAY_MS] with the mouse inside the label area shows a
 * preview popover built by [attachmentPreviewPopover]. Leaving the row or
 * clicking cancels the pending popover.
 */
internal class AttachmentRow(
    private val attachment: Attachment,
    private val project: Project,
    private val tcFile: VirtualFile,
    private val isMissing: Boolean,
    private val popupSlot: PopupSlot = PopupSlot(),
    private val onDelete: () -> Unit,
    private val onRelink: (() -> Unit)? = null,
) : JPanel(BorderLayout(JBUI.scale(4), 0)) {

    private var focusedRing: Boolean = false
    private val hoverAlarm = Alarm(Alarm.ThreadToUse.SWING_THREAD)
    private var myPopup: com.intellij.openapi.ui.popup.JBPopup? = null

    init {
        isOpaque = false
        isFocusable = true
        handCursor()

        val fileName = attachment.path.substringAfterLast('/')
        val resolved: VirtualFile? = runReadAction { AttachmentSupport.resolveFile(project, tcFile, attachment) }
        val icon = when {
            resolved != null -> try {
                IconUtil.getIcon(resolved, 0, null)
            } catch (_: Throwable) {
                AllIcons.FileTypes.Any_type
            }
            else -> AllIcons.FileTypes.Any_type
        }
        val iconLabel = JBLabel(icon).apply {
            border = JBUI.Borders.emptyRight(4)
        }
        add(iconLabel, BorderLayout.WEST)

        val displayName = if (isMissing) {
            fileName + SpeqaBundle.message("attachment.missing.suffix")
        } else {
            fileName
        }
        val nameLabel = JBLabel(displayName).apply {
            minimumSize = Dimension(0, preferredSize.height)
            toolTipText = if (isMissing) {
                SpeqaBundle.message("tooltip.attachmentMissing", fileName)
            } else {
                fileName
            }
        }
        if (isMissing) {
            val errorColor: Color = JBColor.namedColor("Component.errorFocusColor", JBColor.RED)
            nameLabel.foreground = errorColor
            iconLabel.isEnabled = false
        }
        add(nameLabel, BorderLayout.CENTER)

        val deleteButton = speqaIconButton(
            icon = AllIcons.Actions.GC,
            tooltip = SpeqaBundle.message("tooltip.removeAttachment"),
            muted = true,
            onAction = onDelete,
        )
        // Suppress the attachment hover preview while the cursor is over the
        // delete button so the native button tooltip can surface instead.
        deleteButton.addMouseListener(object : MouseAdapter() {
            override fun mouseEntered(e: MouseEvent) {
                cancelPendingPopup()
            }
        })
        val actions = JPanel(FlowLayout(FlowLayout.RIGHT, JBUI.scale(2), 0)).apply {
            isOpaque = false
            add(deleteButton)
        }
        add(actions, BorderLayout.EAST)

        val mouseAdapter = object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    cancelPendingPopup()
                    requestFocusInWindow()
                    activate()
                }
            }

            override fun mouseEntered(e: MouseEvent) {
                schedulePopup()
            }

            override fun mouseExited(e: MouseEvent) {
                // Re-evaluate — exiting into a child component fires exit here too.
                // Also keep the popup alive if the cursor moved into the popup window.
                val inside = containsOnScreen(e) || isCursorInsideCurrentPopup()
                if (!inside) {
                    cancelPendingPopup()
                }
            }
        }
        addMouseListener(mouseAdapter)
        addMouseMotionListener(mouseAdapter)

        addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                when (e.keyCode) {
                    KeyEvent.VK_ENTER, KeyEvent.VK_SPACE -> {
                        activate()
                        e.consume()
                    }
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
        if (isMissing && onRelink != null) {
            onRelink.invoke()
            return
        }
        val file = runReadAction { AttachmentSupport.resolveFile(project, tcFile, attachment) } ?: return
        OpenFileDescriptor(project, file).navigate(true)
    }

    private fun schedulePopup() {
        // Don't re-schedule if our popup is still alive (prevents flicker when
        // the cursor briefly moves from the popup back onto the row).
        if (myPopup?.isDisposed == false) return
        hoverAlarm.cancelAllRequests()
        hoverAlarm.addRequest({
            if (!isShowing) return@addRequest
            if (!isMouseCurrentlyInside()) return@addRequest
            showPopup()
        }, HOVER_DELAY_MS)
    }

    private fun cancelPendingPopup() {
        hoverAlarm.cancelAllRequests()
        myPopup = null
        popupSlot.cancelAndClear()
    }

    private fun showPopup() {
        popupSlot.cancelAndClear()
        val popup = attachmentPreviewPopover(attachment, project, tcFile)
        myPopup = popup
        popupSlot.popup = popup
        popup.addListener(object : com.intellij.openapi.ui.popup.JBPopupListener {
            override fun onClosed(event: com.intellij.openapi.ui.popup.LightweightWindowEvent) {
                if (myPopup === popup) myPopup = null
                if (popupSlot.popup === popup) popupSlot.popup = null
            }
        })
        popup.showUnderneathOf(this)
        // Dismiss the popup when the cursor leaves the popup window, unless it
        // re-entered the row (mouseEntered will reschedule / keep popup alive).
        val popupWindow = SwingUtilities.getWindowAncestor(popup.content)
        popupWindow?.addMouseListener(object : MouseAdapter() {
            override fun mouseExited(e: MouseEvent) {
                if (!isMouseCurrentlyInside() && !isCursorInsideCurrentPopup()) {
                    cancelPendingPopup()
                }
            }
        })
    }

    private fun isCursorInsideCurrentPopup(): Boolean {
        val content = myPopup?.content ?: return false
        if (!content.isShowing) return false
        val window = SwingUtilities.getWindowAncestor(content) ?: return false
        val pi = java.awt.MouseInfo.getPointerInfo() ?: return false
        return window.bounds.contains(pi.location)
    }

    private fun isMouseCurrentlyInside(): Boolean {
        val pi = java.awt.MouseInfo.getPointerInfo() ?: return false
        return containsOnScreenPoint(pi.location)
    }

    private fun containsOnScreen(e: MouseEvent): Boolean {
        val onScreen = e.locationOnScreen
        return containsOnScreenPoint(onScreen)
    }

    private fun containsOnScreenPoint(p: java.awt.Point): Boolean {
        if (!isShowing) return false
        val origin = locationOnScreen
        return p.x in origin.x..(origin.x + width) && p.y in origin.y..(origin.y + height)
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

    override fun removeNotify() {
        cancelPendingPopup()
        super.removeNotify()
    }
}
