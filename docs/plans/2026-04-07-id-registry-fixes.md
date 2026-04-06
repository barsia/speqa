# ID Registry & Editor Fixes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix 13 review findings: thread safety in IdSet, double-counting race, missing debounce/refresh parity, TestRunEditor state hoisting, VFS filter, unused icons, and spec update for scan scope.

**Architecture:** Make `IdSet` thread-safe with `ConcurrentHashMap`, remove manual register/unregister in editors (rely on VFS listener + immediate local state), add debounce and periodic refresh to all editors consistently, extract shared ID state helper, remove dead icon entries.

**Tech Stack:** Kotlin, IntelliJ Platform SDK, Compose (Jewel), javax.swing.Timer

---

### Task 1: Make `IdSet` thread-safe and fix VFS listener

**Files:**
- Modify: `src/main/kotlin/io/speqa/speqa/registry/SpeqaIdRegistry.kt`

- [ ] **Step 1: Replace `mutableMapOf` with `ConcurrentHashMap` in `IdSet`**

```kotlin
import java.util.concurrent.ConcurrentHashMap

class IdSet {
    private val counts = ConcurrentHashMap<Int, Int>()

    fun register(id: Int) {
        counts.merge(id, 1, Int::plus)
    }

    fun unregister(id: Int) {
        counts.computeIfPresent(id) { _, count -> if (count <= 1) null else count - 1 }
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
```

Key changes:
- `ConcurrentHashMap` replaces `mutableMapOf` — safe for concurrent reads from EDT while VFS listener writes from pooled thread
- `register()` uses `merge()` — atomic increment
- `unregister()` uses `computeIfPresent()` — atomic decrement-or-remove

- [ ] **Step 2: Filter `VFileDeleteEvent` by Speqa file type and use `invokeLater` for scan**

Replace the `subscribeToVfsEvents` method and `handleFileChange`:

```kotlin
import com.intellij.openapi.application.invokeLater

private fun subscribeToVfsEvents() {
    project.messageBus.connect().subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
        override fun after(events: List<VFileEvent>) {
            val hasSpeqaChange = events.any { event ->
                when (event) {
                    is VFileCreateEvent -> event.childName.endsWith(".${SpeqaDefaults.TEST_CASE_EXTENSION}") ||
                        event.childName.endsWith(".${SpeqaDefaults.TEST_RUN_EXTENSION}")
                    is VFileDeleteEvent -> isSpeqaFile(event.file)
                    is VFileContentChangeEvent -> isSpeqaFile(event.file)
                    else -> false
                }
            }
            if (hasSpeqaChange) {
                invokeLater { scan() }
            }
        }
    })
}
```

Remove the old `handleFileChange` method entirely.

Key changes:
- Delete events now filtered by `isSpeqaFile()` — no more full rescan on deleting any file
- Create events checked via `event.childName` since the `file` may be null
- Batch: multiple events in one VFS batch → single scan
- `invokeLater` ensures scan runs on EDT, matching ConcurrentHashMap write visibility

- [ ] **Step 3: Expand `skipDirs` to cover common non-source directories**

```kotlin
private val skipDirs = setOf(
    ".git", ".idea", ".gradle", ".intellijPlatform",
    "build", "out", "target", "dist",
    "node_modules", "vendor",
)
```

- [ ] **Step 4: Run `./gradlew compileKotlin` to verify**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/io/speqa/speqa/registry/SpeqaIdRegistry.kt
git commit -m "fix: thread-safe IdSet, filter VFS events, expand skipDirs

- Replace mutableMapOf with ConcurrentHashMap for safe concurrent access
- Filter VFileDeleteEvent by isSpeqaFile() before triggering rescan
- Batch VFS events into single scan via invokeLater
- Add out, target, dist, vendor to skipDirs

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

---

### Task 2: Extract shared `IdStateHolder` to eliminate duplication

**Files:**
- Create: `src/main/kotlin/io/speqa/speqa/editor/IdStateHolder.kt`
- Modify: `src/main/kotlin/io/speqa/speqa/editor/SpeqaFormEditor.kt`
- Modify: `src/main/kotlin/io/speqa/speqa/editor/SpeqaPreviewEditor.kt`
- Modify: `src/main/kotlin/io/speqa/speqa/run/TestRunEditor.kt`

- [ ] **Step 1: Create `IdStateHolder` class**

Create `src/main/kotlin/io/speqa/speqa/editor/IdStateHolder.kt`:

```kotlin
package io.github.barsia.speqa.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.intellij.openapi.project.Project
import io.github.barsia.speqa.registry.IdType
import io.github.barsia.speqa.registry.SpeqaIdRegistry
import javax.swing.Timer

class IdStateHolder(
    private val project: Project,
    private val idType: IdType,
    private val currentId: () -> Int?,
) {
    var nextFreeId by mutableStateOf(computeNextFreeId())
        private set
    var isDuplicate by mutableStateOf(computeIsDuplicate())
        private set
    var isEditing by mutableStateOf(false)

    private val refreshTimer = Timer(2000) {
        refresh()
    }.apply {
        isRepeats = true
    }

    private fun computeNextFreeId(): Int {
        val registry = SpeqaIdRegistry.getInstance(project)
        registry.ensureInitialized()
        return registry.idSet(idType).nextFreeId()
    }

    private fun computeIsDuplicate(): Boolean {
        val registry = SpeqaIdRegistry.getInstance(project)
        registry.ensureInitialized()
        return currentId()?.let { registry.idSet(idType).isDuplicate(it) } ?: false
    }

    fun refresh() {
        nextFreeId = computeNextFreeId()
        isDuplicate = computeIsDuplicate()
    }

    fun start() {
        refreshTimer.start()
    }

    fun stop() {
        refreshTimer.stop()
    }
}
```

- [ ] **Step 2: Refactor `SpeqaFormEditor` to use `IdStateHolder`**

Replace the ID state fields and methods in `SpeqaFormEditor`:

Remove these lines (41-60):
```kotlin
    private var nextFreeId by mutableStateOf(computeNextFreeId())
    private var isIdDuplicate by mutableStateOf(computeIsIdDuplicate())
    private var isIdEditing by mutableStateOf(false)

    private fun computeNextFreeId(): Int { ... }
    private fun computeIsIdDuplicate(): Boolean { ... }
    private fun refreshIdState() { ... }
```

Replace with:
```kotlin
    private val idState = IdStateHolder(project, IdType.TEST_CASE) { parsed.testCase.id }
```

In the `documentChanged` listener, replace `refreshIdState()` with `idState.refresh()`.

In the `composePanel` content, replace references:
- `nextFreeTestCaseId = nextFreeId` → `nextFreeTestCaseId = idState.nextFreeId`
- `isIdDuplicate = isIdDuplicate` → `isIdDuplicate = idState.isDuplicate`
- `isIdEditing = isIdEditing` → `isIdEditing = idState.isEditing`
- `onIdEditingChange = { isIdEditing = it }` → `onIdEditingChange = { idState.isEditing = it }`

In `onIdAssign`, remove the manual register/unregister block:
```kotlin
// REMOVE these lines:
val registry = SpeqaIdRegistry.getInstance(project)
registry.idSet(IdType.TEST_CASE).register(newId)
oldId?.let { if (it != newId) registry.idSet(IdType.TEST_CASE).unregister(it) }
```

Keep `idState.refresh()` at the end of `onIdAssign`.

In `init`, add `idState.start()`.

Add `dispose()` body:
```kotlin
override fun dispose() {
    idState.stop()
}
```

- [ ] **Step 3: Refactor `SpeqaPreviewEditor` to use `IdStateHolder`**

Remove lines 44-63 (nextFreeId, isIdDuplicate, isIdEditing, compute*, refreshIdState).

Remove `idRefreshTimer` (lines 80-85) and its stop in `dispose()`.

Replace with:
```kotlin
    private val idState = IdStateHolder(project, IdType.TEST_CASE) { parsed.testCase.id }
```

In `refreshTimer` callback, replace `refreshIdState()` with `idState.refresh()`.

In `composePanel` content, update references same as Step 2.

In `onIdAssign`, remove manual register/unregister. Keep `idState.refresh()`.

In `init`, add `idState.start()`.

Update `dispose()`:
```kotlin
override fun dispose() {
    refreshTimer.stop()
    idState.stop()
}
```

- [ ] **Step 4: Refactor `TestRunEditor` to use `IdStateHolder`**

Add a class-level `IdStateHolder`:
```kotlin
    private val idState = IdStateHolder(project, IdType.TEST_RUN) { runId }
```

Remove `isRunIdEditing` field. Remove the inline registry calls inside the compose panel (lines 58-61):
```kotlin
// REMOVE:
val registry = SpeqaIdRegistry.getInstance(project)
registry.ensureInitialized()
val nextFreeRunId = registry.idSet(IdType.TEST_RUN).nextFreeId()
val isRunIdDuplicate = runId?.let { registry.idSet(IdType.TEST_RUN).isDuplicate(it) } ?: false
```

Replace with references to `idState`:
```kotlin
                nextFreeRunId = idState.nextFreeId,
                isRunIdDuplicate = idState.isDuplicate,
                isRunIdEditing = idState.isEditing,
                onRunIdEditingChange = { idState.isEditing = it },
                onRunIdAssign = { newId ->
                    runId = newId
                    saveToDocument()
                    idState.refresh()
                },
```

Remove the manual register/unregister from `onRunIdAssign`.

Add `init` block and update `dispose()`:
```kotlin
init {
    idState.start()
}

override fun dispose() {
    idState.stop()
}
```

- [ ] **Step 5: Run `./gradlew compileKotlin` to verify**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/io/speqa/speqa/editor/IdStateHolder.kt \
  src/main/kotlin/io/speqa/speqa/editor/SpeqaFormEditor.kt \
  src/main/kotlin/io/speqa/speqa/editor/SpeqaPreviewEditor.kt \
  src/main/kotlin/io/speqa/speqa/run/TestRunEditor.kt
git commit -m "refactor: extract IdStateHolder, remove manual register/unregister

- Shared IdStateHolder provides nextFreeId, isDuplicate, isEditing
  with 2s periodic refresh for all editors
- Remove manual register()/unregister() from onIdAssign — VFS
  listener handles registry updates after document write
- TestRunEditor ID state now hoisted out of Compose tree

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

---

### Task 3: Add debounce to `SpeqaFormEditor` document listener

**Files:**
- Modify: `src/main/kotlin/io/speqa/speqa/editor/SpeqaFormEditor.kt`

- [ ] **Step 1: Add a debounce timer for ID refresh in SpeqaFormEditor**

The document listener should still update `parsed` and `headerMeta` immediately (the form needs instant feedback), but `idState.refresh()` should be debounced since it iterates the registry map.

Add a timer field before the `documentListener`:

```kotlin
    private val idRefreshDebounceTimer = Timer(300) {
        idState.refresh()
    }.apply {
        isRepeats = false
    }
```

Change the `documentChanged` listener:

```kotlin
    private val documentListener = object : DocumentListener {
        override fun documentChanged(event: DocumentEvent) {
            parsed = parseTestCaseSafely(event.document.text)
            headerMeta = resolveTestCaseHeaderMeta(project, file)
            idRefreshDebounceTimer.restart()
        }
    }
```

Update `dispose()`:
```kotlin
    override fun dispose() {
        idRefreshDebounceTimer.stop()
        idState.stop()
    }
```

- [ ] **Step 2: Run `./gradlew compileKotlin` to verify**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/io/speqa/speqa/editor/SpeqaFormEditor.kt
git commit -m "fix: debounce ID refresh in SpeqaFormEditor (300ms)

Prevents nextFreeId() map iteration on every keystroke.
Parsed state and headerMeta still update immediately.

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

---

### Task 4: Remove unused `SpeqaIcons` calendar entries and fix `DateIconLabel` icon loading

**Files:**
- Modify: `src/main/kotlin/io/speqa/speqa/filetype/SpeqaIcons.kt`
- Modify: `src/main/kotlin/io/speqa/speqa/editor/ui/EditorPrimitives.kt`

- [ ] **Step 1: Remove unused calendar icon entries from `SpeqaIcons`**

Remove these two lines from `SpeqaIcons.kt`:
```kotlin
    val CalendarCreated: Icon = IconLoader.getIcon("/icons/calendarCreated.svg", SpeqaIcons::class.java)
    val CalendarUpdated: Icon = IconLoader.getIcon("/icons/calendarUpdated.svg", SpeqaIcons::class.java)
```

These are Swing `Icon` instances that are never referenced — the Compose side uses `IntelliJIconKey` directly.

- [ ] **Step 2: Change `DateIconLabel` to accept `IconKey` parameter**

In `EditorPrimitives.kt`, change the `DateIconLabel` signature and implementation to accept a pre-built `IconKey` instead of a raw path string. This makes the API explicit about what icon system is used:

```kotlin
@OptIn(ExperimentalJewelApi::class, ExperimentalFoundationApi::class)
@Composable
internal fun DateIconLabel(
    iconKey: IconKey,
    tooltipText: String,
    dateLabel: String,
    modifier: Modifier = Modifier,
) {
    val color = SpeqaThemeColors.mutedForeground
    Tooltip(
        tooltip = { Text(tooltipText) },
    ) {
        Row(
            modifier = modifier.height(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                key = iconKey,
                contentDescription = tooltipText,
                modifier = Modifier.size(12.dp).offset(y = 2.dp),
                tint = color,
            )
            Text(
                text = dateLabel,
                fontSize = 12.sp,
                lineHeight = 12.sp,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
```

Remove the unused `import org.jetbrains.jewel.ui.icon.IconKey` if it was only used by `DateIconLabel` (check first — it may be used elsewhere).

- [ ] **Step 3: Update callers in `TestCaseForm.kt` and `TestCasePreview.kt`**

In both `TestCaseForm.kt` (UtilityRow) and `TestCasePreview.kt` (HeaderUtilityRow), update the `DateIconLabel` calls. Change from:

```kotlin
DateIconLabel(
    iconPath = "/icons/calendarCreated.svg",
    tooltipText = ...,
    dateLabel = ...,
)
```

To:

```kotlin
DateIconLabel(
    iconKey = IntelliJIconKey("/icons/calendarCreated.svg", "/icons/calendarCreated.svg", iconClass = SpeqaLayout::class.java),
    tooltipText = ...,
    dateLabel = ...,
)
```

Same for `calendarUpdated.svg`. The `IntelliJIconKey` import should already be available in these files. If not, add: `import org.jetbrains.jewel.ui.icon.IntelliJIconKey`.

- [ ] **Step 4: Run `./gradlew compileKotlin` to verify**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/io/speqa/speqa/filetype/SpeqaIcons.kt \
  src/main/kotlin/io/speqa/speqa/editor/ui/EditorPrimitives.kt \
  src/main/kotlin/io/speqa/speqa/editor/ui/TestCaseForm.kt \
  src/main/kotlin/io/speqa/speqa/editor/ui/TestCasePreview.kt
git commit -m "fix: remove unused SpeqaIcons calendar entries, type DateIconLabel param

- CalendarCreated/CalendarUpdated were dead code (Swing Icons never
  referenced — Compose uses IntelliJIconKey)
- DateIconLabel now accepts IconKey instead of raw path string

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

---

### Task 5: Update spec to document scan scope and focus-loss behavior

**Files:**
- Modify: `docs/specs/2026-04-06-speqa-mvp-design.md`

- [ ] **Step 1: Update spec — ID system scan scope**

In the spec section about the ID system (the `**ID system:**` bullet under Key Decisions in section 3), the current text already says "scans entire project at startup (skipping `.git`, `build`, `node_modules`)". Update the skip list to match the code:

Find:
```
scans entire project at startup (skipping `.git`, `build`, `node_modules`)
```

Replace with:
```
scans entire project at startup (skipping `.git`, `.idea`, `.gradle`, `.intellijPlatform`, `build`, `out`, `target`, `dist`, `node_modules`, `vendor`)
```

- [ ] **Step 2: Update spec — ID duplicate detection mechanism**

Find the `**ID duplicate detection:**` bullet. Update it to clarify the VFS-driven mechanism:

Find:
```
Periodic refresh (2s) catches cross-file duplicates. Does not block saving
```

Replace with:
```
VFS listener triggers rescan on Speqa file changes; 2s periodic refresh in all editors catches cross-file duplicates. Does not block saving
```

- [ ] **Step 3: Update spec — ID focus-loss behavior**

In the `**ID in header UI:**` bullet, the current text says "No auto-commit on focus loss." This is the intentional spec decision. Add a clarifying note to distinguish from title behavior:

Find:
```
No auto-commit on focus loss.
```

Replace with:
```
No auto-commit on focus loss (unlike title, which auto-commits on focus loss).
```

- [ ] **Step 4: Commit**

```bash
git add docs/specs/2026-04-06-speqa-mvp-design.md
git commit -m "docs: update spec — scan scope dirs, VFS-driven refresh, focus-loss note

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

---

### Task 6: Verify SVG icons render correctly at 1x and 2x DPI

**Files:**
- Modify (if needed): `src/main/resources/icons/testCaseDraft.svg`
- Modify (if needed): `src/main/resources/icons/testCaseReady.svg`
- Modify (if needed): `src/main/resources/icons/testCaseDeprecated.svg`
- Modify (if needed): `src/main/resources/META-INF/pluginIcon.svg`

- [ ] **Step 1: Verify test case SVG icons render at 16x16**

Open each SVG in a browser at native 16x16 size. Check:
- Circles at `r="0.7"` are visible (they render as ~1.4px dots)
- The rotation transform doesn't clip content outside the 16x16 viewBox
- Draft (gray), Ready (green), and Deprecated (red + strikethrough) are visually distinct

If circles are too small at 1x, increase radius to `r="0.9"` and adjust positions slightly to stay within viewBox after rotation.

- [ ] **Step 2: Verify pluginIcon.svg path renders "SpeQA"**

Open `src/main/resources/META-INF/pluginIcon.svg` in a browser. Verify:
- The SVG `<path>` element renders the text "SpeQA" inside the banner
- The text is centered and legible at both 40x40 (Marketplace listing) and 16x16 (toolbar)

- [ ] **Step 3: If fixes needed, commit**

Only commit if SVG changes were required:
```bash
git add src/main/resources/icons/*.svg src/main/resources/META-INF/pluginIcon.svg
git commit -m "fix: adjust SVG icon sizes for 1x DPI rendering

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

---

### Task 7: Final verification

- [ ] **Step 1: Run full compile**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Verify no unused imports**

Run: `./gradlew compileKotlin -Werror` (if warning-as-error is configured) or manually check files modified in Tasks 1-4 for unused imports.

- [ ] **Step 3: Verify the complete list of fixes**

Checklist of all 13 findings:
1. Thread safety: `IdSet` uses `ConcurrentHashMap` ✓ (Task 1)
2. Double-counting race: manual register/unregister removed ✓ (Task 2)
3. `rootPath` scan scope: spec already correct, code matches ✓ (Task 1 note / Task 5)
4. No debounce in FormEditor: debounce added ✓ (Task 3)
5. `idRefreshTimer` missing from FormEditor: `IdStateHolder` provides it ✓ (Task 2)
6. `TestRunEditor` ID not hoisted: now uses `IdStateHolder` ✓ (Task 2)
7. Unfiltered `VFileDeleteEvent`: filtered by `isSpeqaFile` ✓ (Task 1)
8. `skipDirs` incomplete: expanded ✓ (Task 1)
9. Focus-loss inconsistency: documented in spec as intentional ✓ (Task 5)
10. Dead code `SpeqaIcons.CalendarCreated/Updated`: removed ✓ (Task 4)
11. Code duplication: extracted `IdStateHolder` ✓ (Task 2)
12. SVG rendering at 1x DPI: verified ✓ (Task 6)
13. `IntelliJIconKey` light/dark tint: `DateIconLabel` uses `tint` param ✓ (Task 4)
