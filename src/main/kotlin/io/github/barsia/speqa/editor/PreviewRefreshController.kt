package io.github.barsia.speqa.editor

internal enum class PreviewRefreshTiming {
    IMMEDIATE,
    DEBOUNCED,
    NONE,
}

internal class PreviewRefreshController {
    private var forceFocusedTextSyncOnNextRefresh = false
    private var immediateRefreshScheduled = false

    fun requestRefresh(fromPreviewUndoRedo: Boolean): PreviewRefreshTiming {
        if (immediateRefreshScheduled) {
            if (fromPreviewUndoRedo) forceFocusedTextSyncOnNextRefresh = true
            return PreviewRefreshTiming.NONE
        }
        return if (fromPreviewUndoRedo) {
            forceFocusedTextSyncOnNextRefresh = true
            immediateRefreshScheduled = true
            PreviewRefreshTiming.IMMEDIATE
        } else {
            PreviewRefreshTiming.DEBOUNCED
        }
    }

    fun consumeForceFocusedTextSync(): Boolean {
        val force = forceFocusedTextSyncOnNextRefresh
        forceFocusedTextSyncOnNextRefresh = false
        return force
    }

    fun markRefreshCompleted() {
        immediateRefreshScheduled = false
    }
}
