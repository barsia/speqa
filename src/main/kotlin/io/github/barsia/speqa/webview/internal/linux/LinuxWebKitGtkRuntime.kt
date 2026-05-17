// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package io.github.barsia.speqa.webview.internal.linux

import org.jetbrains.annotations.ApiStatus

/** Variants are declared in preference order (highest first). */
@ApiStatus.Internal
internal enum class LinuxWebKitGtkRuntime(val soname: String, val bundleSubdir: String) {
  Wk41("libwebkit2gtk-4.1.so.0", "native/linux/wk41"),
  Wk40("libwebkit2gtk-4.0.so.37", "native/linux/wk40"),
  ;

  companion object {
    /** Returns the highest-preference variant for which [isInstalled] is true, or null if none match. */
    fun selectPreferred(isInstalled: (LinuxWebKitGtkRuntime) -> Boolean): LinuxWebKitGtkRuntime? =
      entries.firstOrNull(isInstalled)
  }
}
