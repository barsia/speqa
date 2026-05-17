# Inline Initial Snapshot Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Eliminate the ~1-second "Waiting for SpeQA preview data..." flash on initial test-case / run preview load by embedding the first snapshot directly in the WebView's initial HTML, so JS can render synchronously on `DOMContentLoaded` instead of waiting for a `ready` → `snapshot` round-trip.

**Architecture:** `SpeqaWebViewPreviewSupport.buildInlinedPreviewHtml` gets an optional `initialSnapshotJson: String?` parameter. When supplied, it injects a `<script type="application/json" id="speqa-initial-snapshot">…</script>` block into `<head>` before the inlined `preview.js`. Both `SpeqaWebViewPreviewPanel` and `SpeqaWebViewRunPanel` serialise their current snapshot JSON (via the existing `SpeqaWebViewPreviewPayload.build` / `SpeqaWebViewRunPayload.build`) BEFORE calling `facade.loadHtml`, and pass it through. `preview.js` on `DOMContentLoaded` reads the inline snapshot — if present, calls `render(snapshot)` synchronously, then still emits `ready` so the bus stays wired for subsequent live updates. The placeholder `<div class="empty">Waiting for SpeQA preview data...</div>` is removed because with inline snapshot the page always opens with rendered content; absence of inline snapshot is a graceful no-op (empty preview area, identical to a freshly cleared edit state).

**Tech Stack:** Kotlin (`kotlinx.serialization.json.JsonObject.toString`), HTML, JS (vanilla, no framework), JUnit 4 for tests, IntelliJ Platform plugin.

---

## Why this plan exists (background)

After routing X11 through the snapshot backend (separate plan), the preview now renders reliably — but the user sees a ~1-second flash of literal "Waiting for SpeQA preview data..." text on every `.tc` open. Trace from the latest sandbox session:

```
22:47:28,535  WebKitGTK ready
22:47:28,718  JS sent  speqa/testCase/ready             (+183 ms after page parse)
22:47:29,582  Kotlin sent snapshot (chars=1032)         (+864 ms after ready)
22:47:29,582  Kotlin sent scrollToFraction (chars=86)
                                ↑ first render lands here, ~1 s after page load
```

`SpeqaWebViewPreviewPanel.installWebView()` already has the test case in memory (`current`) at the moment it calls `facade.loadHtml(...)`. The snapshot JSON can therefore be built up-front and shipped with the HTML, eliminating the round-trip. The post-ready `publishSnapshot()` in the `READY_METHOD` handler stays in place — it becomes a same-data re-render, which is idempotent (`render()` produces identical DOM for identical input), and continues to be the channel for subsequent updates (edits, theme changes, etc.).

## File structure

| File | Action | Responsibility |
|------|--------|----------------|
| `src/main/kotlin/io/github/barsia/speqa/editor/webview/SpeqaWebViewPreviewSupport.kt` | Modify | Add `initialSnapshotJson` parameter to `buildInlinedPreviewHtml`; new private `withInitialSnapshot` helper to embed the script tag with safe escaping |
| `src/main/kotlin/io/github/barsia/speqa/editor/webview/SpeqaWebViewPreviewPanel.kt` | Modify | Build initial snapshot JSON via `SpeqaWebViewPreviewPayload.build(...)` inside `loadPreviewHtml()` and pass it through |
| `src/main/kotlin/io/github/barsia/speqa/editor/webview/SpeqaWebViewRunPanel.kt` | Modify | Same as Preview panel, but using `SpeqaWebViewRunPayload.build(...)` |
| `src/main/resources/webview/test-case-preview/index.html` | Modify | Remove the `<div class="empty">Waiting for SpeQA preview data...</div>` placeholder line |
| `src/main/resources/webview/test-case-preview/preview.js` | Modify | On `DOMContentLoaded`, parse `#speqa-initial-snapshot` if present and call `render()` synchronously, then still notify `ready` |
| `src/test/kotlin/io/github/barsia/speqa/editor/webview/SpeqaWebViewPreviewHtmlTest.kt` | Modify | New tests for inline-snapshot embedding (presence + `</script>` escape correctness) |

All four panels (Preview + Run) share the same HTML/CSS/JS, so the JS-side change covers both call sites at once.

---

### Task 1: Support — add `initialSnapshotJson` parameter to `buildInlinedPreviewHtml` (TDD)

**Files:**
- Modify: `src/main/kotlin/io/github/barsia/speqa/editor/webview/SpeqaWebViewPreviewSupport.kt`
- Modify: `src/test/kotlin/io/github/barsia/speqa/editor/webview/SpeqaWebViewPreviewHtmlTest.kt`

- [ ] **Step 1: Add three failing tests to `SpeqaWebViewPreviewHtmlTest.kt`**

Append these tests after the last existing `@Test` in the class:

```kotlin
  @Test
  fun `omits initial-snapshot script when no snapshot supplied`() {
    val html = SpeqaWebViewPreviewSupport.buildInlinedPreviewHtml("light")
    assertTrue(!html.contains("id=\"speqa-initial-snapshot\""))
  }

  @Test
  fun `embeds initial-snapshot script when snapshot supplied`() {
    val html = SpeqaWebViewPreviewSupport.buildInlinedPreviewHtml(
      theme = "dark",
      initialSnapshotJson = """{"theme":"dark","title":"hello"}""",
    )
    assertTrue(html.contains("<script type=\"application/json\" id=\"speqa-initial-snapshot\">"))
    assertTrue(html.contains("\"title\":\"hello\""))
    // Embedded script must sit inside <head> so it parses before the inlined preview.js
    val headEnd = html.indexOf("</head>")
    val scriptIdx = html.indexOf("id=\"speqa-initial-snapshot\"")
    assertTrue(scriptIdx in 0..<headEnd)
  }

  @Test
  fun `escapes inner closing script tag inside initial snapshot json`() {
    val html = SpeqaWebViewPreviewSupport.buildInlinedPreviewHtml(
      theme = "light",
      initialSnapshotJson = """{"x":"a</script>b"}""",
    )
    // Raw </script> inside the JSON would close the host <script>; must be escaped to <\/script>
    assertTrue(!html.contains("\"a</script>b\""))
    assertTrue(html.contains("\"a<\\/script>b\""))
  }
```

- [ ] **Step 2: Run the tests to confirm they fail**

```
cd /home/siarhei/speqa/speqa && ./gradlew test --tests 'io.github.barsia.speqa.editor.webview.SpeqaWebViewPreviewHtmlTest' --console=plain --no-daemon 2>&1 | tail -20
```

Expected: compile failure (new tests reference a `initialSnapshotJson` parameter that doesn't exist yet). That's the failing-test signal — fine to proceed.

- [ ] **Step 3: Modify `SpeqaWebViewPreviewSupport.kt`**

Locate the existing `buildInlinedPreviewHtml` function (around lines 51–60). Replace it with:

```kotlin
  fun buildInlinedPreviewHtml(theme: String, initialSnapshotJson: String? = null): String {
    val skeleton = readResource("$PREVIEW_RESOURCE_ROOT/index.html")
    val css = readResource("$PREVIEW_RESOURCE_ROOT/$STYLESHEET_NAME")
    val js = readResource("$PREVIEW_RESOURCE_ROOT/$SCRIPT_NAME")
    val highlight = readResource("$PREVIEW_RESOURCE_ROOT/$HIGHLIGHT_SCRIPT_NAME")
    var html = withInlinedStylesheet(skeleton, STYLESHEET_NAME, css)
    html = withInlinedScript(html, SCRIPT_NAME, js)
    html = withInlinedScript(html, HIGHLIGHT_SCRIPT_NAME, highlight)
    html = withInitialTheme(html, theme)
    if (initialSnapshotJson != null) {
      html = withInitialSnapshot(html, initialSnapshotJson)
    }
    return html
  }

  private fun withInitialSnapshot(html: String, snapshotJson: String): String {
    // Escape `</` so a literal `</script>` substring inside the JSON cannot terminate the
    // host <script> tag. `<\/` is still valid JSON (the `\/` is a recognised escape for `/`
    // per RFC 8259 §7) so `JSON.parse` recovers the original content byte-for-byte.
    val safe = snapshotJson.replace("</", "<\\/")
    val tag = "<script type=\"application/json\" id=\"speqa-initial-snapshot\">$safe</script>"
    return html.replace("</head>", "$tag\n</head>", ignoreCase = false)
  }
```

(The `private fun withInitialSnapshot` goes immediately after the existing `withInitialTheme` helper, keeping the helpers grouped.)

- [ ] **Step 4: Run the tests to confirm they pass**

```
cd /home/siarhei/speqa/speqa && ./gradlew test --tests 'io.github.barsia.speqa.editor.webview.SpeqaWebViewPreviewHtmlTest' --console=plain --no-daemon 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`, all tests pass (the three new ones plus any pre-existing ones in the file).

- [ ] **Step 5: Commit**

```bash
cd /home/siarhei/speqa/speqa && git add src/main/kotlin/io/github/barsia/speqa/editor/webview/SpeqaWebViewPreviewSupport.kt src/test/kotlin/io/github/barsia/speqa/editor/webview/SpeqaWebViewPreviewHtmlTest.kt && git commit -m "webview: support embedding an initial snapshot JSON into the preview HTML"
```

---

### Task 2: `preview.js` — consume `#speqa-initial-snapshot` on load

**Files:**
- Modify: `src/main/resources/webview/test-case-preview/preview.js`
- Modify: `src/main/resources/webview/test-case-preview/index.html`

- [ ] **Step 1: Read the relevant section of `preview.js`**

```
cd /home/siarhei/speqa/speqa && sed -n '3589,3599p' src/main/resources/webview/test-case-preview/preview.js
```

Current content:

```js
window.__KWRY__.subscribe(methods.snapshot, function(snapshot) {
  hideAllMarkdownPopovers();
  render(snapshot);
});
window.__KWRY__.subscribe(methods.scrollToFraction, scrollToFraction);
window.__KWRY__.subscribe(methods.pastePreviewText, pastePreviewText);
window.addEventListener("DOMContentLoaded", function() {
  window.__KWRY__.notify(methods.ready);
});
```

- [ ] **Step 2: Replace the DOMContentLoaded block**

Use Edit to replace the `window.addEventListener("DOMContentLoaded", ...)` block with:

```js
window.addEventListener("DOMContentLoaded", function() {
  // If Kotlin embedded an initial snapshot in <head>, render it synchronously so the user
  // sees the test case immediately instead of a 1-second "loading" state while waiting for
  // the ready → snapshot round-trip. The ready notify still fires after — Kotlin uses it to
  // wire up the bus for subsequent edits, scroll restore, theme changes, etc.
  var initialScript = document.getElementById("speqa-initial-snapshot");
  if (initialScript) {
    try {
      render(JSON.parse(initialScript.textContent));
    } catch (e) {
      // Parse failures fall through to the handshake path — Kotlin will publish a fresh
      // snapshot after ready, producing an identical render to what we would have done.
    }
  }
  window.__KWRY__.notify(methods.ready);
});
```

- [ ] **Step 3: Remove the "Waiting..." placeholder from `index.html`**

Locate this line:

```html
      <div class="empty">Waiting for SpeQA preview data...</div>
```

(It is inside `<section class="card" id="app">…</section>`.) Replace the whole `<section class="card" id="app">…</section>` block — currently:

```html
    <section class="card" id="app">
      <div class="empty">Waiting for SpeQA preview data...</div>
    </section>
```

with:

```html
    <section class="card" id="app"></section>
```

`render()` writes its full content into `#app` on first call, so the empty inner `<div>` is unnecessary; an empty `<section>` is the cleanest fallback when no snapshot is supplied (no misleading "loading" string visible).

- [ ] **Step 4: Quick smoke check — HTML test suite still passes**

The HTML test suite reads `preview.js` and `index.html` from disk and asserts substrings. Verify those assertions still hold:

```
cd /home/siarhei/speqa/speqa && ./gradlew test --tests 'io.github.barsia.speqa.editor.webview.SpeqaWebViewPreviewHtmlTest' --console=plain --no-daemon 2>&1 | tail -10
```

Expected: BUILD SUCCESSFUL. If anything fails because a test asserts `Waiting for SpeQA preview data` — surface it in your report; that test was outdated.

- [ ] **Step 5: Commit**

```bash
cd /home/siarhei/speqa/speqa && git add src/main/resources/webview/test-case-preview/preview.js src/main/resources/webview/test-case-preview/index.html && git commit -m "webview: render inlined initial snapshot synchronously on DOMContentLoaded"
```

---

### Task 3: Pass initial snapshot from `SpeqaWebViewPreviewPanel`

**Files:**
- Modify: `src/main/kotlin/io/github/barsia/speqa/editor/webview/SpeqaWebViewPreviewPanel.kt`

- [ ] **Step 1: Locate current `loadPreviewHtml()`**

Find the function — currently at around line 794:

```kotlin
  private fun loadPreviewHtml(): String = SpeqaWebViewPreviewSupport.buildInlinedPreviewHtml(currentTheme())
```

There's also `private fun publishSnapshot(...)` nearby (around line 769) that builds the snapshot via `SpeqaWebViewPreviewPayload.build(current, currentTheme(), createdLabel = meta.createdLabel, updatedLabel = meta.updatedLabel, restorePreviewTextFocus = ...)`.

- [ ] **Step 2: Replace `loadPreviewHtml()`**

Edit it to:

```kotlin
  private fun loadPreviewHtml(): String {
    val meta = resolveTestCaseHeaderMeta(project, file)
    val initialSnapshotJson = SpeqaWebViewPreviewPayload.build(
      testCase = current,
      theme = currentTheme(),
      createdLabel = meta.createdLabel,
      updatedLabel = meta.updatedLabel,
      restorePreviewTextFocus = false,
    ).toString()
    return SpeqaWebViewPreviewSupport.buildInlinedPreviewHtml(currentTheme(), initialSnapshotJson)
  }
```

Notes for the implementer:
- `current` and `currentTheme()` are existing members of the panel — no new state.
- `resolveTestCaseHeaderMeta(project, file)` is the same helper already used by `publishSnapshot`.
- `restorePreviewTextFocus = false` is correct for initial load because no focus restoration applies on first paint (there is nothing to restore yet).
- `JsonObject.toString()` from `kotlinx.serialization.json` produces a strictly JSON-compatible string — safe to inline.

- [ ] **Step 3: Compile**

```
cd /home/siarhei/speqa/speqa && ./gradlew compileKotlin --console=plain --no-daemon 2>&1 | tail -5
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Run any panel-level tests that exist**

```
cd /home/siarhei/speqa/speqa && ./gradlew test --tests 'io.github.barsia.speqa.editor.webview.SpeqaWebViewPreviewPanelTest' --console=plain --no-daemon 2>&1 | tail -10
```

Expected: BUILD SUCCESSFUL. If the panel test mocks `loadPreviewHtml` or asserts no metadata is read at init time, surface that in your report — but those assertions would be obsolete now and need updating to reflect the new behaviour.

- [ ] **Step 5: Commit**

```bash
cd /home/siarhei/speqa/speqa && git add src/main/kotlin/io/github/barsia/speqa/editor/webview/SpeqaWebViewPreviewPanel.kt && git commit -m "webview: ship initial test-case snapshot inline with the preview HTML"
```

---

### Task 4: Pass initial snapshot from `SpeqaWebViewRunPanel`

**Files:**
- Modify: `src/main/kotlin/io/github/barsia/speqa/editor/webview/SpeqaWebViewRunPanel.kt`

Symmetric to Task 3 but using `SpeqaWebViewRunPayload.build(...)`.

- [ ] **Step 1: Locate current `loadPreviewHtml()`**

Find the function — currently at around line 579:

```kotlin
  private fun loadPreviewHtml(): String = SpeqaWebViewPreviewSupport.buildInlinedPreviewHtml(currentTheme())
```

And `private fun publishSnapshot(...)` (around line 555) which calls `SpeqaWebViewRunPayload.build(current, currentTheme(), createdLabel = meta.createdLabel, restorePreviewTextFocus = ...)`. Note: `SpeqaWebViewRunPayload.build` does NOT take `updatedLabel` — only `createdLabel` — verify this by reading the file briefly before editing.

- [ ] **Step 2: Replace `loadPreviewHtml()`**

Edit to:

```kotlin
  private fun loadPreviewHtml(): String {
    val meta = resolveTestCaseHeaderMeta(project, file)
    val initialSnapshotJson = SpeqaWebViewRunPayload.build(
      run = current,
      theme = currentTheme(),
      createdLabel = meta.createdLabel,
      restorePreviewTextFocus = false,
    ).toString()
    return SpeqaWebViewPreviewSupport.buildInlinedPreviewHtml(currentTheme(), initialSnapshotJson)
  }
```

(If `SpeqaWebViewRunPayload.build`'s actual parameter name for the first positional arg is something other than `run` — e.g. `testRun` — adjust to match the real signature. Read the file once to confirm the parameter name; the rest of the call stays the same.)

- [ ] **Step 3: Compile**

```
cd /home/siarhei/speqa/speqa && ./gradlew compileKotlin --console=plain --no-daemon 2>&1 | tail -5
```

Expected: `BUILD SUCCESSFUL`. If it fails because of an argument name or shape mismatch, fix the call to match the real `SpeqaWebViewRunPayload.build` signature and re-compile.

- [ ] **Step 4: Test sweep**

```
cd /home/siarhei/speqa/speqa && ./gradlew test --console=plain --no-daemon 2>&1 | tail -10
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
cd /home/siarhei/speqa/speqa && git add src/main/kotlin/io/github/barsia/speqa/editor/webview/SpeqaWebViewRunPanel.kt && git commit -m "webview: ship initial test-run snapshot inline with the preview HTML"
```

---

### Task 5: Local build and manual verification

**Files:** none modified.

- [ ] **Step 1: Local build**

```
source "$HOME/.cargo/env" && cd /home/siarhei/speqa/speqa && ./gradlew buildPlugin --console=plain --no-daemon 2>&1 | tail -5
```

Expected: `BUILD SUCCESSFUL`, ZIP at `build/distributions/speqa-0.0.0-dev.zip`.

- [ ] **Step 2: User installs and reopens IDE, opens a `.tc` file**

Human step. The fastest path: re-run the sandbox IDE via `./gradlew runIde` (uses the freshly compiled plugin without manual install), or install the ZIP into a regular IDE.

- [ ] **Step 3: Verify no "Waiting for SpeQA preview data..." flash**

Visual check. When the `.tc` editor tab opens, the right pane should show rendered preview content **immediately** (within one paint frame, no perceptible delay), not a brief flash of placeholder text.

- [ ] **Step 4: Verify subsequent edits still update the preview**

Type a character in the source editor — the preview should update via the normal `publishSnapshot()` path (post-ready snapshot push over the bus). If the preview stops updating after edits, the `READY_METHOD` handler's `publishSnapshot()` regressed somewhere — investigate before reporting done.

- [ ] **Step 5: Check the log for clean lifecycle**

```
tail -200 /home/siarhei/speqa/speqa/.intellijPlatform/sandbox/IU-2026.1/log/idea.log | grep "speqa.webview"
```

Expected sequence (give or take ordering):

```
lifecycle: linux-webkitgtk-create — initializing WebKitGTK
lifecycle: linux-webkitgtk-runtime-selected — Wk40
lifecycle: linux-webkitgtk-load — Wk40: …
LinuxWebKitGtkBridge: Wayland WebKitGTK widgets created
lifecycle: linux-webkitgtk-create — WebKitGTK ready
Received WebView message from JS: method=speqa/testCase/ready
Sending first WebView message to JS: method=speqa/testCase/snapshot, chars=…
Sending first WebView message to JS: method=speqa/testCase/scrollToFraction, chars=…
```

The post-ready snapshot publish still fires (idempotent re-render with same data). The crash-free, no-X11-overlay behaviour from the previous plan must continue to hold.

- [ ] **Step 6: If anything in steps 3–5 fails**

Stop. Report which step failed and what you observed (which substring appeared visually, what the log shows). Do NOT roll back without diagnosing.

---

---

## Follow-up fix: seed panels with real model at construction (2026-05-17)

**Problem:** After the original plan landed, users reported the steps section still shows "No steps yet" on first paint. Root cause: `loadPreviewHtml()` runs inside the panel's `init` block, when `current` is still the default empty model (`TestCase()` / `TestRun()`). The real data only arrives via the subsequent `updateFrom()` call, by which point the inline snapshot JSON is already baked into the HTML.

**Fix:** Accept the initial value as a constructor parameter (with `TestCase()` / `TestRun()` default for backwards compatibility). Callers that already have the real value (`SpeqaPreviewEditor` has `parsed` initialized before the panel field; `TestRunEditor` has `current` initialized before `panel`) pass it in, so `current` is correct when `loadPreviewHtml()` runs.

Files modified:
- `SpeqaWebViewPreviewPanel` — add `initialTestCase: TestCase = TestCase()` parameter; initialize `current = initialTestCase`
- `SpeqaPreviewEditor` — pass `initialTestCase = parsed.testCase` to the panel constructor
- `SpeqaWebViewRunPanel` — add `initialRun: TestRun = TestRun()` parameter; initialize `current = initialRun`
- `TestRunEditor` — pass `initialRun = current` to the panel constructor (uses runner-normalized `current`, not raw `initialRun`)

The existing `updateFrom(...)` calls in `init` blocks are left intact — they remain the path for subsequent updates.

## Self-review notes

- **Spec coverage**: there is no separate spec for this UX change. The fix is small and self-justifying. README documentation for the rendering model already says "snapshots pushed back into Swing at a limited refresh rate" — that statement remains correct (snapshots still flow over the bus for all edits). No README update needed.
- **Placeholder scan**: every step contains the exact code, the exact path, the exact command. No "similar to" / "TBD" / "appropriate" patterns. ✓
- **Type consistency**: 
  - `buildInlinedPreviewHtml(theme: String, initialSnapshotJson: String? = null)` — single signature used in all three tasks (Support, PreviewPanel, RunPanel). ✓
  - `SpeqaWebViewPreviewPayload.build(...)` and `SpeqaWebViewRunPayload.build(...)` both return `JsonObject`; `.toString()` produces a valid JSON string. ✓
  - `#speqa-initial-snapshot` id is consistent across Support helper, JS reader, and tests. ✓
- **Backwards-compat**: the `initialSnapshotJson` parameter defaults to `null`. Any caller that doesn't supply it (or supplies null) gets the previous behaviour — no inline script, JS falls through to handshake. Removing the `<div class="empty">Waiting…</div>` placeholder is the only behaviour change for callers that don't pass a snapshot, and even then the section ends up as an empty container that `render()` fills in once the post-ready snapshot arrives — visually equivalent to a black/dark pane for a fraction of a second, which is what we already see before this fix anyway. ✓
- **TDD discipline**: Task 1 writes the failing test first, then implements. Tasks 2–4 are simple plumbing/glue that the existing HTML test suite covers indirectly (via the `buildInlinedPreviewHtml` substring assertions). Task 5 is end-to-end verification.
- **Frequent commits**: 5 commits, one logical change each.
- **No "Co-Authored-By: Claude"** in any commit message per project rule. ✓
