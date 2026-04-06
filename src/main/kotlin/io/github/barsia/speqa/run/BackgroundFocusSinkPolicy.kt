package io.github.barsia.speqa.run

internal object BackgroundFocusSinkPolicy {
    fun shouldRequestSinkFocus(pointerUp: Boolean, upConsumed: Boolean): Boolean {
        return pointerUp && !upConsumed
    }
}
