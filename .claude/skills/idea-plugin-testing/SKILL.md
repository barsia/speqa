---
name: idea-plugin-testing
description: Use when writing, organizing, or reviewing automated tests for a Kotlin/Java IntelliJ Platform plugin - JUnit tests, BasePlatformTestCase/LightPlatformTestCase/HeavyPlatformTestCase fixtures, annotator/inspection/PSI/action/file-template tests, deciding what to extract as pure logic vs run on the platform, what to mock inside a platform fixture, testing bundled jar resources, flaky resource-loading in the test classpath, or judging whether a new test is worth writing at all.
user-invocable: true
---

# IntelliJ Plugin Testing (Kotlin + JUnit)

## Overview

Two principles drive every testing decision here:

1. **Push logic down to where it can be tested without the platform.** A pure function tested with plain JUnit runs in milliseconds, never flakes, and needs no IDE. Most "plugin logic" (parsing, serializing, aggregating, decision rules) does not actually need the platform - it only *looks* like it does because it lives next to platform code. Extract it.
2. **Cover the gap, not the line.** A test earns its place by failing when a real invariant breaks. Tests written for a coverage number (re-asserting what three other tests already prove) cost maintenance and prove nothing. One test on the only untested branch beats ten redundant ones.

The platform test framework is JUnit (JUnit 4 idioms - `BasePlatformTestCase` extends it) plus IntelliJ fixtures. Reach for a fixture only for behavior that genuinely needs a live project, VFS, PSI, editor, or the daemon.

## Pick the lightest level that proves the behavior

Climb this ladder and stop at the first rung that can actually exercise the behavior. Lower is faster, less flaky, and clearer.

| Level | Base class / harness | Use for | Cost |
|-------|----------------------|---------|------|
| 1. Pure unit | none (plain JUnit) | parsers, serializers, patch-offset math, result aggregation, decision functions, anything you can express as `input -> output` | ~ms, never flakes |
| 2. Light fixture | `BasePlatformTestCase` / `LightPlatformTestCase` | annotators, inspections, PSI, file templates, actions, Document/VFS, quick fixes, completion | in-memory project, ~100ms |
| 3. Heavy fixture | `HeavyPlatformTestCase` | real on-disk project, multiple modules, SDK/roots, project-model changes | slow; avoid unless required |
| 4. Real IDE | `runIde` sandbox + a UI driver | true end-to-end UI you cannot reach below | very slow; reserve for a few smoke flows |

**The single most valuable habit:** before writing a fixture test, ask "what's the pure decision here?" and extract it. Examples from this codebase that started life tangled in platform code and became plain-JUnit tests once extracted:

- `computeDuplicateIdRenumberPlan(entries)` / `computeDuplicateIdReview(entries)` - pure list-in, plan-out; no `Project`, no index.
- `TestRunSupport.deriveRunResult(steps)` / `aggregateResult(cases)` / `recomputeCaseResult(case)` - verdict math.
- `nextFocusTargetAfterDelete(index, sizeBefore)` - the focus-restore decision, separated from the Swing component.
- `isKeyboardFocusCause(cause)` - the focus-visible decision, separated from the painting.

Each is a one-liner test (`assertEquals(expected, fn(input))`) and covers the logic that actually breaks.

## Light fixtures: BasePlatformTestCase

The fixture is your in-memory project. It **gives** you a real `project`, an in-memory VFS + PSI, a `myFixture` handle, and runs the real daemon. You almost never construct platform objects yourself.

```kotlin
class SpeqaAnnotatorTest : BasePlatformTestCase() {
    override fun getTestDataPath(): String = "src/test/resources/testData/annotator"

    fun `test valid title shows no title warning`() {
        myFixture.configureByFile("validTitle.tc.md")          // loads a fixture file into the in-memory editor
        val highlights = myFixture.doHighlighting(HighlightSeverity.WARNING)  // runs the annotator
        val titleWarnings = highlights.filter { it.description?.contains("title is not set") == true }
        assertTrue(titleWarnings.isEmpty())
    }
}
```

Key handles: `configureByText(fileName, text)` or `configureByFile(name)` (relative to `getTestDataPath()`), `doHighlighting(severity)` for annotators/inspections, `myFixture.editor`/`file`/`project`, `myFixture.type(...)`, `launchAction(...)` for quick fixes/intentions.

### What to mock (and what not to)

- **Do not mock the platform.** The project, VFS, PSI, editor, and `FileBasedIndex` come from the fixture and behave correctly. Mocking them fakes the very thing under test.
- **Replace your own services** when a test needs a controlled one. Use `project.replaceService(Foo::class.java, fake, testRootDisposable)` (or `ApplicationManager.getApplication().replaceService(...)`), tied to the test disposable so it is torn down. Prefer a small real/fake implementation over a deep mock.
- **Keep `@Service` logic thin** so most of it can be tested at level 1 without a fixture at all.
- **`DumbService`/indexing:** index-backed queries return empty during dumb mode by design - a fixture-level test that depends on an index must let indexing finish, or you test the pure query logic at level 1 instead.

## Testing bundled jar resources

A plugin that bundles templates/skills into the jar (e.g. a New Project wizard starter) should be guarded by a test that the resource is actually on the classpath and parses - otherwise a broken `processResources` wiring ships silently.

```kotlin
@Test
fun `bundled starter is on the classpath and parses`() {
    val stream = SpeqaProjectScaffold::class.java
        .getResourceAsStream("/templates/${SpeqaProjectScaffold.BUNDLED_SAMPLE_RESOURCE}")
    assertNotNull("check the processResources 'from' rule", stream)
    val parsed = TestCaseParser.parse(stream!!.readBytes().toString(Charsets.UTF_8))
    assertEquals(1, parsed.id)
}
```

**Hard-won caveat - Gradle `rename {}` is flaky with the test classpath.** Bundling a resource with `from(file) { into(...); rename { "x" } }` produces the renamed file in `build/resources/main`, yet the test classloader intermittently fails to find it (clean build fails, a rerun passes). Bundle the resource **as-is, with no `rename`** - copy `from(file) { into(...) }` and let the source carry the final name. If you need a file that the running plugin must NOT also index (e.g. a `.tc.md` template that would collide with real ids in a dogfooded repo), give the source a non-indexed suffix like `foo.tc.md.template`, bundle it unchanged, and strip the suffix when the plugin writes it into the user's project. This avoids both the flake and the self-indexing.

## Cover the gap, not the line

Before adding a test, find the branch or invariant that nothing else proves. Read the existing tests first.

Worked example: `TestRunSupport`'s result math had 15 tests across `deriveRunResult` and `aggregateResult` - thorough. But `recomputeCaseResult` (re-derive a case's result from its steps *unless it was manually overridden*) had none. The valuable test is the one that pins the override branch with a case where the steps disagree:

```kotlin
@Test
fun `recomputeCaseResult keeps a manual override even when the steps disagree`() {
    val case = RunCase(caseId = 1, stepResults = listOf(StepResult(verdict = StepVerdict.FAILED)),
                       result = RunResult.PASSED, manualResult = true)
    assertEquals(RunResult.PASSED, TestRunSupport.recomputeCaseResult(case).result)   // override survives
}
```

The "disagree" setup is mandatory, not incidental: if the steps agreed with the override, the test could not tell a preserved override from a re-derived one - it would pass whether the logic was right or broken.

## UI behavior you cannot reach below

Most UI logic *can* be pulled down: keyboard-focus decisions, list-mutation focus targets, and verdict aggregation are all level-1 functions here. Genuinely visual/interactive behavior that survives only end-to-end (real focus traversal, painting, drag) belongs at level 4 - a `runIde` sandbox driven by a UI test harness (IntelliJ's UI test framework / driver). It is slow and brittle, so keep it to a few smoke paths and prove everything else lower. (Manual `.tc.md` smoke cases are the pragmatic stand-in until a real-IDE harness exists - see speqa-test-cases.)

## Common mistakes

| Mistake | Fix |
|---------|-----|
| Writing a `BasePlatformTestCase` for logic that is just `input -> output` | Extract a pure function, test at level 1 |
| Mocking `Project`/VFS/PSI inside a fixture | Use the fixture's real ones; only replace your own services |
| `HeavyPlatformTestCase` "to be safe" | Use light unless you need on-disk modules/SDK |
| Bundling a jar resource with `rename {}` then a flaky guard test | Bundle as-is; use a `.template` suffix + strip-on-install if it must stay out of the index |
| Adding a test to raise coverage % | Find the untested branch/invariant; if none, don't add it |
| Index query returns empty in a fixture test | It is dumb-mode; finish indexing, or test the pure query logic at level 1 |
