package io.github.barsia.speqa.registry

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class SpeqaTagRegistryIndexTest : BasePlatformTestCase() {

    fun `test registry indexes normalized tag and environment values for test cases and test runs`() {
        val baseDir = project.guessProjectDir() ?: error("Missing project dir")
        lateinit var authCaseFile: com.intellij.openapi.vfs.VirtualFile
        lateinit var apiCaseFile: com.intellij.openapi.vfs.VirtualFile
        lateinit var authRunFile: com.intellij.openapi.vfs.VirtualFile

        runWriteAction {
            val testCasesDir = VfsUtil.createDirectoryIfMissing(baseDir, "test-cases") ?: error("Missing test-cases dir")
            val testRunsDir = VfsUtil.createDirectoryIfMissing(baseDir, "test-runs") ?: error("Missing test-runs dir")

            authCaseFile = testCasesDir.createChildData(this, "auth.tc.md")
            VfsUtil.saveText(
                authCaseFile,
                """
                |---
                |title: "Auth"
                |tags: auth, smoke
                |environment: "Chrome 120, macOS 14"
                |---
                """.trimMargin(),
            )
            apiCaseFile = testCasesDir.createChildData(this, "api.tc.md")
            VfsUtil.saveText(
                apiCaseFile,
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
            authRunFile = testRunsDir.createChildData(this, "auth.tr.md")
            VfsUtil.saveText(
                authRunFile,
                """
                |---
                |title: "Auth Run"
                |tags: auth
                |environment:
                |  - browser
                |  - staging
                |started_at: 2026-04-12T10:00:00
                |result: passed
                |---
                """.trimMargin(),
            )
        }

        val registry = SpeqaTagRegistry(project)
        registry.indexFile(authCaseFile, isTestRun = false)
        registry.indexFile(apiCaseFile, isTestRun = false)
        registry.indexFile(authRunFile, isTestRun = true)
        registry.markInitializedForTest()

        assertEquals(listOf("api.tc.md", "auth.tc.md"), registry.findTestCasesByTag("auth").map { it.name })
        assertEquals(listOf("auth.tr.md"), registry.findTestRunsByTag("auth").map { it.name })
        assertEquals(listOf("auth.tc.md"), registry.findTestCasesByEnvironment("Chrome 120, macOS 14").map { it.name })
        assertEquals(listOf("auth.tr.md"), registry.findTestRunsByEnvironment("browser").map { it.name })
        assertEquals(listOf("auth.tr.md"), registry.findTestRunsByEnvironment("staging").map { it.name })
    }
}

private fun SpeqaTagRegistry.indexFile(file: com.intellij.openapi.vfs.VirtualFile, isTestRun: Boolean) {
    val indexMethod = javaClass.getDeclaredMethod(
        "extractTagsAndEnvironments",
        com.intellij.openapi.vfs.VirtualFile::class.java,
        java.lang.Boolean.TYPE,
    )
    indexMethod.isAccessible = true
    indexMethod.invoke(this, file, isTestRun)
}

private fun SpeqaTagRegistry.markInitializedForTest() {
    val initializedField = javaClass.getDeclaredField("initialized")
    initializedField.isAccessible = true
    initializedField.set(this, true)
}
