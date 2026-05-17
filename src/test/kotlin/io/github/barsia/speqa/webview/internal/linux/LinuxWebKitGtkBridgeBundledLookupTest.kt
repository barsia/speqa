// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package io.github.barsia.speqa.webview.internal.linux

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.net.URLClassLoader
import java.nio.file.Files

class LinuxWebKitGtkBridgeBundledLookupTest {
  @get:Rule
  val tempDir = TemporaryFolder()

  @Test
  fun `finds wk41 so when 4_1 runtime is selected`() {
    val root = tempDir.root.toPath()
    val wk41Lib = Files.createDirectories(root.resolve("native/linux/wk41")).resolve("libLinuxWebKitGtkBridge.so")
    Files.writeString(wk41Lib, "fake-wk41")
    val wk40Lib = Files.createDirectories(root.resolve("native/linux/wk40")).resolve("libLinuxWebKitGtkBridge.so")
    Files.writeString(wk40Lib, "fake-wk40")

    val classLoader = URLClassLoader(arrayOf(root.toUri().toURL()), null)
    val found = findBundledLibraryForRuntime(LinuxWebKitGtkRuntime.Wk41, classLoader)
    assertEquals(wk41Lib.fileName, found?.fileName)
    assertEquals("fake-wk41", Files.readString(found!!))
  }

  @Test
  fun `finds wk40 so when 4_0 runtime is selected`() {
    val root = tempDir.root.toPath()
    val wk41Lib = Files.createDirectories(root.resolve("native/linux/wk41")).resolve("libLinuxWebKitGtkBridge.so")
    Files.writeString(wk41Lib, "fake-wk41")
    val wk40Lib = Files.createDirectories(root.resolve("native/linux/wk40")).resolve("libLinuxWebKitGtkBridge.so")
    Files.writeString(wk40Lib, "fake-wk40")

    val classLoader = URLClassLoader(arrayOf(root.toUri().toURL()), null)
    val found = findBundledLibraryForRuntime(LinuxWebKitGtkRuntime.Wk40, classLoader)
    assertEquals("fake-wk40", Files.readString(found!!))
  }

  @Test
  fun `returns null when neither variant is bundled`() {
    val classLoader = URLClassLoader(arrayOf(tempDir.root.toURI().toURL()), null)
    val found = findBundledLibraryForRuntime(LinuxWebKitGtkRuntime.Wk41, classLoader)
    assertNull(found)
  }
}
