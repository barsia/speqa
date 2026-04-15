# Test Case Writer Skill Installation Feature

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Allow users to install the test-case-writer skill into their project via Tools menu or automatically during project creation.

**Architecture:** Bundle SKILL.md as a plugin resource. Two entry points: (1) Tools menu action copies it to `.claude/skills/test-case-writer/SKILL.md` in the current project, (2) checkbox in project wizard copies it during scaffolding.

**Tech Stack:** Kotlin, IntelliJ Platform SDK, VFS API

---

## File Structure

| File | Action | Responsibility |
|------|--------|----------------|
| `src/main/resources/templates/test-case-writer-skill.md` | Create | Bundled skill file (copy of SKILL.md) |
| `src/main/kotlin/io/github/barsia/speqa/actions/InstallSkillAction.kt` | Create | Tools menu action: copy skill to project |
| `src/main/kotlin/io/github/barsia/speqa/wizard/SpeqaProjectScaffold.kt` | Modify | Add `installSkill()` method |
| `src/main/kotlin/io/github/barsia/speqa/wizard/SpeqaNewProjectWizard.kt` | Modify | Add checkbox to SpeqaAssetsStep |
| `src/main/kotlin/io/github/barsia/speqa/wizard/SpeqaProjectGenerator.kt` | Modify | Pass skill flag to scaffold |
| `src/main/resources/META-INF/plugin.xml` | Modify | Register action + group |
| `src/main/resources/messages/SpeqaBundle.properties` | Modify | Add UI strings |
| `docs/specs/2026-04-06-speqa-design.md` | Modify | Document the feature (spec-first rule) |

---

### Task 1: Bundle the skill file as a plugin resource

**Files:**
- Create: `src/main/resources/templates/test-case-writer-skill.md`

- [ ] **Step 1: Copy the skill file into plugin resources**

Copy the current test-case-writer SKILL.md content into `src/main/resources/templates/test-case-writer-skill.md`. This is the file that will be bundled with the plugin JAR.

```bash
cp ~/.claude/skills/test-case-writer/SKILL.md src/main/resources/templates/test-case-writer-skill.md
```

- [ ] **Step 2: Verify the file is in the right location**

```bash
ls -la src/main/resources/templates/test-case-writer-skill.md
```

Expected: file exists with the skill content.

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/templates/test-case-writer-skill.md
git commit -m "feat: bundle test-case-writer skill as plugin resource"
```

---

### Task 2: Create the Install Skill action (Tools menu)

**Files:**
- Modify: `docs/specs/2026-04-06-speqa-design.md`
- Create: `src/main/kotlin/io/github/barsia/speqa/actions/InstallSkillAction.kt`
- Modify: `src/main/resources/META-INF/plugin.xml`
- Modify: `src/main/resources/messages/SpeqaBundle.properties`

- [ ] **Step 1: Update the spec**

Add to `docs/specs/2026-04-06-speqa-design.md` under an appropriate section:

```markdown
### Install Test Case Writer Skill

- **Tools > SpeQA > Install Test Case Writer Skill** copies bundled `test-case-writer-skill.md` to `.claude/skills/test-case-writer/SKILL.md` in the current project.
- If the file already exists, shows a confirmation dialog before overwriting.
- Shows a balloon notification on success or failure.
- Action is only enabled when a project is open.
```

- [ ] **Step 2: Add bundle strings**

Add to `src/main/resources/messages/SpeqaBundle.properties`:

```properties
# --- Install Skill ---
action.Speqa.InstallSkill.text=Install Test Case Writer Skill
action.Speqa.InstallSkill.description=Copy test-case-writer skill for Claude Code into this project
skill.install.success=Test case writer skill installed to {0}
skill.install.failed=Failed to install skill: {0}
skill.install.overwrite.title=Skill Already Exists
skill.install.overwrite.message=Test case writer skill already exists in this project. Overwrite?
skill.install.notification.group=SpeQA
```

- [ ] **Step 3: Create InstallSkillAction.kt**

Create `src/main/kotlin/io/github/barsia/speqa/actions/InstallSkillAction.kt`:

```kotlin
package io.github.barsia.speqa.actions

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VfsUtil
import io.github.barsia.speqa.SpeqaBundle
import java.nio.charset.StandardCharsets

class InstallSkillAction : AnAction(), DumbAware {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val basePath = project.basePath ?: return

        val skillContent = javaClass.getResourceAsStream("/templates/test-case-writer-skill.md")
            ?.readBytes()
            ?.toString(StandardCharsets.UTF_8) ?: run {
            notify(project, SpeqaBundle.message("skill.install.failed", "Resource not found"), NotificationType.ERROR)
            return
        }

        val targetPath = "$basePath/.claude/skills/test-case-writer"
        val targetFile = "$targetPath/SKILL.md"

        // Check if already exists
        val existingDir = VfsUtil.findFileByIoFile(java.io.File(targetFile), true)
        if (existingDir != null) {
            val result = Messages.showYesNoDialog(
                project,
                SpeqaBundle.message("skill.install.overwrite.message"),
                SpeqaBundle.message("skill.install.overwrite.title"),
                Messages.getQuestionIcon(),
            )
            if (result != Messages.YES) return
        }

        try {
            runWriteAction {
                val dir = VfsUtil.createDirectoryIfMissing(targetPath) ?: error("Cannot create directory")
                val file = dir.findChild("SKILL.md")?.also { VfsUtil.saveText(it, skillContent) }
                    ?: dir.createChildData(this, "SKILL.md").also { VfsUtil.saveText(it, skillContent) }
            }
            val relativePath = ".claude/skills/test-case-writer/SKILL.md"
            notify(project, SpeqaBundle.message("skill.install.success", relativePath), NotificationType.INFORMATION)
        } catch (ex: Exception) {
            notify(project, SpeqaBundle.message("skill.install.failed", ex.message ?: "Unknown error"), NotificationType.ERROR)
        }
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = e.project?.basePath != null
    }

    private fun notify(project: Project, content: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("SpeQA")
            .createNotification(content, type)
            .notify(project)
    }
}
```

- [ ] **Step 4: Register action and group in plugin.xml**

Add notification group to extensions in `src/main/resources/META-INF/plugin.xml`:

```xml
<notificationGroup id="SpeQA" displayType="BALLOON"/>
```

Add action group and action inside `<actions>`:

```xml
<group id="Speqa.ToolsMenu" text="SpeQA" popup="true">
    <add-to-group group-id="ToolsMenu" anchor="last"/>
    <action id="Speqa.InstallSkill"
            class="io.github.barsia.speqa.actions.InstallSkillAction"/>
</group>
```

- [ ] **Step 5: Verify compilation**

```bash
./gradlew compileKotlin 2>&1 | grep -E "^e:|BUILD"
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/io/github/barsia/speqa/actions/InstallSkillAction.kt
git add src/main/resources/META-INF/plugin.xml
git add src/main/resources/messages/SpeqaBundle.properties
git add docs/specs/2026-04-06-speqa-design.md
git commit -m "feat: add Tools > SpeQA > Install Test Case Writer Skill action"
```

---

### Task 3: Add skill installation to project scaffold

**Files:**
- Modify: `docs/specs/2026-04-06-speqa-design.md`
- Modify: `src/main/kotlin/io/github/barsia/speqa/wizard/SpeqaProjectScaffold.kt`

- [ ] **Step 1: Update spec**

Add to the spec:

```markdown
- `SpeqaProjectScaffold.installSkill(baseDir)` copies the bundled skill to `.claude/skills/test-case-writer/SKILL.md` relative to `baseDir`.
```

- [ ] **Step 2: Add installSkill method to SpeqaProjectScaffold**

Add to `src/main/kotlin/io/github/barsia/speqa/wizard/SpeqaProjectScaffold.kt`:

```kotlin
fun installSkill(baseDir: VirtualFile) {
    val skillContent = SpeqaProjectScaffold::class.java
        .getResourceAsStream("/templates/test-case-writer-skill.md")
        ?.readBytes()
        ?.toString(java.nio.charset.StandardCharsets.UTF_8) ?: return

    val skillDir = VfsUtil.createDirectoryIfMissing(baseDir, ".claude/skills/test-case-writer") ?: return
    val skillFile = skillDir.createChildData(this, "SKILL.md")
    VfsUtil.saveText(skillFile, skillContent)
}
```

- [ ] **Step 3: Verify compilation**

```bash
./gradlew compileKotlin 2>&1 | grep -E "^e:|BUILD"
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/io/github/barsia/speqa/wizard/SpeqaProjectScaffold.kt
git add docs/specs/2026-04-06-speqa-design.md
git commit -m "feat: add installSkill method to SpeqaProjectScaffold"
```

---

### Task 4: Add checkbox to IntelliJ New Project Wizard

**Files:**
- Modify: `docs/specs/2026-04-06-speqa-design.md`
- Modify: `src/main/kotlin/io/github/barsia/speqa/wizard/SpeqaNewProjectWizard.kt`
- Modify: `src/main/resources/messages/SpeqaBundle.properties`

- [ ] **Step 1: Update spec**

Add:

```markdown
- IntelliJ New Project Wizard: SpeqaAssetsStep shows a checkbox "Include test-case-writer skill for Claude Code" (default: checked). When checked, calls `SpeqaProjectScaffold.installSkill(baseDir)` during project setup.
```

- [ ] **Step 2: Add bundle string**

Add to `SpeqaBundle.properties`:

```properties
wizard.includeSkill=Include test-case-writer skill for Claude Code
```

- [ ] **Step 3: Add checkbox to SpeqaAssetsStep**

Modify `SpeqaAssetsStep` in `src/main/kotlin/io/github/barsia/speqa/wizard/SpeqaNewProjectWizard.kt`:

```kotlin
class SpeqaAssetsStep(parent: NewProjectWizardStep) : AbstractNewProjectWizardStep(parent) {

    private val includeSkill = propertyGraph.property(true)

    override fun setupUI(builder: com.intellij.ui.dsl.builder.Panel) {
        builder.row {
            checkBox(SpeqaBundle.message("wizard.includeSkill"))
                .bindSelected(includeSkill)
        }
    }

    override fun setupProject(project: Project) {
        val sampleTestCase = runWriteAction<com.intellij.openapi.vfs.VirtualFile?> {
            val basePath = project.basePath ?: return@runWriteAction null
            val baseDir = VfsUtil.createDirectoryIfMissing(basePath) ?: return@runWriteAction null
            if (includeSkill.get()) {
                SpeqaProjectScaffold.installSkill(baseDir)
            }
            SpeqaProjectScaffold.generate(baseDir)
        }
        if (sampleTestCase != null) {
            openInitialTestCase(project, sampleTestCase)
        }
    }
}
```

- [ ] **Step 4: Verify compilation**

```bash
./gradlew compileKotlin 2>&1 | grep -E "^e:|BUILD"
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/io/github/barsia/speqa/wizard/SpeqaNewProjectWizard.kt
git add src/main/resources/messages/SpeqaBundle.properties
git add docs/specs/2026-04-06-speqa-design.md
git commit -m "feat: add skill checkbox to IntelliJ New Project Wizard"
```

---

### Task 5: Add skill installation to WebStorm/PyCharm project generator

**Files:**
- Modify: `docs/specs/2026-04-06-speqa-design.md`
- Modify: `src/main/kotlin/io/github/barsia/speqa/wizard/SpeqaProjectGenerator.kt`

- [ ] **Step 1: Update spec**

Add:

```markdown
- WebStorm/PyCharm DirectoryProjectGenerator: always installs the skill during project creation (no checkbox UI available in this wizard type).
```

- [ ] **Step 2: Add installSkill call to SpeqaProjectGenerator**

Modify `generateProject` in `src/main/kotlin/io/github/barsia/speqa/wizard/SpeqaProjectGenerator.kt`:

```kotlin
override fun generateProject(
    project: Project,
    baseDir: VirtualFile,
    settings: Any,
    module: Module,
) {
    val sampleTestCase = runWriteAction {
        SpeqaProjectScaffold.installSkill(baseDir)
        SpeqaProjectScaffold.generate(baseDir)
    } ?: return
    openInitialTestCase(project, sampleTestCase)
}
```

- [ ] **Step 3: Verify compilation**

```bash
./gradlew compileKotlin 2>&1 | grep -E "^e:|BUILD"
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/io/github/barsia/speqa/wizard/SpeqaProjectGenerator.kt
git add docs/specs/2026-04-06-speqa-design.md
git commit -m "feat: install skill during WebStorm/PyCharm project creation"
```

---

### Task 6: Run tests and final verification

**Files:** None (verification only)

- [ ] **Step 1: Run all tests**

```bash
./gradlew test 2>&1 | grep -E "FAILED|BUILD"
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 2: Verify plugin builds**

```bash
./gradlew compileKotlin 2>&1 | grep -E "^e:|BUILD"
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Verify resource is bundled**

```bash
./gradlew jar && jar tf build/libs/*.jar | grep test-case-writer
```

Expected: `templates/test-case-writer-skill.md` appears in the JAR listing.

---

## Risks

1. **NotificationGroup registration**: `NotificationGroupManager.getInstance().getNotificationGroup("SpeQA")` requires the group registered in plugin.xml. If missing, runtime NPE.
2. **DirectoryProjectGenerator has no settings UI**: WebStorm's `DirectoryProjectGeneratorBase` doesn't support custom UI panels, so the skill is always installed. Acceptable tradeoff.
3. **Spec-first hook**: Every `.kt` edit needs a preceding spec edit. Tasks are ordered to respect this (spec step comes before code step in each task).
4. **VFS in write action**: All file creation must be inside `runWriteAction`. The scaffold already does this; InstallSkillAction must too.
