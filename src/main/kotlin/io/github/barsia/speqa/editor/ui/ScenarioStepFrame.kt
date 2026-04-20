package io.github.barsia.speqa.editor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable

internal data class ScenarioStepFrameLayout(
    val narrow: Boolean,
    val metaStacked: Boolean,
)

@Composable
internal fun ScenarioStepFrame(
    modifier: Modifier = Modifier,
    gutterModifier: Modifier = Modifier,
    gutter: @Composable ColumnScope.() -> Unit,
    actionContent: @Composable () -> Unit,
    expectedContent: @Composable () -> Unit,
    metaContent: @Composable (ScenarioStepFrameLayout) -> Unit,
    footerContent: @Composable () -> Unit = {},
) {
    Layout(
        modifier = modifier.fillMaxWidth(),
        content = {
            Column(
                modifier = gutterModifier,
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(SpeqaLayout.s1),
                content = gutter,
            )
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val layout = ScenarioStepFrameLayout(
                    narrow = maxWidth < 440.dp,
                    metaStacked = maxWidth < 320.dp,
                )
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(SpeqaLayout.compactGap),
                ) {
                    if (layout.narrow) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(SpeqaLayout.s2),
                        ) {
                            actionContent()
                            expectedContent()
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(SpeqaLayout.s5),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Column(modifier = Modifier.weight(1f)) { actionContent() }
                            Column(modifier = Modifier.weight(1f)) { expectedContent() }
                        }
                    }
                    metaContent(layout)
                    footerContent()
                }
            }
        },
    ) { measurables, constraints ->
        val gutterPlaceable = measurables[0].measure(constraints.copy(minWidth = 0))
        val gapPx = with(this) { SpeqaLayout.s2.roundToPx() }
        val contentConstraints = constraints.copy(
            minWidth = 0,
            maxWidth = (constraints.maxWidth - gutterPlaceable.width - gapPx).coerceAtLeast(0),
        )
        val contentPlaceable = measurables[1].measure(contentConstraints)
        val width = (gutterPlaceable.width + gapPx + contentPlaceable.width).coerceAtMost(constraints.maxWidth)
        val height = maxOf(gutterPlaceable.height, contentPlaceable.height)
        layout(width, height) {
            gutterPlaceable.placeRelative(0, 0)
            contentPlaceable.placeRelative(gutterPlaceable.width + gapPx, 0)
        }
    }
}
