package io.github.barsia.speqa.editor.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import com.intellij.icons.AllIcons
import io.github.barsia.speqa.SpeqaBundle
import org.jetbrains.jewel.bridge.icon.fromPlatformIcon
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.Tooltip
import org.jetbrains.jewel.ui.icon.IntelliJIconKey
import androidx.compose.foundation.gestures.detectTapGestures

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class, ExperimentalJewelApi::class)
@Composable
internal fun TagCloud(
    tags: List<String>,
    allKnownTags: List<String>,
    onTagsChange: ((List<String>) -> Unit)?,
    label: String,
    coloredChips: Boolean = false,
    onChipClick: ((String) -> Unit)? = null,
    chipTooltip: ((String) -> String)? = null,
    chipContextActions: ((String) -> List<TagChipContextAction>)? = null,
    addItemLabel: String = SpeqaBundle.message("tagCloud.addTag"),
    showLabel: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    var isAdding by remember { mutableStateOf(false) }
    val textFieldState = remember { TextFieldState() }
    val addIcon = remember { IntelliJIconKey.fromPlatformIcon(AllIcons.General.Add, SpeqaLayout::class.java) }
    val addButtonFocusRequester = remember { FocusRequester() }

    fun dismissInput() {
        isAdding = false
        textFieldState.edit { replace(0, length, "") }
    }

    fun dismissAndRestoreAnchor() {
        dismissInput()
        addButtonFocusRequester.requestFocus()
    }

    fun dismissAndContinueTraversal(direction: FocusDirection) {
        dismissAndRestoreAnchor()
        focusManager.moveFocus(direction)
    }

    fun addTag(tag: String) {
        val trimmed = tag.trim()
        if (trimmed.isNotEmpty() && trimmed !in tags && onTagsChange != null) {
            onTagsChange(tags + trimmed)
        }
        dismissInput()
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(SpeqaLayout.tightGap),
    ) {
        // Header row: label + divider + [+] button
        if (showLabel || onTagsChange != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(SpeqaLayout.blockPadding),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (showLabel) {
                    SectionLabel(label)
                }
                Box(
                    Modifier.weight(1f).height(1.dp).background(SpeqaThemeColors.divider)
                )
                if (onTagsChange != null) {
                    var buttonHeightPx by remember { mutableStateOf(0) }
                    val gapPx = with(LocalDensity.current) { SpeqaLayout.tightGap.roundToPx() }
                    Box(modifier = Modifier.onGloballyPositioned { buttonHeightPx = it.size.height }) {
                        var addFocused by remember { mutableStateOf(false) }
                        val addHoverSource = remember { MutableInteractionSource() }
                        val addHovered by addHoverSource.collectIsHoveredAsState()
                        val addBorder = if (addFocused) SpeqaThemeColors.accent else Color.Transparent
                        val addBg = if (addHovered || addFocused) SpeqaThemeColors.actionHover else Color.Transparent
                        val addTagDescription = addItemLabel
                        Tooltip(tooltip = { Text(addItemLabel) }) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .focusRequester(addButtonFocusRequester)
                                    .semantics {
                                        role = Role.Button
                                        contentDescription = addTagDescription
                                    }
                                    .background(addBg, RoundedCornerShape(4.dp))
                                    .border(1.dp, addBorder, RoundedCornerShape(4.dp))
                                    .hoverable(addHoverSource)
                                    .onFocusChanged { addFocused = it.isFocused }
                                    .onPreviewKeyEvent { event ->
                                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                        when (event.key) {
                                            Key.Enter, Key.NumPadEnter, Key.Spacebar -> {
                                                focusManager.clearFocus()
                                                isAdding = true
                                                textFieldState.edit { replace(0, length, "") }
                                                true
                                            }
                                            Key.Tab -> {
                                                focusManager.moveFocus(if (event.isShiftPressed) FocusDirection.Previous else FocusDirection.Next)
                                                true
                                            }
                                            else -> false
                                        }
                                    }
                                    .handOnHover()
                                    .clickableWithPointer(focusable = true) {
                                        focusManager.clearFocus()
                                        isAdding = true
                                        textFieldState.edit { replace(0, length, "") }
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    key = addIcon,
                                    contentDescription = addItemLabel,
                                    modifier = Modifier.size(14.dp),
                                    tint = if (addHovered || addFocused) SpeqaThemeColors.foreground else SpeqaThemeColors.mutedForeground,
                                )
                            }
                        }

                        if (isAdding) {
                            Popup(
                                offset = androidx.compose.ui.unit.IntOffset(0, buttonHeightPx + gapPx),
                                onDismissRequest = ::dismissInput,
                                properties = androidx.compose.ui.window.PopupProperties(focusable = true),
                            ) {
                                TagInput(
                                    textFieldState = textFieldState,
                                    allKnownTags = allKnownTags,
                                    currentTags = tags,
                                    onAdd = ::addTag,
                                    onDismiss = ::dismissAndRestoreAnchor,
                                    onTabDismiss = { direction -> dismissAndContinueTraversal(direction) },
                                    placeholder = addItemLabel,
                                )
                            }
                        }
                    }
                }
            }
        }

        // Chips or empty state
        if (tags.isNotEmpty()) {
            val chipSpacing = SpeqaLayout.tightGap
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(chipSpacing),
                verticalArrangement = Arrangement.spacedBy(chipSpacing),
            ) {
                tags.forEach { tag ->
                    TagChip(
                        tag = tag,
                        colored = coloredChips,
                        onClick = onChipClick?.let { click -> { click(tag) } },
                        tooltipText = chipTooltip?.invoke(tag),
                        contextActions = chipContextActions?.invoke(tag).orEmpty(),
                    )
                }
            }
        } else {
            Text(
                text = when (label) {
                    SpeqaBundle.message("label.tags") -> SpeqaBundle.message("label.noTags")
                    SpeqaBundle.message("label.environment") -> SpeqaBundle.message("label.noEnvironments")
                    else -> SpeqaBundle.message("label.notSet")
                },
                color = SpeqaThemeColors.mutedForeground,
                fontSize = 13.sp,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalJewelApi::class)
@Composable
internal fun TagChip(
    tag: String,
    colored: Boolean,
    onClick: (() -> Unit)?,
    tooltipText: String?,
    contextActions: List<TagChipContextAction>,
) {
    val chipColor = if (colored) tagChipColor(tag) else SpeqaThemeColors.subtleSurface
    val shape = RoundedCornerShape(12.dp)
    var isFocused by remember { mutableStateOf(false) }
    val chipBorderColor = if (isFocused) SpeqaThemeColors.accent else SpeqaThemeColors.subtleBorder
    val focusManager = LocalFocusManager.current

    val chipModifier = Modifier
        .background(chipColor, shape)
        .border(0.5.dp, chipBorderColor, shape)
        .onFocusChanged { isFocused = it.isFocused }
        .padding(horizontal = 8.dp, vertical = 3.dp)
        .let { base ->
            val clickableModifier = if (onClick != null) {
                base
                    .handOnHover()
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (event.key) {
                            Key.Enter, Key.NumPadEnter, Key.Spacebar -> {
                                onClick()
                                true
                            }
                            Key.Tab -> {
                                focusManager.moveFocus(
                                    if (event.isShiftPressed) FocusDirection.Previous
                                    else FocusDirection.Next
                                )
                                true
                            }
                            else -> false
                        }
                    }
                    .focusTarget()
                    .pointerInput(onClick) {
                        detectTapGestures(
                            onTap = { onClick() },
                        )
                    }
            } else {
                base
            }
            clickableModifier
        }

    val content: @Composable () -> Unit = {
        Row(
            modifier = chipModifier,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = tag,
                color = SpeqaThemeColors.foreground,
                fontSize = 12.sp,
            )
        }
    }

    val tooltipWrapped: @Composable () -> Unit = {
        if (tooltipText != null) {
            Tooltip(tooltip = { Text(tooltipText) }) {
                content()
            }
        } else {
            content()
        }
    }

    if (contextActions.isNotEmpty()) {
        ContextMenuArea(
            items = {
                contextActions.map { action ->
                    ContextMenuItem(action.text) { action.onSelect() }
                }
            },
        ) {
            tooltipWrapped()
        }
    } else {
        tooltipWrapped()
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalJewelApi::class)
@Composable
private fun TagInput(
    textFieldState: TextFieldState,
    allKnownTags: List<String>,
    currentTags: List<String>,
    onAdd: (String) -> Unit,
    onDismiss: () -> Unit,
    onTabDismiss: (FocusDirection) -> Unit,
    placeholder: String,
) {
    val inputText = textFieldState.text.toString()
    val suggestions = remember(inputText, allKnownTags, currentTags) {
        val available = allKnownTags.filter { it !in currentTags }
        if (inputText.isBlank()) available
        else available.filter { it.contains(inputText, ignoreCase = true) }
    }

    var selectedIndex by remember(inputText) { mutableStateOf(-1) }

    val inputFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        inputFocusRequester.requestFocus()
    }

    var wasFocused by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .widthIn(min = 140.dp, max = 220.dp)
            .background(SpeqaThemeColors.surface, RoundedCornerShape(8.dp))
            .border(1.dp, SpeqaThemeColors.border, RoundedCornerShape(8.dp))
            .padding(SpeqaLayout.compactGap)
            .onFocusChanged { state ->
                if (state.hasFocus) wasFocused = true
                if (!state.hasFocus && wasFocused) onDismiss()
            }
            .focusTarget(),
    ) {
        var isFieldFocused by remember { mutableStateOf(false) }
        val fieldBorderColor = if (isFieldFocused) SpeqaThemeColors.accent else SpeqaThemeColors.border
        androidx.compose.foundation.text.BasicTextField(
            state = textFieldState,
            textStyle = androidx.compose.ui.text.TextStyle(
                fontSize = 12.sp,
                color = SpeqaThemeColors.foreground,
            ),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(SpeqaThemeColors.accent),
            modifier = Modifier
                .fillMaxWidth()
                .background(SpeqaThemeColors.fieldSurface, RoundedCornerShape(4.dp))
                .border(1.dp, fieldBorderColor, RoundedCornerShape(4.dp))
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .focusRequester(inputFocusRequester)
                .onFocusChanged { isFieldFocused = it.isFocused }
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.DirectionDown -> {
                            if (suggestions.isNotEmpty()) {
                                selectedIndex = (selectedIndex + 1).coerceAtMost(suggestions.lastIndex)
                            }
                            true
                        }
                        Key.DirectionUp -> {
                            if (suggestions.isNotEmpty()) {
                                selectedIndex = (selectedIndex - 1).coerceAtLeast(-1)
                            }
                            true
                        }
                        Key.Enter, Key.NumPadEnter -> {
                            if (selectedIndex in suggestions.indices) {
                                onAdd(suggestions[selectedIndex])
                            } else {
                                onAdd(textFieldState.text.toString())
                            }
                            true
                        }
                        Key.Escape, Key.Tab -> {
                            if (event.key == Key.Tab) {
                                onTabDismiss(if (event.isShiftPressed) FocusDirection.Previous else FocusDirection.Next)
                            } else {
                                onDismiss()
                            }
                            true
                        }
                        else -> false
                    }
                },
            decorator = { innerTextField ->
                if (textFieldState.text.isEmpty()) {
                    Text(placeholder, fontSize = 12.sp, color = SpeqaThemeColors.mutedForeground)
                }
                innerTextField()
            },
        )
        if (suggestions.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SpeqaThemeColors.headerSurface, RoundedCornerShape(4.dp))
                    .padding(vertical = 4.dp),
            ) {
                suggestions.take(8).forEachIndexed { index, suggestion ->
                    val isSelected = index == selectedIndex
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (isSelected) SpeqaThemeColors.actionHover else Color.Transparent)
                            .clickableWithPointer { onAdd(suggestion) }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = suggestion,
                            color = SpeqaThemeColors.foreground,
                            fontSize = 12.sp,
                        )
                    }
                }
            }
        }
    }
}

private val TAG_CHIP_PALETTE = listOf(
    Color(0xFF4A6FA5).copy(alpha = 0.15f),
    Color(0xFF6A9B6A).copy(alpha = 0.15f),
    Color(0xFFA56A8A).copy(alpha = 0.15f),
    Color(0xFFB5944A).copy(alpha = 0.15f),
    Color(0xFF5A9B9B).copy(alpha = 0.15f),
    Color(0xFFA56A5A).copy(alpha = 0.15f),
    Color(0xFF7A6AB5).copy(alpha = 0.15f),
    Color(0xFF8A9B5A).copy(alpha = 0.15f),
    Color(0xFF5A8AAA).copy(alpha = 0.15f),
    Color(0xFFAA7A5A).copy(alpha = 0.15f),
    Color(0xFF6AAA8A).copy(alpha = 0.15f),
    Color(0xFFAA5A7A).copy(alpha = 0.15f),
    Color(0xFF8A7AAA).copy(alpha = 0.15f),
    Color(0xFFAAAA5A).copy(alpha = 0.15f),
    Color(0xFF5AAAAA).copy(alpha = 0.15f),
    Color(0xFFAA8A5A).copy(alpha = 0.15f),
)

private fun tagChipColor(tag: String): Color {
    val index = (tag.hashCode() and 0x7FFFFFFF) % TAG_CHIP_PALETTE.size
    return TAG_CHIP_PALETTE[index]
}
