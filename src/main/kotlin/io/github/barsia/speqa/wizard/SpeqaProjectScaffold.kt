package io.github.barsia.speqa.wizard

import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile

internal object SpeqaProjectScaffold {
    const val TEST_CASES_DIR = "test-cases"
    const val TEST_RUNS_DIR = "test-runs"

    /**
     * Repo-relative path of the single starter test case the wizard installs into a
     * new project; also its path under the bundled `/templates/` resources.
     * [SpeqaProjectScaffoldTest] guards that this resource stays bundled and parseable.
     */
    const val BUNDLED_SAMPLE_PATH = "test-cases/smoke/plugin-installation.tc.md"

    fun generate(baseDir: VirtualFile): VirtualFile? {
        VfsUtil.createDirectoryIfMissing(baseDir, TEST_RUNS_DIR)

        val content = readBundledSample() ?: return null
        val dirPath = BUNDLED_SAMPLE_PATH.substringBeforeLast('/')
        val fileName = BUNDLED_SAMPLE_PATH.substringAfterLast('/')
        val dir = VfsUtil.createDirectoryIfMissing(baseDir, dirPath) ?: return null
        val tcFile = dir.findChild(fileName) ?: dir.createChildData(this, fileName)
        VfsUtil.saveText(tcFile, content)
        return tcFile
    }

    private fun readBundledSample(): String? =
        SpeqaProjectScaffold::class.java
            .getResourceAsStream("/templates/$BUNDLED_SAMPLE_PATH")
            ?.readBytes()
            ?.toString(java.nio.charset.StandardCharsets.UTF_8)

    fun installSkill(baseDir: VirtualFile) {
        val skillContent = SpeqaProjectScaffold::class.java
            .getResourceAsStream("/templates/speqa-test-cases-skill.md")
            ?.readBytes()
            ?.toString(java.nio.charset.StandardCharsets.UTF_8) ?: return

        val skillDir = VfsUtil.createDirectoryIfMissing(baseDir, ".claude/skills/speqa-test-cases") ?: return
        val skillFile = skillDir.findChild("SKILL.md") ?: skillDir.createChildData(this, "SKILL.md")
        VfsUtil.saveText(skillFile, skillContent)
    }

}
