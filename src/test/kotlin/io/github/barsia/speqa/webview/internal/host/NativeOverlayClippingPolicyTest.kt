package io.github.barsia.speqa.webview.internal.host

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NativeOverlayClippingPolicyTest {
  private var previousEnabled: String? = null
  private var previousDiagnostics: String? = null

  @Before
  fun setUp() {
    previousEnabled = System.getProperty(NativeOverlayClippingPolicy.ENABLED_PROPERTY)
    previousDiagnostics = System.getProperty(NativeOverlayClippingPolicy.DIAGNOSTICS_PROPERTY)
    System.clearProperty(NativeOverlayClippingPolicy.ENABLED_PROPERTY)
    System.clearProperty(NativeOverlayClippingPolicy.DIAGNOSTICS_PROPERTY)
    NativeOverlayClippingPolicy.resetRuntimeDisableForTests()
  }

  @After
  fun tearDown() {
    restoreProperty(NativeOverlayClippingPolicy.ENABLED_PROPERTY, previousEnabled)
    restoreProperty(NativeOverlayClippingPolicy.DIAGNOSTICS_PROPERTY, previousDiagnostics)
    NativeOverlayClippingPolicy.resetRuntimeDisableForTests()
  }

  @Test
  fun `overlay clipping is enabled by default`() {
    assertTrue(NativeOverlayClippingPolicy.isEnabled())
    assertFalse(NativeOverlayClippingPolicy.isDiagnosticsEnabled())
  }

  @Test
  fun `overlay clipping can be disabled by system property`() {
    System.setProperty(NativeOverlayClippingPolicy.ENABLED_PROPERTY, "false")

    assertFalse(NativeOverlayClippingPolicy.isEnabled())
  }

  @Test
  fun `overlay clipping diagnostics can be enabled by system property`() {
    System.setProperty(NativeOverlayClippingPolicy.DIAGNOSTICS_PROPERTY, "true")

    assertTrue(NativeOverlayClippingPolicy.isDiagnosticsEnabled())
  }

  @Test
  fun `runtime disable wins over enabled property for the current session`() {
    System.setProperty(NativeOverlayClippingPolicy.ENABLED_PROPERTY, "true")

    NativeOverlayClippingPolicy.disableForSession("native failure")

    assertFalse(NativeOverlayClippingPolicy.isEnabled())
    assertEquals("native failure", NativeOverlayClippingPolicy.runtimeDisabledReason())
  }

  @Test
  fun `runtime disable records only the first failure reason`() {
    NativeOverlayClippingPolicy.disableForSession("first")
    NativeOverlayClippingPolicy.disableForSession("second")

    assertEquals("first", NativeOverlayClippingPolicy.runtimeDisabledReason())
  }

  @Test
  fun `test reset clears runtime disable reason`() {
    NativeOverlayClippingPolicy.disableForSession("native failure")

    NativeOverlayClippingPolicy.resetRuntimeDisableForTests()

    assertTrue(NativeOverlayClippingPolicy.isEnabled())
    assertNull(NativeOverlayClippingPolicy.runtimeDisabledReason())
  }

  private fun restoreProperty(name: String, value: String?) {
    if (value == null) {
      System.clearProperty(name)
    }
    else {
      System.setProperty(name, value)
    }
  }
}
