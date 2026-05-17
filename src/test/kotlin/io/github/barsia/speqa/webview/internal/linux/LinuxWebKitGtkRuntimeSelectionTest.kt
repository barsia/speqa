// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package io.github.barsia.speqa.webview.internal.linux

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LinuxWebKitGtkRuntimeSelectionTest {
  @Test
  fun `prefers 4_1 when both runtimes are present`() {
    val installed = setOf(LinuxWebKitGtkRuntime.Wk41, LinuxWebKitGtkRuntime.Wk40)
    val selected = LinuxWebKitGtkRuntime.selectPreferred { it in installed }
    assertEquals(LinuxWebKitGtkRuntime.Wk41, selected)
  }

  @Test
  fun `picks 4_1 when only 4_1 is installed`() {
    val installed = setOf(LinuxWebKitGtkRuntime.Wk41)
    val selected = LinuxWebKitGtkRuntime.selectPreferred { it in installed }
    assertEquals(LinuxWebKitGtkRuntime.Wk41, selected)
  }

  @Test
  fun `falls back to 4_0 when 4_1 is missing`() {
    val installed = setOf(LinuxWebKitGtkRuntime.Wk40)
    val selected = LinuxWebKitGtkRuntime.selectPreferred { it in installed }
    assertEquals(LinuxWebKitGtkRuntime.Wk40, selected)
  }

  @Test
  fun `returns null when no runtime is installed`() {
    val selected = LinuxWebKitGtkRuntime.selectPreferred { false }
    assertNull(selected)
  }

  @Test
  fun `runtime exposes the correct soname`() {
    assertEquals("libwebkit2gtk-4.1.so.0", LinuxWebKitGtkRuntime.Wk41.soname)
    assertEquals("libwebkit2gtk-4.0.so.37", LinuxWebKitGtkRuntime.Wk40.soname)
  }

  @Test
  fun `runtime exposes the correct bundle subdirectory`() {
    assertEquals("native/linux/wk41", LinuxWebKitGtkRuntime.Wk41.bundleSubdir)
    assertEquals("native/linux/wk40", LinuxWebKitGtkRuntime.Wk40.bundleSubdir)
  }
}
