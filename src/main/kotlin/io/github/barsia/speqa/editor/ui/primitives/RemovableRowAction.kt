package io.github.barsia.speqa.editor.ui.primitives

import com.intellij.icons.AllIcons
import com.intellij.util.ui.JBUI
import java.awt.Container
import java.awt.Dimension
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.lang.ref.WeakReference
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.Timer

fun shouldShowRemovableRowAction(
    hasAction: Boolean,
    rowHovered: Boolean,
    rowFocused: Boolean,
    actionFocused: Boolean,
): Boolean = hasAction && (rowHovered || rowFocused || actionFocused)

class RemovableRowActionSlot(
    tooltip: String,
    private val onAction: () -> Unit,
) : JPanel(null) {

    val actionComponent: JComponent = speqaIconButton(
        icon = AllIcons.Actions.Close,
        tooltip = tooltip,
        muted = true,
        danger = true,
        onAction = onAction,
    )

    private var rowHovered = false
    private var rowFocused = false
    private var actionFocused = false
    private var hoverRoot: JComponent? = null

    init {
        isOpaque = false
        add(actionComponent)
        actionComponent.isFocusable = false
        actionComponent.isVisible = false
        actionComponent.addFocusListener(object : FocusAdapter() {
            override fun focusGained(e: FocusEvent) {
                actionFocused = true
                refreshVisibility()
            }

            override fun focusLost(e: FocusEvent) {
                actionFocused = false
                refreshVisibility()
            }
        })
    }

    fun setRowInteraction(hovered: Boolean = rowHovered, focused: Boolean = rowFocused) {
        if (hovered) {
            activeHoverSlot?.get()?.takeIf { it !== this }?.clearHover()
            activeHoverSlot = WeakReference(this)
            hoverReconcileTimer.start()
        } else if (activeHoverSlot?.get() === this) {
            activeHoverSlot = null
        }
        rowHovered = hovered
        rowFocused = focused
        refreshVisibility()
        if (activeHoverSlot?.get() == null) hoverReconcileTimer.stop()
    }

    fun bindHoverRoot(root: JComponent) {
        hoverRoot = root
    }

    override fun getPreferredSize(): Dimension = JBUI.size(22, 22)

    override fun getMinimumSize(): Dimension = preferredSize

    override fun getMaximumSize(): Dimension = preferredSize

    override fun doLayout() {
        actionComponent.setBounds(0, 0, width, height)
    }

    private fun refreshVisibility() {
        actionComponent.isVisible = shouldShowRemovableRowAction(
            hasAction = true,
            rowHovered = rowHovered,
            rowFocused = rowFocused,
            actionFocused = actionFocused,
        )
        repaint()
    }

    private fun clearHover() {
        rowHovered = false
        refreshVisibility()
    }

    private fun isPointerInsideHoverRoot(): Boolean =
        hoverRoot?.let { isCursorInsideComponent(it) } ?: false

    companion object {
        private var activeHoverSlot: WeakReference<RemovableRowActionSlot>? = null
        private val hoverReconcileTimer: Timer = Timer(40) {
            reconcileActiveHoverWithPointer()
        }.apply {
            isRepeats = true
        }

        private fun reconcileActiveHoverWithPointer() {
            val slot = activeHoverSlot?.get()
            when {
                slot == null -> hoverReconcileTimer.stop()
                !slot.isPointerInsideHoverRoot() -> slot.setRowInteraction(hovered = false)
            }
        }
    }
}

fun installRemovableRowActionVisibility(
    row: JComponent,
    slot: RemovableRowActionSlot,
    rowFocused: () -> Boolean,
) {
    slot.bindHoverRoot(row)
    val listener = object : MouseAdapter() {
        override fun mouseEntered(e: MouseEvent) {
            slot.setRowInteraction(hovered = true, focused = rowFocused())
        }

        override fun mouseExited(e: MouseEvent) {
            SwingUtilities.invokeLater {
                slot.setRowInteraction(
                    hovered = isCursorInsideComponent(row),
                    focused = rowFocused(),
                )
            }
        }
    }
    attachRecursively(row, listener)
    slot.setRowInteraction(hovered = isCursorInsideComponent(row), focused = rowFocused())
}

private fun isCursorInsideComponent(component: JComponent): Boolean {
    if (!component.isShowing) return false
    val pointer = java.awt.MouseInfo.getPointerInfo()?.location ?: return false
    val origin = component.locationOnScreen
    return pointer.x >= origin.x &&
        pointer.y >= origin.y &&
        pointer.x < origin.x + component.width &&
        pointer.y < origin.y + component.height
}

private fun attachRecursively(component: java.awt.Component, listener: MouseAdapter) {
    component.addMouseListener(listener)
    if (component is Container) {
        for (child in component.components) attachRecursively(child, listener)
    }
}
