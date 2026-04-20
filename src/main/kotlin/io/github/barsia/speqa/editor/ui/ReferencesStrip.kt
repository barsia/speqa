package io.github.barsia.speqa.editor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import io.github.barsia.speqa.SpeqaBundle
import io.github.barsia.speqa.model.Attachment
import io.github.barsia.speqa.model.Link
import org.jetbrains.jewel.ui.component.Text

@Composable
internal fun ReferencesStrip(
    project: Project,
    tcFile: VirtualFile,
    attachments: List<Attachment>,
    links: List<Link>,
    onAttachmentsChange: (List<Attachment>) -> Unit,
    onLinksChange: (List<Link>) -> Unit,
    onOpenFile: (Attachment) -> Unit,
    attachmentRevision: Long = 0L,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ReferencesCounter(
                emoji = "\uD83D\uDCCE",
                label = SpeqaBundle.message("references.counter.files", attachments.size),
                onClick = { expanded = !expanded },
            )
            Text("\u00B7", color = SpeqaThemeColors.mutedForeground, fontSize = 12.sp)
            ReferencesCounter(
                emoji = "\uD83D\uDD17",
                label = SpeqaBundle.message("references.counter.links", links.size),
                onClick = { expanded = !expanded },
            )
        }

        if (expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AttachmentList(
                    attachments = attachments,
                    project = project,
                    tcFile = tcFile,
                    onAttachmentsChange = onAttachmentsChange,
                    onOpenFile = onOpenFile,
                    showAddButton = true,
                    attachmentRevision = attachmentRevision,
                )
                LinkList(
                    links = links,
                    onLinksChange = onLinksChange,
                    project = project,
                )
            }
        }
    }
}

@Composable
private fun ReferencesCounter(emoji: String, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.clickableWithPointer(onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(emoji, fontSize = 12.sp, color = SpeqaThemeColors.mutedForeground)
        Text(label, fontSize = 12.sp, color = SpeqaThemeColors.mutedForeground)
    }
}
