// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package io.github.barsia.speqa.webview.internal.linux

import io.github.barsia.speqa.webview.internal.WebViewLogger
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
internal fun interface LinuxWebKitGtkRuntimeProbe {
  fun isInstalled(runtime: LinuxWebKitGtkRuntime): Boolean
}

@ApiStatus.Internal
internal class LdconfigLinuxWebKitGtkRuntimeProbe(
  private val ldconfigRunner: () -> String = ::runLdconfig,
) : LinuxWebKitGtkRuntimeProbe {
  private val cachedOutput by lazy {
    runCatching { ldconfigRunner() }
      .onFailure {
        WebViewLogger.LOG.warn(
          "ldconfig probe failed; cannot detect WebKitGTK 4.1/4.0 — install libwebkit2gtk-4.1-0 (preferred) or libwebkit2gtk-4.0-37",
          it,
        )
      }
      .getOrNull()
  }

  override fun isInstalled(runtime: LinuxWebKitGtkRuntime): Boolean {
    val output = cachedOutput ?: return false
    return output.lineSequence().any { line ->
      val firstToken = line.trim().substringBefore(' ', missingDelimiterValue = "")
      firstToken == runtime.soname
    }
  }
}

private fun runLdconfig(): String {
  val candidates = listOf(
    arrayOf("ldconfig", "-p"),
    arrayOf("/sbin/ldconfig", "-p"),
    arrayOf("/usr/sbin/ldconfig", "-p"),
  )
  var lastError: Throwable? = null
  for (cmd in candidates) {
    try {
      val process = ProcessBuilder(*cmd).redirectErrorStream(true).start()
      val output = process.inputStream.bufferedReader().use { it.readText() }
      val rc = process.waitFor()
      if (rc == 0) return output
      lastError = IllegalStateException("${cmd.joinToString(" ")} exited with $rc: ${output.take(200)}")
    }
    catch (t: Throwable) {
      lastError = t
    }
  }
  throw IllegalStateException("Could not run ldconfig -p", lastError)
}
