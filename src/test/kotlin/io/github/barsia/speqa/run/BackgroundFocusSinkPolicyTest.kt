package io.github.barsia.speqa.run

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundFocusSinkPolicyTest {

    @Test
    fun `requests sink focus when pointer up arrives unconsumed`() {
        assertTrue(BackgroundFocusSinkPolicy.shouldRequestSinkFocus(pointerUp = true, upConsumed = false))
    }

    @Test
    fun `does not request sink focus when gesture is cancelled`() {
        assertFalse(BackgroundFocusSinkPolicy.shouldRequestSinkFocus(pointerUp = false, upConsumed = false))
    }

    @Test
    fun `does not request sink focus when pointer up is already consumed`() {
        assertFalse(BackgroundFocusSinkPolicy.shouldRequestSinkFocus(pointerUp = true, upConsumed = true))
    }
}
