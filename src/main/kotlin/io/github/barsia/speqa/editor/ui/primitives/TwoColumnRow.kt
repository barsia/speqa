package io.github.barsia.speqa.editor.ui.primitives

import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Component
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.GridLayout
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Two-column layout used by the panel section rows (PRIORITY | STATUS,
 * ENVIRONMENT | TAGS, LINKS | ATTACHMENTS). Each column has a caption row and
 * a body row. The caption row optionally includes a right-aligned action
 * component (e.g. a `+` header button).
 *
 * The outer split uses [GridLayout] with two equal-weight cells so column
 * widths are identical across every section row, regardless of body content.
 * This guarantees Priority/Environment/Links left-column widths line up, and
 * Status/Tags/Attachments right-column widths line up — including their
 * header `+` buttons (anchored to the right of the caption row).
 */
fun twoColumnRow(
    leftCaption: String,
    rightCaption: String,
    leftBody: JComponent,
    rightBody: JComponent,
    leftHeaderAction: JComponent? = null,
    rightHeaderAction: JComponent? = null,
): JPanel {
    val columnGap = JBUI.scale(16)
    val captionGap = JBUI.scale(4)

    val row = JPanel(GridLayout(1, 2, columnGap, 0))
    row.isOpaque = false
    row.alignmentX = Component.LEFT_ALIGNMENT

    row.add(buildColumn(leftCaption, leftBody, leftHeaderAction, captionGap))
    row.add(buildColumn(rightCaption, rightBody, rightHeaderAction, captionGap))
    return row
}

private fun buildColumn(
    caption: String,
    body: JComponent,
    headerAction: JComponent?,
    captionGap: Int,
): JPanel {
    val column = JPanel()
    column.layout = BoxLayout(column, BoxLayout.Y_AXIS)
    column.isOpaque = false
    column.alignmentX = Component.LEFT_ALIGNMENT

    val header = JPanel(GridBagLayout())
    header.isOpaque = false
    header.alignmentX = Component.LEFT_ALIGNMENT
    header.add(
        sectionCaption(caption),
        GridBagConstraints().apply {
            gridx = 0; gridy = 0
            anchor = GridBagConstraints.WEST
            weightx = 0.0
        },
    )
    header.add(
        Box.createHorizontalGlue(),
        GridBagConstraints().apply {
            gridx = 1; gridy = 0
            fill = GridBagConstraints.HORIZONTAL
            weightx = 1.0
        },
    )
    if (headerAction != null) {
        header.add(
            headerAction,
            GridBagConstraints().apply {
                gridx = 2; gridy = 0
                anchor = GridBagConstraints.EAST
                weightx = 0.0
            },
        )
    }
    column.add(header)
    column.add(Box.createVerticalStrut(captionGap))

    body.alignmentX = Component.LEFT_ALIGNMENT
    val bodyWrapper = JPanel(BorderLayout())
    bodyWrapper.isOpaque = false
    bodyWrapper.alignmentX = Component.LEFT_ALIGNMENT
    bodyWrapper.add(body, BorderLayout.CENTER)
    column.add(bodyWrapper)
    return column
}
