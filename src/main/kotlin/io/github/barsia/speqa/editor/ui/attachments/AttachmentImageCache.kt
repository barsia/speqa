@file:Suppress("DEPRECATION")

package io.github.barsia.speqa.editor.ui.attachments

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.ImageLoader
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.ImageUtil
import java.awt.Dimension
import java.awt.Image
import java.awt.image.BufferedImage
import java.util.concurrent.atomic.AtomicLong
import javax.imageio.ImageIO
import javax.swing.SwingUtilities

/**
 * Project-scoped LRU cache for scaled attachment preview images.
 *
 * The cache is keyed by `(VirtualFile.url, modificationStamp, targetWidth, targetHeight)`
 * so any edit to the underlying file automatically invalidates its cached render.
 * Image decoding and scaling run on the shared application executor and the
 * callback is dispatched on the EDT. Only image attachments are cached —
 * Markdown and text previews remain cheap to reload.
 */
@Service(Service.Level.PROJECT)
internal class AttachmentImageCache(@Suppress("UNUSED_PARAMETER") private val project: Project) : Disposable {

    private val cache = LruMap<Key, BufferedImage>(CAPACITY)
    private val slotTokens = HashMap<Any, Long>()
    private val tokenSeq = AtomicLong(0L)
    private val lock = Any()
    @Volatile private var disposed: Boolean = false

    /**
     * Request a scaled image for [file] at [targetSize]. If the pair is already
     * cached, [onReady] is invoked synchronously on the EDT with the cached
     * bitmap. Otherwise the load is queued on a background executor and the
     * result is delivered on the EDT. On failure [onReady] is invoked with
     * `null`.
     *
     * [slotKey] identifies the logical destination (typically the popup
     * instance). A newer request for the same slot cancels the callback of any
     * in-flight prior request for that slot.
     */
    fun request(
        file: VirtualFile,
        targetSize: Dimension,
        slotKey: Any,
        onReady: (BufferedImage?) -> Unit,
    ) {
        val stamp = file.modificationStamp
        val key = Key(file.url, stamp, targetSize.width, targetSize.height)

        val cached = synchronized(lock) { cache.get(key) }
        val token = synchronized(lock) {
            val t = tokenSeq.incrementAndGet()
            slotTokens[slotKey] = t
            t
        }
        if (cached != null) {
            SwingUtilities.invokeLater {
                if (isCurrent(slotKey, token)) onReady(cached)
            }
            return
        }

        AppExecutorUtil.getAppExecutorService().execute {
            if (disposed) {
                SwingUtilities.invokeLater { if (isCurrent(slotKey, token)) onReady(null) }
                return@execute
            }
            val loaded: BufferedImage? = try {
                val raw: BufferedImage? = if (file.extension?.lowercase() == "svg") {
                    val url = VfsUtilCore.virtualToIoFile(file).toURI().toURL()
                    ImageLoader.loadFromUrl(url)?.let { ImageUtil.toBufferedImage(it) }
                } else {
                    val bytes = runReadAction<ByteArray> { file.contentsToByteArray() }
                    ImageIO.read(bytes.inputStream())
                }
                if (raw != null) scaleToFit(raw, targetSize) else null
            } catch (_: Throwable) {
                null
            }
            if (loaded != null) {
                synchronized(lock) { cache.put(key, loaded) }
            }
            SwingUtilities.invokeLater {
                if (isCurrent(slotKey, token)) onReady(loaded)
            }
        }
    }

    private fun isCurrent(slotKey: Any, token: Long): Boolean {
        if (disposed) return false
        synchronized(lock) {
            return slotTokens[slotKey] == token
        }
    }

    override fun dispose() {
        disposed = true
        synchronized(lock) {
            cache.clear()
            slotTokens.clear()
        }
    }

    internal data class Key(
        val url: String,
        val modificationStamp: Long,
        val width: Int,
        val height: Int,
    )

    companion object {
        /** Entry-count cap for the LRU. 32 scaled previews comfortably covers any one editor session. */
        internal const val CAPACITY: Int = 32

        fun getInstance(project: Project): AttachmentImageCache = project.service()
    }
}

/**
 * Minimal access-order LRU keyed map. Not thread-safe; callers synchronize.
 * Extracted to keep the cache's data-structure semantics unit-testable in
 * isolation from threading and the IntelliJ platform.
 */
internal class LruMap<K, V>(private val capacity: Int) {
    init { require(capacity > 0) { "capacity must be positive" } }

    private val backing = object : LinkedHashMap<K, V>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: Map.Entry<K, V>): Boolean = size > capacity
    }

    fun get(key: K): V? = backing[key]

    fun put(key: K, value: V): V? = backing.put(key, value)

    val size: Int get() = backing.size

    fun keysInOrder(): List<K> = backing.keys.toList()

    fun clear() { backing.clear() }
}

/** Aspect-preserving scale to fit inside [target], painting into an ARGB buffer for Swing. */
private fun scaleToFit(source: BufferedImage, target: Dimension): BufferedImage {
    val (w, h) = fitSize(source.width, source.height, target.width, target.height)
    val scaled = source.getScaledInstance(w, h, Image.SCALE_SMOOTH)
    val result = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
    val g = result.createGraphics()
    try {
        g.drawImage(scaled, 0, 0, null)
    } finally {
        g.dispose()
    }
    return result
}

private fun fitSize(iw: Int, ih: Int, maxW: Int, maxH: Int): Pair<Int, Int> {
    if (iw <= 0 || ih <= 0) return maxW to maxH
    val s = minOf(1f, maxW.toFloat() / iw, maxH.toFloat() / ih)
    return (iw * s).toInt().coerceAtLeast(1) to (ih * s).toInt().coerceAtLeast(1)
}
