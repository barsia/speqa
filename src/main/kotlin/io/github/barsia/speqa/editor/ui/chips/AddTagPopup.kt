// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package io.github.barsia.speqa.editor.ui.chips

import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.JBColor
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import io.github.barsia.speqa.SpeqaBundle
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.Font
import java.awt.KeyboardFocusManager
import java.awt.Point
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.SwingUtilities
import javax.swing.DefaultListCellRenderer
import javax.swing.DefaultListModel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListSelectionModel
import javax.swing.event.DocumentEvent

/**
 * Floating popup anchored under [anchor] that lets the user pick an existing
 * tag from [allKnown] (minus [currentSelection]) or create a new one. The
 * popup has a search field on top and a scrollable list of matches below.
 * When the typed query does not exactly match any pickable tag, a synthetic
 * `+ Create '<query>'` row is prepended.
 *
 * Keyboard: Up/Down navigates the list, Enter picks, Escape dismisses.
 * Mouse: click on a row picks it.
 */
internal class AddTagPopup(
    private val anchor: javax.swing.JComponent,
    private val allKnown: () -> Set<String>,
    private val currentSelection: () -> Set<String>,
    private val onPick: (String) -> Unit,
) {

    private val searchField = JBTextField().apply {
        emptyText.text = SpeqaBundle.message("tagCloud.searchPlaceholder")
        font = font.deriveFont(Font.PLAIN)
    }

    private val listModel = DefaultListModel<Row>()
    private val list = JBList(listModel).apply {
        selectionMode = ListSelectionModel.SINGLE_SELECTION
        visibleRowCount = 8
        cellRenderer = RowRenderer()
        background = JBColor.background()
    }

    private var popup: JBPopup? = null
    /** True when the popup was dismissed by a keyboard action (ESC/Enter/Tab).
     *  False for click-outside and mouse item selection — the onClosed listener
     *  handles focus restoration for those paths. */
    private var dismissedByKeyboard = false

    private sealed interface Row {
        data class Existing(val value: String) : Row
        data class Create(val query: String) : Row
    }

    /** Re-query [allKnown] and rebuild the visible list. Safe to call any
     *  time the popup is alive (e.g. once the tag registry finishes its
     *  background scan and new tags become available). */
    fun refresh() {
        if (popup?.isDisposed == true) return
        refreshRows()
    }

    fun show() {
        refreshRows()

        val content = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(JBUI.scale(4))
            add(searchField, BorderLayout.NORTH)
            add(
                JBScrollPane(list).apply {
                    border = JBUI.Borders.emptyTop(4)
                    preferredSize = Dimension(JBUI.scale(240), JBUI.scale(220))
                },
                BorderLayout.CENTER,
            )
        }

        val builder = JBPopupFactory.getInstance()
            .createComponentPopupBuilder(content, searchField)
            .setResizable(false)
            .setMovable(false)
            .setRequestFocus(true)
            .setFocusable(true)
            .setCancelOnClickOutside(true)
            .setCancelOnOtherWindowOpen(true)
            .setMinSize(Dimension(JBUI.scale(260), JBUI.scale(260)))
        popup = builder.createPopup()
        // Return focus to the anchor on any non-keyboard dismissal (click-outside,
        // mouse item selection). Keyboard paths (ESC/Enter/Tab) set dismissedByKeyboard=true
        // and handle focus themselves to also drive Tab traversal where needed.
        popup?.addListener(object : JBPopupListener {
            override fun onClosed(event: LightweightWindowEvent) {
                if (!dismissedByKeyboard) {
                    SwingUtilities.invokeLater { anchor.requestFocusInWindow() }
                }
            }
        })

        wireKeyboard()
        wireMouse()
        wireSearch()

        val anchorPoint = if (anchor.isShowing) {
            RelativePoint(anchor, Point(0, anchor.height))
        } else {
            RelativePoint.getCenterOf(anchor)
        }
        popup?.show(anchorPoint)
    }

    private fun wireSearch() {
        searchField.document.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) = refreshRows()
        })
    }

    private fun wireKeyboard() {
        searchField.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                when (e.keyCode) {
                    KeyEvent.VK_DOWN -> {
                        if (listModel.size() == 0) return
                        val next = (list.selectedIndex + 1).coerceAtMost(listModel.size() - 1)
                        list.selectedIndex = next
                        list.ensureIndexIsVisible(next)
                        e.consume()
                    }
                    KeyEvent.VK_UP -> {
                        if (listModel.size() == 0) return
                        val next = (list.selectedIndex - 1).coerceAtLeast(0)
                        list.selectedIndex = next
                        list.ensureIndexIsVisible(next)
                        e.consume()
                    }
                    KeyEvent.VK_ENTER -> {
                        dismissedByKeyboard = true
                        commitSelection()
                        SwingUtilities.invokeLater { anchor.requestFocusInWindow() }
                        e.consume()
                    }
                    KeyEvent.VK_ESCAPE -> {
                        dismissedByKeyboard = true
                        popup?.cancel()
                        SwingUtilities.invokeLater { anchor.requestFocusInWindow() }
                        e.consume()
                    }
                    KeyEvent.VK_TAB -> {
                        dismissedByKeyboard = true
                        popup?.cancel()
                        SwingUtilities.invokeLater {
                            anchor.requestFocusInWindow()
                            val km = KeyboardFocusManager.getCurrentKeyboardFocusManager()
                            if (e.isShiftDown) km.focusPreviousComponent(anchor)
                            else km.focusNextComponent(anchor)
                        }
                        e.consume()
                    }
                }
            }
        })
    }

    private fun wireMouse() {
        list.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                val idx = list.locationToIndex(e.point)
                if (idx < 0) return
                list.selectedIndex = idx
                commitSelection()
            }
        })
        // Hand cursor while hovering an actual row; default cursor in the
        // empty area below the last item.
        val handCursor = java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR)
        val defaultCursor = java.awt.Cursor.getDefaultCursor()
        list.addMouseMotionListener(object : java.awt.event.MouseMotionAdapter() {
            override fun mouseMoved(e: MouseEvent) {
                val idx = list.locationToIndex(e.point)
                val onRow = idx >= 0 && list.getCellBounds(idx, idx)?.contains(e.point) == true
                list.cursor = if (onRow) handCursor else defaultCursor
            }
        })
    }

    private fun commitSelection() {
        val row = list.selectedValue ?: return
        val picked = when (row) {
            is Row.Existing -> row.value
            is Row.Create -> row.query
        }
        popup?.cancel()
        onPick(picked)
    }

    private fun refreshRows() {
        val query = searchField.text.trim()
        val currentlySelected = currentSelection()
        val pickable = allKnown()
            .filter { known -> currentlySelected.none { it.equals(known, ignoreCase = true) } }
            .sorted()
        val filtered = if (query.isBlank()) {
            pickable
        } else {
            pickable.filter { it.contains(query, ignoreCase = true) }
        }
        val showCreate = query.isNotBlank() &&
            pickable.none { it.equals(query, ignoreCase = true) } &&
            currentlySelected.none { it.equals(query, ignoreCase = true) }

        listModel.clear()
        if (showCreate) listModel.addElement(Row.Create(query))
        for (value in filtered) listModel.addElement(Row.Existing(value))

        if (listModel.size() > 0) list.selectedIndex = 0
    }

    private inner class RowRenderer : DefaultListCellRenderer() {
        override fun getListCellRendererComponent(
            list: JList<*>?,
            value: Any?,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean,
        ): Component {
            val text = when (value) {
                is Row.Create -> SpeqaBundle.message("tagCloud.createNew", value.query)
                is Row.Existing -> value.value
                else -> value?.toString().orEmpty()
            }
            val c = super.getListCellRendererComponent(list, text, index, isSelected, cellHasFocus)
            if (c is javax.swing.JLabel) {
                c.border = JBUI.Borders.empty(4, 8)
                if (value is Row.Create && !isSelected) {
                    c.foreground = JBColor.namedColor("Link.activeForeground", JBColor.BLUE)
                    c.font = c.font.deriveFont(Font.ITALIC)
                }
            }
            return c
        }
    }
}
