@file:Suppress("DEPRECATION")

package io.github.barsia.speqa.editor.ui.attachments

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import io.github.barsia.speqa.SpeqaBundle
import io.github.barsia.speqa.editor.AttachmentSupport
import io.github.barsia.speqa.editor.ui.primitives.MarkdownReadOnlyPane
import io.github.barsia.speqa.model.Attachment
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.RoundRectangle2D
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingConstants

/** Max displayed bytes for non-markdown text preview. */
private const val TEXT_PREVIEW_BUDGET_BYTES: Int = 64 * 1024

private val PREVIEW_MAX_WIDTH_PX: Int get() = JBUI.scale(320)
private val PREVIEW_MAX_HEIGHT_PX: Int get() = JBUI.scale(240)

/**
 * Builds a JBPopup showing a lightweight preview of [attachment]. The popup
 * is cancel-on-click-outside and shows no shadow. Caller controls showing via
 * [JBPopup.showInScreenCoordinates] / `showUnderneathOf`.
 */
internal fun attachmentPreviewPopover(
    attachment: Attachment,
    project: Project,
    tcFile: VirtualFile,
): JBPopup {
    val content = buildPreviewContent(attachment, project, tcFile)
    return JBPopupFactory.getInstance()
        .createComponentPopupBuilder(content, null)
        .setRequestFocus(false)
        .setCancelOnClickOutside(true)
        .setCancelOnWindowDeactivation(true)
        .setFocusable(false)
        .setMovable(false)
        .setResizable(false)
        .createPopup()
}

private fun buildPreviewContent(
    attachment: Attachment,
    project: Project,
    tcFile: VirtualFile,
): JComponent {
    val fileName = attachment.path.substringAfterLast('/')
    val resolved: VirtualFile? = runReadAction { AttachmentSupport.resolveFile(project, tcFile, attachment) }
    if (resolved == null) {
        return mutedInfoPanel(fileName, null)
    }
    return when (previewTypeFor(attachment.path)) {
        AttachmentPreviewType.IMAGE -> imagePreview(resolved, project)
        AttachmentPreviewType.MARKDOWN -> markdownPreview(resolved)
        AttachmentPreviewType.TEXT -> textPreview(resolved)
        AttachmentPreviewType.OTHER -> mutedInfoPanel(fileName, resolved.length)
    }
}

/**
 * Builds the image-preview content lazily. The wrapper panel ships with a
 * muted "loading" placeholder sized to the maximum preview bounds so the popup
 * does not resize when the image arrives from the cache/background loader.
 * The actual decoding is delegated to [AttachmentImageCache]; the callback is
 * dispatched on the EDT and no-ops if the wrapper is no longer showing
 * (popup dismissed before load finished).
 */
private fun imagePreview(file: VirtualFile, project: Project): JComponent {
    // Read actual image dimensions synchronously (ImageIO reads only the header,
    // cheap even for large files) so the popup is sized to the image from the start.
    val actual = readImageDimensions(file)
    val target = if (actual != null) {
        val (w, h) = calculateAttachmentPreviewSize(
            actual.width,
            actual.height,
            PREVIEW_MAX_WIDTH_PX,
            PREVIEW_MAX_HEIGHT_PX,
        )
        Dimension(w, h)
    } else {
        Dimension(PREVIEW_MAX_WIDTH_PX, PREVIEW_MAX_HEIGHT_PX)
    }

    var loadedImage: java.awt.image.BufferedImage? = null
    val arc = JBUI.scale(8).toFloat()

    val wrapper = object : JPanel(BorderLayout()) {
        override fun paintComponent(g: Graphics) {
            val img = loadedImage
            if (img != null) {
                val g2 = g as Graphics2D
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                val clip = RoundRectangle2D.Float(0f, 0f, width.toFloat(), height.toFloat(), arc, arc)
                g2.clip(clip)
                val x = (width - img.width) / 2
                val y = (height - img.height) / 2
                g2.drawImage(img, x, y, null)
            } else {
                super.paintComponent(g)
            }
        }
        override fun isOpaque() = false
    }
    wrapper.preferredSize = Dimension(target.width, target.height)

    val placeholder = JBLabel(SpeqaBundle.message("attachment.preview.loading"), SwingConstants.CENTER)
    placeholder.foreground = com.intellij.util.ui.UIUtil.getContextHelpForeground()
    wrapper.add(placeholder, BorderLayout.CENTER)

    AttachmentImageCache.getInstance(project).request(file, target, slotKey = wrapper) { image ->
        if (!wrapper.isShowing && wrapper.parent == null) return@request
        loadedImage = image
        wrapper.removeAll()
        if (image == null) {
            val unavailable = JBLabel(SpeqaBundle.message("attachment.preview.unavailable"), SwingConstants.CENTER)
            unavailable.foreground = com.intellij.util.ui.UIUtil.getContextHelpForeground()
            wrapper.add(unavailable, BorderLayout.CENTER)
        }
        wrapper.revalidate()
        wrapper.repaint()
    }
    return wrapper
}

private fun readImageDimensions(file: VirtualFile): Dimension? {
    return try {
        val bytes = runReadAction<ByteArray> { file.contentsToByteArray() }
        javax.imageio.ImageIO.createImageInputStream(java.io.ByteArrayInputStream(bytes)).use { iis ->
            val readers = javax.imageio.ImageIO.getImageReaders(iis) ?: return null
            if (!readers.hasNext()) return null
            val reader = readers.next()
            try {
                reader.input = iis
                Dimension(reader.getWidth(0), reader.getHeight(0))
            } finally {
                reader.dispose()
            }
        }
    } catch (_: Throwable) {
        null
    }
}

private fun markdownPreview(file: VirtualFile): JComponent {
    val text = runReadAction<String> { String(file.contentsToByteArray(), Charsets.UTF_8) }
    val pane = MarkdownReadOnlyPane()
    pane.setMarkdown(text)
    pane.preferredSize = Dimension(PREVIEW_MAX_WIDTH_PX, PREVIEW_MAX_HEIGHT_PX)
    return pane
}

private fun textPreview(file: VirtualFile): JComponent {
    val bytes = runReadAction<ByteArray> { file.contentsToByteArray() }
    val clipped = if (bytes.size > TEXT_PREVIEW_BUDGET_BYTES) bytes.copyOf(TEXT_PREVIEW_BUDGET_BYTES) else bytes
    val text = String(clipped, Charsets.UTF_8)
    val area = JBTextArea(text)
    area.isEditable = false
    area.font = Font(Font.MONOSPACED, Font.PLAIN, area.font.size)
    area.lineWrap = false
    area.preferredSize = Dimension(PREVIEW_MAX_WIDTH_PX, PREVIEW_MAX_HEIGHT_PX)
    return area
}

private fun mutedInfoPanel(fileName: String, sizeBytes: Long?): JComponent {
    val panel = JPanel()
    panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
    panel.border = JBUI.Borders.empty(8)
    val title = JBLabel(fileName)
    panel.add(title)
    if (sizeBytes != null) {
        val kb = (sizeBytes / 1024).coerceAtLeast(1)
        val sub = JBLabel(SpeqaBundle.message("attachment.info.size", kb))
        sub.foreground = com.intellij.util.ui.UIUtil.getContextHelpForeground()
        panel.add(sub)
    } else {
        val sub = JBLabel(SpeqaBundle.message("attachment.preview.unsupported"))
        sub.foreground = com.intellij.util.ui.UIUtil.getContextHelpForeground()
        panel.add(sub)
    }
    panel.preferredSize = Dimension(PREVIEW_MAX_WIDTH_PX, panel.preferredSize.height.coerceAtLeast(JBUI.scale(60)))
    return panel
}

internal fun calculateAttachmentPreviewSize(
    imageWidth: Int,
    imageHeight: Int,
    maxWidthPx: Int,
    maxHeightPx: Int,
): Pair<Int, Int> {
    if (imageWidth <= 0 || imageHeight <= 0) return maxWidthPx to maxHeightPx
    val widthScale = maxWidthPx.toFloat() / imageWidth
    val heightScale = maxHeightPx.toFloat() / imageHeight
    val scale = minOf(1f, widthScale, heightScale)
    return (imageWidth * scale).toInt().coerceAtLeast(1) to
        (imageHeight * scale).toInt().coerceAtLeast(1)
}
