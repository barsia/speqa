# Test Run Redesign — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Redesign the test run to be self-contained (no linked test case), add NONE/BLOCKED verdicts, fix result auto-calculation, add manual result override, improve step row UX (expected result, collapsible comments), fix sync bug with text editor.

**Architecture:** `TestRun` model becomes self-contained with `title`, `StepResult` gains `expected`. `StepVerdict` gets `NONE` (initial) and `BLOCKED` values. `completedStepIndexes` is removed — verdict is the sole source of truth. `deriveRunResult` uses new logic ignoring NONE and SKIPPED. UI header shows result chip, dates with own tooltips. Step rows show expected, 4 verdict chips, collapsible comment.

**Tech Stack:** Kotlin, Compose/Jewel, IntelliJ Platform SDK.

**Spec:** `docs/specs/2026-04-06-speqa-design.md` — sections 4 and 8.

---

## File Structure

| File | Change | Responsibility |
|------|--------|---------------|
| `src/main/kotlin/io/speqa/speqa/model/TestRun.kt` | Modify | Add `NONE`/`BLOCKED` to `StepVerdict`, add `title`/`manualResult` to `TestRun`, add `expected` to `StepResult`, remove `testCaseFile` |
| `src/main/kotlin/io/speqa/speqa/model/TestCase.kt` | No change | — |
| `src/main/kotlin/io/speqa/speqa/parser/TestRunSerializer.kt` | Modify | Serialize new format: title, no test_case, expected in steps, NONE verdict as omitted line |
| `src/main/kotlin/io/speqa/speqa/parser/TestRunParser.kt` | Modify | Parse new format: title, manual_result, expected from steps, empty verdict = NONE |
| `src/main/kotlin/io/speqa/speqa/run/TestRunSupport.kt` | Modify | Remove `resolveLinkedTestCase`, `mergeStepResults`, `completedStepIndexes`, `synthesizeTestCase`. New `deriveRunResult` without completedStepIndexes. New `createInitialRun` that copies title/steps/expected from test case. New `nextRunFileName` with tc-stem prefix |
| `src/main/kotlin/io/speqa/speqa/run/TestRunPanel.kt` | Modify | Remove `testCase` param, use `TestRun` data directly. New header with result chip, own date tooltips. Step row with expected, 4 verdict chips, collapsible comment |
| `src/main/kotlin/io/speqa/speqa/run/TestRunEditor.kt` | Modify | Remove `testCase` field, remove `completedStepIndexes`. Read all data from `TestRun` |
| `src/main/kotlin/io/speqa/speqa/run/TestRunEditorProvider.kt` | Modify | Remove `resolveLinkedTestCase` call, pass only `TestRun` |
| `src/main/kotlin/io/speqa/speqa/editor/SpeqaEditorSupport.kt` | Modify | New run file naming `{tc-stem}_{timestamp}.tr.md`, copy title/expected into initial run |
| `src/main/kotlin/io/speqa/speqa/editor/ui/EditorPrimitives.kt` | Modify | Add `verdictBlocked` color to `SpeqaThemeColors` |
| `src/main/resources/messages/SpeqaBundle.properties` | Modify | Add bundle keys for Started/Finished tooltips, Blocked verdict, comment button |
| `src/test/kotlin/io/speqa/speqa/run/TestRunSupportTest.kt` | Create | Tests for new `deriveRunResult` logic |
| `src/test/kotlin/io/speqa/speqa/parser/TestRunSerializerTest.kt` | Modify | Update for new format |
| `src/test/kotlin/io/speqa/speqa/parser/TestRunParserTest.kt` | Modify | Update for new format |

---

### Task 1: Model Changes — StepVerdict, StepResult, TestRun

**Files:**
- Modify: `src/main/kotlin/io/speqa/speqa/model/TestRun.kt`
- Test: `src/test/kotlin/io/speqa/speqa/model/TestCaseTest.kt` (add verdict tests)

- [ ] **Step 1: Write failing tests for new verdict enum**

Add to `src/test/kotlin/io/speqa/speqa/model/TestCaseTest.kt`:

```kotlin
@Test
fun `stepVerdict NONE has empty label`() {
    assertEquals("", StepVerdict.NONE.label)
}

@Test
fun `stepVerdict BLOCKED has blocked label`() {
    assertEquals("blocked", StepVerdict.BLOCKED.label)
}

@Test
fun `stepVerdict fromString returns NONE for empty`() {
    assertEquals(StepVerdict.NONE, StepVerdict.fromString(""))
}

@Test
fun `stepVerdict fromString returns NONE for unknown`() {
    assertEquals(StepVerdict.NONE, StepVerdict.fromString("garbage"))
}

@Test
fun `stepVerdict fromString returns BLOCKED`() {
    assertEquals(StepVerdict.BLOCKED, StepVerdict.fromString("blocked"))
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests "io.github.barsia.speqa.model.TestCaseTest" --info`
Expected: FAIL — `NONE` and `BLOCKED` do not exist.

- [ ] **Step 3: Update the model**

Modify `src/main/kotlin/io/speqa/speqa/model/TestRun.kt`:

```kotlin
enum class StepVerdict(val label: String) {
    NONE(""),
    PASSED("passed"),
    FAILED("failed"),
    SKIPPED("skipped"),
    BLOCKED("blocked");

    companion object {
        fun fromString(value: String): StepVerdict {
            if (value.isBlank()) return NONE
            return entries.firstOrNull { it.label.equals(value.trim(), ignoreCase = true) } ?: NONE
        }
    }
}

data class StepResult(
    val action: String = "",
    val expected: String = "",
    val verdict: StepVerdict = StepVerdict.NONE,
    val comment: String = "",
)

data class TestRun(
    val id: Int? = null,
    val title: String = "",
    val startedAt: LocalDateTime = LocalDateTime.now(),
    val finishedAt: LocalDateTime? = null,
    val result: RunResult = RunResult.FAILED,
    val manualResult: Boolean = false,
    val environment: String = "",
    val runner: String = "",
    val stepResults: List<StepResult> = emptyList(),
    val summary: String = "",
)
```

Key changes:
- `StepVerdict`: add `NONE("")` as first entry and `BLOCKED("blocked")`. `fromString` returns `NONE` for blank/unknown.
- `StepResult`: add `expected: String = ""`, change default verdict to `StepVerdict.NONE`.
- `TestRun`: add `title: String`, `manualResult: Boolean`. Remove `testCaseFile: String`.

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew test --tests "io.github.barsia.speqa.model.TestCaseTest" --info`
Expected: ALL PASS

- [ ] **Step 5: Fix compilation errors in other files**

Removing `testCaseFile` and changing `StepVerdict.SKIPPED` default will cause compile errors in files that reference them. For now, fix compilation only — don't change behavior yet. Key files:
- `TestRunParser.kt`: remove `testCaseFile` parsing, update `StepVerdict.SKIPPED` → `StepVerdict.NONE`
- `TestRunSerializer.kt`: remove `test_case` line, will be fully rewritten in Task 3
- `TestRunSupport.kt`: remove `testCaseFile` references
- `TestRunEditor.kt`: remove `testCase` constructor param — temporarily pass title from `initialRun.title`
- `TestRunEditorProvider.kt`: remove `resolveLinkedTestCase` call
- `TestRunPanel.kt`: replace `testCase` param with `title: String` and `environmentOptions: List<String>`
- `SpeqaEditorSupport.kt`: update `createInitialRun` call

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git commit -m "refactor: make TestRun self-contained, add NONE/BLOCKED verdicts"
```

---

### Task 2: New deriveRunResult Logic

**Files:**
- Create: `src/test/kotlin/io/speqa/speqa/run/TestRunSupportTest.kt`
- Modify: `src/main/kotlin/io/speqa/speqa/run/TestRunSupport.kt`

- [ ] **Step 1: Write failing tests**

Create `src/test/kotlin/io/speqa/speqa/run/TestRunSupportTest.kt`:

```kotlin
package io.github.barsia.speqa.run

import io.github.barsia.speqa.model.RunResult
import io.github.barsia.speqa.model.StepResult
import io.github.barsia.speqa.model.StepVerdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TestRunSupportTest {

    @Test
    fun `all passed steps produce passed result`() {
        val steps = listOf(
            StepResult(verdict = StepVerdict.PASSED),
            StepResult(verdict = StepVerdict.PASSED),
        )
        assertEquals(RunResult.PASSED, TestRunSupport.deriveRunResult(steps))
    }

    @Test
    fun `one failed step produces failed result`() {
        val steps = listOf(
            StepResult(verdict = StepVerdict.PASSED),
            StepResult(verdict = StepVerdict.FAILED),
        )
        assertEquals(RunResult.FAILED, TestRunSupport.deriveRunResult(steps))
    }

    @Test
    fun `one blocked step without failed produces blocked result`() {
        val steps = listOf(
            StepResult(verdict = StepVerdict.PASSED),
            StepResult(verdict = StepVerdict.BLOCKED),
        )
        assertEquals(RunResult.BLOCKED, TestRunSupport.deriveRunResult(steps))
    }

    @Test
    fun `failed takes precedence over blocked`() {
        val steps = listOf(
            StepResult(verdict = StepVerdict.FAILED),
            StepResult(verdict = StepVerdict.BLOCKED),
        )
        assertEquals(RunResult.FAILED, TestRunSupport.deriveRunResult(steps))
    }

    @Test
    fun `skipped steps are ignored`() {
        val steps = listOf(
            StepResult(verdict = StepVerdict.PASSED),
            StepResult(verdict = StepVerdict.SKIPPED),
        )
        assertEquals(RunResult.PASSED, TestRunSupport.deriveRunResult(steps))
    }

    @Test
    fun `NONE steps are ignored`() {
        val steps = listOf(
            StepResult(verdict = StepVerdict.PASSED),
            StepResult(verdict = StepVerdict.NONE),
        )
        assertEquals(RunResult.PASSED, TestRunSupport.deriveRunResult(steps))
    }

    @Test
    fun `all NONE steps produce null result`() {
        val steps = listOf(
            StepResult(verdict = StepVerdict.NONE),
            StepResult(verdict = StepVerdict.NONE),
        )
        assertNull(TestRunSupport.deriveRunResult(steps))
    }

    @Test
    fun `all skipped steps produce null result`() {
        val steps = listOf(
            StepResult(verdict = StepVerdict.SKIPPED),
            StepResult(verdict = StepVerdict.SKIPPED),
        )
        assertNull(TestRunSupport.deriveRunResult(steps))
    }

    @Test
    fun `empty steps produce null result`() {
        assertNull(TestRunSupport.deriveRunResult(emptyList()))
    }

    @Test
    fun `mixed NONE skipped and passed produces passed`() {
        val steps = listOf(
            StepResult(verdict = StepVerdict.NONE),
            StepResult(verdict = StepVerdict.SKIPPED),
            StepResult(verdict = StepVerdict.PASSED),
        )
        assertEquals(RunResult.PASSED, TestRunSupport.deriveRunResult(steps))
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests "io.github.barsia.speqa.run.TestRunSupportTest" --info`
Expected: FAIL — signature mismatch (old `deriveRunResult` takes two params).

- [ ] **Step 3: Implement new deriveRunResult**

In `TestRunSupport.kt`, replace `deriveRunResult` and remove `completedStepIndexes`, `defaultCurrentStepIndex`:

```kotlin
fun deriveRunResult(stepResults: List<StepResult>): RunResult? {
    val meaningful = stepResults.filter { it.verdict != StepVerdict.NONE && it.verdict != StepVerdict.SKIPPED }
    if (meaningful.isEmpty()) return null
    if (meaningful.any { it.verdict == StepVerdict.FAILED }) return RunResult.FAILED
    if (meaningful.any { it.verdict == StepVerdict.BLOCKED }) return RunResult.BLOCKED
    return RunResult.PASSED
}

fun currentStepIndex(stepResults: List<StepResult>): Int {
    val nextNone = stepResults.indexOfFirst { it.verdict == StepVerdict.NONE }
    return if (nextNone >= 0) nextNone else stepResults.lastIndex.coerceAtLeast(0)
}
```

Remove: `completedStepIndexes()`, `defaultCurrentStepIndex()`, `resolveLinkedTestCase()`, `synthesizeTestCase()`, `prettifyTitle()`, `testCaseStem()`, `mergeStepResults()`. Remove the `anyIndexed` extension.

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew test --tests "io.github.barsia.speqa.run.TestRunSupportTest" --info`
Expected: ALL PASS

- [ ] **Step 5: Commit**

```bash
git commit -m "refactor: new deriveRunResult logic, remove completedStepIndexes"
```

---

### Task 3: Serializer — New .tr.md Format

**Files:**
- Modify: `src/main/kotlin/io/speqa/speqa/parser/TestRunSerializer.kt`
- Modify: `src/test/kotlin/io/speqa/speqa/parser/TestRunSerializerTest.kt` (if exists, otherwise create)

- [ ] **Step 1: Write failing test for new format**

```kotlin
package io.github.barsia.speqa.parser

import io.github.barsia.speqa.model.RunResult
import io.github.barsia.speqa.model.StepResult
import io.github.barsia.speqa.model.StepVerdict
import io.github.barsia.speqa.model.TestRun
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class TestRunSerializerTest {

    @Test
    fun `serializes title in frontmatter`() {
        val run = TestRun(title = "Login test", startedAt = LocalDateTime.of(2026, 4, 11, 10, 0))
        val result = TestRunSerializer.serialize(run)
        assertTrue(result.contains("title: \"Login test\""))
    }

    @Test
    fun `does not serialize test_case field`() {
        val run = TestRun(title = "Login test", startedAt = LocalDateTime.of(2026, 4, 11, 10, 0))
        val result = TestRunSerializer.serialize(run)
        assertFalse(result.contains("test_case"))
    }

    @Test
    fun `serializes manual_result flag`() {
        val run = TestRun(title = "Test", manualResult = true, startedAt = LocalDateTime.of(2026, 4, 11, 10, 0))
        val result = TestRunSerializer.serialize(run)
        assertTrue(result.contains("manual_result: true"))
    }

    @Test
    fun `serializes step with expected and verdict`() {
        val run = TestRun(
            title = "Test",
            startedAt = LocalDateTime.of(2026, 4, 11, 10, 0),
            stepResults = listOf(
                StepResult(action = "Click button", expected = "Page loads", verdict = StepVerdict.PASSED),
            ),
        )
        val result = TestRunSerializer.serialize(run)
        assertTrue(result.contains("1. Click button"))
        assertTrue(result.contains("> Expected: Page loads"))
        assertTrue(result.contains("— **passed**"))
    }

    @Test
    fun `omits verdict line for NONE`() {
        val run = TestRun(
            title = "Test",
            startedAt = LocalDateTime.of(2026, 4, 11, 10, 0),
            stepResults = listOf(
                StepResult(action = "Click button", expected = "Page loads", verdict = StepVerdict.NONE),
            ),
        )
        val result = TestRunSerializer.serialize(run)
        assertTrue(result.contains("1. Click button"))
        assertFalse(result.contains("**"))
    }

    @Test
    fun `serializes step comment after verdict`() {
        val run = TestRun(
            title = "Test",
            startedAt = LocalDateTime.of(2026, 4, 11, 10, 0),
            stepResults = listOf(
                StepResult(action = "Click", expected = "", verdict = StepVerdict.FAILED, comment = "Got 500 error"),
            ),
        )
        val result = TestRunSerializer.serialize(run)
        assertTrue(result.contains("— **failed**"))
        assertTrue(result.contains("> Got 500 error"))
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests "io.github.barsia.speqa.parser.TestRunSerializerTest" --info`

- [ ] **Step 3: Rewrite serializer**

```kotlin
object TestRunSerializer {
    private val formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    fun serialize(testRun: TestRun): String {
        return buildString {
            appendLine("---")
            testRun.id?.let { appendLine("id: $it") }
            appendLine("title: ${SpeqaMarkdown.quoteYamlScalar(testRun.title)}")
            appendLine("started_at: ${SpeqaMarkdown.quoteYamlScalar(formatter.format(testRun.startedAt))}")
            testRun.finishedAt?.let { appendLine("finished_at: ${SpeqaMarkdown.quoteYamlScalar(formatter.format(it))}") }
            appendLine("result: ${testRun.result.label}")
            if (testRun.manualResult) appendLine("manual_result: true")
            appendLine("environment: ${SpeqaMarkdown.quoteYamlScalar(testRun.environment)}")
            appendLine("runner: ${SpeqaMarkdown.quoteYamlScalar(testRun.runner)}")
            appendLine("---")
            appendLine()
            appendLine("## Step Results")
            appendLine()
            if (testRun.stepResults.isNotEmpty()) {
                testRun.stepResults.forEachIndexed { index, step ->
                    appendStepResult(index + 1, step)
                    appendLine()
                }
            }
            appendLine("## Summary")
            appendLine()
            if (testRun.summary.isNotBlank()) {
                appendLine(testRun.summary)
            }
        }.trimEnd() + "\n"
    }

    private fun StringBuilder.appendStepResult(number: Int, step: StepResult) {
        appendLine("$number. ${step.action}")
        if (step.expected.isNotBlank()) {
            appendLine("   > Expected: ${step.expected}")
        }
        if (step.verdict != StepVerdict.NONE) {
            appendLine("   — **${step.verdict.label}**")
        }
        if (step.comment.isNotBlank()) {
            step.comment.lines().forEach { line ->
                appendLine("   > $line")
            }
        }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew test --tests "io.github.barsia.speqa.parser.TestRunSerializerTest" --info`
Expected: ALL PASS

- [ ] **Step 5: Commit**

```bash
git commit -m "refactor: TestRunSerializer for self-contained format with expected/NONE"
```

---

### Task 4: Parser — New .tr.md Format

**Files:**
- Modify: `src/main/kotlin/io/speqa/speqa/parser/TestRunParser.kt`
- Modify: `src/test/kotlin/io/speqa/speqa/parser/TestRunParserTest.kt` (if exists, otherwise create)

- [ ] **Step 1: Write failing tests for new format**

```kotlin
package io.github.barsia.speqa.parser

import io.github.barsia.speqa.model.StepVerdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TestRunParserNewFormatTest {

    @Test
    fun `parses title from frontmatter`() {
        val content = """
            |---
            |title: "Login test"
            |started_at: 2026-04-11T10:00:00
            |result: passed
            |---
        """.trimMargin()
        val run = TestRunParser.parse(content)
        assertEquals("Login test", run.title)
    }

    @Test
    fun `parses manual_result flag`() {
        val content = """
            |---
            |title: "Test"
            |started_at: 2026-04-11T10:00:00
            |result: passed
            |manual_result: true
            |---
        """.trimMargin()
        val run = TestRunParser.parse(content)
        assertTrue(run.manualResult)
    }

    @Test
    fun `manual_result defaults to false`() {
        val content = """
            |---
            |title: "Test"
            |started_at: 2026-04-11T10:00:00
            |result: passed
            |---
        """.trimMargin()
        val run = TestRunParser.parse(content)
        assertFalse(run.manualResult)
    }

    @Test
    fun `parses step with expected and verdict`() {
        val content = """
            |---
            |title: "Test"
            |started_at: 2026-04-11T10:00:00
            |result: passed
            |---
            |
            |## Step Results
            |
            |1. Click button
            |   > Expected: Page loads
            |   — **passed**
        """.trimMargin()
        val run = TestRunParser.parse(content)
        assertEquals(1, run.stepResults.size)
        assertEquals("Click button", run.stepResults[0].action)
        assertEquals("Page loads", run.stepResults[0].expected)
        assertEquals(StepVerdict.PASSED, run.stepResults[0].verdict)
    }

    @Test
    fun `step without verdict line gets NONE`() {
        val content = """
            |---
            |title: "Test"
            |started_at: 2026-04-11T10:00:00
            |result: failed
            |---
            |
            |## Step Results
            |
            |1. Click button
            |   > Expected: Page loads
        """.trimMargin()
        val run = TestRunParser.parse(content)
        assertEquals(StepVerdict.NONE, run.stepResults[0].verdict)
    }

    @Test
    fun `parses comment lines after verdict`() {
        val content = """
            |---
            |title: "Test"
            |started_at: 2026-04-11T10:00:00
            |result: failed
            |---
            |
            |## Step Results
            |
            |1. Click button
            |   > Expected: Page loads
            |   — **failed**
            |   > Got 500 error
            |   > Server timeout
        """.trimMargin()
        val run = TestRunParser.parse(content)
        assertEquals("Got 500 error\nServer timeout", run.stepResults[0].comment)
    }

    @Test
    fun `parses blocked verdict`() {
        val content = """
            |---
            |title: "Test"
            |started_at: 2026-04-11T10:00:00
            |result: blocked
            |---
            |
            |## Step Results
            |
            |1. Click button
            |   — **blocked**
        """.trimMargin()
        val run = TestRunParser.parse(content)
        assertEquals(StepVerdict.BLOCKED, run.stepResults[0].verdict)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

- [ ] **Step 3: Rewrite parser**

Update `TestRunParser.kt` to handle new format:
- Parse `title` from frontmatter
- Parse `manual_result` boolean (default false)
- No `testCaseFile` — remove parsing
- Step parsing: action line, optional `> Expected:` line, optional `— **verdict**` line, comment lines after verdict
- Update regex patterns: `STEP_PATTERN` matches just `N. action`, `VERDICT_PATTERN` matches `— **verdict**`, `EXPECTED_PATTERN` matches `> Expected: ...`, `COMMENT_PATTERN` matches `> ...` (after verdict)

```kotlin
object TestRunParser {
    fun parse(content: String): TestRun {
        val normalized = SpeqaMarkdown.normalizeLineEndings(content)
        if (normalized.isBlank()) return TestRun()

        val (frontmatter, body) = SpeqaMarkdown.splitFrontmatter(normalized)
        val meta = SpeqaMarkdown.parseYamlMap(frontmatter)

        return TestRun(
            id = if ("id" in meta) (meta["id"] as? Number)?.toInt() else null,
            title = SpeqaMarkdown.parseScalar(meta["title"]),
            startedAt = parseDateTime(meta["started_at"]) ?: LocalDateTime.now(),
            finishedAt = parseDateTime(meta["finished_at"]),
            result = RunResult.fromString(SpeqaMarkdown.parseScalar(meta["result"]).ifBlank { "failed" }),
            manualResult = meta["manual_result"]?.toString()?.trim()?.equals("true", ignoreCase = true) == true,
            environment = SpeqaMarkdown.parseScalar(meta["environment"]),
            runner = SpeqaMarkdown.parseScalar(meta["runner"]),
            stepResults = parseStepResults(body),
            summary = SpeqaMarkdown.extractSection(body, "## Summary").orEmpty().trim(),
        )
    }

    private fun parseStepResults(body: String): List<StepResult> {
        val section = SpeqaMarkdown.extractSection(body, "## Step Results") ?: return emptyList()
        val steps = mutableListOf<StepResult>()
        var action: String? = null
        var expected = ""
        var verdict = StepVerdict.NONE
        var verdictSeen = false
        var commentLines = mutableListOf<String>()

        fun flush() {
            if (action != null) {
                steps += StepResult(action = action!!, expected = expected, verdict = verdict, comment = commentLines.joinToString("\n"))
            }
        }

        for (line in section.lines()) {
            val trimmed = line.trim()

            val stepMatch = STEP_PATTERN.matchEntire(trimmed)
            if (stepMatch != null) {
                flush()
                action = stepMatch.groupValues[1].trim()
                expected = ""
                verdict = StepVerdict.NONE
                verdictSeen = false
                commentLines = mutableListOf()
                continue
            }

            if (action == null) continue

            val verdictMatch = VERDICT_PATTERN.matchEntire(trimmed)
            if (verdictMatch != null) {
                verdict = StepVerdict.fromString(verdictMatch.groupValues[1])
                verdictSeen = true
                continue
            }

            val quoteMatch = QUOTE_PATTERN.matchEntire(trimmed)
            if (quoteMatch != null) {
                val quoteText = quoteMatch.groupValues[1]
                if (!verdictSeen && quoteText.startsWith("Expected: ")) {
                    expected = quoteText.removePrefix("Expected: ").trim()
                } else if (verdictSeen) {
                    commentLines += quoteText
                }
                continue
            }
        }
        flush()
        return steps
    }

    // ... parseDateTime unchanged ...

    private val STEP_PATTERN = Regex("""^\d+\.\s+(.+)$""")
    private val VERDICT_PATTERN = Regex("""^[—-]\s*\*\*(passed|failed|skipped|blocked)\*\*$""", RegexOption.IGNORE_CASE)
    private val QUOTE_PATTERN = Regex("""^>\s?(.*)$""")
}
```

- [ ] **Step 4: Run tests to verify they pass**

- [ ] **Step 5: Commit**

```bash
git commit -m "refactor: TestRunParser for self-contained format with expected/NONE/BLOCKED"
```

---

### Task 5: Run Creation — File Naming and Initial Data

**Files:**
- Modify: `src/main/kotlin/io/speqa/speqa/run/TestRunSupport.kt`
- Modify: `src/main/kotlin/io/speqa/speqa/editor/SpeqaEditorSupport.kt`

- [ ] **Step 1: Update `nextRunFileName` to use tc-stem prefix**

In `TestRunSupport.kt`:

```kotlin
fun nextRunFileName(testCaseFileName: String, now: LocalDateTime, existingNames: Set<String>): String {
    val stem = testCaseFileName.removeSuffix(".${SpeqaDefaults.TEST_CASE_EXTENSION}")
    val timestamp = runFileTimestampFormat.format(now)
    var candidate = "${stem}_$timestamp.${SpeqaDefaults.TEST_RUN_EXTENSION}"
    var counter = 2
    while (candidate in existingNames) {
        candidate = "${stem}_${timestamp}-$counter.${SpeqaDefaults.TEST_RUN_EXTENSION}"
        counter += 1
    }
    return candidate
}
```

- [ ] **Step 2: Update `createInitialRun` to copy title and expected**

```kotlin
fun createInitialRun(
    testCase: TestCase,
    startedAt: LocalDateTime = LocalDateTime.now(),
    runner: String = defaultRunner(),
): TestRun {
    return TestRun(
        title = testCase.title,
        startedAt = startedAt,
        finishedAt = null,
        result = RunResult.FAILED,
        environment = testCase.environment?.firstOrNull().orEmpty(),
        runner = runner,
        stepResults = testCase.steps.map { step ->
            StepResult(action = step.action, expected = step.expected.orEmpty())
        },
        summary = "",
    )
}
```

- [ ] **Step 3: Update `SpeqaEditorSupport.startTestRun`**

Change `nextRunFileName` call to pass test case file name:

```kotlin
val defaultFileName = TestRunSupport.nextRunFileName(
    testCaseFileName = testCaseFile.name,
    now = startedAt,
    existingNames = emptySet(),
)
```

Change `createInitialRun` call — remove `testCaseFileName` param:

```kotlin
val initialRun = TestRunSupport.createInitialRun(
    testCase = testCase,
    startedAt = startedAt,
)
```

- [ ] **Step 4: Verify compilation**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git commit -m "feat: run file naming {tc-stem}_{timestamp}.tr.md, copy title/expected"
```

---

### Task 6: Bundle Keys and Theme Colors

**Files:**
- Modify: `src/main/resources/messages/SpeqaBundle.properties`
- Modify: `src/main/kotlin/io/speqa/speqa/editor/ui/EditorPrimitives.kt`

- [ ] **Step 1: Add bundle keys**

```properties
# --- Test Run dates ---
run.tooltip.started=Started
run.tooltip.finished=Finished

# --- Verdict ---
run.blocked=Blocked
run.addComment=Add comment
```

- [ ] **Step 2: Add `verdictBlocked` color to SpeqaThemeColors**

In `EditorPrimitives.kt`, in `SpeqaThemeColors`:

```kotlin
val verdictBlocked: Color
    @Composable get() = accentSubtle
```

(Reuses `accentSubtle` — a muted accent tone, visually distinct from passed/failed/skipped.)

- [ ] **Step 3: Verify compilation**

Run: `./gradlew compileKotlin`

- [ ] **Step 4: Commit**

```bash
git commit -m "feat: add bundle keys for run tooltips and blocked verdict color"
```

---

### Task 7: TestRunEditor and TestRunEditorProvider — Remove testCase Dependency

**Files:**
- Modify: `src/main/kotlin/io/speqa/speqa/run/TestRunEditor.kt`
- Modify: `src/main/kotlin/io/speqa/speqa/run/TestRunEditorProvider.kt`

- [ ] **Step 1: Update TestRunEditorProvider**

Remove `resolveLinkedTestCase` call. Pass only `initialRun`:

```kotlin
override fun createEditor(project: Project, file: VirtualFile): FileEditor {
    val document = FileDocumentManager.getInstance().getDocument(file)
        ?: return TextEditorProvider.getInstance().createEditor(project, file)
    val initialRun = TestRunSupport.parseTestRunOrNull(document.text)
        ?: return TextEditorProvider.getInstance().createEditor(project, file)
    val textEditor = TextEditorProvider.getInstance().createEditor(project, file) as TextEditor
    return TestRunSplitEditor(
        textEditor = textEditor,
        previewEditor = TestRunEditor(project, file, document, initialRun),
    )
}
```

- [ ] **Step 2: Update TestRunEditor**

Remove `testCase` constructor param. All data from `initialRun`:

```kotlin
class TestRunEditor(
    private val project: Project,
    private val file: VirtualFile,
    private val document: Document,
    private val initialRun: TestRun,
) : UserDataHolderBase(), FileEditor, Disposable {

    private var runId by mutableStateOf(initialRun.id)
    private var title by mutableStateOf(initialRun.title)
    private var stepResults by mutableStateOf(initialRun.stepResults)
    private var currentStepIndex by mutableStateOf(TestRunSupport.currentStepIndex(initialRun.stepResults))
    private var environment by mutableStateOf(initialRun.environment)
    private var runner by mutableStateOf(initialRun.runner.ifBlank { TestRunSupport.defaultRunner() })
    private var summary by mutableStateOf(initialRun.summary)
    private var isFinished by mutableStateOf(initialRun.finishedAt != null)
    private var manualResult by mutableStateOf(initialRun.manualResult)
    private var overriddenResult by mutableStateOf(initialRun.result)
    // ... rest of editor
```

Update `saveToDocument` to use new `deriveRunResult`:

```kotlin
private fun saveToDocument() {
    val autoResult = TestRunSupport.deriveRunResult(stepResults)
    val run = TestRun(
        id = runId,
        title = title,
        startedAt = startedAt,
        finishedAt = if (isFinished) LocalDateTime.now() else null,
        result = if (manualResult) overriddenResult else (autoResult ?: RunResult.BLOCKED),
        manualResult = manualResult,
        environment = environment,
        runner = runner,
        stepResults = stepResults,
        summary = summary,
    )
    // ... serialize and write
}
```

Remove `completedStepIndexes` state entirely.

- [ ] **Step 3: Verify compilation**

Run: `./gradlew compileKotlin`

- [ ] **Step 4: Commit**

```bash
git commit -m "refactor: TestRunEditor reads all data from TestRun, no testCase dependency"
```

---

### Task 8: TestRunPanel UI — Header, Step Rows, Comments

**Files:**
- Modify: `src/main/kotlin/io/speqa/speqa/run/TestRunPanel.kt`

This is the largest task. Key changes:

- [ ] **Step 1: Update TestRunPanel signature**

Remove `testCase: TestCase` and `completedStepIndexes`. Add `title`, `environmentOptions`, `result`, `manualResult`, `onResultOverride`:

```kotlin
@Composable
fun TestRunPanel(
    title: String,
    runId: Int?,
    // ... id params unchanged ...
    startedAt: LocalDateTime,
    finishedAt: LocalDateTime?,
    result: RunResult?,
    manualResult: Boolean,
    onResultOverride: (RunResult) -> Unit,
    stepResults: List<StepResult>,
    currentStepIndex: Int,
    environment: String,
    environmentOptions: List<String>,
    runner: String,
    summary: String,
    onEnvironmentChange: (String) -> Unit,
    onRunnerChange: (String) -> Unit,
    onSummaryChange: (String) -> Unit,
    onStepVerdictChange: (Int, StepVerdict) -> Unit,
    onStepCommentChange: (Int, String) -> Unit,
    onNext: () -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
)
```

- [ ] **Step 2: Update header — dates with own tooltips, result chip**

Replace date `DateIconLabel` calls to use `run.tooltip.started` / `run.tooltip.finished` bundle keys instead of `preview.created` / `preview.updated`.

Add result chip after progress row — clickable, shows `RunResult` values on click for manual override.

- [ ] **Step 3: Update StepResultRow — expected, 4 chips, collapsible comment**

```kotlin
@Composable
private fun StepResultRow(
    index: Int,
    step: StepResult,
    isCurrent: Boolean,
    onVerdictChange: (StepVerdict) -> Unit,
    onCommentChange: (String) -> Unit,
) {
    var showComment by remember { mutableStateOf(step.comment.isNotBlank()) }
    // ... layout:
    // Action text
    // Expected: text (if step.expected.isNotBlank())
    // Row: [Passed] [Failed] [Skipped] [Blocked] [💬 comment icon]
    // if (showComment) { PlainTextInput with vertically centered placeholder }
}
```

4 verdict chips: Passed, Failed, Skipped, Blocked. Comment icon button toggles `showComment`. PlainTextInput placeholder centered vertically using `Alignment.CenterVertically` in a Box with `minHeight`.

- [ ] **Step 4: Verify compilation**

Run: `./gradlew compileKotlin`

- [ ] **Step 5: Run all tests**

Run: `./gradlew test`
Expected: ALL PASS

- [ ] **Step 6: Commit**

```bash
git commit -m "feat: redesigned TestRunPanel — result chip, expected, 4 verdicts, collapsible comments"
```

---

### Task 9: Wire TestRunEditor to New TestRunPanel

**Files:**
- Modify: `src/main/kotlin/io/speqa/speqa/run/TestRunEditor.kt`

- [ ] **Step 1: Update compose panel call to match new TestRunPanel signature**

Pass `title`, `result`, `manualResult`, `onResultOverride`, `environmentOptions` from editor state. Remove `testCase` and `completedStepIndexes`.

- [ ] **Step 2: Add `onResultOverride` handler**

```kotlin
onResultOverride = { newResult ->
    manualResult = true
    overriddenResult = newResult
    saveToDocument()
},
```

- [ ] **Step 3: Update `onStepVerdictChange` — remove completedStepIndexes logic**

```kotlin
onStepVerdictChange = { index, verdict ->
    stepResults = stepResults.toMutableList().also { results ->
        results[index] = results[index].copy(verdict = verdict)
    }
    if (index == currentStepIndex && currentStepIndex < stepResults.lastIndex) {
        currentStepIndex += 1
    }
    saveToDocument()
},
```

- [ ] **Step 4: Verify compilation and test**

Run: `./gradlew compileKotlin && ./gradlew test`

- [ ] **Step 5: Commit**

```bash
git commit -m "feat: wire TestRunEditor to redesigned TestRunPanel"
```

---

### Task 10: Cleanup and Final Verification

**Files:**
- Modify: `src/main/kotlin/io/speqa/speqa/run/TestRunSupport.kt` (remove dead code)
- Modify: `src/main/kotlin/io/speqa/speqa/wizard/SpeqaProjectScaffold.kt` (update sample if needed)

- [ ] **Step 1: Remove dead code from TestRunSupport**

Verify and remove any remaining dead methods: `resolveLinkedTestCase`, `synthesizeTestCase`, `mergeStepResults`, `completedStepIndexes`, `defaultCurrentStepIndex`, `prettifyTitle`, `testCaseStem`, `anyIndexed` extension.

- [ ] **Step 2: Run full test suite**

Run: `./gradlew test`
Expected: ALL PASS

- [ ] **Step 3: Run in sandbox and verify**

Run: `./gradlew runIde`
- Create a test case with steps and expected results
- Start a test run — verify title, expected results shown, 4 verdict chips, comment button works
- Verify run file naming: `{tc-stem}_{timestamp}.tr.md`
- Verify result auto-calculates from verdicts
- Manually override result — verify it sticks
- Edit `.tr.md` in text editor, delete a verdict — verify UI reflects the change (no reversion)

- [ ] **Step 4: Commit**

```bash
git commit -m "chore: cleanup dead code from test run redesign"
```
