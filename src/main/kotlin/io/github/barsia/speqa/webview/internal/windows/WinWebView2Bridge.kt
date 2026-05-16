// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package io.github.barsia.speqa.webview.internal.windows

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.util.SystemInfo
import io.github.barsia.speqa.webview.internal.NativeLibraryLoader
import io.github.barsia.speqa.webview.internal.WebViewLogger
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Files
import java.nio.file.Path

@ApiStatus.Internal
internal object WinWebView2Bridge {
  private val candidateFileNames = listOf(
    "WinWebView2Bridge.dll",
    "win_webview2_bridge.dll",
  )
  private val sourceRootMarkers = listOf(
    ".ultimate.root.marker",
    "intellij.idea.community.main.iml",
    "settings.gradle.kts",
  )

  init {
    if (SystemInfo.isWindows) {
      loadNativeLibrary()
    }
  }

  @JvmStatic
  private external fun createNative(parentHwnd: Long, userDataDir: String, callbacks: Callbacks): Long

  @JvmStatic
  private external fun destroyNative(handle: Long)

  @JvmStatic
  private external fun attachToParentNative(handle: Long, parentHwnd: Long)

  @JvmStatic
  private external fun detachFromParentNative(handle: Long)

  @JvmStatic
  private external fun setBoundsNative(handle: Long, x: Int, y: Int, width: Int, height: Int, scale: Double)

  @JvmStatic
  private external fun setVisibleNative(handle: Long, visible: Boolean)

  @JvmStatic
  private external fun focusNative(handle: Long)

  @JvmStatic
  private external fun clearFocusNative(handle: Long)

  @JvmStatic
  private external fun loadUrlNative(handle: Long, url: String)

  @JvmStatic
  private external fun loadHtmlNative(handle: Long, html: String, baseUrl: String?)

  @JvmStatic
  private external fun evaluateJavaScriptNative(handle: Long, evalId: Long, script: String)

  @JvmStatic
  private external fun deliverJsonToJavaScriptNative(handle: Long, rawJson: String)

  @JvmStatic
  private external fun pumpMessagesNative(maxMessages: Int): Boolean

  fun create(parentHwnd: Long, userDataDir: String, callbacks: Callbacks): Long = createNative(parentHwnd, userDataDir, callbacks)
  fun destroy(handle: Long) = destroyNative(handle)
  fun attachToParent(handle: Long, parentHwnd: Long) = attachToParentNative(handle, parentHwnd)
  fun detachFromParent(handle: Long) = detachFromParentNative(handle)
  fun setBounds(handle: Long, x: Int, y: Int, width: Int, height: Int, scale: Double) = setBoundsNative(handle, x, y, width, height, scale)
  fun setVisible(handle: Long, visible: Boolean) = setVisibleNative(handle, visible)
  fun focus(handle: Long) = focusNative(handle)
  fun clearFocus(handle: Long) = clearFocusNative(handle)
  fun loadUrl(handle: Long, url: String) = loadUrlNative(handle, url)
  fun loadHtml(handle: Long, html: String, baseUrl: String?) = loadHtmlNative(handle, html, baseUrl)
  fun evaluateJavaScript(handle: Long, evalId: Long, script: String) = evaluateJavaScriptNative(handle, evalId, script)
  fun deliverJsonToJavaScript(handle: Long, rawJson: String) = deliverJsonToJavaScriptNative(handle, rawJson)
  fun pumpMessages(maxMessages: Int): Boolean = pumpMessagesNative(maxMessages)

  private fun loadNativeLibrary() {
    val libraryPath = findNativeLibrary()
                      ?: error("Windows WebView2 bridge DLL is missing. Checked: ${candidateDescriptions().joinToString()}")
    WebViewLogger.logLifecycle("win-webview2-load", libraryPath.toString())
    System.load(libraryPath.toString())
  }

  private fun findNativeLibrary(): Path? {
    for (fileName in candidateFileNames) {
      PathManager.findBinFile(fileName)?.let { return it }
    }

    NativeLibraryLoader.findBundledLibrary(candidateFileNames, "native/windows", javaClass)?.let { return it }

    for (root in nativeLibraryTargetRoots()) {
      for (fileName in candidateFileNames) {
        val candidate = root.resolve(fileName)
        if (Files.isRegularFile(candidate)) return candidate
      }
    }
    return null
  }

  private fun candidateDescriptions(): List<String> {
    return candidateFileNames.map { "bin/$it" } +
           NativeLibraryLoader.bundledResourceDescriptions(candidateFileNames, "native/windows") +
           nativeLibraryTargetRoots().flatMap { root ->
             candidateFileNames.map { root.resolve(it).toString() }
           }
  }

  private fun nativeLibraryTargetRoots(): List<Path> {
    val roots = buildList {
      add(Path.of(PathManager.getHomePath()))
      add(Path.of(System.getProperty("user.dir")))
    }

    return roots.asSequence()
      .flatMap { root -> sequenceOf(root, findSourceRoot(root)) }
      .filterNotNull()
      .distinct()
      .flatMap { root ->
        sequenceOf(
          root.resolve("native/WinWebView2Bridge/target/debug"),
          root.resolve("plugins/speqa/native/WinWebView2Bridge/target/debug"),
          root.resolve("native/WinWebView2Bridge/target/release"),
          root.resolve("plugins/speqa/native/WinWebView2Bridge/target/release"),
        )
      }
      .distinct()
      .toList()
  }

  private fun findSourceRoot(start: Path): Path? {
    var root: Path? = start.toAbsolutePath().normalize()
    while (root != null) {
      if (sourceRootMarkers.any { Files.isRegularFile(root.resolve(it)) }) {
        return root
      }
      root = root.parent
    }
    return null
  }

  internal interface Callbacks {
    fun onCreated(handle: Long)
    fun onCreateFailed(message: String)
    fun onMessage(raw: String)
    fun onEvaluationResult(evalId: Long, result: String?)
    fun onEvaluationError(evalId: Long, message: String)
    fun onAcceleratorKeyPressed(keyEventKind: Int, virtualKey: Int, modifiers: Int, keyEventLParam: Int): Boolean
    fun onLog(level: Int, message: String)
  }
}
