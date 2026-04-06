package io.github.barsia.speqa.editor

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class HeaderMetadataResolverTest {

    @Test
    fun `created at resolver caches repeated lookups for unchanged file`() {
        var invocations = 0
        val expected = Instant.parse("2026-04-12T10:00:00Z")
        val resolver = CreatedAtResolver(
            resolveCreatedAt = { _, _ ->
                invocations += 1
                expected
            },
        )

        val first = resolver.resolve("project-root", "/tmp/case.tc.md", 123L)
        val second = resolver.resolve("project-root", "/tmp/case.tc.md", 123L)

        assertEquals(expected, first)
        assertEquals(expected, second)
        assertEquals(1, invocations)
    }

    @Test
    fun `created at resolver invalidates cache when timestamp changes`() {
        var invocations = 0
        val resolver = CreatedAtResolver(
            resolveCreatedAt = { _, _ ->
                invocations += 1
                Instant.ofEpochSecond(invocations.toLong())
            },
        )

        val first = resolver.resolve("project-root", "/tmp/case.tc.md", 123L)
        val second = resolver.resolve("project-root", "/tmp/case.tc.md", 124L)

        assertEquals(Instant.ofEpochSecond(1), first)
        assertEquals(Instant.ofEpochSecond(2), second)
        assertEquals(2, invocations)
    }
}
