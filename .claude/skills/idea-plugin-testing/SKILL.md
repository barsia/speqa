---
name: idea-plugin-testing
description: Use when writing, organizing, or reviewing automated tests for a Kotlin/Java IntelliJ Platform plugin - JUnit tests, BasePlatformTestCase/LightPlatformTestCase/HeavyPlatformTestCase fixtures, annotator/inspection/PSI/action/file-template tests, deciding what to extract as pure logic vs run on the platform, what to mock inside a platform fixture, testing bundled jar resources, flaky resource-loading in the test classpath, or judging whether a new test is worth writing at all.
user-invocable: true
---

# IntelliJ Plugin Testing (Kotlin + JUnit)

## Overview

Two principles drive every decision:

1. **Push logic down until it needs no platform.** A pure function tested with plain JUnit runs in milliseconds and never flakes. Most "plugin logic" (parsing, serializing, aggregating, decision rules) only *looks* platform-bound because it lives next to platform code - extract it.
2. **Cover the gap, not the line.** A test earns its place by failing when a real invariant breaks. Re-asserting what other tests already prove costs maintenance and proves nothing; one test on the only untested branch beats ten redundant ones.

The harness is JUnit 4 (`BasePlatformTestCase` extends it) plus IntelliJ fixtures. Reach for a fixture only for behavior that genuinely needs a live project, VFS, PSI, editor, or daemon.

## Pick the lightest level that proves the behavior

Stop at the first rung that can exercise the behavior - lower is faster and less flaky.

| Level | Harness | Use for | Cost |
|-------|---------|---------|------|
| 1. Pure unit | plain JUnit | parsers, serializers, patch-offset math, result aggregation, decision functions - anything `input -> output` | ~ms, never flakes |
| 2. Light fixture | `BasePlatformTestCase` / `LightPlatformTestCase` | annotators, inspections, PSI, file templates, actions, Document/VFS, quick fixes, completion | in-memory project, ~100ms |
| 3. Heavy fixture | `HeavyPlatformTestCase` | real on-disk project, multiple modules, SDK/roots | slow; avoid unless required |
| 4. Real IDE | `runIde` sandbox + UI driver | end-to-end UI you can't reach below | very slow; a few smoke flows only |

**Most valuable habit: before writing a fixture test, ask "what's the pure decision here?" and extract it.** Real examples that became plain-JUnit tests once pulled out of platform code: `computeDuplicateIdRenumberPlan` / `computeDuplicateIdReview` (list in, plan out), `TestRunSupport.deriveRunResult`/`aggregateResult`/`recomputeCaseResult` (verdict math), `nextFocusTargetAfterDelete` (focus-restore decision), `isKeyboardFocusCause` (focus-visible decision). Each is a one-line `assertEquals` and covers the logic that actually breaks.

## Light fixtures: BasePlatformTestCase

The fixture IS your in-memory project: it gives you a real `project`, in-memory VFS + PSI, a `myFixture` handle, and the real daemon. You rarely construct platform objects yourself.

```kotlin
class SpeqaAnnotatorTest : BasePlatformTestCase() {
    override fun getTestDataPath() = "src/test/resources/testData/annotator"

    fun `test valid title shows no warning`() {
        myFixture.configureByFile("validTitle.tc.md")                      // load into the in-memory editor
        val warnings = myFixture.doHighlighting(HighlightSeverity.WARNING) // run the annotator
        assertTrue(warnings.none { it.description?.contains("title is not set") == true })
    }
}
```

Handles: `configureByText`/`configureByFile` (relative to `getTestDataPath()`), `doHighlighting(severity)`, `myFixture.editor`/`file`/`project`/`type(...)`, `launchAction(...)` for fixes/intentions.

**What to mock:**
- **Not the platform.** Project, VFS, PSI, editor, `FileBasedIndex` come from the fixture and behave correctly - mocking them fakes the thing under test.
- **Your own services**, when needed: `project.replaceService(Foo::class.java, fake, testRootDisposable)` (or on the application), tied to the test disposable. Prefer a small fake over a deep mock; keep `@Service` logic thin so most of it tests at level 1.
- **Indexing/dumb mode:** index-backed queries return empty during indexing by design - let indexing finish, or test the pure query at level 1.

## Bundled jar resources

A plugin that bundles a template/skill into the jar should guard that it's on the classpath and parses, or a broken `processResources` ships silently:

```kotlin
@Test fun `bundled starter is on the classpath and parses`() {
    val stream = Scaffold::class.java.getResourceAsStream("/templates/${Scaffold.BUNDLED_RESOURCE}")
    assertNotNull("check the processResources 'from' rule", stream)
    assertEquals(1, TestCaseParser.parse(stream!!.readBytes().toString(Charsets.UTF_8)).id)
}
```

**Caveat:** Gradle `rename {}` is flaky with the test classpath (clean build fails to find the renamed resource, a rerun finds it). Bundle the file under its final name, no `rename`. If it must NOT be indexed by your own plugin (a `.tc.md` template that would collide with real ids), give the source a `.template` suffix, bundle unchanged, and strip it on install.

## Cover the gap, not the line

Read the existing tests first; add the one that pins a branch nothing else proves. `TestRunSupport` had 15 tests on result math but none on `recomputeCaseResult` (re-derive a case's result *unless manually overridden*). The valuable test pins the override branch with steps that *disagree*:

```kotlin
@Test fun `manual override survives even when the steps disagree`() {
    val case = RunCase(stepResults = listOf(StepResult(StepVerdict.FAILED)),
                       result = RunResult.PASSED, manualResult = true)
    assertEquals(RunResult.PASSED, TestRunSupport.recomputeCaseResult(case).result)
}
```

The "disagree" setup is mandatory: if the steps agreed with the override, the test couldn't tell a preserved override from a re-derived one - it would pass whether the logic was right or broken.

## UI you can't reach lower

Most UI logic pulls down to level 1 (focus decisions, list-mutation targets, verdict math are all pure here). Genuinely end-to-end visual/interactive behavior (real focus traversal, painting, drag) belongs at level 4: a `runIde` sandbox driven by a UI harness - slow and brittle, so keep it to a few smoke paths. (Manual `.tc.md` smoke cases stand in until a real-IDE harness exists - see speqa-test-cases.)

## Common mistakes

| Mistake | Fix |
|---------|-----|
| `BasePlatformTestCase` for `input -> output` logic | Extract a pure function, test at level 1 |
| Mocking `Project`/VFS/PSI in a fixture | Use the fixture's real ones; replace only your services |
| `HeavyPlatformTestCase` "to be safe" | Use light unless you need on-disk modules/SDK |
| Bundling a resource with `rename {}` + flaky guard | Bundle as-is; `.template` suffix + strip-on-install if it must stay unindexed |
| A test added to raise coverage % | Find the untested branch; if none, don't add it |
| Index query empty in a fixture test | Dumb mode - finish indexing or test the pure query at level 1 |
