// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package io.github.barsia.speqa.editor.ui.chips

import com.intellij.icons.AllIcons
import com.intellij.ide.ui.laf.darcula.DarculaUIUtil
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import io.github.barsia.speqa.SpeqaBundle
import io.github.barsia.speqa.editor.ui.primitives.handCursor
import io.github.barsia.speqa.editor.ui.primitives.replaceIconColor
import io.github.barsia.speqa.editor.ui.primitives.speqaMutedIconColor
import java.awt.Color
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.MouseInfo
import java.awt.Point
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.lang.ref.WeakReference
import javax.swing.BorderFactory
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.Timer

private const val TAG_CHIP_DELETE_ICON_SCALE = 0.93
private const val TAG_CHIP_DELETE_INSET = 2

/**
 * Pure key-routing function for chip keyboard shortcuts. Invokes the
 * appropriate callback based on [keyCode] and returns true if the key was
 * consumed, false otherwise. Extracted for testability without a running
 * IntelliJ platform.
 */
fun tagChipKeyAction(
    keyCode: Int,
    onClick: (() -> Unit)?,
    onEdit: (() -> Unit)?,
    onDelete: (() -> Unit)?,
): Boolean = when (keyCode) {
    KeyEvent.VK_ENTER, KeyEvent.VK_SPACE -> { onClick?.invoke(); onClick != null }
    KeyEvent.VK_F2 -> { onEdit?.invoke(); onEdit != null }
    KeyEvent.VK_DELETE, KeyEvent.VK_BACK_SPACE -> { onDelete?.invoke(); onDelete != null }
    else -> false
}

fun shouldShowTagChipDeleteAction(
    hasDelete: Boolean,
    hovered: Boolean,
    chipFocused: Boolean,
    deleteFocused: Boolean,
): Boolean = hasDelete && (hovered || chipFocused || deleteFocused)

data class TagChipCornerDeleteGeometry(
    val fillBounds: Rectangle,
    val deleteButtonBounds: Rectangle,
)

fun tagChipCornerDeleteGeometry(
    width: Int,
    height: Int,
    deleteButtonSize: Int,
): TagChipCornerDeleteGeometry {
    val inset = JBUI.scale(TAG_CHIP_DELETE_INSET)
    // Delete button sits fully inside the chip (no vertical overlap above it).
    val fillBounds = Rectangle(0, 0, width, height)
    val deleteButtonBounds = Rectangle(
        (width - deleteButtonSize - inset).coerceAtLeast(0),
        ((height - deleteButtonSize) / 2).coerceAtLeast(0),
        deleteButtonSize,
        deleteButtonSize,
    )
    return TagChipCornerDeleteGeometry(fillBounds, deleteButtonBounds)
}

/**
 * Tag chip with a label, optional edit (pencil) button, and optional delete (X)
 * button. Click on the chip body activates `onClick` (typically opens a
 * matches dialog). Keyboard: Enter/Space activates click, F2 fires `onEdit`,
 * Delete/Backspace fires `onDelete`. Right-click does nothing.
 */
class TagChip(
    tag: String,
    colored: Boolean,
    onClick: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    tooltip: String? = null,
    private val alwaysShowDelete: Boolean = false,
) : JPanel(null) {

    private val backgroundColor: Color =
        if (colored) tagChipColor(tag) else JBUI.CurrentTheme.ActionButton.hoverBackground()
    private var focusedRing = false
    private var hovered = false
    private var editButton: JComponent? = null
    private var deleteFocused = false
    private var deleteButton: JComponent? = null

    private val deleteButtonSize: Int
        get() = if (deleteButton == null) 0 else JBUI.scale(14)

    private val deleteButtonOverlap: Int
        get() = 0

    private val editGap: Int
        get() = if (editButton == null) 0 else maxOf(deleteButtonOverlap, JBUI.scale(2))

    init {
        isOpaque = false
        // Keep the remove affordance as an overlay so hover/focus never
        // changes the chip width or the wrap position of neighboring chips.
        border = BorderFactory.createEmptyBorder(JBUI.scale(2), JBUI.scale(8), JBUI.scale(2), 0)
        isFocusable = onClick != null
        if (onClick != null) handCursor()
        if (tooltip != null) toolTipText = tooltip
        val label = JLabel(tag).apply { if (tooltip != null) toolTipText = tooltip }
        add(label)
        if (onClick != null) {
            label.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            label.addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    if (e.button != MouseEvent.BUTTON1) return
                    if (e.isPopupTrigger) return
                    onClick.invoke()
                }
            })
        }
        if (onEdit != null) {
            editButton = HoverTintIconButton(
                icon = AllIcons.Actions.Edit,
                tooltip = SpeqaBundle.message("tagCloud.editTag"),
                onAction = onEdit,
            ).also { add(it) }
        }
        if (onDelete != null) {
            deleteButton = CornerDeleteButton(onDelete).apply {
                toolTipText = SpeqaBundle.message("tagCloud.removeTag")
                isVisible = alwaysShowDelete
                addFocusListener(object : FocusAdapter() {
                    override fun focusGained(e: FocusEvent) {
                        deleteFocused = true
                        updateDeleteButtonVisibility()
                    }

                    override fun focusLost(e: FocusEvent) {
                        deleteFocused = false
                        updateDeleteButtonVisibility()
                    }
                })
            }.also { add(it) }
        }
        if (onClick != null) {
            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    if (e.isPopupTrigger) return
                    if (e.button != MouseEvent.BUTTON1) return
                    // Don't grab focus on mouse click - the visible focus ring
                    // that lingers after the dialog opens is more noise than
                    // signal. Keyboard activation still works (Tab + Enter)
                    // because focus traversal moves focus to the chip naturally.
                    onClick.invoke()
                }
            })
            addKeyListener(object : KeyAdapter() {
                override fun keyPressed(e: KeyEvent) {
                    if (tagChipKeyAction(e.keyCode, onClick, onEdit, onDelete)) e.consume()
                }
            })
            // Only paint the focus ring when the chip received focus via
            // KEYBOARD traversal (Tab), not on programmatic / mouse focus.
            // Swing exposes this via JComponent.isFocusOwner + focus traversal
            // policy heuristics; the simplest approximation is to listen only
            // to KEYBOARD focus events. AWT doesn't directly distinguish the
            // cause, but we no longer grab focus on mouseClicked, so any
            // focusGained that arrives is keyboard-initiated.
            addFocusListener(object : FocusAdapter() {
                override fun focusGained(e: FocusEvent) {
                    focusedRing = true
                    updateDeleteButtonVisibility()
                    repaint()
                }

                override fun focusLost(e: FocusEvent) {
                    focusedRing = false
                    deleteFocused = e.oppositeComponent?.let { opposite ->
                        deleteButton?.let { opposite == it || SwingUtilities.isDescendingFrom(opposite, it) }
                    } == true
                    updateDeleteButtonVisibility()
                    repaint()
                }
            })
        }
        installHoverTracking(this)
        components.filterIsInstance<JComponent>().forEach { installHoverTracking(it) }
    }

    private fun installHoverTracking(component: JComponent) {
        component.addMouseListener(object : MouseAdapter() {
            override fun mouseEntered(e: MouseEvent) {
                activateHover()
            }

            override fun mouseExited(e: MouseEvent) {
                SwingUtilities.invokeLater {
                    if (isPointerInsideChip()) {
                        activateHover()
                    } else {
                        deactivateHover()
                    }
                }
            }
        })
    }

    private fun activateHover() {
        hoveredTag?.get()?.takeIf { it !== this }?.deactivateHover()
        hoveredTag = WeakReference(this)
        hovered = true
        updateDeleteButtonVisibility()
        hoverReconcileTimer.start()
    }

    private fun deactivateHover() {
        if (hoveredTag?.get() === this) hoveredTag = null
        hovered = false
        updateDeleteButtonVisibility()
        if (hoveredTag?.get() == null) hoverReconcileTimer.stop()
    }

    private fun isPointerInsideChip(): Boolean {
        val screenPoint = MouseInfo.getPointerInfo()?.location ?: return false
        if (!isShowing) return false
        val point = Point(screenPoint)
        SwingUtilities.convertPointFromScreen(point, this)
        return point.x >= 0 && point.y >= 0 && point.x < width && point.y < height
    }

    private fun updateDeleteButtonVisibility() {
        val shown = alwaysShowDelete || shouldShowTagChipDeleteAction(
            hasDelete = deleteButton != null,
            hovered = hovered,
            chipFocused = focusedRing,
            deleteFocused = deleteFocused,
        )
        deleteButton?.isVisible = shown
        repaint()
    }

    override fun getPreferredSize(): Dimension {
        val insets = insets
        val label = getComponent(0).preferredSize
        val edit = editButton?.preferredSize ?: Dimension(0, 0)
        val gap = editGap
        // Reserve deleteButtonSize on the right for the button (no vertical overlap).
        val deleteReserve = deleteButtonSize
        val fillWidth = insets.left + label.width + gap + edit.width + deleteReserve + insets.right
        val fillHeight = insets.top + maxOf(label.height, edit.height) + insets.bottom
        return Dimension(fillWidth, fillHeight)
    }

    override fun getMinimumSize(): Dimension = preferredSize

    override fun doLayout() {
        val insets = insets
        val geometry = tagChipCornerDeleteGeometry(width, height, deleteButtonSize)
        val fillBounds = geometry.fillBounds
        val label = getComponent(0)
        val labelSize = label.preferredSize
        val edit = editButton
        val editSize = edit?.preferredSize ?: Dimension(0, 0)
        val contentHeight = maxOf(labelSize.height, editSize.height)
        val labelY = fillBounds.y + insets.top + (contentHeight - labelSize.height) / 2
        label.setBounds(fillBounds.x + insets.left, labelY, labelSize.width, labelSize.height)
        edit?.let {
            val x = fillBounds.x + insets.left + labelSize.width + editGap
            val y = fillBounds.y + insets.top + (contentHeight - editSize.height) / 2
            it.setBounds(x, y, editSize.width, editSize.height)
        }
        deleteButton?.let { button ->
            val bounds = geometry.deleteButtonBounds
            button.setBounds(bounds)
        }
    }

    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.color = backgroundColor
            val arc = JBUI.scale(12)
            val fillBounds = tagChipCornerDeleteGeometry(width, height, deleteButtonSize).fillBounds
            g2.fillRoundRect(
                fillBounds.x,
                fillBounds.y,
                fillBounds.width - 1,
                fillBounds.height - 1,
                arc,
                arc,
            )
            if (focusedRing) {
                val focusGraphics = g2.create(fillBounds.x, fillBounds.y, fillBounds.width, fillBounds.height) as Graphics2D
                try {
                    DarculaUIUtil.paintFocusBorder(focusGraphics, fillBounds.width, fillBounds.height, arc.toFloat(), true)
                } finally {
                    focusGraphics.dispose()
                }
            }
        } finally {
            g2.dispose()
        }
        super.paintComponent(g)
    }

    private class CornerDeleteButton(
        private val onAction: () -> Unit,
    ) : JComponent() {
        private val icon: Icon = replaceIconColor(
            AllIcons.Actions.Close,
            JBColor.namedColor(
                "Component.errorFocusColor",
                JBColor(Color(0xCC4646), Color(0xFF7373)),
            ),
        )

        init {
            isOpaque = false
            isFocusable = true
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    if (e.button != MouseEvent.BUTTON1) return
                    onAction()
                    e.consume()
                }
            })
            addKeyListener(object : KeyAdapter() {
                override fun keyPressed(e: KeyEvent) {
                    if (e.keyCode == KeyEvent.VK_ENTER || e.keyCode == KeyEvent.VK_SPACE) {
                        onAction()
                        e.consume()
                    }
                }
            })
        }

        override fun getPreferredSize(): Dimension = JBUI.size(14, 14)

        override fun paintComponent(g: Graphics) {
            val g2 = g.create() as Graphics2D
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                val scale = TAG_CHIP_DELETE_ICON_SCALE
                val scaledWidth = (icon.iconWidth * scale).toInt()
                val scaledHeight = (icon.iconHeight * scale).toInt()
                val x = (width - scaledWidth) / 2
                val y = (height - scaledHeight) / 2
                val iconGraphics = g2.create(x, y, scaledWidth, scaledHeight) as Graphics2D
                try {
                    iconGraphics.scale(scale, scale)
                    icon.paintIcon(this, iconGraphics, 0, 0)
                } finally {
                    iconGraphics.dispose()
                }
            } finally {
                g2.dispose()
            }
        }
    }

    private class HoverTintIconButton(
        icon: Icon,
        tooltip: String,
        private val onAction: () -> Unit,
    ) : JComponent() {
        private val defaultIcon = replaceIconColor(icon, speqaMutedIconColor())
        private val hoverIcon = replaceIconColor(
            icon,
            JBColor.namedColor("Link.hoverForeground", JBColor.BLUE),
        )
        private var highlighted = false

        init {
            isOpaque = false
            isFocusable = true
            toolTipText = tooltip
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            addMouseListener(object : MouseAdapter() {
                override fun mouseEntered(e: MouseEvent) {
                    highlighted = true
                    repaint()
                }

                override fun mouseExited(e: MouseEvent) {
                    highlighted = hasFocus()
                    repaint()
                }

                override fun mouseClicked(e: MouseEvent) {
                    if (e.button != MouseEvent.BUTTON1) return
                    onAction()
                    e.consume()
                }
            })
            addFocusListener(object : FocusAdapter() {
                override fun focusGained(e: FocusEvent) {
                    highlighted = true
                    repaint()
                }

                override fun focusLost(e: FocusEvent) {
                    highlighted = false
                    repaint()
                }
            })
            addKeyListener(object : KeyAdapter() {
                override fun keyPressed(e: KeyEvent) {
                    if (e.keyCode == KeyEvent.VK_ENTER || e.keyCode == KeyEvent.VK_SPACE) {
                        onAction()
                        e.consume()
                    }
                }
            })
        }

        override fun getPreferredSize(): Dimension = JBUI.size(22, 22)

        override fun paintComponent(g: Graphics) {
            val icon = if (highlighted) hoverIcon else defaultIcon
            val x = (width - icon.iconWidth) / 2
            val y = (height - icon.iconHeight) / 2
            icon.paintIcon(this, g, x, y)
        }
    }

    companion object {
        private var hoveredTag: WeakReference<TagChip>? = null
        private val hoverReconcileTimer: Timer = Timer(40) {
            reconcileHoveredTagWithPointer()
        }.apply {
            isRepeats = true
        }

        private fun reconcileHoveredTagWithPointer() {
            val chip = hoveredTag?.get()
            when {
                chip == null -> hoverReconcileTimer.stop()
                !chip.isPointerInsideChip() -> chip.deactivateHover()
            }
        }
    }
}
