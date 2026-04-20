package io.github.barsia.speqa.editor.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
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
import androidx.compose.ui.unit.dp
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import io.github.barsia.speqa.SpeqaBundle
import io.github.barsia.speqa.editor.AttachmentSupport
import io.github.barsia.speqa.model.Attachment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.jewel.ui.component.Text
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO

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
    Box(modifier = modifier) {
        if (isImage) {
            var image by remember(attachment.path) { mutableStateOf<ImageBitmap?>(null) }
            LaunchedEffect(attachment.path) {
                image = loadImage(project, tcFile, attachment)
            }
            val bitmap = image
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = attachment.path.substringAfterLast('/'),
                    modifier = Modifier.sizeIn(maxWidth = 320.dp, maxHeight = 240.dp),
                )
            } else {
                Box(modifier = Modifier.size(200.dp, 120.dp), contentAlignment = Alignment.Center) {
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
    val file = ReadAction.compute<VirtualFile?, RuntimeException> {
        AttachmentSupport.resolveFile(project, tcFile, attachment)
    } ?: return@withContext null
    val bytes = ReadAction.compute<ByteArray, RuntimeException> {
        file.contentsToByteArray()
    }
    val extension = attachment.path.substringAfterLast('.', "").lowercase()
    try {
        if (extension == "svg") {
            val awt = com.intellij.util.SVGLoader.load(null, ByteArrayInputStream(bytes), 2f) as? java.awt.image.BufferedImage
                ?: return@withContext null
            awt.toComposeImageBitmap()
        } else {
            val awt = ImageIO.read(ByteArrayInputStream(bytes)) ?: return@withContext null
            awt.toComposeImageBitmap()
        }
    } catch (t: Throwable) {
        null
    }
}
