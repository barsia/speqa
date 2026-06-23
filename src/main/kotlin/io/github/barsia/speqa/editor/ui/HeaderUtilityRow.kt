// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package io.github.barsia.speqa.editor.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.util.IconLoader
import com.intellij.util.ui.JBUI
import io.github.barsia.speqa.SpeqaBundle
import io.github.barsia.speqa.editor.ui.primitives.DateIconLabel
import io.github.barsia.speqa.editor.ui.primitives.speqaIconButton
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Single-line header row: ID chip (West, fixed) + dates row (Center, ellipsis on shrink)
 * + trailing action (East, fixed). The Run button (or verdict control) is always
 * visible at the far right; the dates ellipsis-truncate when the row is narrow.
 */
class HeaderUtilityRow(
    idChip: JComponent,
    leftDateIcon: Icon,
    leftDateText: String,
    leftDateNormalTooltip: String,
    rightDateIcon: Icon,
    rightDateText: String,
    rightDateNormalTooltip: String,
    trailing: JComponent,
    middleDateIcon: Icon? = null,
    middleDateText: String = "",
    middleDateNormalTooltip: String = "",
) : JPanel(BorderLayout(JBUI.scale(12), 0)) {

    private val createdLabel = DateIconLabel(leftDateIcon, leftDateText, leftDateNormalTooltip)
    private val middleLabel: DateIconLabel? = if (middleDateIcon != null) {
        DateIconLabel(middleDateIcon, middleDateText, middleDateNormalTooltip)
    } else null
    private val updatedLabel = DateIconLabel(rightDateIcon, rightDateText, rightDateNormalTooltip)

    private val middleStrut: Component? = if (middleLabel != null) Box.createHorizontalStrut(JBUI.scale(12)) else null
    private val updatedStrut: Component = Box.createHorizontalStrut(JBUI.scale(12))

    private val datesRow = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.X_AXIS)
        isOpaque = false
        alignmentY = Component.CENTER_ALIGNMENT
        add(createdLabel)
        if (middleLabel != null && middleStrut != null) {
            add(middleStrut)
            add(middleLabel)
        }
        add(updatedStrut)
        add(updatedLabel)
    }

    init {
        isOpaque = false
        alignmentX = Component.LEFT_ALIGNMENT
        add(NoShrinkWrap(idChip), BorderLayout.WEST)
        add(datesRow, BorderLayout.CENTER)
        add(NoShrinkWrap(trailing), BorderLayout.EAST)
    }

    fun setDates(leftText: String, rightText: String) {
        createdLabel.text = leftText
        updatedLabel.text = rightText
    }

    /**
     * Set all three dates at once. The middle slot is silently ignored when it
     * was not built. Started / Finished labels (icon + text) hide entirely when
     * their date is blank, so the test run shows no Finished icon before the
     * run actually finishes, and no Started icon before any verdict has been
     * picked. Created always stays visible: it reflects the file's own
     * creation time, which always exists.
     */
    fun setRunDates(createdText: String, startedText: String, finishedText: String) {
        createdLabel.text = createdText
        val startedVisible = startedText.isNotEmpty()
        middleLabel?.let {
            it.text = startedText
            it.isVisible = startedVisible
        }
        middleStrut?.isVisible = startedVisible
        val finishedVisible = finishedText.isNotEmpty()
        updatedLabel.text = finishedText
        updatedLabel.isVisible = finishedVisible
        updatedStrut.isVisible = finishedVisible
        datesRow.revalidate()
        datesRow.repaint()
    }

    /**
     * Test-only accessor for the trailing component wrapper. Used by the
     * layout test to assert the wrapper enforces a no-shrink minimum width.
     */
    internal val trailingContainer: JComponent get() = getComponent(2) as JComponent

    /**
     * Wraps a child so the BorderLayout never sizes it below its preferred
     * width. BorderLayout already respects the child's preferred size by
     * default, but a child that itself reports a smaller minimum size can be
     * compressed; pinning min == pref prevents that.
     */
    private class NoShrinkWrap(child: JComponent) : JPanel(BorderLayout()) {
        init {
            isOpaque = false
            add(child, BorderLayout.CENTER)
        }
        override fun getMinimumSize(): Dimension = preferredSize
    }

    companion object {
        /** Test-case variant with a Run button on the far right. */
        fun forTestCase(
            idChip: JComponent,
            createdLabel: String,
            updatedLabel: String,
            onRun: () -> Unit,
        ): HeaderUtilityRow {
            val runButton = speqaIconButton(
                icon = AllIcons.Actions.Execute,
                tooltip = SpeqaBundle.message("tooltip.startTestRun"),
                muted = false,
                onAction = onRun,
            )
            // Wrap so the BorderLayout.EAST slot does not stretch the
            // ActionButton vertically; the hover background would otherwise
            // render as a tall rectangle. BoxLayout.Y_AXIS with glue above
            // and below vertically centers the 22x22 button on the same line
            // as the date text in the row's center.
            val runButtonHolder = JPanel().apply {
                isOpaque = false
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                add(Box.createVerticalGlue())
                runButton.alignmentX = Component.CENTER_ALIGNMENT
                add(runButton)
                add(Box.createVerticalGlue())
            }
            return HeaderUtilityRow(
                idChip = idChip,
                leftDateIcon = IconLoader.getIcon("/icons/calendarCreated.svg", HeaderUtilityRow::class.java),
                leftDateText = createdLabel,
                leftDateNormalTooltip = SpeqaBundle.message("preview.created"),
                rightDateIcon = IconLoader.getIcon("/icons/calendarUpdated.svg", HeaderUtilityRow::class.java),
                rightDateText = updatedLabel,
                rightDateNormalTooltip = SpeqaBundle.message("preview.updated"),
                trailing = runButtonHolder,
            )
        }

        /**
         * Test-run variant: three dates (Created from the file, Started, Finished)
         * plus a trailing slot. All three are shown when present.
         */
        fun forTestRun(
            idChip: JComponent,
            createdLabel: String,
            startedLabel: String,
            finishedLabel: String,
            trailing: JComponent,
        ): HeaderUtilityRow {
            return HeaderUtilityRow(
                idChip = idChip,
                leftDateIcon = IconLoader.getIcon("/icons/calendarCreated.svg", HeaderUtilityRow::class.java),
                leftDateText = createdLabel,
                leftDateNormalTooltip = SpeqaBundle.message("run.tooltip.created"),
                middleDateIcon = IconLoader.getIcon("/icons/calendarUpdated.svg", HeaderUtilityRow::class.java),
                middleDateText = startedLabel,
                middleDateNormalTooltip = SpeqaBundle.message("run.tooltip.started"),
                rightDateIcon = IconLoader.getIcon("/icons/calendarFinished.svg", HeaderUtilityRow::class.java),
                rightDateText = finishedLabel,
                rightDateNormalTooltip = SpeqaBundle.message("run.tooltip.finished"),
                trailing = trailing,
            )
        }
    }
}
