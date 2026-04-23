package io.github.barsia.speqa.editor.ui.attachments

import org.junit.Assert.assertEquals
import org.junit.Test

class AttachmentPreviewTypeTest {
    @Test
    fun `png classifies as image`() {
        assertEquals(AttachmentPreviewType.IMAGE, previewTypeFor("attachments/case/shot.png"))
    }

    @Test
    fun `jpeg classifies as image`() {
        assertEquals(AttachmentPreviewType.IMAGE, previewTypeFor("a.JPEG"))
    }

    @Test
    fun `md classifies as markdown`() {
        assertEquals(AttachmentPreviewType.MARKDOWN, previewTypeFor("notes.md"))
    }

    @Test
    fun `tc dot md classifies as markdown`() {
        assertEquals(AttachmentPreviewType.MARKDOWN, previewTypeFor("cases/login.tc.md"))
    }

    @Test
    fun `tr dot md classifies as markdown`() {
        assertEquals(AttachmentPreviewType.MARKDOWN, previewTypeFor("runs/2026-04-21.tr.md"))
    }

    @Test
    fun `txt classifies as text`() {
        assertEquals(AttachmentPreviewType.TEXT, previewTypeFor("log.txt"))
    }

    @Test
    fun `json classifies as text`() {
        assertEquals(AttachmentPreviewType.TEXT, previewTypeFor("payload.json"))
    }

    @Test
    fun `pdf classifies as other`() {
        assertEquals(AttachmentPreviewType.OTHER, previewTypeFor("spec.pdf"))
    }

    @Test
    fun `no extension classifies as other`() {
        assertEquals(AttachmentPreviewType.OTHER, previewTypeFor("README"))
    }

    @Test
    fun `svg classifies as other because swing popover has no svg decoder`() {
        assertEquals(AttachmentPreviewType.OTHER, previewTypeFor("icon.svg"))
    }
}
