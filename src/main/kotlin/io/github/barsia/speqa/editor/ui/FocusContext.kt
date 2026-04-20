package io.github.barsia.speqa.editor.ui

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

internal enum class StepSlot { ACTION, EXPECTED, TICKET, LINK, ATTACHMENT }

@Immutable
internal data class FocusSlot(val stepIndex: Int, val slot: StepSlot)

internal class FocusContext {
    var current by mutableStateOf<FocusSlot?>(null)
}

internal val LocalFocusContext = compositionLocalOf<FocusContext> {
    error("FocusContext not provided — wrap your preview in CompositionLocalProvider(LocalFocusContext provides ...)")
}
