package io.github.barsia.speqa.editor.ui.chips

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MetadataActionsTest {
    @Test
    fun `truncate empty string`() {
        assertEquals("", truncateWithEllipsis("", 5))
    }

    @Test
    fun `truncate shorter string is unchanged`() {
        assertEquals("abc", truncateWithEllipsis("abc", 5))
    }

    @Test
    fun `truncate exactly maxChars is unchanged`() {
        assertEquals("abcde", truncateWithEllipsis("abcde", 5))
    }

    @Test
    fun `truncate longer string adds ellipsis within budget`() {
        val result = truncateWithEllipsis("abcdefghij", 5)
        assertEquals("abcd…", result)
        assertTrue(result.length <= 5)
    }

    @Test
    fun `truncate trims trailing whitespace before ellipsis`() {
        assertEquals("abc…", truncateWithEllipsis("abc     defghi", 6))
    }

    @Test
    fun `indexed file match uses raw title when non-blank`() {
        val display = indexedFileMatchFrom(
            idText = "TC-7",
            titleRaw = "Login flow",
            fallbackTitle = "login.tc.md",
            relativePath = "cases/auth/login.tc.md",
            isCurrent = true,
        )
        assertEquals("TC-7", display.idText)
        assertEquals("Login flow", display.titleText)
        assertEquals("cases/auth/login.tc.md", display.pathText)
        assertTrue(display.isCurrent)
    }

    @Test
    fun `indexed file match falls back to file name when title blank`() {
        val display = indexedFileMatchFrom(
            idText = null,
            titleRaw = "   ",
            fallbackTitle = "login.tc.md",
            relativePath = "cases/auth/login.tc.md",
            isCurrent = false,
        )
        assertEquals("login.tc.md", display.titleText)
        assertFalse(display.isCurrent)
    }

    @Test
    fun `indexed file match truncates long title and path`() {
        val longTitle = "x".repeat(200)
        val longPath = "p/".repeat(80) + "file.tc.md"
        val display = indexedFileMatchFrom(
            idText = null,
            titleRaw = longTitle,
            fallbackTitle = "file.tc.md",
            relativePath = longPath,
            isCurrent = false,
            titleTruncation = 20,
            pathTruncation = 30,
        )
        assertTrue(display.titleText.length <= 20)
        assertTrue(display.titleText.endsWith("…"))
        assertTrue(display.pathText.length <= 30)
        assertTrue(display.pathText.endsWith("…"))
    }
}
