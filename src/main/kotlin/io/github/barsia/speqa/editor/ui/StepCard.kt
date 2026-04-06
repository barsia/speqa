package io.github.barsia.speqa.editor.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInput
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
    project: Project? = null,
    tcFile: VirtualFile? = null,
    onOpenFile: (Attachment) -> Unit = {},
    attachmentRevision: Long = 0L,
    modifier: Modifier = Modifier,
    actionFocusRequester: FocusRequester? = null,
    actionReverseEntryFocusRequester: FocusRequester? = null,
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
    val removeIcon = IntelliJIconKey.fromPlatformIcon(AllIcons.Actions.GC, SpeqaLayout::class.java)
    val dragHandleIcon = IntelliJIconKey("/icons/dragHandle.svg", "/icons/dragHandle.svg", iconClass = SpeqaLayout::class.java)
    val deleteColor = SpeqaThemeColors.destructive
    val resolvedActionFocusRequester = actionFocusRequester ?: remember { FocusRequester() }
    val stepTrashFocusRequester = remember { FocusRequester() }
    val expectedForwardEntryFocusRequester = remember { FocusRequester() }
    var pendingExpectedFocus by remember { mutableStateOf(false) }

    val currentOnFocusConsumed by androidx.compose.runtime.rememberUpdatedState(onFocusConsumed)
    LaunchedEffect(requestFocus) {
        if (requestFocus) {
            resolvedActionFocusRequester.requestFocus()
            currentOnFocusConsumed()
        }
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            modifier = Modifier
                .width(SpeqaLayout.stepNumberColumnWidth)
                .padding(top = 1.dp),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = (index + 1).toString().padStart(2, '0'),
                color = SpeqaThemeColors.mutedForeground,
                fontSize = 12.sp,
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

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(SpeqaLayout.compactGap),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(SpeqaLayout.compactGap),
                verticalAlignment = Alignment.Top,
            ) {
                PlainTextInput(
                    value = action,
                    onValueChange = onActionChange,
                    placeholder = SpeqaBundle.message("placeholder.action"),
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(resolvedActionFocusRequester)
                        .focusProperties {
                            previous = previousActionFocusRequester ?: FocusRequester.Default
                            next = stepTrashFocusRequester
                        },
                    singleLine = false,
                )
                val stepTrashHoverSource = remember { MutableInteractionSource() }
                val isStepTrashHovered by stepTrashHoverSource.collectIsHoveredAsState()
                var isStepTrashFocused by remember { mutableStateOf(false) }
                val stepTrashTint = if (isStepTrashHovered || isStepTrashFocused) deleteColor else SpeqaThemeColors.mutedForeground
                Tooltip(tooltip = { Text(SpeqaBundle.message("tooltip.deleteStep")) }) {
                    SpeqaIconButton(
                        focusable = true,
                        onClick = {
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
                        },
                        modifier = Modifier
                            .focusRequester(stepTrashFocusRequester)
                            .then(actionReverseEntryFocusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
                            .focusProperties {
                                previous = resolvedActionFocusRequester
                                next = expectedForwardEntryFocusRequester
                            }
                            .hoverable(stepTrashHoverSource)
                            .onFocusChanged { isStepTrashFocused = it.hasFocus },
                    ) {
                        Icon(removeIcon, contentDescription = SpeqaBundle.message("tooltip.deleteStep"), modifier = Modifier.size(16.dp), tint = stepTrashTint)
                    }
                }
            }
            if (project != null && tcFile != null && actionAttachments.isNotEmpty()) {
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

            if (expected != null) {
                var trashColumnWidth by remember { mutableStateOf(0.dp) }
                val density = LocalDensity.current
                val expectedFieldFocusRequester = remember { FocusRequester() }
                LaunchedEffect(pendingExpectedFocus) {
                    if (pendingExpectedFocus) {
                        expectedFieldFocusRequester.requestFocus()
                        pendingExpectedFocus = false
                    }
                }
                val expectedTrashFocusRequester = remember { FocusRequester() }
                val expectedAttachFocusRequester = remember { FocusRequester() }
                val expectedAttachmentPrimaryFocusRequesters = remember(expectedAttachments.size) {
                    List(expectedAttachments.size) { FocusRequester() }
                }
                val expectedAttachmentDeleteFocusRequesters = remember(expectedAttachments.size, expectedReverseEntryFocusRequester) {
                    List(expectedAttachments.size) { attachmentIndex ->
                        if (attachmentIndex == expectedAttachments.lastIndex && expectedReverseEntryFocusRequester != null) {
                            expectedReverseEntryFocusRequester
                        } else {
                            FocusRequester()
                        }
                    }
                }
                val resolvedExpectedAttachFocusRequester = if (expectedAttachments.isEmpty() && expectedReverseEntryFocusRequester != null) {
                    expectedReverseEntryFocusRequester
                } else {
                    expectedAttachFocusRequester
                }
                Column(verticalArrangement = Arrangement.spacedBy(SpeqaLayout.itemGap)) {
                Text(
                    SpeqaBundle.message("label.expected"),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = SpeqaThemeColors.mutedForeground,
                    modifier = Modifier.padding(end = trashColumnWidth),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(SpeqaLayout.compactGap),
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(SpeqaLayout.itemGap),
                    ) {
                        PlainTextInput(
                            value = expected,
                            onValueChange = { onExpectedChange(it) },
                            placeholder = SpeqaBundle.message("placeholder.expected"),
                            modifier = Modifier
                                .focusRequester(expectedFieldFocusRequester)
                                .focusRequester(expectedForwardEntryFocusRequester)
                                .focusProperties {
                                    previous = stepTrashFocusRequester
                                    next = expectedTrashFocusRequester
                                },
                            singleLine = false,
                        )
                        if (project != null && tcFile != null) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                AddAttachmentButton(
                                    project,
                                    tcFile,
                                    expectedAttachments,
                                    onExpectedAttachmentsChange,
                                    modifier = Modifier
                                        .focusRequester(resolvedExpectedAttachFocusRequester)
                                        .focusProperties {
                                            previous = expectedTrashFocusRequester
                                            next = expectedAttachmentPrimaryFocusRequesters.firstOrNull() ?: (nextExpectedExitFocusRequester ?: FocusRequester.Default)
                                        },
                                )
                            }
                        }
                        if (project != null && tcFile != null && expectedAttachments.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
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
                                        actionModifier = Modifier
                                            .focusRequester(expectedAttachmentPrimaryFocusRequesters[attachmentIndex])
                                            .focusProperties {
                                                previous = if (attachmentIndex == 0) {
                                                    resolvedExpectedAttachFocusRequester
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
                                                    ?: (nextExpectedExitFocusRequester ?: FocusRequester.Default)
                                            },
                                    )
                                }
                            }
                        }
                    }
                    val expectedTrashHoverSource = remember { MutableInteractionSource() }
                    val isExpectedTrashHovered by expectedTrashHoverSource.collectIsHoveredAsState()
                    var isExpectedTrashFocused by remember { mutableStateOf(false) }
                    val expectedTrashTint = if (isExpectedTrashHovered || isExpectedTrashFocused) deleteColor else SpeqaThemeColors.mutedForeground
                    Tooltip(tooltip = { Text(SpeqaBundle.message("tooltip.deleteExpected")) }) {
                        SpeqaIconButton(
                            focusable = true,
                            onClick = { onExpectedChange(null) },
                            modifier = Modifier
                                .focusRequester(expectedTrashFocusRequester)
                                .focusProperties {
                                    previous = expectedFieldFocusRequester
                                    next = resolvedExpectedAttachFocusRequester
                                }
                                .hoverable(expectedTrashHoverSource)
                                .onFocusChanged { isExpectedTrashFocused = it.hasFocus }
                                .onGloballyPositioned { coordinates ->
                                    with(density) {
                                        trashColumnWidth = coordinates.size.width.toDp() + SpeqaLayout.compactGap
                                    }
                                },
                        ) {
                            Icon(removeIcon, contentDescription = SpeqaBundle.message("tooltip.deleteExpected"), modifier = Modifier.size(16.dp), tint = expectedTrashTint)
                        }
                    }
                }
                }
            } else {
                QuietActionText(
                    label = SpeqaBundle.message("form.addExpected"),
                    onClick = {
                        pendingExpectedFocus = true
                        onExpectedChange("")
                    },
                    enabled = true,
                    modifier = Modifier
                        .focusRequester(expectedForwardEntryFocusRequester)
                        .then(expectedReverseEntryFocusRequester?.let { Modifier.focusRequester(it) } ?: Modifier),
                    previousFocusRequester = stepTrashFocusRequester,
                    nextFocusRequester = nextExpectedExitFocusRequester,
                )
            }
        }
    }
}
