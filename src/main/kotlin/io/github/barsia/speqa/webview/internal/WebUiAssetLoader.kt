// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package io.github.barsia.speqa.webview.internal

import com.intellij.openapi.application.PathManager
import org.jetbrains.annotations.ApiStatus
import java.io.IOException
import java.net.URI
import java.net.URL
import java.nio.charset.StandardCharsets
import java.nio.file.FileSystem
import java.nio.file.FileSystemNotFoundException
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.Comparator

/**
 * Resolves a base URL for bundled web UI resources so they can be loaded in a WebView.
 *
 * Strategy depends on how the classpath resource resolves:
 *  - `file:` (exploded plugin / sources layout) — return the URL as-is, the WebView
 *    reads from disk directly with no extraction.
 *  - `jar:` (IntelliJ dev-run and packaged distributions alike — both jar the
 *    module into its runtime classpath) — extract the resource tree into an
 *    IDE-managed cache under [PathManager.getSystemPath] and hand back a `file:`
 *    URL into that cache. Keyed by content hash so rebuilds invalidate cleanly.
 *
 * The long-term plan is to drop extraction entirely in favor of a custom
 * `WKURLSchemeHandler` that reads classpath bytes directly — that removes the
 * on-disk cache and the dev/dist distinction. Tracked as follow-up.
 */
@ApiStatus.Internal
internal object WebUiAssetLoader {
  private val cacheRoot: Path = Path.of(PathManager.getSystemPath(), "webview-cache")

  /**
   * Returns a base URL for the classpath [resourceRoot] directory, suitable for
   * loading `${baseUrl}/index.html` (and its relative assets) in a WebView.
   *
   * @param resourceRoot classpath prefix, e.g. `"webui/sample-panel"`
   * @param classLoader the class loader to resolve resources from
   * @return `file:` URL string — either pointing at the source tree (exploded
   *         classpath) or at the extracted copy under the IDE system cache
   * @throws IOException when the resource root is missing or uses an
   *         unsupported classpath protocol
   */
  fun getBaseUrl(resourceRoot: String, classLoader: ClassLoader = WebUiAssetLoader::class.java.classLoader): String {
    val normalizedRoot = resourceRoot.trim('/').trim()
    if (normalizedRoot.isEmpty()) {
      throw IOException("Resource root must not be empty")
    }
    val resourceUrl = classLoader.getResource(normalizedRoot)
                      ?: throw IOException("Resource root not found: $normalizedRoot")
    return when (resourceUrl.protocol) {
      // Round-trip through Path.toUri() so the result is always the canonical
      // `file:///<absolute-path>` form — some `URLClassLoader` implementations
      // return `file:/...` (no authority), which NSURL/WKWebView can reject.
      "file" -> Path.of(resourceUrl.toURI()).toUri().toString().trimEnd('/')
      "jar" -> extractJarResourcesAndGetBaseUrl(normalizedRoot, classLoader)
      else -> throw IOException("Unsupported resource protocol '${resourceUrl.protocol}' for $normalizedRoot")
    }
  }

  private fun extractJarResourcesAndGetBaseUrl(normalizedRoot: String, classLoader: ClassLoader): String {
    val rootCacheDir = cacheRoot.resolve(sanitizeForCachePath(normalizedRoot))
    val contentHash = withResourceRootPath(normalizedRoot, classLoader) { rootPath ->
      computeTreeHash(rootPath)
    }
    val extractDir = rootCacheDir.resolve(contentHash)

    if (!Files.exists(extractDir.resolve("index.html"))) {
      Files.createDirectories(extractDir)
      withResourceRootPath(normalizedRoot, classLoader) { rootPath ->
        extractResourceTree(rootPath, extractDir)
      }
    }

    cleanupStaleCacheEntries(rootCacheDir, keepHash = contentHash)
    return extractDir.toUri().toString().trimEnd('/')
  }

  private fun extractResourceTree(sourceRoot: Path, targetDir: Path) {
    val resourceFiles = collectResourceFiles(sourceRoot)
    if (resourceFiles.isEmpty()) {
      throw IOException("No files found under resource root: $sourceRoot")
    }

    resourceFiles.forEach { sourceFile ->
      val relativePath = sourceRoot.relativize(sourceFile).toString()
      val targetFile = targetDir.resolve(relativePath)
      Files.createDirectories(targetFile.parent)
      Files.copy(sourceFile, targetFile, StandardCopyOption.REPLACE_EXISTING)
    }

    if (!Files.exists(targetDir.resolve("index.html"))) {
      throw IOException("Extracted resource root does not contain index.html: $sourceRoot")
    }
  }

  private fun computeTreeHash(resourceRoot: Path): String {
    val resourceFiles = collectResourceFiles(resourceRoot)
    val digest = MessageDigest.getInstance("SHA-256")

    resourceFiles.forEach { file ->
      val relativePath = resourceRoot.relativize(file).toString().replace('\\', '/')
      digest.update(relativePath.toByteArray(StandardCharsets.UTF_8))
      digest.update(0)
      digest.update(Files.readAllBytes(file))
      digest.update(0)
    }

    return digest.digest().take(8).joinToString("") { "%02x".format(it) }
  }

  private fun collectResourceFiles(resourceRoot: Path): List<Path> {
    val result = ArrayList<Path>()
    Files.walk(resourceRoot).use { stream ->
      stream
        .filter { Files.isRegularFile(it) }
        .forEach { result.add(it) }
    }
    result.sortBy { resourceRoot.relativize(it).toString().replace('\\', '/') }
    return result
  }

  private fun cleanupStaleCacheEntries(rootCacheDir: Path, keepHash: String) {
    if (!Files.isDirectory(rootCacheDir)) return

    Files.list(rootCacheDir).use { stream ->
      stream
        .filter { Files.isDirectory(it) }
        .filter { it.fileName.toString() != keepHash }
        .forEach { staleDir ->
          runCatching { deleteRecursively(staleDir) }
        }
    }
  }

  private fun deleteRecursively(path: Path) {
    Files.walk(path).use { stream ->
      stream
        .sorted(Comparator.reverseOrder())
        .forEach { Files.deleteIfExists(it) }
    }
  }

  private fun sanitizeForCachePath(resourceRoot: String): String {
    return resourceRoot
      .replace('/', '_')
      .replace('\\', '_')
      .replace(':', '_')
  }

  private inline fun <T> withResourceRootPath(resourceRoot: String, classLoader: ClassLoader, action: (Path) -> T): T {
    val resourceUrl = classLoader.getResource(resourceRoot)
                      ?: throw IOException("Resource root not found: $resourceRoot")

    return when (resourceUrl.protocol) {
      "file" -> action(Path.of(resourceUrl.toURI()))
      "jar" -> withJarResourceRoot(resourceUrl, action)
      else -> throw IOException("Unsupported resource protocol '${resourceUrl.protocol}' for $resourceRoot")
    }
  }

  private inline fun <T> withJarResourceRoot(resourceUrl: URL, action: (Path) -> T): T {
    val spec = resourceUrl.toURI().toString()
    val separatorIndex = spec.indexOf("!/")
    if (separatorIndex < 0) {
      throw IOException("Invalid jar resource URL: $resourceUrl")
    }

    val jarUri = URI.create(spec.substring(0, separatorIndex))
    val pathInJar = spec.substring(separatorIndex + 1)

    val (fs, shouldClose) = openOrReuseFileSystem(jarUri)
    return try {
      action(fs.getPath(pathInJar))
    }
    finally {
      if (shouldClose) {
        fs.close()
      }
    }
  }

  private fun openOrReuseFileSystem(jarUri: URI): Pair<FileSystem, Boolean> {
    return try {
      Pair(FileSystems.getFileSystem(jarUri), false)
    }
    catch (_: FileSystemNotFoundException) {
      Pair(FileSystems.newFileSystem(jarUri, emptyMap<String, Any>()), true)
    }
  }
}
