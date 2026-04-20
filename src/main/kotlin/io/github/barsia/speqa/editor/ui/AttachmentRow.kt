package io.github.barsia.speqa.editor.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.foundation.focusable
import androidx.compose.runtime.getValue
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import io.github.barsia.speqa.SpeqaBundle
import io.github.barsia.speqa.editor.AttachmentSupport
import io.github.barsia.speqa.model.Attachment
import org.jetbrains.jewel.bridge.icon.fromPlatformIcon
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.Tooltip
import org.jetbrains.jewel.ui.icon.IntelliJIconKey

@Composable
private fun AttachmentRowInner(
    fileIcon: IntelliJIconKey,
    iconTint: Color,
    iconAlpha: Float,
    fileName: String,
    nameColor: Color,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            Icon(fileIcon, contentDescription = null, modifier = Modifier.size(14.dp).alpha(iconAlpha), tint = iconTint)
        }
        Text(fileName, fontSize = 12.sp, color = nameColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@OptIn(ExperimentalJewelApi::class, ExperimentalFoundationApi::class)
@Composable
internal fun AttachmentRow(
    attachment: Attachment,
    onClick: () -> Unit,
    onDelete: (() -> Unit)?,
    isMissing: Boolean = false,
    onRelink: (() -> Unit)? = null,
    compact: Boolean = false,
    project: Project? = null,
    tcFile: VirtualFile? = null,
    modifier: Modifier = Modifier,
    actionModifier: Modifier = Modifier,
    deleteModifier: Modifier = Modifier,
) {
    val hoverFocus = rememberHoverFocusState()
    val deleteColor = SpeqaThemeColors.destructive
    val removeIcon = IntelliJIconKey.fromPlatformIcon(AllIcons.Actions.GC, SpeqaLayout::class.java)
    val fileIcon = if (AttachmentSupport.isImage(attachment)) {
        IntelliJIconKey("/icons/fileImage.svg", "/icons/fileImage.svg", iconClass = SpeqaLayout::class.java)
    } else {
        IntelliJIconKey("/fileTypes/any_type.svg", "/fileTypes/any_type.svg", iconClass = AllIcons::class.java)
    }
    val fileName = attachment.path.substringAfterLast('/')
    val rowAction = if (isMissing && onRelink != null) onRelink else onClick
    val tooltipText = if (isMissing) {
        SpeqaBundle.message("tooltip.attachmentMissing", fileName)
    } else {
        fileName
    }
    val nameColor = if (isMissing) SpeqaThemeColors.destructive else SpeqaThemeColors.accent
    val iconTint = nameColor
    val iconAlpha = if (isMissing) 0.6f else 1f

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = actionModifier
                .semantics { role = Role.Button }
                .border(1.dp, if (hoverFocus.isFocused) SpeqaThemeColors.accent else Color.Transparent, RoundedCornerShape(4.dp))
                .onFocusChanged { hoverFocus.updateFocus(it.isFocused) }
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.Enter, Key.NumPadEnter, Key.Spacebar -> { rowAction(); true }
                        else -> false
                    }
                }
                .hoverable(hoverFocus.interactionSource)
                .handOnHover()
                .focusable()
                .pointerInput(rowAction) { detectTapGestures { rowAction() } }
                .padding(end = 5.dp),
        ) {
            when {
                project != null && tcFile != null -> RichTooltip(
                    tooltip = { AttachmentPreviewPopover(attachment, project, tcFile) },
                ) {
                    AttachmentRowInner(fileIcon, iconTint, iconAlpha, fileName, nameColor)
                }
                isMissing -> Tooltip(tooltip = { Text(tooltipText) }) {
                    AttachmentRowInner(fileIcon, iconTint, iconAlpha, fileName, nameColor)
                }
                else -> AttachmentRowInner(fileIcon, iconTint, iconAlpha, fileName, nameColor)
            }
        }
        if (onDelete != null) {
            val trashHoverFocus = rememberHoverFocusState()
            val trashTint = if (trashHoverFocus.isHovered || trashHoverFocus.isFocused) deleteColor else SpeqaThemeColors.mutedForeground
            Tooltip(tooltip = { Text(SpeqaBundle.message("tooltip.removeAttachment")) }) {
                SpeqaIconButton(
                    focusable = true,
                    onClick = onDelete,
                    modifier = deleteModifier
                        .hoverable(trashHoverFocus.interactionSource)
                        .onFocusChanged { trashHoverFocus.updateFocus(it.hasFocus) },
                ) {
                    Icon(removeIcon, contentDescription = SpeqaBundle.message("tooltip.removeAttachment"), modifier = Modifier.size(16.dp), tint = trashTint)
                }
            }
        }
    }
}
