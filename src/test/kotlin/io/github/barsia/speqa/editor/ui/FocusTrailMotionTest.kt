package io.github.barsia.speqa.editor.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusTrailMotionTest {

    @Test
    fun `focus trail motion uses short fade and small slide`() {
        val motion = focusTrailMotionContract()

        assertEquals(220, motion.durationMillis)
        assertEquals(8, motion.slideOffsetDp)
        assertTrue(motion.durationMillis > 0)
        assertTrue(motion.slideOffsetDp > 0)
    }
}
