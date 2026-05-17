// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package io.github.barsia.speqa.webview.internal.linux

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LinuxWebKitGtkRuntimeProbeTest {
  @Test
  fun `ldconfig output parser detects 4_1`() {
    val ldconfigOutput = """
      ${'\t'}libwebkit2gtk-4.1.so.0 (libc6,x86-64) => /lib/x86_64-linux-gnu/libwebkit2gtk-4.1.so.0
      ${'\t'}libfoo.so.1 (libc6,x86-64) => /lib/x86_64-linux-gnu/libfoo.so.1
    """.trimIndent()
    val probe = LdconfigLinuxWebKitGtkRuntimeProbe { ldconfigOutput }
    assertTrue(probe.isInstalled(LinuxWebKitGtkRuntime.Wk41))
    assertFalse(probe.isInstalled(LinuxWebKitGtkRuntime.Wk40))
  }

  @Test
  fun `ldconfig output parser detects 4_0`() {
    val ldconfigOutput = "\tlibwebkit2gtk-4.0.so.37 (libc6,x86-64) => /lib/x86_64-linux-gnu/libwebkit2gtk-4.0.so.37"
    val probe = LdconfigLinuxWebKitGtkRuntimeProbe { ldconfigOutput }
    assertFalse(probe.isInstalled(LinuxWebKitGtkRuntime.Wk41))
    assertTrue(probe.isInstalled(LinuxWebKitGtkRuntime.Wk40))
  }

  @Test
  fun `ldconfig probe returns false when command throws`() {
    val probe = LdconfigLinuxWebKitGtkRuntimeProbe { error("ldconfig not available") }
    assertFalse(probe.isInstalled(LinuxWebKitGtkRuntime.Wk41))
    assertFalse(probe.isInstalled(LinuxWebKitGtkRuntime.Wk40))
  }

  @Test
  fun `ldconfig output parser rejects soname substring false positives`() {
    // A hypothetical library whose name has the wk40 soname as a prefix; must not match.
    val ldconfigOutput = "\tlibwebkit2gtk-4.0.so.370 (libc6,x86-64) => /lib/x86_64-linux-gnu/libwebkit2gtk-4.0.so.370"
    val probe = LdconfigLinuxWebKitGtkRuntimeProbe { ldconfigOutput }
    assertFalse(probe.isInstalled(LinuxWebKitGtkRuntime.Wk41))
    assertFalse(probe.isInstalled(LinuxWebKitGtkRuntime.Wk40))
  }
}
