package io.github.barsia.speqa.editor.ui

import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import io.github.barsia.speqa.SpeqaBundle
import io.github.barsia.speqa.editor.AttachmentSupport
import io.github.barsia.speqa.model.Attachment
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.Tooltip
import org.jetbrains.jewel.ui.icon.IntelliJIconKey

@Composable
internal fun AttachmentList(
    attachments: List<Attachment>,
    project: Project,
    tcFile: VirtualFile,
    onAttachmentsChange: (List<Attachment>) -> Unit,
    onOpenFile: (Attachment) -> Unit,
    modifier: Modifier = Modifier,
    showAddButton: Boolean = true,
    attachmentRevision: Long = 0L,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        attachments.forEachIndexed { index, attachment ->
            // Read attachmentRevision to create a Compose state dependency — when VFS listener
            // increments it, this composable recomposes and resolveFile is called again.
            @Suppress("UNUSED_VARIABLE")
            val rev = attachmentRevision
            val missing = AttachmentSupport.resolveFile(project, tcFile, attachment) == null
            AttachmentRow(
                attachment = attachment,
                onClick = { onOpenFile(attachment) },
                onDelete = {
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
                    when (choice) {
                        0 -> onAttachmentsChange(attachments - attachment)
                        1 -> {
                            runWriteAction { AttachmentSupport.deleteFile(project, tcFile, attachment) }
                            onAttachmentsChange(attachments - attachment)
                        }
                    }
                },
                isMissing = missing,
                onRelink = if (missing) {
                    {
                        com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                            val descriptor = FileChooserDescriptorFactory.createAllButJarContentsDescriptor()
                            FileChooser.chooseFiles(descriptor, project, null) { chosen ->
                                if (chosen.isNotEmpty()) {
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
                    }
                } else null,
            )
        }

        if (showAddButton) {
            AddAttachmentButton(project, tcFile, attachments, onAttachmentsChange)
        }
    }
}

@OptIn(ExperimentalJewelApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun AddAttachmentButton(
    project: Project,
    tcFile: VirtualFile,
    attachments: List<Attachment>,
    onAttachmentsChange: (List<Attachment>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val attachIcon = IntelliJIconKey("/icons/paperclip.svg", "/icons/paperclip.svg", iconClass = SpeqaLayout::class.java)
    val clipHoverSource = remember { MutableInteractionSource() }
    val isClipHovered by clipHoverSource.collectIsHoveredAsState()
    var isClipFocused by remember { mutableStateOf(false) }
    val clipTint = if (isClipHovered || isClipFocused) SpeqaThemeColors.foreground else SpeqaThemeColors.mutedForeground
    Row(
        modifier = modifier
            .semantics { role = Role.Button }
            .hoverable(clipHoverSource)
            .onFocusChanged { isClipFocused = it.hasFocus }
            .clickableWithPointer(focusable = true) {
                com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                    val descriptor = FileChooserDescriptorFactory.createAllButJarContentsDescriptor()
                    FileChooser.chooseFiles(descriptor, project, null) { chosen ->
                        if (chosen.isNotEmpty()) {
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
            .padding(end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            Icon(attachIcon, contentDescription = null, modifier = Modifier.size(14.dp), tint = clipTint)
        }
        Text(
            SpeqaBundle.message("tooltip.addAttachment"),
            fontSize = 12.sp,
            color = clipTint,
        )
    }
}
