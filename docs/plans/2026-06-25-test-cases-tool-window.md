# Test Cases Tool Window Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a left-docked "SpeQA" tool window that shows the `test-cases/` directory as a curated tree where folders are sections and `.tc.md` files appear as leaves labeled by their parsed `title`.

**Architecture:** Built on the IntelliJ platform tree framework (`AbstractTreeStructure` + `StructureTreeModel` + `AsyncTreeModel` + `Tree`), the same machinery as the Project view. A `ToolWindowFactory` builds the tree, installs double-click/Enter navigation and speed-search, and subscribes to VFS changes scoped to `test-cases/` to keep the tree live. Test-case titles (and status, for the icon) are parsed lazily via `TestCaseParser` and cached per file by modification stamp. The ordering/filtering logic (folders first, then test cases by `title`, `.tc.md`-only) is a pure function with unit tests.

**Tech Stack:** Kotlin, IntelliJ Platform SDK (tool windows, tree framework), JUnit 4, Gradle.

**Spec:** `docs/specs/2026-04-06-speqa-design.md` section "9a. Test Cases Tool Window".

---

## File Structure

- Create `src/main/kotlin/io/github/barsia/speqa/toolwindow/SpeqaTreeOrdering.kt` - pure ordering/filtering (unit-tested).
- Create `src/main/kotlin/io/github/barsia/speqa/toolwindow/TestCaseSummaryCache.kt` - per-file cache of parsed `title` + `status`.
- Create `src/main/kotlin/io/github/barsia/speqa/toolwindow/SpeqaTreeNodes.kt` - folder & test-case `AbstractTreeNode`s.
- Create `src/main/kotlin/io/github/barsia/speqa/toolwindow/SpeqaTreeStructure.kt` - `AbstractTreeStructure` over the nodes.
- Create `src/main/kotlin/io/github/barsia/speqa/toolwindow/SpeqaToolWindowFactory.kt` - factory, navigation, speed-search, VFS listener.
- Modify `src/main/kotlin/io/github/barsia/speqa/filetype/SpeqaIcons.kt` - add `forStatus(status)` helper.
- Modify `src/main/kotlin/io/github/barsia/speqa/filetype/SpeqaIconProvider.kt` - reuse `SpeqaIcons.forStatus`.
- Modify `src/main/resources/META-INF/plugin.xml` - register `<toolWindow>`.
- Modify `src/main/resources/messages/SpeqaBundle.properties` - add tool-window empty-state string.
- Create `src/test/kotlin/io/github/barsia/speqa/toolwindow/SpeqaTreeOrderingTest.kt` - ordering/filtering tests.

---

## Task 1: Shared status-to-icon helper

Extract the status-to-icon mapping so the tool window and the existing icon provider share one source of truth.

**Files:**
- Modify: `src/main/kotlin/io/github/barsia/speqa/filetype/SpeqaIcons.kt`
- Modify: `src/main/kotlin/io/github/barsia/speqa/filetype/SpeqaIconProvider.kt:30-37`

- [ ] **Step 1: Add `forStatus` to `SpeqaIcons`**

Add the import and function to `SpeqaIcons.kt`:

```kotlin
package io.github.barsia.speqa.filetype

import com.intellij.openapi.util.IconLoader
import io.github.barsia.speqa.model.Status
import javax.swing.Icon

object SpeqaIcons {
    val PluginIcon: Icon = IconLoader.getIcon("/icons/speqa16.svg", SpeqaIcons::class.java)
    val TestCaseDraft: Icon = IconLoader.getIcon("/icons/testCaseDraft.svg", SpeqaIcons::class.java)
    val TestCaseReady: Icon = IconLoader.getIcon("/icons/testCaseReady.svg", SpeqaIcons::class.java)
    val TestCaseDeprecated: Icon = IconLoader.getIcon("/icons/testCaseDeprecated.svg", SpeqaIcons::class.java)
    val TestRunPassed: Icon = IconLoader.getIcon("/icons/testRunPassed.svg", SpeqaIcons::class.java)
    val TestRunFailed: Icon = IconLoader.getIcon("/icons/testRunFailed.svg", SpeqaIcons::class.java)
    val TestRunBlocked: Icon = IconLoader.getIcon("/icons/testRunBlocked.svg", SpeqaIcons::class.java)

    fun forStatus(status: Status): Icon = when (status) {
        Status.DRAFT -> TestCaseDraft
        Status.READY -> TestCaseReady
        Status.DEPRECATED -> TestCaseDeprecated
    }
}
```

- [ ] **Step 2: Use the helper in `SpeqaIconProvider`**

Replace the `when (status)` block in `iconForTestCase` (lines 30-37) with the helper:

```kotlin
    private fun iconForTestCase(file: PsiFile): Icon {
        val status = Regex("""^status:\s*([A-Za-z]+)""", RegexOption.MULTILINE)
            .find(file.text)
            ?.groupValues
            ?.getOrNull(1)
            ?.let(Status::fromString)
            ?: Status.DRAFT

        return SpeqaIcons.forStatus(status)
    }
```

- [ ] **Step 3: Compile**

Run: `./gradlew compileKotlin 2>&1 | grep -E "^e:|BUILD"`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/io/github/barsia/speqa/filetype/SpeqaIcons.kt src/main/kotlin/io/github/barsia/speqa/filetype/SpeqaIconProvider.kt
git commit -m "refactor: extract SpeqaIcons.forStatus helper"
```

---

## Task 2: Pure ordering and filtering

The contract: a node's children are its subdirectories (folders, all of them) plus its `.tc.md` files; folders sort first by name, then test cases by `title`; both use case-insensitive natural ordering. This logic is pure and unit-tested.

**Files:**
- Create: `src/main/kotlin/io/github/barsia/speqa/toolwindow/SpeqaTreeOrdering.kt`
- Test: `src/test/kotlin/io/github/barsia/speqa/toolwindow/SpeqaTreeOrderingTest.kt`

- [ ] **Step 1: Write the failing test**

Create `src/test/kotlin/io/github/barsia/speqa/toolwindow/SpeqaTreeOrderingTest.kt`:

```kotlin
package io.github.barsia.speqa.toolwindow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeqaTreeOrderingTest {

    private fun folder(name: String) = SpeqaTreeItem.Folder(name, name)
    private fun case(title: String) = SpeqaTreeItem.TestCase(title, title)

    @Test
    fun `folders come before test cases`() {
        val ordered = orderChildren(listOf(case("zzz"), folder("alpha")))
        assertTrue(ordered[0] is SpeqaTreeItem.Folder)
        assertTrue(ordered[1] is SpeqaTreeItem.TestCase)
    }

    @Test
    fun `folders sorted case-insensitively by name`() {
        val ordered = orderChildren(listOf(folder("Beta"), folder("alpha"), folder("Gamma")))
        assertEquals(listOf("alpha", "Beta", "Gamma"), ordered.map { it.payload })
    }

    @Test
    fun `test cases sorted by title with natural order`() {
        val ordered = orderChildren(listOf(case("Step 10"), case("Step 2"), case("Step 1")))
        assertEquals(listOf("Step 1", "Step 2", "Step 10"), ordered.map { it.payload })
    }

    @Test
    fun `test case file names recognized by tc-md suffix`() {
        assertTrue(isTestCaseFileName("login.tc.md"))
        assertFalse(isTestCaseFileName("notes.md"))
        assertFalse(isTestCaseFileName("run.tr.md"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "io.github.barsia.speqa.toolwindow.SpeqaTreeOrderingTest" 2>&1 | grep -E "FAILED|BUILD|error:|e:"`
Expected: compilation failure / unresolved reference `SpeqaTreeItem`, `orderChildren`, `isTestCaseFileName`.

- [ ] **Step 3: Implement the ordering**

Create `src/main/kotlin/io/github/barsia/speqa/toolwindow/SpeqaTreeOrdering.kt`:

```kotlin
package io.github.barsia.speqa.toolwindow

import com.intellij.openapi.util.text.NaturalComparator
import io.github.barsia.speqa.model.SpeqaDefaults

/**
 * A child of a tree node, independent of the VFS layer so the ordering rules
 * can be unit-tested. [payload] carries the concrete element (a VirtualFile at
 * runtime); [sortKey] is the folder name or the test case title.
 */
sealed class SpeqaTreeItem<T> {
    abstract val payload: T
    abstract val sortKey: String

    class Folder<T>(override val payload: T, override val sortKey: String) : SpeqaTreeItem<T>()
    class TestCase<T>(override val payload: T, override val sortKey: String) : SpeqaTreeItem<T>()
}

/** True when [name] is a SpeQA test case file (`*.tc.md`). */
fun isTestCaseFileName(name: String): Boolean =
    name.endsWith(".${SpeqaDefaults.TEST_CASE_EXTENSION}")

/**
 * Folders first (by name), then test cases (by title); both case-insensitive
 * natural order so "Step 2" precedes "Step 10".
 */
fun <T> orderChildren(items: List<SpeqaTreeItem<T>>): List<SpeqaTreeItem<T>> {
    val byKey = Comparator<SpeqaTreeItem<T>> { a, b ->
        NaturalComparator.INSTANCE.compare(a.sortKey, b.sortKey)
    }
    val folders = items.filterIsInstance<SpeqaTreeItem.Folder<T>>().sortedWith(byKey)
    val cases = items.filterIsInstance<SpeqaTreeItem.TestCase<T>>().sortedWith(byKey)
    return folders + cases
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "io.github.barsia.speqa.toolwindow.SpeqaTreeOrderingTest" 2>&1 | grep -E "FAILED|BUILD|Tests "`
Expected: `BUILD SUCCESSFUL` (4 tests pass).

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/io/github/barsia/speqa/toolwindow/SpeqaTreeOrdering.kt src/test/kotlin/io/github/barsia/speqa/toolwindow/SpeqaTreeOrderingTest.kt
git commit -m "feat: add tool window tree ordering and filtering rules"
```

---

## Task 3: Test case summary cache

Lazily parse each `.tc.md` file's `title` and `status`, cached by modification stamp so the tree does not re-read disk on every repaint.

**Files:**
- Create: `src/main/kotlin/io/github/barsia/speqa/toolwindow/TestCaseSummaryCache.kt`

- [ ] **Step 1: Implement the cache**

Create `src/main/kotlin/io/github/barsia/speqa/toolwindow/TestCaseSummaryCache.kt`:

```kotlin
package io.github.barsia.speqa.toolwindow

import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import io.github.barsia.speqa.model.Status
import io.github.barsia.speqa.model.TestCase
import io.github.barsia.speqa.parser.TestCaseParser
import java.util.concurrent.ConcurrentHashMap

/** Parsed display data for a single test case leaf. */
data class TestCaseSummary(val title: String, val status: Status)

/**
 * Caches the parsed [TestCaseSummary] for each `.tc.md` file, keyed by path and
 * invalidated when the file's modification stamp changes (or explicitly via
 * [invalidate] on a VFS event).
 */
class TestCaseSummaryCache {
    private data class Entry(val stamp: Long, val summary: TestCaseSummary)

    private val cache = ConcurrentHashMap<String, Entry>()

    fun summaryFor(file: VirtualFile): TestCaseSummary {
        val stamp = file.modificationStamp
        cache[file.path]?.let { if (it.stamp == stamp) return it.summary }
        val summary = readSummary(file)
        cache[file.path] = Entry(stamp, summary)
        return summary
    }

    fun invalidate(path: String) {
        cache.remove(path)
    }

    private fun readSummary(file: VirtualFile): TestCaseSummary = try {
        val testCase = TestCaseParser.parse(VfsUtilCore.loadText(file))
        TestCaseSummary(testCase.title, testCase.status ?: Status.DRAFT)
    } catch (_: Exception) {
        TestCaseSummary(TestCase().title, Status.DRAFT)
    }
}
```

Note: `TestCase()` default `title` is `"Untitled Test Case"`, matching the parser's own fallback.

- [ ] **Step 2: Compile**

Run: `./gradlew compileKotlin 2>&1 | grep -E "^e:|BUILD"`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/io/github/barsia/speqa/toolwindow/TestCaseSummaryCache.kt
git commit -m "feat: add per-file test case summary cache for tool window"
```

---

## Task 4: Tree nodes

Folder nodes expose their ordered children; test-case nodes are navigable leaves labeled by `title` with a status icon.

**Files:**
- Create: `src/main/kotlin/io/github/barsia/speqa/toolwindow/SpeqaTreeNodes.kt`

- [ ] **Step 1: Implement the nodes**

Create `src/main/kotlin/io/github/barsia/speqa/toolwindow/SpeqaTreeNodes.kt`:

```kotlin
package io.github.barsia.speqa.toolwindow

import com.intellij.icons.AllIcons
import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import io.github.barsia.speqa.filetype.SpeqaIcons

/** Folder section: its children are subfolders (all of them) plus `.tc.md` leaves. */
class SpeqaFolderNode(
    project: Project,
    dir: VirtualFile,
    private val cache: TestCaseSummaryCache,
) : AbstractTreeNode<VirtualFile>(project, dir) {

    override fun getChildren(): Collection<AbstractTreeNode<*>> {
        val dir = value
        if (!dir.isValid || !dir.isDirectory) return emptyList()

        val items = dir.children.mapNotNull { child ->
            when {
                child.isDirectory -> SpeqaTreeItem.Folder(child, child.name)
                isTestCaseFileName(child.name) ->
                    SpeqaTreeItem.TestCase(child, cache.summaryFor(child).title)
                else -> null
            }
        }

        val project = project ?: return emptyList()
        return orderChildren(items).map { item ->
            when (item) {
                is SpeqaTreeItem.Folder -> SpeqaFolderNode(project, item.payload, cache)
                is SpeqaTreeItem.TestCase -> SpeqaTestCaseNode(project, item.payload, cache)
            }
        }
    }

    override fun update(presentation: PresentationData) {
        presentation.presentableText = value.name
        presentation.setIcon(AllIcons.Nodes.Folder)
    }
}

/** Leaf test case: labeled by parsed title, navigates to the file on open. */
class SpeqaTestCaseNode(
    project: Project,
    file: VirtualFile,
    private val cache: TestCaseSummaryCache,
) : AbstractTreeNode<VirtualFile>(project, file) {

    override fun getChildren(): Collection<AbstractTreeNode<*>> = emptyList()

    override fun update(presentation: PresentationData) {
        val summary = cache.summaryFor(value)
        presentation.presentableText = summary.title
        presentation.setIcon(SpeqaIcons.forStatus(summary.status))
    }

    override fun canNavigate(): Boolean = value.isValid

    override fun canNavigateToSource(): Boolean = value.isValid

    override fun navigate(requestFocus: Boolean) {
        val project = project ?: return
        if (value.isValid) {
            FileEditorManager.getInstance(project).openFile(value, requestFocus)
        }
    }
}
```

- [ ] **Step 2: Compile**

Run: `./gradlew compileKotlin 2>&1 | grep -E "^e:|BUILD"`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/io/github/barsia/speqa/toolwindow/SpeqaTreeNodes.kt
git commit -m "feat: add tool window folder and test case tree nodes"
```

---

## Task 5: Tree structure

Wraps the root folder node so `StructureTreeModel` can drive it.

**Files:**
- Create: `src/main/kotlin/io/github/barsia/speqa/toolwindow/SpeqaTreeStructure.kt`

- [ ] **Step 1: Implement the structure**

Create `src/main/kotlin/io/github/barsia/speqa/toolwindow/SpeqaTreeStructure.kt`:

```kotlin
package io.github.barsia.speqa.toolwindow

import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.ide.util.treeView.AbstractTreeStructure
import com.intellij.ide.util.treeView.NodeDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/**
 * Tree structure rooted at the `test-cases/` directory. The root node is hidden
 * (the tree is configured with `isRootVisible = false`), so its children form
 * the top level shown to the user.
 */
class SpeqaTreeStructure(
    project: Project,
    rootDir: VirtualFile,
    cache: TestCaseSummaryCache,
) : AbstractTreeStructure() {

    private val root = SpeqaFolderNode(project, rootDir, cache)

    override fun getRootElement(): Any = root

    override fun getChildElements(element: Any): Array<Any> =
        (element as AbstractTreeNode<*>).children.toTypedArray()

    override fun getParentElement(element: Any): Any? =
        (element as? AbstractTreeNode<*>)?.parent

    override fun createDescriptor(element: Any, parentDescriptor: NodeDescriptor<*>?): NodeDescriptor<*> =
        element as NodeDescriptor<*>

    override fun commit() = Unit

    override fun hasSomethingToCommit(): Boolean = false
}
```

- [ ] **Step 2: Compile**

Run: `./gradlew compileKotlin 2>&1 | grep -E "^e:|BUILD"`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/io/github/barsia/speqa/toolwindow/SpeqaTreeStructure.kt
git commit -m "feat: add tool window tree structure"
```

---

## Task 6: Tool window factory, registration, and live updates

Builds the tree, wires navigation and speed-search, keeps it live via a scoped VFS listener, and registers the tool window in `plugin.xml`.

**Files:**
- Create: `src/main/kotlin/io/github/barsia/speqa/toolwindow/SpeqaToolWindowFactory.kt`
- Modify: `src/main/resources/messages/SpeqaBundle.properties`
- Modify: `src/main/resources/META-INF/plugin.xml`

- [ ] **Step 1: Add the empty-state bundle string**

Append to `src/main/resources/messages/SpeqaBundle.properties` (under a new section heading):

```properties

# --- Tool window ---
toolwindow.speqa.empty=No test cases yet
```

- [ ] **Step 2: Implement the factory**

Create `src/main/kotlin/io/github/barsia/speqa/toolwindow/SpeqaToolWindowFactory.kt`:

```kotlin
package io.github.barsia.speqa.toolwindow

import com.intellij.ide.util.treeView.NodeRenderer
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.TreeSpeedSearch
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.tree.AsyncTreeModel
import com.intellij.ui.tree.StructureTreeModel
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.EditSourceOnDoubleClickHandler
import com.intellij.util.EditSourceOnEnterKeyHandler
import io.github.barsia.speqa.SpeqaBundle
import io.github.barsia.speqa.wizard.SpeqaProjectScaffold

class SpeqaToolWindowFactory : ToolWindowFactory, DumbAware {

    override fun shouldBeAvailable(project: Project): Boolean = testCasesDir(project) != null

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val rootDir = testCasesDir(project) ?: return
        val cache = TestCaseSummaryCache()
        val structure = SpeqaTreeStructure(project, rootDir, cache)
        val treeModel = StructureTreeModel(structure, toolWindow.disposable)
        val asyncModel = AsyncTreeModel(treeModel, toolWindow.disposable)

        val tree = Tree(asyncModel).apply {
            isRootVisible = false
            showsRootHandles = true
            cellRenderer = NodeRenderer()
            emptyText.text = SpeqaBundle.message("toolwindow.speqa.empty")
        }
        TreeSpeedSearch.installOn(tree)
        EditSourceOnDoubleClickHandler.install(tree)
        EditSourceOnEnterKeyHandler.install(tree)

        subscribeToVfsChanges(project, toolWindow, rootDir, cache, treeModel)

        val content = ContentFactory.getInstance()
            .createContent(ScrollPaneFactory.createScrollPane(tree), null, false)
        toolWindow.contentManager.addContent(content)
    }

    private fun subscribeToVfsChanges(
        project: Project,
        toolWindow: ToolWindow,
        rootDir: VirtualFile,
        cache: TestCaseSummaryCache,
        treeModel: StructureTreeModel<SpeqaTreeStructure>,
    ) {
        project.messageBus.connect(toolWindow.disposable)
            .subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
                override fun after(events: List<VFileEvent>) {
                    val rootPath = rootDir.path
                    val relevant = events.filter { it.path.startsWith(rootPath) }
                    if (relevant.isEmpty()) return
                    relevant.forEach { cache.invalidate(it.path) }
                    treeModel.invalidateAsync()
                }
            })
    }

    private fun testCasesDir(project: Project): VirtualFile? =
        project.guessProjectDir()
            ?.findChild(SpeqaProjectScaffold.TEST_CASES_DIR)
            ?.takeIf { it.isDirectory }
}
```

- [ ] **Step 3: Register the tool window in `plugin.xml`**

In `src/main/resources/META-INF/plugin.xml`, inside the `<extensions defaultExtensionNs="com.intellij">` block (after the `<fileEditorProvider .../>` lines), add:

```xml
        <toolWindow id="SpeQA"
                    anchor="left"
                    secondary="false"
                    icon="/icons/speqa16.svg"
                    factoryClass="io.github.barsia.speqa.toolwindow.SpeqaToolWindowFactory"/>
```

- [ ] **Step 4: Compile**

Run: `./gradlew compileKotlin 2>&1 | grep -E "^e:|BUILD"`
Expected: `BUILD SUCCESSFUL`. If an import is unresolved (platform package paths can differ slightly by SDK version), fix the import to the path your SDK exposes and recompile. Known alternate for `VFileEvent`: `com.intellij.openapi.vfs.newvfs.events.VFileEvent`.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/io/github/barsia/speqa/toolwindow/SpeqaToolWindowFactory.kt src/main/resources/messages/SpeqaBundle.properties src/main/resources/META-INF/plugin.xml
git commit -m "feat: add SpeQA test cases tool window"
```

---

## Task 7: Full verification and manual smoke test

**Files:** none (verification only).

- [ ] **Step 1: Run the full test suite**

Run: `./gradlew test 2>&1 | grep -E "FAILED|BUILD|Tests "`
Expected: `BUILD SUCCESSFUL`, no `FAILED`.

- [ ] **Step 2: Launch the sandbox IDE**

Run: `./gradlew runIde`

Manual checks in the sandbox (open or create a SpeQA project with a `test-cases/` folder):
- The "SpeQA" tool window appears docked on the left.
- Subfolders under `test-cases/` show as folders, including empty ones.
- `.tc.md` files show by their `title`, not file name; non-`.tc.md` files are hidden.
- Folders sort first, then test cases alphabetically by title.
- Double-click / Enter on a test case opens it in the SpeQA split editor.
- Editing and saving a test case's `title` updates the tree label; adding/deleting a `.tc.md` file updates the tree without restart.
- Verify in both a light and a dark theme that icons and labels render natively.

- [ ] **Step 3: Final commit (only if manual fixes were needed)**

```bash
git add -A
git commit -m "fix: tool window smoke-test follow-ups"
```

---

## Self-Review Notes

- **Spec coverage:** placement/availability (Task 6 `shouldBeAvailable` + `plugin.xml` anchor), root = `test-cases/` with hidden root node (Tasks 5-6), folders-as-sections incl. empty (Task 4 `getChildren` keeps all dirs), leaves by `title` with status icon (Tasks 3-4), non-`.tc.md` hidden (Task 2 filter), folders-first natural ordering (Task 2), double-click/Enter open (Task 4 navigation + Task 6 handlers), speed-search (Task 6), live updates + title cache invalidation (Tasks 3, 6), empty-state string (Task 6). All spec bullets map to a task.
- **No hardcoded UI strings:** the only free-text label is the empty-state, sourced from `SpeqaBundle`. The tool window id "SpeQA" is the brand name used as the stripe title.
- **Colors/icons:** icons come from `SpeqaIcons` / `AllIcons`; no hardcoded colors. `NodeRenderer` uses native theme tokens.
- **DRY:** status-to-icon mapping is shared via `SpeqaIcons.forStatus` (Task 1); `test-cases` dir name reuses `SpeqaProjectScaffold.TEST_CASES_DIR`; `.tc.md` suffix reuses `SpeqaDefaults.TEST_CASE_EXTENSION`.
