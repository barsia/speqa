# Live ID Duplicate Detection via FileBasedIndex Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the disk-scanning `SpeqaIdRegistry` with a platform `FileBasedIndex` so test-case/test-run ID duplicate detection reflects unsaved in-editor content and lights up through the daemon's native debounce, with no hand-rolled document listeners, debounce timers, or daemon restarts.

**Architecture:** A `FileBasedIndex` keyed by `"TC:<id>"` / `"TR:<id>"` over `.tc.md`/`.tr.md` files. The platform reindexes unsaved documents at query time, so `getContainingFiles` during highlighting sees the live buffer. A stateless `SpeqaIds` facade wraps the index for the four needs: is-duplicate, used-ids, next-free-id, all-entries. Because `getContainingFiles` is an over-approximation, every candidate is re-verified against its current parsed id before being counted. The old project-scanning service, its startup activity, the in-memory `IdSet`, and the manual daemon-restart are all removed.

**Tech Stack:** Kotlin, IntelliJ Platform SDK (`FileBasedIndex`, `ScalarIndexExtension`, `DumbService`, `GlobalSearchScope`), JUnit 4.

**SDK verification note:** FileBasedIndex APIs (`ScalarIndexExtension`, `DataIndexer`, `FileBasedIndex.InputFilter`, `getContainingFiles`, `processAllKeys`, `ID.create`) are given here in their canonical form. If a signature differs on platform 2026.1 (the local SDK), make the minimal change to compile while preserving behavior, and note the adjustment in the task report. Verified fact this plan relies on: `FileBasedIndex` reflects unsaved/uncommitted documents at query time (it indexes them on the fly before serving `getContainingFiles`/`processAllKeys`), so highlighting sees the live editor buffer. `getContainingFiles` may also return stale/extra files, so candidates MUST be re-verified against current content.

---

## File Structure

- `registry/IdType.kt` (create): move the `IdType` enum out of the doomed `SpeqaIdRegistry.kt` so consumers keep importing `io.github.barsia.speqa.registry.IdType`.
- `registry/SpeqaIdIndex.kt` (create): the `FileBasedIndex` extension + pure key-mapping helper + key/name constants.
- `registry/SpeqaIds.kt` (create): stateless query facade (`isDuplicate`, `usedIds`, `nextFreeId`, `containingFiles`, `allEntries`) with dumb-mode guards and candidate verification.
- `registry/SpeqaIdRegistry.kt` (delete): disk scan, VFS listener, `IdSet`, manual daemon restart, all superseded.
- `registry/SpeqaIdRegistryStartup.kt` (delete): no startup scan needed; the index self-builds.
- `registry/DuplicateIdResolver.kt` (keep, unchanged): pure renumber planner.
- `validation/SpeqaAnnotator.kt` (modify): query `SpeqaIds.isDuplicate`.
- `validation/AssignNextFreeIdFix.kt` (modify): allocate via `SpeqaIds.nextFreeId`; drop registry register/unregister.
- `editor/IdStateHolder.kt` (modify): query `SpeqaIds`.
- `actions/CreateTestCaseAction.kt` (modify): allocate via `SpeqaIds.nextFreeId`; drop `register`.
- `editor/SpeqaEditorSupport.kt` (modify): allocate TR id via `SpeqaIds.nextFreeId`; drop `register`.
- `actions/ResolveDuplicateIdsAction.kt` (modify): source entries from `SpeqaIds.allEntries`; drop the file walk, `extractId`, and `SCAN_SKIP_DIRS`.
- `META-INF/plugin.xml` (modify): register `<fileBasedIndex>`; remove the `postStartupActivity` for `SpeqaIdRegistryStartup`.
- Tests: create `registry/SpeqaIdIndexTest.kt` (pure mapping); delete `registry/SpeqaIdRegistryTest.kt`, `registry/SpeqaIdRegistryInitTest.kt`, `registry/IdSetTest.kt`; keep `registry/DuplicateIdResolverTest.kt`.
- `docs/specs/2026-04-06-speqa-design.md` (modify): rewrite the ID-detection contract to the index model.

---

### Task 1: Decouple `IdType` and update the spec contract

**Files:**
- Create: `src/main/kotlin/io/github/barsia/speqa/registry/IdType.kt`
- Modify: `src/main/kotlin/io/github/barsia/speqa/registry/SpeqaIdRegistry.kt` (remove the enum)
- Modify: `docs/specs/2026-04-06-speqa-design.md`

- [ ] **Step 1: Create the standalone enum**

Create `src/main/kotlin/io/github/barsia/speqa/registry/IdType.kt`:

```kotlin
package io.github.barsia.speqa.registry

enum class IdType {
    TEST_CASE,
    TEST_RUN,
}
```

- [ ] **Step 2: Remove the enum from the old registry file**

In `src/main/kotlin/io/github/barsia/speqa/registry/SpeqaIdRegistry.kt`, delete the `enum class IdType() { TEST_CASE(), TEST_RUN(), }` declaration (the file is deleted entirely in Task 8, but removing the enum now avoids a duplicate-declaration compile error once `IdType.kt` exists).

- [ ] **Step 3: Update the spec contract**

In `docs/specs/2026-04-06-speqa-design.md`, set the **ID system** bullet to:

```
- **ID system:** `id` is an optional integer in frontmatter, displayed as `TC-N` / `TR-N` (prefix is UI-only). Duplicate detection and id allocation are backed by a project `FileBasedIndex` (`SpeqaIdIndex`) keyed by `"TC:<id>"` / `"TR:<id>"` over `.tc.md` / `.tr.md` files. Because the platform reindexes unsaved documents at query time, detection reflects the live editor buffer and surfaces through the daemon's normal highlighting pass (its native debounce), with no separate scan, VFS listener, or daemon restart. `getContainingFiles` is an over-approximation, so every candidate file is re-verified against its current parsed id before being counted. Queries are guarded against dumb mode (return "not duplicate" / empty while indexes are unavailable).
```

Set the **ID generation** bullet to:

```
- **ID generation:** the next free id is the smallest positive integer not present in the index's key set for that type (`SpeqaIds.nextFreeId`). Allocation does not reserve in memory; because the index reflects unsaved buffers, a freshly written or quick-fixed id is visible to the next allocation query. Auto-assigned at file creation for TC and TR.
```

Update any remaining sentence in the **ID duplicate detection**, **ID uniqueness scope**, and **Batch duplicate-ID resolver** bullets that references `SpeqaIdRegistry` scanning / `IdSet` / `reserveNextFreeId` / the 2s poll / daemon restart, so it instead describes index-backed queries. Keep the rest of those bullets (origin-agnostic resolution, earliest-created keeps, single write action, no new duplicate) intact.

- [ ] **Step 4: Verify no em dashes**

Run: `grep -n "EMDASH" docs/specs/2026-04-06-speqa-design.md || echo "ok"` (replace EMDASH with the actual em-dash glyph when running)
Expected: `ok` (the repo hook forbids em dashes in `.md`).

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/io/github/barsia/speqa/registry/IdType.kt src/main/kotlin/io/github/barsia/speqa/registry/SpeqaIdRegistry.kt docs/specs/2026-04-06-speqa-design.md
git commit -m "Move IdType to its own file; spec: index-backed ID detection"
```

Note: the project will NOT compile fully until Task 8 removes the rest of `SpeqaIdRegistry.kt`; that is expected mid-migration. Do not run `compileKotlin` as a gate for this task.

---

### Task 2: The FileBasedIndex extension + pure mapping test

**Files:**
- Create: `src/main/kotlin/io/github/barsia/speqa/registry/SpeqaIdIndex.kt`
- Test: `src/test/kotlin/io/github/barsia/speqa/registry/SpeqaIdIndexTest.kt`
- Modify: `src/main/resources/META-INF/plugin.xml`

- [ ] **Step 1: Write the failing test for the pure key mapping**

Create `src/test/kotlin/io/github/barsia/speqa/registry/SpeqaIdIndexTest.kt`:

```kotlin
package io.github.barsia.speqa.registry

import org.junit.Assert.assertEquals
import org.junit.Test

class SpeqaIdIndexTest {

    @Test
    fun maps_test_case_id_to_tc_key() {
        assertEquals(setOf("TC:73"), SpeqaIdIndex.indexKeysFor("a.tc.md", "---\nid: 73\n---\n"))
    }

    @Test
    fun maps_test_run_id_to_tr_key() {
        assertEquals(setOf("TR:5"), SpeqaIdIndex.indexKeysFor("a.tr.md", "---\nid: 5\n---\n"))
    }

    @Test
    fun no_id_yields_no_keys() {
        assertEquals(emptySet<String>(), SpeqaIdIndex.indexKeysFor("a.tc.md", "---\ntitle: x\n---\n"))
    }

    @Test
    fun non_speqa_file_yields_no_keys() {
        assertEquals(emptySet<String>(), SpeqaIdIndex.indexKeysFor("a.md", "---\nid: 73\n---\n"))
    }

    @Test
    fun key_helper_formats_by_type() {
        assertEquals("TC:73", SpeqaIdIndex.key(IdType.TEST_CASE, 73))
        assertEquals("TR:5", SpeqaIdIndex.key(IdType.TEST_RUN, 5))
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests "io.github.barsia.speqa.registry.SpeqaIdIndexTest" 2>&1 | grep -E "FAILED|BUILD|error:"`
Expected: compile FAIL, unresolved `SpeqaIdIndex`.

- [ ] **Step 3: Create the index extension**

Create `src/main/kotlin/io/github/barsia/speqa/registry/SpeqaIdIndex.kt`:

```kotlin
package io.github.barsia.speqa.registry

import com.intellij.util.indexing.DataIndexer
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.FileContent
import com.intellij.util.indexing.ID
import com.intellij.util.indexing.ScalarIndexExtension
import com.intellij.util.io.EnumeratorStringDescriptor
import com.intellij.util.io.KeyDescriptor
import io.github.barsia.speqa.model.SpeqaDefaults

/**
 * Project index mapping each test-case / test-run id to the files that declare it.
 * Keys are "TC:<id>" / "TR:<id>". The platform reindexes unsaved documents at query
 * time, so queries reflect the live editor buffer (this is what makes duplicate
 * detection update as you type, through the daemon's normal highlighting pass).
 */
class SpeqaIdIndex : ScalarIndexExtension<String>() {

    override fun getName(): ID<String, Void> = NAME

    override fun getIndexer(): DataIndexer<String, Void, FileContent> =
        DataIndexer { content ->
            indexKeysFor(content.fileName, content.contentAsText.toString()).associateWith { null }
        }

    override fun getKeyDescriptor(): KeyDescriptor<String> = EnumeratorStringDescriptor.INSTANCE

    override fun getVersion(): Int = 1

    override fun getInputFilter(): FileBasedIndex.InputFilter =
        FileBasedIndex.InputFilter { file ->
            file.name.endsWith(".${SpeqaDefaults.TEST_CASE_EXTENSION}") ||
                file.name.endsWith(".${SpeqaDefaults.TEST_RUN_EXTENSION}")
        }

    override fun dependsOnFileContent(): Boolean = true

    companion object {
        val NAME: ID<String, Void> = ID.create("io.github.barsia.speqa.id")

        /** Pure mapping: file name + content -> index keys. No platform APIs. */
        fun indexKeysFor(fileName: String, text: String): Set<String> {
            val typePrefix = when {
                fileName.endsWith(".${SpeqaDefaults.TEST_CASE_EXTENSION}") -> "TC"
                fileName.endsWith(".${SpeqaDefaults.TEST_RUN_EXTENSION}") -> "TR"
                else -> return emptySet()
            }
            val id = ID_REGEX.find(text)?.groupValues?.get(1)?.toIntOrNull() ?: return emptySet()
            return setOf("$typePrefix:$id")
        }

        fun key(type: IdType, id: Int): String =
            (if (type == IdType.TEST_CASE) "TC" else "TR") + ":" + id

        fun typePrefix(type: IdType): String = if (type == IdType.TEST_CASE) "TC:" else "TR:"

        private val ID_REGEX = Regex("""(?m)^id:\s*(\d+)\s*$""")
    }
}
```

Note: `.tc.md` also ends with `.md`, so the input filter intentionally checks the full `.tc.md` / `.tr.md` suffixes (the existing registry used the same `SpeqaDefaults.TEST_CASE_EXTENSION` suffix check). `ScalarIndexExtension<String>` supplies the `Void` value externalizer, so none is declared. If `ScalarIndexExtension` / `FileBasedIndex.InputFilter` differ on this SDK, adjust to compile and report it.

- [ ] **Step 4: Register the index in plugin.xml**

In `src/main/resources/META-INF/plugin.xml`, inside the `<extensions defaultExtensionPointName...>` block (where `<annotator>` and `<postStartupActivity>` live), add:

```xml
        <fileBasedIndex implementation="io.github.barsia.speqa.registry.SpeqaIdIndex"/>
```

- [ ] **Step 5: Run the pure test to verify it passes**

Run: `./gradlew test --tests "io.github.barsia.speqa.registry.SpeqaIdIndexTest" 2>&1 | grep -E "FAILED|BUILD|Tests "`
Expected: BUILD SUCCESSFUL (the pure mapping does not need the platform). If the module does not compile yet because of the half-migrated `SpeqaIdRegistry.kt`, this test becomes runnable after Task 8; in that case report DONE_WITH_CONCERNS noting the test is written and will pass post-Task-8, and still commit.

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/io/github/barsia/speqa/registry/SpeqaIdIndex.kt src/test/kotlin/io/github/barsia/speqa/registry/SpeqaIdIndexTest.kt src/main/resources/META-INF/plugin.xml
git commit -m "Add SpeqaIdIndex FileBasedIndex for test-case/run ids"
```

---

### Task 3: The `SpeqaIds` query facade

**Files:**
- Create: `src/main/kotlin/io/github/barsia/speqa/registry/SpeqaIds.kt`

- [ ] **Step 1: Create the facade**

Create `src/main/kotlin/io/github/barsia/speqa/registry/SpeqaIds.kt`:

```kotlin
package io.github.barsia.speqa.registry

import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.indexing.FileBasedIndex

/**
 * Stateless query facade over [SpeqaIdIndex]. All queries are guarded against dumb
 * mode (indexes unavailable) and re-verify candidate files against their CURRENT
 * parsed id, because getContainingFiles is an over-approximation that can return
 * stale or extra files.
 */
object SpeqaIds {

    fun isDuplicate(project: Project, type: IdType, id: Int): Boolean =
        containingFiles(project, type, id).size > 1

    fun containingFiles(project: Project, type: IdType, id: Int): List<VirtualFile> {
        if (DumbService.isDumb(project)) return emptyList()
        val scope = GlobalSearchScope.projectScope(project)
        val candidates = FileBasedIndex.getInstance()
            .getContainingFiles(SpeqaIdIndex.NAME, SpeqaIdIndex.key(type, id), scope)
        return candidates.filter { it.isValid && currentId(project, it) == id }
    }

    fun usedIds(project: Project, type: IdType): Set<Int> {
        if (DumbService.isDumb(project)) return emptySet()
        val prefix = SpeqaIdIndex.typePrefix(type)
        val ids = HashSet<Int>()
        FileBasedIndex.getInstance().processAllKeys(
            SpeqaIdIndex.NAME,
            { key ->
                if (key.startsWith(prefix)) key.removePrefix(prefix).toIntOrNull()?.let(ids::add)
                true
            },
            project,
        )
        return ids
    }

    fun nextFreeId(project: Project, type: IdType): Int {
        val used = usedIds(project, type)
        var candidate = 1
        while (candidate in used) candidate++
        return candidate
    }

    /** All (file, id) pairs of the given type, verified against current content. */
    fun allEntries(project: Project, type: IdType): List<Pair<VirtualFile, Int>> {
        if (DumbService.isDumb(project)) return emptyList()
        val result = ArrayList<Pair<VirtualFile, Int>>()
        for (id in usedIds(project, type)) {
            for (file in containingFiles(project, type, id)) {
                result.add(file to id)
            }
        }
        return result
    }

    /** Current id parsed from the live document if loaded, else from disk bytes. */
    private fun currentId(project: Project, file: VirtualFile): Int? {
        val text = FileDocumentManager.getInstance().getCachedDocument(file)?.text
            ?: runCatching { String(file.contentsToByteArray(), file.charset) }.getOrNull()
            ?: return null
        return SpeqaIdIndex.indexKeysFor(file.name, text)
            .firstOrNull()
            ?.substringAfter(':')
            ?.toIntOrNull()
    }
}
```

Note: `currentId` reuses the same pure `indexKeysFor` parser so verification can never disagree with the indexer. `getCachedDocument` is non-null only for files with a loaded (possibly unsaved) document, giving live verification for open files and disk for the rest. These reads run inside the caller's read action (annotator / actions already hold read access; see consumer tasks).

- [ ] **Step 2: Compile**

Run: `./gradlew compileKotlin 2>&1 | grep -E "^e:|BUILD"`
Expected: this may still fail because `SpeqaIdRegistry.kt` is mid-removal; if the only errors are in `SpeqaIdRegistry.kt` / not-yet-migrated consumers, that is acceptable. If `SpeqaIds.kt` itself has errors (e.g. `processAllKeys` signature), fix them to compile and report the adjustment.

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/io/github/barsia/speqa/registry/SpeqaIds.kt
git commit -m "Add SpeqaIds index-query facade with candidate verification"
```

---

### Task 4: Migrate the annotator

**Files:**
- Modify: `src/main/kotlin/io/github/barsia/speqa/validation/SpeqaAnnotator.kt`

- [ ] **Step 1: Replace the duplicate check**

In `SpeqaAnnotator.kt`, replace the duplicate-ID block (the `testCase.id?.let { id -> ... registry.idSet(IdType.TEST_CASE).isDuplicate(id) ... }`) with:

```kotlin
        // Warning: duplicate ID
        testCase.id?.let { id ->
            if (SpeqaIds.isDuplicate(file.project, IdType.TEST_CASE, id)) {
                val idRange = findFrontmatterValueRange(text, "id")
                if (idRange != null) {
                    val safeRange = TextRange(idRange.startOffset, idRange.endOffset.coerceAtMost(len))
                    if (!safeRange.isEmpty && safeRange.startOffset < len) {
                        holder.newAnnotation(
                            HighlightSeverity.WARNING,
                            SpeqaBundle.message("annotator.duplicateTestCaseId", id),
                        )
                            .range(safeRange)
                            .withFix(AssignNextFreeIdFix(IdType.TEST_CASE))
                            .create()
                    }
                }
            }
        }
```

Update imports: remove `import io.github.barsia.speqa.registry.SpeqaIdRegistry`, add `import io.github.barsia.speqa.registry.SpeqaIds`. Keep `import io.github.barsia.speqa.registry.IdType`.

- [ ] **Step 2: Compile (this file only)**

Run: `./gradlew compileKotlin 2>&1 | grep -E "^e:" | grep "SpeqaAnnotator"`
Expected: no errors referencing `SpeqaAnnotator.kt` (other files may still error pre-Task-8).

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/io/github/barsia/speqa/validation/SpeqaAnnotator.kt
git commit -m "Annotator: query SpeqaIds for duplicate detection"
```

---

### Task 5: Migrate the quick fix

**Files:**
- Modify: `src/main/kotlin/io/github/barsia/speqa/validation/AssignNextFreeIdFix.kt`

- [ ] **Step 1: Replace allocation**

Replace the body of `invoke` so it allocates from the index facade (the index reflects the unsaved edit, so consecutive fixes see prior ones):

```kotlin
    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        if (file == null) return
        val document = PsiDocumentManager.getInstance(project).getDocument(file) ?: return
        val match = ID_LINE_REGEX.find(document.text) ?: return
        val nextId = SpeqaIds.nextFreeId(project, idType)
        document.replaceString(match.range.first, match.range.last + 1, "id: $nextId")
        PsiDocumentManager.getInstance(project).commitDocument(document)
    }
```

Update imports: remove `import io.github.barsia.speqa.registry.SpeqaIdRegistry`, add `import io.github.barsia.speqa.registry.SpeqaIds`. Remove the now-unused `DIGITS_REGEX` companion entry if present.

- [ ] **Step 2: Compile (this file only)**

Run: `./gradlew compileKotlin 2>&1 | grep -E "^e:" | grep "AssignNextFreeIdFix"`
Expected: no errors referencing this file.

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/io/github/barsia/speqa/validation/AssignNextFreeIdFix.kt
git commit -m "Quick fix: allocate next free id via SpeqaIds"
```

---

### Task 6: Migrate `IdStateHolder`

**Files:**
- Modify: `src/main/kotlin/io/github/barsia/speqa/editor/IdStateHolder.kt`

- [ ] **Step 1: Replace registry calls**

In `IdStateHolder.kt`, change `computeNextFreeId` and `computeIsDuplicate` to use the facade under read access (the 2s timer fires on the EDT; index queries need read access):

```kotlin
    private fun computeNextFreeId(): Int =
        com.intellij.openapi.application.runReadAction<Int> { SpeqaIds.nextFreeId(project, idType) }

    private fun computeIsDuplicate(): Boolean =
        com.intellij.openapi.application.runReadAction<Boolean> {
            currentId()?.let { SpeqaIds.isDuplicate(project, idType, it) } ?: false
        }
```

Update imports: remove `import io.github.barsia.speqa.registry.SpeqaIdRegistry`, add `import io.github.barsia.speqa.registry.SpeqaIds`. Keep the existing 2s refresh timer; it now polls the index, which keeps the preview header in sync without extra wiring.

- [ ] **Step 2: Compile (this file only)**

Run: `./gradlew compileKotlin 2>&1 | grep -E "^e:" | grep "IdStateHolder"`
Expected: no errors referencing this file.

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/io/github/barsia/speqa/editor/IdStateHolder.kt
git commit -m "IdStateHolder: read next-free/duplicate state from SpeqaIds"
```

---

### Task 7: Migrate both id-allocation creation paths

**Files:**
- Modify: `src/main/kotlin/io/github/barsia/speqa/actions/CreateTestCaseAction.kt`
- Modify: `src/main/kotlin/io/github/barsia/speqa/editor/SpeqaEditorSupport.kt`

- [ ] **Step 1: Test-case creation**

In `CreateTestCaseAction.kt`, replace the registry allocation with:

```kotlin
        val nextId = io.github.barsia.speqa.registry.SpeqaIds.nextFreeId(project, IdType.TEST_CASE)
```

Remove the later `registry.idSet(IdType.TEST_CASE).register(nextId)` line and the `val registry = SpeqaIdRegistry.getInstance(project)` / `registry.ensureInitialized()` lines. Remove the now-unused `import io.github.barsia.speqa.registry.SpeqaIdRegistry`. Keep `import io.github.barsia.speqa.registry.IdType`. The newly written file is picked up by the index; no manual registration is needed.

- [ ] **Step 2: Test-run creation**

In `SpeqaEditorSupport.kt`, replace the TR allocation so it reads:

```kotlin
    val trId = io.github.barsia.speqa.registry.SpeqaIds.nextFreeId(project, IdType.TEST_RUN)
```

Remove the `val trRegistry = SpeqaIdRegistry.getInstance(project)`, `trRegistry.ensureInitialized()`, and `trRegistry.idSet(IdType.TEST_RUN).register(trId)` lines. Remove the now-unused `import io.github.barsia.speqa.registry.SpeqaIdRegistry` if no other usage remains in the file (keep `resolveTestCaseCreatedEpochMillis` and other code intact). This path runs under read access already; if `nextFreeId` complains about read access, wrap it in `com.intellij.openapi.application.runReadAction`.

- [ ] **Step 3: Compile (these files only)**

Run: `./gradlew compileKotlin 2>&1 | grep -E "^e:" | grep -E "CreateTestCaseAction|SpeqaEditorSupport"`
Expected: no errors referencing these two files.

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/io/github/barsia/speqa/actions/CreateTestCaseAction.kt src/main/kotlin/io/github/barsia/speqa/editor/SpeqaEditorSupport.kt
git commit -m "Allocate TC/TR ids from the index facade at creation"
```

---

### Task 8: Migrate the batch action and delete the old registry

**Files:**
- Modify: `src/main/kotlin/io/github/barsia/speqa/actions/ResolveDuplicateIdsAction.kt`
- Delete: `src/main/kotlin/io/github/barsia/speqa/registry/SpeqaIdRegistry.kt`
- Delete: `src/main/kotlin/io/github/barsia/speqa/registry/SpeqaIdRegistryStartup.kt`
- Delete: `src/test/kotlin/io/github/barsia/speqa/registry/SpeqaIdRegistryTest.kt`
- Delete: `src/test/kotlin/io/github/barsia/speqa/registry/SpeqaIdRegistryInitTest.kt`
- Delete: `src/test/kotlin/io/github/barsia/speqa/registry/IdSetTest.kt`
- Modify: `src/main/resources/META-INF/plugin.xml`

- [ ] **Step 1: Rewrite the batch collection to use the index**

In `ResolveDuplicateIdsAction.kt`, replace the progress Runnable's body (the part that collected files and read ids) with index-sourced, already-verified entries:

```kotlin
            Runnable {
                // Index reflects unsaved buffers; allEntries is already content-verified.
                val entries = com.intellij.openapi.application.runReadAction<List<Pair<com.intellij.openapi.vfs.VirtualFile, Int>>> {
                    SpeqaIds.allEntries(project, IdType.TEST_CASE)
                }
                val duplicateIds = entries.groupingBy { it.second }.eachCount()
                    .filterValues { it > 1 }.keys
                // Git author-date only for files whose id is actually duplicated.
                val tcEntries = entries.map { (file, id) ->
                    val created = if (id in duplicateIds) {
                        resolveTestCaseCreatedEpochMillis(project, file)
                    } else {
                        null
                    }
                    TestCaseIdEntry(file.path, id, created)
                }
                plan = computeDuplicateIdRenumberPlan(tcEntries)
            },
```

Delete the `collectTestCaseFiles` function entirely. Update imports: remove `SpeqaIdRegistry`, `VfsUtilCore`, and `SpeqaDefaults` if now unused; add `import io.github.barsia.speqa.registry.SpeqaIds`. Keep `LocalFileSystem` and `FileDocumentManager` (still used by `applyPlan`), `IdType`, `TestCaseIdEntry`, `computeDuplicateIdRenumberPlan`, `resolveTestCaseCreatedEpochMillis`.

- [ ] **Step 2: Delete the obsolete files**

```bash
git rm src/main/kotlin/io/github/barsia/speqa/registry/SpeqaIdRegistry.kt \
       src/main/kotlin/io/github/barsia/speqa/registry/SpeqaIdRegistryStartup.kt \
       src/test/kotlin/io/github/barsia/speqa/registry/SpeqaIdRegistryTest.kt \
       src/test/kotlin/io/github/barsia/speqa/registry/SpeqaIdRegistryInitTest.kt \
       src/test/kotlin/io/github/barsia/speqa/registry/IdSetTest.kt
```

- [ ] **Step 3: Remove the startup activity registration**

In `src/main/resources/META-INF/plugin.xml`, delete the line:

```xml
        <postStartupActivity implementation="io.github.barsia.speqa.registry.SpeqaIdRegistryStartup"/>
```

- [ ] **Step 4: Compile the whole module**

Run: `./gradlew compileKotlin 2>&1 | grep -E "^e:|BUILD"`
Expected: BUILD SUCCESSFUL, no `e:` lines. Fix any remaining references to the deleted `SpeqaIdRegistry` / `IdSet` / `extractId` / `SCAN_SKIP_DIRS` until clean.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "Batch resolver via index; remove disk-scanning SpeqaIdRegistry"
```

---

### Task 9: Full verification

**Files:** none (verification only).

- [ ] **Step 1: Run the full test suite**

Run: `./gradlew test 2>&1 | grep -E "FAILED|BUILD|Tests "`
Expected: BUILD SUCCESSFUL. `SpeqaIdIndexTest` and `DuplicateIdResolverTest` pass; the deleted registry/IdSet tests are gone.

- [ ] **Step 2: Build the plugin**

Run: `./gradlew buildPlugin 2>&1 | tail -6`
Expected: BUILD SUCCESSFUL (confirms `<fileBasedIndex>` registration parses and no dangling `postStartupActivity`).

- [ ] **Step 3: Manual sandbox smoke (the actual goal)**

Run `./gradlew runIde`, open a project with test cases. (a) In one `.tc.md`, change `id:` to a value already used by another `.tc.md`. Confirm the duplicate warning underline + gutter appears as you type, without saving, after the daemon's normal highlight delay (about 300 ms), not after a multi-second wait. (b) Confirm the quick fix `Assign next free ID` resolves it, and applying it to several files sharing one id gives distinct ids. (c) Confirm `Tools > SpeQA > Resolve Duplicate Test Case IDs` still lists and renumbers duplicates. (d) Confirm entering dumb mode / indexing does not throw (queries are dumb-guarded).

- [ ] **Step 4: Commit any incidental fixes**

```bash
git add -A
git commit -m "Verify live index-based ID detection end to end"
```

---

## Self-Review

**Spec coverage:**
- Index-backed detection reflecting unsaved buffers, daemon-native debounce, candidate verification, dumb-mode guard: Tasks 2, 3, 4 + spec Task 1. Covered.
- Allocation from index used-set, no in-memory reservation: Tasks 3, 5, 7 + spec Task 1. Covered.
- Batch resolver on index, git only for duplicate groups: Task 8 (keeps the perf shape from the prior feature). Covered.
- Removal of disk scan / startup activity / IdSet / daemon-restart: Task 8. Covered.

**Placeholder scan:** every code step shows full code; platform-API uncertainty is bounded by explicit "adjust to compile and report" notes with the canonical code given, not vague TODOs.

**Type consistency:** `SpeqaIdIndex.NAME`, `SpeqaIdIndex.key(type,id)`, `SpeqaIdIndex.typePrefix(type)`, `SpeqaIdIndex.indexKeysFor(name,text)` are defined in Task 2 and used unchanged in Task 3. `SpeqaIds.isDuplicate/usedIds/nextFreeId/containingFiles/allEntries` are defined in Task 3 and used with identical signatures in Tasks 4-8. `IdType` (Task 1) is referenced throughout. `TestCaseIdEntry` / `computeDuplicateIdRenumberPlan` (existing `DuplicateIdResolver`) and `resolveTestCaseCreatedEpochMillis` (existing) are reused unchanged in Task 8.

**Ordering caveat (intentional):** the module is non-compiling between Task 1 and Task 8 because `IdType` is removed from `SpeqaIdRegistry.kt` while the file still exists. Per-file `grep "^e:" | grep <file>` gates are used in the intermediate tasks; the whole-module `compileKotlin` gate is at Task 8 Step 4. Executors must not treat intermediate whole-module compile failures as task failure.
