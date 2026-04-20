package io.github.barsia.speqa.editor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import io.github.barsia.speqa.SpeqaBundle
import io.github.barsia.speqa.model.TestCase
import org.jetbrains.jewel.ui.component.Text

internal data class FocusTrailMotionContract(
    val durationMillis: Int,
    val slideOffsetDp: Int,
)

internal fun focusTrailMotionContract(): FocusTrailMotionContract {
    return FocusTrailMotionContract(
        durationMillis = 220,
        slideOffsetDp = 8,
    )
}

internal fun shouldShowFocusTrail(
    titleBounds: androidx.compose.ui.geometry.Rect?,
    viewportBounds: androidx.compose.ui.geometry.Rect?,
): Boolean {
    if (titleBounds == null || viewportBounds == null) return false
    return titleBounds.bottom <= viewportBounds.top
}

internal fun focusTrailTitle(testCase: TestCase): String {
    val title = testCase.title.ifBlank { SpeqaBundle.message("label.untitledTestCase") }
    val idPrefix = SpeqaBundle.message("label.idPrefix.tc")
    return testCase.id?.let { "$idPrefix$it · $title" } ?: title
}

@Composable
internal fun FocusTrail(
    titleText: String,
    progressText: String? = null,
    progressColor: androidx.compose.ui.graphics.Color = SpeqaThemeColors.mutedForeground,
    onProgressClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(SpeqaLayout.s5)
                .background(SpeqaThemeColors.headerSurface)
                .padding(horizontal = SpeqaLayout.s4),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SpeqaLayout.s2),
        ) {
            Text(
                text = titleText,
                color = SpeqaThemeColors.mutedForeground,
                fontSize = SpeqaTypography.metaFontSize,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (progressText != null) {
                val clickMod = if (onProgressClick != null) {
                    Modifier
                        .handOnHover()
                        .clickableWithPointer(focusable = true, onClick = onProgressClick)
                } else Modifier
                Text(
                    text = progressText,
                    color = progressColor,
                    fontSize = SpeqaTypography.metaFontSize,
                    maxLines = 1,
                    modifier = clickMod,
                )
            }
        }
        HorizontalHairline()
    }
}

@Composable
internal fun FocusTrail(
    testCase: TestCase,
    onJumpToFirstMissingExpected: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val totalSteps = testCase.steps.size
    val filledCount = testCase.steps.count {
        it.action.isNotBlank() && !it.expected.isNullOrBlank()
    }
    val missingExists = filledCount < totalSteps
    FocusTrail(
        titleText = focusTrailTitle(testCase),
        progressText = if (totalSteps > 0) SpeqaBundle.message("focusTrail.progress", filledCount, totalSteps) else null,
        progressColor = if (missingExists) SpeqaThemeColors.destructive else SpeqaThemeColors.mutedForeground,
        onProgressClick = if (missingExists) onJumpToFirstMissingExpected else null,
        modifier = modifier,
    )
}
