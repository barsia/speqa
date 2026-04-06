# Unique ID System Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add auto-increment unique IDs (`TC-N` / `TR-N`) to test cases and test runs, with in-memory registry, inline editing in the header, and duplicate detection.

**Architecture:** `SpeqaIdRegistry` project-level service scans root path at startup, builds `Map<Int, Int>` (id → file count) for TC and TR types, stays current via `VirtualFileListener`. ID stored as `Int?` in `TestCase`/`TestRun` frontmatter. UI shows `TC-N` prefix — prefix is display-only. `CreateFromTemplateHandler` injects next free ID at file creation. Annotator + editor header show duplicate warnings.

**Tech Stack:** Kotlin, IntelliJ Platform SDK (Project Service, StartupActivity, VirtualFileListener), Compose/Jewel for header UI.

**Spec:** `docs/specs/2026-04-06-speqa-design.md` — sections on ID system, file formats, architecture, template, annotator.

---

## File Structure

```
src/main/kotlin/io/speqa/speqa/
├── model/
│   ├── TestCase.kt              # ADD id: Int? field
│   └── TestRun.kt               # ADD id: Int? field
├── parser/
│   ├── TestCaseParser.kt        # MODIFY: parse id from frontmatter
│   ├── TestCaseSerializer.kt    # MODIFY: serialize id to frontmatter
│   ├── TestRunParser.kt         # MODIFY: parse id from frontmatter
│   └── TestRunSerializer.kt     # MODIFY: serialize id to frontmatter
├── registry/
│   └── SpeqaIdRegistry.kt       # CREATE: project service + VFS listener
├── actions/
│   └── CreateTestCaseAction.kt  # MODIFY: inject ID via template properties
├── editor/
│   ├── SpeqaEditorSupport.kt    # MODIFY: inject ID when creating test run
│   └── ui/
│       └── EditorPrimitives.kt  # MODIFY: add InlineEditableIdRow composable
├── editor/ui/
│   ├── TestCasePreview.kt       # MODIFY: show ID in header
│   └── TestCaseForm.kt          # MODIFY: show ID in header
├── validation/
│   └── SpeqaAnnotator.kt        # MODIFY: add duplicate ID warning
└── run/
    └── TestRunPanel.kt          # MODIFY: show TR-N in header

src/main/resources/
├── fileTemplates/internal/
│   └── Speqa Test Case.tc.md.ft # MODIFY: add id: ${ID} line + update template
├── messages/
│   └── SpeqaBundle.properties   # ADD: ID-related strings
└── META-INF/
    └── plugin.xml               # ADD: postStartupActivity registration

src/test/kotlin/io/speqa/speqa/
├── parser/
│   ├── TestCaseParserTest.kt    # MODIFY: test id parsing
│   └── TestCaseSerializerTest.kt # MODIFY: test id serialization
├── model/
│   └── TestCaseTest.kt          # MODIFY: test id default
└── registry/
    └── SpeqaIdRegistryTest.kt   # CREATE: test registry logic
```

---

### Task 1: Add `id` field to data models

**Files:**
- Modify: `src/main/kotlin/io/speqa/speqa/model/TestCase.kt:58-66`
- Modify: `src/main/kotlin/io/speqa/speqa/model/TestRun.kt:35-44`
- Test: `src/test/kotlin/io/speqa/speqa/model/TestCaseTest.kt`

- [ ] **Step 1: Write failing test**

```kotlin
// Add to TestCaseTest.kt
@Test
fun `default test case has null id`() {
    val testCase = TestCase()
    assertNull(testCase.id)
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "io.github.barsia.speqa.model.TestCaseTest" -v`
Expected: Compilation error — `id` not found on `TestCase`.

- [ ] **Step 3: Add id field to TestCase**

In `TestCase.kt`, change `data class TestCase` to:
```kotlin
data class TestCase(
    val id: Int? = null,
    val title: String = "Untitled Test Case",
    val priority: Priority? = null,
    val status: Status? = null,
    val environment: List<String>? = null,
    val tags: List<String>? = null,
    val bodyBlocks: List<TestCaseBodyBlock> = emptyList(),
    val steps: List<TestStep> = emptyList(),
)
```

- [ ] **Step 4: Add id field to TestRun**

In `TestRun.kt`, change `data class TestRun` to:
```kotlin
data class TestRun(
    val id: Int? = null,
    val testCaseFile: String = "",
    val startedAt: LocalDateTime = LocalDateTime.now(),
    val finishedAt: LocalDateTime? = null,
    val result: RunResult = RunResult.FAILED,
    val environment: String = "",
    val runner: String = "",
    val stepResults: List<StepResult> = emptyList(),
    val summary: String = "",
)
```

- [ ] **Step 5: Run tests**

Run: `./gradlew test -v`
Expected: All tests PASS (id defaults to null, existing tests unaffected).

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/io/speqa/speqa/model/TestCase.kt src/main/kotlin/io/speqa/speqa/model/TestRun.kt src/test/kotlin/io/speqa/speqa/model/TestCaseTest.kt
git commit -m "feat: add optional id field to TestCase and TestRun data models"
```

---

### Task 2: Parse and serialize `id` in frontmatter

**Files:**
- Modify: `src/main/kotlin/io/speqa/speqa/parser/TestCaseParser.kt:22-30`
- Modify: `src/main/kotlin/io/speqa/speqa/parser/TestCaseSerializer.kt:11-18`
- Modify: `src/main/kotlin/io/speqa/speqa/parser/TestRunParser.kt:20-29`
- Modify: `src/main/kotlin/io/speqa/speqa/parser/TestRunSerializer.kt:11-19`
- Test: `src/test/kotlin/io/speqa/speqa/parser/TestCaseParserTest.kt`
- Test: `src/test/kotlin/io/speqa/speqa/parser/TestCaseSerializerTest.kt`

- [ ] **Step 1: Write parser test**

```kotlin
// Add to TestCaseParserTest.kt
@Test
fun `parse id from frontmatter`() {
    val content = """
        |---
        |id: 42
        |title: "With ID"
        |---
        |
        |1. Do thing
        |   > Done
    """.trimMargin()

    val testCase = TestCaseParser.parse(content)
    assertEquals(42, testCase.id)
}

@Test
fun `parse missing id returns null`() {
    val content = """
        |---
        |title: "No ID"
        |---
        |
        |1. Do thing
    """.trimMargin()

    val testCase = TestCaseParser.parse(content)
    assertNull(testCase.id)
}
```

- [ ] **Step 2: Write serializer test**

```kotlin
// Add to TestCaseSerializerTest.kt
@Test
fun `serialize id in frontmatter`() {
    val testCase = TestCase(id = 7, title = "With ID")
    val result = TestCaseSerializer.serialize(testCase)
    assertTrue(result.contains("id: 7"))
}

@Test
fun `serialize null id omits field`() {
    val testCase = TestCase(id = null, title = "No ID")
    val result = TestCaseSerializer.serialize(testCase)
    assertFalse(result.contains("id:"))
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `./gradlew test --tests "io.github.barsia.speqa.parser.*" -v`
Expected: New tests fail (id not parsed/serialized yet).

- [ ] **Step 4: Update TestCaseParser**

In `TestCaseParser.kt`, in the `parse()` function, add `id` parsing. Change the `return TestCase(...)` block to:
```kotlin
return TestCase(
    id = if ("id" in meta) (meta["id"] as? Number)?.toInt() else null,
    title = SpeqaMarkdown.parseScalar(meta["title"]).ifBlank { "Untitled Test Case" },
    priority = if ("priority" in meta) Priority.fromString(SpeqaMarkdown.parseScalar(meta["priority"])) else null,
    status = if ("status" in meta) Status.fromString(SpeqaMarkdown.parseScalar(meta["status"])) else null,
    environment = if ("environment" in meta) SpeqaMarkdown.parseStringList(meta["environment"]) else null,
    tags = if ("tags" in meta) SpeqaMarkdown.parseStringList(meta["tags"]) else null,
    bodyBlocks = parseBodyBlocks(body),
    steps = parseSteps(body),
)
```

- [ ] **Step 5: Update TestCaseSerializer**

In `TestCaseSerializer.kt`, in `serialize()`, add after `appendLine("---")` and before `appendLine("title: ...")`:
```kotlin
testCase.id?.let { appendLine("id: $it") }
```

So the frontmatter block becomes:
```kotlin
appendLine("---")
testCase.id?.let { appendLine("id: $it") }
appendLine("title: ${SpeqaMarkdown.quoteYamlScalar(testCase.title)}")
testCase.priority?.let { appendLine("priority: ${it.label}") }
...
```

- [ ] **Step 6: Update TestRunParser**

In `TestRunParser.kt`, in `parse()`, add `id` to the `return TestRun(...)`:
```kotlin
return TestRun(
    id = if ("id" in meta) (meta["id"] as? Number)?.toInt() else null,
    testCaseFile = SpeqaMarkdown.parseScalar(meta["test_case"]),
    ...
)
```

- [ ] **Step 7: Update TestRunSerializer**

In `TestRunSerializer.kt`, in `serialize()`, add after `appendLine("---")`:
```kotlin
testRun.id?.let { appendLine("id: $it") }
```

- [ ] **Step 8: Run tests**

Run: `./gradlew test -v`
Expected: All tests PASS.

- [ ] **Step 9: Commit**

```bash
git add src/main/kotlin/io/speqa/speqa/parser/ src/test/kotlin/io/speqa/speqa/parser/
git commit -m "feat: parse and serialize id field in .tc.md and .tr.md frontmatter"
```

---

### Task 3: Create SpeqaIdRegistry project service

**Files:**
- Create: `src/main/kotlin/io/speqa/speqa/registry/SpeqaIdRegistry.kt`
- Create: `src/test/kotlin/io/speqa/speqa/registry/SpeqaIdRegistryTest.kt`
- Modify: `src/main/resources/META-INF/plugin.xml`

- [ ] **Step 1: Write tests for registry logic**

```kotlin
// src/test/kotlin/io/speqa/speqa/registry/SpeqaIdRegistryTest.kt
package io.github.barsia.speqa.registry

import org.junit.Assert.*
import org.junit.Test

class SpeqaIdRegistryTest {

    @Test
    fun `nextFreeId returns 1 when empty`() {
        val registry = IdSet()
        assertEquals(1, registry.nextFreeId())
    }

    @Test
    fun `nextFreeId fills gaps`() {
        val registry = IdSet()
        registry.register(1)
        registry.register(3)
        assertEquals(2, registry.nextFreeId())
    }

    @Test
    fun `nextFreeId returns max plus 1 when no gaps`() {
        val registry = IdSet()
        registry.register(1)
        registry.register(2)
        registry.register(3)
        assertEquals(4, registry.nextFreeId())
    }

    @Test
    fun `isDuplicate returns false for single use`() {
        val registry = IdSet()
        registry.register(5)
        assertFalse(registry.isDuplicate(5))
    }

    @Test
    fun `isDuplicate returns true for multiple uses`() {
        val registry = IdSet()
        registry.register(5)
        registry.register(5)
        assertTrue(registry.isDuplicate(5))
    }

    @Test
    fun `unregister decrements count`() {
        val registry = IdSet()
        registry.register(5)
        registry.register(5)
        registry.unregister(5)
        assertFalse(registry.isDuplicate(5))
    }

    @Test
    fun `unregister removes when count reaches zero`() {
        val registry = IdSet()
        registry.register(5)
        registry.unregister(5)
        assertFalse(registry.isUsed(5))
        assertEquals(1, registry.nextFreeId())
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests "io.github.barsia.speqa.registry.*" -v`
Expected: Compilation error — `IdSet` not found.

- [ ] **Step 3: Create IdSet and SpeqaIdRegistry**

```kotlin
// src/main/kotlin/io/speqa/speqa/registry/SpeqaIdRegistry.kt
package io.github.barsia.speqa.registry

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import io.github.barsia.speqa.model.SpeqaDefaults
import io.github.barsia.speqa.settings.SpeqaSettings

enum class IdType(val extension: String, val prefix: String) {
    TEST_CASE(SpeqaDefaults.TEST_CASE_EXTENSION, "TC"),
    TEST_RUN(SpeqaDefaults.TEST_RUN_EXTENSION, "TR"),
}

/** Thread-safe ID count tracker. Extracted for testability. */
class IdSet {
    private val counts = mutableMapOf<Int, Int>()

    fun register(id: Int) {
        counts[id] = (counts[id] ?: 0) + 1
    }

    fun unregister(id: Int) {
        val count = counts[id] ?: return
        if (count <= 1) counts.remove(id) else counts[id] = count - 1
    }

    fun isUsed(id: Int): Boolean = id in counts

    fun isDuplicate(id: Int): Boolean = (counts[id] ?: 0) > 1

    fun nextFreeId(): Int {
        var candidate = 1
        while (candidate in counts) candidate++
        return candidate
    }

    fun clear() = counts.clear()
}

@Service(Service.Level.PROJECT)
class SpeqaIdRegistry(private val project: Project) {
    private val tcIds = IdSet()
    private val trIds = IdSet()
    @Volatile
    private var initialized = false

    fun idSet(type: IdType): IdSet = when (type) {
        IdType.TEST_CASE -> tcIds
        IdType.TEST_RUN -> trIds
    }

    fun ensureInitialized() {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            scan()
            subscribeToVfsEvents()
            initialized = true
        }
    }

    private fun scan() {
        tcIds.clear()
        trIds.clear()
        val rootPath = SpeqaSettings.getInstance(project).rootPath
        val projectDir = project.basePath?.let { VirtualFileManager.getInstance().findFileByUrl("file://$it") } ?: return
        val rootDir = projectDir.findFileByRelativePath(rootPath) ?: return
        scanDirectory(rootDir)
    }

    private fun scanDirectory(dir: VirtualFile) {
        for (child in dir.children) {
            if (child.isDirectory) {
                scanDirectory(child)
            } else {
                extractId(child)?.let { (type, id) ->
                    idSet(type).register(id)
                }
            }
        }
    }

    private fun subscribeToVfsEvents() {
        project.messageBus.connect().subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
            override fun after(events: List<VFileEvent>) {
                for (event in events) {
                    when (event) {
                        is VFileCreateEvent -> event.file?.let { handleFileAdded(it) }
                        is VFileDeleteEvent -> handleFileRemoved(event.file)
                        is VFileContentChangeEvent -> handleFileChanged(event.file)
                    }
                }
            }
        })
    }

    private fun handleFileAdded(file: VirtualFile) {
        if (!isSpeqaFile(file)) return
        extractId(file)?.let { (type, id) -> idSet(type).register(id) }
    }

    private fun handleFileRemoved(file: VirtualFile) {
        if (!isSpeqaFile(file)) return
        // We can't read the file anymore — we need to rescan or track file->id mapping
        // For simplicity, trigger a full rescan on delete
        scan()
    }

    private fun handleFileChanged(file: VirtualFile) {
        if (!isSpeqaFile(file)) return
        // Rescan is simplest — file content changed, old id may differ from new
        scan()
    }

    private fun isSpeqaFile(file: VirtualFile): Boolean {
        return file.name.endsWith(".${SpeqaDefaults.TEST_CASE_EXTENSION}") ||
            file.name.endsWith(".${SpeqaDefaults.TEST_RUN_EXTENSION}")
    }

    companion object {
        fun getInstance(project: Project): SpeqaIdRegistry = project.service()

        private val ID_REGEX = Regex("""^id:\s*(\d+)\s*$""", RegexOption.MULTILINE)

        fun extractId(file: VirtualFile): Pair<IdType, Int>? {
            val type = when {
                file.name.endsWith(".${SpeqaDefaults.TEST_CASE_EXTENSION}") -> IdType.TEST_CASE
                file.name.endsWith(".${SpeqaDefaults.TEST_RUN_EXTENSION}") -> IdType.TEST_RUN
                else -> return null
            }
            // Read only first 512 bytes for performance (frontmatter is at the top)
            val bytes = try {
                val stream = file.inputStream
                val buf = ByteArray(512)
                val read = stream.read(buf)
                stream.close()
                if (read <= 0) return null
                buf.copyOf(read)
            } catch (_: Exception) {
                return null
            }
            val header = String(bytes, Charsets.UTF_8)
            val match = ID_REGEX.find(header) ?: return null
            val id = match.groupValues[1].toIntOrNull() ?: return null
            return type to id
        }
    }
}
```

- [ ] **Step 4: Register startup activity in plugin.xml**

Add inside `<extensions defaultExtensionNs="com.intellij">`:
```xml
<postStartupActivity implementation="io.github.barsia.speqa.registry.SpeqaIdRegistryStartup"/>
```

Create the startup class:
```kotlin
// src/main/kotlin/io/speqa/speqa/registry/SpeqaIdRegistryStartup.kt
package io.github.barsia.speqa.registry

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

class SpeqaIdRegistryStartup : ProjectActivity {
    override suspend fun execute(project: Project) {
        SpeqaIdRegistry.getInstance(project).ensureInitialized()
    }
}
```

- [ ] **Step 5: Run tests**

Run: `./gradlew test -v`
Expected: All tests PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/io/speqa/speqa/registry/ src/test/kotlin/io/speqa/speqa/registry/ src/main/resources/META-INF/plugin.xml
git commit -m "feat: add SpeqaIdRegistry project service with VFS listener and startup scan"
```

---

### Task 4: Update file template and CreateTestCaseAction to inject ID

**Files:**
- Modify: `src/main/resources/fileTemplates/internal/Speqa Test Case.tc.md.ft`
- Modify: `src/main/kotlin/io/speqa/speqa/actions/CreateTestCaseAction.kt`
- Modify: `src/main/kotlin/io/speqa/speqa/editor/SpeqaEditorSupport.kt`

- [ ] **Step 1: Update file template**

Replace `src/main/resources/fileTemplates/internal/Speqa Test Case.tc.md.ft` with:
```
---
id: ${ID}
title: "${NAME}"
priority: medium
status: draft
---

Preconditions: 

1. Action
   > Result
```

- [ ] **Step 2: Update CreateTestCaseAction to inject ID**

In `CreateTestCaseAction.kt`, override `createFile` to inject the ID as a template property. Replace the `createFile` method:

```kotlin
override fun createFile(name: String, templateName: String, dir: PsiDirectory): PsiFile {
    val targetDir = resolveTargetDirectory(dir)
    val project = dir.project
    val registry = SpeqaIdRegistry.getInstance(project)
    registry.ensureInitialized()
    val nextId = registry.idSet(IdType.TEST_CASE).nextFreeId()

    // IntelliJ template system uses Properties for variable substitution
    val props = java.util.Properties()
    props.setProperty("ID", nextId.toString())

    val template = com.intellij.ide.fileTemplates.FileTemplateManager.getInstance(project)
        .getInternalTemplate("Speqa Test Case.tc.md")
    val fileName = normalizeFileName(name)
    val psiFile = com.intellij.ide.fileTemplates.FileTemplateUtil
        .createFromTemplate(template, fileName, props, targetDir) as PsiFile

    registry.idSet(IdType.TEST_CASE).register(nextId)
    return psiFile
}
```

Remove the `super.createFile(...)` call — we now create the file directly via `FileTemplateUtil`.

Add imports:
```kotlin
import io.github.barsia.speqa.registry.SpeqaIdRegistry
import io.github.barsia.speqa.registry.IdType
```

- [ ] **Step 3: Update startTestRun to inject TR ID**

In `SpeqaEditorSupport.kt`, in `startTestRun()`, where `TestRunSupport.createInitialRun(...)` is called, add id:

After the line `val initialRun = TestRunSupport.createInitialRun(...)`, add:
```kotlin
val trRegistry = SpeqaIdRegistry.getInstance(project)
trRegistry.ensureInitialized()
val trId = trRegistry.idSet(IdType.TEST_RUN).nextFreeId()
val initialRunWithId = initialRun.copy(id = trId)
```

Then use `initialRunWithId` instead of `initialRun` when serializing:
```kotlin
VfsUtil.saveText(file, TestRunSerializer.serialize(initialRunWithId))
```

And register the id:
```kotlin
trRegistry.idSet(IdType.TEST_RUN).register(trId)
```

Add imports:
```kotlin
import io.github.barsia.speqa.registry.SpeqaIdRegistry
import io.github.barsia.speqa.registry.IdType
```

- [ ] **Step 4: Verify compilation**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/fileTemplates/ src/main/kotlin/io/speqa/speqa/actions/ src/main/kotlin/io/speqa/speqa/editor/SpeqaEditorSupport.kt
git commit -m "feat: inject auto-increment ID at test case and test run creation"
```

---

### Task 5: Add i18n strings for ID feature

**Files:**
- Modify: `src/main/resources/messages/SpeqaBundle.properties`

- [ ] **Step 1: Add ID-related strings**

Append to `SpeqaBundle.properties`:
```properties

# --- ID ---
label.id=ID
label.idPrefix.tc=TC-
label.idPrefix.tr=TR-
label.assignId=Click to assign ID
tooltip.assignId=Click to assign ID
tooltip.editId=Edit ID
tooltip.confirmId=Confirm ID
id.duplicate=TC-{0} is already in use
id.duplicateTr=TR-{0} is already in use
annotator.duplicateTestCaseId=Duplicate test case ID: TC-{0}
annotator.duplicateTestRunId=Duplicate test run ID: TR-{0}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/resources/messages/SpeqaBundle.properties
git commit -m "feat: add i18n strings for ID system"
```

---

### Task 6: Add ID display and inline editing to header UI

**Files:**
- Modify: `src/main/kotlin/io/speqa/speqa/editor/ui/EditorPrimitives.kt`
- Modify: `src/main/kotlin/io/speqa/speqa/editor/ui/TestCasePreview.kt`
- Modify: `src/main/kotlin/io/speqa/speqa/editor/ui/TestCaseForm.kt`

- [ ] **Step 1: Create InlineEditableIdRow composable in EditorPrimitives.kt**

Add a new composable after `InlineEditableTitleRow`:

```kotlin
@OptIn(ExperimentalJewelApi::class)
@Composable
internal fun InlineEditableIdRow(
    id: Int?,
    idType: IdType,
    nextFreeId: Int,
    isDuplicate: Boolean,
    onIdAssign: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val prefix = when (idType) {
        IdType.TEST_CASE -> SpeqaBundle.message("label.idPrefix.tc")
        IdType.TEST_RUN -> SpeqaBundle.message("label.idPrefix.tr")
    }

    if (id == null) {
        // Unassigned: show placeholder with next free ID in red
        val displayText = "$prefix$nextFreeId"
        Box(
            modifier = modifier
                .clickable { onIdAssign(nextFreeId) }
                .padding(vertical = 2.dp),
        ) {
            Tooltip(tooltip = { Text(SpeqaBundle.message("tooltip.assignId")) }) {
                Text(
                    displayText,
                    color = Color(0xFFCC3333),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    } else {
        // Assigned: show TC-N, inline-editable number
        var isEditing by rememberSaveable { mutableStateOf(false) }
        var draftNumber by rememberSaveable(id) { mutableStateOf(id.toString()) }
        val focusRequester = remember { FocusRequester() }

        LaunchedEffect(isEditing) {
            if (isEditing) focusRequester.requestFocus()
        }
        LaunchedEffect(id) {
            if (!isEditing) draftNumber = id.toString()
        }

        fun commit() {
            if (!isEditing) return
            val parsed = draftNumber.trim().toIntOrNull()
            if (parsed != null && parsed != id) {
                onIdAssign(parsed)
            }
            isEditing = false
        }

        Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(prefix, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = SpeqaThemeColors.mutedForeground)

            if (isEditing) {
                BasicTextField(
                    value = draftNumber,
                    onValueChange = { draftNumber = it.filter(Char::isDigit) },
                    textStyle = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = SpeqaThemeColors.foreground),
                    singleLine = true,
                    modifier = Modifier
                        .width(48.dp)
                        .focusRequester(focusRequester)
                        .onFocusChanged { if (!it.isFocused && isEditing) commit() }
                        .onPreviewKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            when (event.key) {
                                Key.Enter, Key.NumPadEnter -> { commit(); true }
                                Key.Escape -> { draftNumber = id.toString(); isEditing = false; true }
                                else -> false
                            }
                        },
                )
            } else {
                Text(
                    id.toString(),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = SpeqaThemeColors.foreground,
                    modifier = Modifier.clickable { isEditing = true },
                )
            }

            // Duplicate warning tooltip
            if (isDuplicate) {
                val msg = when (idType) {
                    IdType.TEST_CASE -> SpeqaBundle.message("id.duplicate", id)
                    IdType.TEST_RUN -> SpeqaBundle.message("id.duplicateTr", id)
                }
                Tooltip(tooltip = { Text(msg) }) {
                    Text("!", color = Color(0xFFCC3333), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
    }
}
```

Add import: `import io.github.barsia.speqa.registry.IdType`

- [ ] **Step 2: Add ID row to TestCasePreview header**

In `TestCasePreview.kt`, in the `PreviewHeader` composable, add `InlineEditableIdRow` before the title row. The preview needs access to `SpeqaIdRegistry` — pass `id`, `nextFreeId`, `isDuplicate`, and `onIdAssign` callback through the composable parameters.

Add new parameters to `TestCasePreview`:
```kotlin
internal fun TestCasePreview(
    testCase: TestCase,
    headerMeta: TestCaseHeaderMeta,
    nextFreeTestCaseId: Int,
    isIdDuplicate: Boolean,
    onRun: () -> Unit,
    onTitleCommit: (String) -> Unit,
    onIdAssign: (Int) -> Unit,
    onPriorityChange: ((Priority) -> Unit)?,
    onStatusChange: ((Status) -> Unit)?,
    modifier: Modifier = Modifier,
)
```

In the header, before the title row:
```kotlin
InlineEditableIdRow(
    id = testCase.id,
    idType = IdType.TEST_CASE,
    nextFreeId = nextFreeTestCaseId,
    isDuplicate = isIdDuplicate,
    onIdAssign = onIdAssign,
)
```

- [ ] **Step 3: Add ID row to TestCaseForm header**

Same pattern as preview — add parameters and `InlineEditableIdRow` to the header strip.

- [ ] **Step 4: Wire registry in SpeqaPreviewEditor and SpeqaFormEditor**

In `SpeqaPreviewEditor.kt` and `SpeqaFormEditor.kt`, compute `nextFreeId` and `isDuplicate` from `SpeqaIdRegistry` and pass to the composable:

```kotlin
val registry = SpeqaIdRegistry.getInstance(project)
registry.ensureInitialized()
val nextFreeId = registry.idSet(IdType.TEST_CASE).nextFreeId()
val isDuplicate = parsed.testCase.id?.let { registry.idSet(IdType.TEST_CASE).isDuplicate(it) } ?: false
```

Wire `onIdAssign`:
```kotlin
onIdAssign = { newId ->
    val updated = parsed.testCase.copy(id = newId)
    parsed = ParsedTestCase(updated, parsed.parseError)
    writeTestCaseToDocument(project, document, updated, "Speqa: Assign ID")
}
```

- [ ] **Step 5: Verify compilation**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/io/speqa/speqa/editor/
git commit -m "feat: add inline-editable ID display in test case header"
```

---

### Task 7: Add duplicate ID detection to SpeqaAnnotator

**Files:**
- Modify: `src/main/kotlin/io/speqa/speqa/validation/SpeqaAnnotator.kt`

- [ ] **Step 1: Add duplicate ID warning**

In `SpeqaAnnotator.kt`, after the title-not-set check, add:

```kotlin
// Warning: duplicate ID
testCase.id?.let { id ->
    val registry = SpeqaIdRegistry.getInstance(file.project)
    registry.ensureInitialized()
    if (registry.idSet(IdType.TEST_CASE).isDuplicate(id)) {
        val idRange = findFrontmatterValueRange(text, "id")
        if (idRange != null) {
            holder.warn(SpeqaBundle.message("annotator.duplicateTestCaseId", id), idRange)
        }
    }
}
```

Add imports:
```kotlin
import io.github.barsia.speqa.registry.SpeqaIdRegistry
import io.github.barsia.speqa.registry.IdType
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Run all tests**

Run: `./gradlew test -v`
Expected: All tests PASS.

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/io/speqa/speqa/validation/SpeqaAnnotator.kt
git commit -m "feat: add duplicate ID detection in SpeqaAnnotator"
```

---

### Task 8: Add TR-N display to TestRunPanel header

**Files:**
- Modify: `src/main/kotlin/io/speqa/speqa/run/TestRunPanel.kt`
- Modify: `src/main/kotlin/io/speqa/speqa/run/TestRunEditor.kt`

- [ ] **Step 1: Add ID to TestRunPanel**

In `TestRunPanel.kt`, add `id: Int?` parameter and display `TR-N` in the header section. Use `InlineEditableIdRow` with `IdType.TEST_RUN`.

Add parameters to `TestRunPanel`:
```kotlin
fun TestRunPanel(
    testCase: TestCase,
    runId: Int?,
    nextFreeRunId: Int,
    isRunIdDuplicate: Boolean,
    onRunIdAssign: (Int) -> Unit,
    stepResults: List<StepResult>,
    ...
)
```

In the header area, add before the title:
```kotlin
InlineEditableIdRow(
    id = runId,
    idType = IdType.TEST_RUN,
    nextFreeId = nextFreeRunId,
    isDuplicate = isRunIdDuplicate,
    onIdAssign = onRunIdAssign,
)
```

- [ ] **Step 2: Wire in TestRunEditor**

In `TestRunEditor.kt`, compute registry values and pass to `TestRunPanel`. Wire `onRunIdAssign` to update the document.

- [ ] **Step 3: Verify compilation and run tests**

Run: `./gradlew compileKotlin && ./gradlew test`
Expected: BUILD SUCCESSFUL, all tests PASS.

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/io/speqa/speqa/run/
git commit -m "feat: add TR-N ID display and editing in test run panel"
```

---

## Summary

| Task | Component | Key Changes |
|------|-----------|-------------|
| 1 | Data Model | Add `id: Int?` to `TestCase` and `TestRun` |
| 2 | Parser/Serializer | Parse and serialize `id` in frontmatter |
| 3 | SpeqaIdRegistry | Project service with startup scan + VFS listener |
| 4 | Template + Actions | Inject ID at file creation |
| 5 | i18n | Bundle strings for ID feature |
| 6 | Header UI | Inline-editable ID row with duplicate warning |
| 7 | Annotator | Duplicate ID underline in text editor |
| 8 | TestRunPanel | TR-N display and editing |
