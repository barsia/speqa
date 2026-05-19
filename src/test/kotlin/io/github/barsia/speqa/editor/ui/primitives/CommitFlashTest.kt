package io.github.barsia.speqa.editor.ui.primitives

import org.junit.Assert.assertEquals
import org.junit.Test
import java.awt.Color

class CommitFlashTest {
    private val red = Color(255, 0, 0, 255)
    private val blue = Color(0, 0, 255, 255)

    @Test
    fun `t=0 returns start`() {
        assertEquals(red, interpolate(red, blue, 0f))
    }

    @Test
    fun `t=1 returns end`() {
        assertEquals(blue, interpolate(red, blue, 1f))
    }

    @Test
    fun `t=0_5 is halfway`() {
        val mid = interpolate(red, blue, 0.5f)
        assertEquals(127, mid.red)
        assertEquals(0, mid.green)
        assertEquals(127, mid.blue)
        assertEquals(255, mid.alpha)
    }

    @Test
    fun `t below zero is clamped`() {
        assertEquals(red, interpolate(red, blue, -0.5f))
    }

    @Test
    fun `t above one is clamped`() {
        assertEquals(blue, interpolate(red, blue, 2f))
    }

    @Test
    fun `alpha interpolates`() {
        val transparent = Color(0, 0, 0, 0)
        val opaque = Color(0, 0, 0, 200)
        val mid = interpolate(transparent, opaque, 0.5f)
        assertEquals(100, mid.alpha)
    }
}
