# Multi-Case Test Run Format Implementation Plan (Plan 1 of 3)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extend the test-run data layer so one `.tr.md` holds an ordered list of per-test-case sections (each with its own metadata, steps, and per-case result), while keeping existing single-case runs working unchanged.

**Architecture:** Introduce a `RunCase` section type. `TestRun` gains `cases: List<RunCase>`. The serializer writes a `Test Case: TC-<id> <title>` marker per section; the parser reads multiple sections and treats a marker-less legacy file as one implicit section. The editor and patcher keep compiling/working via a single-case compatibility path; full multi-section editor/patcher is Plan 2.

**Tech Stack:** Kotlin, IntelliJ Platform plugin, JUnit 4. Existing parser/serializer are `object`s using `buildString` and line-scanning regexes.

**Design references:** `docs/plans/2026-06-26-test-run-from-test-cases-design.md`. Current single-case format and code map are in that design plus the model files listed per task.

---

## File format (multi-case `.tr.md`)

```
---
id: 12
title: "High - 2026-06-26 14:30"
runner: "alice"
started_at: "2026-06-26T14:30:00"
finished_at: "2026-06-26T15:00:00"
result: failed
manual_result: true
---

Test Case: TC-5 Sign in with valid creds
priority: high
tags: smoke, auth
environment: chrome

Description paragraph.

Preconditions:

User is logged out.

Scenario:

1. Open login page
   > Login page visible
   - passed

Result: passed

Test Case: TC-8 Sign in with wrong password
priority: normal

Scenario:

1. Enter wrong password
   > Error shown
   - failed

Result: failed
```

Rules:
- Run frontmatter keeps only run-level fields: `id, title, runner, started_at, finished_at, result, manual_result`. Run-level `tags/priority/environment` are gone (they live per case).
- A section starts at a bare `Test Case: TC-<id> <title>` line (id required). It runs until the next `Test Case:` line or EOF.
- Per-case metadata lines, each optional, directly under the marker: `priority: <label>`, `tags: a, b`, `environment: x, y`. Comma-separated, single line, mirroring the existing step `Ticket:`/`Links:` style.
- Inside a section: optional body blocks (description / `Preconditions:`), optional `Links:` / `Attachments:`, then `Scenario:` with the existing step format (unchanged), then a `Result: <verdict>` line for the per-case result (omitted when NOT_STARTED).
- Legacy compatibility: a file with NO `Test Case:` marker is parsed as a single implicit `RunCase` using today's top-level parse (frontmatter `tags/priority/environment` become that one case's metadata).
- Run-level `result` is the aggregate: any FAILED -> FAILED; else any BLOCKED -> BLOCKED; else any IN_PROGRESS or mixed started/not-started -> IN_PROGRESS; else all PASSED -> PASSED; else NOT_STARTED.

---

## Task 1: Add the `RunCase` model and `cases` to `TestRun` (with single-case compat)

**Files:**
- Modify: `src/main/kotlin/io/github/barsia/speqa/model/TestRun.kt`
- Test: `src/test/kotlin/io/github/barsia/speqa/model/TestRunTest.kt`

- [ ] **Step 1: Write the failing test**

Add to `TestRunTest.kt`:

```kotlin
@Test
fun `single-case run exposes its one case via cases`() {
    val step = StepResult(action = "do", verdict = StepVerdict.PASSED)
    val case = RunCase(
        caseId = 5,
        title = "Login",
        priority = Priority.HIGH,
        tags = listOf("smoke"),
        environment = listOf("chrome"),
        stepResults = listOf(step),
        result = RunResult.PASSED,
    )
    val run = TestRun(id = 12, title = "High", cases = listOf(case))

    assertEquals(1, run.cases.size)
    assertEquals(5, run.cases.first().caseId)
    assertEquals(listOf(step), run.cases.first().stepResults)
    // Compat accessor still flattens to all steps:
    assertEquals(listOf(step), run.stepResults)
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "*TestRunTest*" 2>&1 | grep -E "FAILED|BUILD"`
Expected: compile failure (`RunCase` unresolved, `cases` unresolved).

- [ ] **Step 3: Add `RunCase` and `cases`, keep old fields as compat**

In `TestRun.kt`, add the section type and extend `TestRun`. Keep the existing flat fields as **compat properties** derived from `cases` so existing editor/patcher code keeps compiling. Replace the `TestRun` data class with:

```kotlin
data class RunCase(
    val caseId: Int,
    val title: String = "",
    val priority: Priority? = null,
    val tags: List<String> = emptyList(),
    val environment: List<String> = emptyList(),
    val bodyBlocks: List<TestCaseBodyBlock> = emptyList(),
    val links: List<Link> = emptyList(),
    val attachments: List<Attachment> = emptyList(),
    val stepResults: List<StepResult> = emptyList(),
    val result: RunResult = RunResult.NOT_STARTED,
)

data class TestRun(
    val id: Int? = null,
    val title: String = "",
    val runner: String = "",
    val startedAt: LocalDateTime? = null,
    val finishedAt: LocalDateTime? = null,
    val result: RunResult = RunResult.NOT_STARTED,
    val manualResult: Boolean = false,
    val cases: List<RunCase> = emptyList(),
    val comment: String = "",
) {
    /** Single-case compatibility view: the lone case, or null when not exactly one. */
    val singleCase: RunCase? get() = cases.singleOrNull()

    // Compat accessors used by the existing single-case editor/patcher (Plan 2 migrates them).
    val tags: List<String> get() = singleCase?.tags ?: emptyList()
    val priority: Priority? get() = singleCase?.priority
    val environment: List<String> get() = singleCase?.environment ?: emptyList()
    val bodyBlocks: List<TestCaseBodyBlock> get() = singleCase?.bodyBlocks ?: emptyList()
    val links: List<Link> get() = singleCase?.links ?: emptyList()
    val attachments: List<Attachment> get() = singleCase?.attachments ?: emptyList()
    val stepResults: List<StepResult> get() = cases.flatMap { it.stepResults }
}
```

(Keep `enum StepVerdict`, `enum RunResult`, and `data class StepResult` exactly as they are.)

- [ ] **Step 4: Update existing call sites that CONSTRUCT a TestRun with flat fields**

The old code builds `TestRun(... stepResults = ..., tags = ..., priority = ..., environment = ..., bodyBlocks = ..., links = ..., attachments = ...)`. Those named args no longer exist on the constructor. Find them:

Run: `grep -rn "TestRun(" src/main/kotlin src/test/kotlin | grep -vE "RunCase|fun |class "`

For each construction that passed flat fields, wrap them into a single `RunCase`. Example transform (in `TestRunSupport.createInitialRun`, handled fully in Task 5 - here just make existing ones compile):

```kotlin
// before: TestRun(id = id, title = t, tags = tg, priority = p, environment = e,
//                 bodyBlocks = b, stepResults = s, result = r, runner = ru)
TestRun(
    id = id, title = t, runner = ru, result = r,
    cases = listOf(RunCase(caseId = id ?: 0, title = t, priority = p, tags = tg,
        environment = e, bodyBlocks = b, stepResults = s, result = r)),
)
```

- [ ] **Step 5: Run the test and the whole module to verify it compiles and passes**

Run: `./gradlew compileKotlin compileTestKotlin test --tests "*TestRunTest*" 2>&1 | grep -E "FAILED|BUILD|^e:"`
Expected: BUILD SUCCESSFUL, the new test passes.

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/io/github/barsia/speqa/model/TestRun.kt src/test/kotlin/io/github/barsia/speqa/model/TestRunTest.kt
git commit -m "feat: add RunCase sections to TestRun with single-case compat"
```

---

## Task 2: Serialize a single section (one-case run round-trips like before)

**Files:**
- Modify: `src/main/kotlin/io/github/barsia/speqa/parser/TestRunSerializer.kt`
- Test: `src/test/kotlin/io/github/barsia/speqa/parser/TestRunSerializerTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
@Test
fun `serializes one case section with marker, metadata and result`() {
    val run = TestRun(
        id = 7, title = "Login test", runner = "alice", result = RunResult.PASSED,
        cases = listOf(RunCase(
            caseId = 5, title = "Sign in", priority = Priority.HIGH,
            tags = listOf("smoke", "auth"), environment = listOf("chrome"),
            stepResults = listOf(StepResult(action = "Open page", expected = "Visible",
                verdict = StepVerdict.PASSED)),
            result = RunResult.PASSED,
        )),
    )
    val out = TestRunSerializer.serialize(run)

    assertTrue(out.contains("Test Case: TC-5 Sign in"))
    assertTrue(out.contains("priority: high"))
    assertTrue(out.contains("tags: smoke, auth"))
    assertTrue(out.contains("environment: chrome"))
    assertTrue(out.contains("Scenario:"))
    assertTrue(out.contains("1. Open page"))
    assertTrue(out.contains("- passed"))
    assertTrue(out.contains("Result: passed"))
    // run-level tags/priority must NOT be in frontmatter anymore
    assertFalse(out.substringBefore("Test Case:").contains("tags:"))
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "*TestRunSerializerTest*" 2>&1 | grep -E "FAILED|BUILD"`
Expected: FAIL (no `Test Case:` marker, no `Result:` line).

- [ ] **Step 3: Rewrite `serialize` to emit run frontmatter + one section per case**

In `TestRunSerializer.serialize`, change the body to: (a) write run-level frontmatter only (`id, title, runner, started_at, finished_at, result, manual_result` - drop tags/priority/environment from frontmatter), then (b) for each `case` in `run.cases`, call a new `StringBuilder.appendCaseSection(case)`. Keep the trailing run `comment` write as today (one blank line then the text).

```kotlin
fun serialize(testRun: TestRun): String = buildString {
    appendRunFrontmatter(testRun)                    // existing frontmatter writer, minus tags/priority/env
    testRun.cases.forEach { appendCaseSection(it) }
    appendOverallComment(testRun.comment)            // existing trailing-comment logic
}.trimEnd() + "\n"

private fun StringBuilder.appendCaseSection(case: RunCase) {
    append("\nTest Case: TC-").append(case.caseId)
    if (case.title.isNotBlank()) append(' ').append(case.title)
    append('\n')
    case.priority?.let { append("priority: ").append(it.label).append('\n') }
    if (case.tags.isNotEmpty()) append("tags: ").append(case.tags.joinToString(", ")).append('\n')
    if (case.environment.isNotEmpty()) append("environment: ").append(case.environment.joinToString(", ")).append('\n')
    appendCaseBodyBlocks(case.bodyBlocks)            // reuse existing body-block writer (DescriptionBlock=0, PreconditionsBlock=1)
    appendCaseLinks(case.links)                      // reuse existing `Links:` writer
    appendCaseAttachments(case.attachments)          // reuse existing `Attachments:` writer
    append("\nScenario:\n\n")
    case.stepResults.forEachIndexed { i, step -> appendStepResult(i + 1, step) }   // EXISTING per-step writer, unchanged
    if (case.result != RunResult.NOT_STARTED) append("\nResult: ").append(case.result.label).append('\n')
}
```

- [ ] **Step 4: Extract the existing writers into the named helpers**

Extract the existing frontmatter / body-block / links / attachments / step writers from the current `serialize` into the helper functions named above (`appendRunFrontmatter`, `appendCaseBodyBlocks`, `appendCaseLinks`, `appendCaseAttachments`, `appendOverallComment`), keeping their bodies byte-for-byte identical to today so the per-step format is unchanged. Remove `tags`/`priority`/`environment` writes from `appendRunFrontmatter` (they are per-case now). `appendStepResult` already exists - reuse as-is.

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew test --tests "*TestRunSerializerTest*" 2>&1 | grep -E "FAILED|BUILD"`
Expected: the new test passes. Existing serializer tests that asserted run-level `tags:` in frontmatter will now fail - update them in Task 4.

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/io/github/barsia/speqa/parser/TestRunSerializer.kt src/test/kotlin/io/github/barsia/speqa/parser/TestRunSerializerTest.kt
git commit -m "feat: serialize run as Test Case sections with per-case metadata and result"
```

---

## Task 3: Parse sections (multi-case) and legacy marker-less files

**Files:**
- Modify: `src/main/kotlin/io/github/barsia/speqa/parser/TestRunParser.kt`
- Test: `src/test/kotlin/io/github/barsia/speqa/parser/TestRunParserTest.kt`

- [ ] **Step 1: Write the failing tests**

```kotlin
@Test
fun `parses two case sections with metadata and per-case result`() {
    val text = """
        ---
        id: 7
        title: "Run"
        result: failed
        ---

        Test Case: TC-5 Sign in
        priority: high
        tags: smoke, auth

        Scenario:

        1. Open page
           > Visible
           - passed

        Result: passed

        Test Case: TC-8 Wrong password

        Scenario:

        1. Enter wrong password
           - failed

        Result: failed
    """.trimIndent()

    val run = TestRunParser.parse(text)

    assertEquals(2, run.cases.size)
    assertEquals(5, run.cases[0].caseId)
    assertEquals("Sign in", run.cases[0].title)
    assertEquals(Priority.HIGH, run.cases[0].priority)
    assertEquals(listOf("smoke", "auth"), run.cases[0].tags)
    assertEquals(RunResult.PASSED, run.cases[0].result)
    assertEquals(1, run.cases[0].stepResults.size)
    assertEquals(StepVerdict.PASSED, run.cases[0].stepResults[0].verdict)
    assertEquals(8, run.cases[1].caseId)
    assertEquals(RunResult.FAILED, run.cases[1].result)
}

@Test
fun `legacy marker-less file parses as one implicit case`() {
    val text = """
        ---
        id: 3
        title: "Legacy"
        priority: normal
        tags: smoke
        result: passed
        ---

        Scenario:

        1. Do thing
           - passed
    """.trimIndent()

    val run = TestRunParser.parse(text)

    assertEquals(1, run.cases.size)
    assertEquals(3, run.cases[0].caseId)            // falls back to run id
    assertEquals(Priority.NORMAL, run.cases[0].priority)
    assertEquals(listOf("smoke"), run.cases[0].tags)
    assertEquals(1, run.cases[0].stepResults.size)
}
```

- [ ] **Step 2: Run to verify they fail**

Run: `./gradlew test --tests "*TestRunParserTest*" 2>&1 | grep -E "FAILED|BUILD"`
Expected: FAIL (`run.cases` empty / size mismatch).

- [ ] **Step 3: Split the body into sections, then parse each with the existing helpers**

Add a `CASE_MARKER = Regex("^Test Case:\\s+TC-(\\d+)(?:\\s+(.*))?$")` to the regex block. In `parse`, after splitting frontmatter, split the body into sections at each `CASE_MARKER` line:

```kotlin
fun parse(content: String): TestRun {
    val normalized = content.replace("\r\n", "\n")
    val (frontmatter, body) = SpeqaMarkdown.splitFrontmatter(normalized)
    val fm = SpeqaMarkdown.parseYamlMap(frontmatter)

    val sections = splitIntoCaseSections(body)        // new: list of (caseId, title, sectionBody)
    val cases = if (sections.isEmpty()) {
        listOf(parseLegacyCase(fm, body))             // one implicit case from today's top-level parse
    } else {
        sections.map { parseCaseSection(it) }
    }
    return TestRun(
        id = fm["id"]?.toIntOrNull(),
        title = fm["title"].orEmpty(),
        runner = fm["runner"].orEmpty(),
        startedAt = parseDateTime(fm["started_at"]),
        finishedAt = parseDateTime(fm["finished_at"]),
        result = RunResult.fromString(fm["result"].orEmpty()),
        manualResult = fm["manual_result"]?.toBoolean() ?: false,
        cases = cases,
        comment = parseOverallComment(body),          // existing helper, unchanged
    )
}
```

`splitIntoCaseSections(body)` scans lines; each `CASE_MARKER` opens a new section (capturing id + title); lines until the next marker form that section's body. Returns empty when no marker is present.

`parseCaseSection(section)` parses the per-case metadata lines (`priority:` -> `Priority.fromString`, `tags:`/`environment:` -> comma split + trim), then runs the EXISTING `parseBodyBlocks`, `parseLinks`, `parseAttachments`, `parseStepResults` over the section body (these already expect `Preconditions:`/`Links:`/`Attachments:`/`Scenario:` markers), and reads the trailing `Result: <label>` line via a new `RESULT_PATTERN = Regex("^Result:\\s*(\\w+)$")` -> `RunResult.fromString`, defaulting NOT_STARTED.

`parseLegacyCase(fm, body)` builds a single `RunCase(caseId = fm["id"]?.toIntOrNull() ?: 0, title = fm["title"].orEmpty(), priority = Priority.fromString(fm["priority"].orEmpty()), tags = parseYamlList(fm,"tags"), environment = parseYamlList(fm,"environment"), bodyBlocks = parseBodyBlocks(body), links = parseLinks(body), attachments = parseAttachments(body), stepResults = parseStepResults(body), result = RunResult.fromString(fm["result"].orEmpty()))` - i.e. exactly today's single-case parse, wrapped in one case.

- [ ] **Step 4: Run to verify they pass**

Run: `./gradlew test --tests "*TestRunParserTest*" 2>&1 | grep -E "FAILED|BUILD"`
Expected: both new tests pass.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/io/github/barsia/speqa/parser/TestRunParser.kt src/test/kotlin/io/github/barsia/speqa/parser/TestRunParserTest.kt
git commit -m "feat: parse Test Case sections and legacy single-case runs"
```

---

## Task 4: Round-trip multi-case + fix existing single-case tests

**Files:**
- Test: `src/test/kotlin/io/github/barsia/speqa/parser/TestRunSerializerTest.kt`
- Test: `src/test/kotlin/io/github/barsia/speqa/parser/TestRunParserTest.kt`

- [ ] **Step 1: Write the round-trip test**

```kotlin
@Test
fun `round trip preserves multi-case run`() {
    val original = TestRun(
        id = 12, title = "High", runner = "alice", result = RunResult.FAILED,
        cases = listOf(
            RunCase(caseId = 5, title = "Sign in", priority = Priority.HIGH,
                tags = listOf("smoke"), environment = listOf("chrome"),
                stepResults = listOf(StepResult(action = "Open", expected = "Visible",
                    verdict = StepVerdict.PASSED)), result = RunResult.PASSED),
            RunCase(caseId = 8, title = "Wrong password",
                stepResults = listOf(StepResult(action = "Enter wrong",
                    verdict = StepVerdict.FAILED)), result = RunResult.FAILED),
        ),
    )
    val reparsed = TestRunParser.parse(TestRunSerializer.serialize(original))
    assertEquals(original.cases, reparsed.cases)
}
```

- [ ] **Step 2: Run; expect pass (and existing tests to surface)**

Run: `./gradlew test --tests "*TestRunSerializerTest*" --tests "*TestRunParserTest*" 2>&1 | grep -E "FAILED|BUILD"`
Expected: the round-trip passes; some PRE-EXISTING single-case tests fail because they assert run-level `tags:`/`priority:` in frontmatter or build `TestRun(stepResults = ...)`.

- [ ] **Step 3: Migrate the pre-existing single-case tests to the new model**

For each failing pre-existing test, change `TestRun(... flat fields ...)` to `TestRun(... cases = listOf(RunCase(caseId = <id>, ...)))`, and change assertions that expected run-level `tags:`/`priority:`/`environment:` in frontmatter to expect them under the `Test Case:` section instead. Keep the per-step assertions (`1. action`, `> expected`, `- verdict`, `Comment:`) unchanged - that format did not change.

- [ ] **Step 4: Run the full parser+serializer suites green**

Run: `./gradlew test --tests "*TestRunSerializerTest*" --tests "*TestRunParserTest*" 2>&1 | grep -E "FAILED|BUILD"`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add src/test/kotlin/io/github/barsia/speqa/parser/
git commit -m "test: round-trip multi-case runs; migrate single-case tests to RunCase"
```

---

## Task 5: `createInitialRun` produces a one-section run; add multi-case builder

**Files:**
- Modify: `src/main/kotlin/io/github/barsia/speqa/run/TestRunSupport.kt`
- Test: `src/test/kotlin/io/github/barsia/speqa/run/TestRunSupportTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
@Test
fun `createMultiCaseRun builds one section per test case`() {
    val tcA = sampleTestCase(id = 5, title = "A", steps = 2)   // existing test helper or inline TestCase
    val tcB = sampleTestCase(id = 8, title = "B", steps = 1)
    val run = TestRunSupport.createMultiCaseRun(
        cases = listOf(
            TestRunSupport.SourceCase(tcA, "test-cases/a.tc.md"),
            TestRunSupport.SourceCase(tcB, "test-cases/b.tc.md"),
        ),
        targetDirectoryPath = "test-runs",
        importOptions = RunImportOptions(importTags = true, importEnvironment = true),
        runner = "alice",
    )
    assertEquals(2, run.cases.size)
    assertEquals(5, run.cases[0].caseId)
    assertEquals(2, run.cases[0].stepResults.size)
    assertEquals(RunResult.NOT_STARTED, run.cases[0].result)
    assertEquals(8, run.cases[1].caseId)
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests "*TestRunSupportTest*" 2>&1 | grep -E "FAILED|BUILD"`
Expected: FAIL (`createMultiCaseRun` / `SourceCase` unresolved).

- [ ] **Step 3: Refactor `createInitialRun` to build a `RunCase`, add `createMultiCaseRun`**

Add `data class SourceCase(val testCase: TestCase, val sourceFilePath: String)`. Add `private fun buildRunCase(source: SourceCase, targetDirectoryPath: String, importOptions: RunImportOptions): RunCase` that does what the current `createInitialRun` body does (map `testCase.steps` -> `StepResult` all NONE, gate tags/environment/bodyBlocks/links/attachments by `importOptions`, rebase attachment paths) but returns a `RunCase` (with `caseId = testCase.id ?: 0`, `result = RunResult.NOT_STARTED`). Then:

```kotlin
fun createInitialRun(testCase: TestCase, sourceFilePath: String, targetDirectoryPath: String,
                     importOptions: RunImportOptions = RunImportOptions(), runner: String = defaultRunner()): TestRun {
    val case = buildRunCase(SourceCase(testCase, sourceFilePath), targetDirectoryPath, importOptions)
    return TestRun(id = null, title = testCase.title, runner = runner,
        result = RunResult.NOT_STARTED, cases = listOf(case))
}

fun createMultiCaseRun(cases: List<SourceCase>, targetDirectoryPath: String,
                       importOptions: RunImportOptions = RunImportOptions(),
                       runner: String = defaultRunner(), title: String = ""): TestRun {
    val runCases = cases.map { buildRunCase(it, targetDirectoryPath, importOptions) }
    return TestRun(id = null, title = title, runner = runner,
        result = RunResult.NOT_STARTED, cases = runCases)
}
```

Keep the runner-only `createInitialRun` overload delegating to the new one. Update `deriveRunResult` callers: per-case result derives from that case's steps; the run-level aggregate is computed by a new `aggregateResult(cases)` per the rules in the format section.

- [ ] **Step 4: Run the support suite green**

Run: `./gradlew test --tests "*TestRunSupportTest*" 2>&1 | grep -E "FAILED|BUILD"`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/io/github/barsia/speqa/run/TestRunSupport.kt src/test/kotlin/io/github/barsia/speqa/run/TestRunSupportTest.kt
git commit -m "feat: createMultiCaseRun builds one RunCase section per source test case"
```

---

## Task 6: Full build + regression

- [ ] **Step 1: Compile and run the whole suite**

Run: `./gradlew compileKotlin compileTestKotlin test 2>&1 | grep -E "FAILED|BUILD|^e:"`
Expected: BUILD SUCCESSFUL. The single-case run editor still works because the compat accessors (`stepResults`, `tags`, etc.) flatten the one case.

- [ ] **Step 2: Manual smoke (optional, sandbox)**

Open an existing single-case `.tr.md` in the sandbox; confirm it loads and verdicts still save (the editor uses the compat path).

- [ ] **Step 3: Commit any test fixups**

```bash
git add -A
git commit -m "test: green build for multi-case run data layer"
```

---

## Self-review notes

- **Spec coverage:** model sections (Task 1), format + serializer (Task 2), parser + legacy (Task 3), round-trip (Task 4), creation (Task 5). Editor sections + section DnD and section-scoped patcher ops are Plan 2; tab + dialog are Plan 3.
- **Compat:** `TestRun` keeps `stepResults/tags/priority/environment/bodyBlocks/links/attachments` as read-only derived accessors so the existing editor/patcher compile for single-case runs until Plan 2 migrates them.
- **Open for Plan 2:** `DocumentRangeLocator`/`DocumentPatcher` still see a single flat `Scenario:`; section-scoped locating/patching and section DnD are Plan 2. Until then, run-side step patch ops operate on a single-case run (the common case from `createInitialRun`).

> **RELEASE BLOCKER - RESOLVED by Plan 2 (Task 1).** Task 2 moved `tags/priority/environment` out of the run frontmatter into the `Test Case:` section, but `DocumentPatcher` still patched the old frontmatter/flat layout, so edits on a newly-created (sectioned) single-case run were silently lost or destructive. Plan 2 closed this NOT by making the patcher section-aware but by routing all run edits through full re-serialization (`TestRunSerializer.serialize`); the run editor no longer uses `DocumentPatcher` at all, and the dead run-only patch ops were removed. The per-case `RunCase.result` recompute on verdict edits is also handled in Plan 2 (`TestRunSupport.recomputeCaseResult`). No remaining release blocker from this plan.

## Not in this plan (later plans)
- Plan 2: run editor renders multiple sections (case heading + per-case result + steps), collapsible, drag-and-drop reordering of whole sections; section-scoped `DocumentRangeLocator`/`DocumentPatcher`.
- Plan 3: Test Runs tab `+ Create test run` entry (header action + empty-state CTA, disabled when no cases) and the adaptive Create Test Run dialog (filters that appear only for present facets, live checkbox list, select-all/clear, per-section import options, date/time title, filename uniqueness).
