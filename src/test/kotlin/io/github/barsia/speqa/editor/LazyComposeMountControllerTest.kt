package io.github.barsia.speqa.editor

import org.junit.Assert.assertEquals
import org.junit.Test

class LazyComposeMountControllerTest {

    @Test
    fun `does not request mount before component becomes displayable`() {
        val controller = LazyComposeMountController()

        assertEquals(false, controller.shouldRequestMount(false))
        assertEquals(false, controller.isMounted)
    }

    @Test
    fun `requests mount once when component becomes displayable`() {
        val controller = LazyComposeMountController()

        assertEquals(true, controller.shouldRequestMount(true))
        assertEquals(false, controller.shouldRequestMount(true))
    }

    @Test
    fun `mounts once after request`() {
        val controller = LazyComposeMountController()

        assertEquals(true, controller.shouldRequestMount(true))
        assertEquals(true, controller.shouldMount())
        assertEquals(true, controller.isMounted)
        assertEquals(false, controller.shouldMount())
    }
}
