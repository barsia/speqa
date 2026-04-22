package io.github.barsia.speqa.editor.ui.attachments

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import io.github.barsia.speqa.SpeqaBundle
import io.github.barsia.speqa.editor.AttachmentSupport
import io.github.barsia.speqa.editor.ui.primitives.DeleteFocusRestorer
import io.github.barsia.speqa.editor.ui.primitives.handCursor
import io.github.barsia.speqa.editor.ui.primitives.mutedActionLabel
import io.github.barsia.speqa.model.Attachment
import java.awt.Component
import java.awt.FlowLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Vertical list of attachment rows plus a trailing "+ attach" button.
 *
 * The list is a plain [JPanel] — structural updates go through [setAttachments]
 * which rebuilds the rows. Focus restoration on delete is handled by
 * [DeleteFocusRestorer].
 */
internal class AttachmentList(
    private val project: Project,
    private val tcFile: VirtualFile,
    private val hideAddButton: Boolean = false,
    private val onAttachmentsChange: (List<Attachment>) -> Unit,
) : JPanel() {

    /** Convenience constructor preserving the original signature for callers. */
    constructor(
        project: Project,
        tcFile: VirtualFile,
        onAttachmentsChange: (List<Attachment>) -> Unit,
    ) : this(project, tcFile, hideAddButton = false, onAttachmentsChange = onAttachmentsChange)

    /** Open the native file chooser. Callable from an external section-header `+` button. */
    fun startAdd() {
        openChooser()
    }

    private var attachments: List<Attachment> = emptyList()
    private val rows = mutableListOf<AttachmentRow>()
    private val sharedPopupSlot = PopupSlot()
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

    fun setAttachments(newAttachments: List<Attachment>) {
        attachments = newAttachments.toList()
        rebuild()
    }

    private fun rebuild() {
        removeAll()
        rows.clear()
        attachments.forEachIndexed { index, attachment ->
            val sizeBefore = attachments.size
            val missing = runCatching {
                AttachmentSupport.resolveFile(project, tcFile, attachment) == null
            }.getOrDefault(false)
            val row = AttachmentRow(
                attachment = attachment,
                project = project,
                tcFile = tcFile,
                isMissing = missing,
                popupSlot = sharedPopupSlot,
                onDelete = { handleDelete(index, attachment, sizeBefore) },
                onRelink = if (missing) { { handleRelink(index) } } else null,
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

    private fun handleDelete(index: Int, attachment: Attachment, sizeBefore: Int) {
        val choice = Messages.showDialog(
            SpeqaBundle.message("dialog.removeAttachment.message"),
            SpeqaBundle.message("dialog.removeAttachment.title"),
            arrayOf(
                SpeqaBundle.message("dialog.removeAttachment.removeLink"),
                SpeqaBundle.message("dialog.removeAttachment.deleteFile"),
                Messages.getCancelButton(),
            ),
            0,
            Messages.getQuestionIcon(),
        )
        val removed = when (choice) {
            0 -> {
                onAttachmentsChange(attachments - attachment)
                true
            }
            1 -> {
                runWriteAction { AttachmentSupport.deleteFile(project, tcFile, attachment) }
                onAttachmentsChange(attachments - attachment)
                true
            }
            else -> false
        }
        if (removed) {
            restorer.onDeleted(index, sizeBefore)
        }
    }

    private fun handleRelink(index: Int) {
        ApplicationManager.getApplication().invokeLater {
            val descriptor = FileChooserDescriptorFactory.createAllButJarContentsDescriptor()
            FileChooser.chooseFiles(descriptor, project, null) { chosen ->
                if (chosen.isEmpty()) return@chooseFiles
                val newAttachment = runWriteAction {
                    AttachmentSupport.copyFileToAttachments(project, tcFile, chosen.first())
                }
                if (newAttachment != null) {
                    val updated = attachments.toMutableList()
                    updated[index] = newAttachment
                    onAttachmentsChange(updated)
                }
            }
        }
    }

    private fun buildAddButton(): JComponent {
        val icon = IconLoader.getIcon("/icons/paperclip.svg", AttachmentList::class.java)
        return mutedActionLabel(
            text = SpeqaBundle.message("tooltip.addAttachment"),
            icon = icon,
            onClick = ::openChooser,
        )
    }

    private fun openChooser() {
        ApplicationManager.getApplication().invokeLater {
            val descriptor = FileChooserDescriptorFactory.createAllButJarContentsDescriptor()
            FileChooser.chooseFiles(descriptor, project, null) { chosen ->
                if (chosen.isEmpty()) return@chooseFiles
                val newAttachment = runWriteAction {
                    AttachmentSupport.copyFileToAttachments(project, tcFile, chosen.first())
                }
                if (newAttachment != null) {
                    onAttachmentsChange(attachments + newAttachment)
                }
            }
        }
    }
}
