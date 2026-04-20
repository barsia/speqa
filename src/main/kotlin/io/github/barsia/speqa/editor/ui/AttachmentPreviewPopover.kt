package io.github.barsia.speqa.editor.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.intellij.openapi.project.Project
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.ImageLoader
import com.intellij.util.ui.ImageUtil
import io.github.barsia.speqa.SpeqaBundle
import io.github.barsia.speqa.editor.AttachmentSupport
import io.github.barsia.speqa.model.Attachment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.jewel.ui.component.Text
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO

// Attachment previews can be re-mounted while the hover host is stabilizing its
// visibility and placement. If image loading were tied to that composable
// lifetime, a slower SVG decode could restart repeatedly and never produce a
// stable frame. Keep the load on the IntelliJ pooled executor, guard it with
// LOAD_IN_FLIGHT, and let the composable poll IMAGE_CACHE until the first bitmap
// is ready.
private val IMAGE_CACHE = java.util.concurrent.ConcurrentHashMap<String, ImageBitmap>()
private val LOAD_IN_FLIGHT = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

private val RASTER_IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "gif", "webp", "bmp")
private val IMAGE_EXTENSIONS = RASTER_IMAGE_EXTENSIONS + "svg"

@Composable
internal fun AttachmentPreviewPopover(
    attachment: Attachment,
    project: Project,
    tcFile: VirtualFile,
    modifier: Modifier = Modifier,
) {
    val extension = attachment.path.substringAfterLast('.', "").lowercase()
    val isImage = extension in IMAGE_EXTENSIONS
    val density = LocalDensity.current
    val maxPreviewWidthPx = remember(density) { with(density) { 320.dp.roundToPx() } }
    val maxPreviewHeightPx = remember(density) { with(density) { 240.dp.roundToPx() } }
    Box(modifier = modifier) {
        if (isImage) {
            // Reserve a fixed 320x240 slot so the popover does not resize when
            // the bitmap finishes loading. In narrow (single-column) layouts the
            // container would otherwise grow from the loading placeholder to the
            // image size, moving the popover out from under the cursor and
            // triggering a hide/show loop that reads as flicker.
            var image by remember(attachment.path) {
                mutableStateOf(IMAGE_CACHE[attachment.path])
            }
            LaunchedEffect(attachment.path) {
                IMAGE_CACHE[attachment.path]?.let {
                    image = it
                    return@LaunchedEffect
                }
                if (LOAD_IN_FLIGHT.add(attachment.path)) {
                    com.intellij.openapi.application.ApplicationManager.getApplication()
                        .executeOnPooledThread {
                            try {
                                val result = kotlinx.coroutines.runBlocking {
                                    loadImage(project, tcFile, attachment)
                                }
                                if (result != null) IMAGE_CACHE[attachment.path] = result
                            } catch (_: Throwable) {
                            } finally {
                                LOAD_IN_FLIGHT.remove(attachment.path)
                            }
                        }
                }
                // Poll the cache until the pool task populates it (or this
                // LaunchedEffect gets cancelled by another remount — the pool
                // task keeps running regardless).
                while (image == null) {
                    kotlinx.coroutines.delay(50)
                    image = IMAGE_CACHE[attachment.path]
                }
            }
            val bitmap = image
            val previewSize = remember(bitmap, maxPreviewWidthPx, maxPreviewHeightPx) {
                if (bitmap == null) {
                    IntSize(maxPreviewWidthPx, maxPreviewHeightPx)
                } else {
                    calculateAttachmentPreviewSize(
                        imageSize = IntSize(bitmap.width, bitmap.height),
                        maxWidthPx = maxPreviewWidthPx,
                        maxHeightPx = maxPreviewHeightPx,
                    )
                }
            }
            val previewWidthDp = with(density) { previewSize.width.toDp() }
            val previewHeightDp = with(density) { previewSize.height.toDp() }
            Box(
                modifier = Modifier.size(previewWidthDp, previewHeightDp),
                contentAlignment = Alignment.Center,
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = attachment.path.substringAfterLast('/'),
                        modifier = Modifier.size(previewWidthDp, previewHeightDp),
                    )
                } else {
                    Text(
                        SpeqaBundle.message("attachment.preview.loading"),
                        color = SpeqaThemeColors.mutedForeground,
                        fontSize = SpeqaTypography.metaFontSize,
                    )
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(SpeqaLayout.s1)) {
                Text(
                    attachment.path.substringAfterLast('/'),
                    color = SpeqaThemeColors.foreground,
                    fontSize = SpeqaTypography.bodyFontSize,
                )
                val resolved = remember(attachment.path) {
                    AttachmentSupport.resolveFile(project, tcFile, attachment)
                }
                if (resolved != null) {
                    val sizeKb = (resolved.length / 1024).coerceAtLeast(1)
                    Text(
                        SpeqaBundle.message("attachment.info.size", sizeKb),
                        color = SpeqaThemeColors.mutedForeground,
                        fontSize = SpeqaTypography.metaFontSize,
                    )
                }
            }
        }
    }
}

private suspend fun loadImage(
    project: Project,
    tcFile: VirtualFile,
    attachment: Attachment,
): ImageBitmap? = withContext(Dispatchers.IO) {
    @Suppress("DEPRECATION")
    val file = runReadAction<VirtualFile?> {
        AttachmentSupport.resolveFile(project, tcFile, attachment)
    } ?: return@withContext null
    val extension = attachment.path.substringAfterLast('.', "").lowercase()
    try {
        if (extension == "svg") {
            val url = VfsUtilCore.virtualToIoFile(file).toURI().toURL()
            val img = ImageLoader.loadFromUrl(url) ?: return@withContext null
            ImageUtil.toBufferedImage(img).toComposeImageBitmap()
        } else {
            @Suppress("DEPRECATION")
            val bytes = runReadAction<ByteArray> { file.contentsToByteArray() }
            val awt = ImageIO.read(ByteArrayInputStream(bytes)) ?: return@withContext null
            awt.toComposeImageBitmap()
        }
    } catch (t: Throwable) {
        null
    }
}
