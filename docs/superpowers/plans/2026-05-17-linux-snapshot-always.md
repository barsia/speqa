# Linux WebView always-snapshot Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stop embedding the WebKitGTK widget as a foreign X11 child of the JBR Swing window. Route the X11 path through the existing snapshot backend so the preview renders reliably, content is actually visible, no JVM crashes, and focus behaves normally.

**Architecture:** The Wayland-only snapshot backend (offscreen `GtkOffscreenWindow` → bitmap pushed to `SwingWebViewHostPanel.setSnapshotImage`) already works end-to-end. Re-route the X11 toolkit selection in `WebViewFacadeFactory.linuxBackend()` to return the same `WaylandSnapshot` enum value so both X11 and Wayland sessions take the snapshot path. The previously X11-only native overlay path stays in code (for future reactivation) but is no longer reached at runtime. Diagnostic logging from the prior debugging session is removed.

**Tech Stack:** Kotlin (IntelliJ Platform plugin), JBR/Swing AWT XToolkit on X11, Rust 1.x WebKitGTK bridge via JNI.

---

## Why this plan exists (background)

Recent debugging established, with concrete evidence (xwd buffer captures, `xwininfo -tree` outputs, JVM SIGABRT crash logs at `/home/siarhei/java_error_in_idea_*.log`), that embedding a `GtkWindow` as a foreign X11 child of the JBR top-level / Content window is fundamentally fragile on this platform:

- Even when reparented under the AWT Content window with `Map State: IsViewable` and non-zero bounds, WebKit's render output does not reach the X11 pixmap — the buffer stays a solid GTK background colour (`0x1a1900` with HW accel on, `0xf7f7f7` after `WEBKIT_HARDWARE_ACCELERATION_POLICY_NEVER` was forced).
- The JVM SIGABRTs in `native thread` within ~10 seconds of `linux-webkitgtk-create — WebKitGTK ready` on every other open of a `.tc` file (PIDs 58558, 66800, 67533 confirmed via `FSRecords … wasn't closed properly/crashed?` warnings).
- Focus management between AWT and the embedded GTK widget never converges — keyboard input ends up trapped or lost.

The Wayland path was already designed around offscreen rendering + Swing bitmap blits — it has none of these problems because no foreign X11 child window exists. Routing X11 through the same path is a small, low-risk change that solves all three symptoms at once.

---

## File structure

| File | Action | Responsibility |
|------|--------|----------------|
| `src/main/kotlin/io/github/barsia/speqa/webview/WebViewFacadeFactory.kt` | Modify | `linuxBackend()` returns `WaylandSnapshot` for both X11 and Wayland toolkits |
| `native/LinuxWebKitGtkBridge/README.md` | Modify | Document that the snapshot path is the active path on both X11 and Wayland; the native X11 overlay path is dormant |
| `src/main/kotlin/io/github/barsia/speqa/webview/internal/linux/LinuxX11NativeWebViewHostPeer.kt` | Modify | Remove the `SpeqaDebug` logging added during the prior diagnostic session (the code path is no longer hit, but leaving warn-level logging there is noise if it ever is) |
| `src/main/kotlin/io/github/barsia/speqa/webview/WebViewFacadeFactory.kt` | (covered above) | Also remove the `SpeqaDebug` log line in `linuxBackend()` |
| `src/main/kotlin/io/github/barsia/speqa/webview/internal/linux/LinuxWebKitWebViewFacade.kt` | Modify | Remove `SpeqaDebug` log lines in `setBounds` and `setHidden` |

`LinuxWebKitBackend.kt` and the X11 native code stay as-is. We are intentionally NOT renaming `WaylandSnapshot` to `Snapshot` in this plan: that's a larger refactor, and validating the routing change first is more important than the rename.

---

### Task 1: Update README to document always-snapshot rendering on Linux

**Files:**
- Modify: `native/LinuxWebKitGtkBridge/README.md`

Project rule: "Spec/documentation must describe current product state." The README currently says X11 uses overlay rendering and Wayland falls back to snapshot. After this change both use snapshot, so the doc must be updated BEFORE the code (matches the spec-first pattern; the spec edit gate is already satisfied for this session, but this still needs to land first commit because the doc is part of the change).

- [ ] **Step 1: Read current README content**

Run:

```
cd /home/siarhei/speqa/speqa && cat native/LinuxWebKitGtkBridge/README.md
```

Locate the existing paragraph(s) about X11 vs Wayland rendering — search for "X11" and "snapshot mode" to find them. There is text like *"Real overlay rendering on top of the IntelliJ window is only available on X11. Under Wayland the bridge falls back to a snapshot mode…"* and likely a header for that section.

- [ ] **Step 2: Replace the "X11 vs Wayland" paragraph(s) with the new description**

Replace the paragraph(s) describing X11 overlay vs Wayland snapshot with this content (keep the surrounding section heading the same as before — match whatever heading currently introduces this discussion, e.g. `## Rendering` or `## Wayland support`):

```markdown
## Rendering

On both X11 and Wayland the bridge renders WebKit content into an offscreen
`GtkOffscreenWindow`, captures the resulting pixel buffer, and hands it to
`SwingWebViewHostPanel.setSnapshotImage`, which paints it as a regular Swing
image inside the editor.

This is reliable across both display servers because the bridge never embeds
a foreign GTK widget as an X11 child of the JBR top-level window. Embedded
X11 children of a JBR/Swing parent do not receive proper expose / focus
events under either GNOME / Mutter compositing or vanilla X11, and the
WebKitGTK render output rarely reaches the X11 pixmap. The native X11
overlay path remains in the codebase for a future re-enable but is not
reached at runtime.

Trade-offs:
  * No GPU-accelerated scrolling — refresh runs at the snapshot cadence
    (~30 fps when content is dirty, idle otherwise).
  * Input events are dispatched through the GTK widget tree on the
    offscreen window; keyboard focus and text input are handled by Swing
    over the painted bitmap.
```

If the README has no such section header at all, append the section at the end of the file (before any trailing blank line).

- [ ] **Step 3: Commit**

```bash
cd /home/siarhei/speqa/speqa && git add native/LinuxWebKitGtkBridge/README.md && git commit -m "docs: snapshot rendering is now the active linux path on both X11 and Wayland"
```

Expected: commit succeeds; `git status` shows the README no longer modified.

---

### Task 2: Route X11 toolkit through the WaylandSnapshot backend

**Files:**
- Modify: `src/main/kotlin/io/github/barsia/speqa/webview/WebViewFacadeFactory.kt`

Current logic at `linuxBackend()` (lines ~99–110, includes the SpeqaDebug warn added during diagnosis):

```kotlin
private fun linuxBackend(): LinuxWebKitBackend {
  check(SystemInfo.isLinux) { "System WebView is supported only on Linux" }
  val backend = when {
    LinuxWaylandWindowUtil.isSupportedToolkit() -> LinuxWebKitBackend.WaylandSnapshot
    LinuxX11WindowUtil.isSupportedToolkit() -> LinuxWebKitBackend.X11
    else -> error("Linux System WebView is supported only with X11 or Wayland/WLToolkit")
  }
  com.intellij.openapi.diagnostic.Logger.getInstance("SpeqaDebug").warn(
    "linux backend selected: $backend (wayland-toolkit=${LinuxWaylandWindowUtil.isSupportedToolkit()}, x11-toolkit=${LinuxX11WindowUtil.isSupportedToolkit()})"
  )
  return backend
}
```

We change two things at once in one commit because they are tightly coupled:
1. X11 toolkit → `WaylandSnapshot` (the routing change)
2. Remove the SpeqaDebug log statement (no longer needed once we know what we're shipping)

- [ ] **Step 1: Replace `linuxBackend()` body**

Use `Edit` to replace the entire `linuxBackend()` function with:

```kotlin
  private fun linuxBackend(): LinuxWebKitBackend {
    check(SystemInfo.isLinux) { "System WebView is supported only on Linux" }
    return when {
      // Both X11 and Wayland route through the snapshot backend — the WebKitGTK widget
      // renders offscreen and the resulting bitmap is painted into Swing. Embedding a
      // foreign GTK X11 child under the JBR top-level / Content window proved unstable
      // (no rendering reached the X11 pixmap, JVM SIGABRTs from the native render thread,
      // focus trapped in the embedded widget) — see the always-snapshot plan for details.
      LinuxWaylandWindowUtil.isSupportedToolkit() -> LinuxWebKitBackend.WaylandSnapshot
      LinuxX11WindowUtil.isSupportedToolkit() -> LinuxWebKitBackend.WaylandSnapshot
      else -> error("Linux System WebView is supported only with X11 or Wayland/WLToolkit")
    }
  }
```

Note the comment block is intentional: this is exactly the "subtle invariant" the project-rule comment guidance protects (`Don't add comments that describe WHAT — describe non-obvious WHY`). The reader looking at this function will rightly wonder why both branches return the same value; the comment explains the deliberate reason.

- [ ] **Step 2: Verify it compiles**

Run:

```
cd /home/siarhei/speqa/speqa && ./gradlew compileKotlin --console=plain --no-daemon 2>&1 | tail -5
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
cd /home/siarhei/speqa/speqa && git add src/main/kotlin/io/github/barsia/speqa/webview/WebViewFacadeFactory.kt && git commit -m "linux: route X11 toolkit through snapshot backend (avoid foreign X11 child embedding)"
```

---

### Task 3: Remove SpeqaDebug logging from the now-dormant X11 path

**Files:**
- Modify: `src/main/kotlin/io/github/barsia/speqa/webview/internal/linux/LinuxX11NativeWebViewHostPeer.kt`
- Modify: `src/main/kotlin/io/github/barsia/speqa/webview/internal/linux/LinuxWebKitWebViewFacade.kt`

The X11 host peer is no longer reached at runtime, but it still has WARN-level debug logs at every attach/detach/setBounds/clearFocus call. The facade also has WARN-level logs in `setBounds` and `setHidden`. These were added during the prior diagnostic session and must be removed (project rule: "Remove debug logging after the fix is confirmed").

- [ ] **Step 1: Restore `LinuxX11NativeWebViewHostPeer.kt` to its pre-debug shape**

Use `Edit` to replace the entire class body with the version that doesn't reference `SpeqaDebug` — same code as before commit `090a90b` introduced the logger. The expected final file content (after the copyright/package/imports):

```kotlin
internal class LinuxX11NativeWebViewHostPeer(
  private val facade: LinuxWebKitWebViewFacade,
) : NativeWebViewHostPeer {

  private var attached = false
  private var lastAppliedFrame: AppliedFrame? = null

  override fun attach(host: Component): Boolean {
    val parentXid = LinuxX11WindowUtil.resolveWindowXid(host) ?: return false
    facade.attachToX11Parent(parentXid)
    attached = true
    lastAppliedFrame = null

    scheduleFrameUpdate(host)
    facade.setHidden(!host.isShowing)
    SwingUtilities.invokeLater { scheduleFrameUpdate(host) }
    return true
  }

  override fun detach() {
    if (!attached) return
    facade.detach()
    attached = false
    lastAppliedFrame = null
  }

  override fun scheduleFrameUpdate(host: Component) {
    if (!attached) return
    val anchor = SwingWebViewHostPanel.resolveWindowsAnchor(host) ?: return
    val bounds = SwingWebViewHostPanel.calculateWindowsBounds(host, anchor)
    val scale = LinuxX11WindowUtil.scale(host)
    val frame = AppliedFrame(bounds, scale)
    if (frame == lastAppliedFrame) return
    lastAppliedFrame = frame
    facade.setBounds(bounds.x, bounds.y, bounds.width, bounds.height, scale)
  }

  override fun updateVisibility(host: Component, hidden: Boolean) {
    if (!attached) return
    facade.setHidden(hidden)
  }

  override fun requestFocus() {
    if (!attached) return
    facade.requestFocus()
  }

  override fun clearFocus() {
    if (!attached) return
    facade.clearFocus()
  }

  private data class AppliedFrame(
    val bounds: SwingWebViewHostPanel.NativeBounds,
    val scale: Double,
  )
}
```

The whole file therefore reads (copyright + package + imports + class body):

```kotlin
// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package io.github.barsia.speqa.webview.internal.linux

import io.github.barsia.speqa.webview.SwingWebViewHostPanel
import io.github.barsia.speqa.webview.internal.host.NativeWebViewHostPeer
import java.awt.Component
import javax.swing.SwingUtilities

internal class LinuxX11NativeWebViewHostPeer(
  private val facade: LinuxWebKitWebViewFacade,
) : NativeWebViewHostPeer {

  private var attached = false
  private var lastAppliedFrame: AppliedFrame? = null

  override fun attach(host: Component): Boolean {
    val parentXid = LinuxX11WindowUtil.resolveWindowXid(host) ?: return false
    facade.attachToX11Parent(parentXid)
    attached = true
    lastAppliedFrame = null

    scheduleFrameUpdate(host)
    facade.setHidden(!host.isShowing)
    SwingUtilities.invokeLater { scheduleFrameUpdate(host) }
    return true
  }

  override fun detach() {
    if (!attached) return
    facade.detach()
    attached = false
    lastAppliedFrame = null
  }

  override fun scheduleFrameUpdate(host: Component) {
    if (!attached) return
    val anchor = SwingWebViewHostPanel.resolveWindowsAnchor(host) ?: return
    val bounds = SwingWebViewHostPanel.calculateWindowsBounds(host, anchor)
    val scale = LinuxX11WindowUtil.scale(host)
    val frame = AppliedFrame(bounds, scale)
    if (frame == lastAppliedFrame) return
    lastAppliedFrame = frame
    facade.setBounds(bounds.x, bounds.y, bounds.width, bounds.height, scale)
  }

  override fun updateVisibility(host: Component, hidden: Boolean) {
    if (!attached) return
    facade.setHidden(hidden)
  }

  override fun requestFocus() {
    if (!attached) return
    facade.requestFocus()
  }

  override fun clearFocus() {
    if (!attached) return
    facade.clearFocus()
  }

  private data class AppliedFrame(
    val bounds: SwingWebViewHostPanel.NativeBounds,
    val scale: Double,
  )
}
```

- [ ] **Step 2: Remove the SpeqaDebug warn from `LinuxWebKitWebViewFacade.setBounds`**

Locate `internal fun setBounds(x, y, width, height, scale)` (around line 207). Current version:

```kotlin
  internal fun setBounds(x: Int, y: Int, width: Int, height: Int, scale: Double) {
    val bounds = PendingBounds(x, y, width, height, scale)
    pendingBounds = bounds
    val handle = nativeHandle
    val st = state.get()
    com.intellij.openapi.diagnostic.Logger.getInstance("SpeqaDebug").warn(
      "facade setBounds: x=$x y=$y w=$width h=$height scale=$scale handle=$handle state=$st backend=$backend"
    )
    if (handle == 0L || st == State.Closed) return
    runOnEdt { applyBounds(handle, bounds) }
  }
```

Replace it with:

```kotlin
  internal fun setBounds(x: Int, y: Int, width: Int, height: Int, scale: Double) {
    val bounds = PendingBounds(x, y, width, height, scale)
    pendingBounds = bounds
    val handle = nativeHandle
    if (handle == 0L || state.get() == State.Closed) return
    runOnEdt { applyBounds(handle, bounds) }
  }
```

- [ ] **Step 3: Remove the SpeqaDebug warn from `LinuxWebKitWebViewFacade.setHidden`**

Current:

```kotlin
  internal fun setHidden(hidden: Boolean) {
    this.hidden = hidden
    val handle = nativeHandle
    val st = state.get()
    com.intellij.openapi.diagnostic.Logger.getInstance("SpeqaDebug").warn(
      "facade setHidden: hidden=$hidden handle=$handle state=$st"
    )
    if (handle == 0L || st == State.Closed) return
    runOnEdt { LinuxWebKitGtkBridge.setVisible(handle, !hidden) }
```

Replace with:

```kotlin
  internal fun setHidden(hidden: Boolean) {
    this.hidden = hidden
    val handle = nativeHandle
    if (handle == 0L || state.get() == State.Closed) return
    runOnEdt { LinuxWebKitGtkBridge.setVisible(handle, !hidden) }
```

(Keep the rest of `setHidden` after `runOnEdt {` exactly as it currently is — Edit just the prelude.)

- [ ] **Step 4: Verify nothing else still references `SpeqaDebug`**

Run:

```
cd /home/siarhei/speqa/speqa && grep -rn "SpeqaDebug" src/main/kotlin/ 2>&1
```

Expected: NO matches. If any line still references `SpeqaDebug`, remove it the same way (it's debug-only).

- [ ] **Step 5: Compile**

```
cd /home/siarhei/speqa/speqa && ./gradlew compileKotlin --console=plain --no-daemon 2>&1 | tail -5
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
cd /home/siarhei/speqa/speqa && git add src/main/kotlin/io/github/barsia/speqa/webview/internal/linux/LinuxX11NativeWebViewHostPeer.kt src/main/kotlin/io/github/barsia/speqa/webview/internal/linux/LinuxWebKitWebViewFacade.kt && git commit -m "linux: drop diagnostic SpeqaDebug logging — root cause confirmed and routed around"
```

---

### Task 4: Local build and manual verification

This is the moment that determines whether the routing change actually fixed the user's symptoms. Project rules:
* "Fix root causes, not symptoms. Never guess at fixes."
* "When a bug is not obvious, add diagnostic logging first…"
* "For UI or frontend changes, start the dev server and use the feature in a browser before reporting the task as complete."

We've already done diagnosis. This task is the in-browser verification step.

**Files:** none modified — verification only.

- [ ] **Step 1: Local build**

Run from the project root:

```
source "$HOME/.cargo/env" && cd /home/siarhei/speqa/speqa && ./gradlew buildPlugin --console=plain --no-daemon 2>&1 | tail -5
```

Expected: `BUILD SUCCESSFUL`, ZIP at `build/distributions/speqa-0.0.0-dev.zip`.

- [ ] **Step 2: User installs and reopens IDE**

This step is for the human user, not the implementer subagent: "Open IntelliJ → Settings → Plugins → ⚙ → Install Plugin from Disk → select `build/distributions/speqa-0.0.0-dev.zip` → Restart IDE → open `123.tc.md` (or any `.tc` file)."

If running this plan via the subagent driver, surface this instruction to the human and pause until they confirm the plugin is loaded and a `.tc` file is open.

- [ ] **Step 3: Verify preview is visible and shows test-case content**

Visual check. Expected: the right pane of the editor for the `.tc` file shows rendered HTML — frontmatter values, Preconditions/Scenario headings, action/result rows, etc. Specifically NOT a solid white block, NOT empty/dark.

- [ ] **Step 4: Verify focus is not trapped**

Click on the left editor pane and type — keystrokes should appear in the source editor as text. Click on the project tree on the far left — selection should move. Press Tab in the editor — caret should move. None of this should require closing the `.tc` tab.

- [ ] **Step 5: Verify no JVM crash**

Leave the `.tc` open for at least 60 seconds, navigate around the IDE. Then:

```
ls -la ~/java_error_in_idea_*.log | tail -5
```

Expected: no NEW crash log file with a timestamp newer than when the current IDE process started.

Also check the IDE log:

```
tail -200 ~/.cache/JetBrains/IntelliJIdea2026.1/log/idea.log | grep -iE "FSRecords.*crashed|wasn't closed properly"
```

Expected: no warning about the current process closing improperly.

- [ ] **Step 6: Verify the lifecycle messages confirm snapshot path**

```
tail -200 ~/.cache/JetBrains/IntelliJIdea2026.1/log/idea.log | grep "linux-webkitgtk"
```

Expected to see `linux-webkitgtk-create — initializing WebKitGTK` and `linux-webkitgtk-create — WebKitGTK ready`. There should be no `linux-webkitgtk-runtime-selected — Wk40` line saying `X11` — because we now select `WaylandSnapshot`. (The runtime selection log entry still fires with `Wk40` since that's the WebKitGTK lib variant, not the backend; the backend selection no longer has its own log line because we removed the SpeqaDebug warn.)

The clearest single check: there should be no `SpeqaDebug` lines at all in the log from this session — confirms the diagnostic logging was correctly removed.

- [ ] **Step 7: If anything in steps 3–6 fails**

Stop. Report which step failed and what you observed. Do NOT proceed to commit a "fix" without going back through the read-the-code → form-a-hypothesis → add-targeted-logging → reproduce loop. The whole point of this plan was to stop guessing; don't undo that now.

---

## Self-review notes

* **Spec coverage:** The "spec" for this change is the README rendering section (Task 1) — there's no separate spec document for embedding mode. Future-state matches code-state. ✓
* **Placeholder scan:** Every step contains exact paths, exact code, exact commands. No "TBD" or "similar to". ✓
* **Type consistency:** `LinuxWebKitBackend.WaylandSnapshot` is used in Task 2 and is the same enum value already defined in `LinuxWebKitBackend.kt` line 9. No new types introduced. ✓
* **No premature renaming:** The plan does NOT rename `WaylandSnapshot` to `Snapshot` — that's tempting (the name will read as misleading once X11 also uses it) but it's a cross-cutting refactor that adds risk and provides no functional value. Once the routing change is verified working in production, a follow-up plan can do the rename cleanly.
* **Verification ordering:** Local build (Task 4) is intentionally the last step. Tasks 1–3 each commit independently so the verification can pinpoint a regression if it occurs.
* **Project rule compliance:**
  * Documentation updated before code touches `.kt` (Task 1 first). ✓
  * Diagnostic logging removed after fix confirmed (Task 3). ✓
  * No destructive git commands; only `git add` + `git commit`. ✓
  * No "Co-Authored-By: Claude" in commit messages. ✓
  * Manual verification step explicit (Task 4 steps 3–6). ✓
