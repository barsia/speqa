# Multi-Case Run Editor Implementation Plan (Plan 2 of 3)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the run editor render each test case as a collapsible, reorderable, editable section (per-case metadata, body, steps, and result), show aggregated run progress, and close the Plan 1 release blocker by routing all run edits through full re-serialization instead of the frontmatter-targeting `DocumentPatcher`.

**Architecture:** All RUN-mode edits regenerate the whole `.tr.md` via `TestRunSerializer` (no `DocumentPatcher` for runs; CASE mode keeps its surgical patching). A single-case run keeps today's flat editor look; a multi-case run renders a vertical list of collapsible `RunCaseSection` panels (one per `RunCase`), each emitting its updated `RunCase` upward so the container rebuilds `TestRun.cases`, recomputes the aggregate, and re-serializes. Whole sections reorder via the existing step drag-and-drop primitives lifted to section level. Per-case result auto-derives from that case's steps and is manually overridable (persisted via a new per-section `manual_result:` line).

**Tech Stack:** Kotlin, IntelliJ Platform plugin (Swing UI), JUnit 4. Reuses `StepsSection`, `DragReorderSupport`/`LivePreviewReorderDecorator`/`DragDropIndexMath`, `SpeqaThemeColors`, `speqaIconButton`, `SpeqaBundle.properties`.

**Design reference:** `docs/plans/2026-06-26-test-run-from-test-cases-design.md` (Phase 1 run editor). Builds on Plan 1 (`docs/plans/2026-06-26-multi-case-run-format-plan.md`), branch `feat/multi-case-run-format`.

**Milestones:** Tasks 1-6 are the foundation (close the blocker + format/result logic); they leave single-case run editing fully correct and are verifiable on their own. Tasks 7-11 add the sectioned multi-case UI. Task 12 is regression + spec.

---

## Background the implementer needs

- `TestRun(id, title, runner, startedAt, finishedAt, result, manualResult, cases: List<RunCase>, comment)`. `RunCase(caseId, title, priority, tags, environment, bodyBlocks, links, attachments, stepResults, result)`. Per-case fields on `TestRun` are READ-ONLY compat getters delegating to the single case; `withSingleCase { }` mutates the lone case. (Plan 1.)
- The run editor: `run/TestRunEditor.kt` hosts `editor/ui/TestCasePanel.kt` in `PanelMode.RUN`. `TestCasePanel.emitRun(updated, op)` routes to `onRunPatch(updated, op)` when `op != null && onRunPatch != null`, else `onRunChange(updated)` (`TestCasePanel.kt:113-117`). `TestRunEditor` wires `onRunPatch` to `patchFromPreview` (surgical via `DocumentPatcher`) and `onRunChange` to `saveToDocument` (full re-serialize) (`TestRunEditor.kt:36-44, 90-113`).
- `TestRunSerializer.serialize` writes run frontmatter + one `Test Case: TC-<id> <title>` section per case, with optional `priority:`/`tags:`/`environment:` lines, body/Links/Attachments, `Scenario:` steps, and a trailing `Result:` (omitted for `NOT_STARTED`). `TestRunParser.parseCaseSections` reads them; `parseLegacyCase` handles marker-less files. (Plan 1.)
- `TestRunSupport.deriveRunResult(stepResults)` = per-case step->result; `TestRunSupport.aggregateResult(cases)` = run-level aggregate. Both exist. (Plan 1.)
- Markdown files reject the em dash character via a pre-commit hook; use a regular hyphen in any `.md` you touch.

---

## Task 1: Route all run edits through full re-serialization (close the release blocker)

**Files:**
- Modify: `src/main/kotlin/io/github/barsia/speqa/run/TestRunEditor.kt`
- Test: `src/test/kotlin/io/github/barsia/speqa/run/RunFullReserializeEditTest.kt` (create)

- [ ] **Step 1: Write the failing test** (this pins the Plan 1 regression: editing a sectioned single-case run must preserve the edit through the editor's save path). Since the editor save path for runs will become "rebuild model -> `TestRunSerializer.serialize`", the test exercises that contract directly at the support level:

```kotlin
package io.github.barsia.speqa.run

import io.github.barsia.speqa.model.Priority
import io.github.barsia.speqa.model.RunCase
import io.github.barsia.speqa.model.TestRun
import io.github.barsia.speqa.parser.TestRunParser
import io.github.barsia.speqa.parser.TestRunSerializer
import org.junit.Assert.assertEquals
import org.junit.Test

class RunFullReserializeEditTest {
    @Test
    fun `editing tags then re-serializing preserves the edit for a single-case run`() {
        val run = TestRun(
            id = 7, title = "Login", runner = "alice",
            cases = listOf(RunCase(caseId = 5, title = "Sign in",
                priority = Priority.MAJOR, tags = listOf("smoke"))),
        )
        // Simulate the editor's full-reserialize edit path: mutate the model, serialize, reload.
        val edited = run.withSingleCase { it.copy(tags = listOf("smoke", "regression")) }
        val reloaded = TestRunParser.parse(TestRunSerializer.serialize(edited))
        assertEquals(listOf("smoke", "regression"), reloaded.cases.single().tags)
    }

    @Test
    fun `editing description then re-serializing keeps the case marker and metadata`() {
        val run = TestRun(id = 7, title = "Login",
            cases = listOf(RunCase(caseId = 5, title = "Sign in", priority = Priority.MAJOR,
                tags = listOf("smoke"),
                bodyBlocks = listOf(io.github.barsia.speqa.model.DescriptionBlock("Original.")))))
        val edited = run.withSingleCase {
            it.copy(bodyBlocks = listOf(io.github.barsia.speqa.model.DescriptionBlock("Edited.")))
        }
        val reloaded = TestRunParser.parse(TestRunSerializer.serialize(edited))
        val case = reloaded.cases.single()
        assertEquals(5, case.caseId)                 // marker preserved (caseId not demoted to 0)
        assertEquals(Priority.MAJOR, case.priority)  // section metadata preserved
        assertEquals("Edited.", (case.bodyBlocks.single() as io.github.barsia.speqa.model.DescriptionBlock).markdown)
    }
}
```

- [ ] **Step 2: Run to verify it passes already at the support level** (these assert the serialize/parse contract, which Plan 1 satisfies). Run: `./gradlew test --tests "*RunFullReserializeEditTest*" --rerun-tasks 2>&1 | grep -E "FAILED|BUILD"`. Expected: PASS. (This test documents the invariant; the editor wiring change below makes the EDITOR actually use this path.)

- [ ] **Step 3: Make the editor use only full re-serialization for runs.** In `TestRunEditor.kt`, pass `onRunPatch = null` to the `TestCasePanel` constructor so `emitRun`/`StepsSection` fall back to `onRunChange` (full re-serialize). Remove the now-unreachable `patchFromPreview` method and the `onRunPatch = { updated, op -> ... patchFromPreview(...) }` wiring. Remove the `DocumentPatcher` and `PatchOperation` imports if they become unused in this file. Keep `onRunChange = { updated -> current = updated; refreshHeaderFromCurrent(); saveToDocument() }` and `saveToDocument()` unchanged.

- [ ] **Step 4: Verify compile + the editor no longer references DocumentPatcher.** Run: `./gradlew compileKotlin 2>&1 | grep -E "^e:|BUILD"`. Then confirm: `grep -n "DocumentPatcher\|onRunPatch\|patchFromPreview" src/main/kotlin/io/github/barsia/speqa/run/TestRunEditor.kt` returns nothing.

- [ ] **Step 5: Run the test + run-editor-adjacent suites.** Run: `./gradlew test --tests "*RunFullReserializeEditTest*" --tests "*TestRun*" --rerun-tasks 2>&1 | grep -E "FAILED|BUILD"`. Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/io/github/barsia/speqa/run/TestRunEditor.kt src/test/kotlin/io/github/barsia/speqa/run/RunFullReserializeEditTest.kt
git commit -m "fix: route run edits through full re-serialization (close sectioned-format patcher gap)"
```

---

## Task 2: Remove dead run-specific patch operations and emit ops

**Files:**
- Modify: `src/main/kotlin/io/github/barsia/speqa/parser/DocumentPatcher.kt`
- Modify: `src/main/kotlin/io/github/barsia/speqa/editor/ui/TestCasePanel.kt`
- Modify: `src/main/kotlin/io/github/barsia/speqa/editor/ui/steps/StepsSection.kt` (only if it references removed ops)

Context: With Task 1, `onRunPatch` is always null for runs, so the run-only `PatchOperation` subtypes and their handlers are dead. CASE-mode ops (`SetDescription`, `SetPreconditions`, `SetStep*`, `SetAttachments`, `SetLinks`, `AddStep`, `DeleteStep`, `ReorderSteps`, `SetFrontmatterField`, `SetFrontmatterList`) MUST stay (test cases still patch surgically).

- [ ] **Step 1: Identify the run-only ops.** These are run-only and become dead: `SetRunVerdict`, `SetRunner`, `SetRunTags`, `SetRunEnvironment`, `SetRunStepVerdict`, `SetRunStepComment`, `SetRunLinks`, `SetRunAttachments` (`DocumentPatcher.kt:42-54`). Confirm with `grep -rn "PatchOperation.SetRun" src/main/kotlin` that, after Task 1, the only references are the constructions inside `TestCasePanel.kt`/`StepsSection.kt` emit calls (which we remove) and the `when` arms in `DocumentPatcher.patch`.

- [ ] **Step 2: Remove the run-only emit ops in `TestCasePanel.kt`.** For each RUN-mode site that calls `emitRun(currentRun.withSingleCase { ... }, PatchOperation.SetRun...(...))`, drop the second argument so it becomes `emitRun(currentRun.withSingleCase { ... })`. There are the 14 sites migrated in Plan 1 (tags/priority/environment/links/attachments/stepResults/bodyBlocks) plus runner/verdict. After this, no `PatchOperation.SetRun*` remains in `TestCasePanel.kt`. (`emitRun(updated)` with no op already routes to `onRunChange`.)

- [ ] **Step 3: Remove the run-only ops in `StepsSection.kt`.** The RUN-mode `onStepVerdictChange`/`onStepCommentChange` paths construct `SetRunStepVerdict`/`SetRunStepComment`. Route them to the no-op-arg run emit (the section already falls back to `onRunChange?.invoke(currentRun)` when `onRunPatch == null`, `TestCasePanel.kt:402`). Ensure the RUN-mode step verdict/comment change updates `currentRun` via `withSingleCase { it.copy(stepResults = ...) }` and emits with no op.

- [ ] **Step 4: Delete the dead subtypes + their `when` arms in `DocumentPatcher.kt`.** Remove the 8 run-only `data class ... : PatchOperation` declarations and their handler branches in `patch(...)`. Remove any now-unused private helpers used only by those branches (verify each helper has no remaining caller via grep before deleting).

- [ ] **Step 5: Compile + full patcher/case suites.** Run: `./gradlew compileKotlin compileTestKotlin test --tests "*DocumentPatcher*" --tests "*TestCase*" --rerun-tasks 2>&1 | grep -E "FAILED|BUILD|^e:"`. Expected: BUILD SUCCESSFUL. If any test referenced a removed op, it was a run-path test now obsolete; migrate or delete it (report which).

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/io/github/barsia/speqa/parser/DocumentPatcher.kt src/main/kotlin/io/github/barsia/speqa/editor/ui/TestCasePanel.kt src/main/kotlin/io/github/barsia/speqa/editor/ui/steps/StepsSection.kt
git commit -m "refactor: drop dead run-specific patch operations now that runs full-reserialize"
```

---

## Task 3: Add `RunCase.manualResult` to the model

**Files:**
- Modify: `src/main/kotlin/io/github/barsia/speqa/model/TestRun.kt`
- Test: `src/test/kotlin/io/github/barsia/speqa/model/TestRunTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
@Test
fun `RunCase carries a manualResult flag defaulting to false`() {
    val auto = RunCase(caseId = 1)
    val manual = RunCase(caseId = 2, result = RunResult.BLOCKED, manualResult = true)
    assertEquals(false, auto.manualResult)
    assertEquals(true, manual.manualResult)
    assertEquals(RunResult.BLOCKED, manual.result)
}
```

- [ ] **Step 2: Run to verify it fails.** Run: `./gradlew test --tests "*TestRunTest*" --rerun-tasks 2>&1 | grep -E "FAILED|BUILD|^e:"`. Expected: compile failure (`manualResult` unresolved).

- [ ] **Step 3: Add the field.** In `RunCase`, add `val manualResult: Boolean = false` after `result`. (Place it last so existing positional/`copy` usage is unaffected.)

- [ ] **Step 4: Run to verify it passes.** Run: `./gradlew test --tests "*TestRunTest*" --rerun-tasks 2>&1 | grep -E "FAILED|BUILD|^e:"`. Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/io/github/barsia/speqa/model/TestRun.kt src/test/kotlin/io/github/barsia/speqa/model/TestRunTest.kt
git commit -m "feat: add manualResult flag to RunCase"
```

---

## Task 4: Serialize and parse the per-section `manual_result:` line

**Files:**
- Modify: `src/main/kotlin/io/github/barsia/speqa/parser/TestRunSerializer.kt`
- Modify: `src/main/kotlin/io/github/barsia/speqa/parser/TestRunParser.kt`
- Test: `src/test/kotlin/io/github/barsia/speqa/parser/TestRunSerializerTest.kt`
- Test: `src/test/kotlin/io/github/barsia/speqa/parser/TestRunParserTest.kt`

- [ ] **Step 1: Write the failing tests.** Serializer test:

```kotlin
@Test
fun `serializes manual_result in the case section only when manual`() {
    val manual = TestRun(id = 1, cases = listOf(RunCase(caseId = 5, title = "A",
        result = RunResult.BLOCKED, manualResult = true)))
    val auto = TestRun(id = 1, cases = listOf(RunCase(caseId = 5, title = "A",
        result = RunResult.PASSED, manualResult = false)))
    val outManual = TestRunSerializer.serialize(manual)
    val outAuto = TestRunSerializer.serialize(auto)
    assertTrue(outManual.contains("manual_result: true"))
    assertTrue(outManual.contains("Result: blocked"))
    assertFalse(outAuto.contains("manual_result"))
}
```

Parser + round-trip test:

```kotlin
@Test
fun `parses manual_result and round-trips it`() {
    val text = """
        ---
        id: 1
        ---

        Test Case: TC-5 A
        manual_result: true

        Scenario:

        1. Do
           - passed

        Result: blocked
    """.trimIndent()
    val run = TestRunParser.parse(text)
    assertEquals(true, run.cases.single().manualResult)
    assertEquals(RunResult.BLOCKED, run.cases.single().result)

    val original = TestRun(id = 1, cases = listOf(RunCase(caseId = 5, title = "A",
        stepResults = listOf(StepResult(action = "Do", verdict = StepVerdict.PASSED)),
        result = RunResult.BLOCKED, manualResult = true)))
    assertEquals(original.cases, TestRunParser.parse(TestRunSerializer.serialize(original)).cases)
}
```

- [ ] **Step 2: Run to verify they fail.** Run: `./gradlew test --tests "*TestRunSerializerTest*" --tests "*TestRunParserTest*" --rerun-tasks 2>&1 | grep -E "FAILED|BUILD"`. Expected: FAIL.

- [ ] **Step 3: Serialize the line.** In `TestRunSerializer.appendCaseSection`, after the `environment:` line and before the body blocks, add: `if (case.manualResult) appendLine("manual_result: true")`. (It is a metadata line directly under the marker, same group as priority/tags/environment.)

- [ ] **Step 4: Parse the line.** In `TestRunParser`: extend the leading-metadata consumption in `parseCaseSection` so a `manual_result:` line (case-insensitive) is recognized as metadata (so it is stripped from the body) and sets `manualResult = value.trim().equals("true", ignoreCase = true)`. Update `CASE_METADATA_PATTERN` to also match `manual_result`. Pass `manualResult` into the returned `RunCase`. (Legacy marker-less files have no per-section manual flag; `parseLegacyCase` leaves `manualResult = false`.)

- [ ] **Step 5: Run to verify they pass.** Run: `./gradlew test --tests "*TestRunSerializerTest*" --tests "*TestRunParserTest*" --rerun-tasks 2>&1 | grep -E "FAILED|BUILD"`. Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/io/github/barsia/speqa/parser/TestRunSerializer.kt src/main/kotlin/io/github/barsia/speqa/parser/TestRunParser.kt src/test/kotlin/io/github/barsia/speqa/parser/
git commit -m "feat: persist per-case manual_result in the run section format"
```

---

## Task 5: Per-case result recompute + manual-override helpers in `TestRunSupport`

**Files:**
- Modify: `src/main/kotlin/io/github/barsia/speqa/run/TestRunSupport.kt`
- Test: `src/test/kotlin/io/github/barsia/speqa/run/TestRunSupportTest.kt`

- [ ] **Step 1: Write the failing tests**

```kotlin
@Test
fun `recomputeCaseResult derives from steps when not manual`() {
    val case = RunCase(caseId = 1,
        stepResults = listOf(StepResult(action = "a", verdict = StepVerdict.PASSED)),
        result = RunResult.NOT_STARTED, manualResult = false)
    val updated = TestRunSupport.recomputeCaseResult(case)
    assertEquals(RunResult.PASSED, updated.result)
    assertEquals(false, updated.manualResult)
}

@Test
fun `recomputeCaseResult leaves a manually overridden case untouched`() {
    val case = RunCase(caseId = 1,
        stepResults = listOf(StepResult(action = "a", verdict = StepVerdict.PASSED)),
        result = RunResult.BLOCKED, manualResult = true)
    assertEquals(case, TestRunSupport.recomputeCaseResult(case))
}

@Test
fun `overrideCaseResult sets result and marks manual`() {
    val case = RunCase(caseId = 1, result = RunResult.PASSED)
    val overridden = TestRunSupport.overrideCaseResult(case, RunResult.BLOCKED)
    assertEquals(RunResult.BLOCKED, overridden.result)
    assertEquals(true, overridden.manualResult)
}

@Test
fun `clearCaseOverride re-derives from steps`() {
    val case = RunCase(caseId = 1,
        stepResults = listOf(StepResult(action = "a", verdict = StepVerdict.FAILED)),
        result = RunResult.PASSED, manualResult = true)
    val cleared = TestRunSupport.clearCaseOverride(case)
    assertEquals(RunResult.FAILED, cleared.result)
    assertEquals(false, cleared.manualResult)
}
```

- [ ] **Step 2: Run to verify they fail.** Run: `./gradlew test --tests "*TestRunSupportTest*" --rerun-tasks 2>&1 | grep -E "FAILED|BUILD|^e:"`. Expected: unresolved references.

- [ ] **Step 3: Implement the helpers** in `TestRunSupport`:

```kotlin
/** Re-derive a case's result from its steps unless it was manually overridden. */
fun recomputeCaseResult(case: RunCase): RunCase =
    if (case.manualResult) case else case.copy(result = deriveRunResult(case.stepResults))

/** Force a case's result and mark it manual. */
fun overrideCaseResult(case: RunCase, result: RunResult): RunCase =
    case.copy(result = result, manualResult = true)

/** Drop a manual override and re-derive from steps. */
fun clearCaseOverride(case: RunCase): RunCase =
    case.copy(result = deriveRunResult(case.stepResults), manualResult = false)
```

Also update `buildRunCase` (Plan 1) to set `result = deriveRunResult(stepResults)` and `manualResult = false` (a freshly imported case is never manual). It already derives the result; just confirm `manualResult` defaults false.

- [ ] **Step 4: Run to verify they pass.** Run: `./gradlew test --tests "*TestRunSupportTest*" --rerun-tasks 2>&1 | grep -E "FAILED|BUILD|^e:"`. Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/io/github/barsia/speqa/run/TestRunSupport.kt src/test/kotlin/io/github/barsia/speqa/run/TestRunSupportTest.kt
git commit -m "feat: per-case result recompute and manual-override helpers"
```

---

## Task 6: Section reorder model helper

**Files:**
- Modify: `src/main/kotlin/io/github/barsia/speqa/run/TestRunSupport.kt`
- Test: `src/test/kotlin/io/github/barsia/speqa/run/TestRunSupportTest.kt`

Context: the section drag-and-drop UI (Task 10) computes a `(from, to)` move via the existing `DragDropIndexMath`; the actual list mutation lives here so it is unit-tested independently of Swing.

- [ ] **Step 1: Write the failing test**

```kotlin
@Test
fun `moveCase reorders the cases list`() {
    val a = RunCase(caseId = 1); val b = RunCase(caseId = 2); val c = RunCase(caseId = 3)
    val run = TestRun(cases = listOf(a, b, c))
    assertEquals(listOf(2, 3, 1), TestRunSupport.moveCase(run, fromIndex = 0, toIndex = 2).cases.map { it.caseId })
    assertEquals(listOf(3, 1, 2), TestRunSupport.moveCase(run, fromIndex = 2, toIndex = 0).cases.map { it.caseId })
    assertEquals(listOf(1, 2, 3), TestRunSupport.moveCase(run, fromIndex = 1, toIndex = 1).cases.map { it.caseId })
}
```

- [ ] **Step 2: Run to verify it fails.** Run: `./gradlew test --tests "*TestRunSupportTest*" --rerun-tasks 2>&1 | grep -E "FAILED|BUILD|^e:"`.

- [ ] **Step 3: Implement**

```kotlin
fun moveCase(run: TestRun, fromIndex: Int, toIndex: Int): TestRun {
    if (fromIndex == toIndex || fromIndex !in run.cases.indices || toIndex !in run.cases.indices) return run
    val next = run.cases.toMutableList()
    next.add(toIndex, next.removeAt(fromIndex))
    return run.copy(cases = next)
}
```

- [ ] **Step 4: Run to verify it passes.** Run: `./gradlew test --tests "*TestRunSupportTest*" --rerun-tasks 2>&1 | grep -E "FAILED|BUILD"`.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/io/github/barsia/speqa/run/TestRunSupport.kt src/test/kotlin/io/github/barsia/speqa/run/TestRunSupportTest.kt
git commit -m "feat: moveCase reorders run cases"
```

> **Milestone:** Tasks 1-6 leave single-case run editing correct (full re-serialize) and the data/result/reorder logic ready. Multi-case runs cannot yet be created from any UI (Plan 3), so the UI tasks below only affect runs once Plan 3 ships, but they are exercised by tests now.

---

## Task 7: Extract a reusable per-case render unit (`RunCaseSection`)

**Files:**
- Create: `src/main/kotlin/io/github/barsia/speqa/editor/ui/run/RunCaseSection.kt`
- Modify: `src/main/kotlin/io/github/barsia/speqa/editor/ui/run/RunCaseSectionState.kt` (create, pure logic)
- Test: `src/test/kotlin/io/github/barsia/speqa/editor/ui/run/RunCaseSectionStateTest.kt`

Goal: a Swing component rendering ONE `RunCase` (collapsible header with `TC-<id> <title>`, per-case result pill, collapse toggle, drag handle; body = editable priority/tags/environment, description/preconditions, links/attachments, and a `StepsSection`). It exposes `fun update(case: RunCase)` and emits changes via `onCaseChange: (RunCase) -> Unit`. Pixel layout mirrors the existing rows in `TestCasePanel` (priority/status row, env/tag row, links/attachments row, captioned description/preconditions sections, `StepsSection`); reuse the SAME sub-builders by extracting them, do not duplicate. Follow `SpeqaThemeColors`, `speqaIconButton`, `handCursor`, and `SpeqaBundle` conventions. New user-visible strings (section collapse tooltip, per-case result label) go in `SpeqaBundle.properties`.

Because Swing rendering is hard to unit-test, put the COLLAPSE and HEADER-LABEL decisions in a pure `RunCaseSectionState` object and test that; the component delegates to it.

- [ ] **Step 1: Write the failing test** for the pure state:

```kotlin
package io.github.barsia.speqa.editor.ui.run

import io.github.barsia.speqa.model.Priority
import io.github.barsia.speqa.model.RunCase
import io.github.barsia.speqa.model.RunResult
import org.junit.Assert.assertEquals
import org.junit.Test

class RunCaseSectionStateTest {
    @Test
    fun `header label shows TC id and title`() {
        assertEquals("TC-5 Sign in",
            RunCaseSectionState.headerLabel(RunCase(caseId = 5, title = "Sign in")))
    }

    @Test
    fun `header label falls back to TC id when title blank`() {
        assertEquals("TC-8", RunCaseSectionState.headerLabel(RunCase(caseId = 8, title = "")))
    }

    @Test
    fun `result badge text uses the case result label`() {
        assertEquals(RunResult.BLOCKED.label,
            RunCaseSectionState.resultBadge(RunCase(caseId = 1, result = RunResult.BLOCKED)))
    }
}
```

- [ ] **Step 2: Run to verify it fails.** Run: `./gradlew test --tests "*RunCaseSectionStateTest*" --rerun-tasks 2>&1 | grep -E "FAILED|BUILD|^e:"`.

- [ ] **Step 3: Implement `RunCaseSectionState`** (pure):

```kotlin
package io.github.barsia.speqa.editor.ui.run

import io.github.barsia.speqa.model.RunCase

object RunCaseSectionState {
    fun headerLabel(case: RunCase): String =
        if (case.title.isBlank()) "TC-${case.caseId}" else "TC-${case.caseId} ${case.title}"

    fun resultBadge(case: RunCase): String = case.result.label
}
```

- [ ] **Step 4: Extract the shared per-case sub-builders.** In `TestCasePanel.kt`, identify the row builders used in RUN mode (priority/status row, env/tag row, links/attachments row, captioned description/preconditions, `StepsSection` construction). Extract them into reusable units (either small builder functions in a new `editor/ui/run/RunCaseBody.kt`, or constructor-injected components) so both `TestCasePanel`'s flat single-case view and `RunCaseSection` use ONE implementation. Keep behavior identical; this is a refactor with no functional change. Report exactly what was extracted.

- [ ] **Step 5: Implement `RunCaseSection`** as a `JPanel` composing: a header row (collapse toggle via `speqaIconButton`, `RunCaseSectionState.headerLabel`, a result pill, a drag handle icon) + a collapsible body using the extracted sub-builders bound to the case. Wire each editable sub-control to produce an updated `RunCase` and call `onCaseChange`. The result pill click opens the run-result chooser (reuse the existing `runResultCombo` chooser pattern) and calls `onCaseChange(TestRunSupport.overrideCaseResult(case, picked))`; a "clear override" affordance calls `onCaseChange(TestRunSupport.clearCaseOverride(case))`. Step verdict/comment changes call `onCaseChange(TestRunSupport.recomputeCaseResult(case.copy(stepResults = ...)))`.

- [ ] **Step 6: Verify compile + state test.** Run: `./gradlew compileKotlin compileTestKotlin test --tests "*RunCaseSectionStateTest*" --tests "*TestCase*" --rerun-tasks 2>&1 | grep -E "FAILED|BUILD|^e:"`. Expected: BUILD SUCCESSFUL (the single-case flat view still works after the extraction).

- [ ] **Step 7: Commit**

```bash
git add src/main/kotlin/io/github/barsia/speqa/editor/ui/run/ src/test/kotlin/io/github/barsia/speqa/editor/ui/run/ src/main/kotlin/io/github/barsia/speqa/editor/ui/TestCasePanel.kt src/main/resources/messages/SpeqaBundle.properties
git commit -m "feat: reusable RunCaseSection component and shared per-case body builders"
```

---

## Task 8: Multi-case container with collapsible sections (single-case stays flat)

**Files:**
- Create: `src/main/kotlin/io/github/barsia/speqa/editor/ui/run/RunCasesContainer.kt`
- Modify: `src/main/kotlin/io/github/barsia/speqa/run/TestRunEditor.kt` (or `TestCasePanel` RUN body) to choose flat vs sectioned by `cases.size`
- Test: `src/test/kotlin/io/github/barsia/speqa/editor/ui/run/RunCasesLayoutDecisionTest.kt`

Goal: when `run.cases.size <= 1`, render today's flat view (the lone case via the extracted body builders, no section chrome). When `>= 2`, render a `RunCasesContainer` = a vertical list of `RunCaseSection` panels, default expanded, collapse state held transiently in the container (not serialized). Editing a section updates `cases[index]`, recomputes the aggregate, and emits the whole `TestRun` via `onRunChange` (full re-serialize).

- [ ] **Step 1: Write the failing test** for the pure layout decision:

```kotlin
package io.github.barsia.speqa.editor.ui.run

import io.github.barsia.speqa.model.RunCase
import io.github.barsia.speqa.model.TestRun
import org.junit.Assert.assertEquals
import org.junit.Test

class RunCasesLayoutDecisionTest {
    @Test
    fun `single case renders flat`() {
        assertEquals(RunLayout.FLAT, RunCasesContainer.layoutFor(TestRun(cases = listOf(RunCase(caseId = 1)))))
        assertEquals(RunLayout.FLAT, RunCasesContainer.layoutFor(TestRun(cases = emptyList())))
    }
    @Test
    fun `multiple cases render sectioned`() {
        assertEquals(RunLayout.SECTIONED,
            RunCasesContainer.layoutFor(TestRun(cases = listOf(RunCase(caseId = 1), RunCase(caseId = 2)))))
    }
}
```

- [ ] **Step 2: Run to verify it fails.** Run: `./gradlew test --tests "*RunCasesLayoutDecisionTest*" --rerun-tasks 2>&1 | grep -E "FAILED|BUILD|^e:"`.

- [ ] **Step 3: Implement** `enum class RunLayout { FLAT, SECTIONED }` and `RunCasesContainer.layoutFor(run) = if (run.cases.size >= 2) SECTIONED else FLAT`, plus the container component: builds one `RunCaseSection` per case with `onCaseChange = { updated -> emit(run.copy(cases = run.cases.mapIndexed { i, c -> if (i == index) updated else c })) }`. The container exposes `fun update(run: TestRun)` (rebuild/refresh sections, preserving collapse state by case index). Collapse state is a `BooleanArray`/`MutableList<Boolean>` keyed by index, default all expanded.

- [ ] **Step 4: Wire into the editor.** In `TestRunEditor`/`TestCasePanel` RUN mode, switch between the flat body and `RunCasesContainer` based on `RunCasesContainer.layoutFor(current)` whenever the run is (re)loaded in `refreshFromDocument`/`updateFromRun`. Both paths emit through `onRunChange`.

- [ ] **Step 5: Verify compile + tests + a manual sandbox note.** Run: `./gradlew compileKotlin compileTestKotlin test --tests "*RunCasesLayoutDecisionTest*" --rerun-tasks 2>&1 | grep -E "FAILED|BUILD|^e:"`. Report that a 2-case run must be checked in the sandbox once Plan 3 can create one (or via a hand-written 2-case `.tr.md`).

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/io/github/barsia/speqa/editor/ui/run/ src/test/kotlin/io/github/barsia/speqa/editor/ui/run/ src/main/kotlin/io/github/barsia/speqa/run/TestRunEditor.kt
git commit -m "feat: sectioned multi-case run layout with flat single-case fallback"
```

---

## Task 9: Header aggregate progress

**Files:**
- Modify: `src/main/kotlin/io/github/barsia/speqa/editor/ui/TestCasePanel.kt` (RUN header) or the run header component
- Create: `src/main/kotlin/io/github/barsia/speqa/run/RunProgressText.kt` (pure) if no equivalent exists; a `RunProgressTextTest.kt` already exists in the run test dir, so check for an existing `RunProgressText`/progress helper first and extend it
- Test: `src/test/kotlin/io/github/barsia/speqa/run/RunProgressTextTest.kt`

Goal: the run header shows "N / M cases done" and the aggregated result. "Done" = cases whose result is not `NOT_STARTED` and not `IN_PROGRESS`. Reuse `TestRunSupport.aggregateResult(cases)` for the overall result; keep the existing run-level manual override.

- [x] **Step 1: Check for an existing progress helper.** Run: `grep -rn "RunProgressText\|cases done\|progressLabel\|fun progress" src/main/kotlin src/test/kotlin`. If a `RunProgressText` helper exists (a `RunProgressTextTest` is present), extend it with a multi-case aggregate function; otherwise create `RunProgressText`.

- [x] **Step 2: Write the failing test**

```kotlin
@Test
fun `caseProgress counts cases with a terminal result`() {
    val cases = listOf(
        RunCase(caseId = 1, result = RunResult.PASSED),
        RunCase(caseId = 2, result = RunResult.FAILED),
        RunCase(caseId = 3, result = RunResult.NOT_STARTED),
        RunCase(caseId = 4, result = RunResult.IN_PROGRESS),
    )
    assertEquals("2 / 4 cases done", RunProgressText.caseProgress(cases))
}
```

(Use the exact string the bundle will render; if localized, assert the formatter output with a fixed locale or assert the count pair `Pair(2, 4)` and format in the component.)

- [x] **Step 3: Run to verify it fails.** Run: `./gradlew test --tests "*RunProgressTextTest*" --rerun-tasks 2>&1 | grep -E "FAILED|BUILD|^e:"`.

- [x] **Step 4: Implement** `RunProgressText.caseProgress(cases)`: `val done = cases.count { it.result != RunResult.NOT_STARTED && it.result != RunResult.IN_PROGRESS }` then format `"$done / ${cases.size} cases done"` (string from `SpeqaBundle`). Bind the run header `progressLabel` (RUN mode, `TestCasePanel.kt:211`) to it and the result pill to `aggregateResult(cases)`.

- [x] **Step 5: Run to verify it passes + commit.**

```bash
git add src/main/kotlin/io/github/barsia/speqa/ src/test/kotlin/io/github/barsia/speqa/run/RunProgressTextTest.kt src/main/resources/messages/SpeqaBundle.properties
git commit -m "feat: aggregate case-progress text in the run header"
```

---

## Task 10: Whole-section drag-and-drop reordering

**Files:**
- Modify: `src/main/kotlin/io/github/barsia/speqa/editor/ui/run/RunCasesContainer.kt`
- Reuse: `editor/ui/steps/DragReorderSupport.kt`, `LivePreviewReorderDecorator.kt`, `DragDropIndexMath.kt`
- Test: `src/test/kotlin/io/github/barsia/speqa/editor/ui/run/RunSectionReorderTest.kt`

Goal: a drag handle on each section header reorders whole sections (only when `cases.size >= 2`). The drag uses the same primitives as step DnD; on drop, compute `(from, to)` via `DragDropIndexMath` and emit `TestRunSupport.moveCase(run, from, to)` through `onRunChange`.

- [ ] **Step 1: Study the step DnD wiring.** Read `StepsSection.kt`'s use of `DragReorderSupport`/`LivePreviewReorderDecorator`/`DragDropIndexMath` and mirror it at the section level. Report the exact API you will reuse.

- [ ] **Step 2: Write the failing test** (drop-index math + model move, no Swing):

```kotlin
@Test
fun `dropping a section emits a moveCase with the computed indices`() {
    val run = TestRun(cases = listOf(RunCase(caseId = 1), RunCase(caseId = 2), RunCase(caseId = 3)))
    // dragging index 0 below index 2 -> DragDropIndexMath yields to=2
    val moved = TestRunSupport.moveCase(run, fromIndex = 0, toIndex = DragDropIndexMath.resolveDropIndex(/* args mirroring step usage */))
    assertEquals(listOf(2, 3, 1), moved.cases.map { it.caseId })
}
```

(Adjust the `DragDropIndexMath` call to its real signature discovered in Step 1; the assertion pins the reorder result.)

- [ ] **Step 3: Run to verify it fails, then implement** the drag handle + `DragReorderSupport` on the section list in `RunCasesContainer`, calling `moveCase` on drop and emitting via `onRunChange`. Hide the handle when `cases.size < 2`.

- [ ] **Step 4: Verify compile + tests.** Run: `./gradlew compileKotlin compileTestKotlin test --tests "*RunSectionReorder*" --tests "*RunCases*" --rerun-tasks 2>&1 | grep -E "FAILED|BUILD|^e:"`.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/io/github/barsia/speqa/editor/ui/run/ src/test/kotlin/io/github/barsia/speqa/editor/ui/run/
git commit -m "feat: drag-and-drop reordering of whole run case sections"
```

---

## Task 11: Per-case result pill + manual-override UI polish

**Files:**
- Modify: `src/main/kotlin/io/github/barsia/speqa/editor/ui/run/RunCaseSection.kt`
- Modify: `src/main/resources/messages/SpeqaBundle.properties`
- Test: `src/test/kotlin/io/github/barsia/speqa/editor/ui/run/RunCaseSectionStateTest.kt`

Goal: finalize the per-case result pill: shows the effective result; clicking chooses a verdict (override -> `overrideCaseResult`); an "auto" affordance clears the override (`clearCaseOverride`); a manual override is visually marked (e.g. a small "manual" hint). Add a pure state helper for the manual hint so it is testable.

- [ ] **Step 1: Write the failing test**

```kotlin
@Test
fun `manual hint shown only when overridden`() {
    assertEquals(true, RunCaseSectionState.isManual(RunCase(caseId = 1, result = RunResult.BLOCKED, manualResult = true)))
    assertEquals(false, RunCaseSectionState.isManual(RunCase(caseId = 1, result = RunResult.PASSED)))
}
```

- [ ] **Step 2-4: Run (fail) -> implement `RunCaseSectionState.isManual(case) = case.manualResult` and wire the pill/override/clear controls and the manual hint label -> run (pass).** Use `speqaIconButton`/`handCursor` and bundle strings; no hardcoded UI text; colors from `SpeqaThemeColors`.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/io/github/barsia/speqa/editor/ui/run/ src/test/kotlin/io/github/barsia/speqa/editor/ui/run/ src/main/resources/messages/SpeqaBundle.properties
git commit -m "feat: per-case result override and manual-result indicator in run sections"
```

---

## Task 12: Spec update + full regression

**Files:**
- Modify: `docs/specs/2026-04-06-speqa-design.md` (run editor + `.tr.md` sectioned format with `manual_result`)
- Verify: whole suite

- [ ] **Step 1: Update the spec.** Document the current run editor behavior: sectioned multi-case rendering (collapsible, drag-and-drop, per-case result with manual override, aggregate header), flat single-case rendering, full-reserialize save model (no surgical patching for runs), and the `.tr.md` section format including the per-section `manual_result:` line. Keep it a description of the current state, not a changelog. Use regular hyphens (no em dash). NOTE: this file may have unrelated pre-existing working-tree edits; coordinate with the user before staging it, or stage only your additions.

- [ ] **Step 2: Full build + regression.** Run: `./gradlew compileKotlin compileTestKotlin test --rerun-tasks 2>&1 | grep -E "FAILED|BUILD|^e:"`. Expected: BUILD SUCCESSFUL, 0 failures.

- [ ] **Step 3: Sandbox smoke (manual).** Open a single-case `.tr.md` (flat view) and a hand-written 2-case `.tr.md` (sectioned view); verify: edit tags/priority/description on each, toggle a step verdict, override a case result then clear it, collapse/expand and reorder sections; confirm every change survives reload (full re-serialize).

- [ ] **Step 4: Commit + remove the Plan 1 release-blocker note** (it is now resolved). Edit `docs/plans/2026-06-26-multi-case-run-format-plan.md` to drop the RELEASE BLOCKER paragraph (or mark it resolved by Plan 2), and update the memory note in a follow-up if desired.

```bash
git add -A
git commit -m "docs: spec the sectioned run editor; resolve Plan 1 release blocker"
```

---

## Self-review notes

- **Spec coverage:** full re-serialize / blocker fix (Tasks 1-2), per-case manual result model+format (Tasks 3-4), result/override + reorder logic (Tasks 5-6), reusable section component (Task 7), sectioned-vs-flat layout (Task 8), aggregate header (Task 9), section DnD (Task 10), result-pill UI (Task 11), spec + regression (Task 12).
- **Closes Plan 1 release blocker** in Task 1 (the earliest task), independent of the UI work.
- **YAGNI:** no per-section comment, no persisted collapse state, no section add/remove inside an existing run (still out of scope; that is a later possibility per the design doc).
- **Compat:** single-case runs render flat and serialize as one section exactly as in Plan 1; legacy marker-less files keep parsing via `parseLegacyCase` (no `manual_result`).
- **Accepted duplication (Task 7):** the per-case body for multi-case sections lives in `editor/ui/run/RunCaseBody.kt` (reuses the real widgets: `PriorityComboBox`/`TagCloud`/`LinkList`/`AttachmentList`/`EditableBodyBlockSection`/`StepsSection`), while the flat single-case run body stays in `TestCasePanel` to preserve today's look and avoid a risky refactor of the CASE/RUN-shared panel. The only genuinely duplicated logic is a ~40-line `RunCase`<->`TestStep` step-uid bridge (verdict/comment survive reorder via uid). Acceptable trade-off for now; unify the run-side rendering later only if it drifts.

## Not in this plan
- Plan 3: Test Runs tab `+ Create test run` entry and the adaptive Create Test Run dialog that produces multi-case runs (`createMultiCaseRun`).
- Adding/removing case sections within an existing run after creation.
