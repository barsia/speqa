package io.github.barsia.speqa.editor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import io.github.barsia.speqa.SpeqaBundle
import io.github.barsia.speqa.editor.AttachmentSupport
import io.github.barsia.speqa.model.Attachment
import io.github.barsia.speqa.model.Link

internal data class StepMetaRowVisualContract(
    val addActionMinHeightDp: Int,
    val equalWidthAddActions: Boolean,
    val singleLineAddActions: Boolean,
    val ellipsisOverflow: Boolean,
)

internal fun stepMetaRowVisualContract(narrow: Boolean): StepMetaRowVisualContract {
    return StepMetaRowVisualContract(
        addActionMinHeightDp = 24,
        equalWidthAddActions = narrow,
        singleLineAddActions = true,
        ellipsisOverflow = true,
    )
}

@Composable
internal fun StepMetaRow(
    stepIndex: Int,
    tickets: List<String>,
    links: List<Link>,
    attachments: List<Attachment>,
    project: Project?,
    tcFile: VirtualFile?,
    onTicketsChange: (List<String>) -> Unit,
    onLinksChange: (List<Link>) -> Unit,
    onAttachmentsChange: (List<Attachment>) -> Unit,
    onOpenFile: (Attachment) -> Unit,
    attachmentRevision: Long,
    ticketAddRequester: FocusRequester,
    linkPrimaryRequesters: List<FocusRequester>,
    linkAddRequester: FocusRequester,
    attachmentPrimaryRequesters: List<FocusRequester>,
    attachmentAddRequester: FocusRequester,
    narrow: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val focusContext = LocalFocusContext.current
    val contract = stepMetaRowVisualContract(narrow)

    fun addActionModifier(focusRequester: FocusRequester, slot: StepSlot): Modifier {
        val base = Modifier
            .heightIn(min = contract.addActionMinHeightDp.dp)
            .wrapContentHeight(Alignment.CenterVertically)
            .focusRequester(focusRequester)
            .onFocusChanged { if (it.isFocused) focusContext.current = FocusSlot(stepIndex, slot) }
        return if (contract.equalWidthAddActions) {
            base.fillMaxWidth()
        } else {
            base
        }
    }

    @Composable
    fun TicketBlock(blockModifier: Modifier = Modifier) {
        var addingNew by remember { mutableStateOf(false) }
        var pendingRestoreTicket by remember { mutableStateOf(false) }
        val restorer = rememberDeleteFocusRestorer(tickets.size, ticketAddRequester)
        LaunchedEffect(pendingRestoreTicket, addingNew) {
            if (pendingRestoreTicket && !addingNew) {
                kotlinx.coroutines.yield()
                try { ticketAddRequester.requestFocus() } catch (_: Throwable) {}
                pendingRestoreTicket = false
            }
        }
        Column(
            modifier = blockModifier,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            tickets.forEachIndexed { index, ticketId ->
                if (project != null) {
                    Box(modifier = Modifier.onFocusChanged { if (it.hasFocus) focusContext.current = FocusSlot(stepIndex, StepSlot.TICKET) }) {
                        TicketChip(
                            ticket = ticketId,
                            project = project,
                            onEdit = { updated ->
                                onTicketsChange(tickets.toMutableList().also { it[index] = updated })
                            },
                            onDelete = {
                                val sizeBefore = tickets.size
                                onTicketsChange(tickets.toMutableList().also { it.removeAt(index) })
                                restorer.onDeleted(index, sizeBefore)
                            },
                            chipFocusRequester = restorer.itemRequesters.getOrNull(index),
                        )
                    }
                }
            }
            if (addingNew) {
                TicketInput(
                    onCommit = { value ->
                        val cleaned = value.split(Regex("[,;\\s]+")).filter { it.isNotBlank() }
                        if (cleaned.isNotEmpty()) onTicketsChange(tickets + cleaned)
                        addingNew = false
                        pendingRestoreTicket = true
                    },
                    onCancel = { addingNew = false; pendingRestoreTicket = true },
                )
            } else {
                val ticketIcon = org.jetbrains.jewel.ui.icon.IntelliJIconKey("/icons/ticket.svg", "/icons/ticket.svg", iconClass = SpeqaLayout::class.java)
                QuietActionText(
                    label = SpeqaBundle.message("step.meta.addTicket"),
                    onClick = { addingNew = true },
                    enabled = true,
                    plain = true,
                    uppercase = false,
                    icon = ticketIcon,
                    iconLeadingPad = 0.dp,
                    labelMaxLines = if (contract.singleLineAddActions) 1 else Int.MAX_VALUE,
                    labelOverflow = if (contract.ellipsisOverflow) TextOverflow.Ellipsis else TextOverflow.Clip,
                    fillLabelWidth = contract.equalWidthAddActions,
                    modifier = addActionModifier(ticketAddRequester, StepSlot.TICKET),
                )
            }
        }
    }

    @Composable
    fun LinkBlock(blockModifier: Modifier = Modifier) {
        val restorer = rememberDeleteFocusRestorer(linkPrimaryRequesters, linkAddRequester)
        Column(
            modifier = blockModifier,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
links.forEachIndexed { index, link ->
                Box(modifier = Modifier.onFocusChanged { if (it.hasFocus) focusContext.current = FocusSlot(stepIndex, StepSlot.LINK) }) {
                    LinkRow(
                        link = link,
                        onClick = { BrowserUtil.browse(link.url) },
                        onEdit = {
                            ApplicationManager.getApplication().invokeLater {
                                val edited = AddEditLinkDialog.show(project, editLink = link)
                                if (edited != null) {
                                    onLinksChange(links.toMutableList().apply { set(index, edited) })
                                }
                            }
                        },
                        onDelete = {
                            val sizeBefore = links.size
                            onLinksChange(links.toMutableList().also { it.removeAt(index) })
                            restorer.onDeleted(index, sizeBefore)
                        },
                        modifier = linkPrimaryRequesters.getOrNull(index)?.let { Modifier.focusRequester(it) } ?: Modifier,
                    )
                }
            }
            val linkIcon = org.jetbrains.jewel.ui.icon.IntelliJIconKey("/icons/chainLink.svg", "/icons/chainLink.svg", iconClass = SpeqaLayout::class.java)
            QuietActionText(
                label = SpeqaBundle.message("step.meta.addLink"),
                onClick = {
                    ApplicationManager.getApplication().invokeLater {
                        val newLink = AddEditLinkDialog.show(project)
                        if (newLink != null) {
                            onLinksChange(links + newLink)
                        }
                    }
                },
                enabled = true,
                plain = true,
                uppercase = false,
                icon = linkIcon,
                labelMaxLines = if (contract.singleLineAddActions) 1 else Int.MAX_VALUE,
                labelOverflow = if (contract.ellipsisOverflow) TextOverflow.Ellipsis else TextOverflow.Clip,
                fillLabelWidth = contract.equalWidthAddActions,
                modifier = addActionModifier(linkAddRequester, StepSlot.LINK),
            )
        }
    }

    @Composable
    fun AttachmentBlock(blockModifier: Modifier = Modifier) {
        val restorer = rememberDeleteFocusRestorer(attachmentPrimaryRequesters, attachmentAddRequester)
        fun addAttachment() {
            if (project == null || tcFile == null) return
            ApplicationManager.getApplication().invokeLater {
                val descriptor = com.intellij.openapi.fileChooser.FileChooserDescriptorFactory.createAllButJarContentsDescriptor()
                com.intellij.openapi.fileChooser.FileChooser.chooseFiles(descriptor, project, null) { chosen ->
                    if (chosen.isNotEmpty()) {
                        val newAttachment = com.intellij.openapi.application.runWriteAction<Attachment?> {
                            AttachmentSupport.copyFileToAttachments(project, tcFile, chosen.first())
                        }
                        if (newAttachment != null) {
                            onAttachmentsChange(attachments + newAttachment)
                        }
                    }
                }
            }
        }
        Column(
            modifier = blockModifier,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (project != null && tcFile != null) {
                val currentAttachmentRevision = attachmentRevision
                attachments.forEachIndexed { index, attachment ->
                    currentAttachmentRevision
                    val missing = AttachmentSupport.resolveFile(project, tcFile, attachment) == null
                    Box(modifier = Modifier.onFocusChanged { if (it.hasFocus) focusContext.current = FocusSlot(stepIndex, StepSlot.ATTACHMENT) }) {
                        AttachmentRow(
                            attachment = attachment,
                            project = project,
                            tcFile = tcFile,
                            onClick = { onOpenFile(attachment) },
                            onDelete = {
                                val sizeBefore = attachments.size
                                onAttachmentsChange(
                                    attachments.toMutableList().also { it.removeAt(index) },
                                )
                                restorer.onDeleted(index, sizeBefore)
                            },
                            isMissing = missing,
                            compact = true,
                            actionModifier = attachmentPrimaryRequesters.getOrNull(index)
                                ?.let { Modifier.focusRequester(it) }
                                ?: Modifier,
                        )
                    }
                }
                val attachIcon = org.jetbrains.jewel.ui.icon.IntelliJIconKey("/icons/paperclip.svg", "/icons/paperclip.svg", iconClass = SpeqaLayout::class.java)
                QuietActionText(
                    label = SpeqaBundle.message("step.meta.addAttachment"),
                    onClick = { addAttachment() },
                    enabled = true,
                    plain = true,
                    uppercase = false,
                    icon = attachIcon,
                    labelMaxLines = if (contract.singleLineAddActions) 1 else Int.MAX_VALUE,
                    labelOverflow = if (contract.ellipsisOverflow) TextOverflow.Ellipsis else TextOverflow.Clip,
                    fillLabelWidth = contract.equalWidthAddActions,
                    modifier = addActionModifier(attachmentAddRequester, StepSlot.ATTACHMENT),
                )
            }
        }
    }

    if (contract.equalWidthAddActions) {
        Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(SpeqaLayout.s4),
            verticalAlignment = Alignment.Top,
        ) {
            TicketBlock(blockModifier = Modifier.weight(1f))
            LinkBlock(blockModifier = Modifier.weight(1f))
            AttachmentBlock(blockModifier = Modifier.weight(1f))
        }
    } else {
        Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(SpeqaLayout.s5),
            verticalAlignment = Alignment.Top,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(SpeqaLayout.s4),
                verticalAlignment = Alignment.Top,
            ) {
                TicketBlock(blockModifier = Modifier.weight(1f))
                LinkBlock(blockModifier = Modifier.weight(1f))
            }
            AttachmentBlock(blockModifier = Modifier.weight(1f))
        }
    }
}
