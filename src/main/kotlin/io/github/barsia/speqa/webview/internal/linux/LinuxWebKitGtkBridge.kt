// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package io.github.barsia.speqa.webview.internal.linux

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.util.SystemInfo
import io.github.barsia.speqa.webview.internal.NativeLibraryLoader
import io.github.barsia.speqa.webview.internal.WebViewLogger
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Files
import java.nio.file.Path

private val candidateFileNames = listOf(
  "libLinuxWebKitGtkBridge.so",
  "liblinux_webkitgtk_bridge.so",
)

internal fun findBundledLibraryForRuntime(
  runtime: LinuxWebKitGtkRuntime,
  classLoader: ClassLoader = LinuxWebKitGtkBridge::class.java.classLoader,
): Path? = NativeLibraryLoader.findBundledLibrary(
  candidateFileNames = candidateFileNames,
  resourceDirectory = runtime.bundleSubdir,
  classLoader = classLoader,
)

@ApiStatus.Internal
internal object LinuxWebKitGtkBridge {
  private val sourceRootMarkers = listOf(
    ".ultimate.root.marker",
    "intellij.idea.community.main.iml",
    "settings.gradle.kts",
  )

  private val runtimeProbe: LinuxWebKitGtkRuntimeProbe = LdconfigLinuxWebKitGtkRuntimeProbe()

  init {
    if (SystemInfo.isLinux) {
      loadNativeLibrary()
    }
  }

  @JvmStatic
  private external fun createNative(parentWindowHandle: Long, backend: Int, callbacks: Callbacks): Long

  @JvmStatic
  private external fun destroyNative(handle: Long)

  @JvmStatic
  private external fun attachToParentNative(handle: Long, parentWindowHandle: Long)

  @JvmStatic
  private external fun detachNative(handle: Long)

  @JvmStatic
  private external fun setBoundsNative(handle: Long, x: Int, y: Int, width: Int, height: Int, scale: Double)

  @JvmStatic
  private external fun setVisibleNative(handle: Long, visible: Boolean)

  @JvmStatic
  private external fun focusNative(handle: Long)

  @JvmStatic
  private external fun clearFocusNative(handle: Long)

  @JvmStatic
  private external fun dispatchMouseButtonNative(
    handle: Long,
    x: Double,
    y: Double,
    button: Int,
    state: Int,
    isPress: Boolean,
  )

  @JvmStatic
  private external fun dispatchMouseMotionNative(
    handle: Long,
    x: Double,
    y: Double,
    state: Int,
  )

  @JvmStatic
  private external fun loadUrlNative(handle: Long, url: String)

  @JvmStatic
  private external fun loadHtmlNative(handle: Long, html: String, baseUrl: String?)

  @JvmStatic
  private external fun evaluateJavaScriptNative(handle: Long, evalId: Long, script: String)

  @JvmStatic
  private external fun deliverJsonToJavaScriptNative(handle: Long, rawJson: String)

  @JvmStatic
  private external fun shutdownRuntimeNative()

  fun create(parentWindowHandle: Long, backend: LinuxWebKitBackend, callbacks: Callbacks): Long = createNative(parentWindowHandle, backend.nativeId, callbacks)
  fun destroy(handle: Long) = destroyNative(handle)
  fun attachToParent(handle: Long, parentWindowHandle: Long) = attachToParentNative(handle, parentWindowHandle)
  fun detach(handle: Long) = detachNative(handle)
  fun setBounds(handle: Long, x: Int, y: Int, width: Int, height: Int, scale: Double) = setBoundsNative(handle, x, y, width, height, scale)
  fun setVisible(handle: Long, visible: Boolean) = setVisibleNative(handle, visible)
  fun focus(handle: Long) = focusNative(handle)
  fun clearFocus(handle: Long) = clearFocusNative(handle)
  fun dispatchMouseButton(handle: Long, x: Double, y: Double, button: Int, state: Int, isPress: Boolean) =
    dispatchMouseButtonNative(handle, x, y, button, state, isPress)

  fun dispatchMouseMotion(handle: Long, x: Double, y: Double, state: Int) =
    dispatchMouseMotionNative(handle, x, y, state)

  fun loadUrl(handle: Long, url: String) = loadUrlNative(handle, url)
  fun loadHtml(handle: Long, html: String, baseUrl: String?) = loadHtmlNative(handle, html, baseUrl)
  fun evaluateJavaScript(handle: Long, evalId: Long, script: String) = evaluateJavaScriptNative(handle, evalId, script)
  fun deliverJsonToJavaScript(handle: Long, rawJson: String) = deliverJsonToJavaScriptNative(handle, rawJson)
  fun shutdownRuntimeForTests() = shutdownRuntimeNative()

  private fun loadNativeLibrary() {
    val runtime = LinuxWebKitGtkRuntime.selectPreferred { runtimeProbe.isInstalled(it) }
                  ?: throw LinuxWebKitGtkMissingException(
                    "WebKitGTK runtime not found. Install libwebkit2gtk-4.1-0 (Ubuntu 22.04+) or libwebkit2gtk-4.0-37 (Ubuntu 20.04 / Debian 11).",
                  )

    WebViewLogger.logLifecycle("linux-webkitgtk-runtime-selected", runtime.name)

    val libraryPath = findNativeLibrary(runtime)
                      ?: error("Linux WebKitGTK bridge library is missing for ${runtime.name}. Checked: ${candidateDescriptions(runtime).joinToString()}")
    WebViewLogger.logLifecycle("linux-webkitgtk-load", "${runtime.name}: $libraryPath")
    try {
      System.load(libraryPath.toString())
    }
    catch (e: UnsatisfiedLinkError) {
      throw IllegalStateException(
        "Failed to load Linux WebKitGTK bridge (${runtime.name}). Soname expected on this host: ${runtime.soname}.",
        e,
      )
    }
  }

  private fun findNativeLibrary(runtime: LinuxWebKitGtkRuntime): Path? {
    for (fileName in candidateFileNames) {
      PathManager.findBinFile(fileName)?.let { return it }
    }

    findBundledLibraryForRuntime(runtime)?.let { return it }

    for (root in nativeLibraryTargetRoots(runtime)) {
      for (fileName in candidateFileNames) {
        val candidate = root.resolve(fileName)
        if (Files.isRegularFile(candidate)) return candidate
      }
    }
    return null
  }

  private fun candidateDescriptions(runtime: LinuxWebKitGtkRuntime): List<String> {
    return candidateFileNames.map { "bin/$it" } +
           NativeLibraryLoader.bundledResourceDescriptions(candidateFileNames, runtime.bundleSubdir) +
           nativeLibraryTargetRoots(runtime).flatMap { root ->
             candidateFileNames.map { root.resolve(it).toString() }
           }
  }

  private fun nativeLibraryTargetRoots(runtime: LinuxWebKitGtkRuntime): List<Path> {
    val featureDir = when (runtime) {
      LinuxWebKitGtkRuntime.Wk41 -> "target-wk41"
      LinuxWebKitGtkRuntime.Wk40 -> "target-wk40"
    }
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
          root.resolve("native/LinuxWebKitGtkBridge/$featureDir/debug"),
          root.resolve("plugins/speqa/native/LinuxWebKitGtkBridge/$featureDir/debug"),
          root.resolve("native/LinuxWebKitGtkBridge/$featureDir/release"),
          root.resolve("plugins/speqa/native/LinuxWebKitGtkBridge/$featureDir/release"),
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
    fun onSnapshot(width: Int, height: Int, pixels: IntArray)
    fun onLog(level: Int, message: String)
  }
}

@ApiStatus.Internal
internal class LinuxWebKitGtkMissingException(message: String) : RuntimeException(message)
