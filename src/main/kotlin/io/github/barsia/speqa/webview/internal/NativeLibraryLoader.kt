package io.github.barsia.speqa.webview.internal

import com.intellij.openapi.application.PathManager
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

internal object NativeLibraryLoader {
  fun findBundledLibrary(
    candidateFileNames: List<String>,
    resourceDirectory: String,
    ownerClass: Class<*>,
  ): Path? {
    for (fileName in candidateFileNames) {
      val resourcePath = "$resourceDirectory/$fileName"
      val resource = ownerClass.classLoader.getResource(resourcePath) ?: continue
      if (resource.protocol == "file") {
        runCatching { Path.of(resource.toURI()) }
          .getOrNull()
          ?.takeIf { Files.isRegularFile(it) }
          ?.let { return it }
      }

      val extracted = extractResource(resourcePath, fileName, ownerClass) ?: continue
      if (Files.isRegularFile(extracted)) return extracted
    }
    return null
  }

  fun bundledResourceDescriptions(candidateFileNames: List<String>, resourceDirectory: String): List<String> {
    return candidateFileNames.map { "$resourceDirectory/$it" }
  }

  private fun extractResource(resourcePath: String, fileName: String, ownerClass: Class<*>): Path? {
    val bytes = ownerClass.classLoader.getResourceAsStream(resourcePath)?.use { it.readBytes() } ?: return null
    val targetDir = Path.of(PathManager.getTempPath()).resolve("speqa-webview-native")
    Files.createDirectories(targetDir)
    val prefix = fileName.substringBeforeLast('.', fileName)
    val suffixRaw = fileName.substringAfterLast('.', "")
    val suffix = if (suffixRaw.isEmpty()) "" else ".$suffixRaw"
    val shortHash = sha256Hex(bytes).substring(0, 16)
    val target = targetDir.resolve("$prefix-$shortHash$suffix")

    if (Files.isRegularFile(target) && Files.size(target) == bytes.size.toLong()) {
      return target
    }

    val tmp = Files.createTempFile(targetDir, "$prefix-$shortHash", "$suffix.tmp")
    try {
      Files.write(tmp, bytes)
      try {
        Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
      } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING)
      }
    } finally {
      Files.deleteIfExists(tmp)
    }
    return target
  }

  private fun sha256Hex(bytes: ByteArray): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
    val sb = StringBuilder(digest.size * 2)
    for (b in digest) {
      val v = b.toInt() and 0xff
      sb.append(HEX_CHARS[v ushr 4])
      sb.append(HEX_CHARS[v and 0x0f])
    }
    return sb.toString()
  }

  private val HEX_CHARS = "0123456789abcdef".toCharArray()
}
