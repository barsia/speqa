package io.github.barsia.speqa.editor

internal class LazyComposeMountController {
    private var isMountRequested: Boolean = false

    var isMounted: Boolean = false
        private set

    fun shouldRequestMount(isDisplayable: Boolean): Boolean {
        if (!isDisplayable || isMountRequested || isMounted) return false
        isMountRequested = true
        return true
    }

    fun shouldMount(): Boolean {
        if (!isMountRequested || isMounted) return false
        isMounted = true
        return true
    }
}
