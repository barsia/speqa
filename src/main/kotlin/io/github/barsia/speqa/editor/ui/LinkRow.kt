package io.github.barsia.speqa.editor.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.intellij.icons.AllIcons
import io.github.barsia.speqa.SpeqaBundle
import io.github.barsia.speqa.model.Link
import org.jetbrains.jewel.bridge.icon.fromPlatformIcon
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.Tooltip
import org.jetbrains.jewel.ui.icon.IntelliJIconKey

@OptIn(ExperimentalJewelApi::class, ExperimentalFoundationApi::class)
@Composable
internal fun LinkRow(
    link: Link,
    onClick: () -> Unit,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val hoverFocus = rememberHoverFocusState()
    val deleteColor = SpeqaThemeColors.destructive
    val removeIcon = IntelliJIconKey.fromPlatformIcon(AllIcons.Actions.GC, SpeqaLayout::class.java)
    val editIcon = IntelliJIconKey.fromPlatformIcon(AllIcons.Actions.Edit, SpeqaLayout::class.java)
    val linkIcon = IntelliJIconKey("/icons/chainLink.svg", "/icons/chainLink.svg", iconClass = SpeqaLayout::class.java)

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .semantics { role = Role.Button }
                .border(
                    1.dp,
                    if (hoverFocus.isFocused) SpeqaThemeColors.accent else Color.Transparent,
                    RoundedCornerShape(4.dp),
                )
                .onFocusChanged { hoverFocus.updateFocus(it.isFocused) }
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.Enter, Key.NumPadEnter, Key.Spacebar -> {
                            onClick(); true
                        }
                        else -> false
                    }
                }
                .hoverable(hoverFocus.interactionSource)
                .handOnHover()
                .focusTarget()
                .pointerInput(onClick) { detectTapGestures { onClick() } },
        ) {
            Tooltip(tooltip = { Text(link.url) }) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                        Icon(
                            linkIcon,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = SpeqaThemeColors.accent,
                        )
                    }
                    Text(
                        link.title,
                        fontSize = 12.sp,
                        color = SpeqaThemeColors.accent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

            // Edit button
            if (onEdit != null) {
                val editHoverFocus = rememberHoverFocusState()
                val editTint =
                    if (editHoverFocus.isHovered || editHoverFocus.isFocused) SpeqaThemeColors.foreground else SpeqaThemeColors.mutedForeground
                Tooltip(tooltip = { Text(SpeqaBundle.message("tooltip.edit")) }) {
                    SpeqaIconButton(
                        focusable = true,
                        onClick = onEdit,
                        modifier = Modifier
                            .hoverable(editHoverFocus.interactionSource)
                            .onFocusChanged { editHoverFocus.updateFocus(it.hasFocus) },
                    ) {
                        Icon(
                            editIcon,
                            contentDescription = SpeqaBundle.message("tooltip.edit"),
                            modifier = Modifier.size(16.dp),
                            tint = editTint,
                        )
                    }
                }
            }

            // Delete button
            if (onDelete != null) {
                val trashHoverFocus = rememberHoverFocusState()
                val trashTint =
                    if (trashHoverFocus.isHovered || trashHoverFocus.isFocused) deleteColor else SpeqaThemeColors.mutedForeground
                Tooltip(tooltip = { Text(SpeqaBundle.message("tooltip.removeLink")) }) {
                    SpeqaIconButton(
                        focusable = true,
                        onClick = onDelete,
                        modifier = Modifier
                            .hoverable(trashHoverFocus.interactionSource)
                            .onFocusChanged { trashHoverFocus.updateFocus(it.hasFocus) },
                    ) {
                        Icon(
                            removeIcon,
                            contentDescription = SpeqaBundle.message("tooltip.removeLink"),
                            modifier = Modifier.size(16.dp),
                            tint = trashTint,
                        )
                    }
                }
            }
        }
    }
