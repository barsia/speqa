package io.github.barsia.speqa.error

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeqaStacktraceSanitizerTest {

    @Test
    fun `extracts class name, method name, and line number from throwable`() {
        val exception = RuntimeException("secret message with /Users/john/project/file.kt")
        val result = SpeqaStacktraceSanitizer.sanitize(exception)

        assertTrue(result.exceptionClass == "java.lang.RuntimeException")
        assertTrue(result.frames.isNotEmpty())
        val frame = result.frames.first()
        assertTrue(frame.module.isNotEmpty())
        assertTrue(frame.function.isNotEmpty())
        assertTrue(frame.lineno > 0)
    }

    @Test
    fun `exception message is not included in result`() {
        val exception = RuntimeException("/Users/john/secrets/password.txt")
        val result = SpeqaStacktraceSanitizer.sanitize(exception)

        assertEquals("", result.exceptionMessage)
    }

    @Test
    fun `file paths are not present in any frame`() {
        val exception = RuntimeException("test")
        val result = SpeqaStacktraceSanitizer.sanitize(exception)

        for (frame in result.frames) {
            assertTrue("module should not contain /", !frame.module.contains("/"))
            assertTrue("module should not contain \\", !frame.module.contains("\\"))
            assertTrue("function should not contain /", !frame.function.contains("/"))
        }
    }

    @Test
    fun `handles chained exceptions`() {
        val cause = IllegalArgumentException("root cause with /home/user/data")
        val exception = RuntimeException("wrapper", cause)
        val result = SpeqaStacktraceSanitizer.sanitize(exception)

        assertEquals("java.lang.RuntimeException", result.exceptionClass)
        assertTrue(result.frames.isNotEmpty())
    }

    @Test
    fun `handles exception with no stacktrace`() {
        val exception = object : RuntimeException("no stack") {
            override fun getStackTrace(): Array<StackTraceElement> = emptyArray()
        }
        val result = SpeqaStacktraceSanitizer.sanitize(exception)

        assertEquals("", result.exceptionMessage)
        assertTrue(result.frames.isEmpty())
    }
}
