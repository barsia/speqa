# JCEF OSR Cursor Fix Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make CSS `cursor: pointer` (and the other CSS cursor families) actually change the Swing cursor when the user hovers over interactive elements in the Linux JCEF preview.

**Architecture:** Register a custom `JBCefOSRHandlerFactory` on the `JBCefBrowserBuilder` that wraps the default factory's `CefRenderHandler`, delegating all methods except `onCursorChange` — which sets the cursor on the `JBCefOsrComponent` directly. JBR's default OSR handler is package-private (cannot be subclassed) and silently no-ops `onCursorChange`, so we sit beside it via the public factory extension point and intercept just the cursor signal. Drag-and-drop (different problem, different APIs on the same render-handler interface) is intentionally out of scope here — a separate plan after this lands.

**Tech Stack:** Kotlin, IntelliJ Platform `com.intellij.ui.jcef.*` (`JBCefOSRHandlerFactory`, `JBCefBrowserBuilder`), CEF `org.cef.handler.CefRenderHandler`, AWT `java.awt.Cursor`.

---

## Why this plan exists (diagnostic evidence)

Empirical from `.intellijPlatform/sandbox/IU-2026.1/log/idea.log` 02:18 session, after diagnostic instrumentation in commits `2ab0f35`, `9305b4a`, `28ac69a`:

- `JBCefApp.isSupported=true`, `isOffScreenRenderingModeEnabled=true` — JCEF on Linux is forced into OSR mode by JBR.
- Component tree: `JBCefBrowser$MyPanel` → `JBCefOsrComponent` (both `cursor=0` / DEFAULT, never change).
- Mouse events reach `JBCefOsrComponent` (`MOUSE_ENTERED`, `MOUSE_MOVED`, `MOUSE_PRESSED`, `MOUSE_DRAGGED` all logged).
- The 250 ms `cursor POLL` and `cursor PCL` watchers attached recursively to every component **produced zero entries** across two minutes of hovering interactive elements. JBR's default `JBCefOsrHandler.onCursorChange` is a no-op: nothing ever calls `Component.setCursor`.
- An earlier attempt (commit `e483f83`) used `CefDisplayHandler.onCursorChange` — never fired. CEF routes cursor changes through `CefRenderHandler.onCursorChange` in OSR mode, not `CefDisplayHandler`. The display handler entry point is the wrong slot.

API archaeology of the JCEF surface in `intellij.platform.ide.impl.jar` (IntelliJ Platform 2026.1):

| Class | Visibility | Notes |
|-------|------------|-------|
| `com.intellij.ui.jcef.JBCefOSRHandlerFactory` | **public interface**, has `DEFAULT` static field | `createComponent(boolean)`, `createCefRenderHandler(JComponent): CefRenderHandler` |
| `com.intellij.ui.jcef.JBCefOsrHandler` | **package-private class** | Cannot subclass from outside. Implements `org.cef.handler.CefRenderHandler` (public interface). |
| `JBCefBrowserBuilder.setOSRHandlerFactory(JBCefOSRHandlerFactory)` | **public** | Our hook. |
| `JBCefOsrComponent.setRenderHandler(JBCefOsrHandler)` | accepts concrete (package-private) type | **Risk:** if JBR's framework downcasts our wrapper to `JBCefOsrHandler`, ClassCastException at runtime. Task 3 verifies empirically. |

Fallback if the wrapper crashes at runtime: switch to `JBCefOsrHandlerBrowser.create(url, customHandler)` and reimplement `CefRenderHandler` (including `onPaint`) from scratch. That is a separate plan, ~5× the surface of this one. We do the cheap try first.

---

## File structure

| File | Action | Responsibility |
|------|--------|----------------|
| `src/main/kotlin/io/github/barsia/speqa/webview/internal/linux/JcefCursorOsrHandlerFactory.kt` | Create | Self-contained `JBCefOSRHandlerFactory` that wraps the default's `CefRenderHandler` and overrides `onCursorChange` to call `Component.setCursor` |
| `src/main/kotlin/io/github/barsia/speqa/webview/internal/linux/JcefWebViewFacade.kt` | Modify | (a) Wire the new factory into `JBCefBrowser.createBuilder().setOSRHandlerFactory(...).build()`. (b) Delete the diagnostic block: tree logging, recursive mouse listeners, cursor PCL, periodic cursor poll, OSR-flag probe, and the dead `CefDisplayHandler` cursor handler. (c) Move the existing `mapCefCursorToAwt` private method into the new factory file (it's only used from the cursor handler now). |

No tests are added in this plan — the cursor signal is delivered by the CEF library in response to live page hovers, which is integration-level and not unit-testable without a JCEF runtime. Verification is manual under `./gradlew runIde` (Task 3).

---

### Task 1: Create `JcefCursorOsrHandlerFactory`

**Files:**
- Create: `src/main/kotlin/io/github/barsia/speqa/webview/internal/linux/JcefCursorOsrHandlerFactory.kt`

The new file holds two things: the factory class, and the private `mapCefCursorToAwt` helper (extracted from `JcefWebViewFacade.kt` so the facade can shrink in Task 2).

- [ ] **Step 1: Create the file with full content**

```kotlin
// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package io.github.barsia.speqa.webview.internal.linux

import com.intellij.ui.jcef.JBCefOSRHandlerFactory
import org.cef.browser.CefBrowser
import org.cef.callback.CefDragData
import org.cef.handler.CefRenderHandler
import org.cef.handler.CefScreenInfo
import org.cef.misc.CefRange
import java.awt.Cursor
import java.awt.Point
import java.awt.Rectangle
import java.nio.ByteBuffer
import javax.swing.JComponent
import javax.swing.SwingUtilities
import org.jetbrains.annotations.ApiStatus

/**
 * Wraps the platform's default OSR render handler so that `onCursorChange` actually applies
 * the requested cursor to the JCEF Swing component. JBR's stock `JBCefOsrHandler` accepts
 * cursor change signals from Chromium but never propagates them to the AWT component, so
 * CSS `cursor: pointer` (and friends) have no visible effect. See the diagnostic notes in
 * the plan for the evidence.
 *
 * Drag-and-drop is intentionally NOT intercepted here — it requires more than cursor work
 * (drag-source-state machine, dragSourceDragOver/dragSourceEndedAt calls) and is the subject
 * of a follow-up plan.
 */
@ApiStatus.Internal
internal class JcefCursorOsrHandlerFactory : JBCefOSRHandlerFactory {
  override fun createCefRenderHandler(component: JComponent): CefRenderHandler {
    val delegate = JBCefOSRHandlerFactory.DEFAULT.createCefRenderHandler(component)
    return DelegatingRenderHandler(delegate, component)
  }
}

private class DelegatingRenderHandler(
  private val delegate: CefRenderHandler,
  private val component: JComponent,
) : CefRenderHandler {

  override fun getViewRect(browser: CefBrowser): Rectangle = delegate.getViewRect(browser)

  override fun getScreenInfo(browser: CefBrowser, screenInfo: CefScreenInfo): Boolean =
    delegate.getScreenInfo(browser, screenInfo)

  override fun getScreenPoint(browser: CefBrowser, viewPoint: Point): Point =
    delegate.getScreenPoint(browser, viewPoint)

  override fun onPopupShow(browser: CefBrowser, show: Boolean) {
    delegate.onPopupShow(browser, show)
  }

  override fun onPopupSize(browser: CefBrowser, size: Rectangle) {
    delegate.onPopupSize(browser, size)
  }

  override fun onPaint(
    browser: CefBrowser,
    popup: Boolean,
    dirtyRects: Array<out Rectangle>,
    buffer: ByteBuffer,
    width: Int,
    height: Int,
  ) {
    delegate.onPaint(browser, popup, dirtyRects, buffer, width, height)
  }

  override fun onCursorChange(browser: CefBrowser, cursorType: Int): Boolean {
    val cursor = mapCefCursorToAwt(cursorType)
    SwingUtilities.invokeLater { component.cursor = cursor }
    // Returning true tells CEF "the host handled the cursor change" — JBR's default returns
    // false (or no-op true) anyway; we don't need to call delegate for this signal.
    return true
  }

  override fun startDragging(
    browser: CefBrowser,
    dragData: CefDragData,
    mask: Int,
    x: Int,
    y: Int,
  ): Boolean = delegate.startDragging(browser, dragData, mask, x, y)

  override fun updateDragCursor(browser: CefBrowser, operation: Int) {
    delegate.updateDragCursor(browser, operation)
  }

  override fun OnImeCompositionRangeChanged(
    browser: CefBrowser,
    selectedRange: CefRange,
    characterBounds: Array<out Rectangle>,
  ) {
    delegate.OnImeCompositionRangeChanged(browser, selectedRange, characterBounds)
  }

  override fun OnTextSelectionChanged(
    browser: CefBrowser,
    selectedText: String?,
    selectedRange: CefRange,
  ) {
    delegate.OnTextSelectionChanged(browser, selectedText, selectedRange)
  }
}

private fun mapCefCursorToAwt(cursorType: Int): Cursor {
  // cef_cursor_type_t values mirror Chromium's WebCursor. We map only the cursors that
  // actually appear in the SpeQA preview; anything else falls back to the default arrow.
  val awtType = when (cursorType) {
    0 -> Cursor.DEFAULT_CURSOR     // CT_POINTER
    1 -> Cursor.CROSSHAIR_CURSOR   // CT_CROSS
    2 -> Cursor.HAND_CURSOR        // CT_HAND
    3 -> Cursor.TEXT_CURSOR        // CT_IBEAM
    4 -> Cursor.WAIT_CURSOR        // CT_WAIT
    6 -> Cursor.E_RESIZE_CURSOR    // CT_EASTRESIZE
    7 -> Cursor.N_RESIZE_CURSOR    // CT_NORTHRESIZE
    8 -> Cursor.NE_RESIZE_CURSOR   // CT_NORTHEASTRESIZE
    9 -> Cursor.NW_RESIZE_CURSOR   // CT_NORTHWESTRESIZE
    10 -> Cursor.S_RESIZE_CURSOR   // CT_SOUTHRESIZE
    11 -> Cursor.SE_RESIZE_CURSOR  // CT_SOUTHEASTRESIZE
    12 -> Cursor.SW_RESIZE_CURSOR  // CT_SOUTHWESTRESIZE
    13 -> Cursor.W_RESIZE_CURSOR   // CT_WESTRESIZE
    14 -> Cursor.N_RESIZE_CURSOR   // CT_NORTHSOUTHRESIZE (closest predefined match)
    15 -> Cursor.E_RESIZE_CURSOR   // CT_EASTWESTRESIZE (closest predefined match)
    18 -> Cursor.E_RESIZE_CURSOR   // CT_COLUMNRESIZE
    19 -> Cursor.N_RESIZE_CURSOR   // CT_ROWRESIZE
    34 -> Cursor.MOVE_CURSOR       // CT_GRAB
    35 -> Cursor.MOVE_CURSOR       // CT_GRABBING
    else -> Cursor.DEFAULT_CURSOR
  }
  return Cursor.getPredefinedCursor(awtType)
}
```

- [ ] **Step 2: Verify compile**

```bash
cd /home/siarhei/speqa/speqa && ./gradlew compileKotlin --console=plain --no-daemon 2>&1 | tail -5
```

Expected: `BUILD SUCCESSFUL`. If the `CefRenderHandler` interface has additional methods beyond the ones we override (the JCEF version in this platform release might add a method we missed), Kotlin will refuse to compile the anonymous-class-style `DelegatingRenderHandler` because the abstract member isn't implemented. Add the missing override(s) by delegating to `delegate.<method>(...)` and recompile. Do NOT just add empty implementations.

If you get *"`OnTextSelectionChanged` has wrong signature"* or similar, inspect the actual abstract method via:

```bash
cd /home/siarhei/speqa/speqa && javap -classpath "$(find ~/.gradle/caches -name 'cef*.jar' -path '*/intellij-platform*' 2>/dev/null | head -1)" org.cef.handler.CefRenderHandler 2>&1 | head -30
```

Match the signature exactly (parameter nullability, varargs, return type).

- [ ] **Step 3: Commit**

```bash
cd /home/siarhei/speqa/speqa && git add src/main/kotlin/io/github/barsia/speqa/webview/internal/linux/JcefCursorOsrHandlerFactory.kt && git commit -m "linux(jcef): add OSR render-handler factory that wraps default + intercepts cursor"
```

---

### Task 2: Wire the factory and clean up dead diagnostic code in `JcefWebViewFacade`

**Files:**
- Modify: `src/main/kotlin/io/github/barsia/speqa/webview/internal/linux/JcefWebViewFacade.kt`

Two changes in one commit because they are tightly coupled: (a) install the factory, (b) remove the diagnostic scaffolding that's no longer needed since we now have a real fix. Removing the dead `addDisplayHandler` cursor handler is part of this too — it never fires in OSR mode.

- [ ] **Step 1: Read current state to anchor edits**

```bash
cd /home/siarhei/speqa/speqa && grep -n "JBCefBrowser.createBuilder\|SpeqaDebug\|attachMouseListenersRecursively\|attachCursorWatchersRecursively\|schedulePeriodicCursorPoll\|logComponentTree\|tryReadOsrFlag\|mapCefCursorToAwt\|displayHandler\|addDisplayHandler" src/main/kotlin/io/github/barsia/speqa/webview/internal/linux/JcefWebViewFacade.kt
```

Verify those line numbers match the edits below before doing the edits; the file may have shifted since the plan was written.

- [ ] **Step 2: Wire the factory into the builder**

Find the line `private val browser: JBCefBrowser = JBCefBrowser.createBuilder().build()` (around line 35). Replace it with:

```kotlin
  private val browser: JBCefBrowser = JBCefBrowser.createBuilder()
    .setOSRHandlerFactory(JcefCursorOsrHandlerFactory())
    .build()
```

- [ ] **Step 3: Delete the diagnostic block in `init`**

The `init` block currently contains (around lines 51–80):
- A `SpeqaDebug: JBCefApp.isSupported=...` log line + `tryReadOsrFlag()` call.
- A `SpeqaDebug: JCEF root component class=...` log line.
- A `HierarchyListener` that triggers `logComponentTree`, `attachMouseListenersRecursively`, `attachCursorWatchersRecursively`, `schedulePeriodicCursorPoll` once on first showing.

Replace the body of `init` between `WebViewLogger.logLifecycle(...)` and the closing `}` of `init` so that everything after the first `WebViewLogger.logLifecycle` line is removed. The final `init` block should be exactly:

```kotlin
  init {
    Disposer.register(this, browser)
    Disposer.register(this, jsQuery)
    state.set(State.Active)
    WebViewLogger.logLifecycle("linux-jcef-create", "JCEF browser ready")
  }
```

- [ ] **Step 4: Delete the dead `CefDisplayHandler` cursor handler from `initialize()`**

In `initialize(onMessage: (String) -> Unit)` (around lines 85–143), find the block starting with `val displayHandler = object : org.cef.handler.CefDisplayHandlerAdapter() {` and ending with `browser.jbCefClient.addDisplayHandler(displayHandler, browser.cefBrowser)`. Delete that whole block. The function should end after `browser.jbCefClient.addLoadHandler(loadHandler, browser.cefBrowser)`.

- [ ] **Step 5: Delete the now-orphan private methods**

The following private methods in the facade are no longer called by any code in the file after Steps 3-4:
- `mapCefCursorToAwt` (around line 214) — moved to `JcefCursorOsrHandlerFactory.kt` in Task 1
- `logComponentTree` (around line 243)
- `attachMouseListenersRecursively` (around line 259)
- `attachCursorWatchersRecursively` (around line 300)
- `schedulePeriodicCursorPoll` (around line 314)
- `tryReadOsrFlag` (around line 332)

Delete all six. Leave the `escapeJsString` and `cancelPendingEvaluations` private methods and the `companion object` intact.

- [ ] **Step 6: Verify the file still compiles and no stray SpeqaDebug remains**

```bash
cd /home/siarhei/speqa/speqa && grep -n "SpeqaDebug" src/main/kotlin/io/github/barsia/speqa/webview/internal/linux/JcefWebViewFacade.kt
```

Expected: empty.

```bash
cd /home/siarhei/speqa/speqa && ./gradlew compileKotlin --console=plain --no-daemon 2>&1 | tail -5
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Run the full test suite**

```bash
cd /home/siarhei/speqa/speqa && ./gradlew test --console=plain --no-daemon 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`. The Linux JCEF contract test (`WebViewNativeHostContractTest`) does not load a real JCEF runtime in JVM tests, so passing here only proves the wiring is type-clean. The real verification is Task 3.

- [ ] **Step 8: Commit**

```bash
cd /home/siarhei/speqa/speqa && git add src/main/kotlin/io/github/barsia/speqa/webview/internal/linux/JcefWebViewFacade.kt && git commit -m "linux(jcef): install cursor-fixing OSR factory; drop dead cursor display handler + diagnostic probes"
```

---

### Task 3: Local build + manual verification (cursor only)

**Files:** none modified.

- [ ] **Step 1: Build locally**

```bash
source "$HOME/.cargo/env" && cd /home/siarhei/speqa/speqa && ./gradlew buildPlugin --console=plain --no-daemon 2>&1 | tail -5
```

Expected: `BUILD SUCCESSFUL`, ZIP at `build/distributions/speqa-0.0.0-dev.zip`. (`source ~/.cargo/env` is precautionary; this build has no Rust step on Linux but Gradle config does.)

- [ ] **Step 2: Run sandbox IDE**

```bash
cd /home/siarhei/speqa/speqa && ./gradlew runIde
```

Open a `.tc` file in the launched sandbox. Wait for the preview pane to render content (instant — no "Waiting…" flash).

- [ ] **Step 3: Cursor verification — must pass**

Hover the mouse pointer over interactive elements inside the preview:
- A link or any element styled with CSS `cursor: pointer` (e.g., the "Run" button, attachment chips, link rows) → the OS cursor must become a **hand**.
- A plain text region inside an editable field → cursor must become an **I-beam (text)**.
- A resize gripper if there is one → cursor must become the appropriate resize arrow.
- Empty area in the preview → cursor must be the **default arrow**.

If hover-over a plain link still shows a default arrow, the fix did not take effect. Stop and check the next step.

- [ ] **Step 4: Diagnostic fallback — if cursor did not change**

There are two possibilities:

**(a) The wrapper crashed at runtime with `ClassCastException`** — JBR's framework downcast our wrapper to `JBCefOsrHandler`. Check:

```bash
grep -E "ClassCastException.*JBCefOsrHandler|JBCefOsrComponent.setRenderHandler" /home/siarhei/speqa/speqa/.intellijPlatform/sandbox/IU-2026.1/log/idea.log | tail -10
```

If you see the exception, the wrapper approach is unviable in this JBR version. The follow-up path is to switch to `JBCefOsrHandlerBrowser.create(url, customRenderHandler)` and reimplement `CefRenderHandler` from scratch (including `onPaint`). That is a separate plan — DO NOT attempt it here. Report `STATUS: BLOCKED — wrapper rejected by framework`.

**(b) The wrapper installed cleanly but cursor still doesn't change** — JBR may construct the OSR component before our factory is consulted, or `setOSRHandlerFactory` may have no effect on the path our builder takes. Check the live component tree by re-enabling a minimal poll in a new (temporary) commit:

```kotlin
// Temporary diagnostic — add inside init, remove after evidence is collected:
javax.swing.Timer(500) {
  WebViewLogger.LOG.warn("cursor-check: ${browser.component.cursor.type}")
}.also { it.isRepeats = true }.start()
```

If the timer log shows the cursor TYPE actually changing on hover but the OS still doesn't display it — that is a separate problem (Wayland/X11 composite quirk or an IDE-side override) and warrants its own plan. Report `STATUS: BLOCKED — cursor set in Swing, not displayed by OS`.

If the timer never logs a non-zero cursor type during hovering — the factory wasn't honored. Report `STATUS: BLOCKED — factory ignored, no setCursor calls`.

- [ ] **Step 5: If cursor verification passes — confirm no regressions**

Click into a preview field and type — keyboard input must still work (no input regression from the wrapper).
Scroll-wheel inside the preview — content must still scroll.
The Mac/Windows code paths are not touched by this plan, but the test suite was already green; no extra check needed.

- [ ] **Step 6: Mark Task 3 done and tell the user**

Tell the user explicitly that the **cursor fix landed** and the **drag-and-drop bug is still open** (separate follow-up plan). Don't claim D&D works — it doesn't. The user has been clear about not wanting premature success claims.

---

## Self-review notes

- **Spec coverage:** the only requirement of this plan is "make `cursor: pointer` change the OS cursor on Linux JCEF preview." Tasks 1 + 2 implement that. Task 3 proves it empirically. ✓
- **Placeholder scan:** every step contains exact code, exact commands, exact expected output. No "TBD"/"similar to"/"appropriate". One conditional fallback path exists in Task 3 step 4, but it is a triage tree, not a placeholder. ✓
- **Type consistency:** `JcefCursorOsrHandlerFactory` (Task 1) is referenced by exact name in Task 2's `setOSRHandlerFactory(JcefCursorOsrHandlerFactory())` call. The `CefRenderHandler` method signatures are taken from `javap` output of the platform's `JBCefOsrHandler` (which implements the interface in question) — they are correct for this platform version; the compile error in Task 1 Step 2 catches the rare case of a method we missed. ✓
- **TDD discipline:** no unit test is added because the relevant signal — Chromium's `onCursorChange` callback — requires a live JCEF runtime. The verification path is manual under `./gradlew runIde`, which is the established pattern in this project for everything WebView-related (see the inline-snapshot and snapshot-input-forwarding plans). ✓
- **Frequent commits:** two functional commits (factory file, facade wiring) + manual verification. Smaller commits than the cleanup combined would be artificial — the wiring and the diagnostic removal must land together to leave the file consistent. ✓
- **Project rules:** no `git checkout --` / `reset` / `restore` / `stash` instructions. No Claude attribution. Spec-edit gate was satisfied earlier in this session by the existing Linux WebView spec (`docs/superpowers/specs/2026-05-17-linux-webkitgtk-runtime-selection.md` superseded by the JCEF migration plan); the cursor fix doesn't change the spec because the spec describes "Linux WebView is JCEF-backed", which remains true.
- **D&D out of scope:** this plan touches only cursor. If Task 3 succeeds, a follow-up plan (writing-plans → subagent-driven-development) addresses drag-and-drop, which involves the drag-source state machine + dragSourceDragOver/dragSourceEndedAt forwarding and is independent of cursor.
