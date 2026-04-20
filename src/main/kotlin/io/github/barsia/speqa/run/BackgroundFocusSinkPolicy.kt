package io.github.barsia.speqa.run

internal object BackgroundFocusSinkPolicy {
    fun shouldRequestSinkFocus(
        pointerUp: Boolean,
        upConsumed: Boolean,
        hitInteractiveDescendant: Boolean = false,
    ): Boolean {
        return pointerUp && !upConsumed && !hitInteractiveDescendant
    }
}
