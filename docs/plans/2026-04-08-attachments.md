# Attachment System Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Allow users to attach local files (images, documents, any type) to test cases, step actions, and step expected results — with drag & drop, file chooser, and manual markdown entry.

**Architecture:** Attachments are stored in a flat folder `attachments/{tc-filename-without-ext}/` next to the `.tc.md` file. In markdown they appear as standard links `[path/to/file]` or `[text](path)`. A new `Attachments:` body section holds general TC attachments; step-level attachments live inline as continuation lines after action or after `>` expected lines. The parser detects attachment links by position, the model carries `List<Attachment>`, and the preview UI renders them as clickable icon+filename rows with hover-preview for images.

**Tech Stack:** Kotlin, Compose/Jewel UI, IntelliJ Platform SDK (VFS, FileChooser, FileEditorManager, refactoring)

---

### Task 1: Data Model — `Attachment` class and model updates

**Files:**
- Modify: `src/main/kotlin/io/speqa/speqa/model/TestCase.kt`

- [ ] **Step 1: Add Attachment data class and update TestStep/TestCase**

```kotlin
// Add to TestCase.kt, before the TestStep data class:

data class Attachment(
    val path: String,  // relative to .tc.md file, e.g. "attachments/sample-login/screenshot.png"
)

// Update TestStep:
data class TestStep(
    val action: String = "",
    val actionAttachments: List<Attachment> = emptyList(),
    val expected: String? = null,
    val expectedAttachments: List<Attachment> = emptyList(),
    val expectedGroupSize: Int = 1,
)

// Update TestCase — add after `tags`:
data class TestCase(
    val id: Int? = null,
    val title: String = "Untitled Test Case",
    val priority: Priority? = null,
    val status: Status? = null,
    val environment: List<String>? = null,
    val tags: List<String>? = null,
    val attachments: List<Attachment> = emptyList(),  // NEW
    val bodyBlocks: List<TestCaseBodyBlock> = emptyList(),
    val steps: List<TestStep> = emptyList(),
)
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL (existing tests may fail due to TestStep constructor change — that's expected, fixed in Task 4)

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/io/speqa/speqa/model/TestCase.kt
git commit -m "feat: add Attachment model and attachment fields to TestStep/TestCase"
```

---

### Task 2: Parser — detect attachment links in markdown

**Files:**
- Modify: `src/main/kotlin/io/speqa/speqa/parser/TestCaseParser.kt`

Attachment links in markdown are either:
- `[path/to/file.png]` — bare link (no URL part)
- `[text](path/to/file.png)` — standard Markdown link
- `![alt](path/to/file.png)` — Markdown image

The regex pattern to match all three: `^\[([^\]]+)\](?:\(([^)]+)\))?$` on a trimmed line, plus `^!\[([^\]]*)\]\(([^)]+)\)$` for images.

Position determines ownership:
- In `Attachments:` section (body, before Steps) → `TestCase.attachments`
- In step continuation after action line, before `>` → `TestStep.actionAttachments`
- In step continuation after `>` expected line → `TestStep.expectedAttachments`

- [ ] **Step 1: Add ATTACHMENT_PATTERN and ATTACHMENTS_MARKER regexes**

Add to the companion-like area at bottom of `TestCaseParser`:

```kotlin
private val ATTACHMENTS_MARKER = Regex("""^[Aa]ttachments:\s*$""")
private val ATTACHMENT_LINK = Regex("""^!?\[([^\]]*)\]\(([^)]+)\)$""")
private val ATTACHMENT_BARE = Regex("""^\[([^\]]+)\]$""")
```

- [ ] **Step 2: Add `parseAttachmentLine` helper**

```kotlin
private fun parseAttachmentLine(trimmed: String): Attachment? {
    ATTACHMENT_LINK.matchEntire(trimmed)?.let { match ->
        return Attachment(path = match.groupValues[2])
    }
    ATTACHMENT_BARE.matchEntire(trimmed)?.let { match ->
        return Attachment(path = match.groupValues[1])
    }
    return null
}
```

- [ ] **Step 3: Update `bodyBeforeStepsMarker` to also stop at `Attachments:` — NO**

Actually, `Attachments:` sits between Preconditions and Steps in the body. The body blocks parser needs to recognize it. The simplest approach: treat `Attachments:` as a new body block type similar to `Preconditions:`, OR parse it separately.

Better approach: parse attachments from the body separately. Add to `parse()`:

```kotlin
return TestCase(
    // ... existing fields ...
    attachments = parseGeneralAttachments(body),  // NEW
    bodyBlocks = parseBodyBlocks(body),
    steps = parseSteps(body),
)
```

Add new function:

```kotlin
private fun parseGeneralAttachments(body: String): List<Attachment> {
    val result = mutableListOf<Attachment>()
    var inAttachmentsSection = false

    for (line in body.lines()) {
        val trimmed = line.trim()

        if (ATTACHMENTS_MARKER.matches(trimmed)) {
            inAttachmentsSection = true
            continue
        }

        if (inAttachmentsSection) {
            if (STEPS_MARKER.matches(trimmed)) break
            if (trimmed.isBlank()) continue
            parseAttachmentLine(trimmed)?.let { result += it }
        }
    }

    return result
}
```

- [ ] **Step 4: Update `bodyBeforeStepsMarker` to also stop at `Attachments:`**

```kotlin
private fun bodyBeforeStepsMarker(body: String): String {
    val collected = mutableListOf<String>()
    for (line in body.lines()) {
        val trimmed = line.trim()
        if (STEPS_MARKER.matches(trimmed) || ATTACHMENTS_MARKER.matches(trimmed)) {
            break
        }
        collected += line
    }
    return collected.joinToString("\n")
}
```

- [ ] **Step 5: Update `parseSteps` to collect attachment lines for action and expected**

In the `parseSteps` function, add tracking for attachments:

```kotlin
private fun parseSteps(body: String): List<TestStep> {
    val steps = mutableListOf<TestStep>()
    var actionLines = mutableListOf<String>()
    var actionAttachments = mutableListOf<Attachment>()
    var expectedAttachments = mutableListOf<Attachment>()
    var currentExpected: MutableList<String>? = null
    var currentExpectedGroupSize = 1
    var groupStartIndex = 0
    var afterMarker = false
    var inExpected = false

    fun flushAction() {
        if (steps.isNotEmpty() && actionLines.isNotEmpty()) {
            val extra = actionLines.joinToString("\n").trimEnd()
            if (extra.isNotBlank()) {
                val current = steps.last()
                steps[steps.lastIndex] = current.copy(
                    action = (current.action + "\n" + extra).trim(),
                )
            }
        }
        actionLines = mutableListOf()
    }

    fun flushActionAttachments() {
        if (steps.isNotEmpty() && actionAttachments.isNotEmpty()) {
            val current = steps.last()
            steps[steps.lastIndex] = current.copy(
                actionAttachments = current.actionAttachments + actionAttachments,
            )
            actionAttachments = mutableListOf()
        }
    }

    fun flushExpectedAttachments() {
        if (steps.isNotEmpty() && expectedAttachments.isNotEmpty()) {
            val current = steps.last()
            steps[steps.lastIndex] = current.copy(
                expectedAttachments = current.expectedAttachments + expectedAttachments,
            )
            expectedAttachments = mutableListOf()
        }
    }

    for (line in body.lines()) {
        val trimmed = line.trim()

        if (!afterMarker) {
            if (STEPS_MARKER.matches(trimmed)) afterMarker = true
            continue
        }

        val stepMatch = STEP_PATTERN.matchEntire(trimmed)

        // New step starts
        if (stepMatch != null) {
            flushAction()
            flushActionAttachments()
            flushExpectedAttachments()
            if (currentExpected != null) {
                groupStartIndex = steps.size
                currentExpected = null
                currentExpectedGroupSize = 1
            }
            inExpected = false
            steps += TestStep(action = stepMatch.groupValues[1].trim())
            continue
        }

        // Blockquote — expected result
        val quoteMatch = EXPECTED_PATTERN.matchEntire(trimmed)
        if (quoteMatch != null && steps.isNotEmpty()) {
            flushAction()
            flushActionAttachments()
            if (currentExpected == null) {
                currentExpected = mutableListOf()
                currentExpectedGroupSize = steps.size - groupStartIndex
            }
            inExpected = true
            currentExpected += quoteMatch.groupValues[1]
            steps[steps.lastIndex] = steps.last().copy(
                expected = currentExpected.joinToString("\n"),
                expectedGroupSize = currentExpectedGroupSize,
            )
            continue
        }

        // Attachment line
        val stripped = line.removeListContinuationIndent().trim()
        val attachment = parseAttachmentLine(stripped)
        if (attachment != null && steps.isNotEmpty()) {
            if (inExpected) {
                expectedAttachments += attachment
            } else {
                actionAttachments += attachment
            }
            continue
        }

        // Blank line or heading — separator
        if (trimmed.isBlank() || trimmed.startsWith("## ")) {
            inExpected = false
            continue
        }

        // Continuation line for action
        if (steps.isNotEmpty() && !inExpected) {
            val lineStripped = line.removeListContinuationIndent()
            actionLines += lineStripped.trimEnd()
        }
    }

    flushAction()
    flushActionAttachments()
    flushExpectedAttachments()
    return steps
}
```

- [ ] **Step 6: Verify compilation**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add src/main/kotlin/io/speqa/speqa/parser/TestCaseParser.kt
git commit -m "feat: parse attachment links from Attachments section and inline step attachments"
```

---

### Task 3: Serializer — write attachment links to markdown

**Files:**
- Modify: `src/main/kotlin/io/speqa/speqa/parser/TestCaseSerializer.kt`

- [ ] **Step 1: Add `appendAttachment` helper**

```kotlin
private fun StringBuilder.appendAttachment(attachment: Attachment, indent: String = "") {
    appendLine("$indent[${attachment.path}]")
}
```

- [ ] **Step 2: Add general attachments section in `serialize`**

Insert after bodyBlocks serialization, before steps:

```kotlin
if (testCase.attachments.isNotEmpty()) {
    appendLine("Attachments:")
    appendLine()
    testCase.attachments.forEach { appendAttachment(it) }
    appendLine()
}
```

- [ ] **Step 3: Update `appendStep` to write action and expected attachments**

```kotlin
private fun StringBuilder.appendStep(number: Int, step: TestStep) {
    val actionLines = step.action.lines()
    appendLine("$number. ${actionLines.firstOrNull().orEmpty()}")
    actionLines.drop(1).forEach { line ->
        appendLine("   $line")
    }
    step.actionAttachments.forEach { appendAttachment(it, indent = "   ") }
    step.expected?.let { exp ->
        if (exp.isEmpty()) {
            appendLine("   >")
        } else {
            exp.lines().forEach { line -> appendLine("   > $line") }
        }
    }
    step.expectedAttachments.forEach { appendAttachment(it, indent = "   ") }
}
```

- [ ] **Step 4: Verify compilation**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/io/speqa/speqa/parser/TestCaseSerializer.kt
git commit -m "feat: serialize attachments to Attachments section and inline in steps"
```

---

### Task 4: Tests — parser and serializer attachment round-trips

**Files:**
- Modify: `src/test/kotlin/io/speqa/speqa/parser/TestCaseParserTest.kt`
- Modify: `src/test/kotlin/io/speqa/speqa/parser/TestCaseSerializerTest.kt`

- [ ] **Step 1: Add parser test for general attachments**

```kotlin
@Test
fun `parse general attachments section`() {
    val content = """
        |---
        |title: "With attachments"
        |---
        |
        |Attachments:
        |
        |[attachments/with-attachments/spec.pdf]
        |[attachments/with-attachments/screenshot.png]
        |
        |Steps:
        |
        |1. Open page
        |   > Page loads
    """.trimMargin()

    val testCase = TestCaseParser.parse(content)

    assertEquals(2, testCase.attachments.size)
    assertEquals("attachments/with-attachments/spec.pdf", testCase.attachments[0].path)
    assertEquals("attachments/with-attachments/screenshot.png", testCase.attachments[1].path)
    assertEquals(1, testCase.steps.size)
}
```

- [ ] **Step 2: Add parser test for step action attachments**

```kotlin
@Test
fun `parse step action attachments`() {
    val content = """
        |---
        |title: "Step attachments"
        |---
        |
        |Steps:
        |
        |1. Click the login button
        |   [attachments/step-attachments/result.png]
        |   > User is redirected
    """.trimMargin()

    val testCase = TestCaseParser.parse(content)

    assertEquals(1, testCase.steps.size)
    assertEquals(1, testCase.steps[0].actionAttachments.size)
    assertEquals("attachments/step-attachments/result.png", testCase.steps[0].actionAttachments[0].path)
    assertEquals("User is redirected", testCase.steps[0].expected)
}
```

- [ ] **Step 3: Add parser test for expected attachments**

```kotlin
@Test
fun `parse expected result attachments`() {
    val content = """
        |---
        |title: "Expected attachments"
        |---
        |
        |Steps:
        |
        |1. Click login
        |   > User is redirected to dashboard
        |   [attachments/expected-attachments/expected.png]
    """.trimMargin()

    val testCase = TestCaseParser.parse(content)

    assertEquals(1, testCase.steps.size)
    assertEquals("User is redirected to dashboard", testCase.steps[0].expected)
    assertEquals(1, testCase.steps[0].expectedAttachments.size)
    assertEquals("attachments/expected-attachments/expected.png", testCase.steps[0].expectedAttachments[0].path)
}
```

- [ ] **Step 4: Add parser test for standard markdown link syntax**

```kotlin
@Test
fun `parse standard markdown link as attachment`() {
    val content = """
        |---
        |title: "Markdown links"
        |---
        |
        |Attachments:
        |
        |[design spec](attachments/markdown-links/spec.pdf)
        |![screenshot](attachments/markdown-links/shot.png)
        |
        |Steps:
        |
        |1. Do thing
    """.trimMargin()

    val testCase = TestCaseParser.parse(content)

    assertEquals(2, testCase.attachments.size)
    assertEquals("attachments/markdown-links/spec.pdf", testCase.attachments[0].path)
    assertEquals("attachments/markdown-links/shot.png", testCase.attachments[1].path)
}
```

- [ ] **Step 5: Add serializer round-trip test with attachments**

```kotlin
@Test
fun `round trip preserves attachments`() {
    val original = TestCase(
        title = "Attachment round trip",
        attachments = listOf(
            Attachment("attachments/attachment-round-trip/spec.pdf"),
            Attachment("attachments/attachment-round-trip/screenshot.png"),
        ),
        steps = listOf(
            TestStep(
                action = "Click login",
                actionAttachments = listOf(Attachment("attachments/attachment-round-trip/action.png")),
                expected = "Dashboard loads",
                expectedAttachments = listOf(Attachment("attachments/attachment-round-trip/expected.png")),
            ),
        ),
    )

    val serialized = TestCaseSerializer.serialize(original)
    val parsed = TestCaseParser.parse(serialized)

    assertEquals(original.attachments.size, parsed.attachments.size)
    assertEquals(original.attachments[0].path, parsed.attachments[0].path)
    assertEquals(original.attachments[1].path, parsed.attachments[1].path)
    assertEquals(original.steps[0].actionAttachments.size, parsed.steps[0].actionAttachments.size)
    assertEquals(original.steps[0].actionAttachments[0].path, parsed.steps[0].actionAttachments[0].path)
    assertEquals(original.steps[0].expectedAttachments.size, parsed.steps[0].expectedAttachments.size)
    assertEquals(original.steps[0].expectedAttachments[0].path, parsed.steps[0].expectedAttachments[0].path)
}
```

- [ ] **Step 6: Fix any existing tests broken by TestStep constructor change**

The `TestStep` constructor now has `actionAttachments` and `expectedAttachments` with defaults, so existing tests should compile. Verify:

Run: `./gradlew test`
Expected: All tests pass

- [ ] **Step 7: Commit**

```bash
git add src/test/kotlin/io/speqa/speqa/parser/TestCaseParserTest.kt src/test/kotlin/io/speqa/speqa/parser/TestCaseSerializerTest.kt
git commit -m "test: attachment parsing and serialization round-trip tests"
```

---

### Task 5: Settings — configurable attachment folder name

**Files:**
- Modify: `src/main/kotlin/io/speqa/speqa/settings/SpeqaSettings.kt`
- Modify: `src/main/kotlin/io/speqa/speqa/settings/SpeqaSettingsConfigurable.kt`
- Modify: `src/main/resources/messages/SpeqaBundle.properties`

- [ ] **Step 1: Add `defaultAttachmentsFolder` to settings state**

In `SpeqaSettings.State`:

```kotlin
data class State(
    var defaultPriority: String = Priority.MEDIUM.label,
    var defaultStatus: String = Status.DRAFT.label,
    var defaultEnvironments: MutableList<String> = mutableListOf(),
    var defaultRunDestination: String = DEFAULT_RUN_DESTINATION,
    var defaultAttachmentsFolder: String = DEFAULT_ATTACHMENTS_FOLDER,  // NEW
)

companion object {
    const val DEFAULT_RUN_DESTINATION = "test-runs"
    const val DEFAULT_ATTACHMENTS_FOLDER = "attachments"  // NEW
    fun getInstance(project: Project): SpeqaSettings = project.service()
}
```

Add property:

```kotlin
var defaultAttachmentsFolder: String
    get() = state.defaultAttachmentsFolder
    set(value) {
        state.defaultAttachmentsFolder = value
    }
```

- [ ] **Step 2: Add settings UI row**

In `SpeqaSettingsConfigurable`, add field and row:

```kotlin
private val attachmentsFolderField = JTextField(24)

// In createComponent, add row:
addRow(panel, constraints, SpeqaBundle.message("settings.defaultAttachmentsFolder"), attachmentsFolderField, SpeqaBundle.message("settings.defaultAttachmentsFolder.comment"))

// In isModified:
|| attachmentsFolderField.text != settings.defaultAttachmentsFolder

// In apply:
settings.defaultAttachmentsFolder = attachmentsFolderField.text.trim().ifEmpty { SpeqaSettings.DEFAULT_ATTACHMENTS_FOLDER }

// In reset:
attachmentsFolderField.text = settings.defaultAttachmentsFolder
```

- [ ] **Step 3: Add bundle keys**

```properties
settings.defaultAttachmentsFolder=Attachments folder
settings.defaultAttachmentsFolder.comment=Folder name for test case attachments (relative to test case file)
```

- [ ] **Step 4: Verify compilation**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/io/speqa/speqa/settings/SpeqaSettings.kt src/main/kotlin/io/speqa/speqa/settings/SpeqaSettingsConfigurable.kt src/main/resources/messages/SpeqaBundle.properties
git commit -m "feat: add configurable attachments folder name in settings"
```

---

### Task 6: Attachment utility — resolve paths, copy files

**Files:**
- Create: `src/main/kotlin/io/speqa/speqa/editor/AttachmentSupport.kt`

- [ ] **Step 1: Create AttachmentSupport utility**

```kotlin
package io.github.barsia.speqa.editor

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import io.github.barsia.speqa.model.Attachment
import io.github.barsia.speqa.settings.SpeqaSettings
import java.io.IOException

object AttachmentSupport {

    /**
     * Returns the attachments folder for a given .tc.md file.
     * E.g. for `test-cases/sample-login.tc.md` → `test-cases/attachments/sample-login/`
     */
    fun resolveAttachmentsDir(project: Project, tcFile: VirtualFile): String {
        val folder = SpeqaSettings.getInstance(project).defaultAttachmentsFolder
        val tcName = tcFile.nameWithoutExtension.removeSuffix(".tc")
        return "$folder/$tcName"
    }

    /**
     * Resolves an attachment path relative to the .tc.md file to a VirtualFile.
     */
    fun resolveFile(tcFile: VirtualFile, attachment: Attachment): VirtualFile? {
        val parent = tcFile.parent ?: return null
        return parent.findFileByRelativePath(attachment.path)
    }

    /**
     * Copies a source file into the attachments folder for the given .tc.md file.
     * Creates the folder if needed. Returns the relative path for the markdown link.
     */
    fun copyFileToAttachments(
        project: Project,
        tcFile: VirtualFile,
        sourceFile: VirtualFile,
    ): Attachment? {
        val parent = tcFile.parent ?: return null
        val dirPath = resolveAttachmentsDir(project, tcFile)
        return try {
            val dir = VfsUtil.createDirectoryIfMissing(parent, dirPath) ?: return null
            val target = if (dir.findChild(sourceFile.name) != null) {
                val baseName = sourceFile.nameWithoutExtension
                val ext = sourceFile.extension?.let { ".$it" } ?: ""
                var counter = 1
                var candidate: String
                do {
                    candidate = "${baseName}_$counter$ext"
                    counter++
                } while (dir.findChild(candidate) != null)
                sourceFile.copy(null, dir, candidate)
            } else {
                sourceFile.copy(null, dir, sourceFile.name)
            }
            Attachment(path = "$dirPath/${target.name}")
        } catch (_: IOException) {
            null
        }
    }

    /**
     * Deletes the attachment file from disk.
     */
    fun deleteFile(tcFile: VirtualFile, attachment: Attachment): Boolean {
        val file = resolveFile(tcFile, attachment) ?: return false
        return try {
            file.delete(null)
            true
        } catch (_: IOException) {
            false
        }
    }

    /**
     * Returns true if the attachment path points to an image file.
     */
    fun isImage(attachment: Attachment): Boolean {
        val ext = attachment.path.substringAfterLast('.', "").lowercase()
        return ext in IMAGE_EXTENSIONS
    }

    private val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "gif", "svg", "webp", "bmp", "ico")
}
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/io/speqa/speqa/editor/AttachmentSupport.kt
git commit -m "feat: add AttachmentSupport utility for path resolution and file operations"
```

---

### Task 7: UI — AttachmentRow composable

**Files:**
- Create: `src/main/kotlin/io/speqa/speqa/editor/ui/AttachmentRow.kt`
- Modify: `src/main/resources/messages/SpeqaBundle.properties`

- [ ] **Step 1: Create AttachmentRow composable**

```kotlin
package io.github.barsia.speqa.editor.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.intellij.icons.AllIcons
import io.github.barsia.speqa.SpeqaBundle
import io.github.barsia.speqa.editor.AttachmentSupport
import io.github.barsia.speqa.model.Attachment
import org.jetbrains.jewel.bridge.icon.fromPlatformIcon
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.Tooltip
import org.jetbrains.jewel.ui.icon.IntelliJIconKey

@OptIn(ExperimentalJewelApi::class, ExperimentalFoundationApi::class)
@Composable
internal fun AttachmentRow(
    attachment: Attachment,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hoverSource = remember { MutableInteractionSource() }
    val isHovered by hoverSource.collectIsHoveredAsState()
    val deleteColor = Color(0xFFCC3333)
    val removeIcon = IntelliJIconKey.fromPlatformIcon(AllIcons.Actions.GC, SpeqaLayout::class.java)
    val fileIcon = if (AttachmentSupport.isImage(attachment)) {
        IntelliJIconKey.fromPlatformIcon(AllIcons.FileTypes.Image, SpeqaLayout::class.java)
    } else {
        IntelliJIconKey.fromPlatformIcon(AllIcons.FileTypes.Any_type, SpeqaLayout::class.java)
    }
    val fileName = attachment.path.substringAfterLast('/')

    Row(
        modifier = modifier.hoverable(hoverSource).clickableWithPointer(onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(fileIcon, contentDescription = null, modifier = Modifier.size(14.dp), tint = SpeqaThemeColors.mutedForeground)
        Text(
            fileName,
            fontSize = 12.sp,
            color = SpeqaThemeColors.accent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Tooltip(tooltip = { Text(SpeqaBundle.message("tooltip.removeAttachment")) }) {
            SpeqaIconButton(
                onClick = onDelete,
                modifier = Modifier.alpha(if (isHovered) 1f else 0f),
            ) {
                Icon(removeIcon, contentDescription = SpeqaBundle.message("tooltip.removeAttachment"), modifier = Modifier.size(14.dp), tint = deleteColor)
            }
        }
    }
}
```

- [ ] **Step 2: Add bundle keys**

```properties
# --- Attachments ---
label.attachments=Attachments
form.addAttachment=+ Attachment
tooltip.removeAttachment=Remove attachment
tooltip.addAttachment=Attach file
dialog.removeAttachment.title=Remove Attachment
dialog.removeAttachment.removeLink=Remove link only
dialog.removeAttachment.deleteFile=Delete file
dialog.removeAttachment.message=How do you want to remove this attachment?
```

- [ ] **Step 3: Verify compilation**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/io/speqa/speqa/editor/ui/AttachmentRow.kt src/main/resources/messages/SpeqaBundle.properties
git commit -m "feat: add AttachmentRow composable with hover-delete and file icon"
```

---

### Task 8: UI — AttachmentList composable with add button and delete dialog

**Files:**
- Create: `src/main/kotlin/io/speqa/speqa/editor/ui/AttachmentList.kt`

- [ ] **Step 1: Create AttachmentList composable**

This composable renders a list of attachments with `+ ATTACHMENT` button. It handles the delete confirmation dialog (remove link vs delete file) and the add flow (file chooser).

```kotlin
package io.github.barsia.speqa.editor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import io.github.barsia.speqa.SpeqaBundle
import io.github.barsia.speqa.editor.AttachmentSupport
import io.github.barsia.speqa.model.Attachment

@Composable
internal fun AttachmentList(
    attachments: List<Attachment>,
    project: Project,
    tcFile: VirtualFile,
    onAttachmentsChange: (List<Attachment>) -> Unit,
    onOpenFile: (Attachment) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(SpeqaLayout.tightGap),
    ) {
        attachments.forEach { attachment ->
            AttachmentRow(
                attachment = attachment,
                onClick = { onOpenFile(attachment) },
                onDelete = {
                    val choice = Messages.showDialog(
                        SpeqaBundle.message("dialog.removeAttachment.message"),
                        SpeqaBundle.message("dialog.removeAttachment.title"),
                        arrayOf(
                            SpeqaBundle.message("dialog.removeAttachment.removeLink"),
                            SpeqaBundle.message("dialog.removeAttachment.deleteFile"),
                            Messages.getCancelButton(),
                        ),
                        0,
                        Messages.getQuestionIcon(),
                    )
                    when (choice) {
                        0 -> onAttachmentsChange(attachments - attachment)
                        1 -> {
                            AttachmentSupport.deleteFile(tcFile, attachment)
                            onAttachmentsChange(attachments - attachment)
                        }
                    }
                },
            )
        }

        QuietActionText(
            label = SpeqaBundle.message("form.addAttachment"),
            onClick = {
                val descriptor = FileChooserDescriptorFactory.createAllButJarContentsDescriptor()
                val chosen = FileChooser.chooseFile(descriptor, project, null)
                if (chosen != null) {
                    val newAttachment = com.intellij.openapi.application.runWriteAction {
                        AttachmentSupport.copyFileToAttachments(project, tcFile, chosen)
                    }
                    if (newAttachment != null) {
                        onAttachmentsChange(attachments + newAttachment)
                    }
                }
            },
            enabled = true,
        )
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/io/speqa/speqa/editor/ui/AttachmentList.kt
git commit -m "feat: add AttachmentList composable with file chooser and delete dialog"
```

---

### Task 9: UI — integrate attachments into TestCasePreview

**Files:**
- Modify: `src/main/kotlin/io/speqa/speqa/editor/ui/TestCasePreview.kt`

- [ ] **Step 1: Add general attachments section between Preconditions and Steps**

In the body Column of `TestCasePreview`, after `EditablePreviewSection` for Preconditions and before `StepsSection`, add:

```kotlin
// General attachments section
onTestCaseChange?.let { onChange ->
    SectionHeaderWithDivider(SpeqaBundle.message("label.attachments"))
    AttachmentList(
        attachments = testCase.attachments,
        project = project,
        tcFile = file,
        onAttachmentsChange = { newAttachments ->
            onChange(testCase.copy(attachments = newAttachments))
        },
        onOpenFile = { attachment ->
            AttachmentSupport.resolveFile(tcFile = file, attachment = attachment)?.let { vf ->
                com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project).openFile(vf, true)
            }
        },
    )
}
```

Note: `TestCasePreview` needs `project` and `file` parameters. These are available in `SpeqaPreviewEditor` which creates `TestCasePreview`. Add them as parameters to `TestCasePreview`.

- [ ] **Step 2: Add `project` and `file` parameters to TestCasePreview**

Update the function signature:

```kotlin
@Composable
internal fun TestCasePreview(
    testCase: TestCase,
    headerMeta: TestCaseHeaderMeta,
    project: Project,          // NEW
    file: VirtualFile,         // NEW
    nextFreeTestCaseId: Int,
    // ... rest unchanged
)
```

Update the call site in `SpeqaPreviewEditor` to pass `project` and `file`.

- [ ] **Step 3: Verify compilation**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/io/speqa/speqa/editor/ui/TestCasePreview.kt src/main/kotlin/io/speqa/speqa/editor/SpeqaPreviewEditor.kt
git commit -m "feat: integrate general attachments section into TestCasePreview"
```

---

### Task 10: UI — integrate attachments into StepCard

**Files:**
- Modify: `src/main/kotlin/io/speqa/speqa/editor/ui/StepCard.kt`
- Modify: `src/main/kotlin/io/speqa/speqa/editor/ui/StepsSection.kt`

- [ ] **Step 1: Add attachment parameters to StepCard**

Add to StepCard parameters:

```kotlin
actionAttachments: List<Attachment> = emptyList(),
expectedAttachments: List<Attachment> = emptyList(),
onActionAttachmentsChange: (List<Attachment>) -> Unit = {},
onExpectedAttachmentsChange: (List<Attachment>) -> Unit = {},
project: Project? = null,
tcFile: VirtualFile? = null,
onOpenFile: (Attachment) -> Unit = {},
```

- [ ] **Step 2: Add AttachmentList after action PlainTextInput**

Inside the action Row, after the PlainTextInput and delete button block, add an attachment list:

```kotlin
if (project != null && tcFile != null) {
    AttachmentList(
        attachments = actionAttachments,
        project = project,
        tcFile = tcFile,
        onAttachmentsChange = onActionAttachmentsChange,
        onOpenFile = onOpenFile,
    )
}
```

- [ ] **Step 3: Add AttachmentList after expected PlainTextInput**

Inside the expected block (when `expected != null`), after the expected PlainTextInput row, add:

```kotlin
if (project != null && tcFile != null) {
    AttachmentList(
        attachments = expectedAttachments,
        project = project,
        tcFile = tcFile,
        onAttachmentsChange = onExpectedAttachmentsChange,
        onOpenFile = onOpenFile,
    )
}
```

- [ ] **Step 4: Wire StepsSection to pass attachment data from TestStep to StepCard**

In `StepsSection`, update the `StepCard` call to pass attachment fields:

```kotlin
StepCard(
    // ... existing params ...
    actionAttachments = step.actionAttachments,
    expectedAttachments = step.expectedAttachments,
    onActionAttachmentsChange = { newAttachments ->
        onTestCaseChange(testCase.copy(steps = testCase.steps.updated(index, step.copy(actionAttachments = newAttachments))))
    },
    onExpectedAttachmentsChange = { newAttachments ->
        onTestCaseChange(testCase.copy(steps = testCase.steps.updated(index, step.copy(expectedAttachments = newAttachments))))
    },
    project = project,
    tcFile = tcFile,
    onOpenFile = { attachment ->
        AttachmentSupport.resolveFile(tcFile, attachment)?.let { vf ->
            FileEditorManager.getInstance(project).openFile(vf, true)
        }
    },
)
```

StepsSection needs `project` and `tcFile` parameters — add them and wire from TestCasePreview.

- [ ] **Step 5: Verify compilation**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/io/speqa/speqa/editor/ui/StepCard.kt src/main/kotlin/io/speqa/speqa/editor/ui/StepsSection.kt
git commit -m "feat: integrate step-level attachments into StepCard UI"
```

---

### Task 11: Drag & drop support

**Files:**
- Modify: `src/main/kotlin/io/speqa/speqa/editor/ui/TestCasePreview.kt`

- [ ] **Step 1: Add file drop handling to the preview panel**

Add a `Modifier.onExternalDrag` or use `TransferHandler` on the Swing `composePanel` in `SpeqaPreviewEditor` to handle file drops. When a file is dropped:

1. Copy to `attachments/{tc-name}/` via `AttachmentSupport.copyFileToAttachments`
2. Add to `testCase.attachments` (general attachments)
3. Write updated test case to document

In `SpeqaPreviewEditor`, on the `composePanel`:

```kotlin
composePanel.transferHandler = object : javax.swing.TransferHandler() {
    override fun canImport(support: TransferSupport): Boolean {
        return support.isDataFlavorSupported(java.awt.datatransfer.DataFlavor.javaFileListFlavor)
    }

    @Suppress("UNCHECKED_CAST")
    override fun importData(support: TransferSupport): Boolean {
        val files = try {
            support.transferable.getTransferData(java.awt.datatransfer.DataFlavor.javaFileListFlavor) as List<java.io.File>
        } catch (_: Exception) {
            return false
        }
        if (files.isEmpty()) return false

        com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
            com.intellij.openapi.application.runWriteAction {
                val newAttachments = files.mapNotNull { javaFile ->
                    val vf = com.intellij.openapi.vfs.LocalFileSystem.getInstance().findFileByIoFile(javaFile) ?: return@mapNotNull null
                    AttachmentSupport.copyFileToAttachments(project, file, vf)
                }
                if (newAttachments.isNotEmpty()) {
                    val updated = parsed.testCase.copy(attachments = parsed.testCase.attachments + newAttachments)
                    parsed = ParsedTestCase(updated, parsed.parseError)
                    writeFromPreview(updated, "Speqa: Add attachments")
                }
            }
        }
        return true
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/io/speqa/speqa/editor/SpeqaPreviewEditor.kt
git commit -m "feat: add drag-and-drop file support to preview panel"
```

---

### Task 12: Rename refactoring — update paths when .tc.md is renamed

**Files:**
- Create: `src/main/kotlin/io/speqa/speqa/editor/AttachmentRefactoringListener.kt`
- Modify: `src/main/resources/META-INF/plugin.xml`

- [ ] **Step 1: Create VFS listener for .tc.md renames**

```kotlin
package io.github.barsia.speqa.editor

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent
import io.github.barsia.speqa.model.SpeqaDefaults
import io.github.barsia.speqa.settings.SpeqaSettings

class AttachmentRefactoringListener(private val project: Project) : BulkFileListener {
    override fun after(events: List<VFileEvent>) {
        for (event in events) {
            if (event !is VFilePropertyChangeEvent) continue
            if (event.propertyName != "name") continue
            val file = event.file
            val oldName = event.oldValue as? String ?: continue
            val newName = event.newValue as? String ?: continue
            if (!oldName.endsWith(".${SpeqaDefaults.TEST_CASE_EXTENSION}")) continue

            val folder = SpeqaSettings.getInstance(project).defaultAttachmentsFolder
            val oldStem = oldName.removeSuffix(".${SpeqaDefaults.TEST_CASE_EXTENSION}")
            val newStem = newName.removeSuffix(".${SpeqaDefaults.TEST_CASE_EXTENSION}")
            if (oldStem == newStem) continue

            val parent = file.parent ?: continue
            val oldDir = parent.findFileByRelativePath("$folder/$oldStem")

            // Rename attachments folder
            if (oldDir != null && oldDir.isDirectory) {
                runWriteAction { oldDir.rename(this, newStem) }
            }

            // Update paths in the markdown file
            val document = FileDocumentManager.getInstance().getDocument(file) ?: continue
            val oldPrefix = "$folder/$oldStem/"
            val newPrefix = "$folder/$newStem/"
            val oldText = document.text
            val newText = oldText.replace(oldPrefix, newPrefix)
            if (newText != oldText) {
                CommandProcessor.getInstance().executeCommand(project, {
                    runWriteAction { document.setText(newText) }
                }, "Speqa: Update attachment paths", null)
            }
        }
    }
}
```

- [ ] **Step 2: Register listener in plugin.xml**

Add inside `<extensions defaultExtensionNs="com.intellij">`:

```xml
<vfs.asyncListener implementation="io.github.barsia.speqa.editor.AttachmentRefactoringListener"/>
```

Note: `BulkFileListener` needs project context. Use `projectService` registration instead:

Actually, `BulkFileListener` is registered via message bus, not as extension. Register in startup activity instead:

In `SpeqaIdRegistryStartup` (or create a new startup), add:

```kotlin
project.messageBus.connect().subscribe(
    com.intellij.openapi.vfs.newvfs.BulkFileListener.TOPIC,
    AttachmentRefactoringListener(project),
)
```

- [ ] **Step 3: Verify compilation**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/io/speqa/speqa/editor/AttachmentRefactoringListener.kt src/main/kotlin/io/speqa/speqa/registry/SpeqaIdRegistryStartup.kt
git commit -m "feat: rename attachment folder and update paths when .tc.md is renamed"
```

---

### Task 13: Update spec document

**Files:**
- Modify: `docs/specs/2026-04-06-speqa-mvp-design.md`

- [ ] **Step 1: Add attachments section to spec**

Add a new section documenting:
- Storage convention: `attachments/{filename-without-ext}/` sibling to `.tc.md`
- Markdown format: `[path]` bare links and `[text](path)` standard links
- `Attachments:` body section marker
- Step-level inline attachments (after action, after expected)
- UI: icon + filename, hover-preview for images, hover-delete with dialog
- Drag & drop, file chooser, manual markdown entry
- Rename refactoring behavior
- Settings: configurable folder name

- [ ] **Step 2: Commit**

```bash
git add docs/specs/2026-04-06-speqa-mvp-design.md
git commit -m "docs: add attachment system to product spec"
```
