package io.github.barsia.speqa.editor.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.project.Project
import io.github.barsia.speqa.SpeqaBundle
import io.github.barsia.speqa.settings.SpeqaSettings
import org.jetbrains.jewel.bridge.icon.fromPlatformIcon
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.Tooltip
import org.jetbrains.jewel.ui.icon.IntelliJIconKey

@OptIn(ExperimentalJewelApi::class, ExperimentalFoundationApi::class)
@Composable
internal fun TicketChip(
    ticket: String,
    project: Project,
    onEdit: (String) -> Unit,
    onDelete: () -> Unit,
    chipFocusRequester: FocusRequester? = null,
) {
    var editing by remember { mutableStateOf(false) }
    var draft by remember(ticket) { mutableStateOf(ticket) }
    var pendingRestore by remember { mutableStateOf(false) }
    val actionBtnFocusRequester = remember { FocusRequester() }

    val settings = remember(project) { SpeqaSettings.getInstance(project) }
    val hoverFocus = rememberHoverFocusState()
    val removeIcon = IntelliJIconKey.fromPlatformIcon(AllIcons.Actions.GC, SpeqaLayout::class.java)
    val editIcon = IntelliJIconKey.fromPlatformIcon(AllIcons.Actions.Edit, SpeqaLayout::class.java)
    val saveIcon = IntelliJIconKey.fromPlatformIcon(AllIcons.Actions.MenuSaveall, SpeqaLayout::class.java)

    fun commit() {
        val trimmed = draft.trim()
        if (trimmed.isNotEmpty() && trimmed != ticket) onEdit(trimmed)
        editing = false
        pendingRestore = true
    }

    LaunchedEffect(pendingRestore, editing) {
        if (pendingRestore && !editing) {
            kotlinx.coroutines.yield()
            try { actionBtnFocusRequester.requestFocus() } catch (_: Throwable) {}
            pendingRestore = false
        }
    }

    Row(
        modifier = Modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (editing) {
            TicketEditField(
                initialValue = draft,
                onValueChange = { draft = it },
                onCommit = { commit() },
                onCancel = { draft = ticket; editing = false; pendingRestore = true },
            )
        } else {
            Box(
                modifier = Modifier
                    .heightIn(min = 24.dp)
                    .wrapContentHeight(Alignment.CenterVertically)
                    .then(if (chipFocusRequester != null) Modifier.focusRequester(chipFocusRequester) else Modifier)
                    .semantics { role = Role.Button }
                    .border(
                        1.dp,
                        if (hoverFocus.isFocused) SpeqaThemeColors.accent else Color.Transparent,
                        RoundedCornerShape(4.dp),
                    )
                    .padding(start = 4.dp, end = 5.dp, top = 3.dp, bottom = 3.dp)
                    .onFocusChanged { hoverFocus.updateFocus(it.isFocused) }
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (event.key) {
                            Key.Enter, Key.NumPadEnter, Key.Spacebar -> {
                                BrowserUtil.browse(settings.resolveTicketUrl(ticket)); true
                            }
                            else -> false
                        }
                    }
                    .hoverable(hoverFocus.interactionSource)
                    .handOnHover()
                    .focusable()
                    .pointerInput(ticket) { detectTapGestures { BrowserUtil.browse(settings.resolveTicketUrl(ticket)) } },
            ) {
                Tooltip(tooltip = { Text(ticket) }) {
                    androidx.compose.foundation.text.BasicText(
                        text = ticket,
                        style = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, color = SpeqaThemeColors.accent),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        val actionHoverFocus = rememberHoverFocusState()
        val actionTint = if (actionHoverFocus.isHovered || actionHoverFocus.isFocused) SpeqaThemeColors.foreground else SpeqaThemeColors.mutedForeground
        val actionTooltip = SpeqaBundle.message(if (editing) "tooltip.save" else "tooltip.edit")
        Tooltip(tooltip = { Text(actionTooltip) }) {
            SpeqaIconButton(
                focusable = true,
                onClick = { if (editing) commit() else editing = true },
                modifier = Modifier
                    .focusRequester(actionBtnFocusRequester)
                    .hoverable(actionHoverFocus.interactionSource)
                    .onFocusChanged { actionHoverFocus.updateFocus(it.hasFocus) },
            ) {
                Icon(if (editing) saveIcon else editIcon, contentDescription = actionTooltip, modifier = Modifier.size(16.dp), tint = actionTint)
            }
        }
        val trashHoverFocus = rememberHoverFocusState()
        val trashTint = if (trashHoverFocus.isHovered || trashHoverFocus.isFocused) SpeqaThemeColors.destructive else SpeqaThemeColors.mutedForeground
        Tooltip(tooltip = { Text(SpeqaBundle.message("tooltip.removeTicket")) }) {
            SpeqaIconButton(
                focusable = true,
                onClick = onDelete,
                modifier = Modifier
                    .hoverable(trashHoverFocus.interactionSource)
                    .onFocusChanged { trashHoverFocus.updateFocus(it.hasFocus) },
            ) {
                Icon(removeIcon, contentDescription = SpeqaBundle.message("tooltip.removeTicket"), modifier = Modifier.size(16.dp), tint = trashTint)
            }
        }
    }
}

@Composable
internal fun TicketEditField(
    initialValue: String,
    onValueChange: (String) -> Unit,
    onCommit: () -> Unit,
    onCancel: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    var tfValue by remember { mutableStateOf(TextFieldValue(initialValue, selection = TextRange(initialValue.length))) }
    var hasBeenFocused by remember { mutableStateOf(false) }
    val editTextStyle = TextStyle(fontSize = 11.sp, color = SpeqaThemeColors.accent)
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val minWidth = remember(initialValue, editTextStyle) {
        with(density) {
            textMeasurer.measure(
                initialValue.ifBlank { SpeqaBundle.message("placeholder.ticketId") },
                editTextStyle,
            ).size.width.toDp()
        }
    }
    val measuredWidth = remember(tfValue.text, editTextStyle, minWidth) {
        with(density) {
            val w = textMeasurer.measure(
                tfValue.text.ifBlank { SpeqaBundle.message("placeholder.ticketId") },
                editTextStyle,
            ).size.width.toDp() + 1.dp
            maxOf(w, minWidth + 1.dp)
        }
    }

    Box(
        modifier = Modifier
            .heightIn(min = 24.dp)
            .wrapContentHeight(Alignment.CenterVertically)
            .padding(horizontal = 4.dp, vertical = 3.dp),
    ) {
        BasicTextField(
            value = tfValue,
            onValueChange = {
                tfValue = it
                onValueChange(it.text)
            },
            textStyle = editTextStyle,
            singleLine = true,
            cursorBrush = SolidColor(SpeqaThemeColors.accent),
            modifier = Modifier
                .width(measuredWidth)
                .focusRequester(focusRequester)
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.Enter, Key.NumPadEnter -> { onCommit(); true }
                        Key.Escape -> { onCancel(); true }
                        else -> false
                    }
                }
                .onFocusChanged { state ->
                    if (state.isFocused) {
                        hasBeenFocused = true
                    } else if (hasBeenFocused) {
                        onCommit()
                    }
                },
            decorationBox = { innerTextField ->
                if (tfValue.text.isBlank()) {
                    Text(SpeqaBundle.message("placeholder.ticketId"), fontSize = 11.sp, color = SpeqaThemeColors.mutedForeground)
                }
                innerTextField()
            },
        )
    }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.yield()
        focusRequester.requestFocus()
    }
}

@OptIn(ExperimentalJewelApi::class, ExperimentalFoundationApi::class)
@Composable
internal fun TicketInput(
    initialValue: String = "",
    onCommit: (String) -> Unit,
    onCancel: () -> Unit,
) {
    var draft by remember { mutableStateOf(initialValue) }
    val saveIcon = IntelliJIconKey.fromPlatformIcon(AllIcons.Actions.MenuSaveall, SpeqaLayout::class.java)
    val saveHoverFocus = rememberHoverFocusState()
    val saveTint = if (saveHoverFocus.isHovered || saveHoverFocus.isFocused) SpeqaThemeColors.foreground else SpeqaThemeColors.mutedForeground

    fun doCommit() {
        if (draft.isBlank()) onCancel() else onCommit(draft)
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TicketEditField(
            initialValue = initialValue,
            onValueChange = { draft = it },
            onCommit = { doCommit() },
            onCancel = onCancel,
        )
        Tooltip(tooltip = { Text(SpeqaBundle.message("tooltip.save")) }) {
            SpeqaIconButton(
                focusable = true,
                onClick = { doCommit() },
                modifier = Modifier
                    .hoverable(saveHoverFocus.interactionSource)
                    .onFocusChanged { saveHoverFocus.updateFocus(it.hasFocus) },
            ) {
                Icon(saveIcon, contentDescription = SpeqaBundle.message("tooltip.save"), modifier = Modifier.size(16.dp), tint = saveTint)
            }
        }
    }
}
