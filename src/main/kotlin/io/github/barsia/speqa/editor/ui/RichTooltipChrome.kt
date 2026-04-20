package io.github.barsia.speqa.editor.ui

internal enum class RichTooltipChrome {
    Default,
    EdgeToEdge,
}

internal fun richTooltipChrome(edgeToEdgeContent: Boolean): RichTooltipChrome {
    return if (edgeToEdgeContent) RichTooltipChrome.EdgeToEdge else RichTooltipChrome.Default
}
