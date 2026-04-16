package io.github.barsia.speqa.editor.ui

import kotlin.math.max
import kotlin.math.min

internal object DragAutoScroll {
    const val DEFAULT_EDGE_ZONE_DP = 48f
    const val DEFAULT_MAX_SPEED_DP_PER_FRAME = 12f

    fun computeScrollDelta(
        itemTop: Float,
        itemBottom: Float,
        viewportTop: Float,
        viewportBottom: Float,
        edgeZonePx: Float,
        maxSpeedPxPerFrame: Float,
    ): Float {
        val viewportHeight = viewportBottom - viewportTop
        if (viewportHeight <= 0f || edgeZonePx <= 0f) return 0f

        val zone = min(edgeZonePx, viewportHeight / 2f)

        val topZoneBottom = viewportTop + zone
        val bottomZoneTop = viewportBottom - zone

        val topPenetration = max(0f, topZoneBottom - itemTop)
        val bottomPenetration = max(0f, itemBottom - bottomZoneTop)

        if (topPenetration == 0f && bottomPenetration == 0f) return 0f

        return if (topPenetration >= bottomPenetration) {
            val fraction = min(1f, topPenetration / zone)
            -fraction * maxSpeedPxPerFrame
        } else {
            val fraction = min(1f, bottomPenetration / zone)
            fraction * maxSpeedPxPerFrame
        }
    }
}
