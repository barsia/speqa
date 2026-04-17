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
import org.jetbrains.jewel.bridge.icon.fromPlatformIcon
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.Tooltip
import org.jetbrains.jewel.ui.icon.IntelliJIconKey

@OptIn(ExperimentalJewelApi::class, ExperimentalFoundationApi::class)
@Composable
fun StepCard(
    index: Int,
    action: String,
    expected: String?,
    onActionChange: (String) -> Unit,
    onExpectedChange: (String?) -> Unit,
    onDelete: () -> Unit,
    actionAttachments: List<Attachment> = emptyList(),
    expectedAttachments: List<Attachment> = emptyList(),
    onActionAttachmentsChange: (List<Attachment>) -> Unit = {},
    onExpectedAttachmentsChange: (List<Attachment>) -> Unit = {},
    ticket: String? = null,
    onTicketChange: (String?) -> Unit = {},
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
) {
    val dragHandleIcon = IntelliJIconKey("/icons/dragHandle.svg", "/icons/dragHandle.svg", iconClass = SpeqaLayout::class.java)
    val resolvedActionFocusRequester = actionFocusRequester ?: remember { FocusRequester() }
    val expectedForwardEntryFocusRequester = remember { FocusRequester() }
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

    // Ticket editing state — outside project check so action bar can reference it
    var isTicketEditing by remember { mutableStateOf(false) }
    val ticketTextFocusRequester = remember { FocusRequester() }
    val ticketFocusRequester = remember { FocusRequester() }

    // Expected attachment focus requesters
    val expectedAttachFocusRequester = remember { FocusRequester() }
    val expectedAttachmentPrimaryFocusRequesters = remember(expectedAttachments.size) {
        List(expectedAttachments.size) { FocusRequester() }
    }
    val expectedAttachmentDeleteFocusRequesters = remember(expectedAttachments.size) {
        List(expectedAttachments.size) { FocusRequester() }
    }
    // Always on AddAttachmentButton — the last focusable element in the step
    val resolvedExpectedAttachFocusRequester = expectedReverseEntryFocusRequester ?: expectedAttachFocusRequester

    val expectedIcon = IntelliJIconKey.fromPlatformIcon(AllIcons.RunConfigurations.TestCustom, SpeqaLayout::class.java)
    val gutterWidth = SpeqaLayout.stepNumberColumnWidth
    val gutterGap = 4.dp

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(SpeqaLayout.compactGap),
    ) {
       Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        // === Action row: gutter(number + drag) | action field ===
        Row(
            horizontalArrangement = Arrangement.spacedBy(gutterGap),
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier
                    .width(gutterWidth)
                    .padding(top = 1.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = (index + 1).toString().padStart(2, '0'),
                    color = SpeqaThemeColors.mutedForeground,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.6.sp,
                )
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
            }
            PlainTextInput(
                value = action,
                onValueChange = onActionChange,
                placeholder = SpeqaBundle.message("placeholder.action"),
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(resolvedActionFocusRequester)
                    .focusProperties {
                        previous = previousActionFocusRequester ?: FocusRequester.Default
                        next = expectedForwardEntryFocusRequester
                    },
                singleLine = false,
            )
        }

        // === Action attachments (indented) ===
        if (project != null && tcFile != null && actionAttachments.isNotEmpty()) {
            Box(modifier = Modifier.padding(start = gutterWidth + gutterGap)) {
                AttachmentList(
                    attachments = actionAttachments,
                    project = project,
                    tcFile = tcFile,
                    onAttachmentsChange = onActionAttachmentsChange,
                    onOpenFile = onOpenFile,
                    showAddButton = false,
                    attachmentRevision = attachmentRevision,
                )
            }
        }
       }

       Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        // === Expected row: gutter(icon) | expected field/button ===
        Row(
            horizontalArrangement = Arrangement.spacedBy(gutterGap),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier = Modifier
                    .width(gutterWidth)
                    .padding(top = if (expected != null) 3.dp else 1.dp),
                contentAlignment = Alignment.TopCenter,
            ) {
                if (expected != null) {
                    Tooltip(tooltip = { Text(SpeqaBundle.message("tooltip.expectedContextMenu")) }) {
                        Icon(
                            expectedIcon,
                            contentDescription = SpeqaBundle.message("label.expectedResult"),
                            modifier = Modifier
                                .size(14.dp)
                                .handOnHover()
                                .contextMenuWithIcon(items = {
                                    listOf(IconMenuItem(SpeqaBundle.message("tooltip.deleteExpected"), deleteIcon) {
                                        pendingExpectedButtonFocus = true
                                        onExpectedChange(null)
                                    })
                                }),
                            tint = SpeqaThemeColors.mutedForeground,
                        )
                    }
                } else {
                    Icon(
                        expectedIcon,
                        contentDescription = SpeqaBundle.message("label.expectedResult"),
                        modifier = Modifier.size(14.dp),
                        tint = SpeqaThemeColors.mutedForeground,
                    )
                }
            }
            if (expected != null) {
                val expectedFieldFocusRequester = remember { FocusRequester() }
                LaunchedEffect(pendingExpectedFocus) {
                    if (pendingExpectedFocus) {
                        expectedFieldFocusRequester.requestFocus()
                        pendingExpectedFocus = false
                    }
                }
                PlainTextInput(
                    value = expected,
                    onValueChange = { onExpectedChange(it) },
                    placeholder = SpeqaBundle.message("placeholder.expected"),
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(expectedFieldFocusRequester)
                        .focusRequester(expectedForwardEntryFocusRequester)
                        .focusProperties {
                            previous = resolvedActionFocusRequester
                            next = ticketTextFocusRequester
                        },
                    singleLine = false,
                )
            } else {
                QuietActionText(
                    label = SpeqaBundle.message("form.addExpected"),
                    onClick = {
                        pendingExpectedFocus = true
                        onExpectedChange("")
                    },
                    enabled = true,
                    plain = true,
                    modifier = Modifier
                        .focusRequester(expectedForwardEntryFocusRequester)
                        .then(if (project == null || tcFile == null) {
                            expectedReverseEntryFocusRequester?.let { Modifier.focusRequester(it) } ?: Modifier
                        } else Modifier),
                    previousFocusRequester = resolvedActionFocusRequester,
                    nextFocusRequester = ticketTextFocusRequester,
                )
            }
        }

        // === Ticket + Attachments row (indented, side by side) ===
        if (project != null && tcFile != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = gutterWidth + gutterGap),
                horizontalArrangement = Arrangement.spacedBy(SpeqaLayout.compactGap),
                verticalAlignment = Alignment.Top,
            ) {
                // Left column: Ticket slot
                Column(modifier = Modifier.weight(1f)) {
                    if (!ticket.isNullOrBlank() || isTicketEditing) {
                        TicketRow(
                            ticket = ticket.orEmpty(),
                            project = project,
                            isEditing = isTicketEditing,
                            onTicketCommit = { value ->
                                val normalized = value
                                    .split(Regex("[,;\\s]+"))
                                    .filter { it.isNotBlank() }
                                    .joinToString(", ")
                                onTicketChange(normalized.ifBlank { null })
                                isTicketEditing = false
                            },
                            onCancel = { isTicketEditing = false },
                            onEditToggle = { isTicketEditing = !isTicketEditing },
                            textFocusRequester = ticketTextFocusRequester,
                            pencilFocusRequester = ticketFocusRequester,
                            previousFocusRequester = expectedForwardEntryFocusRequester,
                            nextFocusRequester = expectedAttachmentPrimaryFocusRequesters.firstOrNull()
                                ?: resolvedExpectedAttachFocusRequester,
                        )
                    } else {
                        TicketLinkButton(
                            onClick = { isTicketEditing = true },
                            modifier = Modifier
                                .focusRequester(ticketTextFocusRequester)
                                .focusProperties {
                                    previous = expectedForwardEntryFocusRequester
                                    next = expectedAttachmentPrimaryFocusRequesters.firstOrNull()
                                        ?: resolvedExpectedAttachFocusRequester
                                },
                        )
                    }
                }

                // Right column: Attachments slot (right-aligned)
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.End,
                ) {
                    val lastTicketRequester = if (!ticket.isNullOrBlank()) ticketFocusRequester else ticketTextFocusRequester
                    expectedAttachments.forEachIndexed { attachmentIndex, attachment ->
                        val missing = AttachmentSupport.resolveFile(project, tcFile, attachment) == null
                        AttachmentRow(
                            attachment = attachment,
                            onClick = { onOpenFile(attachment) },
                            onDelete = {
                                onExpectedAttachmentsChange(expectedAttachments - attachment)
                            },
                            isMissing = missing,
                            onRelink = null,
                            compact = true,
                            actionModifier = Modifier
                                .focusRequester(expectedAttachmentPrimaryFocusRequesters[attachmentIndex])
                                .focusProperties {
                                    previous = if (attachmentIndex == 0) {
                                        lastTicketRequester
                                    } else {
                                        expectedAttachmentDeleteFocusRequesters[attachmentIndex - 1]
                                    }
                                    next = expectedAttachmentDeleteFocusRequesters[attachmentIndex]
                                },
                            deleteModifier = Modifier
                                .focusRequester(expectedAttachmentDeleteFocusRequesters[attachmentIndex])
                                .focusProperties {
                                    previous = expectedAttachmentPrimaryFocusRequesters[attachmentIndex]
                                    next = expectedAttachmentPrimaryFocusRequesters.getOrNull(attachmentIndex + 1)
                                        ?: resolvedExpectedAttachFocusRequester
                                },
                        )
                    }
                    AddAttachmentButton(
                        project,
                        tcFile,
                        expectedAttachments,
                        onExpectedAttachmentsChange,
                        modifier = Modifier
                            .focusRequester(resolvedExpectedAttachFocusRequester)
                            .focusProperties {
                                previous = expectedAttachmentDeleteFocusRequesters.lastOrNull()
                                    ?: lastTicketRequester
                                next = nextExpectedExitFocusRequester ?: FocusRequester.Default
                            },
                    )
                }
            }
        }
       }
    }
}

@Composable
private fun TicketRow(
    ticket: String,
    project: Project,
    isEditing: Boolean = false,
    onTicketCommit: (String) -> Unit = {},
    onCancel: () -> Unit = {},
    onEditToggle: () -> Unit = {},
    textFocusRequester: FocusRequester = remember { FocusRequester() },
    pencilFocusRequester: FocusRequester = remember { FocusRequester() },
    pencilExtraFocusRequester: FocusRequester? = null,
    previousFocusRequester: FocusRequester? = null,
    nextFocusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier,
) {
    val settings = remember(project) { io.github.barsia.speqa.settings.SpeqaSettings.getInstance(project) }
    val focusManager = LocalFocusManager.current

    var draft by remember(ticket) { mutableStateOf(ticket) }
    val textFieldFocusRequester = remember { FocusRequester() }
    var wasFocused by remember { mutableStateOf(false) }

    LaunchedEffect(isEditing) {
        if (!isEditing) wasFocused = false
    }

    val ticketPrefixIcon = IntelliJIconKey("/icons/ticket.svg", "/icons/ticket.svg", iconClass = SpeqaLayout::class.java)
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(0.dp)) {
        Box(
            modifier = Modifier.height(24.dp).padding(end = 6.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            val prefixTint = if (ticket.isNotBlank()) SpeqaThemeColors.foreground else SpeqaThemeColors.mutedForeground
            Icon(ticketPrefixIcon, contentDescription = SpeqaBundle.message("label.ticket"), modifier = Modifier.size(14.dp), tint = prefixTint)
        }

        // Text area — weight(1f) so pencil/save icon stays at fixed position
        if (isEditing) {
            var tfValue by remember(draft) {
                mutableStateOf(TextFieldValue(draft, selection = androidx.compose.ui.text.TextRange(draft.length)))
            }
            val editTextStyle = TextStyle(fontSize = 11.sp, color = SpeqaThemeColors.accent)
            val textMeasurer = rememberTextMeasurer()
            val density = LocalDensity.current
            val measuredWidth = remember(tfValue.text, editTextStyle) {
                with(density) {
                    val w = textMeasurer.measure(
                        tfValue.text.ifBlank { SpeqaBundle.message("placeholder.ticketId") },
                        editTextStyle,
                    ).size.width.toDp()
                    maxOf(w + 2.dp, 40.dp) // +2dp for cursor
                }
            }
            Box(
                modifier = Modifier
                    .heightIn(min = 24.dp)
                    .wrapContentHeight(Alignment.CenterVertically)
                    .border(1.dp, SpeqaThemeColors.accent, RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 3.dp),
            ) {
                BasicTextField(
                    value = tfValue,
                    onValueChange = { tfValue = it; draft = it.text },
                    textStyle = editTextStyle,
                    singleLine = true,
                    cursorBrush = SolidColor(SpeqaThemeColors.accent),
                    modifier = Modifier
                        .width(measuredWidth)
                        .focusRequester(textFieldFocusRequester)
                        .onPreviewKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown) {
                                when (event.key) {
                                    Key.Enter, Key.NumPadEnter -> { onTicketCommit(draft); true }
                                    Key.Escape -> { onCancel(); true }
                                    else -> false
                                }
                            } else false
                        }
                        .onFocusChanged { state ->
                            if (state.isFocused) wasFocused = true
                            if (!state.isFocused && wasFocused) onTicketCommit(draft)
                        },
                    decorationBox = { innerTextField ->
                        if (draft.isBlank()) {
                            Text(SpeqaBundle.message("placeholder.ticketId"), fontSize = 11.sp, color = SpeqaThemeColors.mutedForeground)
                        }
                        innerTextField()
                    },
                )
            }
            LaunchedEffect(isEditing) {
                if (isEditing) {
                    kotlinx.coroutines.yield()
                    textFieldFocusRequester.requestFocus()
                }
            }
        } else {
            val ids = ticket.split(",").map { it.trim() }.filter { it.isNotBlank() }
            val annotated = buildAnnotatedString {
                ids.forEachIndexed { idx, id ->
                    if (idx > 0) append(", ")
                    pushStringAnnotation("ticket", id)
                    withStyle(SpanStyle(color = SpeqaThemeColors.accent)) {
                        append(id)
                    }
                    pop()
                }
            }
            var isTextFocused by remember { mutableStateOf(false) }
            val textFocusBorder = if (isTextFocused) SpeqaThemeColors.accent else androidx.compose.ui.graphics.Color.Transparent
            Box(
                modifier = Modifier
                    .heightIn(min = 24.dp)
                    .wrapContentHeight(Alignment.CenterVertically)
                    .border(1.dp, textFocusBorder, RoundedCornerShape(4.dp))
                    .focusRequester(textFocusRequester)
                    .onFocusChanged { isTextFocused = it.isFocused }
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (event.key) {
                            Key.Enter, Key.NumPadEnter -> {
                                ids.forEach { id ->
                                    com.intellij.ide.BrowserUtil.browse(settings.resolveTicketUrl(id))
                                }
                                true
                            }
                            Key.Tab -> {
                                focusManager.moveFocus(if (event.isShiftPressed) FocusDirection.Previous else FocusDirection.Next)
                                true
                            }
                            else -> false
                        }
                    }
                    .focusProperties {
                        if (previousFocusRequester != null) previous = previousFocusRequester
                        next = pencilFocusRequester
                    }
                    .focusable()
                    .handOnHover()
                    .padding(horizontal = 4.dp, vertical = 3.dp),
            ) {
                ClickableText(
                    text = annotated,
                    style = TextStyle(fontSize = 11.sp, color = SpeqaThemeColors.mutedForeground),
                    onClick = { offset ->
                        annotated.getStringAnnotations("ticket", offset, offset).firstOrNull()?.let { annotation ->
                            com.intellij.ide.BrowserUtil.browse(settings.resolveTicketUrl(annotation.item))
                        }
                    },
                )
            }
        }

        val editSaveIcon = IntelliJIconKey.fromPlatformIcon(
            if (isEditing) AllIcons.Actions.MenuSaveall else AllIcons.Actions.Edit,
            SpeqaLayout::class.java,
        )
        val editHoverSource = remember { MutableInteractionSource() }
        val isEditHovered by editHoverSource.collectIsHoveredAsState()
        var isEditFocused by remember { mutableStateOf(false) }
        val editTint = if (isEditHovered || isEditFocused) SpeqaThemeColors.foreground else SpeqaThemeColors.mutedForeground
        val editFocusBorder = if (isEditFocused) SpeqaThemeColors.accent else androidx.compose.ui.graphics.Color.Transparent
        Box(
            modifier = Modifier
                .size(24.dp)
                .border(1.dp, editFocusBorder, RoundedCornerShape(4.dp))
                .hoverable(editHoverSource)
                .focusRequester(pencilFocusRequester)
                .then(pencilExtraFocusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
                .onFocusChanged { isEditFocused = it.isFocused }
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.Enter, Key.NumPadEnter, Key.Spacebar -> { onEditToggle(); true }
                        Key.Tab -> {
                            focusManager.moveFocus(if (event.isShiftPressed) FocusDirection.Previous else FocusDirection.Next)
                            true
                        }
                        else -> false
                    }
                }
                .focusProperties {
                    previous = textFocusRequester
                    if (nextFocusRequester != null) next = nextFocusRequester
                }
                .focusable()
                .handOnHover()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { onEditToggle() },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                editSaveIcon,
                contentDescription = if (isEditing) SpeqaBundle.message("tooltip.save") else SpeqaBundle.message("tooltip.edit"),
                modifier = Modifier.size(14.dp),
                tint = editTint,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TicketLinkButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val ticketIcon = IntelliJIconKey("/icons/ticket.svg", "/icons/ticket.svg", iconClass = SpeqaLayout::class.java)
    val hoverSource = remember { MutableInteractionSource() }
    val isHovered by hoverSource.collectIsHoveredAsState()
    var isFocused by remember { mutableStateOf(false) }
    val tint = if (isHovered || isFocused) SpeqaThemeColors.foreground else SpeqaThemeColors.mutedForeground
    Row(
        modifier = modifier
            .semantics { role = Role.Button }
            .hoverable(hoverSource)
            .onFocusChanged { isFocused = it.hasFocus }
            .clickableWithPointer(focusable = true, showFocusBorder = true) { onClick() }
            .padding(end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.height(24.dp).padding(end = 6.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Icon(ticketIcon, contentDescription = SpeqaBundle.message("tooltip.linkTicket"), modifier = Modifier.size(14.dp), tint = tint)
        }
        Text(SpeqaBundle.message("tooltip.linkTicket"), fontSize = 12.sp, color = tint)
    }
}
