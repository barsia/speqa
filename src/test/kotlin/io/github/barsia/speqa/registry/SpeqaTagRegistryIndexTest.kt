package io.github.barsia.speqa.registry

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.util.concurrent.TimeUnit

class SpeqaTagRegistryIndexTest : BasePlatformTestCase() {

    fun `test registry indexes normalized tag and environment values for test cases and test runs`() {
        val basePath = project.basePath ?: error("Missing project base path")
        val baseDir = VfsUtil.createDirectoryIfMissing(basePath) ?: error("Missing project dir")

        runWriteAction {
            val testCasesDir = VfsUtil.createDirectoryIfMissing(baseDir, "test-cases") ?: error("Missing test-cases dir")
            val testRunsDir = VfsUtil.createDirectoryIfMissing(baseDir, "test-runs") ?: error("Missing test-runs dir")

            VfsUtil.saveText(
                testCasesDir.createChildData(this, "auth.tc.md"),
                """
                |---
                |title: "Auth"
                |tags: auth, smoke
                |environment: "Chrome 120, macOS 14"
                |---
                """.trimMargin(),
            )
            VfsUtil.saveText(
                testCasesDir.createChildData(this, "api.tc.md"),
                """
                |---
                |title: "API"
                |tags:
                |  - auth
                |environment:
                |  - API
                |  - Staging
                |---
                """.trimMargin(),
            )
            VfsUtil.saveText(
                testRunsDir.createChildData(this, "auth.tr.md"),
                """
                |---
                |title: "Auth Run"
                |tags: auth
                |environment: browser, staging
                |started_at: 2026-04-12T10:00:00
                |result: passed
                |---
                """.trimMargin(),
            )
        }

        val registry = SpeqaTagRegistry(project)
        registry.ensureInitialized()
        waitFor("tag registry to index files", 5_000) {
            registry.findTestCasesByTag("auth").map { it.name } == listOf("api.tc.md", "auth.tc.md")
        }

        assertEquals(listOf("api.tc.md", "auth.tc.md"), registry.findTestCasesByTag("auth").map { it.name })
        assertEquals(listOf("auth.tr.md"), registry.findTestRunsByTag("auth").map { it.name })
        assertEquals(listOf("auth.tc.md"), registry.findTestCasesByEnvironment("Chrome 120, macOS 14").map { it.name })
        assertEquals(listOf("auth.tr.md"), registry.findTestRunsByEnvironment("browser").map { it.name })
        assertEquals(listOf("auth.tr.md"), registry.findTestRunsByEnvironment("staging").map { it.name })
    }

    private fun waitFor(message: String, timeoutMs: Long, condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs)
        while (System.nanoTime() < deadline) {
            if (condition()) return
            Thread.sleep(25)
        }
        fail("Timed out waiting for $message")
    }
}
