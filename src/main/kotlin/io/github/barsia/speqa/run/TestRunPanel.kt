package io.github.barsia.speqa.run

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import io.github.barsia.speqa.editor.ui.clickableWithPointer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.project.Project
import io.github.barsia.speqa.SpeqaBundle
import io.github.barsia.speqa.editor.ScrollSyncController
import io.github.barsia.speqa.editor.ui.AttachmentRow
import io.github.barsia.speqa.editor.ui.InlineEditableIdRow
import io.github.barsia.speqa.editor.ui.LinkRow
import io.github.barsia.speqa.editor.ui.MetadataValueKind
import io.github.barsia.speqa.editor.ui.MarkdownText
import io.github.barsia.speqa.editor.ui.SpeqaLayout
import io.github.barsia.speqa.editor.ui.SpeqaThemeColors
import io.github.barsia.speqa.editor.ui.PlainTextInput
import io.github.barsia.speqa.editor.ui.SectionHeaderWithDivider
import io.github.barsia.speqa.editor.ui.SpeqaIconButton
import io.github.barsia.speqa.editor.ui.TagCloud
import io.github.barsia.speqa.editor.ui.handOnHover
import io.github.barsia.speqa.editor.ui.rememberTestRunMetadataActions
import io.github.barsia.speqa.editor.ui.SurfaceDivider
import io.github.barsia.speqa.editor.ui.DateIconLabel
import io.github.barsia.speqa.model.Attachment
import io.github.barsia.speqa.model.DescriptionBlock
import io.github.barsia.speqa.model.Link
import io.github.barsia.speqa.model.PreconditionsBlock
import io.github.barsia.speqa.model.Priority
import io.github.barsia.speqa.model.RunResult
import io.github.barsia.speqa.model.StepResult
import io.github.barsia.speqa.model.StepVerdict
import io.github.barsia.speqa.model.TestCaseBodyBlock
import io.github.barsia.speqa.registry.IdType
import org.jetbrains.jewel.bridge.icon.fromPlatformIcon
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.ListComboBox
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.Tooltip
import org.jetbrains.jewel.ui.icon.IntelliJIconKey
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Composable
fun TestRunPanel(
    project: Project,
    scrollSyncController: ScrollSyncController? = null,
    title: String,
    tags: List<String> = emptyList(),
    runId: Int?,
    nextFreeRunId: Int,
    isRunIdDuplicate: Boolean,
    isRunIdEditing: Boolean,
    onRunIdEditingChange: (Boolean) -> Unit,
    onRunIdAssign: (Int) -> Unit,
    createdLabel: String,
    startedAt: LocalDateTime?,
    finishedAt: LocalDateTime?,
    result: RunResult,
    manualResult: Boolean,
    onResultOverride: (RunResult) -> Unit,
    stepResults: List<StepResult>,
    environment: String,
    environmentOptions: List<String>,
    runner: String,
    onEnvironmentChange: (String) -> Unit,
    onRunnerChange: (String) -> Unit,
    onStepVerdictChange: (Int, StepVerdict) -> Unit,
    onStepCommentChange: (Int, String) -> Unit,
    priority: Priority?,
    bodyBlocks: List<TestCaseBodyBlock>,
    links: List<Link>,
    attachments: List<Attachment>,
    onOpenAttachment: (Attachment) -> Unit,
    comment: String,
    onCommentChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    val totalSteps = stepResults.size
    val (tagClick, tagTooltip, tagMenu) = rememberTestRunMetadataActions(project, MetadataValueKind.TAG)
    val focusSinkRequester = remember { FocusRequester() }

    // Scroll sync: editor → compose
    scrollSyncController?.let { sync ->
        LaunchedEffect(sync) {
            sync.editorScrollFraction.collectLatest { fraction ->
                val maxScroll = scrollState.maxValue
                if (maxScroll > 0) {
                    val targetY = (fraction * maxScroll).toInt().coerceIn(0, maxScroll)
                    scrollState.scrollTo(targetY)
                }
            }
        }
    }

    // Scroll sync: compose → editor
    val currentScrollSync by rememberUpdatedState(scrollSyncController)
    LaunchedEffect(scrollState) {
        snapshotFlow { scrollState.value }
            .distinctUntilChanged()
            .collect { scrollY ->
                val sync = currentScrollSync ?: return@collect
                val maxScroll = scrollState.maxValue
                if (maxScroll > 0) {
                    val fraction = scrollY.toFloat() / maxScroll
                    sync.onComposeScroll(fraction)
                }
            }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SpeqaThemeColors.surface)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = true)
                    val up = waitForUpOrCancellation()
                    if (BackgroundFocusSinkPolicy.shouldRequestSinkFocus(pointerUp = up != null, upConsumed = up?.isConsumed == true)) {
                        focusSinkRequester.requestFocus()
                    }
                }
            },
    ) {
        Box(
            modifier = Modifier
                .size(0.dp)
                .focusRequester(focusSinkRequester)
                .focusTarget(),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(start = SpeqaLayout.pagePadding, end = SpeqaLayout.pagePadding, top = SpeqaLayout.compactGap, bottom = SpeqaLayout.pagePadding),
            verticalArrangement = Arrangement.spacedBy(SpeqaLayout.blockGap),
        ) {
            // Header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SpeqaThemeColors.headerSurface, RoundedCornerShape(SpeqaLayout.headerRadius))
                    .padding(
                        start = SpeqaLayout.contentInset,
                        end = SpeqaLayout.contentInset,
                        top = SpeqaLayout.contentInset,
                        bottom = 0.dp,
                    ),
                verticalArrangement = Arrangement.spacedBy(SpeqaLayout.compactGap),
            ) {
                val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(SpeqaLayout.compactGap),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    InlineEditableIdRow(
                        id = runId,
                        idType = IdType.TEST_RUN,
                        nextFreeId = nextFreeRunId,
                        isDuplicate = isRunIdDuplicate,
                        isEditing = isRunIdEditing,
                        onEditingChange = onRunIdEditingChange,
                        onIdAssign = onRunIdAssign,
                    )
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(SpeqaLayout.compactGap),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        DateIconLabel(
                            iconKey = IntelliJIconKey("/icons/calendarCreated.svg", "/icons/calendarCreated.svg", iconClass = SpeqaLayout::class.java),
                            label = SpeqaBundle.message("run.tooltip.created"),
                            dateLabel = createdLabel,
                        )
                        startedAt?.let {
                            DateIconLabel(
                                iconKey = IntelliJIconKey("/icons/calendarUpdated.svg", "/icons/calendarUpdated.svg", iconClass = SpeqaLayout::class.java),
                                label = SpeqaBundle.message("run.tooltip.started"),
                                dateLabel = it.format(dateFormatter),
                            )
                        }
                        finishedAt?.let {
                            DateIconLabel(
                                iconKey = IntelliJIconKey("/icons/calendarFinished.svg", "/icons/calendarFinished.svg", iconClass = SpeqaLayout::class.java),
                                label = SpeqaBundle.message("run.tooltip.finished"),
                                dateLabel = it.format(dateFormatter),
                            )
                        }
                    }
                }
                Text(
                    title.ifBlank { SpeqaBundle.message("label.untitledTestCase") },
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = SpeqaThemeColors.foreground,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(SpeqaLayout.pagePadding),
                    verticalArrangement = Arrangement.spacedBy(SpeqaLayout.compactGap),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        SpeqaBundle.message("run.progress", stepResults.count { it.verdict != StepVerdict.NONE }, totalSteps),
                        fontSize = 13.sp,
                        color = SpeqaThemeColors.mutedForeground,
                    )
                    if (result == RunResult.NOT_STARTED || result == RunResult.IN_PROGRESS) {
                        Text(
                            if (result == RunResult.NOT_STARTED) SpeqaBundle.message("run.notStarted")
                            else SpeqaBundle.message("run.inProgress"),
                            fontSize = 13.sp,
                            color = SpeqaThemeColors.mutedForeground,
                        )
                    } else {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(SpeqaLayout.compactGap),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                SpeqaBundle.message("run.result"),
                                fontSize = 13.sp,
                                color = SpeqaThemeColors.mutedForeground,
                            )
                            val selectableResults = listOf(RunResult.PASSED, RunResult.FAILED, RunResult.BLOCKED)
                            val resultItems = selectableResults.map { it.label.replaceFirstChar(Char::uppercase) }
                            val selectedResultIndex = selectableResults.indexOf(result)
                            ListComboBox(
                                items = resultItems,
                                selectedIndex = selectedResultIndex,
                                modifier = Modifier.heightIn(min = SpeqaLayout.controlHeight).handOnHover(),
                                onSelectedItemChange = { index -> onResultOverride(selectableResults[index]) },
                            )
                        }
                    }
                    if (tags.isNotEmpty()) {
                        TagCloud(
                            tags = tags,
                            allKnownTags = emptyList(),
                            onTagsChange = null,
                            label = SpeqaBundle.message("label.tags"),
                            coloredChips = true,
                            onChipClick = tagClick,
                            chipTooltip = tagTooltip,
                            chipContextActions = tagMenu,
                            showLabel = false,
                        )
                    }
                }

                // Environment / Runner inside header (same compactGap spacing as other header rows)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(SpeqaLayout.sectionGap),
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(SpeqaLayout.blockGap),
                    ) {
                        SectionHeaderWithDivider(SpeqaBundle.message("run.environment"))
                        if (environmentOptions.isNotEmpty()) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(SpeqaLayout.compactGap),
                                verticalArrangement = Arrangement.spacedBy(SpeqaLayout.compactGap),
                            ) {
                                environmentOptions.forEach { option ->
                                    VerdictChip(
                                        text = option,
                                        selected = environment == option,
                                        selectedBackground = SpeqaThemeColors.accentSubtle,
                                        selectedTextColor = SpeqaThemeColors.accent,
                                        onClick = { onEnvironmentChange(option) },
                                    )
                                }
                            }
                        }
                        PlainTextInput(
                            value = environment,
                            onValueChange = onEnvironmentChange,
                            placeholder = SpeqaBundle.message("placeholder.environment.run"),
                        )
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(SpeqaLayout.blockGap),
                    ) {
                        SectionHeaderWithDivider(SpeqaBundle.message("run.runner"))
                        PlainTextInput(
                            value = runner,
                            onValueChange = onRunnerChange,
                            placeholder = SpeqaBundle.message("placeholder.runner"),
                        )
                    }
                }
            }

            // Body blocks (readonly from test case)
            bodyBlocks.forEach { block ->
                when (block) {
                    is DescriptionBlock -> if (block.markdown.isNotBlank()) {
                        Column(
                            modifier = Modifier.padding(horizontal = SpeqaLayout.contentInset),
                            verticalArrangement = Arrangement.spacedBy(SpeqaLayout.blockGap),
                        ) {
                            SectionHeaderWithDivider(SpeqaBundle.message("label.description"))
                            MarkdownText(block.markdown)
                        }
                    }
                    is PreconditionsBlock -> if (block.markdown.isNotBlank()) {
                        Column(
                            modifier = Modifier.padding(horizontal = SpeqaLayout.contentInset),
                            verticalArrangement = Arrangement.spacedBy(SpeqaLayout.blockGap),
                        ) {
                            SectionHeaderWithDivider(SpeqaBundle.message("label.preconditions"))
                            MarkdownText(block.markdown)
                        }
                    }
                }
            }

            // Attachments and Links (readonly from test case) — side by side like TestCasePreview
            if (attachments.isNotEmpty() || links.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = SpeqaLayout.contentInset),
                    horizontalArrangement = Arrangement.spacedBy(SpeqaLayout.blockGap),
                ) {
                    if (attachments.isNotEmpty()) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(SpeqaLayout.itemGap),
                        ) {
                            SectionHeaderWithDivider(SpeqaBundle.message("label.attachments"))
                            attachments.forEach { att ->
                                AttachmentRow(
                                    attachment = att,
                                    onClick = { onOpenAttachment(att) },
                                    onDelete = null,
                                )
                            }
                        }
                    }
                    if (links.isNotEmpty()) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(SpeqaLayout.itemGap),
                        ) {
                            SectionHeaderWithDivider(SpeqaBundle.message("label.links"))
                            links.forEach { link ->
                                LinkRow(
                                    link = link,
                                    onClick = { BrowserUtil.browse(link.url) },
                                )
                            }
                        }
                    }
                }
            }

            // Step Results section
            Column(
                modifier = Modifier.padding(horizontal = SpeqaLayout.contentInset),
                verticalArrangement = Arrangement.spacedBy(SpeqaLayout.blockGap),
            ) {
                SectionHeaderWithDivider(SpeqaBundle.message("run.stepResults"))

                if (stepResults.isEmpty()) {
                    Text(
                        SpeqaBundle.message("run.noStepsFound"),
                        fontSize = 13.sp,
                        color = SpeqaThemeColors.mutedForeground,
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                        stepResults.forEachIndexed { index, step ->
                            if (index > 0) SurfaceDivider()
                            StepResultRow(
                                index = index,
                                step = step,
                                onVerdictChange = { verdict -> onStepVerdictChange(index, verdict) },
                                onCommentChange = { comment -> onStepCommentChange(index, comment) },
                                onOpenAttachment = onOpenAttachment,
                            )
                        }
                    }
                }
            }

            // Overall run comment
            Column(
                modifier = Modifier.padding(horizontal = SpeqaLayout.contentInset),
                verticalArrangement = Arrangement.spacedBy(SpeqaLayout.blockGap),
            ) {
                SectionHeaderWithDivider(SpeqaBundle.message("run.comment"))
                PlainTextInput(
                    value = comment,
                    onValueChange = onCommentChange,
                    placeholder = SpeqaBundle.message("run.commentPlaceholder"),
                    singleLine = false,
                    minHeight = 40,
                )
            }
        }
    }
}

@OptIn(ExperimentalJewelApi::class, ExperimentalFoundationApi::class)
@Composable
private fun StepResultRow(
    index: Int,
    step: StepResult,
    onVerdictChange: (StepVerdict) -> Unit,
    onCommentChange: (String) -> Unit,
    onOpenAttachment: (Attachment) -> Unit,
) {
    var showComment by remember(index) { mutableStateOf(false) }
    var focusCommentAtEndRequest by remember(index) { mutableStateOf(0) }
    val passedFocusRequester = remember { FocusRequester() }
    val failedFocusRequester = remember { FocusRequester() }
    val skippedFocusRequester = remember { FocusRequester() }
    val blockedFocusRequester = remember { FocusRequester() }
    val commentToggleFocusRequester = remember { FocusRequester() }
    val commentFieldFocusRequester = remember { FocusRequester() }

    LaunchedEffect(focusCommentAtEndRequest) {
        if (focusCommentAtEndRequest > 0) {
            commentFieldFocusRequester.requestFocus()
        }
    }

    val barColor = when (step.verdict) {
        StepVerdict.NONE -> Color.Transparent
        StepVerdict.PASSED -> SpeqaThemeColors.passedIndicator
        StepVerdict.FAILED -> SpeqaThemeColors.destructive
        StepVerdict.SKIPPED -> SpeqaThemeColors.skippedIndicator
        StepVerdict.BLOCKED -> SpeqaThemeColors.accent
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                if (barColor != Color.Transparent) {
                    drawRect(barColor, topLeft = Offset.Zero, size = Size(2.dp.toPx(), size.height))
                }
            }
            .padding(start = SpeqaLayout.compactGap, top = SpeqaLayout.blockGap, bottom = SpeqaLayout.blockGap),
        horizontalArrangement = Arrangement.spacedBy(SpeqaLayout.compactGap),
    ) {

        // Step number column
        Box(modifier = Modifier.width(SpeqaLayout.stepNumberColumnWidth)) {
            Text(
                text = (index + 1).toString().padStart(2, '0'),
                color = SpeqaThemeColors.mutedForeground,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.6.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(SpeqaLayout.compactGap),
        ) {
            // Action text
            MarkdownText(
                markdown = step.action.ifBlank { SpeqaBundle.message("run.emptyStep") },
                color = SpeqaThemeColors.foreground,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 20.sp,
            )

            // Action attachments
            if (step.actionAttachments.isNotEmpty()) {
                step.actionAttachments.forEach { att ->
                    AttachmentRow(
                        attachment = att,
                        onClick = { onOpenAttachment(att) },
                        onDelete = null,
                    )
                }
            }

            // Expected text with label
            if (step.expected.isNotBlank()) {
                Column(verticalArrangement = Arrangement.spacedBy(SpeqaLayout.itemGap)) {
                    Text(
                        SpeqaBundle.message("label.expected"),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = SpeqaThemeColors.mutedForeground,
                    )
                    MarkdownText(
                        markdown = step.expected,
                        color = SpeqaThemeColors.foreground,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                    )
                }
            }

            // Expected attachments
            if (step.expectedAttachments.isNotEmpty()) {
                step.expectedAttachments.forEach { att ->
                    AttachmentRow(
                        attachment = att,
                        onClick = { onOpenAttachment(att) },
                        onDelete = null,
                    )
                }
            }

            // Verdict chips + comment toggle button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(SpeqaLayout.compactGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                VerdictChip(
                    text = SpeqaBundle.message("run.passed"),
                    selected = step.verdict == StepVerdict.PASSED,
                    selectedBackground = SpeqaThemeColors.verdictPassed,
                    selectedTextColor = SpeqaThemeColors.passedIndicator,
                    onClick = { onVerdictChange(StepVerdict.PASSED) },
                    modifier = Modifier
                        .focusRequester(passedFocusRequester)
                        .focusProperties {
                            next = failedFocusRequester
                            previous = FocusRequester.Default
                        },
                )
                VerdictChip(
                    text = SpeqaBundle.message("run.failed"),
                    selected = step.verdict == StepVerdict.FAILED,
                    selectedBackground = SpeqaThemeColors.verdictFailed,
                    selectedTextColor = SpeqaThemeColors.destructive,
                    onClick = { onVerdictChange(StepVerdict.FAILED) },
                    modifier = Modifier
                        .focusRequester(failedFocusRequester)
                        .focusProperties {
                            previous = passedFocusRequester
                            next = skippedFocusRequester
                        },
                )
                VerdictChip(
                    text = SpeqaBundle.message("run.skipped"),
                    selected = step.verdict == StepVerdict.SKIPPED,
                    selectedBackground = SpeqaThemeColors.verdictSkipped,
                    selectedTextColor = SpeqaThemeColors.skippedIndicator,
                    onClick = { onVerdictChange(StepVerdict.SKIPPED) },
                    modifier = Modifier
                        .focusRequester(skippedFocusRequester)
                        .focusProperties {
                            previous = failedFocusRequester
                            next = blockedFocusRequester
                        },
                )
                VerdictChip(
                    text = SpeqaBundle.message("run.blocked"),
                    selected = step.verdict == StepVerdict.BLOCKED,
                    selectedBackground = SpeqaThemeColors.verdictBlocked,
                    selectedTextColor = SpeqaThemeColors.accent,
                    onClick = { onVerdictChange(StepVerdict.BLOCKED) },
                    modifier = Modifier
                        .focusRequester(blockedFocusRequester)
                        .focusProperties {
                            previous = skippedFocusRequester
                            next = commentToggleFocusRequester
                        },
                )
                val commentIcon = remember {
                    IntelliJIconKey.fromPlatformIcon(AllIcons.General.Balloon, SpeqaLayout::class.java)
                }
                val hasStoredComment = step.comment.isNotBlank()
                val commentTooltip = when {
                    hasStoredComment -> SpeqaBundle.message("run.editComment")
                    showComment -> SpeqaBundle.message("run.hideCommentField")
                    else -> SpeqaBundle.message("run.addComment")
                }
                Box {
                    Tooltip(tooltip = { Text(commentTooltip) }) {
                        SpeqaIconButton(
                            onClick = {
                                showComment = !showComment
                                if (showComment) {
                                    focusCommentAtEndRequest++
                                }
                            },
                            focusable = true,
                            keyboardFocusRingOnly = true,
                            modifier = Modifier
                                .focusRequester(commentToggleFocusRequester)
                                .focusProperties {
                                    previous = blockedFocusRequester
                                    next = if (showComment) commentFieldFocusRequester else FocusRequester.Default
                                },
                        ) {
                            Icon(
                                key = commentIcon,
                                contentDescription = commentTooltip,
                                modifier = Modifier.width(16.dp).height(16.dp),
                                tint = SpeqaThemeColors.foreground,
                            )
                        }
                    }
                    if (hasStoredComment) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = (-1).dp, y = 1.dp)
                                .size(4.dp)
                                .background(
                                    color = SpeqaThemeColors.accent,
                                    shape = RoundedCornerShape(percent = 50),
                                ),
                        )
                    }
                }
            }

            if (showComment) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(SpeqaLayout.itemGap),
                ) {
                    Text(
                        SpeqaBundle.message("run.stepComment"),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = SpeqaThemeColors.mutedForeground,
                    )
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        PlainTextInput(
                            value = step.comment,
                            onValueChange = onCommentChange,
                            placeholder = SpeqaBundle.message("run.addComment"),
                            focusAtEndRequest = focusCommentAtEndRequest,
                            modifier = Modifier
                                .focusRequester(commentFieldFocusRequester)
                                .focusProperties {
                                    previous = commentToggleFocusRequester
                                    next = FocusRequester.Default
                                },
                            singleLine = false,
                            minHeight = 36,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VerdictChip(
    text: String,
    selected: Boolean,
    selectedBackground: Color,
    selectedTextColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val borderColor = if (selected) selectedTextColor.copy(alpha = 0.45f) else SpeqaThemeColors.divider
    Box(
        modifier = modifier
            .background(
                if (selected) selectedBackground else SpeqaThemeColors.chipSurface,
                RoundedCornerShape(SpeqaLayout.actionPillRadius),
            )
            .border(1.dp, borderColor, RoundedCornerShape(SpeqaLayout.actionPillRadius))
            .clickableWithPointer(focusable = true, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            fontSize = 13.sp,
            fontWeight = FontWeight.Normal,
            color = if (selected) selectedTextColor else SpeqaThemeColors.mutedForeground,
        )
    }
}
