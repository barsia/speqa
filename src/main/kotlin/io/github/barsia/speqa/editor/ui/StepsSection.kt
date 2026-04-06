package io.github.barsia.speqa.editor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
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
) {
    var draggedIndex by remember { mutableStateOf(-1) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var itemHeights by remember { mutableStateOf(mapOf<Int, Int>()) }
    val currentTestCase by rememberUpdatedState(testCase)
    val currentOnPatch by rememberUpdatedState(onPatch)
    val stepActionFocusRequesters = remember(testCase.steps.size) {
        List(testCase.steps.size) { FocusRequester() }
    }
    val stepExpectedReverseEntryFocusRequesters = remember(testCase.steps.size) {
        List(testCase.steps.size) { FocusRequester() }
    }
    val stepActionReverseEntryFocusRequesters = remember(testCase.steps.size) {
        List(testCase.steps.size) { FocusRequester() }
    }
    val addStepFocusRequester = remember { FocusRequester() }

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
                    .padding(start = 0.dp, top = SpeqaLayout.blockPadding, end = 0.dp, bottom = SpeqaLayout.blockPadding),
                verticalArrangement = Arrangement.spacedBy(SpeqaLayout.blockGap),
            ) {
                val dropTargetIndex = if (draggedIndex >= 0) {
                    calculateTargetIndex(draggedIndex, dragOffsetY, itemHeights, testCase.steps.size)
                } else -1

                testCase.steps.forEachIndexed { index, step ->
                    val isDragging = draggedIndex == index
                    val isDropTarget = draggedIndex >= 0 && index == dropTargetIndex && index != draggedIndex
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .onGloballyPositioned { coordinates ->
                                if (draggedIndex >= 0) return@onGloballyPositioned
                                val h = coordinates.size.height
                                if (itemHeights[index] != h) {
                                    itemHeights = itemHeights + (index to h)
                                }
                            }
                            .graphicsLayer {
                                translationY = if (isDragging) dragOffsetY else 0f
                                alpha = if (isDragging) 0.7f else 1f
                            }
                            .drawWithContent {
                                if (isDropTarget) {
                                    drawRect(
                                        color = SpeqaThemeColors.dropTarget,
                                        topLeft = androidx.compose.ui.geometry.Offset(0f, -4f),
                                        size = androidx.compose.ui.geometry.Size(size.width, 4f),
                                    )
                                }
                                drawContent()
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
                            },
                            actionAttachments = step.actionAttachments,
                            expectedAttachments = step.expectedAttachments,
                            onActionAttachmentsChange = { newAttachments ->
                                val tc = currentTestCase
                                val s = tc.steps.getOrNull(index) ?: return@StepCard
                                currentOnPatch(tc.copy(steps = tc.steps.updated(index, s.copy(actionAttachments = newAttachments))), PatchOperation.SetStepActionAttachments(index, newAttachments))
                            },
                            onExpectedAttachmentsChange = { newAttachments ->
                                val tc = currentTestCase
                                val s = tc.steps.getOrNull(index) ?: return@StepCard
                                currentOnPatch(tc.copy(steps = tc.steps.updated(index, s.copy(expectedAttachments = newAttachments))), PatchOperation.SetStepExpectedAttachments(index, newAttachments))
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
                            actionReverseEntryFocusRequester = stepActionReverseEntryFocusRequesters[index],
                            previousActionFocusRequester = when {
                                index == 0 -> null
                                testCase.steps[index - 1].expected != null -> stepExpectedReverseEntryFocusRequesters[index - 1]
                                else -> stepActionReverseEntryFocusRequesters[index - 1]
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
                        )
                    }
                    if (index < testCase.steps.lastIndex) {
                        SurfaceDivider()
                    }
                }

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

    var i = fromIndex
    while (if (direction > 0) i < totalItems - 1 else i > 0) {
        val nextIndex = i + direction
        val nextHeight = heights[nextIndex] ?: 100
        accumulated += nextHeight / 2f
        if (absOffset > accumulated) {
            target = nextIndex
            accumulated += nextHeight / 2f
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
