# Test Run Full Content — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make test run a complete copy of test case content (description, preconditions, links, all attachments, priority, status) plus run-specific data (verdicts, comments, overall comment, result). Fix comment parsing bug. Align step number styling with test case.

**Architecture:** Extend `TestRun` and `StepResult` models with all test case fields. Serializer/parser handle new fields. `createInitialRun` copies everything from test case. UI shows readonly test case data + editable run data. Parser uses blank-line separator to distinguish expected from comment.

**Tech Stack:** Kotlin, Compose/Jewel, IntelliJ Platform SDK.

**Spec:** `docs/specs/2026-04-06-speqa-design.md` — section 8.

---

## File Structure

| File | Change | Responsibility |
|------|--------|---------------|
| `src/main/kotlin/io/speqa/speqa/model/TestRun.kt` | Modify | Add priority, status, bodyBlocks, links, attachments to TestRun. Add actionAttachments, expectedAttachments to StepResult. Rename summary→comment |
| `src/main/kotlin/io/speqa/speqa/run/TestRunSupport.kt` | Modify | `createInitialRun` copies all fields from test case |
| `src/main/kotlin/io/speqa/speqa/parser/TestRunSerializer.kt` | Modify | Serialize new fields in frontmatter, body blocks before steps, blank line between expected and verdict |
| `src/main/kotlin/io/speqa/speqa/parser/TestRunParser.kt` | Modify | Parse new frontmatter fields, body blocks, fix comment/expected separation |
| `src/main/kotlin/io/speqa/speqa/run/TestRunPanel.kt` | Modify | Show description, preconditions, links, attachments (readonly). Add overall comment field. Fix step number alignment |
| `src/main/kotlin/io/speqa/speqa/run/TestRunEditor.kt` | Modify | Wire new fields, overall comment state |
| `src/main/resources/messages/SpeqaBundle.properties` | Modify | Add bundle keys for run comment |
| `src/test/kotlin/io/speqa/speqa/parser/TestRunSerializerTest.kt` | Modify | Tests for new format |
| `src/test/kotlin/io/speqa/speqa/parser/TestRunParserTest.kt` | Modify | Tests for new format + comment bug fix |

---

### Task 1: Model — Add Test Case Fields to TestRun and StepResult

**Files:**
- Modify: `src/main/kotlin/io/speqa/speqa/model/TestRun.kt`

- [ ] **Step 1: Update spec**

Edit `docs/specs/2026-04-06-speqa-design.md` — already done above.

- [ ] **Step 2: Update TestRun data class**

```kotlin
data class TestRun(
    val id: Int? = null,
    val title: String = "",
    val tags: List<String> = emptyList(),
    val priority: Priority? = null,
    val status: Status? = null,
    val manualResult: Boolean = false,
    val startedAt: LocalDateTime? = null,
    val finishedAt: LocalDateTime? = null,
    val result: RunResult = RunResult.NOT_STARTED,
    val environment: String = "",
    val runner: String = "",
    val bodyBlocks: List<TestCaseBodyBlock> = emptyList(),
    val links: List<Link> = emptyList(),
    val attachments: List<Attachment> = emptyList(),
    val stepResults: List<StepResult> = emptyList(),
    val comment: String = "",
)
```

Add imports for `Priority`, `Status`, `TestCaseBodyBlock`, `Link`, `Attachment`.

- [ ] **Step 3: Update StepResult data class**

```kotlin
data class StepResult(
    val action: String = "",
    val expected: String = "",
    val verdict: StepVerdict = StepVerdict.NONE,
    val comment: String = "",
    val actionAttachments: List<Attachment> = emptyList(),
    val expectedAttachments: List<Attachment> = emptyList(),
)
```

- [ ] **Step 4: Fix compilation errors**

Other files reference old fields. Fix:
- `TestRunSupport.kt` — `createInitialRun` needs to copy new fields
- `TestRunEditor.kt` — add `comment` mutable state (was `summary`, may have been removed)
- `TestRunPanel.kt` — add `comment`/`onCommentChange` params

Run: `./gradlew compileKotlin`

- [ ] **Step 5: Commit**

```bash
git commit -m "feat: extend TestRun/StepResult with full test case content"
```

---

### Task 2: createInitialRun — Copy All Test Case Data

**Files:**
- Modify: `src/main/kotlin/io/speqa/speqa/run/TestRunSupport.kt`

- [ ] **Step 1: Update createInitialRun**

```kotlin
fun createInitialRun(
    testCase: TestCase,
    runner: String = defaultRunner(),
): TestRun {
    return TestRun(
        title = testCase.title,
        tags = testCase.tags.orEmpty(),
        priority = testCase.priority,
        status = testCase.status,
        environment = testCase.environment?.firstOrNull().orEmpty(),
        runner = runner,
        bodyBlocks = testCase.bodyBlocks,
        links = testCase.links,
        attachments = testCase.attachments,
        stepResults = testCase.steps.map { step ->
            StepResult(
                action = step.action,
                expected = step.expected.orEmpty(),
                actionAttachments = step.actionAttachments,
                expectedAttachments = step.expectedAttachments,
            )
        },
    )
}
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew compileKotlin`

- [ ] **Step 3: Commit**

```bash
git commit -m "feat: createInitialRun copies all test case content"
```

---

### Task 3: Serializer — New Format with Body Blocks and Comment Bug Fix

**Files:**
- Modify: `src/main/kotlin/io/speqa/speqa/parser/TestRunSerializer.kt`
- Modify: `src/test/kotlin/io/speqa/speqa/parser/TestRunSerializerTest.kt`

- [ ] **Step 1: Write tests for new format**

Add tests:
```kotlin
@Test
fun `serializes priority and status`() {
    val run = TestRun(title = "Test", priority = Priority.MAJOR, status = Status.READY)
    val result = TestRunSerializer.serialize(run)
    assertTrue(result.contains("priority: major"))
    assertTrue(result.contains("status: ready"))
}

@Test
fun `serializes body blocks before steps`() {
    val run = TestRun(
        title = "Test",
        bodyBlocks = listOf(DescriptionBlock("Test description")),
        stepResults = listOf(StepResult(action = "Click")),
    )
    val result = TestRunSerializer.serialize(run)
    val descIdx = result.indexOf("Test description")
    val stepIdx = result.indexOf("1. Click")
    assertTrue("Body before steps", descIdx < stepIdx)
}

@Test
fun `serializes links`() {
    val run = TestRun(title = "Test", links = listOf(Link("Jira", "https://jira.example.com/123")))
    val result = TestRunSerializer.serialize(run)
    assertTrue(result.contains("Links:"))
    assertTrue(result.contains("[Jira](https://jira.example.com/123)"))
}

@Test
fun `serializes overall comment after steps`() {
    val run = TestRun(
        title = "Test",
        stepResults = listOf(StepResult(action = "Click", verdict = StepVerdict.PASSED)),
        comment = "Overall run comment",
    )
    val result = TestRunSerializer.serialize(run)
    val stepIdx = result.indexOf("1. Click")
    val commentIdx = result.indexOf("Overall run comment")
    assertTrue("Comment after steps", commentIdx > stepIdx)
}

@Test
fun `blank line between expected and verdict prevents merge`() {
    val run = TestRun(
        title = "Test",
        stepResults = listOf(
            StepResult(action = "Click", expected = "Page loads", verdict = StepVerdict.PASSED, comment = "Looks good"),
        ),
    )
    val result = TestRunSerializer.serialize(run)
    // Expected block, then verdict, then comment — all separated
    val lines = result.lines()
    val expectedLine = lines.indexOfFirst { it.trim().startsWith("> ") && it.contains("Page loads") }
    val verdictLine = lines.indexOfFirst { it.trim().startsWith("— ") }
    assertTrue("Expected before verdict", expectedLine < verdictLine)
}
```

- [ ] **Step 2: Rewrite serializer**

Frontmatter: add `priority`, `status`. After frontmatter, write body blocks (Description, Preconditions markdown). Then links. Then attachments. Then steps. Then overall comment.

Step format:
```
1. Action text
   > Expected line 1
   > Expected line 2
   — passed
   > Comment line 1
```

Key: blank line is NOT needed between expected and verdict — the `— verdict` line is unambiguous. The parser fix is what matters (see Task 4).

After all steps, if `comment.isNotBlank()`, write it as plain text.

```kotlin
fun serialize(testRun: TestRun): String {
    return buildString {
        // Frontmatter
        appendLine("---")
        testRun.id?.let { appendLine("id: $it") }
        appendLine("title: ${SpeqaMarkdown.quoteYamlScalar(testRun.title)}")
        testRun.priority?.let { appendLine("priority: ${it.label}") }
        testRun.status?.let { appendLine("status: ${it.label}") }
        testRun.startedAt?.let { appendLine("started_at: ${SpeqaMarkdown.quoteYamlScalar(formatter.format(it))}") }
        testRun.finishedAt?.let { appendLine("finished_at: ${SpeqaMarkdown.quoteYamlScalar(formatter.format(it))}") }
        appendLine("result: ${testRun.result.label}")
        if (testRun.manualResult) appendLine("manual_result: true")
        appendLine("environment: ${SpeqaMarkdown.quoteYamlScalar(testRun.environment)}")
        appendLine("runner: ${SpeqaMarkdown.quoteYamlScalar(testRun.runner)}")
        if (testRun.tags.isNotEmpty()) {
            appendLine("tags:")
            testRun.tags.forEach { tag -> appendLine("  - $tag") }
        }
        appendLine("---")
        appendLine()

        // Body blocks (description, preconditions)
        testRun.bodyBlocks.forEach { block ->
            when (block) {
                is DescriptionBlock -> if (block.markdown.isNotBlank()) {
                    appendLine(block.markdown)
                    appendLine()
                }
                is PreconditionsBlock -> if (block.markdown.isNotBlank()) {
                    appendLine(block.markerStyle.marker)
                    appendLine()
                    appendLine(block.markdown)
                    appendLine()
                }
            }
        }

        // Links
        if (testRun.links.isNotEmpty()) {
            appendLine("Links:")
            appendLine()
            testRun.links.forEach { link ->
                appendLine("- [${link.title}](${link.url})")
            }
            appendLine()
        }

        // Attachments (test case level)
        if (testRun.attachments.isNotEmpty()) {
            appendLine("Attachments:")
            appendLine()
            testRun.attachments.forEach { att ->
                appendLine("- ${att.path}")
            }
            appendLine()
        }

        // Steps
        if (testRun.stepResults.isNotEmpty()) {
            appendLine("Steps:")
            appendLine()
            testRun.stepResults.forEachIndexed { index, step ->
                appendStepResult(index + 1, step)
                appendLine()
            }
        }

        // Overall run comment
        if (testRun.comment.isNotBlank()) {
            appendLine(testRun.comment)
        }
    }.trimEnd() + "\n"
}

private fun StringBuilder.appendStepResult(number: Int, step: StepResult) {
    appendLine("$number. ${step.action}")
    if (step.expected.isNotBlank()) {
        step.expected.lines().forEach { line ->
            appendLine("   > $line")
        }
    }
    if (step.verdict != StepVerdict.NONE) {
        appendLine("   — ${step.verdict.label}")
    }
    if (step.comment.isNotBlank()) {
        step.comment.lines().forEach { line ->
            appendLine("   >> $line")
        }
    }
}
```

Key change: step COMMENTS use `>>` prefix (double blockquote) to distinguish from expected (`>`). This is the fix for the comment/expected merge bug.

- [ ] **Step 3: Run tests**

Run: `./gradlew test --tests "io.github.barsia.speqa.parser.TestRunSerializerTest"`

- [ ] **Step 4: Commit**

```bash
git commit -m "feat: serialize full test case content in .tr.md, use >> for step comments"
```

---

### Task 4: Parser — Parse Full Content and Fix Comment Bug

**Files:**
- Modify: `src/main/kotlin/io/speqa/speqa/parser/TestRunParser.kt`
- Modify: `src/test/kotlin/io/speqa/speqa/parser/TestRunParserTest.kt`

- [ ] **Step 1: Write test for comment bug fix**

```kotlin
@Test
fun `distinguishes expected from step comment using double blockquote`() {
    val content = """
        |---
        |title: "Test"
        |result: passed
        |---
        |
        |1. Click button
        |   > Page loads
        |   — passed
        |   >> Looks good
    """.trimMargin()
    val run = TestRunParser.parse(content)
    assertEquals("Page loads", run.stepResults[0].expected)
    assertEquals("passed", run.stepResults[0].verdict.label)
    assertEquals("Looks good", run.stepResults[0].comment)
}

@Test
fun `parses priority and status from frontmatter`() {
    val content = "---\ntitle: \"Test\"\npriority: major\nstatus: ready\nresult: passed\n---"
    val run = TestRunParser.parse(content)
    assertEquals(Priority.MAJOR, run.priority)
    assertEquals(Status.READY, run.status)
}

@Test
fun `parses overall comment after steps`() {
    val content = """
        |---
        |title: "Test"
        |result: passed
        |---
        |
        |1. Click
        |   — passed
        |
        |Overall run went well.
    """.trimMargin()
    val run = TestRunParser.parse(content)
    assertEquals("Overall run went well.", run.comment)
}

@Test
fun `parses body blocks before steps`() {
    val content = """
        |---
        |title: "Test"
        |result: passed
        |---
        |
        |This is a description.
        |
        |Preconditions:
        |
        |1. User is logged in
        |
        |1. Click button
        |   — passed
    """.trimMargin()
    val run = TestRunParser.parse(content)
    assertTrue(run.bodyBlocks.isNotEmpty())
}
```

- [ ] **Step 2: Update parser**

Key changes:
- Parse `priority`, `status` from frontmatter
- Parse body blocks (Description, Preconditions) from body — reuse `TestCaseParser` body block logic or simplified version
- Steps: `>` before verdict = expected, `>>` after verdict = comment
- After last step, remaining text (not starting with `N.`) = overall comment
- Add `COMMENT_PATTERN = Regex("""^>>\s?(.*)$""")` for step comments

Update step parsing:
```kotlin
val quoteMatch = QUOTE_PATTERN.matchEntire(trimmed)
val commentMatch = COMMENT_PATTERN.matchEntire(trimmed)
if (commentMatch != null && action != null) {
    commentLines += commentMatch.groupValues[1]
    continue
}
if (quoteMatch != null && action != null && !verdictSeen) {
    val line = quoteText.removePrefix("Expected: ").trim()
    expected = if (expected.isEmpty()) line else "$expected\n$line"
    continue
}
```

New patterns:
```kotlin
private val COMMENT_PATTERN = Regex("""^>>\s?(.*)$""")
```

- [ ] **Step 3: Run tests**

Run: `./gradlew test --tests "io.github.barsia.speqa.parser.TestRunParserTest"`

- [ ] **Step 4: Commit**

```bash
git commit -m "feat: parse full test case content from .tr.md, fix comment/expected merge bug"
```

---

### Task 5: Bundle Keys

**Files:**
- Modify: `src/main/resources/messages/SpeqaBundle.properties`

- [ ] **Step 1: Add keys**

```properties
run.comment=Run comment
run.commentPlaceholder=Add a comment about this test run
```

- [ ] **Step 2: Commit**

```bash
git commit -m "feat: add bundle keys for run comment"
```

---

### Task 6: TestRunEditor — Wire New Fields

**Files:**
- Modify: `src/main/kotlin/io/speqa/speqa/run/TestRunEditor.kt`

- [ ] **Step 1: Add mutable state for comment**

```kotlin
private var comment by mutableStateOf(initialRun.comment)
```

- [ ] **Step 2: Update saveToDocument**

Add `comment = comment` snapshot, include in `TestRun(...)` constructor. Also include `bodyBlocks`, `links`, `attachments`, `priority`, `status` from `initialRun` (readonly, just pass through).

- [ ] **Step 3: Update document listener refresh**

When document changes externally, also update `comment` from parsed run.

- [ ] **Step 4: Pass to TestRunPanel**

Add `comment`, `onCommentChange`, `bodyBlocks`, `links`, `attachments`, `priority`, `status` to panel call.

- [ ] **Step 5: Verify compilation**

Run: `./gradlew compileKotlin`

- [ ] **Step 6: Commit**

```bash
git commit -m "feat: wire full test case content through TestRunEditor"
```

---

### Task 7: TestRunPanel UI — Show Full Content

**Files:**
- Modify: `src/main/kotlin/io/speqa/speqa/run/TestRunPanel.kt`

- [ ] **Step 1: Update signature**

Add parameters:
```kotlin
priority: Priority?,
status: Status?,
bodyBlocks: List<TestCaseBodyBlock>,
links: List<Link>,
attachments: List<Attachment>,
comment: String,
onCommentChange: (String) -> Unit,
```

- [ ] **Step 2: Show description and preconditions (readonly)**

After environment/runner section, before steps, show body blocks as readonly text (not editable). Use same visual style as test case preview — section headers with `SectionHeaderWithDivider`, text content in `Text` composable.

- [ ] **Step 3: Show links (readonly)**

After body blocks, show links section if `links.isNotEmpty()`. Each link as clickable row (same as test case LinkRow but readonly).

- [ ] **Step 4: Show attachments (readonly)**

After links, show test case-level attachments if `attachments.isNotEmpty()`.

- [ ] **Step 5: Show overall run comment (editable)**

After steps section, show a `PlainTextInput` for the overall run comment with placeholder from bundle key `run.commentPlaceholder`. Always visible (unlike old Summary which was hidden when empty).

- [ ] **Step 6: Fix step number alignment**

Look at how test case `StepCard` renders the step number and match the alignment. The step number should be left-aligned like in test case.

- [ ] **Step 7: Verify compilation and test**

Run: `./gradlew compileKotlin && ./gradlew test`

- [ ] **Step 8: Commit**

```bash
git commit -m "feat: show full test case content in test run panel"
```

---

### Task 8: Final Verification

- [ ] **Step 1: Run full test suite**

Run: `./gradlew test`

- [ ] **Step 2: Run in sandbox**

Run: `./gradlew runIde`

Verify:
- Create test case with description, preconditions, links, attachments, steps with expected + attachments
- Start test run — all content appears in run panel (readonly)
- Set verdicts — result auto-calculates
- Add step comment via comment button — persists across saves
- Add overall run comment — persists
- Edit .tr.md in text editor — preview updates
- Delete verdict from text editor — preview reflects NONE
- Step numbers aligned like test case

- [ ] **Step 3: Commit cleanup if needed**
