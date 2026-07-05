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
    /**
     * Whether the `+ Create '<query>'` row is offered for a query that matches no known value.
     * True when authoring metadata (the editor's tag clouds), false for filtering, where a value
     * that does not exist in the project can only ever match nothing.
     */
    private val allowCreate: Boolean = true,
) {

    private val searchField = JBTextField().apply {
        emptyText.text = SpeqaBundle.message("tagCloud.searchPlaceholder")
        font = font.deriveFont(Font.PLAIN)
    }

    private val listModel = DefaultListModel<Row>()
    private val list = JBList(listModel).apply {
        selectionMode = ListSelectionModel.SINGLE_SELECTION
        cellRenderer = RowRenderer()
        background = JBColor.background()
        // Shown when the query filters every row out (always possible with allowCreate=false),
        // so the popup explains itself instead of showing a blank area.
        setEmptyText(SpeqaBundle.message("tagCloud.noMatches"))
    }
    private val scrollPane = JBScrollPane(list).apply {
        border = JBUI.Borders.emptyTop(4)
    }

    /**
     * Sizes the list viewport to the current number of rows (capped at [MAX_VISIBLE_ROWS], beyond
     * which it scrolls), so the popup is as tall as its content instead of a fixed block with
     * empty space below. Width stays fixed so the popup does not jitter horizontally.
     */
    private fun fitListSize() {
        list.visibleRowCount = visibleRowCount(listModel.size(), MAX_VISIBLE_ROWS)
        val viewport = list.preferredScrollableViewportSize
        scrollPane.preferredSize = Dimension(JBUI.scale(240), viewport.height)
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
        fitListSize()
        popup?.takeIf { it.isVisible }?.pack(true, true)
    }

    /** Builds, shows, and returns the underlying popup so a caller can track its lifetime. */
    fun show(): JBPopup? {
        refreshRows()
        fitListSize()

        val content = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(JBUI.scale(4))
            add(searchField, BorderLayout.NORTH)
            add(scrollPane, BorderLayout.CENTER)
        }

        val builder = JBPopupFactory.getInstance()
            .createComponentPopupBuilder(content, searchField)
            .setResizable(false)
            .setMovable(false)
            .setRequestFocus(true)
            .setFocusable(true)
            .setCancelOnClickOutside(true)
            .setCancelOnOtherWindowOpen(true)
            .setMinSize(Dimension(JBUI.scale(240), 0))
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
        return popup
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
        val showCreate = shouldShowCreateRow(allowCreate, query, pickable, currentlySelected)

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

    private companion object {
        /** Rows shown before the list starts to scroll instead of growing the popup. */
        const val MAX_VISIBLE_ROWS = 8
    }
}

/**
 * Whether the `+ Create '<query>'` row is offered: only when creation is allowed, the [query] is
 * non-blank, and it does not already exist as a [pickable] value or an already [selected] one
 * (case-insensitive). Pure so the "filter offers existing values only" contract and the
 * exact-match suppression are unit-tested without a live popup.
 */
internal fun shouldShowCreateRow(
    allowCreate: Boolean,
    query: String,
    pickable: List<String>,
    selected: Set<String>,
): Boolean =
    allowCreate &&
        query.isNotBlank() &&
        pickable.none { it.equals(query, ignoreCase = true) } &&
        selected.none { it.equals(query, ignoreCase = true) }

/**
 * The list viewport row count: the item count, but at least 1 (so an empty list still has a
 * sensible height) and at most [max] (beyond which the list scrolls instead of growing the
 * popup). Pure so the "popup fits its content, capped" contract is unit-tested.
 */
internal fun visibleRowCount(itemCount: Int, max: Int): Int = itemCount.coerceIn(1, max)
