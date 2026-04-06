package io.github.barsia.speqa.editor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.focus.focusTarget
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.hoverable

import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.intellij.icons.AllIcons
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import io.github.barsia.speqa.SpeqaBundle
import io.github.barsia.speqa.editor.AttachmentSupport
import io.github.barsia.speqa.editor.ScrollSyncController
import io.github.barsia.speqa.editor.TestCaseHeaderMeta
import io.github.barsia.speqa.registry.IdType
import io.github.barsia.speqa.model.DescriptionBlock
import io.github.barsia.speqa.model.PreconditionsBlock
import io.github.barsia.speqa.model.Priority
import io.github.barsia.speqa.model.Status
import io.github.barsia.speqa.model.TestCase
import io.github.barsia.speqa.model.TestCaseBodyBlock
import io.github.barsia.speqa.parser.PatchOperation
import org.jetbrains.jewel.bridge.icon.fromPlatformIcon
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.ListComboBox
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.Tooltip
import org.jetbrains.jewel.ui.icon.IntelliJIconKey

@OptIn(ExperimentalFoundationApi::class, ExperimentalJewelApi::class)
@Composable
internal fun TestCasePreview(
    testCase: TestCase,
    headerMeta: TestCaseHeaderMeta,
    project: Project,
    file: VirtualFile,
    nextFreeTestCaseId: Int,
    isIdDuplicate: Boolean,
    isIdEditing: Boolean,
    onIdEditingChange: (Boolean) -> Unit,
    onRun: () -> Unit,
    onTitleCommit: (String) -> Unit,
    onIdAssign: (Int) -> Unit,
    allKnownTags: List<String> = emptyList(),
    allKnownEnvironments: List<String> = emptyList(),
    onPriorityChange: ((Priority) -> Unit)?,
    onStatusChange: ((Status) -> Unit)?,
    onPatch: ((TestCase, PatchOperation) -> Unit)?,
    scrollSyncController: ScrollSyncController? = null,
    attachmentRevision: Long = 0L,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    var stepDragActive by remember { mutableStateOf(false) }
    var focusRequestStepIndex by remember { mutableStateOf(-1) }
    val focusSinkRequester = remember { FocusRequester() }

    // Scroll sync: editor → compose (collect fraction from flow)
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

    // Scroll sync: compose → editor (report fraction on scroll)
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
            .background(SpeqaThemeColors.surface)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = true)
                    val up = waitForUpOrCancellation()
                    if (up != null && !up.isConsumed) {
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
                .fillMaxWidth()
                .verticalScroll(scrollState, enabled = !stepDragActive)
                .padding(start = SpeqaLayout.pagePadding, end = SpeqaLayout.pagePadding, top = SpeqaLayout.compactGap, bottom = SpeqaLayout.pagePadding),
            verticalArrangement = Arrangement.spacedBy(SpeqaLayout.blockGap),
        ) {
            PreviewHeader(
            testCase = testCase,
            headerMeta = headerMeta,
            project = project,
            nextFreeTestCaseId = nextFreeTestCaseId,
            isIdDuplicate = isIdDuplicate,
            isIdEditing = isIdEditing,
            onIdEditingChange = onIdEditingChange,
            onRun = onRun,
            onTitleCommit = onTitleCommit,
            onIdAssign = onIdAssign,
            allKnownTags = allKnownTags,
            allKnownEnvironments = allKnownEnvironments,
            onPriorityChange = onPriorityChange,
            onStatusChange = onStatusChange,
            onPatch = onPatch,
        )

        Column(
            modifier = Modifier.padding(horizontal = SpeqaLayout.contentInset),
            verticalArrangement = Arrangement.spacedBy(SpeqaLayout.blockGap),
        ) {
            EditablePreviewSection(
                title = SpeqaBundle.message("label.description"),
                text = mergeBlocks(testCase.bodyBlocks, DescriptionBlock::class.java),
                emptyLabel = SpeqaBundle.message("label.noDescription"),
                onCommit = onPatch?.let { patch ->
                    { newText ->
                        val updated = replaceBodyBlocks(testCase, DescriptionBlock::class.java) { DescriptionBlock(newText) }
                        patch(updated, PatchOperation.SetDescription(newText))
                    }
                },
            )

            EditablePreviewSection(
                title = SpeqaBundle.message("label.preconditions"),
                text = mergeBlocks(testCase.bodyBlocks, PreconditionsBlock::class.java),
                emptyLabel = SpeqaBundle.message("label.noPreconditions"),
                onCommit = onPatch?.let { patch ->
                    { newText ->
                        val markerStyle = testCase.bodyBlocks.filterIsInstance<PreconditionsBlock>().firstOrNull()?.markerStyle
                            ?: io.github.barsia.speqa.model.PreconditionsMarkerStyle.PRECONDITIONS
                        val updated = replaceBodyBlocks(testCase, PreconditionsBlock::class.java) {
                            PreconditionsBlock(markerStyle, newText)
                        }
                        patch(updated, PatchOperation.SetPreconditions(markerStyle, newText))
                    }
                },
            )

            onPatch?.let { patch ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(SpeqaLayout.blockGap),
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(SpeqaLayout.itemGap),
                    ) {
                        SectionHeaderWithDivider(SpeqaBundle.message("label.attachments"))
                            AttachmentList(
                                attachments = testCase.attachments,
                                project = project,
                                tcFile = file,
                                onAttachmentsChange = { newAttachments ->
                                    patch(testCase.copy(attachments = newAttachments), PatchOperation.SetAttachments(newAttachments))
                                },
                                onOpenFile = { attachment ->
                                    AttachmentSupport.resolveFile(project, file, attachment)?.let { vf ->
                                        FileEditorManager.getInstance(project).openFile(vf, true)
                                    }
                                },
                                attachmentRevision = attachmentRevision,
                            )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(SpeqaLayout.itemGap),
                    ) {
                        SectionHeaderWithDivider(SpeqaBundle.message("label.links"))
                        LinkList(
                            links = testCase.links,
                            onLinksChange = { newLinks ->
                                patch(testCase.copy(links = newLinks), PatchOperation.SetLinks(newLinks))
                            },
                            project = project,
                        )
                    }
                }

                StepsSection(
                    testCase = testCase,
                    onPatch = patch,
                    project = project,
                    tcFile = file,
                    focusRequestStepIndex = focusRequestStepIndex,
                    onFocusRequestStepIndexChange = { focusRequestStepIndex = it },
                    onStepDragActiveChange = { stepDragActive = it },
                    attachmentRevision = attachmentRevision,
                )
            } ?: PreviewStepsSection(testCase = testCase)
        }
        }
    }
}

private fun <T : TestCaseBodyBlock> mergeBlocks(
    blocks: List<TestCaseBodyBlock>,
    type: Class<T>,
): String {
    return blocks
        .filter { type.isInstance(it) }
        .map { it.markdown.trim() }
        .filter { it.isNotBlank() }
        .joinToString("\n\n")
}

private fun <T : TestCaseBodyBlock> replaceBodyBlocks(
    testCase: TestCase,
    type: Class<T>,
    factory: () -> TestCaseBodyBlock,
): TestCase {
    val hasExisting = testCase.bodyBlocks.any { type.isInstance(it) }
    val newBlocks = if (hasExisting) {
        var replaced = false
        testCase.bodyBlocks.mapNotNull {
            if (type.isInstance(it)) {
                if (!replaced) { replaced = true; factory() } else null
            } else it
        }
    } else {
        testCase.bodyBlocks + factory()
    }
    return testCase.copy(bodyBlocks = canonicalBlockOrder(newBlocks))
}

private fun canonicalBlockOrder(blocks: List<TestCaseBodyBlock>): List<TestCaseBodyBlock> {
    return blocks.filterIsInstance<DescriptionBlock>() + blocks.filterIsInstance<PreconditionsBlock>()
}

@Composable
private fun EditablePreviewSection(
    title: String,
    text: String,
    emptyLabel: String,
    onCommit: ((String) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(SpeqaLayout.tightGap),
    ) {
        SectionLabel(title)
        PlainTextInput(
            value = text,
            onValueChange = { onCommit?.invoke(it) },
            readOnly = onCommit == null,
            placeholder = emptyLabel,
            singleLine = false,
            minHeight = 60,
        )
    }
}

@OptIn(ExperimentalJewelApi::class)
@Composable
private fun PreviewHeader(
    testCase: TestCase,
    headerMeta: TestCaseHeaderMeta,
    project: Project,
    nextFreeTestCaseId: Int,
    isIdDuplicate: Boolean,
    isIdEditing: Boolean,
    onIdEditingChange: (Boolean) -> Unit,
    onRun: () -> Unit,
    onTitleCommit: (String) -> Unit,
    onIdAssign: (Int) -> Unit,
    allKnownTags: List<String>,
    allKnownEnvironments: List<String>,
    onPriorityChange: ((Priority) -> Unit)?,
    onStatusChange: ((Status) -> Unit)?,
    onPatch: ((TestCase, PatchOperation) -> Unit)?,
) {
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
        HeaderUtilityRow(
            testCase = testCase,
            nextFreeTestCaseId = nextFreeTestCaseId,
            isIdDuplicate = isIdDuplicate,
            isIdEditing = isIdEditing,
            onIdEditingChange = onIdEditingChange,
            onIdAssign = onIdAssign,
            headerMeta = headerMeta,
            onRun = onRun,
        )
        Box(modifier = Modifier.fillMaxWidth()) {
            InlineEditableTitleRow(
                title = testCase.title.ifBlank { SpeqaBundle.message("label.untitledTestCase") },
                onTitleCommit = onTitleCommit,
            )
        }
        SurfaceDivider()
        Box(modifier = Modifier.fillMaxWidth()) {
            AdaptiveMetadataGrid(
                testCase = testCase,
                allKnownTags = allKnownTags,
                allKnownEnvironments = allKnownEnvironments,
                project = project,
                onPriorityChange = onPriorityChange,
                onStatusChange = onStatusChange,
                onPatch = onPatch,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalJewelApi::class)
@Composable
private fun HeaderUtilityRow(
    testCase: TestCase,
    nextFreeTestCaseId: Int,
    isIdDuplicate: Boolean,
    isIdEditing: Boolean,
    onIdEditingChange: (Boolean) -> Unit,
    onIdAssign: (Int) -> Unit,
    headerMeta: TestCaseHeaderMeta,
    onRun: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(SpeqaLayout.compactGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        InlineEditableIdRow(
            id = testCase.id,
            idType = IdType.TEST_CASE,
            nextFreeId = nextFreeTestCaseId,
            isDuplicate = isIdDuplicate,
            isEditing = isIdEditing,
            onEditingChange = onIdEditingChange,
            onIdAssign = onIdAssign,
        )
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(SpeqaLayout.compactGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DateIconLabel(
                iconKey = IntelliJIconKey("/icons/calendarCreated.svg", "/icons/calendarCreated.svg", iconClass = SpeqaLayout::class.java),
                label = SpeqaBundle.message("preview.created"),
                dateLabel = headerMeta.createdLabel,
            )
            DateIconLabel(
                iconKey = IntelliJIconKey("/icons/calendarUpdated.svg", "/icons/calendarUpdated.svg", iconClass = SpeqaLayout::class.java),
                label = SpeqaBundle.message("preview.updated"),
                dateLabel = headerMeta.updatedLabel,
            )
        }
        val playIcon = IntelliJIconKey.fromPlatformIcon(AllIcons.Actions.Execute, TestCase::class.java)
        Tooltip(
            tooltip = {
                Text(SpeqaBundle.message("tooltip.startTestRun"))
            },
        ) {
            SpeqaIconButton(onClick = onRun, focusable = true) {
                Icon(
                    key = playIcon,
                    contentDescription = SpeqaBundle.message("tooltip.startTestRun"),
                )
            }
        }
    }
}

@OptIn(ExperimentalJewelApi::class, ExperimentalLayoutApi::class)
@Composable
private fun AdaptiveMetadataGrid(
    testCase: TestCase,
    allKnownTags: List<String>,
    allKnownEnvironments: List<String>,
    project: Project,
    onPriorityChange: ((Priority) -> Unit)?,
    onStatusChange: ((Status) -> Unit)?,
    onPatch: ((TestCase, PatchOperation) -> Unit)?,
) {
    val minCellWidth = 140.dp

    val priorityCell: @Composable (Modifier) -> Unit = { mod ->
        PreviewMetadataCell(modifier = mod, label = SpeqaBundle.message("label.priority")) {
            val priority = testCase.priority
            if (priority != null && onPriorityChange != null) {
                ListComboBox(
                    items = Priority.entries.map { it.label.replaceFirstChar(Char::uppercase) },
                    selectedIndex = Priority.entries.indexOf(priority),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = SpeqaLayout.controlHeight)
                        .handOnHover(),
                    onSelectedItemChange = { onPriorityChange(Priority.entries[it]) },
                )
            } else {
                SingleLineMetadataValue(priority?.label?.replaceFirstChar(Char::uppercase) ?: SpeqaBundle.message("label.notSet"))
            }
        }
    }

    val statusCell: @Composable (Modifier) -> Unit = { mod ->
        PreviewMetadataCell(modifier = mod, label = SpeqaBundle.message("label.status")) {
            val status = testCase.status
            if (status != null && onStatusChange != null) {
                ListComboBox(
                    items = Status.entries.map { it.label.replaceFirstChar(Char::uppercase) },
                    selectedIndex = Status.entries.indexOf(status),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = SpeqaLayout.controlHeight)
                        .handOnHover(),
                    onSelectedItemChange = { onStatusChange(Status.entries[it]) },
                )
            } else {
                SingleLineMetadataValue(status?.label?.replaceFirstChar(Char::uppercase) ?: SpeqaBundle.message("label.notSet"))
            }
        }
    }

    val envCell: @Composable (Modifier) -> Unit = { mod ->
        val (envClick, envTooltip, envMenu) = rememberTestCaseMetadataActions(
            project = project,
            kind = MetadataValueKind.ENVIRONMENT,
            onRemove = onPatch?.let { patch ->
                { value ->
                    val updated = testCase.environment.orEmpty().filter { it != value }
                    patch(
                        testCase.copy(environment = updated.ifEmpty { null }),
                        PatchOperation.SetFrontmatterList("environment", updated.ifEmpty { null }),
                    )
                }
            },
        )
        TagCloud(
            tags = testCase.environment.orEmpty(),
            allKnownTags = allKnownEnvironments,
            onTagsChange = onPatch?.let { patch ->
                { newItems ->
                    patch(
                        testCase.copy(environment = newItems.ifEmpty { null }),
                        PatchOperation.SetFrontmatterList("environment", newItems.ifEmpty { null }),
                    )
                }
            },
            label = SpeqaBundle.message("label.environment"),
            onChipClick = envClick,
            chipTooltip = envTooltip,
            chipContextActions = envMenu,
            addItemLabel = SpeqaBundle.message("environment.add"),
            modifier = mod,
        )
    }

    val tagsCell: @Composable (Modifier) -> Unit = { mod ->
        val (tagClick, tagTooltip, tagMenu) = rememberTestCaseMetadataActions(
            project = project,
            kind = MetadataValueKind.TAG,
            onRemove = onPatch?.let { patch ->
                { value ->
                    val updated = testCase.tags.orEmpty().filter { it != value }
                    patch(
                        testCase.copy(tags = updated.ifEmpty { null }),
                        PatchOperation.SetFrontmatterList("tags", updated.ifEmpty { null }),
                    )
                }
            },
        )
        TagCloud(
            tags = testCase.tags.orEmpty(),
            allKnownTags = allKnownTags,
            onTagsChange = onPatch?.let { patch ->
                { newItems ->
                    patch(
                        testCase.copy(tags = newItems.ifEmpty { null }),
                        PatchOperation.SetFrontmatterList("tags", newItems.ifEmpty { null }),
                    )
                }
            },
            label = SpeqaBundle.message("label.tags"),
            coloredChips = true,
            onChipClick = tagClick,
            chipTooltip = tagTooltip,
            chipContextActions = tagMenu,
            modifier = mod,
        )
    }

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val gap = SpeqaLayout.blockGap
        val availableWidth = maxWidth
        val fitsAll4 = availableWidth >= minCellWidth * 4 + gap * 3
        val fitsPair = availableWidth >= minCellWidth * 2 + gap

        if (fitsAll4) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(gap),
            ) {
                val cellMod = Modifier.weight(1f)
                priorityCell(cellMod)
                statusCell(cellMod)
                envCell(cellMod)
                tagsCell(cellMod)
            }
        } else if (fitsPair) {
            Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    priorityCell(Modifier.weight(1f))
                    statusCell(Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    envCell(Modifier.weight(1f))
                    tagsCell(Modifier.weight(1f))
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                priorityCell(Modifier.fillMaxWidth())
                statusCell(Modifier.fillMaxWidth())
                envCell(Modifier.fillMaxWidth())
                tagsCell(Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun PreviewBodyTextOrPlaceholder(
    text: String,
    emptyLabel: String,
) {
    if (text.isBlank()) {
        EmptyPreviewValue(emptyLabel)
    } else {
        MarkdownText(
            markdown = text,
            color = SpeqaThemeColors.foreground,
            fontSize = 14.sp,
            lineHeight = 20.sp,
        )
    }
}

@Composable
private fun PreviewStepsSection(testCase: TestCase) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeaderWithDivider(SpeqaBundle.message("label.steps"))
        if (testCase.steps.isEmpty()) {
            EmptyPreviewValue(SpeqaBundle.message("label.noSteps"))
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                testCase.steps.forEachIndexed { index, step ->
                    if (index > 0) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(SpeqaThemeColors.divider)
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Text(
                            text = (index + 1).toString().padStart(2, '0'),
                            color = SpeqaThemeColors.mutedForeground,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            lineHeight = 20.sp,
                            letterSpacing = 0.6.sp,
                            modifier = Modifier.width(SpeqaLayout.stepNumberColumnWidth),
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            val actionText = step.action.ifBlank { SpeqaBundle.message("label.emptyAction") }
                            val actionColor =
                                if (step.action.isBlank()) SpeqaThemeColors.mutedForeground else SpeqaThemeColors.foreground

                            MarkdownText(
                                markdown = actionText,
                                color = actionColor,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                lineHeight = 20.sp,
                            )

                            if (!step.expected.isNullOrBlank()) {
                                MarkdownText(
                                    markdown = step.expected,
                                    color = SpeqaThemeColors.foreground,
                                    fontSize = 14.sp,
                                    lineHeight = 20.sp,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewMetadataCell(
    label: String,
    modifier: Modifier = Modifier,
    editAction: (() -> Unit)? = null,
    isEditing: Boolean = false,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(SpeqaLayout.tightGap),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            SectionLabel(label)
            if (editAction != null) {
                EditToggleIcon(
                    isEditing = isEditing,
                    onClick = editAction,
                )
            }
        }
        content()
    }
}

@Composable
private fun SingleLineMetadataValue(text: String) {
    Text(
        text = text,
        color = SpeqaThemeColors.foreground,
        fontSize = 13.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun EmptyPreviewValue(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text,
        modifier = modifier,
        color = SpeqaThemeColors.mutedForeground,
        fontSize = 13.sp,
    )
}
