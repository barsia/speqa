package io.github.barsia.speqa.editor.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import io.github.barsia.speqa.SpeqaBundle
import io.github.barsia.speqa.editor.AttachmentSupport
import io.github.barsia.speqa.model.TestCase
import io.github.barsia.speqa.model.TestStep
import io.github.barsia.speqa.parser.PatchOperation
import org.jetbrains.jewel.ui.component.Text
import kotlin.math.abs

@Composable
internal fun StepsSection(
    testCase: TestCase,
    onPatch: (TestCase, PatchOperation) -> Unit,
    project: Project? = null,
    tcFile: VirtualFile? = null,
    focusRequestStepIndex: Int = -1,
    onFocusRequestStepIndexChange: (Int) -> Unit = {},
    onStepDragActiveChange: (Boolean) -> Unit = {},
    attachmentRevision: Long = 0L,
    scrollState: ScrollState? = null,
    viewportBounds: () -> Rect? = { null },
) {
    var draggedIndex by remember { mutableStateOf(-1) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var itemHeights by remember { mutableStateOf(mapOf<Int, Int>()) }
    // Plain (non-observable) map — read each frame from the auto-scroll LaunchedEffect.
    // `onGloballyPositioned` fires only on layout, not on `graphicsLayer.translationY`
    // changes, so we must hold a live LayoutCoordinates handle for every item (not just
    // the dragged one) so that when drag starts we already have its handle.
    val itemCoordinates = remember(testCase.steps.size) { mutableMapOf<Int, LayoutCoordinates>() }
    val density = LocalDensity.current
    val edgeZonePx = with(density) { DragAutoScroll.DEFAULT_EDGE_ZONE_DP.dp.toPx() }
    val maxSpeedPxPerFrame = with(density) { DragAutoScroll.DEFAULT_MAX_SPEED_DP_PER_FRAME.dp.toPx() }
    val currentTestCase by rememberUpdatedState(testCase)
    val currentOnPatch by rememberUpdatedState(onPatch)
    val stepActionFocusRequesters = remember(testCase.steps.size) {
        List(testCase.steps.size) { FocusRequester() }
    }
    val stepExpectedReverseEntryFocusRequesters = remember(testCase.steps.size) {
        List(testCase.steps.size) { FocusRequester() }
    }
    val addStepFocusRequester = remember { FocusRequester() }
    var pendingAddStepFocus by remember { mutableStateOf(false) }
    LaunchedEffect(pendingAddStepFocus) {
        if (pendingAddStepFocus) {
            addStepFocusRequester.requestFocus()
            pendingAddStepFocus = false
        }
    }

    LaunchedEffect(draggedIndex) {
        if (draggedIndex < 0) return@LaunchedEffect
        val state = scrollState ?: return@LaunchedEffect
        while (true) {
            withFrameNanos { }
            if (draggedIndex < 0) break
            val itemBounds = itemCoordinates[draggedIndex]
                ?.takeIf { it.isAttached }
                ?.boundsInWindow()
                ?: continue
            val viewport = viewportBounds() ?: continue
            // `boundsInWindow()` returns layout bounds — `graphicsLayer.translationY`
            // (the drag offset) is NOT reflected there. Add it manually to get visual Y.
            val delta = DragAutoScroll.computeScrollDelta(
                itemTop = itemBounds.top + dragOffsetY,
                itemBottom = itemBounds.bottom + dragOffsetY,
                viewportTop = viewport.top,
                viewportBottom = viewport.bottom,
                edgeZonePx = edgeZonePx,
                maxSpeedPxPerFrame = maxSpeedPxPerFrame,
            )
            if (delta == 0f) continue
            val consumed = state.scrollBy(delta)
            if (consumed != 0f) {
                dragOffsetY += consumed
            }
        }
    }

    fun onDragEnd() {
        val fromIndex = draggedIndex
        val currentOffsetY = dragOffsetY
        val tc = currentTestCase
        draggedIndex = -1
        dragOffsetY = 0f
        onStepDragActiveChange(false)
        if (fromIndex < 0) return
        val targetIndex = calculateTargetIndex(fromIndex, currentOffsetY, itemHeights, tc.steps.size)
        if (targetIndex != fromIndex && targetIndex in tc.steps.indices) {
            val reordered = tc.steps.toMutableList()
            val item = reordered.removeAt(fromIndex)
            reordered.add(targetIndex, item)
            currentOnPatch(tc.copy(steps = reordered), PatchOperation.ReorderSteps(fromIndex, targetIndex))
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(SpeqaLayout.blockGap)) {
        SectionHeaderWithDivider(SpeqaBundle.message("form.section.steps"))

        if (testCase.steps.isEmpty()) {
            Text(
                SpeqaBundle.message("form.emptySteps"),
                fontSize = 13.sp,
                color = SpeqaThemeColors.mutedForeground,
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SpeqaThemeColors.blockSurface, RoundedCornerShape(SpeqaLayout.blockRadius))
                    .padding(start = 0.dp, top = 0.dp, end = 0.dp, bottom = SpeqaLayout.blockPadding),
                verticalArrangement = Arrangement.spacedBy(SpeqaLayout.blockGap),
            ) {
                val dropTargetIndex = if (draggedIndex >= 0) {
                    calculateTargetIndex(draggedIndex, dragOffsetY, itemHeights, testCase.steps.size)
                } else -1
                // Inter-step layout in the Column: Arrangement.spacedBy(blockGap) + 1dp
                // SurfaceDivider between boxes — so the distance from one step's top to the
                // next step's top is (itemHeight + 2*blockGap + 1dp). Neighbours must shift by
                // exactly that much to fully close the dragged item's slot; anything less
                // leaves a visible residual gap around the dragged item's former bottom border.
                val blockGapPx = with(density) { SpeqaLayout.blockGap.toPx() }
                val dividerPx = with(density) { 1.dp.toPx() }
                val interStepPaddingPx = 2f * blockGapPx + dividerPx
                val draggedSlotHeight = if (draggedIndex >= 0) {
                    (itemHeights[draggedIndex] ?: 0) + interStepPaddingPx
                } else 0f

                testCase.steps.forEachIndexed { index, step ->
                 key(step.uid) {
                    val isDragging = draggedIndex == index
                    val isDropTarget = draggedIndex >= 0 && index == dropTargetIndex && index != draggedIndex
                    val shiftTarget = when {
                        isDragging || draggedIndex < 0 -> 0f
                        // Dragging down: items in (draggedIndex, dropTargetIndex] move up.
                        dropTargetIndex > draggedIndex && index in (draggedIndex + 1)..dropTargetIndex -> -draggedSlotHeight
                        // Dragging up: items in [dropTargetIndex, draggedIndex) move down.
                        dropTargetIndex < draggedIndex && index in dropTargetIndex until draggedIndex -> draggedSlotHeight
                        else -> 0f
                    }
                    val springShift by animateFloatAsState(
                        targetValue = shiftTarget,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow,
                        ),
                        label = "stepReorderShift",
                    )
                    // Bypass the spring as soon as the drag ends. `animateFloatAsState` snaps
                    // to the new target only on the next frame, which otherwise flashes the
                    // pre-reorder shifted position for one frame before settling. Reading 0
                    // directly when there is no drag avoids that frame entirely.
                    val animatedShift = if (draggedIndex < 0) 0f else springShift
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .onGloballyPositioned { coordinates ->
                                itemCoordinates[index] = coordinates
                                if (draggedIndex >= 0) return@onGloballyPositioned
                                val h = coordinates.size.height
                                if (itemHeights[index] != h) {
                                    itemHeights = itemHeights + (index to h)
                                }
                            }
                            .graphicsLayer {
                                translationY = if (isDragging) dragOffsetY else animatedShift
                                alpha = if (isDragging) 0.7f else 1f
                            }
                            .drawWithContent {
                                drawContent()
                                if (isDropTarget) {
                                    // Keep the drop-box anchored to the final landing screen
                                    // position through the whole shift animation — otherwise it
                                    // appears to slide in from outside as `animatedShift` ramps up.
                                    //
                                    // Landing position (= dropped item's layoutTop after reorder):
                                    //  - drag UP:   landingY = dropTarget.layoutTop
                                    //  - drag DOWN: landingY = dropTarget.layoutTop + (dropTargetH − draggedH)
                                    //
                                    // `drawWithContent` runs inside `graphicsLayer { translationY = animatedShift }`,
                                    // so screenY = layoutTop + animatedShift + localY. Cancel the
                                    // `animatedShift` term to keep localY independent of the animation.
                                    val draggingDown = dropTargetIndex > draggedIndex
                                    val draggedHeightPx = (itemHeights[draggedIndex] ?: 0).toFloat()
                                    val yOffset = if (draggingDown) {
                                        size.height - draggedHeightPx - animatedShift
                                    } else {
                                        -animatedShift
                                    }
                                    val slotSize = Size(size.width, draggedHeightPx)
                                    val slotTopLeft = Offset(0f, yOffset)
                                    val cornerRadiusPx = with(density) { 4.dp.toPx() }
                                    val cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx)
                                    drawRoundRect(
                                        color = SpeqaThemeColors.dropTarget.copy(alpha = 0.18f),
                                        topLeft = slotTopLeft,
                                        size = slotSize,
                                        cornerRadius = cornerRadius,
                                    )
                                    drawRoundRect(
                                        color = SpeqaThemeColors.dropTarget,
                                        topLeft = slotTopLeft,
                                        size = slotSize,
                                        cornerRadius = cornerRadius,
                                        style = Stroke(width = 2f),
                                    )
                                }
                            },
                    ) {
                        StepCard(
                            index = index,
                            action = step.action,
                            expected = step.expected,
                            onActionChange = { action ->
                                val tc = currentTestCase
                                val s = tc.steps.getOrNull(index) ?: return@StepCard
                                currentOnPatch(tc.copy(steps = tc.steps.updated(index, s.copy(action = action))), PatchOperation.SetStepAction(index, action))
                            },
                            onExpectedChange = { expected ->
                                val tc = currentTestCase
                                val s = tc.steps.getOrNull(index) ?: return@StepCard
                                currentOnPatch(tc.copy(steps = tc.steps.updated(index, s.copy(expected = expected))), PatchOperation.SetStepExpected(index, expected))
                            },
                            onDelete = {
                                val tc = currentTestCase
                                currentOnPatch(tc.copy(steps = tc.steps.filterIndexed { current, _ -> current != index }), PatchOperation.DeleteStep(index))
                                val remaining = tc.steps.size - 1
                                if (remaining > 0) {
                                    onFocusRequestStepIndexChange((index - 1).coerceAtLeast(0))
                                } else {
                                    pendingAddStepFocus = true
                                }
                            },
                            attachments = step.attachments,
                            onAttachmentsChange = { newAttachments ->
                                val tc = currentTestCase
                                val s = tc.steps.getOrNull(index) ?: return@StepCard
                                currentOnPatch(
                                    tc.copy(steps = tc.steps.updated(index, s.copy(attachments = newAttachments))),
                                    PatchOperation.SetStepAttachments(index, newAttachments),
                                )
                            },
                            tickets = step.tickets,
                            onTicketsChange = { newTickets ->
                                val tc = currentTestCase
                                val s = tc.steps.getOrNull(index) ?: return@StepCard
                                currentOnPatch(tc.copy(steps = tc.steps.updated(index, s.copy(tickets = newTickets))), PatchOperation.SetStepTickets(index, newTickets))
                            },
                            links = step.links,
                            onLinksChange = { next ->
                                val tc = currentTestCase
                                val s = tc.steps.getOrNull(index) ?: return@StepCard
                                currentOnPatch(
                                    tc.copy(steps = tc.steps.updated(index, s.copy(links = next))),
                                    PatchOperation.SetStepLinks(index, next),
                                )
                            },
                            project = project,
                            tcFile = tcFile,
                            onOpenFile = { attachment ->
                                if (project != null && tcFile != null) {
                                    AttachmentSupport.resolveFile(project, tcFile, attachment)?.let { vf ->
                                        FileEditorManager.getInstance(project).openFile(vf, true)
                                    }
                                }
                            },
                            attachmentRevision = attachmentRevision,
                            actionFocusRequester = stepActionFocusRequesters[index],
                            previousActionFocusRequester = when {
                                index == 0 -> null
                                else -> stepExpectedReverseEntryFocusRequesters[index - 1]
                            },
                            nextExpectedExitFocusRequester = stepActionFocusRequesters.getOrNull(index + 1) ?: addStepFocusRequester,
                            expectedReverseEntryFocusRequester = stepExpectedReverseEntryFocusRequesters[index],
                            requestFocus = index == focusRequestStepIndex,
                            onFocusConsumed = { onFocusRequestStepIndexChange(-1) },
                            isDragging = isDragging,
                            onDragStart = {
                                draggedIndex = index
                                onStepDragActiveChange(true)
                            },
                            onDrag = { delta -> dragOffsetY += delta },
                            onDragEnd = { onDragEnd() },
                            onInsertStepAfter = {
                                val tc = currentTestCase
                                val newStep = TestStep()
                                val next = tc.steps.toMutableList().apply { add(index + 1, newStep) }
                                currentOnPatch(tc.copy(steps = next), PatchOperation.AddStep(newStep))
                                onFocusRequestStepIndexChange(index + 1)
                            },
                            onDuplicateStep = {
                                val tc = currentTestCase
                                val source = tc.steps.getOrNull(index) ?: return@StepCard
                                val duplicate = source.copy(uid = TestStep.nextUid())
                                val next = tc.steps.toMutableList().apply { add(index + 1, duplicate) }
                                currentOnPatch(tc.copy(steps = next), PatchOperation.AddStep(duplicate))
                                onFocusRequestStepIndexChange(index + 1)
                            },
                            onMoveStepUp = {
                                if (index == 0) return@StepCard
                                val tc = currentTestCase
                                val next = tc.steps.toMutableList()
                                val item = next.removeAt(index)
                                next.add(index - 1, item)
                                currentOnPatch(tc.copy(steps = next), PatchOperation.ReorderSteps(index, index - 1))
                                onFocusRequestStepIndexChange(index - 1)
                            },
                            onMoveStepDown = {
                                if (index >= testCase.steps.lastIndex) return@StepCard
                                val tc = currentTestCase
                                val next = tc.steps.toMutableList()
                                val item = next.removeAt(index)
                                next.add(index + 1, item)
                                currentOnPatch(tc.copy(steps = next), PatchOperation.ReorderSteps(index, index + 1))
                                onFocusRequestStepIndexChange(index + 1)
                            },
                            onEnterFromExpected = {
                                val tc = currentTestCase
                                if (index >= tc.steps.lastIndex) {
                                    val newStep = TestStep()
                                    val next = tc.steps.toMutableList().apply { add(index + 1, newStep) }
                                    currentOnPatch(tc.copy(steps = next), PatchOperation.AddStep(newStep))
                                }
                                onFocusRequestStepIndexChange(index + 1)
                            },
                            onRequestDeleteStep = {
                                val step = testCase.steps.getOrNull(index) ?: return@StepCard
                                val confirmed = if (!step.expected.isNullOrBlank()) {
                                    val result = com.intellij.openapi.ui.Messages.showOkCancelDialog(
                                        SpeqaBundle.message("dialog.deleteStep.message"),
                                        SpeqaBundle.message("dialog.deleteStep.title"),
                                        com.intellij.openapi.ui.Messages.getOkButton(),
                                        com.intellij.openapi.ui.Messages.getCancelButton(),
                                        com.intellij.openapi.ui.Messages.getWarningIcon(),
                                    )
                                    result == com.intellij.openapi.ui.Messages.OK
                                } else {
                                    true
                                }
                                if (confirmed) {
                                    val tc = currentTestCase
                                    val nextSteps = tc.steps.filterIndexed { i, _ -> i != index }
                                    currentOnPatch(tc.copy(steps = nextSteps), PatchOperation.DeleteStep(index))
                                    val focusTarget = index.coerceAtMost(nextSteps.lastIndex).coerceAtLeast(0)
                                    onFocusRequestStepIndexChange(focusTarget)
                                }
                            },
                        )
                    }
                    if (index < testCase.steps.lastIndex) {
                        SurfaceDivider(visible = draggedIndex < 0)
                    }
                 }
                }

                HorizontalHairline()

                QuietActionText(
                    label = SpeqaBundle.message("form.addStep"),
                    onClick = {
                        val tc = currentTestCase
                        val newStep = TestStep()
                        currentOnPatch(tc.copy(steps = tc.steps + newStep), PatchOperation.AddStep(newStep))
                        onFocusRequestStepIndexChange(tc.steps.size)
                    },
                    enabled = true,
                    modifier = Modifier.focusRequester(addStepFocusRequester),
                    previousFocusRequester = if (testCase.steps.isEmpty()) null
                        else stepExpectedReverseEntryFocusRequesters.last(),
                )
            }
        }
    }
}

private fun calculateTargetIndex(
    fromIndex: Int,
    offsetY: Float,
    heights: Map<Int, Int>,
    totalItems: Int,
): Int {
    val direction = if (offsetY > 0) 1 else -1
    var target = fromIndex
    val absOffset = abs(offsetY)
    var accumulated = 0f

    // Fraction of the adjacent step's height that must be crossed before the drop-target
    // flips to the next slot. 0.5 = midpoint (original snappy behaviour). Higher values make
    // the current target "stickier" — the user has to commit further into the next slot
    // before it takes over, which prevents accidental target flips from small overshoots.
    val commitFraction = 0.7f

    var i = fromIndex
    while (if (direction > 0) i < totalItems - 1 else i > 0) {
        val nextIndex = i + direction
        val nextHeight = heights[nextIndex] ?: 100
        accumulated += nextHeight * commitFraction
        if (absOffset > accumulated) {
            target = nextIndex
            accumulated += nextHeight * (1f - commitFraction)
        } else {
            break
        }
        i += direction
    }
    return target
}

internal fun <T> List<T>.updated(index: Int, value: T): List<T> = mapIndexed { current, item ->
    if (current == index) value else item
}
