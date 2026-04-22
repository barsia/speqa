package io.github.barsia.speqa.editor.ui

import com.intellij.openapi.ui.ComboBox
import io.github.barsia.speqa.editor.ui.primitives.handCursor
import io.github.barsia.speqa.model.Priority
import io.github.barsia.speqa.model.Status
import java.awt.Component
import javax.swing.DefaultListCellRenderer
import javax.swing.JList

/**
 * Typed `ComboBox<Priority>` with a capitalizing cell renderer and a
 * `handCursor` affordance. Emits [onChange] only when the selection
 * differs from the current model.
 */
class PriorityComboBox(private val onChange: (Priority) -> Unit) : ComboBox<Priority>(Priority.entries.toTypedArray()) {
    init {
        handCursor()
        renderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: JList<*>?, value: Any?, index: Int, isSelected: Boolean, cellHasFocus: Boolean,
            ): Component {
                val text = (value as? Priority)?.label?.replaceFirstChar { it.uppercase() } ?: ""
                return super.getListCellRendererComponent(list, text, index, isSelected, cellHasFocus)
            }
        }
        addActionListener {
            val picked = selectedItem as? Priority ?: return@addActionListener
            onChange(picked)
        }
    }

    /** Set the current value without re-firing [onChange]. */
    fun setValue(value: Priority?) {
        if (value == null) return
        if (selectedItem != value) {
            val l = actionListeners
            l.forEach { removeActionListener(it) }
            selectedItem = value
            l.forEach { addActionListener(it) }
        }
    }
}

/** Typed `ComboBox<Status>` mirroring [PriorityComboBox]. */
class StatusComboBox(private val onChange: (Status) -> Unit) : ComboBox<Status>(Status.entries.toTypedArray()) {
    init {
        handCursor()
        renderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: JList<*>?, value: Any?, index: Int, isSelected: Boolean, cellHasFocus: Boolean,
            ): Component {
                val text = (value as? Status)?.label?.replaceFirstChar { it.uppercase() } ?: ""
                return super.getListCellRendererComponent(list, text, index, isSelected, cellHasFocus)
            }
        }
        addActionListener {
            val picked = selectedItem as? Status ?: return@addActionListener
            onChange(picked)
        }
    }

    fun setValue(value: Status?) {
        if (value == null) return
        if (selectedItem != value) {
            val l = actionListeners
            l.forEach { removeActionListener(it) }
            selectedItem = value
            l.forEach { addActionListener(it) }
        }
    }
}
