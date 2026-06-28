package io.github.barsia.speqa.wizard

import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile

internal object SpeqaProjectScaffold {
    const val TEST_CASES_DIR = "test-cases"
    const val TEST_RUNS_DIR = "test-runs"

    /**
     * Path under the bundled `/templates/` resources of the starter template the wizard
     * installs. It keeps a `.tc.md.template` suffix so SpeQA does not index it as a test
     * case in this repo; the wizard strips `.template` when writing it into a new project.
     * [SpeqaProjectScaffoldTest] guards that this resource stays bundled.
     */
    const val BUNDLED_SAMPLE_RESOURCE = "test-cases/login-happy-path.tc.md.template"

    fun generate(baseDir: VirtualFile): VirtualFile? {
        VfsUtil.createDirectoryIfMissing(baseDir, TEST_RUNS_DIR)

        val content = readBundledSample() ?: return null
        val dirPath = BUNDLED_SAMPLE_RESOURCE.substringBeforeLast('/')
        val fileName = BUNDLED_SAMPLE_RESOURCE.substringAfterLast('/').removeSuffix(".template")
        val dir = VfsUtil.createDirectoryIfMissing(baseDir, dirPath) ?: return null
        val tcFile = dir.findChild(fileName) ?: dir.createChildData(this, fileName)
        VfsUtil.saveText(tcFile, content)
        return tcFile
    }

    private fun readBundledSample(): String? =
        SpeqaProjectScaffold::class.java
            .getResourceAsStream("/templates/$BUNDLED_SAMPLE_RESOURCE")
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
