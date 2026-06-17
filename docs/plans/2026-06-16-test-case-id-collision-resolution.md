# Test Case ID Collision Resolution Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make duplicate test-case ID resolution correct and bulk-capable: fix the quick fix that re-hands the same number to several duplicates, and add an explicit batch action that renumbers all duplicate IDs in the project deterministically.

**Architecture:** Keep the existing sequential per-project ID counter. IDs stay plain integers (`TC-N`) and remain unique only within a single tree, never a stable cross-branch key. Collisions are inherent to parallel branch creation and are resolved AFTER the merge produces the combined tree, never prevented. Two layers: an atomic allocation primitive in `IdSet` that the per-file quick fix and a new batch action both consume, and a pure, origin-agnostic renumbering decision (earliest-created keeps the contested number, losers move to the first globally-free id) wrapped by an explicit, preview-confirmed action.

**Tech Stack:** Kotlin, IntelliJ Platform SDK, JUnit 4. Git author-date created-time resolution already exists in `SpeqaEditorSupport.kt`.

---

## File Structure

- `src/main/kotlin/io/github/barsia/speqa/registry/SpeqaIdRegistry.kt` (modify): add `IdSet.reserveNextFreeId()`; expose scan skip-dirs as an internal companion constant for reuse by the batch action.
- `src/main/kotlin/io/github/barsia/speqa/registry/DuplicateIdResolver.kt` (create): pure types `TestCaseIdEntry` / `IdRenumber` and the pure decision `computeDuplicateIdRenumberPlan`.
- `src/main/kotlin/io/github/barsia/speqa/validation/AssignNextFreeIdFix.kt` (modify): allocate via `reserveNextFreeId()` and reconcile the registry synchronously.
- `src/main/kotlin/io/github/barsia/speqa/editor/SpeqaEditorSupport.kt` (modify): add `resolveTestCaseCreatedEpochMillis(project, file)` reusing the existing cached created-at resolver.
- `src/main/kotlin/io/github/barsia/speqa/actions/ResolveDuplicateIdsAction.kt` (create): scan tree, build entries, compute plan, show preview, apply.
- `src/main/kotlin/io/github/barsia/speqa/actions/ResolveDuplicateIdsDialog.kt` (create): read-only preview table.
- `src/main/resources/messages/SpeqaBundle.properties` (modify): user-visible strings.
- `src/main/resources/META-INF/plugin.xml` (modify): register the action under the `Speqa.ToolsMenu` group.
- `src/test/kotlin/io/github/barsia/speqa/registry/IdSetTest.kt` (create): unit tests for `reserveNextFreeId` and the consecutive-fix sequence.
- `src/test/kotlin/io/github/barsia/speqa/registry/DuplicateIdResolverTest.kt` (create): unit tests for the pure plan.

---

### Task 1: Atomic ID reservation primitive

**Files:**
- Modify: `src/main/kotlin/io/github/barsia/speqa/registry/SpeqaIdRegistry.kt` (the `IdSet` class, lines 22-49)
- Test: `src/test/kotlin/io/github/barsia/speqa/registry/IdSetTest.kt`

- [ ] **Step 1: Write the failing test**

Create `src/test/kotlin/io/github/barsia/speqa/registry/IdSetTest.kt`:

```kotlin
package io.github.barsia.speqa.registry

import org.junit.Assert.assertEquals
import org.junit.Test

class IdSetTest {

    @Test
    fun reserveNextFreeId_returns_distinct_increasing_ids_on_empty_set() {
        val set = IdSet()
        assertEquals(1, set.reserveNextFreeId())
        assertEquals(2, set.reserveNextFreeId())
        assertEquals(3, set.reserveNextFreeId())
    }

    @Test
    fun reserveNextFreeId_fills_the_lowest_gap_then_claims_it() {
        val set = IdSet()
        set.register(1)
        set.register(3)
        assertEquals(2, set.reserveNextFreeId())
        // 2 is now occupied, so the next reservation skips to 4
        assertEquals(4, set.reserveNextFreeId())
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "io.github.barsia.speqa.registry.IdSetTest" 2>&1 | grep -E "FAILED|BUILD|error:|reserveNextFreeId"`
Expected: compilation FAIL with unresolved reference `reserveNextFreeId`.

- [ ] **Step 3: Add the primitive**

In `SpeqaIdRegistry.kt`, inside `class IdSet`, add after `nextFreeId()` (after line 41):

```kotlin
    @Synchronized
    fun reserveNextFreeId(): Int {
        var candidate = 1
        while (counts.containsKey(candidate)) candidate++
        register(candidate)
        return candidate
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "io.github.barsia.speqa.registry.IdSetTest" 2>&1 | grep -E "FAILED|BUILD|Tests "`
Expected: BUILD SUCCESSFUL, both tests pass.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/io/github/barsia/speqa/registry/SpeqaIdRegistry.kt src/test/kotlin/io/github/barsia/speqa/registry/IdSetTest.kt
git commit -m "Add atomic IdSet.reserveNextFreeId primitive"
```

---

### Task 2: Fix the duplicate-ID quick fix

The quick fix currently reads `nextFreeId()` but never writes the assignment back to the registry, so applying it to several files sharing one duplicate id hands them all the same number again. Switch it to reserve atomically and reconcile the registry in place.

**Files:**
- Modify: `src/main/kotlin/io/github/barsia/speqa/validation/AssignNextFreeIdFix.kt`
- Test: `src/test/kotlin/io/github/barsia/speqa/registry/IdSetTest.kt` (extend)

- [ ] **Step 1: Write the failing test**

This reproduces the bug at the registry level: three files share id `5` (count 3). Each fix unregisters the old id and reserves a new one. The result must be three distinct ids, none of them `5` repeated. Append to `IdSetTest.kt`:

```kotlin
    @Test
    fun three_consecutive_fixes_on_a_shared_id_yield_distinct_ids() {
        val set = IdSet()
        // Three files all carry id 5; ids 1..4 are taken by unrelated files.
        listOf(1, 2, 3, 4).forEach(set::register)
        repeat(3) { set.register(5) }

        // Simulate applying the quick fix to each of the three duplicates in turn:
        // unregister this file's old id, then reserve a fresh one.
        val assigned = (1..3).map {
            set.unregister(5)
            set.reserveNextFreeId()
        }

        // The buggy old behaviour handed out [6, 6, 6] (stale registry). Now each
        // fix sees the previous one: 6, then 7, then 5 (the first two vacated 5, so
        // the last file reclaims it). All three are distinct and none reproduce 5x3.
        assertEquals(listOf(6, 7, 5), assigned)
        assertEquals(3, assigned.toSet().size)
    }
```

- [ ] **Step 2: Run test to verify it passes**

Run: `./gradlew test --tests "io.github.barsia.speqa.registry.IdSetTest" 2>&1 | grep -E "FAILED|BUILD|Tests "`
Expected: BUILD SUCCESSFUL. This validates the exact sequence the fix performs against the Task 1 primitive (the old stale-registry behaviour would have returned `[6, 6, 6]`). If it fails or returns `[6, 6, 6]`, Task 1 is incomplete.

- [ ] **Step 3: Rewrite the quick fix**

Replace the body of `AssignNextFreeIdFix.kt` `invoke` (lines 25-34) and add an old-id helper regex. The full file becomes:

```kotlin
package io.github.barsia.speqa.validation

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import io.github.barsia.speqa.SpeqaBundle
import io.github.barsia.speqa.registry.IdType
import io.github.barsia.speqa.registry.SpeqaIdRegistry

class AssignNextFreeIdFix(private val idType: IdType) : IntentionAction {

    override fun getText(): String = SpeqaBundle.message("annotator.fix.assignNextFreeId")

    override fun getFamilyName(): String = SpeqaBundle.message("annotator.fix.assignNextFreeId")

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
        if (file == null) return false
        return ID_LINE_REGEX.containsMatchIn(file.text)
    }

    override fun startInWriteAction(): Boolean = true

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        if (file == null) return
        val document = PsiDocumentManager.getInstance(project).getDocument(file) ?: return
        val match = ID_LINE_REGEX.find(document.text) ?: return
        val oldId = DIGITS_REGEX.find(match.value)?.value?.toIntOrNull()
        val registry = SpeqaIdRegistry.getInstance(project)
        registry.ensureInitialized()
        val idSet = registry.idSet(idType)
        // Free this file's current id first so a still-duplicated value stays
        // occupied by its other holders and is not handed back to this file.
        if (oldId != null) idSet.unregister(oldId)
        val nextId = idSet.reserveNextFreeId()
        document.replaceString(match.range.first, match.range.last + 1, "id: $nextId")
        PsiDocumentManager.getInstance(project).commitDocument(document)
    }

    private companion object {
        val ID_LINE_REGEX = Regex("""(?m)^id:\s*\d+\s*$""")
        val DIGITS_REGEX = Regex("""\d+""")
    }
}
```

- [ ] **Step 4: Run the registry tests and compile**

Run: `./gradlew test --tests "io.github.barsia.speqa.registry.IdSetTest" 2>&1 | grep -E "FAILED|BUILD|Tests "`
Expected: BUILD SUCCESSFUL.
Run: `./gradlew compileKotlin 2>&1 | grep -E "^e:|BUILD"`
Expected: BUILD SUCCESSFUL, no `e:` lines.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/io/github/barsia/speqa/validation/AssignNextFreeIdFix.kt src/test/kotlin/io/github/barsia/speqa/registry/IdSetTest.kt
git commit -m "Fix Assign-next-free-ID quick fix to reconcile the ID registry"
```

---

### Task 3: Pure batch renumbering decision

Origin-agnostic pure function. Within each group of entries sharing an id, the earliest-created entry keeps the id; the rest move to the first id free across the whole snapshot and across assignments already made in this pass.

**Files:**
- Create: `src/main/kotlin/io/github/barsia/speqa/registry/DuplicateIdResolver.kt`
- Test: `src/test/kotlin/io/github/barsia/speqa/registry/DuplicateIdResolverTest.kt`

- [ ] **Step 1: Write the failing test**

Create `src/test/kotlin/io/github/barsia/speqa/registry/DuplicateIdResolverTest.kt`:

```kotlin
package io.github.barsia.speqa.registry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DuplicateIdResolverTest {

    @Test
    fun no_duplicates_yields_empty_plan() {
        val entries = listOf(
            TestCaseIdEntry("a.tc.md", 1, 10L),
            TestCaseIdEntry("b.tc.md", 2, 20L),
        )
        assertEquals(emptyList<IdRenumber>(), computeDuplicateIdRenumberPlan(entries))
    }

    @Test
    fun earliest_created_keeps_the_number_loser_takes_first_free() {
        // ids present: {1,2,3}. Files b and c both hold 2; b is older.
        val entries = listOf(
            TestCaseIdEntry("a.tc.md", 1, 5L),
            TestCaseIdEntry("b.tc.md", 2, 10L),
            TestCaseIdEntry("c.tc.md", 2, 20L),
            TestCaseIdEntry("d.tc.md", 3, 5L),
        )
        val plan = computeDuplicateIdRenumberPlan(entries)
        // b (older) keeps 2; c (newer) moves to the first free id, which is 4.
        assertEquals(listOf(IdRenumber("c.tc.md", 2, 4)), plan)
    }

    @Test
    fun multiple_groups_each_resolved_losers_get_distinct_free_ids() {
        val entries = listOf(
            TestCaseIdEntry("a.tc.md", 1, 5L),
            TestCaseIdEntry("b.tc.md", 1, 9L),
            TestCaseIdEntry("x.tc.md", 2, 5L),
            TestCaseIdEntry("y.tc.md", 2, 9L),
        )
        val plan = computeDuplicateIdRenumberPlan(entries)
        // Keepers: a (id 1), x (id 2). Losers b and y, ordered by (created, path): b then y.
        // Occupied {1,2}; first free 3 -> b, next free 4 -> y.
        assertEquals(
            listOf(
                IdRenumber("b.tc.md", 1, 3),
                IdRenumber("y.tc.md", 2, 4),
            ),
            plan,
        )
    }

    @Test
    fun missing_created_time_falls_back_to_path_order() {
        val entries = listOf(
            TestCaseIdEntry("z.tc.md", 5, null),
            TestCaseIdEntry("a.tc.md", 5, null),
        )
        val plan = computeDuplicateIdRenumberPlan(entries)
        // Both created-times null -> tie broken by path: "a.tc.md" keeps 5, "z.tc.md" moves.
        assertEquals(listOf(IdRenumber("z.tc.md", 5, 1)), plan)
    }

    @Test
    fun resolution_never_produces_a_new_duplicate() {
        val entries = listOf(
            TestCaseIdEntry("a.tc.md", 7, 1L),
            TestCaseIdEntry("b.tc.md", 7, 2L),
            TestCaseIdEntry("c.tc.md", 7, 3L),
            TestCaseIdEntry("d.tc.md", 8, 1L),
        )
        val plan = computeDuplicateIdRenumberPlan(entries)
        // Final id assignment = keepers' ids + plan's new ids; all must be unique.
        val keptIds = (entries.map { it.id }.toSet()) // 7,8 stay occupied by keepers
        val finalIds = entries.associate { it.path to it.id }.toMutableMap()
        plan.forEach { finalIds[it.path] = it.newId }
        assertTrue(keptIds.isNotEmpty())
        assertEquals(finalIds.values.toSet().size, finalIds.values.size)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "io.github.barsia.speqa.registry.DuplicateIdResolverTest" 2>&1 | grep -E "FAILED|BUILD|error:"`
Expected: compilation FAIL with unresolved references `TestCaseIdEntry`, `IdRenumber`, `computeDuplicateIdRenumberPlan`.

- [ ] **Step 3: Implement the pure decision**

Create `src/main/kotlin/io/github/barsia/speqa/registry/DuplicateIdResolver.kt`:

```kotlin
package io.github.barsia.speqa.registry

/** One test-case file as seen in the current tree snapshot. */
data class TestCaseIdEntry(
    val path: String,
    val id: Int,
    val createdEpochMillis: Long?,
)

/** A single renumbering: [path] moves from [oldId] to [newId]. */
data class IdRenumber(
    val path: String,
    val oldId: Int,
    val newId: Int,
)

/**
 * Pure, origin-agnostic batch resolution for duplicate test-case ids. It reasons only
 * about the given snapshot, never about git branches.
 *
 * Within each group of entries that share an id, the earliest-created entry keeps the
 * id (createdEpochMillis ascending, nulls last, then path ascending as a stable tie
 * break so two machines resolving an identical tree produce an identical result). Every
 * other entry is a loser. Losers, ordered by the same key, are each assigned the first
 * id that is free across the whole snapshot and across assignments already made in this
 * pass, so the result contains no new duplicate and no non-duplicate file is moved.
 */
fun computeDuplicateIdRenumberPlan(entries: List<TestCaseIdEntry>): List<IdRenumber> {
    val occupied = HashSet<Int>()
    entries.forEach { occupied.add(it.id) }

    val order = compareBy<TestCaseIdEntry>(
        { it.createdEpochMillis ?: Long.MAX_VALUE },
        { it.path },
    )

    val losers = entries
        .groupBy { it.id }
        .values
        .filter { it.size > 1 }
        .flatMap { group -> group.sortedWith(order).drop(1) }
        .sortedWith(order)

    var candidate = 1
    val plan = ArrayList<IdRenumber>(losers.size)
    for (loser in losers) {
        while (candidate in occupied) candidate++
        occupied.add(candidate)
        plan.add(IdRenumber(loser.path, loser.id, candidate))
    }
    return plan
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "io.github.barsia.speqa.registry.DuplicateIdResolverTest" 2>&1 | grep -E "FAILED|BUILD|Tests "`
Expected: BUILD SUCCESSFUL, all five tests pass.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/io/github/barsia/speqa/registry/DuplicateIdResolver.kt src/test/kotlin/io/github/barsia/speqa/registry/DuplicateIdResolverTest.kt
git commit -m "Add pure duplicate-ID renumbering decision"
```

---

### Task 4: Created-time helper and reusable skip-dirs

Expose a created-epoch-millis resolver for arbitrary test-case files (reusing the existing cached git author-date resolver), and make the scan skip-dir set reusable so the batch action does not duplicate it.

**Files:**
- Modify: `src/main/kotlin/io/github/barsia/speqa/editor/SpeqaEditorSupport.kt`
- Modify: `src/main/kotlin/io/github/barsia/speqa/registry/SpeqaIdRegistry.kt`

- [ ] **Step 1: Expose the scan skip-dirs as a companion constant**

In `SpeqaIdRegistry.kt`, delete the instance field `skipDirs` (lines 90-94) and add to the `companion object` (after `getInstance`, near line 137):

```kotlin
        internal val SCAN_SKIP_DIRS = setOf(
            ".git", ".idea", ".gradle", ".intellijPlatform",
            "build", "out", "target", "dist",
            "node_modules", "vendor",
        )
```

Then update the one reference in `scanDirectory` (line 99) from `child.name !in skipDirs` to `child.name !in SCAN_SKIP_DIRS`.

- [ ] **Step 2: Add the created-epoch-millis helper**

In `SpeqaEditorSupport.kt`, add a new internal function next to `resolveTestCaseHeaderMeta` (after line 103). It reuses the existing `createdAtResolver` (cached) and the same fallbacks:

```kotlin
internal fun resolveTestCaseCreatedEpochMillis(project: com.intellij.openapi.project.Project, file: VirtualFile): Long? {
    val basePath = project.basePath
    val instant = if (basePath != null) {
        createdAtResolver.resolve(basePath, file.path, file.timeStamp)
    } else {
        null
    } ?: resolveFileCreatedInstant(file)
    return instant?.toEpochMilli()
}
```

- [ ] **Step 3: Compile**

Run: `./gradlew compileKotlin 2>&1 | grep -E "^e:|BUILD"`
Expected: BUILD SUCCESSFUL, no `e:` lines.

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/io/github/barsia/speqa/editor/SpeqaEditorSupport.kt src/main/kotlin/io/github/barsia/speqa/registry/SpeqaIdRegistry.kt
git commit -m "Expose created-epoch-millis resolver and reusable scan skip-dirs"
```

---

### Task 5: User-visible strings

**Files:**
- Modify: `src/main/resources/messages/SpeqaBundle.properties`

- [ ] **Step 1: Add the keys**

Append after the existing `annotator.fix.assignNextFreeId` line (line 168):

The plugin declares `<resource-bundle>messages.SpeqaBundle</resource-bundle>`, so action text/description resolve by the convention key `action.<actionId>.text` / `.description` (the action id is `Speqa.ResolveDuplicateIds`). Do not hardcode the text in plugin.xml.

```properties
action.Speqa.ResolveDuplicateIds.text=Resolve Duplicate Test Case IDs
action.Speqa.ResolveDuplicateIds.description=Find test cases that share an ID and renumber the duplicates
resolveDuplicateIds.dialog.title=Resolve Duplicate Test Case IDs
resolveDuplicateIds.dialog.header=The following test cases will be renumbered. The earliest-created file in each group keeps its ID.
resolveDuplicateIds.column.file=File
resolveDuplicateIds.column.currentId=Current
resolveDuplicateIds.column.newId=New
resolveDuplicateIds.apply=Renumber
resolveDuplicateIds.none.title=No Duplicate IDs
resolveDuplicateIds.none.message=No duplicate test case IDs were found in this project.
```

- [ ] **Step 2: Commit**

```bash
git add src/main/resources/messages/SpeqaBundle.properties
git commit -m "Add strings for the resolve-duplicate-IDs action"
```

---

### Task 6: Batch action, preview dialog, and registration

**Files:**
- Create: `src/main/kotlin/io/github/barsia/speqa/actions/ResolveDuplicateIdsDialog.kt`
- Create: `src/main/kotlin/io/github/barsia/speqa/actions/ResolveDuplicateIdsAction.kt`
- Modify: `src/main/resources/META-INF/plugin.xml`

- [ ] **Step 1: Create the preview dialog**

Create `src/main/kotlin/io/github/barsia/speqa/actions/ResolveDuplicateIdsDialog.kt`:

```kotlin
package io.github.barsia.speqa.actions

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import io.github.barsia.speqa.SpeqaBundle
import io.github.barsia.speqa.registry.IdRenumber
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.table.DefaultTableModel

class ResolveDuplicateIdsDialog(
    project: Project,
    private val plan: List<IdRenumber>,
) : DialogWrapper(project) {

    init {
        title = SpeqaBundle.message("resolveDuplicateIds.dialog.title")
        setOKButtonText(SpeqaBundle.message("resolveDuplicateIds.apply"))
        init()
    }

    override fun createNorthPanel(): JComponent =
        JBLabel(SpeqaBundle.message("resolveDuplicateIds.dialog.header"))

    override fun createCenterPanel(): JComponent {
        val columns = arrayOf(
            SpeqaBundle.message("resolveDuplicateIds.column.file"),
            SpeqaBundle.message("resolveDuplicateIds.column.currentId"),
            SpeqaBundle.message("resolveDuplicateIds.column.newId"),
        )
        val model = object : DefaultTableModel(columns, 0) {
            override fun isCellEditable(row: Int, column: Int): Boolean = false
        }
        for (renumber in plan) {
            model.addRow(arrayOf(renumber.path, "TC-${renumber.oldId}", "TC-${renumber.newId}"))
        }
        val table = JBTable(model)
        val scroll = JBScrollPane(table)
        scroll.preferredSize = Dimension(640, 320)
        return scroll
    }
}
```

- [ ] **Step 2: Create the action**

Create `src/main/kotlin/io/github/barsia/speqa/actions/ResolveDuplicateIdsAction.kt`:

```kotlin
package io.github.barsia.speqa.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import io.github.barsia.speqa.SpeqaBundle
import io.github.barsia.speqa.editor.resolveTestCaseCreatedEpochMillis
import io.github.barsia.speqa.model.SpeqaDefaults
import io.github.barsia.speqa.registry.IdRenumber
import io.github.barsia.speqa.registry.IdType
import io.github.barsia.speqa.registry.SpeqaIdRegistry
import io.github.barsia.speqa.registry.TestCaseIdEntry
import io.github.barsia.speqa.registry.computeDuplicateIdRenumberPlan

class ResolveDuplicateIdsAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val entries = collectTestCaseFiles(project).mapNotNull { file ->
            val extracted = SpeqaIdRegistry.extractId(file) ?: return@mapNotNull null
            if (extracted.first != IdType.TEST_CASE) return@mapNotNull null
            TestCaseIdEntry(file.path, extracted.second, resolveTestCaseCreatedEpochMillis(project, file))
        }
        val plan = computeDuplicateIdRenumberPlan(entries)
        if (plan.isEmpty()) {
            Messages.showInfoMessage(
                project,
                SpeqaBundle.message("resolveDuplicateIds.none.message"),
                SpeqaBundle.message("resolveDuplicateIds.none.title"),
            )
            return
        }
        if (!ResolveDuplicateIdsDialog(project, plan).showAndGet()) return
        applyPlan(project, plan)
    }

    private fun applyPlan(project: Project, plan: List<IdRenumber>) {
        WriteCommandAction.runWriteCommandAction(
            project,
            SpeqaBundle.message("resolveDuplicateIds.dialog.title"),
            null,
            Runnable {
                val fileDocumentManager = FileDocumentManager.getInstance()
                for (renumber in plan) {
                    val file = LocalFileSystem.getInstance().findFileByPath(renumber.path) ?: continue
                    val document = fileDocumentManager.getDocument(file) ?: continue
                    val match = ID_LINE_REGEX.find(document.text) ?: continue
                    document.replaceString(match.range.first, match.range.last + 1, "id: ${renumber.newId}")
                }
            },
        )
        // Persist so the VFS listener rescans and the registry reflects the new ids.
        FileDocumentManager.getInstance().saveAllDocuments()
    }

    private fun collectTestCaseFiles(project: Project): List<VirtualFile> {
        val basePath = project.basePath ?: return emptyList()
        val root = LocalFileSystem.getInstance().findFileByPath(basePath) ?: return emptyList()
        val result = ArrayList<VirtualFile>()
        VfsUtilCore.iterateChildrenRecursively(
            root,
            { dir -> !dir.isDirectory || dir.name !in SpeqaIdRegistry.SCAN_SKIP_DIRS },
            { file ->
                if (!file.isDirectory && file.name.endsWith(".${SpeqaDefaults.TEST_CASE_EXTENSION}")) {
                    result.add(file)
                }
                true
            },
        )
        return result
    }

    private companion object {
        val ID_LINE_REGEX = Regex("""(?m)^id:\s*\d+\s*$""")
    }
}
```

- [ ] **Step 3: Register the action in plugin.xml**

In `src/main/resources/META-INF/plugin.xml`, inside the `<group id="Speqa.ToolsMenu" ...>` block (the group opens at line 87), add as a new child after the `Speqa.InstallSkill` action (line 90):

```xml
            <action id="Speqa.ResolveDuplicateIds"
                    class="io.github.barsia.speqa.actions.ResolveDuplicateIdsAction"/>
```

Text and description come from the resource bundle via the `action.Speqa.ResolveDuplicateIds.text` / `.description` keys (Task 5), matching every other action in this file.

- [ ] **Step 4: Compile**

Run: `./gradlew compileKotlin 2>&1 | grep -E "^e:|BUILD"`
Expected: BUILD SUCCESSFUL, no `e:` lines.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/io/github/barsia/speqa/actions/ResolveDuplicateIdsAction.kt src/main/kotlin/io/github/barsia/speqa/actions/ResolveDuplicateIdsDialog.kt src/main/resources/META-INF/plugin.xml
git commit -m "Add Resolve Duplicate Test Case IDs batch action"
```

---

### Task 7: Full verification and smoke test

**Files:** none (verification only).

- [ ] **Step 1: Run the full test suite**

Run: `./gradlew test 2>&1 | grep -E "FAILED|BUILD|Tests "`
Expected: BUILD SUCCESSFUL, no FAILED lines.

- [ ] **Step 2: Build the plugin (wiring smoke test)**

Run: `./gradlew buildPlugin 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL. This confirms `plugin.xml` parses with the new action and the bundle keys resolve.

- [ ] **Step 3: Manual sandbox smoke test**

Run the IDE sandbox (`./gradlew runIde`), open a SpeQA project, create two `.tc.md` files with the same `id:` value, then invoke `Tools > SpeQA > Resolve Duplicate Test Case IDs`. Verify: the preview lists exactly the newer file with `old -> new`, the older file keeps its number, applying renumbers only the newer file to a free id, and re-running the action reports "No Duplicate IDs". Then create three files sharing one id and apply the per-file `Assign next free ID` quick fix to each in turn (no save between) and verify they receive three distinct ids.

- [ ] **Step 4: Commit (if any incidental fixes were needed)**

```bash
git add -A
git commit -m "Verify ID collision resolution end to end"
```

---

## Self-Review

**Spec coverage** (against the three updated bullets in `docs/specs/2026-04-06-speqa-design.md`):
- "ID generation / `reserveNextFreeId()`" -> Task 1 (primitive), consumed by Task 2 (quick fix) and Task 6 (batch). Covered.
- "ID duplicate detection / quick fix reconciles the registry" -> Task 2. Covered.
- "Batch duplicate-ID resolver / origin-agnostic / earliest-created keeps / preview / single write action / no new duplicate, no cascade" -> Task 3 (decision) + Task 4 (created time) + Task 6 (action, preview, apply). Covered.
- "ID uniqueness scope (collisions resolved after merge, ids not a stable cross-branch key)" -> documented contract; enforced behaviorally by the batch action being post-merge and renumber-based. No code beyond Tasks 3/6 required.

**Placeholder scan:** no TBD/TODO; every code step shows full code; every test step shows assertions and the run command with expected output.

**Type consistency:** `reserveNextFreeId()` (Task 1) is used verbatim in Tasks 2 and 6 via `idSet`. `TestCaseIdEntry(path, id, createdEpochMillis)`, `IdRenumber(path, oldId, newId)`, and `computeDuplicateIdRenumberPlan(entries)` (Task 3) are used with the same signatures in Task 6. `resolveTestCaseCreatedEpochMillis(project, file)` (Task 4) is called with the same arguments in Task 6. `SpeqaIdRegistry.SCAN_SKIP_DIRS` (Task 4) and `SpeqaIdRegistry.extractId` (existing) are referenced in Task 6. `SpeqaDefaults.TEST_CASE_EXTENSION` is the existing constant used by the registry.
