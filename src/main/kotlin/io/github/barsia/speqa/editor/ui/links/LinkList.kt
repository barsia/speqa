package io.github.barsia.speqa.editor.ui.links

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IconLoader
import io.github.barsia.speqa.SpeqaBundle
import io.github.barsia.speqa.editor.ui.AddEditLinkDialog
import io.github.barsia.speqa.editor.ui.primitives.DeleteFocusRestorer
import io.github.barsia.speqa.editor.ui.primitives.mutedActionLabel
import io.github.barsia.speqa.model.Link
import java.awt.Component
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Vertical list of [LinkRow]s plus a trailing "+ link" button. Uses
 * [DeleteFocusRestorer] to move focus after a row is removed.
 */
internal class LinkList(
    private val project: Project?,
    private val hideAddButton: Boolean = false,
    private val onLinksChange: (List<Link>) -> Unit,
) : JPanel() {

    /** Convenience constructor preserving the original signature for callers. */
    constructor(project: Project?, onLinksChange: (List<Link>) -> Unit) :
        this(project, hideAddButton = false, onLinksChange = onLinksChange)

    /** Open the `AddEditLinkDialog`. Callable from an external section-header `+` button. */
    fun startAdd() {
        openAddDialog()
    }

    private var links: List<Link> = emptyList()
    private val rows = mutableListOf<LinkRow>()
    private val addButton: JComponent = buildAddButton()
    private val restorer = DeleteFocusRestorer(
        itemProvider = { rows.getOrNull(it) },
        addButton = addButton,
    )

    init {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        isOpaque = false
        if (!hideAddButton) add(addButton)
    }

    fun setLinks(newLinks: List<Link>) {
        links = newLinks.toList()
        rebuild()
    }

    private fun rebuild() {
        removeAll()
        rows.clear()
        links.forEachIndexed { index, link ->
            val sizeBefore = links.size
            val row = LinkRow(
                link = link,
                project = project,
                onEdited = { updated ->
                    onLinksChange(links.toMutableList().apply { set(index, updated) })
                },
                onDelete = {
                    onLinksChange(links - link)
                    restorer.onDeleted(index, sizeBefore)
                },
            )
            row.alignmentX = Component.LEFT_ALIGNMENT
            rows.add(row)
            add(row)
        }
        if (!hideAddButton) {
            addButton.alignmentX = Component.LEFT_ALIGNMENT
            add(addButton)
        }
        revalidate()
        repaint()
    }

    private fun buildAddButton(): JComponent {
        val icon = IconLoader.getIcon("/icons/chainLink.svg", LinkList::class.java)
        return mutedActionLabel(
            text = SpeqaBundle.message("tooltip.addLink"),
            icon = icon,
            onClick = ::openAddDialog,
        )
    }

    private fun openAddDialog() {
        ApplicationManager.getApplication().invokeLater {
            val newLink = AddEditLinkDialog.show(project)
            if (newLink != null) {
                onLinksChange(links + newLink)
            }
        }
    }
}
