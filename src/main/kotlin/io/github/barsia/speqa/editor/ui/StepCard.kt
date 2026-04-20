package io.github.barsia.speqa.editor.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.text.ClickableText
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.Modifier
import androidx.compose.foundation.focusable
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import io.github.barsia.speqa.SpeqaBundle
import io.github.barsia.speqa.editor.AttachmentSupport
import io.github.barsia.speqa.model.Attachment
import io.github.barsia.speqa.model.Link
import org.jetbrains.jewel.bridge.icon.fromPlatformIcon
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.Tooltip
import org.jetbrains.jewel.ui.icon.IntelliJIconKey

internal fun stepCardAccentBarVisible(isFocused: Boolean, isDragging: Boolean): Boolean {
    return isFocused && !isDragging
}

@OptIn(ExperimentalJewelApi::class, ExperimentalFoundationApi::class)
@Composable
fun StepCard(
    index: Int,
    action: String,
    expected: String?,
    onActionChange: (String) -> Unit,
    onExpectedChange: (String?) -> Unit,
    onDelete: () -> Unit,
    attachments: List<Attachment> = emptyList(),
    onAttachmentsChange: (List<Attachment>) -> Unit = {},
    tickets: List<String> = emptyList(),
    onTicketsChange: (List<String>) -> Unit = {},
    links: List<Link> = emptyList(),
    onLinksChange: (List<Link>) -> Unit = {},
    project: Project? = null,
    tcFile: VirtualFile? = null,
    onOpenFile: (Attachment) -> Unit = {},
    attachmentRevision: Long = 0L,
    modifier: Modifier = Modifier,
    actionFocusRequester: FocusRequester? = null,
    previousActionFocusRequester: FocusRequester? = null,
    nextExpectedExitFocusRequester: FocusRequester? = null,
    expectedReverseEntryFocusRequester: FocusRequester? = null,
    requestFocus: Boolean = false,
    onFocusConsumed: () -> Unit = {},
    isDragging: Boolean = false,
    onDragStart: () -> Unit = {},
    onDrag: (Float) -> Unit = {},
    onDragEnd: () -> Unit = {},
    onInsertStepAfter: () -> Unit = {},
    onDuplicateStep: () -> Unit = {},
    onMoveStepUp: () -> Unit = {},
    onMoveStepDown: () -> Unit = {},
    onRequestDeleteStep: () -> Unit = {},
    onEnterFromExpected: () -> Unit = {},
) {
    val focusContext = LocalFocusContext.current
    val dragHandleIcon = IntelliJIconKey("/icons/dragHandle.svg", "/icons/dragHandle.svg", iconClass = SpeqaLayout::class.java)
    val resolvedActionFocusRequester = actionFocusRequester ?: remember { FocusRequester() }
    val expectedForwardEntryFocusRequester = remember { FocusRequester() }
    val expectedFieldFocusRequester = remember { FocusRequester() }
    val deleteStepAction: () -> Unit = {
        if (!expected.isNullOrBlank()) {
            val result = Messages.showOkCancelDialog(
                SpeqaBundle.message("dialog.deleteStep.message"),
                SpeqaBundle.message("dialog.deleteStep.title"),
                Messages.getOkButton(),
                Messages.getCancelButton(),
                Messages.getWarningIcon(),
            )
            if (result == Messages.OK) onDelete()
        } else {
            onDelete()
        }
    }
    val deleteIcon: javax.swing.Icon = AllIcons.Actions.GC
    val stepContextMenuItems = { listOf(IconMenuItem(SpeqaBundle.message("tooltip.deleteStep"), deleteIcon, deleteStepAction)) }
    var pendingExpectedFocus by remember { mutableStateOf(false) }
    var pendingExpectedButtonFocus by remember { mutableStateOf(false) }
    LaunchedEffect(pendingExpectedFocus) {
        if (pendingExpectedFocus) {
            expectedFieldFocusRequester.requestFocus()
            pendingExpectedFocus = false
        }
    }
    LaunchedEffect(pendingExpectedButtonFocus) {
        if (pendingExpectedButtonFocus) {
            expectedForwardEntryFocusRequester.requestFocus()
            pendingExpectedButtonFocus = false
        }
    }

    val currentOnFocusConsumed by androidx.compose.runtime.rememberUpdatedState(onFocusConsumed)
    LaunchedEffect(requestFocus) {
        if (requestFocus) {
            resolvedActionFocusRequester.requestFocus()
            currentOnFocusConsumed()
        }
    }

    val ticketTextFocusRequester = remember { FocusRequester() }

    val expectedAttachFocusRequester = remember { FocusRequester() }
    val attachmentPrimaryFocusRequesters = remember(attachments.size) {
        List(attachments.size) { FocusRequester() }
    }
    // Always on AddAttachmentButton — the last focusable element in the step
    val resolvedExpectedAttachFocusRequester = expectedReverseEntryFocusRequester ?: expectedAttachFocusRequester

    val linkPrimaryRequesters = remember(links.size) { List(links.size) { FocusRequester() } }
    val linkAddRequester = remember { FocusRequester() }
    val attachmentAddRequester = resolvedExpectedAttachFocusRequester
    val stepHoverSource = remember { MutableInteractionSource() }
    val isStepHovered by stepHoverSource.collectIsHoveredAsState()
    var isStepFocused by remember { mutableStateOf(false) }
    val isStepActive = isStepHovered || isStepFocused || isDragging
    ScenarioStepFrame(
        modifier = modifier
            .hoverable(stepHoverSource)
            .onFocusChanged { isStepFocused = it.hasFocus }
            .accentBar(active = stepCardAccentBarVisible(isFocused = isStepFocused, isDragging = isDragging))
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val metaOrCtrl = event.isMetaPressed || event.isCtrlPressed
                val alt = event.isAltPressed
                when {
                    metaOrCtrl && event.key == Key.D -> { onDuplicateStep(); true }
                    metaOrCtrl && event.key == Key.Backspace -> { onRequestDeleteStep(); true }
                    alt && event.key == Key.DirectionUp -> { onMoveStepUp(); true }
                    alt && event.key == Key.DirectionDown -> { onMoveStepDown(); true }
                    event.key == Key.Escape -> false
                    else -> false
                }
            }
            .padding(start = 4.dp),
        gutterModifier = Modifier.width(SpeqaLayout.stepNumberColumnWidth).padding(top = 1.dp),
        gutter = {
            Text(
                text = (index + 1).toString().padStart(2, '0'),
                color = SpeqaThemeColors.mutedForeground,
                fontSize = SpeqaTypography.numericFontSize,
                fontWeight = SpeqaTypography.numericWeight,
                letterSpacing = SpeqaTypography.numericTracking,
            )
            if (isStepActive) {
                Tooltip(tooltip = { Text(SpeqaBundle.message("tooltip.dragToReorder")) }) {
                    Icon(
                        dragHandleIcon,
                        contentDescription = SpeqaBundle.message("tooltip.dragToReorder"),
                        modifier = Modifier
                            .size(16.dp)
                            .semantics { role = Role.Button }
                            .handOnHover()
                            .contextMenuWithIcon(items = stepContextMenuItems)
                            .pointerInput(index) {
                                detectVerticalDragGestures(
                                    onDragStart = { onDragStart() },
                                    onDragEnd = { onDragEnd() },
                                    onDragCancel = { onDragEnd() },
                                    onVerticalDrag = { _, dragAmount -> onDrag(dragAmount) },
                                )
                            },
                        tint = SpeqaThemeColors.mutedForeground,
                    )
                }
            } else {
                Spacer(modifier = Modifier.size(16.dp))
            }
        },
        actionContent = {
            PlainTextInput(
                value = action,
                onValueChange = onActionChange,
                placeholder = SpeqaBundle.message("placeholder.action"),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(resolvedActionFocusRequester)
                    .focusProperties {
                        previous = previousActionFocusRequester ?: FocusRequester.Default
                        next = expectedForwardEntryFocusRequester
                    }
                    .onFocusChanged { if (it.isFocused) focusContext.current = FocusSlot(index, StepSlot.ACTION) },
                singleLine = false,
                onPlainEnter = {
                    if (expected == null) {
                        onExpectedChange("")
                        pendingExpectedFocus = true
                    } else {
                        expectedFieldFocusRequester.requestFocus()
                    }
                    true
                },
            )
        },
        expectedContent = {
            if (expected != null) {
                PlainTextInput(
                    value = expected,
                    onValueChange = { onExpectedChange(it) },
                    placeholder = SpeqaBundle.message("placeholder.setExpected"),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(expectedFieldFocusRequester)
                        .focusRequester(expectedForwardEntryFocusRequester)
                        .focusProperties {
                            previous = resolvedActionFocusRequester
                        }
                        .onFocusChanged { if (it.isFocused) focusContext.current = FocusSlot(index, StepSlot.EXPECTED) },
                    singleLine = false,
                    onPlainEnter = { onEnterFromExpected(); true },
                )
            } else {
                Text(
                    text = SpeqaBundle.message("placeholder.setExpected"),
                    color = SpeqaThemeColors.mutedForeground.copy(alpha = 0.6f),
                    fontSize = SpeqaTypography.placeholderFontSize,
                    fontStyle = FontStyle.Italic,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickableWithPointer(onClick = {
                            pendingExpectedFocus = true
                            onExpectedChange("")
                        })
                        .focusRequester(expectedForwardEntryFocusRequester)
                        .onFocusChanged { if (it.isFocused) focusContext.current = FocusSlot(index, StepSlot.EXPECTED) }
                        .focusable(),
                )
            }
        },
        metaContent = { layout ->
            StepMetaRow(
                stepIndex = index,
                tickets = tickets,
                links = links,
                attachments = attachments,
                project = project,
                tcFile = tcFile,
                onTicketsChange = onTicketsChange,
                onLinksChange = onLinksChange,
                onAttachmentsChange = onAttachmentsChange,
                onOpenFile = onOpenFile,
                attachmentRevision = attachmentRevision,
                ticketAddRequester = ticketTextFocusRequester,
                linkPrimaryRequesters = linkPrimaryRequesters,
                linkAddRequester = linkAddRequester,
                attachmentPrimaryRequesters = attachmentPrimaryFocusRequesters,
                attachmentAddRequester = attachmentAddRequester,
                narrow = layout.narrow,
            )
        },
    )
}
