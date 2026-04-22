package io.github.barsia.speqa.editor.ui.chips

import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import java.awt.Dimension
import java.awt.Point
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.DefaultListModel
import javax.swing.JComponent
import javax.swing.ListSelectionModel
import javax.swing.event.DocumentEvent

/**
 * Anchored autocomplete popup for the [TagCloud] add input. Opens below the
 * anchor when there is vertical room, above otherwise. The input keeps focus;
 * the popup relays keyboard events (Up/Down/Enter/Esc) through listeners on
 * the input. Reuses a single [JBPopup] instance across rapid typing — only the
 * list model is refreshed.
 */
internal class TagAutocompletePopup(
    private val anchor: JComponent,
    private val input: JBTextField,
    private val allTags: () -> Set<String>,
    private val currentTags: () -> Set<String>,
    private val onPick: (String) -> Unit,
) {
    private val model = DefaultListModel<String>()
    private val list = JBList(model).apply {
        selectionMode = ListSelectionModel.SINGLE_SELECTION
        visibleRowCount = 8
    }
    private var popup: JBPopup? = null
    private var installed = false

    fun install() {
        if (installed) return
        installed = true
        input.document.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) = refresh()
        })
        input.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                when (e.keyCode) {
                    KeyEvent.VK_DOWN -> {
                        if (model.isEmpty) return
                        val next = (list.selectedIndex + 1).coerceAtMost(model.size() - 1)
                        list.selectedIndex = next
                        list.ensureIndexIsVisible(next)
                        e.consume()
                    }
                    KeyEvent.VK_UP -> {
                        if (model.isEmpty) return
                        val prev = (list.selectedIndex - 1).coerceAtLeast(0)
                        list.selectedIndex = prev
                        list.ensureIndexIsVisible(prev)
                        e.consume()
                    }
                    KeyEvent.VK_ENTER -> {
                        val picked = list.selectedValue
                        if (popup?.isVisible == true && picked != null) {
                            onPick(picked)
                            input.text = ""
                            hide()
                            e.consume()
                        }
                    }
                    KeyEvent.VK_ESCAPE -> {
                        if (popup?.isVisible == true) {
                            hide()
                            e.consume()
                        }
                    }
                }
            }
        })
        // Initial population once the input is shown.
        refresh()
    }

    fun refresh() {
        val query = input.text?.trim().orEmpty().lowercase()
        val excluded = currentTags()
        val candidates = allTags()
            .asSequence()
            .filter { it !in excluded }
            .filter { query.isEmpty() || it.lowercase().contains(query) }
            .sorted()
            .toList()
        model.clear()
        candidates.forEach { model.addElement(it) }
        if (candidates.isEmpty()) {
            hide()
            return
        }
        if (list.selectedIndex !in 0 until model.size()) {
            list.selectedIndex = 0
        }
        show()
    }

    private fun show() {
        if (!input.isShowing) return
        val width = maxOf(anchor.width, JBUI.scale(180))
        list.preferredSize = Dimension(width, list.preferredSize.height)
        val existing = popup
        if (existing != null && existing.isVisible) {
            existing.size = Dimension(width, existing.size.height.coerceAtLeast(JBUI.scale(40)))
            return
        }
        val scroll = JBScrollPane(list).apply {
            border = JBUI.Borders.empty()
            preferredSize = Dimension(width, JBUI.scale(160))
        }
        val built = JBPopupFactory.getInstance()
            .createComponentPopupBuilder(scroll, null)
            .setRequestFocus(false)
            .setFocusable(false)
            .setCancelOnClickOutside(true)
            .setCancelOnWindowDeactivation(true)
            .setResizable(false)
            .setMovable(false)
            .setMinSize(Dimension(width, JBUI.scale(40)))
            .createPopup()
        popup = built
        val point = placementPoint()
        built.show(RelativePoint(anchor, point))
    }

    fun hide() {
        popup?.cancel()
        popup = null
    }

    private fun placementPoint(): Point {
        val belowY = anchor.height + JBUI.scale(2)
        val screenBottom = anchor.locationOnScreen.y + anchor.height + JBUI.scale(160)
        val screenLimit = anchor.graphicsConfiguration?.bounds?.let { it.y + it.height } ?: Int.MAX_VALUE
        val preferAbove = screenBottom > screenLimit
        val y = if (preferAbove) -JBUI.scale(160) - JBUI.scale(2) else belowY
        return Point(0, y)
    }
}
