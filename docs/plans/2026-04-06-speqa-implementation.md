# Test Run / Test Case Unification Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. The **authoritative spec** is `docs/specs/2026-04-06-speqa-design.md` — read it before starting any task.

**Goal:** Unify `TestRun` and `TestCase` around one scenario model and one step visual language, remove silent data loss, and let the user choose which metadata to import when creating a test run.

**Architecture:** Keep the run header structure, but make the run step visually match the case step: same gutter, same `action | expected` content frame, same editable metadata row. The implementation is split into five shippable slices: import-options plumbing, model/parser/serializer parity, editable run metadata, shared step layout, and verification cleanup. Each slice ends with compile/test evidence before moving on.

**Tech Stack:** Kotlin, Jetpack Compose for Desktop, Jewel, IntelliJ Platform, Gradle Kotlin DSL, JUnit 4.

---

## File structure

| File | Responsibility | First touched in |
|---|---|---|
| `docs/specs/2026-04-06-speqa-design.md` | Authoritative product/spec contract | already updated |
| `src/main/kotlin/io/github/barsia/speqa/editor/RunCreationDialog.kt` | Run creation dialog UI + request payload | Task 1 |
| `src/test/kotlin/io/github/barsia/speqa/editor/RunCreationDialogPathTest.kt` | Dialog path helpers; extend with import-option helper tests | Task 1 |
| `src/main/kotlin/io/github/barsia/speqa/editor/SpeqaEditorSupport.kt` | `startTestRun` flow | Task 1 |
| `src/main/kotlin/io/github/barsia/speqa/model/TestRun.kt` | Unified run data model | Task 2 |
| `src/main/kotlin/io/github/barsia/speqa/run/TestRunSupport.kt` | `createInitialRun` import logic + attachment rebasing | Task 2 |
| `src/main/kotlin/io/github/barsia/speqa/parser/SpeqaMarkdown.kt` | Shared scalar/list frontmatter helpers | Task 2 |
| `src/main/kotlin/io/github/barsia/speqa/parser/TestRunParser.kt` | Run parser parity | Task 2 |
| `src/main/kotlin/io/github/barsia/speqa/parser/TestRunSerializer.kt` | Run serializer parity | Task 2 |
| `src/test/kotlin/io/github/barsia/speqa/model/TestRunTest.kt` | Model defaults | Task 2 |
| `src/test/kotlin/io/github/barsia/speqa/run/TestRunSupportTest.kt` | Import-option tests | Task 2 |
| `src/test/kotlin/io/github/barsia/speqa/parser/TestRunParserTest.kt` | Parser parity tests | Task 2 |
| `src/test/kotlin/io/github/barsia/speqa/parser/TestRunSerializerTest.kt` | Serializer parity tests | Task 2 |
| `src/test/kotlin/io/github/barsia/speqa/run/TestRunAttachmentPathTest.kt` | Attachment rebase coverage | Task 2 |
| `src/main/kotlin/io/github/barsia/speqa/run/TestRunEditor.kt` | Mutable run state + save wiring | Task 3 |
| `src/main/kotlin/io/github/barsia/speqa/run/TestRunPanel.kt` | Run header + step UI | Tasks 3, 4 |
| `src/main/kotlin/io/github/barsia/speqa/editor/ui/TagCloud.kt` | Reused editable metadata UI | Task 3 |
| `src/main/kotlin/io/github/barsia/speqa/editor/ui/LinkList.kt` | Reused editable links UI | Task 3 |
| `src/main/kotlin/io/github/barsia/speqa/editor/ui/AttachmentRow.kt` | Reused attachment item UI | Task 3 |
| `src/main/kotlin/io/github/barsia/speqa/editor/ui/StepMetaRow.kt` | Shared editable tickets/links/attachments row | Task 4 |
| `src/main/kotlin/io/github/barsia/speqa/editor/ui/StepCard.kt` | Existing case-step implementation; adapt to shared frame | Task 4 |
| `src/main/kotlin/io/github/barsia/speqa/editor/ui/ScenarioStepFrame.kt` | New shared step gutter/content frame | Task 4 |
| `src/main/resources/messages/SpeqaBundle.properties` | New strings/tooltips/checkbox labels | Tasks 1, 3, 4 |

---

## Task 1: Run Creation Import Options

**Files:**
- Modify: `src/main/kotlin/io/github/barsia/speqa/editor/RunCreationDialog.kt`
- Modify: `src/main/kotlin/io/github/barsia/speqa/editor/SpeqaEditorSupport.kt`
- Modify: `src/main/resources/messages/SpeqaBundle.properties`
- Test: `src/test/kotlin/io/github/barsia/speqa/editor/RunCreationDialogPathTest.kt`

- [ ] **Step 1: Extend the request model with explicit import options**

Add a dedicated value object near `RunCreationRequest`:

```kotlin
internal data class RunImportOptions(
    val importTags: Boolean = true,
    val importEnvironment: Boolean = true,
    val importTickets: Boolean = false,
    val importLinks: Boolean = false,
    val importAttachments: Boolean = false,
)

internal data class RunCreationRequest(
    val destinationRelativePath: String,
    val fileName: String,
    val importOptions: RunImportOptions,
)
```

- [ ] **Step 2: Write a small helper test for default import values**

Append to `RunCreationDialogPathTest.kt`:

```kotlin
@Test
fun `run import options defaults match product decision`() {
    val defaults = RunImportOptions()

    assertTrue(defaults.importTags)
    assertTrue(defaults.importEnvironment)
    assertFalse(defaults.importTickets)
    assertFalse(defaults.importLinks)
    assertFalse(defaults.importAttachments)
}
```

- [ ] **Step 3: Run the focused test**

Run:

```bash
./gradlew test --tests io.github.barsia.speqa.editor.RunCreationDialogPathTest
```

Expected: FAIL with unresolved `RunImportOptions`.

- [ ] **Step 4: Add checkbox UI to `RunCreationDialog`**

In `RunCreationDialog.kt`, add five checkboxes and feed them into the request:

```kotlin
private val importTagsCheckBox = JBCheckBox(SpeqaBundle.message("dialog.createRun.import.tags"), true)
private val importEnvironmentCheckBox = JBCheckBox(SpeqaBundle.message("dialog.createRun.import.environment"), true)
private val importTicketsCheckBox = JBCheckBox(SpeqaBundle.message("dialog.createRun.import.tickets"), false)
private val importLinksCheckBox = JBCheckBox(SpeqaBundle.message("dialog.createRun.import.links"), false)
private val importAttachmentsCheckBox = JBCheckBox(SpeqaBundle.message("dialog.createRun.import.attachments"), false)
```

Use a helper to disable empty categories:

```kotlin
private fun configureImportCheckBox(
    checkBox: JBCheckBox,
    hasContent: Boolean,
    emptyTooltip: String,
) {
    checkBox.isEnabled = hasContent
    if (!hasContent) {
        checkBox.isSelected = false
        checkBox.toolTipText = emptyTooltip
    }
}
```

and build the request as:

```kotlin
RunCreationRequest(
    destinationRelativePath = relativePath,
    fileName = fileNameField.text.trim(),
    importOptions = RunImportOptions(
        importTags = importTagsCheckBox.isSelected,
        importEnvironment = importEnvironmentCheckBox.isSelected,
        importTickets = importTicketsCheckBox.isSelected,
        importLinks = importLinksCheckBox.isSelected,
        importAttachments = importAttachmentsCheckBox.isSelected,
    ),
)
```

- [ ] **Step 5: Thread source-content booleans from `startTestRun` into the dialog**

In `SpeqaEditorSupport.kt`, compute:

```kotlin
val hasTags = testCase.tags.orEmpty().isNotEmpty()
val hasEnvironment = testCase.environment.orEmpty().isNotEmpty()
val hasTickets = testCase.steps.any { it.tickets.isNotEmpty() }
val hasLinks = testCase.links.isNotEmpty() || testCase.steps.any { it.links.isNotEmpty() }
val hasAttachments = testCase.attachments.isNotEmpty() || testCase.steps.any {
    it.actionAttachments.isNotEmpty() || it.expectedAttachments.isNotEmpty()
}
```

and pass them to the dialog constructor:

```kotlin
val request = RunCreationDialog(
    project = project,
    destinationRelativePath = savedDestination,
    fileName = defaultFileName,
    hasTags = hasTags,
    hasEnvironment = hasEnvironment,
    hasTickets = hasTickets,
    hasLinks = hasLinks,
    hasAttachments = hasAttachments,
).takeIf { it.showAndGet() }?.request ?: return
```

- [ ] **Step 6: Add bundle keys for labels and empty-to-import tooltips**

Append to `SpeqaBundle.properties`:

```properties
dialog.createRun.import.section=Import Into Test Run
dialog.createRun.import.tags=Tags
dialog.createRun.import.environment=Environment
dialog.createRun.import.tickets=Tickets
dialog.createRun.import.links=Links
dialog.createRun.import.attachments=Attachments
dialog.createRun.import.tags.empty=The source test case has no tags to import
dialog.createRun.import.environment.empty=The source test case has no environment values to import
dialog.createRun.import.tickets.empty=The source test case has no step tickets to import
dialog.createRun.import.links.empty=The source test case has no links to import
dialog.createRun.import.attachments.empty=The source test case has no attachments to import
```

- [ ] **Step 7: Run compile + focused dialog tests**

Run:

```bash
./gradlew test --tests io.github.barsia.speqa.editor.RunCreationDialogPathTest
./gradlew compileKotlin
```

Expected: both succeed.

- [ ] **Step 8: Commit**

```bash
git add src/main/kotlin/io/github/barsia/speqa/editor/RunCreationDialog.kt \
        src/main/kotlin/io/github/barsia/speqa/editor/SpeqaEditorSupport.kt \
        src/main/resources/messages/SpeqaBundle.properties \
        src/test/kotlin/io/github/barsia/speqa/editor/RunCreationDialogPathTest.kt
git commit -m "feat: add run import options dialog"
```

---

## Task 2: Model, Parser, Serializer, and Import Parity

**Files:**
- Modify: `src/main/kotlin/io/github/barsia/speqa/model/TestRun.kt`
- Modify: `src/main/kotlin/io/github/barsia/speqa/run/TestRunSupport.kt`
- Modify: `src/main/kotlin/io/github/barsia/speqa/parser/SpeqaMarkdown.kt`
- Modify: `src/main/kotlin/io/github/barsia/speqa/parser/TestRunParser.kt`
- Modify: `src/main/kotlin/io/github/barsia/speqa/parser/TestRunSerializer.kt`
- Test: `src/test/kotlin/io/github/barsia/speqa/model/TestRunTest.kt`
- Test: `src/test/kotlin/io/github/barsia/speqa/run/TestRunSupportTest.kt`
- Test: `src/test/kotlin/io/github/barsia/speqa/run/TestRunAttachmentPathTest.kt`
- Test: `src/test/kotlin/io/github/barsia/speqa/parser/TestRunParserTest.kt`
- Test: `src/test/kotlin/io/github/barsia/speqa/parser/TestRunSerializerTest.kt`

- [ ] **Step 1: Write failing tests for environment list semantics and no-loss import**

Append to `TestRunSupportTest.kt`:

```kotlin
@Test
fun `createInitialRun respects import options`() {
    val testCase = TestCase(
        title = "Login",
        tags = listOf("smoke"),
        environment = listOf("test1, env20"),
        links = listOf(Link("Spec", "https://example.com/spec")),
        attachments = listOf(Attachment("attachments/top.png")),
        steps = listOf(
            TestStep(
                action = "Open",
                expected = "Opened",
                tickets = listOf("QA-1"),
                links = listOf(Link("Step", "https://example.com/step")),
                actionAttachments = listOf(Attachment("attachments/action.png")),
                expectedAttachments = listOf(Attachment("attachments/expected.png")),
            ),
        ),
    )

    val run = TestRunSupport.createInitialRun(
        testCase = testCase,
        sourceFilePath = "/tmp/case.tc.md",
        targetDirectoryPath = "/tmp/runs",
        importOptions = RunImportOptions(),
    )

    assertEquals(listOf("smoke"), run.tags)
    assertEquals(listOf("test1, env20"), run.environment)
    assertTrue(run.links.isEmpty())
    assertTrue(run.attachments.isEmpty())
    assertTrue(run.stepResults.single().tickets.isEmpty())
    assertTrue(run.stepResults.single().links.isEmpty())
    assertTrue(run.stepResults.single().actionAttachments.isEmpty())
    assertTrue(run.stepResults.single().expectedAttachments.isEmpty())
}
```

Append to `TestRunParserTest.kt`:

```kotlin
@Test
fun `environment scalar with commas stays one entry`() {
    val run = TestRunParser.parse(
        """
        ---
        title: "Login"
        environment: test1, env20
        ---
        """.trimIndent(),
    )

    assertEquals(listOf("test1, env20"), run.environment)
}
```

- [ ] **Step 2: Run the focused tests**

Run:

```bash
./gradlew test --tests io.github.barsia.speqa.run.TestRunSupportTest --tests io.github.barsia.speqa.parser.TestRunParserTest
```

Expected: FAIL because `TestRun.environment` is still `String` and `createInitialRun` lacks `importOptions`.

- [ ] **Step 3: Update the run model**

In `TestRun.kt`, change the model:

```kotlin
data class StepResult(
    val action: String = "",
    val expected: String = "",
    val tickets: List<String> = emptyList(),
    val links: List<Link> = emptyList(),
    val verdict: StepVerdict = StepVerdict.NONE,
    val comment: String = "",
    val actionAttachments: List<Attachment> = emptyList(),
    val expectedAttachments: List<Attachment> = emptyList(),
)

data class TestRun(
    val id: Int? = null,
    val title: String = "",
    val tags: List<String> = emptyList(),
    val priority: Priority? = null,
    val manualResult: Boolean = false,
    val startedAt: LocalDateTime? = null,
    val finishedAt: LocalDateTime? = null,
    val result: RunResult = RunResult.NOT_STARTED,
    val environment: List<String> = emptyList(),
    val runner: String = "",
    val bodyBlocks: List<TestCaseBodyBlock> = emptyList(),
    val links: List<Link> = emptyList(),
    val attachments: List<Attachment> = emptyList(),
    val comment: String = "",
    val stepResults: List<StepResult> = emptyList(),
)
```

- [ ] **Step 4: Add shared scalar/list helpers for frontmatter**

In `SpeqaMarkdown.kt`, add:

```kotlin
fun appendStringListField(
    builder: StringBuilder,
    key: String,
    values: List<String>,
) {
    if (values.isEmpty()) return
    if (values.size == 1) {
        builder.appendLine("$key: ${quoteYamlScalar(values.single())}")
    } else {
        builder.appendLine("$key:")
        values.forEach { builder.appendLine("  - ${quoteYamlScalar(it)}") }
    }
}
```

and keep `parseStringList` unchanged for scalar-or-list parsing.

- [ ] **Step 5: Make `createInitialRun` explicit and lossless**

In `TestRunSupport.kt`, change the signature and field mapping:

```kotlin
fun createInitialRun(
    testCase: TestCase,
    sourceFilePath: String,
    targetDirectoryPath: String,
    importOptions: RunImportOptions,
    runner: String = defaultRunner(),
): TestRun {
    return TestRun(
        title = testCase.title,
        tags = if (importOptions.importTags) testCase.tags.orEmpty() else emptyList(),
        priority = testCase.priority,
        environment = if (importOptions.importEnvironment) testCase.environment.orEmpty() else emptyList(),
        runner = runner,
        bodyBlocks = testCase.bodyBlocks,
        links = if (importOptions.importLinks) testCase.links else emptyList(),
        attachments = if (importOptions.importAttachments) {
            rebaseAttachments(testCase.attachments, sourceFilePath, targetDirectoryPath)
        } else {
            emptyList()
        },
        stepResults = testCase.steps.map { step ->
            StepResult(
                action = step.action,
                expected = step.expected.orEmpty(),
                tickets = if (importOptions.importTickets) step.tickets else emptyList(),
                links = if (importOptions.importLinks) step.links else emptyList(),
                actionAttachments = if (importOptions.importAttachments) {
                    rebaseAttachments(step.actionAttachments, sourceFilePath, targetDirectoryPath)
                } else {
                    emptyList()
                },
                expectedAttachments = if (importOptions.importAttachments) {
                    rebaseAttachments(step.expectedAttachments, sourceFilePath, targetDirectoryPath)
                } else {
                    emptyList()
                },
            )
        },
    )
}
```

- [ ] **Step 6: Update parser + serializer to round-trip the unified model**

In `TestRunParser.kt`, parse:

```kotlin
tags = SpeqaMarkdown.parseStringList(meta["tags"]),
environment = SpeqaMarkdown.parseStringList(meta["environment"]),
```

and inside `parseStepResults`, collect `links`, `actionAttachments`, and `expectedAttachments` separately, mirroring `TestCaseParser`.

In `TestRunSerializer.kt`, emit:

```kotlin
SpeqaMarkdown.appendStringListField(this, "environment", testRun.environment)
SpeqaMarkdown.appendStringListField(this, "tags", testRun.tags)
```

and inside `appendStepResult`:

```kotlin
if (step.tickets.isNotEmpty()) {
    appendLine()
    appendLine("   Ticket: ${step.tickets.joinToString(", ")}")
}
if (step.links.isNotEmpty()) {
    val rendered = step.links.joinToString(", ") { link ->
        if (link.title.isBlank()) link.url else "[${link.title}](${link.url})"
    }
    appendLine("   Links: $rendered")
}
step.actionAttachments.forEach { att ->
    append("   ")
    appendAttachment(att)
}
step.expectedAttachments.forEach { att ->
    append("   ")
    appendAttachment(att)
}
```

- [ ] **Step 7: Update assertions in run tests**

Adjust tests to the new list model:

```kotlin
assertEquals(emptyList<String>(), TestRun().environment)
```

and add one serializer round-trip test:

```kotlin
@Test
fun `round trip preserves step links and split attachments`() {
    val original = TestRun(
        title = "Round trip",
        environment = listOf("test1, env20"),
        stepResults = listOf(
            StepResult(
                action = "Open",
                expected = "Opened",
                tickets = listOf("QA-1"),
                links = listOf(Link("Spec", "https://example.com/spec")),
                actionAttachments = listOf(Attachment("a.png")),
                expectedAttachments = listOf(Attachment("b.png")),
            ),
        ),
    )

    val parsed = TestRunParser.parse(TestRunSerializer.serialize(original))
    assertEquals(original.environment, parsed.environment)
    assertEquals(original.stepResults, parsed.stepResults)
}
```

- [ ] **Step 8: Run focused tests, then full parser/model run suite**

Run:

```bash
./gradlew test --tests io.github.barsia.speqa.model.TestRunTest \
               --tests io.github.barsia.speqa.run.TestRunSupportTest \
               --tests io.github.barsia.speqa.run.TestRunAttachmentPathTest \
               --tests io.github.barsia.speqa.parser.TestRunParserTest \
               --tests io.github.barsia.speqa.parser.TestRunSerializerTest
./gradlew compileKotlin
```

Expected: all selected tests pass, compile succeeds.

- [ ] **Step 9: Commit**

```bash
git add src/main/kotlin/io/github/barsia/speqa/model/TestRun.kt \
        src/main/kotlin/io/github/barsia/speqa/run/TestRunSupport.kt \
        src/main/kotlin/io/github/barsia/speqa/parser/SpeqaMarkdown.kt \
        src/main/kotlin/io/github/barsia/speqa/parser/TestRunParser.kt \
        src/main/kotlin/io/github/barsia/speqa/parser/TestRunSerializer.kt \
        src/test/kotlin/io/github/barsia/speqa/model/TestRunTest.kt \
        src/test/kotlin/io/github/barsia/speqa/run/TestRunSupportTest.kt \
        src/test/kotlin/io/github/barsia/speqa/run/TestRunAttachmentPathTest.kt \
        src/test/kotlin/io/github/barsia/speqa/parser/TestRunParserTest.kt \
        src/test/kotlin/io/github/barsia/speqa/parser/TestRunSerializerTest.kt
git commit -m "feat: unify test run scenario model"
```

---

## Task 3: Editable Run Metadata and Top-Level Collections

**Files:**
- Modify: `src/main/kotlin/io/github/barsia/speqa/run/TestRunEditor.kt`
- Modify: `src/main/kotlin/io/github/barsia/speqa/run/TestRunPanel.kt`
- Modify: `src/main/resources/messages/SpeqaBundle.properties`

- [ ] **Step 1: Write a failing parser/serializer state test for editable run metadata**

Append to `TestRunSerializerTest.kt`:

```kotlin
@Test
fun `serialize writes single environment scalar and multiple tags list`() {
    val run = TestRun(
        title = "Run",
        environment = listOf("test1, env20"),
        tags = listOf("smoke", "auth"),
    )

    val result = TestRunSerializer.serialize(run)

    assertTrue(result.contains("environment: \"test1, env20\""))
    assertTrue(result.contains("tags:"))
    assertTrue(result.contains("  - \"smoke\""))
}
```

- [ ] **Step 2: Run the focused serializer test**

Run:

```bash
./gradlew test --tests io.github.barsia.speqa.parser.TestRunSerializerTest
```

Expected: FAIL until serializer and state wiring are updated.

- [ ] **Step 3: Promote run metadata from `initialRun` constants to mutable state**

In `TestRunEditor.kt`, add/edit:

```kotlin
private var tags by mutableStateOf(initialRun.tags)
private var environment by mutableStateOf(initialRun.environment)
private var links by mutableStateOf(initialRun.links)
private var attachments by mutableStateOf(initialRun.attachments)
```

and in the refresh timer:

```kotlin
tags = parsed.tags
environment = parsed.environment
links = parsed.links
attachments = parsed.attachments
```

- [ ] **Step 4: Save live run metadata instead of `initialRun` snapshots**

In `saveToDocument()`:

```kotlin
val run = TestRun(
    id = snapshotRunId,
    title = snapshotTitle,
    tags = snapshotTags,
    priority = initialRun.priority,
    manualResult = snapshotManualResult,
    startedAt = snapshotStartedAt,
    finishedAt = snapshotFinishedAt,
    result = if (snapshotManualResult) snapshotOverriddenResult else autoResult,
    environment = snapshotEnvironment,
    runner = snapshotRunner,
    bodyBlocks = initialRun.bodyBlocks,
    links = snapshotLinks,
    attachments = snapshotAttachments,
    comment = snapshotComment,
    stepResults = snapshotStepResults,
)
```

- [ ] **Step 5: Replace run header metadata UI with editable collections**

In `TestRunPanel.kt`, replace the old `String` environment field usage with:

```kotlin
TagCloud(
    tags = environment,
    allKnownTags = emptyList(),
    onTagsChange = onEnvironmentChange,
    label = SpeqaBundle.message("label.environment"),
    addItemLabel = SpeqaBundle.message("environment.add"),
    coloredChips = true,
)
```

and render editable run tags with the same `TagCloud` path rather than read-only chips.

- [ ] **Step 6: Make top-level links and attachments editable in the run**

In `TestRunPanel.kt`, use:

```kotlin
LinkList(
    links = links,
    onLinksChange = onLinksChange,
    project = project,
)
```

and an editable attachment section that mirrors the test-case preview’s add/remove path, with callbacks:

```kotlin
onAttachmentsChange = { next ->
    attachments = next
    saveToDocument()
}
```

- [ ] **Step 7: Add any missing bundle keys**

If the run header needs a different add label or tooltip, add only the missing keys. Do not hardcode UI text in Kotlin.

- [ ] **Step 8: Run compile + serializer tests**

Run:

```bash
./gradlew test --tests io.github.barsia.speqa.parser.TestRunSerializerTest
./gradlew compileKotlin
```

Expected: both succeed.

- [ ] **Step 9: Commit**

```bash
git add src/main/kotlin/io/github/barsia/speqa/run/TestRunEditor.kt \
        src/main/kotlin/io/github/barsia/speqa/run/TestRunPanel.kt \
        src/main/resources/messages/SpeqaBundle.properties \
        src/test/kotlin/io/github/barsia/speqa/parser/TestRunSerializerTest.kt
git commit -m "feat: make run metadata editable"
```

---

## Task 4: Shared Step Frame and Visual Parity

**Files:**
- Create: `src/main/kotlin/io/github/barsia/speqa/editor/ui/ScenarioStepFrame.kt`
- Modify: `src/main/kotlin/io/github/barsia/speqa/editor/ui/StepMetaRow.kt`
- Modify: `src/main/kotlin/io/github/barsia/speqa/editor/ui/StepCard.kt`
- Modify: `src/main/kotlin/io/github/barsia/speqa/run/TestRunPanel.kt`
- Modify: `src/main/resources/messages/SpeqaBundle.properties`

- [ ] **Step 1: Add a pure layout frame for shared step rendering**

Create `ScenarioStepFrame.kt`:

```kotlin
@Composable
internal fun ScenarioStepFrame(
    index: Int,
    narrow: Boolean,
    modifier: Modifier = Modifier,
    gutterContent: @Composable ColumnScope.() -> Unit,
    actionContent: @Composable () -> Unit,
    expectedContent: @Composable () -> Unit,
    metaContent: @Composable () -> Unit,
    footerContent: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(start = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(SpeqaLayout.s2),
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            modifier = Modifier.width(SpeqaLayout.s5).padding(top = 1.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(SpeqaLayout.s1),
            content = gutterContent,
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (narrow) {
                Column(verticalArrangement = Arrangement.spacedBy(SpeqaLayout.s2)) {
                    actionContent()
                    expectedContent()
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(SpeqaLayout.s5),
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(modifier = Modifier.weight(1f)) { actionContent() }
                    Column(modifier = Modifier.weight(1f)) { expectedContent() }
                }
            }

            metaContent()
            footerContent?.invoke()
        }
    }
}
```

- [ ] **Step 2: Run compile to prove the new frame is syntactically valid**

Run:

```bash
./gradlew compileKotlin
```

Expected: PASS with the new file unused.

- [ ] **Step 3: Move `StepCard` onto the shared frame**

In `StepCard.kt`, replace the current duplicated gutter/content structure with:

```kotlin
ScenarioStepFrame(
    index = index,
    narrow = narrow,
    modifier = modifier
        .hoverable(stepHoverSource)
        .onFocusChanged { isStepFocused = it.hasFocus }
        .accentBar(active = isStepFocused || isDragging),
    gutterContent = {
        Text(
            text = (index + 1).toString().padStart(2, '0'),
            color = SpeqaThemeColors.mutedForeground,
            fontSize = SpeqaTypography.numericFontSize,
            fontWeight = SpeqaTypography.numericWeight,
            letterSpacing = SpeqaTypography.numericTracking,
        )
        DragHandleOrSpacer(...)
    },
    actionContent = { ActionEditor(...) },
    expectedContent = { ExpectedEditor(...) },
    metaContent = {
        StepMetaRow(...)
    },
)
```

- [ ] **Step 4: Make `StepResultRow` use the same frame**

In `TestRunPanel.kt`, inside `StepResultRow`, replace the custom vertical layout with:

```kotlin
ScenarioStepFrame(
    index = index,
    narrow = maxWidth < 440.dp,
    modifier = Modifier
        .fillMaxWidth()
        .drawBehind { if (barColor != Color.Transparent) drawRect(barColor, topLeft = Offset.Zero, size = Size(2.dp.toPx(), size.height)) }
        .padding(top = SpeqaLayout.blockGap, bottom = SpeqaLayout.blockGap),
    gutterContent = {
        Text(
            text = (index + 1).toString().padStart(2, '0'),
            color = SpeqaThemeColors.mutedForeground,
            fontSize = SpeqaTypography.numericFontSize,
            fontWeight = SpeqaTypography.numericWeight,
            letterSpacing = SpeqaTypography.numericTracking,
        )
    },
    actionContent = {
        MarkdownText(step.action.ifBlank { SpeqaBundle.message("run.emptyStep") }, ...)
    },
    expectedContent = {
        if (step.expected.isNotBlank()) {
            Column(verticalArrangement = Arrangement.spacedBy(SpeqaLayout.tightGap)) {
                Text(SpeqaBundle.message("label.expectedResult"), ...)
                MarkdownText(step.expected, ...)
            }
        }
    },
    metaContent = {
        StepMetaRow(
            stepIndex = index,
            tickets = step.tickets,
            links = step.links,
            actionAttachments = step.actionAttachments,
            expectedAttachments = step.expectedAttachments,
            ...
        )
    },
    footerContent = {
        RunExecutionControls(...)
    },
)
```

- [ ] **Step 5: Extend `StepMetaRow` to work in both test case and test run**

Make `StepMetaRow` generic over callbacks rather than hard-wired to case editing. Its public shape should look like:

```kotlin
internal fun StepMetaRow(
    stepIndex: Int,
    tickets: List<String>,
    links: List<Link>,
    actionAttachments: List<Attachment>,
    expectedAttachments: List<Attachment>,
    project: Project?,
    tcFile: VirtualFile?,
    onTicketsChange: (List<String>) -> Unit,
    onLinksChange: (List<Link>) -> Unit,
    onActionAttachmentsChange: (List<Attachment>) -> Unit,
    onExpectedAttachmentsChange: (List<Attachment>) -> Unit,
    onOpenFile: (Attachment) -> Unit,
    ...
)
```

No case-specific assumptions inside it.

- [ ] **Step 6: Add a focused UI regression test or pure layout smoke if possible**

If there is no Compose UI test harness yet, add a narrow pure test for the breakpoint helper:

```kotlin
@Test
fun `run and case share same step breakpoint`() {
    assertTrue(440.dp.value >= 440f)
}
```

If that feels too artificial, skip adding a fake test and rely on `compileKotlin` + manual smoke; note the reason in the commit message.

- [ ] **Step 7: Manual smoke checkpoint**

Run:

```bash
./gradlew runIde
```

Manual check:
- open a `.tc.md`
- open/create a `.tr.md`
- confirm the scenario step uses the same two-column frame in both
- confirm `action` / `expected` are readonly in the run
- confirm tickets / links / attachments are editable in the run

- [ ] **Step 8: Run compile**

```bash
./gradlew compileKotlin
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 9: Commit**

```bash
git add src/main/kotlin/io/github/barsia/speqa/editor/ui/ScenarioStepFrame.kt \
        src/main/kotlin/io/github/barsia/speqa/editor/ui/StepMetaRow.kt \
        src/main/kotlin/io/github/barsia/speqa/editor/ui/StepCard.kt \
        src/main/kotlin/io/github/barsia/speqa/run/TestRunPanel.kt \
        src/main/resources/messages/SpeqaBundle.properties
git commit -m "feat: unify run and case step layout"
```

---

## Task 5: End-to-End Verification and Regression Net

**Files:**
- Modify: `src/test/kotlin/io/github/barsia/speqa/parser/TestRunParserTest.kt`
- Modify: `src/test/kotlin/io/github/barsia/speqa/parser/TestRunSerializerTest.kt`
- Modify: `src/test/kotlin/io/github/barsia/speqa/run/TestRunSupportTest.kt`
- Modify: `docs/specs/2026-04-06-speqa-design.md` only if implementation forces a spec correction

- [ ] **Step 1: Add one full creation-flow regression test**

In `TestRunSupportTest.kt`, add:

```kotlin
@Test
fun `default import options copy only tags and environment`() {
    val testCase = TestCase(
        title = "Login",
        tags = listOf("smoke"),
        environment = listOf("test1, env20"),
        links = listOf(Link("Spec", "https://example.com/spec")),
        attachments = listOf(Attachment("top.png")),
        steps = listOf(
            TestStep(
                action = "Open",
                expected = "Opened",
                tickets = listOf("QA-1"),
                links = listOf(Link("Step", "https://example.com/step")),
                actionAttachments = listOf(Attachment("action.png")),
                expectedAttachments = listOf(Attachment("expected.png")),
            ),
        ),
    )

    val run = TestRunSupport.createInitialRun(
        testCase = testCase,
        sourceFilePath = "/tmp/login.tc.md",
        targetDirectoryPath = "/tmp/runs",
        importOptions = RunImportOptions(),
    )

    assertEquals(listOf("smoke"), run.tags)
    assertEquals(listOf("test1, env20"), run.environment)
    assertTrue(run.links.isEmpty())
    assertTrue(run.attachments.isEmpty())
    assertEquals("Open", run.stepResults.single().action)
    assertEquals("Opened", run.stepResults.single().expected)
}
```

- [ ] **Step 2: Add one full serializer/parser regression**

In `TestRunParserTest.kt`, add:

```kotlin
@Test
fun `run round trip preserves editable metadata and readonly scenario`() {
    val original = TestRun(
        title = "Login",
        tags = listOf("smoke"),
        environment = listOf("test1, env20"),
        links = listOf(Link("Spec", "https://example.com/spec")),
        attachments = listOf(Attachment("top.png")),
        stepResults = listOf(
            StepResult(
                action = "Open",
                expected = "Opened",
                tickets = listOf("QA-1"),
                links = listOf(Link("Step", "https://example.com/step")),
                actionAttachments = listOf(Attachment("action.png")),
                expectedAttachments = listOf(Attachment("expected.png")),
            ),
        ),
    )

    val parsed = TestRunParser.parse(TestRunSerializer.serialize(original))
    assertEquals(original, parsed)
}
```

- [ ] **Step 3: Run targeted tests**

Run:

```bash
./gradlew test --tests io.github.barsia.speqa.run.TestRunSupportTest \
               --tests io.github.barsia.speqa.parser.TestRunParserTest \
               --tests io.github.barsia.speqa.parser.TestRunSerializerTest
```

Expected: PASS.

- [ ] **Step 4: Run full baseline verification**

Run:

```bash
./gradlew compileKotlin
./gradlew test
```

Expected: `BUILD SUCCESSFUL`.
If `SpeqaTagRegistryIndexTest` is the only flaky failure, re-run once and document it; any other failure blocks completion.

- [ ] **Step 5: Final manual smoke**

Run:

```bash
./gradlew runIde
```

Manual checklist:
- create run from case with all five checkbox states visible
- verify empty categories are disabled with tooltips
- verify defaults: tags/env on; tickets/links/attachments off
- create one run with defaults, one with everything on
- verify `.tr.md` contents match the selected import set
- edit run tags/environment/top-level links/top-level attachments
- edit step tickets/links/attachments
- verify `action` and `expected` stay read-only
- verify case/run step parity on desktop width and narrow width

- [ ] **Step 6: Commit**

```bash
git add src/test/kotlin/io/github/barsia/speqa/run/TestRunSupportTest.kt \
        src/test/kotlin/io/github/barsia/speqa/parser/TestRunParserTest.kt \
        src/test/kotlin/io/github/barsia/speqa/parser/TestRunSerializerTest.kt \
        docs/specs/2026-04-06-speqa-design.md
git commit -m "test: cover unified test run behavior"
```

---

## Self-review

**Spec coverage**
- Run import checkboxes with empty-state tooltips: Task 1
- Environment one-type contract and scalar semantics: Task 2
- No silent loss for tickets/links/attachments: Task 2
- Editable run metadata (`tags`, `environment`, top-level links/attachments): Task 3
- Full visual parity of step body: Task 4
- Verification and end-to-end regression net: Task 5

**Placeholder scan**
- No `TODO`, `TBD`, or “handle appropriately” placeholders remain.
- Every code-changing task includes concrete snippets and exact file paths.
- Every verification step includes an exact command and expected outcome.

**Type consistency**
- `TestRun.environment` is `List<String>` across model, parser, serializer, editor, and UI.
- `StepResult` carries `tickets`, `links`, `actionAttachments`, and `expectedAttachments`.
- `RunCreationRequest` consistently contains `RunImportOptions`.

**Open implementation note**
- If shared `StepMetaRow` reuse turns out to be too entangled with case-only code, split the row into `ScenarioStepMetaRow` and keep `StepMetaRow` as a thin case wrapper. Do not keep duplicated layout logic in `StepCard` and `StepResultRow`.

## Execution Handoff

Plan complete and saved to `docs/plans/2026-04-06-speqa-implementation.md`.

Two execution options:

**1. Subagent-Driven (recommended)** - I dispatch a fresh subagent per task, review between tasks, fast iteration

**2. Inline Execution** - Execute tasks in this session using executing-plans, batch execution with checkpoints

Which approach?
