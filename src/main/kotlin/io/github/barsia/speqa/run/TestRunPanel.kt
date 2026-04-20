package io.github.barsia.speqa.run

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.focusable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.ClickableText
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withStyle
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import io.github.barsia.speqa.SpeqaBundle
import io.github.barsia.speqa.editor.ScrollSyncController
import io.github.barsia.speqa.editor.ui.AttachmentList
import io.github.barsia.speqa.editor.ui.AttachmentRow
import io.github.barsia.speqa.editor.ui.DateIconLabel
import io.github.barsia.speqa.editor.ui.EditableBodyBlockSection
import io.github.barsia.speqa.editor.ui.FocusContext
import io.github.barsia.speqa.editor.ui.FocusSlot
import io.github.barsia.speqa.editor.ui.FocusTrail
import io.github.barsia.speqa.editor.ui.HeaderAddIconButton
import io.github.barsia.speqa.editor.ui.InlineEditableIdRow
import io.github.barsia.speqa.editor.ui.InlineEditableTitleRow
import io.github.barsia.speqa.editor.ui.LinkList
import io.github.barsia.speqa.editor.ui.LocalFocusContext
import io.github.barsia.speqa.editor.ui.MetadataValueKind
import io.github.barsia.speqa.editor.ui.MarkdownText
import io.github.barsia.speqa.editor.ui.PlainTextInput
import io.github.barsia.speqa.editor.ui.mergeBodyBlocks
import io.github.barsia.speqa.editor.ui.replaceBodyBlocks
import io.github.barsia.speqa.editor.ui.SectionHeaderWithDivider
import io.github.barsia.speqa.editor.ui.SectionLabel
import io.github.barsia.speqa.editor.ui.StepSlot
import io.github.barsia.speqa.editor.ui.StepMetaRow
import io.github.barsia.speqa.editor.ui.ScenarioStepFrame
import io.github.barsia.speqa.editor.ui.SpeqaLayout
import io.github.barsia.speqa.editor.ui.SpeqaTypography
import io.github.barsia.speqa.editor.ui.SpeqaThemeColors
import io.github.barsia.speqa.editor.ui.SpeqaIconButton
import io.github.barsia.speqa.editor.ui.TagCloud
import io.github.barsia.speqa.editor.ui.handOnHover
import io.github.barsia.speqa.editor.ui.focusTrailMotionContract
import io.github.barsia.speqa.editor.ui.rememberTestRunMetadataActions
import io.github.barsia.speqa.editor.ui.SurfaceDivider
import io.github.barsia.speqa.editor.ui.shouldShowFocusTrail
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
    file: VirtualFile,
    scrollSyncController: ScrollSyncController? = null,
    title: String,
    onTitleCommit: (String) -> Unit,
    tags: List<String> = emptyList(),
    allKnownTags: List<String> = emptyList(),
    onTagsChange: (List<String>) -> Unit,
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
    environment: List<String>,
    environmentOptions: List<String>,
    runner: String,
    onEnvironmentChange: (List<String>) -> Unit,
    onRunnerChange: (String) -> Unit,
    onStepActionChange: (Int, String) -> Unit,
    onStepExpectedChange: (Int, String) -> Unit,
    onStepVerdictChange: (Int, StepVerdict) -> Unit,
    onStepCommentChange: (Int, String) -> Unit,
    onStepTicketChange: (Int, List<String>) -> Unit,
    onStepLinkChange: (Int, List<Link>) -> Unit,
    onStepAttachmentsChange: (Int, List<Attachment>) -> Unit,
    priority: Priority?,
    bodyBlocks: List<TestCaseBodyBlock>,
    onBodyBlocksChange: (List<TestCaseBodyBlock>) -> Unit,
    links: List<Link>,
    onLinksChange: (List<Link>) -> Unit,
    attachments: List<Attachment>,
    onAttachmentsChange: (List<Attachment>) -> Unit,
    onOpenAttachment: (Attachment) -> Unit,
    comment: String,
    onCommentChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    val stepActionFocusRequesters = remember(stepResults.size) {
        List(stepResults.size) { FocusRequester() }
    }
    val runCommentFocusRequester = remember { FocusRequester() }
    val totalSteps = stepResults.size
    val (tagClick, tagTooltip, tagMenu) = rememberTestRunMetadataActions(
        project = project,
        kind = MetadataValueKind.TAG,
        onRemove = { value -> onTagsChange(tags.filter { it != value }) },
    )
    val (envClick, envTooltip, envMenu) = rememberTestRunMetadataActions(
        project = project,
        kind = MetadataValueKind.ENVIRONMENT,
        onRemove = { value -> onEnvironmentChange(environment.filter { it != value }) },
    )
    val focusSinkRequester = remember { FocusRequester() }
    val focusContext = remember { FocusContext() }
    val headerAddLinkRequester = remember { FocusRequester() }
    val headerAddAttachmentRequester = remember { FocusRequester() }
    var headerTitleBounds by remember { mutableStateOf<Rect?>(null) }
    var viewportCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

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

    CompositionLocalProvider(LocalFocusContext provides focusContext) {
        val focusTrailMotion = remember { focusTrailMotionContract() }
        val density = LocalDensity.current
        val focusTrailSlidePx = remember(density, focusTrailMotion) {
            with(density) { focusTrailMotion.slideOffsetDp.dp.roundToPx() }
        }
        val showFocusTrail by remember(headerTitleBounds, viewportCoordinates) {
            androidx.compose.runtime.derivedStateOf {
                shouldShowFocusTrail(
                    titleBounds = headerTitleBounds,
                    viewportBounds = viewportCoordinates?.takeIf { it.isAttached }?.boundsInWindow(),
                )
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
                    .onGloballyPositioned { viewportCoordinates = it }
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Final)
                                if (event.type == PointerEventType.Scroll) {
                                    event.changes.forEach { it.consume() }
                                }
                            }
                        }
                    }
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
                InlineEditableTitleRow(
                    title = title.ifBlank { SpeqaBundle.message("label.untitledTestCase") },
                    onTitleCommit = onTitleCommit,
                    modifier = Modifier.onGloballyPositioned { headerTitleBounds = it.boundsInWindow() },
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(SpeqaLayout.sectionGap),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FlowRow(
                        modifier = Modifier.weight(1f),
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
                    }
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(SpeqaLayout.compactGap),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SectionLabel(SpeqaBundle.message("run.runner"))
                        PlainTextInput(
                            value = runner,
                            onValueChange = onRunnerChange,
                            placeholder = SpeqaBundle.message("placeholder.runner"),
                            onCommitRequest = onRunnerChange,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(SpeqaLayout.blockGap),
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(SpeqaLayout.blockGap),
                    ) {
                        TagCloud(
                            tags = environment,
                            allKnownTags = environmentOptions,
                            onTagsChange = onEnvironmentChange,
                            label = SpeqaBundle.message("run.environment"),
                            addItemLabel = SpeqaBundle.message("environment.add"),
                            onChipClick = envClick,
                            chipTooltip = envTooltip,
                            chipContextActions = envMenu,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(SpeqaLayout.blockGap),
                    ) {
                        TagCloud(
                            tags = tags,
                            allKnownTags = allKnownTags,
                            onTagsChange = onTagsChange,
                            label = SpeqaBundle.message("label.tags"),
                            coloredChips = true,
                            onChipClick = tagClick,
                            chipTooltip = tagTooltip,
                            chipContextActions = tagMenu,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(SpeqaLayout.blockGap),
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(SpeqaLayout.tightGap),
                    ) {
                        SectionHeaderWithDivider(
                            title = SpeqaBundle.message("label.links"),
                            actions = {
                                HeaderAddIconButton(
                                    tooltip = SpeqaBundle.message("tooltip.addLink"),
                                    onClick = {
                                        com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                                            val newLink = io.github.barsia.speqa.editor.ui.AddEditLinkDialog.show(project)
                                            if (newLink != null) {
                                                onLinksChange(links + newLink)
                                            }
                                        }
                                    },
                                    addRequester = headerAddLinkRequester,
                                )
                            },
                        )
                        LinkList(
                            links = links,
                            onLinksChange = onLinksChange,
                            project = project,
                            showAddButton = false,
                            externalAddRequester = headerAddLinkRequester,
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(SpeqaLayout.tightGap),
                    ) {
                        SectionHeaderWithDivider(
                            title = SpeqaBundle.message("label.attachments"),
                            actions = {
                                HeaderAddIconButton(
                                    tooltip = SpeqaBundle.message("tooltip.addAttachment"),
                                    onClick = {
                                        com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                                            val descriptor = com.intellij.openapi.fileChooser.FileChooserDescriptorFactory.createAllButJarContentsDescriptor()
                                            com.intellij.openapi.fileChooser.FileChooser.chooseFiles(descriptor, project, null) { chosen ->
                                                if (chosen.isNotEmpty()) {
                                                    val newAttachment = runWriteAction<Attachment?> {
                                                        io.github.barsia.speqa.editor.AttachmentSupport.copyFileToAttachments(project, file, chosen.first())
                                                    }
                                                    if (newAttachment != null) {
                                                        onAttachmentsChange(attachments + newAttachment)
                                                    }
                                                }
                                            }
                                        }
                                    },
                                    addRequester = headerAddAttachmentRequester,
                                )
                            },
                        )
                        AttachmentList(
                            attachments = attachments,
                            project = project,
                            tcFile = file,
                            onAttachmentsChange = onAttachmentsChange,
                            onOpenFile = onOpenAttachment,
                            showAddButton = false,
                            externalAddRequester = headerAddAttachmentRequester,
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.padding(horizontal = SpeqaLayout.contentInset),
                verticalArrangement = Arrangement.spacedBy(SpeqaLayout.blockGap),
            ) {
                EditableBodyBlockSection(
                    title = SpeqaBundle.message("label.description"),
                    text = mergeBodyBlocks(bodyBlocks, DescriptionBlock::class.java),
                    emptyLabel = SpeqaBundle.message("label.noDescription"),
                    onCommit = { newText ->
                        onBodyBlocksChange(
                            replaceBodyBlocks(bodyBlocks, DescriptionBlock::class.java) {
                                DescriptionBlock(newText)
                            },
                        )
                    },
                )
                EditableBodyBlockSection(
                    title = SpeqaBundle.message("label.preconditions"),
                    text = mergeBodyBlocks(bodyBlocks, PreconditionsBlock::class.java),
                    emptyLabel = SpeqaBundle.message("label.noPreconditions"),
                    onCommit = { newText ->
                        val markerStyle = bodyBlocks.filterIsInstance<PreconditionsBlock>().firstOrNull()?.markerStyle
                            ?: io.github.barsia.speqa.model.PreconditionsMarkerStyle.PRECONDITIONS
                        onBodyBlocksChange(
                            replaceBodyBlocks(bodyBlocks, PreconditionsBlock::class.java) {
                                PreconditionsBlock(markerStyle, newText)
                            },
                        )
                    },
                )
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
                                project = project,
                                file = file,
                                actionFocusRequester = stepActionFocusRequesters[index],
                                nextActionFocusRequester = stepActionFocusRequesters.getOrNull(index + 1),
                                fallbackExpectedExitFocusRequester = runCommentFocusRequester,
                                onActionChange = { action -> onStepActionChange(index, action) },
                                onExpectedChange = { expected -> onStepExpectedChange(index, expected) },
                                onVerdictChange = { verdict -> onStepVerdictChange(index, verdict) },
                                onCommentChange = { comment -> onStepCommentChange(index, comment) },
                                onTicketChange = { ticket -> onStepTicketChange(index, ticket) },
                                onLinkChange = { links -> onStepLinkChange(index, links) },
                                onAttachmentsChange = { attachments -> onStepAttachmentsChange(index, attachments) },
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
                    modifier = Modifier.focusRequester(runCommentFocusRequester),
                )
            }
            }
            AnimatedVisibility(
                visible = showFocusTrail,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth(),
                enter = fadeIn(animationSpec = tween(focusTrailMotion.durationMillis)) +
                    slideInVertically(
                        animationSpec = tween(focusTrailMotion.durationMillis),
                        initialOffsetY = { -focusTrailSlidePx },
                    ),
                exit = fadeOut(animationSpec = tween(focusTrailMotion.durationMillis)) +
                    slideOutVertically(
                        animationSpec = tween(focusTrailMotion.durationMillis),
                        targetOffsetY = { -focusTrailSlidePx },
                    ),
            ) {
                FocusTrail(
                    titleText = runStickyTitle(runId = runId, title = title),
                    progressText = runStickyProgressLabel(
                        completedSteps = completedRunSteps(stepResults),
                        totalSteps = stepResults.size,
                    ),
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
    project: Project,
    file: VirtualFile,
    actionFocusRequester: FocusRequester,
    nextActionFocusRequester: FocusRequester?,
    fallbackExpectedExitFocusRequester: FocusRequester,
    onActionChange: (String) -> Unit,
    onExpectedChange: (String) -> Unit,
    onVerdictChange: (StepVerdict) -> Unit,
    onCommentChange: (String) -> Unit,
    onTicketChange: (List<String>) -> Unit,
    onLinkChange: (List<Link>) -> Unit,
    onAttachmentsChange: (List<Attachment>) -> Unit,
    onOpenAttachment: (Attachment) -> Unit,
) {
    var showComment by remember(index) { mutableStateOf(false) }
    var focusCommentAtEndRequest by remember(index) { mutableStateOf(0) }
    val ticketAddRequester = remember { FocusRequester() }
    val linkPrimaryRequesters = remember(step.links.size) { List(step.links.size) { FocusRequester() } }
    val linkAddRequester = remember { FocusRequester() }
    val attachmentPrimaryRequesters = remember(step.attachments.size) {
        List(step.attachments.size) { FocusRequester() }
    }
    val attachmentAddRequester = remember { FocusRequester() }
    val passedFocusRequester = remember { FocusRequester() }
    val failedFocusRequester = remember { FocusRequester() }
    val skippedFocusRequester = remember { FocusRequester() }
    val blockedFocusRequester = remember { FocusRequester() }
    val commentToggleFocusRequester = remember { FocusRequester() }
    val commentFieldFocusRequester = remember { FocusRequester() }
    val expectedFieldFocusRequester = remember { FocusRequester() }
    var showExpectedEditor by remember(index) { mutableStateOf(step.expected.isNotBlank()) }
    var focusExpectedAtEndRequest by remember(index) { mutableStateOf(0) }
    val focusContext = LocalFocusContext.current

    LaunchedEffect(step.expected) {
        if (step.expected.isNotBlank()) {
            showExpectedEditor = true
        }
    }

    LaunchedEffect(focusCommentAtEndRequest) {
        if (focusCommentAtEndRequest > 0) {
            commentFieldFocusRequester.requestFocus()
        }
    }
    LaunchedEffect(focusExpectedAtEndRequest) {
        if (focusExpectedAtEndRequest > 0) {
            expectedFieldFocusRequester.requestFocus()
        }
    }

    val barColor = when (step.verdict) {
        StepVerdict.NONE -> Color.Transparent
        StepVerdict.PASSED -> SpeqaThemeColors.passedIndicator
        StepVerdict.FAILED -> SpeqaThemeColors.destructive
        StepVerdict.SKIPPED -> SpeqaThemeColors.skippedIndicator
        StepVerdict.BLOCKED -> SpeqaThemeColors.accent
    }

    ScenarioStepFrame(
        modifier = Modifier
            .drawBehind {
                if (barColor != Color.Transparent) {
                    drawRect(barColor, topLeft = Offset.Zero, size = Size(2.dp.toPx(), size.height))
                }
            }
            .padding(start = SpeqaLayout.compactGap, top = SpeqaLayout.blockGap, bottom = SpeqaLayout.blockGap),
        gutterModifier = Modifier.width(SpeqaLayout.stepNumberColumnWidth).padding(top = 1.dp),
        gutter = {
            Text(
                text = (index + 1).toString().padStart(2, '0'),
                color = SpeqaThemeColors.mutedForeground,
                fontSize = SpeqaTypography.numericFontSize,
                fontWeight = SpeqaTypography.numericWeight,
                letterSpacing = SpeqaTypography.numericTracking,
            )
        },
        actionContent = {
            PlainTextInput(
                value = step.action,
                onValueChange = onActionChange,
                placeholder = SpeqaBundle.message("placeholder.action"),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(actionFocusRequester)
                    .onFocusChanged { if (it.isFocused) focusContext.current = FocusSlot(index, StepSlot.ACTION) },
                singleLine = false,
                onPlainEnter = {
                    showExpectedEditor = true
                    focusExpectedAtEndRequest++
                    true
                },
            )
        },
        expectedContent = {
            if (showExpectedEditor || step.expected.isNotBlank()) {
                PlainTextInput(
                    value = step.expected,
                    onValueChange = onExpectedChange,
                    placeholder = SpeqaBundle.message("placeholder.setExpected"),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(expectedFieldFocusRequester)
                        .onFocusChanged { if (it.isFocused) focusContext.current = FocusSlot(index, StepSlot.EXPECTED) },
                    singleLine = false,
                    focusAtEndRequest = focusExpectedAtEndRequest,
                    onPlainEnter = {
                        when (runExpectedEnterTarget(hasNextStep = nextActionFocusRequester != null)) {
                            RunExpectedEnterTarget.NEXT_STEP_ACTION -> nextActionFocusRequester?.requestFocus()
                            RunExpectedEnterTarget.RUN_COMMENT -> fallbackExpectedExitFocusRequester.requestFocus()
                        }
                        true
                    },
                )
            } else {
                Text(
                    text = SpeqaBundle.message("placeholder.setExpected"),
                    color = SpeqaThemeColors.mutedForeground.copy(alpha = 0.6f),
                    fontSize = SpeqaTypography.placeholderFontSize,
                    fontWeight = FontWeight.Normal,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickableWithPointer(
                            onClick = {
                                showExpectedEditor = true
                                focusExpectedAtEndRequest++
                            },
                        )
                        .onFocusChanged { if (it.isFocused) focusContext.current = FocusSlot(index, StepSlot.EXPECTED) }
                        .focusable(),
                )
            }
        },
        metaContent = { layout ->
            StepMetaRow(
                stepIndex = index,
                tickets = step.tickets,
                links = step.links,
                attachments = step.attachments,
                project = project,
                tcFile = file,
                onTicketsChange = onTicketChange,
                onLinksChange = onLinkChange,
                onAttachmentsChange = onAttachmentsChange,
                onOpenFile = onOpenAttachment,
                attachmentRevision = 0L,
                ticketAddRequester = ticketAddRequester,
                linkPrimaryRequesters = linkPrimaryRequesters,
                linkAddRequester = linkAddRequester,
                attachmentPrimaryRequesters = attachmentPrimaryRequesters,
                attachmentAddRequester = attachmentAddRequester,
                narrow = layout.narrow,
            )
        },
        footerContent = {
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
                            previous = attachmentAddRequester
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
                    val commentHoverSource = remember { MutableInteractionSource() }
                    val isCommentHovered by commentHoverSource.collectIsHoveredAsState()
                    Tooltip(tooltip = { Text(commentTooltip) }) {
                        SpeqaIconButton(
                            onClick = {
                                showComment = !showComment
                                if (showComment) focusCommentAtEndRequest++
                            },
                            focusable = true,
                            keyboardFocusRingOnly = true,
                            modifier = Modifier
                                .hoverable(commentHoverSource)
                                .focusRequester(commentToggleFocusRequester)
                                .focusProperties {
                                    previous = blockedFocusRequester
                                    next = if (showComment) commentFieldFocusRequester else FocusRequester.Default
                                },
                        ) {
                            val commentTint = if (isCommentHovered || showComment || hasStoredComment) SpeqaThemeColors.foreground else SpeqaThemeColors.mutedForeground
                            Icon(
                                key = commentIcon,
                                contentDescription = commentTooltip,
                                modifier = Modifier.width(16.dp).height(16.dp),
                                tint = commentTint,
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
                    verticalArrangement = Arrangement.spacedBy(SpeqaLayout.tightGap),
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
        },
    )
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
    val hoverSource = remember { MutableInteractionSource() }
    val isHovered by hoverSource.collectIsHoveredAsState()
    val borderColor = if (selected) selectedTextColor.copy(alpha = 0.45f) else SpeqaThemeColors.divider
    Box(
        modifier = modifier
            .background(
                if (selected) selectedBackground else SpeqaThemeColors.chipSurface,
                RoundedCornerShape(SpeqaLayout.actionPillRadius),
            )
            .border(1.dp, borderColor, RoundedCornerShape(SpeqaLayout.actionPillRadius))
            .hoverable(hoverSource)
            .clickableWithPointer(focusable = true, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        val textColor = when {
            selected -> selectedTextColor
            isHovered -> SpeqaThemeColors.foreground
            else -> SpeqaThemeColors.mutedForeground
        }
        Text(
            text,
            fontSize = 13.sp,
            fontWeight = FontWeight.Normal,
            color = textColor,
        )
    }
}

internal data class RunHeaderTagsLayoutContract(
    val showStandaloneTagStrip: Boolean,
    val showCompactAddAffordance: Boolean,
)

internal fun runHeaderTagsLayoutContract(tags: List<String>): RunHeaderTagsLayoutContract {
    return RunHeaderTagsLayoutContract(
        showStandaloneTagStrip = false,
        showCompactAddAffordance = true,
    )
}

internal enum class RunExpectedEnterTarget {
    NEXT_STEP_ACTION,
    RUN_COMMENT,
}

internal fun runExpectedEnterTarget(hasNextStep: Boolean): RunExpectedEnterTarget {
    return if (hasNextStep) RunExpectedEnterTarget.NEXT_STEP_ACTION else RunExpectedEnterTarget.RUN_COMMENT
}

internal data class RunHeaderLayoutContract(
    val runnerInlineWithProgress: Boolean,
    val environmentTagsTwoColumns: Boolean,
    val referencesMatchTestCaseHeaderPattern: Boolean,
    val titleUsesInlineEditablePattern: Boolean,
    val metadataUsesSharedSuggestions: Boolean,
    val tagsUseTestCaseEmptyState: Boolean,
    val headerPairsShareColumnGrid: Boolean,
    val usesSharedStickyTrail: Boolean,
)

internal fun runHeaderLayoutContract(): RunHeaderLayoutContract {
    return RunHeaderLayoutContract(
        runnerInlineWithProgress = true,
        environmentTagsTwoColumns = true,
        referencesMatchTestCaseHeaderPattern = true,
        titleUsesInlineEditablePattern = true,
        metadataUsesSharedSuggestions = true,
        tagsUseTestCaseEmptyState = true,
        headerPairsShareColumnGrid = true,
        usesSharedStickyTrail = true,
    )
}

internal fun completedRunSteps(stepResults: List<StepResult>): Int {
    return stepResults.count { it.verdict != StepVerdict.NONE }
}

internal fun runStickyProgressLabel(completedSteps: Int, totalSteps: Int): String? {
    if (totalSteps <= 0) return null
    return SpeqaBundle.message("run.progress", completedSteps, totalSteps)
}

internal fun runStickyTitle(runId: Int?, title: String): String {
    val resolvedTitle = title.ifBlank { SpeqaBundle.message("label.untitledTestCase") }
    val idPrefix = SpeqaBundle.message("label.idPrefix.tr")
    return runId?.let { "$idPrefix$it · $resolvedTitle" } ?: resolvedTitle
}

internal data class RunScrollConsumptionContract(
    val consumeScrollAtViewportBoundary: Boolean,
)

internal fun runScrollConsumptionContract(): RunScrollConsumptionContract {
    return RunScrollConsumptionContract(
        consumeScrollAtViewportBoundary = true,
    )
}

internal data class RunStepCommentLayoutContract(
    val usesRelaxedLabelGap: Boolean,
)

internal fun runStepCommentLayoutContract(): RunStepCommentLayoutContract {
    return RunStepCommentLayoutContract(
        usesRelaxedLabelGap = true,
    )
}

internal data class RunStepEditabilityContract(
    val actionEditable: Boolean,
    val expectedEditable: Boolean,
    val bodyBlocksEditable: Boolean,
)

internal fun runStepEditabilityContract(): RunStepEditabilityContract {
    return RunStepEditabilityContract(
        actionEditable = true,
        expectedEditable = true,
        bodyBlocksEditable = true,
    )
}

internal data class RunTopLevelReferencesVisualContract(
    val iconOnlyAddAffordances: Boolean,
)

internal fun runTopLevelReferencesVisualContract(): RunTopLevelReferencesVisualContract {
    return RunTopLevelReferencesVisualContract(
        iconOnlyAddAffordances = true,
    )
}
