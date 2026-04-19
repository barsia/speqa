package io.github.barsia.speqa.wizard

import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile

internal object SpeqaProjectScaffold {
    const val TEST_CASES_DIR = "test-cases"
    const val TEST_RUNS_DIR = "test-runs"

    fun generate(baseDir: VirtualFile): VirtualFile? {
        VfsUtil.createDirectoryIfMissing(baseDir, TEST_CASES_DIR)
        VfsUtil.createDirectoryIfMissing(baseDir, TEST_RUNS_DIR)

        val tcDir = baseDir.findChild(TEST_CASES_DIR) ?: return null

        val tcFile = tcDir.createChildData(this, "sample-login.tc.md")
        VfsUtil.saveText(tcFile, SAMPLE_TEST_CASE)
        return tcFile
    }

    fun installSkill(baseDir: VirtualFile) {
        val skillContent = SpeqaProjectScaffold::class.java
            .getResourceAsStream("/templates/test-case-writer-skill.md")
            ?.readBytes()
            ?.toString(java.nio.charset.StandardCharsets.UTF_8) ?: return

        val skillDir = VfsUtil.createDirectoryIfMissing(baseDir, ".claude/skills/test-case-writer") ?: return
        val skillFile = skillDir.findChild("SKILL.md") ?: skillDir.createChildData(this, "SKILL.md")
        VfsUtil.saveText(skillFile, skillContent)
    }

    private val SAMPLE_TEST_CASE = """
        |---
        |id: 1
        |title: "Login with valid credentials"
        |priority: normal
        |status: draft
        |environment:
        |  - "Chrome 120, macOS 14"
        |tags:
        |  - auth
        |  - smoke
        |---
        |
        |This test case verifies the standard login flow for active users.
        |
        |Preconditions:
        |
        |1. User account exists in the system
        |2. User is on the login page
        |
        |Scenario:
        |
        |1. Type "testuser@example.com" into the email field
        |   > Email field accepts input, no validation errors
        |
        |2. Type "SecureP@ss123" into the password field
        |   > Password is masked, no validation errors
        |
        |3. Click the "Login" button
        |   > User is redirected to the dashboard
    """.trimMargin() + "\n"

}
