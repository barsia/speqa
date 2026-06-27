# Create-From-Test-Cases Flow Implementation Plan (Plan 3 of 3)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add the Test Runs tab entry points (`+ Create test run` header action + empty-state CTA) and an adaptive Create Test Run dialog that selects multiple test cases by facet and produces ONE multi-case `.tr.md` via `TestRunSupport.createMultiCaseRun`, then opens it.

**Architecture:** Pure logic first (adaptive facet detection, dialog filter predicate mirroring the tool-window `matchesFilter`, selection model, date/time title), each unit-tested. Then the dialog UI (extend the existing `RunCreationDialog` concept into a `CreateTestRunDialog` with adaptive Priority/Tags/Environment filters, a live checkbox case list with select-all/clear, per-section import options, an editable date/time title, and a footer count). Then the entry points (header action placed first in the Test Runs tab, empty-state button) and preserving the single-case entry (opens the dialog with that one case pre-checked). The Create action gathers `SourceCase`s from the checked files (parse each `.tc.md` to a `TestCase`) and calls `createMultiCaseRun`.

**Tech Stack:** Kotlin, IntelliJ Platform plugin (Swing UI, `DialogWrapper`, tool-window `titleActions`), JUnit 4. Reuses `TestRunSupport.createMultiCaseRun`/`SourceCase`/`normalizeRunFileName`, `TestCaseSummaryCache`, `TestCaseParser`, `RunImportOptions`, `SpeqaBundle`.

**Design reference:** `docs/plans/2026-06-26-test-run-from-test-cases-design.md` (Phase 2). Builds on Plan 1 + Plan 2, branch `feat/multi-case-run-format`.

**Milestones:** Tasks 1-5 are pure logic + the creation wiring (fully testable headless). Tasks 6-9 are the dialog + entry-point UI. Task 10 is spec + regression.

---

## Background the implementer needs

- `TestRunSupport.createMultiCaseRun(cases: List<SourceCase>, targetDirectoryPath: String, importOptions: RunImportOptions = RunImportOptions(), runner: String = defaultRunner(), title: String = ""): TestRun` and `data class SourceCase(val testCase: TestCase, val sourceFilePath: String)` exist (Plan 1). `TestRunSupport.normalizeRunFileName(requested, existingNames): String` enforces filename uniqueness. `TestRunSerializer.serialize` + the write/open path used by the single-case flow live in `editor/SpeqaEditorSupport.kt` (`startTestRun`) and `RunCreationDialog`.
- Test-case facet values come from `toolwindow/TestCaseSummaryCache.kt`: `data class TestCaseSummary(title, status, priority: Priority, tags: Set<String>, environments: Set<String>, ...)`, `summaryFor(file): TestCaseSummary`.
- The tool-window filter predicate to MIRROR is `toolwindow/SpeqaTreeFilter.kt` `matchesFilter(summary, filter)`: AND across facets; within tags/environments match-any; a facet with no selection does not constrain.
- The Test Runs tab header actions are exposed as `titleActions: List<AnAction>` in `toolwindow/SpeqaFilterHeader.kt` and wired by `toolwindow/SpeqaToolWindowFactory.kt`. The single-case entry is `RunTestCaseAction` -> `startTestRun(project, file)`.
- `Priority` enum: CRITICAL/MAJOR/NORMAL/LOW. A test case "has a priority facet value" always (priority is non-null on the summary); treat the Priority filter as present whenever there are >=2 distinct priorities among the cases, or always-present per the design's "shown only if at least one test case has a value" - see Task 1 for the exact rule.
- Markdown files reject the em dash character via a pre-commit hook; use a regular hyphen in any `.md` you touch.
- Pre-existing unrelated working-tree files (docs/specs/2026-04-06-speqa-design.md, SpeqaEditorBase, SpeqaEditorSupport (NOTE: this one is touched by this plan - coordinate), SpeqaToolWindowLayout, CreateTestCaseAction) must not be casually staged; stage explicit paths per task.

---

## Task 1: Adaptive facet detection (which filters to show)

**Files:**
- Create: `src/main/kotlin/io/github/barsia/speqa/editor/runcreation/CreateRunFacets.kt`
- Test: `src/test/kotlin/io/github/barsia/speqa/editor/runcreation/CreateRunFacetsTest.kt`

A facet control is shown only when it can actually narrow the set. Rule: **Priority** shown when the cases span >=2 distinct priorities; **Tags** shown when at least one case has >=1 tag; **Environment** shown when at least one case has >=1 environment.

- [ ] **Step 1: Failing test**

```kotlin
package io.github.barsia.speqa.editor.runcreation

import io.github.barsia.speqa.model.Priority
import org.junit.Assert.assertEquals
import org.junit.Test

class CreateRunFacetsTest {
    private fun case(p: Priority, tags: Set<String> = emptySet(), envs: Set<String> = emptySet()) =
        CaseFacets(priority = p, tags = tags, environments = envs)

    @Test fun `priority facet shown only when multiple distinct priorities`() {
        assertEquals(false, CreateRunFacets.present(listOf(case(Priority.MAJOR), case(Priority.MAJOR))).priority)
        assertEquals(true, CreateRunFacets.present(listOf(case(Priority.MAJOR), case(Priority.LOW))).priority)
    }

    @Test fun `tags facet shown when any case has a tag`() {
        assertEquals(false, CreateRunFacets.present(listOf(case(Priority.LOW))).tags)
        assertEquals(true, CreateRunFacets.present(listOf(case(Priority.LOW, tags = setOf("smoke")))).tags)
    }

    @Test fun `environment facet shown when any case has an environment`() {
        assertEquals(false, CreateRunFacets.present(listOf(case(Priority.LOW))).environments)
        assertEquals(true, CreateRunFacets.present(listOf(case(Priority.LOW, envs = setOf("chrome")))).environments)
    }

    @Test fun `empty input shows no facets`() {
        val p = CreateRunFacets.present(emptyList())
        assertEquals(false, p.priority); assertEquals(false, p.tags); assertEquals(false, p.environments)
    }
}
```

- [ ] **Step 2: Run -> FAIL.** `./gradlew test --tests "*CreateRunFacetsTest*" --rerun-tasks 2>&1 | grep -E "FAILED|BUILD|^e:"`.

- [ ] **Step 3: Implement**

```kotlin
package io.github.barsia.speqa.editor.runcreation

import io.github.barsia.speqa.model.Priority

/** Facet values of one test case relevant to run creation. */
data class CaseFacets(
    val priority: Priority,
    val tags: Set<String>,
    val environments: Set<String>,
)

/** Which adaptive filter controls the Create Test Run dialog should show. */
data class PresentFacets(val priority: Boolean, val tags: Boolean, val environments: Boolean)

object CreateRunFacets {
    fun present(cases: List<CaseFacets>): PresentFacets = PresentFacets(
        priority = cases.map { it.priority }.distinct().size >= 2,
        tags = cases.any { it.tags.isNotEmpty() },
        environments = cases.any { it.environments.isNotEmpty() },
    )
}
```

- [ ] **Step 4: Run -> PASS. Commit**

```bash
git add src/main/kotlin/io/github/barsia/speqa/editor/runcreation/CreateRunFacets.kt src/test/kotlin/io/github/barsia/speqa/editor/runcreation/CreateRunFacetsTest.kt
git commit -m "feat: adaptive facet detection for the Create Test Run dialog"
```

---

## Task 2: Dialog filter predicate + selection model (mirror matchesFilter)

**Files:**
- Modify: `src/main/kotlin/io/github/barsia/speqa/editor/runcreation/CreateRunFacets.kt` (add the predicate + a selection holder), or a sibling file `CreateRunSelection.kt`
- Test: `src/test/kotlin/io/github/barsia/speqa/editor/runcreation/CreateRunSelectionTest.kt`

The live case list shows cases matching the active filters; filtering mirrors `matchesFilter` (AND across facets; tags/environments match-any; empty facet selection does not constrain).

- [ ] **Step 1: Failing test**

```kotlin
package io.github.barsia.speqa.editor.runcreation

import io.github.barsia.speqa.model.Priority
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CreateRunSelectionTest {
    private fun c(p: Priority, tags: Set<String> = emptySet(), envs: Set<String> = emptySet()) =
        CaseFacets(p, tags, envs)

    @Test fun `no active filter matches all`() {
        val f = CreateRunFilter()
        assertTrue(f.matches(c(Priority.LOW)))
    }

    @Test fun `priority filter constrains`() {
        val f = CreateRunFilter(priority = Priority.MAJOR)
        assertEquals(true, f.matches(c(Priority.MAJOR)))
        assertEquals(false, f.matches(c(Priority.LOW)))
    }

    @Test fun `tags match any selected`() {
        val f = CreateRunFilter(tags = setOf("smoke", "auth"))
        assertEquals(true, f.matches(c(Priority.LOW, tags = setOf("auth"))))
        assertEquals(false, f.matches(c(Priority.LOW, tags = setOf("regression"))))
    }

    @Test fun `facets combine with AND`() {
        val f = CreateRunFilter(priority = Priority.MAJOR, environments = setOf("chrome"))
        assertEquals(true, f.matches(c(Priority.MAJOR, envs = setOf("chrome"))))
        assertEquals(false, f.matches(c(Priority.MAJOR, envs = setOf("firefox"))))
    }
}
```

- [ ] **Step 2: Run -> FAIL.**

- [ ] **Step 3: Implement**

```kotlin
/** Active filter selection in the Create Test Run dialog; mirrors tool-window matchesFilter. */
data class CreateRunFilter(
    val priority: Priority? = null,
    val tags: Set<String> = emptySet(),
    val environments: Set<String> = emptySet(),
) {
    fun matches(facets: CaseFacets): Boolean {
        if (priority != null && facets.priority != priority) return false
        if (tags.isNotEmpty() && facets.tags.none { it in tags }) return false
        if (environments.isNotEmpty() && facets.environments.none { it in environments }) return false
        return true
    }
}
```

- [ ] **Step 4: Run -> PASS. Commit**

```bash
git add src/main/kotlin/io/github/barsia/speqa/editor/runcreation/ src/test/kotlin/io/github/barsia/speqa/editor/runcreation/CreateRunSelectionTest.kt
git commit -m "feat: Create Test Run dialog filter predicate mirroring matchesFilter"
```

---

## Task 3: Default run title (date/time)

**Files:**
- Create: `src/main/kotlin/io/github/barsia/speqa/editor/runcreation/CreateRunTitle.kt`
- Test: `src/test/kotlin/io/github/barsia/speqa/editor/runcreation/CreateRunTitleTest.kt`

Default title carries date/time so repeats are distinct. When exactly one facet VALUE is active (e.g. a single priority selected, or a single tag), prefix it; otherwise "Test Run". The timestamp is passed in (no `Date.now()` in tests/scripts).

- [ ] **Step 1: Failing test**

```kotlin
package io.github.barsia.speqa.editor.runcreation

import org.junit.Assert.assertEquals
import org.junit.Test

class CreateRunTitleTest {
    @Test fun `single active facet value prefixes the title`() {
        assertEquals("High - 2026-06-27 14:30",
            CreateRunTitle.defaultTitle(activeLabel = "High", timestamp = "2026-06-27 14:30"))
    }
    @Test fun `no single active facet uses generic prefix`() {
        assertEquals("Test Run - 2026-06-27 14:30",
            CreateRunTitle.defaultTitle(activeLabel = null, timestamp = "2026-06-27 14:30"))
    }
}
```

- [ ] **Step 2: Run -> FAIL.**

- [ ] **Step 3: Implement**

```kotlin
package io.github.barsia.speqa.editor.runcreation

object CreateRunTitle {
    /** [activeLabel] is the single active facet value (e.g. a lone selected priority/tag) or null. */
    fun defaultTitle(activeLabel: String?, timestamp: String): String =
        "${activeLabel ?: "Test Run"} - $timestamp"
}
```
(The dialog computes `activeLabel`: the single selected priority's display label when only Priority is constrained to one value and tags/environments are empty; or the single selected tag/environment when that is the only constraint; else null. The `timestamp` is formatted by the dialog from the current time with a stable `yyyy-MM-dd HH:mm` formatter.)

- [ ] **Step 4: Run -> PASS. Commit**

```bash
git add src/main/kotlin/io/github/barsia/speqa/editor/runcreation/CreateRunTitle.kt src/test/kotlin/io/github/barsia/speqa/editor/runcreation/CreateRunTitleTest.kt
git commit -m "feat: default date/time run title for the Create Test Run dialog"
```

---

## Task 4: Footer summary text

**Files:**
- Modify: `src/main/kotlin/io/github/barsia/speqa/editor/runcreation/CreateRunTitle.kt` (or a small `CreateRunSummary.kt`)
- Test: add to an existing runcreation test file
- Modify: `src/main/resources/messages/SpeqaBundle.properties`

The footer reads "N cases selected -> 1 test run (N sections)" (0 when none). Keep the counting pure; format via bundle.

- [ ] **Step 1: Failing test** for `CreateRunSummary.selectionCount(selected: Int): Pair<Int, Int>` returning `(selected, sections)` where sections == selected. (Trivial but pins the contract and lets the dialog format from counts.)
```kotlin
@Test fun `selection count maps to section count`() {
    assertEquals(3 to 3, CreateRunSummary.selectionCount(3))
    assertEquals(0 to 0, CreateRunSummary.selectionCount(0))
}
```
- [ ] **Step 2-3:** implement `object CreateRunSummary { fun selectionCount(selected: Int) = selected to selected }`; add bundle string `dialog.createRun.footer={0} cases selected -> {1} test run sections` (use a regular hyphen/arrow text the bundle allows; keep ASCII). Run -> PASS.
- [ ] **Step 4: Commit**
```bash
git add src/main/kotlin/io/github/barsia/speqa/editor/runcreation/ src/test/kotlin/io/github/barsia/speqa/editor/runcreation/ src/main/resources/messages/SpeqaBundle.properties
git commit -m "feat: Create Test Run dialog footer summary"
```

---

## Task 5: Multi-case run creation wiring (gather SourceCases, write, open)

**Files:**
- Modify: `src/main/kotlin/io/github/barsia/speqa/editor/SpeqaEditorSupport.kt` (add a multi-case creation entry alongside `startTestRun`)
- Test: `src/test/kotlin/io/github/barsia/speqa/run/CreateMultiCaseRunWiringTest.kt`

Add a function that, given a list of selected `.tc.md` `VirtualFile`s + a `RunCreationRequest`-like (destination, fileName, importOptions, title), parses each file to a `TestCase` (via `TestCaseParser`), builds `SourceCase(testCase, sourceRelativePath)` for each, calls `TestRunSupport.createMultiCaseRun(...)`, serializes via `TestRunSerializer`, writes the file at the destination with `normalizeRunFileName` uniqueness, and opens it in the editor. Keep the file-write/open mechanics IDENTICAL to the existing single-case `startTestRun` path (reuse its helpers; do not fork the write logic).

Because file IO/VFS is hard to unit-test headlessly, extract the PURE part - building the `TestRun` from parsed `TestCase`s + request - into a testable function and test THAT; the VFS write/open stays a thin wrapper mirroring `startTestRun`.

- [ ] **Step 1: Failing test** (pure builder)
```kotlin
@Test
fun `builds a multi-case run from selected test cases`() {
    val tcA = sampleTestCase(id = 5, title = "A", steps = 2)   // build TestCase the way TestRunSupportTest does
    val tcB = sampleTestCase(id = 8, title = "B", steps = 1)
    val run = TestRunSupport.createMultiCaseRun(
        cases = listOf(TestRunSupport.SourceCase(tcA, "test-cases/a.tc.md"),
                       TestRunSupport.SourceCase(tcB, "test-cases/b.tc.md")),
        targetDirectoryPath = "test-runs",
        importOptions = RunImportOptions(importTags = true),
        runner = "alice", title = "High - 2026-06-27 14:30",
    )
    assertEquals(2, run.cases.size)
    assertEquals("High - 2026-06-27 14:30", run.title)
    assertEquals(listOf(5, 8), run.cases.map { it.caseId })
}
```
(This largely re-confirms `createMultiCaseRun`; the NEW code under test is the wiring function that maps selected files -> parsed TestCases -> SourceCases. If you can construct `TestCase`s in-test the same way `TestRunSupportTest` does, test the wiring function with already-parsed cases by factoring it as `buildMultiCaseRun(parsed: List<SourceCase>, request, runner)`.)

- [ ] **Step 2-3:** implement `buildMultiCaseRun(...)` (pure, delegates to `createMultiCaseRun`) + a `createMultiCaseRunFile(project, selectedFiles, request, title)` VFS wrapper mirroring `startTestRun`'s write/open (parse each file with `TestCaseParser.parse`, compute each source's relative path, call `buildMultiCaseRun`, serialize, `normalizeRunFileName`, write under destination, open). Run the pure test -> PASS.

- [ ] **Step 4: Verify + commit**
```bash
./gradlew compileKotlin compileTestKotlin test --tests "*CreateMultiCaseRunWiring*" --tests "*TestRunSupport*" --rerun-tasks 2>&1 | grep -E "FAILED|BUILD|^e:"
git add src/main/kotlin/io/github/barsia/speqa/editor/SpeqaEditorSupport.kt src/test/kotlin/io/github/barsia/speqa/run/CreateMultiCaseRunWiringTest.kt
git commit -m "feat: build and write a multi-case run from selected test cases"
```
NOTE: `SpeqaEditorSupport.kt` already has unrelated pre-existing working-tree edits. Coordinate: stage it deliberately and confirm the diff is only your additions plus the pre-existing edits are NOT included - if they are intermixed, report it rather than committing the pre-existing edit.

---

## Task 6: CreateTestRunDialog - adaptive filters + live case list

**Files:**
- Create: `src/main/kotlin/io/github/barsia/speqa/editor/runcreation/CreateTestRunDialog.kt`
- Reuse: `RunCreationPathSupport`, `RunImportOptions` (from `editor/RunCreationDialog.kt`), `CreateRunFacets`, `CreateRunFilter`, `CreateRunTitle`, `CreateRunSummary`
- Modify: `src/main/resources/messages/SpeqaBundle.properties`
- Test: pure dialog-state tests where possible (the Swing dialog itself is not unit-tested; the logic it delegates to is covered by Tasks 1-4)

A `DialogWrapper` that takes the candidate test cases (file + `CaseFacets` + title) and:
- Shows Priority/Tags/Environment filter controls ONLY for `CreateRunFacets.present(...)` facets (reuse the tool-window facet popup style if practical, else simple combo/checkbox controls).
- Shows a scrollable live list of cases matching the current `CreateRunFilter`, each a `JBCheckBox` (default all checked); a Select all / Clear control at top.
- Footer: `CreateRunSummary` text "N cases selected ...".
- Import options checkboxes (reuse the existing 5 from `RunCreationDialog`, gated by whether ANY selected case has that content).
- Editable title field defaulting to `CreateRunTitle.defaultTitle(activeLabel, timestamp)`.
- Destination + file name fields (reuse `RunCreationPathSupport`); OK disabled when 0 selected or invalid path/name.
- Exposes a request (selected files + destination + fileName + importOptions + title) for the caller to pass to `createMultiCaseRunFile`.

- [ ] **Step 1:** Add any pure dialog-state helper that needs testing (e.g. recomputing the checked set when filters change: cases that drop out of the filter become unchecked/hidden; re-entering does not silently re-check - decide and TEST this rule). Write the failing test for that rule in a `CreateRunDialogStateTest`.
- [ ] **Step 2-4:** Implement the dialog using the pure pieces; wire filter-change -> recompute visible list + footer; Select all/Clear; OK-enablement. New bundle strings for: dialog title, filter labels, select-all/clear, footer, empty-match state ("0 cases selected"), title label. No hardcoded UI strings; colors via theme tokens; clickable elements get handCursor/speqaIconButton.
- [ ] **Step 5: Verify** `./gradlew compileKotlin compileTestKotlin test --tests "*CreateRunDialogState*" --rerun-tasks 2>&1 | grep -E "FAILED|BUILD|^e:"`.
- [ ] **Step 6: Commit**
```bash
git add src/main/kotlin/io/github/barsia/speqa/editor/runcreation/ src/test/kotlin/io/github/barsia/speqa/editor/runcreation/ src/main/resources/messages/SpeqaBundle.properties
git commit -m "feat: adaptive Create Test Run dialog with live case selection"
```

---

## Task 7: Header action `+ Create test run` (Test Runs tab, placed first)

**Files:**
- Modify: `src/main/kotlin/io/github/barsia/speqa/toolwindow/SpeqaFilterHeader.kt` (add the action to `titleActions`, FIRST, with a separator before the facets) and/or `SpeqaToolWindowFactory.kt`
- Modify: `src/main/resources/messages/SpeqaBundle.properties`
- Test: pure enablement-rule test if extracted

The action opens `CreateTestRunDialog` with all test cases, no preselection of facets (all cases checked). Enabled when >=1 test case exists; disabled with tooltip "Create a test case first" otherwise. On OK -> `createMultiCaseRunFile(...)`.

- [ ] **Step 1:** If you extract an enablement predicate (`fun canCreateRun(testCaseCount: Int) = testCaseCount > 0`), TDD it. Otherwise implement directly.
- [ ] **Step 2-4:** Add an `AnAction` (icon + `SpeqaBundle` text) FIRST in the Test Runs tab `titleActions` with a separator before the filter facets (the design: primary action precedes the refine-only facets). `update()` sets enabled + tooltip from the test-case count (via `TestCaseSummaryCache`/the tree model). `actionPerformed` gathers all test cases -> `CaseFacets` -> opens `CreateTestRunDialog` -> on OK calls `createMultiCaseRunFile`. Use `ActionUpdateThread.BGT` as the other actions do.
- [ ] **Step 5: Verify** the plugin compiles and the run-creation tests stay green; sandbox smoke deferred to Task 10.
- [ ] **Step 6: Commit**
```bash
git add src/main/kotlin/io/github/barsia/speqa/toolwindow/ src/main/resources/messages/SpeqaBundle.properties
git commit -m "feat: + Create test run header action in the Test Runs tab"
```

---

## Task 8: Empty-state CTA

**Files:**
- Modify: the Test Runs tab content builder (`SpeqaToolWindowFactory.kt` / the panel that renders the empty tab body)
- Modify: `src/main/resources/messages/SpeqaBundle.properties`

When no runs exist, the Test Runs tab body shows a prominent `+ Create test run` button (same action as Task 7). When no test cases exist, show it disabled with the hint "Create a test case first - there is nothing to run yet."

- [ ] **Step 1-4:** Add the empty-state button to the runs tab empty body, invoking the same creation entry as Task 7; enable/disable + hint by test-case count. Bundle strings; theme tokens; handCursor.
- [ ] **Step 5: Commit**
```bash
git add src/main/kotlin/io/github/barsia/speqa/toolwindow/ src/main/resources/messages/SpeqaBundle.properties
git commit -m "feat: empty-state Create test run CTA in the Test Runs tab"
```

---

## Task 9: Preserve the single-case entry (pre-checked case)

**Files:**
- Modify: `src/main/kotlin/io/github/barsia/speqa/editor/SpeqaEditorSupport.kt` (`startTestRun`) and/or `RunTestCaseAction`
- Test: behavior preserved (existing single-case run tests stay green)

The existing "run this test case" entry opens the SAME `CreateTestRunDialog` with only that one case present and pre-checked, producing a one-section run (which renders flat per Plan 2). Keep today's single-case behavior intact: a user running one case still gets a one-case run file at the same destination/name default.

- [ ] **Step 1-4:** Route `startTestRun(project, file)` to open `CreateTestRunDialog` seeded with the single case (pre-checked, facets hidden since one case), or - if simpler and behavior-identical - keep the existing single-case `RunCreationDialog` path for the single-case entry and only use `CreateTestRunDialog` for the tab entry points. CHOOSE the lower-risk option that keeps the existing single-case flow and its tests green; report which. Verify existing run-creation tests pass.
- [ ] **Step 5: Commit**
```bash
git add src/main/kotlin/io/github/barsia/speqa/
git commit -m "feat: single-case run entry opens the unified create dialog (one pre-checked case)"
```

---

## Task 10: Spec + full regression

**Files:**
- Modify: `docs/specs/2026-04-06-speqa-design.md` (Test Runs tab entry points + adaptive Create Test Run dialog; fold in the Plan 2 multi-case editor sections too if not already). Coordinate with the plan owner: this file carries pre-existing unrelated working-tree edits; do not clobber them.
- Verify: whole suite.

- [ ] **Step 1: Spec.** Document the entry points (header action first + empty-state CTA, disabled-when-no-cases), the adaptive dialog (filters shown per present facets, AND/any semantics, live checkbox list with select-all/clear default-all-checked, per-section import options, date/time title, filename uniqueness), and that Create writes one multi-case `.tr.md` via `createMultiCaseRun` and opens it. Regular hyphens only. If the spec file is entangled with unrelated WIP, add only your sections and leave staging to the plan owner.
- [ ] **Step 2: Full build + regression.** `./gradlew compileKotlin compileTestKotlin test --rerun-tasks 2>&1 | grep -E "FAILED|BUILD|^e:"` -> BUILD SUCCESSFUL, 0 failures.
- [ ] **Step 3: Sandbox smoke (manual).** With >=2 test cases of differing facets: open the Test Runs tab -> `+ Create test run` -> filter, check/uncheck, Select all/Clear, confirm footer count, set title, Create -> one multi-case run opens (sectioned editor). With one case via the single-case entry -> one-section run (flat). With no test cases -> header action + empty-state button disabled with hint.
- [ ] **Step 4: Commit**
```bash
git add docs/specs/2026-04-06-speqa-design.md
git commit -m "docs: spec the Create Test Run flow (entry points + adaptive dialog)"
```

---

## Self-review notes
- **Spec coverage:** adaptive facets (Task 1), filter predicate (Task 2), title (Task 3), footer (Task 4), creation wiring (Task 5), dialog (Task 6), header action (Task 7), empty-state CTA (Task 8), single-case entry preserved (Task 9), spec + regression (Task 10).
- **Reuses Plan 1/2:** `createMultiCaseRun`/`SourceCase`/`normalizeRunFileName`; the multi-case editor (Plan 2) renders the produced run; single selection -> one-section run renders flat.
- **YAGNI:** no saved filter presets, no per-case destination, no cross-run reporting (design out-of-scope).

## Not in this plan
- Adding/removing case sections in an existing run after creation (design: possible later).
- Reporting/dashboards beyond the run editor's per-case results and aggregate.
