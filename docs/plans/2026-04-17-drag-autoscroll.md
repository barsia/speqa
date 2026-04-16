# Drag Auto-Scroll Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** When the user drags a step card toward the top or bottom edge of the visible preview viewport, the preview auto-scrolls in that direction so the user can reorder steps across long test cases without releasing the pointer.

**Architecture:** A pure function `DragAutoScroll.computeScrollDelta` takes the dragged item's bounds, the viewport bounds, an edge-zone size, and a max speed, and returns the per-frame scroll delta (negative = scroll up, positive = scroll down, 0 = no scroll). `StepsSection` receives `scrollState: ScrollState?` and a `viewportBounds: () -> Rect?` callback from `TestCasePreview`. A `LaunchedEffect` keyed on `draggedIndex` runs a `withFrameNanos` loop while dragging: each frame it reads the dragged item's current bounds, computes the delta, calls `scrollState.scrollBy(delta)` (which works even when user scroll is disabled via `enabled = false`), and adds the scrolled amount to `dragOffsetY` so the item stays glued to the pointer.

**Tech Stack:** Kotlin, Compose for Desktop (`androidx.compose.foundation.ScrollState`, `LayoutCoordinates.boundsInWindow()`, `withFrameNanos`), JUnit 5 for the pure-function tests.

---

## File Structure

| File | Role |
|------|------|
| `src/main/kotlin/io/github/barsia/speqa/editor/ui/DragAutoScroll.kt` | **Create.** Pure `object` with `computeScrollDelta` and constants `DEFAULT_EDGE_ZONE_PX`, `DEFAULT_MAX_SPEED_PX_PER_FRAME`. No Compose imports — testable as plain JUnit. |
| `src/test/kotlin/io/github/barsia/speqa/editor/ui/DragAutoScrollTest.kt` | **Create.** Unit tests covering viewport-middle, top-edge partial/full, bottom-edge partial/full, item larger than viewport. |
| `src/main/kotlin/io/github/barsia/speqa/editor/ui/TestCasePreview.kt` | **Modify.** Measure scroll container viewport bounds via `onGloballyPositioned`; pass `scrollState` + `viewportBounds` lambda into `StepsSection`. |
| `src/main/kotlin/io/github/barsia/speqa/editor/ui/StepsSection.kt` | **Modify.** Accept new params, track dragged item's `LayoutCoordinates`, run auto-scroll `LaunchedEffect` loop, compensate `dragOffsetY`. |
| `docs/specs/2026-04-06-speqa-design.md` | **Modify.** Add a single bullet describing the auto-scroll behavior. Spec edit must precede `.kt` edits (session hook). |

---

## Task 1: Pure `DragAutoScroll` Function (TDD)

**Files:**
- Create: `src/main/kotlin/io/github/barsia/speqa/editor/ui/DragAutoScroll.kt`
- Test: `src/test/kotlin/io/github/barsia/speqa/editor/ui/DragAutoScrollTest.kt`

Contract:
- If the item's top is above `viewport.top + edgeZone`, scroll up (negative delta) proportional to how far into the zone the top is. Cap at `-maxSpeed`.
- If the item's bottom is below `viewport.bottom - edgeZone`, scroll down (positive delta). Cap at `+maxSpeed`.
- Otherwise, return 0.
- If item is larger than viewport and straddles both edges, prefer whichever edge the item penetrates deeper (larger penetration wins).

- [ ] **Step 1.1: Write the failing test**

Create `src/test/kotlin/io/github/barsia/speqa/editor/ui/DragAutoScrollTest.kt`:

```kotlin
package io.github.barsia.speqa.editor.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DragAutoScrollTest {
    private val edgeZone = 40f
    private val maxSpeed = 20f

    @Test
    fun `item in middle of viewport returns zero`() {
        val delta = DragAutoScroll.computeScrollDelta(
            itemTop = 300f, itemBottom = 340f,
            viewportTop = 100f, viewportBottom = 600f,
            edgeZonePx = edgeZone, maxSpeedPxPerFrame = maxSpeed,
        )
        assertEquals(0f, delta)
    }

    @Test
    fun `item top fully inside top edge zone returns max negative speed`() {
        // viewportTop=100, edgeZone=40, so zone is [100, 140]. itemTop=100 → penetration=40 → full speed up.
        val delta = DragAutoScroll.computeScrollDelta(
            itemTop = 100f, itemBottom = 140f,
            viewportTop = 100f, viewportBottom = 600f,
            edgeZonePx = edgeZone, maxSpeedPxPerFrame = maxSpeed,
        )
        assertEquals(-maxSpeed, delta)
    }

    @Test
    fun `item top partially in top edge zone returns proportional negative speed`() {
        // itemTop=120 → 20px into 40px zone → 0.5 * maxSpeed = 10 → -10
        val delta = DragAutoScroll.computeScrollDelta(
            itemTop = 120f, itemBottom = 160f,
            viewportTop = 100f, viewportBottom = 600f,
            edgeZonePx = edgeZone, maxSpeedPxPerFrame = maxSpeed,
        )
        assertEquals(-10f, delta)
    }

    @Test
    fun `item bottom fully inside bottom edge zone returns max positive speed`() {
        // viewportBottom=600, edgeZone=40, zone is [560, 600]. itemBottom=600 → full speed down.
        val delta = DragAutoScroll.computeScrollDelta(
            itemTop = 560f, itemBottom = 600f,
            viewportTop = 100f, viewportBottom = 600f,
            edgeZonePx = edgeZone, maxSpeedPxPerFrame = maxSpeed,
        )
        assertEquals(maxSpeed, delta)
    }

    @Test
    fun `item bottom partially in bottom edge zone returns proportional positive speed`() {
        // itemBottom=580 → 20px into 40px zone → 0.5 * maxSpeed = 10 → +10
        val delta = DragAutoScroll.computeScrollDelta(
            itemTop = 540f, itemBottom = 580f,
            viewportTop = 100f, viewportBottom = 600f,
            edgeZonePx = edgeZone, maxSpeedPxPerFrame = maxSpeed,
        )
        assertEquals(10f, delta)
    }

    @Test
    fun `item larger than viewport uses deeper penetration`() {
        // itemTop=90 (penetration 50 past top), itemBottom=605 (penetration 45 past bottom)
        // Top penetration is deeper → scroll up at max speed.
        val delta = DragAutoScroll.computeScrollDelta(
            itemTop = 90f, itemBottom = 605f,
            viewportTop = 100f, viewportBottom = 600f,
            edgeZonePx = edgeZone, maxSpeedPxPerFrame = maxSpeed,
        )
        assertTrue(delta < 0f, "expected negative (scroll up), got $delta")
        assertEquals(-maxSpeed, delta)
    }

    @Test
    fun `item top above viewport is clamped to max negative speed`() {
        val delta = DragAutoScroll.computeScrollDelta(
            itemTop = 50f, itemBottom = 90f,
            viewportTop = 100f, viewportBottom = 600f,
            edgeZonePx = edgeZone, maxSpeedPxPerFrame = maxSpeed,
        )
        assertEquals(-maxSpeed, delta)
    }

    @Test
    fun `zero-height viewport returns zero`() {
        val delta = DragAutoScroll.computeScrollDelta(
            itemTop = 0f, itemBottom = 0f,
            viewportTop = 0f, viewportBottom = 0f,
            edgeZonePx = edgeZone, maxSpeedPxPerFrame = maxSpeed,
        )
        assertEquals(0f, delta)
    }
}
```

- [ ] **Step 1.2: Run the test to verify it fails**

Run: `./gradlew test --tests "io.github.barsia.speqa.editor.ui.DragAutoScrollTest" 2>&1 | tail -20`
Expected: compilation error — `Unresolved reference: DragAutoScroll`.

- [ ] **Step 1.3: Implement the function**

Create `src/main/kotlin/io/github/barsia/speqa/editor/ui/DragAutoScroll.kt`:

```kotlin
package io.github.barsia.speqa.editor.ui

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Pure math for drag auto-scroll. No Compose / platform dependencies — unit-testable.
 *
 * Returns the per-frame scroll delta in pixels:
 * - Negative → scroll viewport up (content moves down).
 * - Positive → scroll viewport down (content moves up).
 * - Zero    → no scroll.
 *
 * Speed is proportional to how deep the item penetrates the edge zone,
 * capped at [maxSpeedPxPerFrame]. When the item is larger than the viewport
 * and overlaps both zones, the deeper penetration wins.
 */
internal object DragAutoScroll {
    const val DEFAULT_EDGE_ZONE_DP = 48f
    const val DEFAULT_MAX_SPEED_DP_PER_FRAME = 12f

    fun computeScrollDelta(
        itemTop: Float,
        itemBottom: Float,
        viewportTop: Float,
        viewportBottom: Float,
        edgeZonePx: Float,
        maxSpeedPxPerFrame: Float,
    ): Float {
        val viewportHeight = viewportBottom - viewportTop
        if (viewportHeight <= 0f || edgeZonePx <= 0f) return 0f

        // Effective zone size can't exceed half the viewport — otherwise zones overlap.
        val zone = min(edgeZonePx, viewportHeight / 2f)

        val topZoneBottom = viewportTop + zone
        val bottomZoneTop = viewportBottom - zone

        // Penetration depth (≥ 0 means "inside the zone").
        val topPenetration = max(0f, topZoneBottom - itemTop)
        val bottomPenetration = max(0f, itemBottom - bottomZoneTop)

        if (topPenetration == 0f && bottomPenetration == 0f) return 0f

        // Larger penetration wins.
        return if (topPenetration >= bottomPenetration) {
            val fraction = min(1f, topPenetration / zone)
            -fraction * maxSpeedPxPerFrame
        } else {
            val fraction = min(1f, bottomPenetration / zone)
            fraction * maxSpeedPxPerFrame
        }
    }
}
```

- [ ] **Step 1.4: Run the test to verify it passes**

Run: `./gradlew test --tests "io.github.barsia.speqa.editor.ui.DragAutoScrollTest" 2>&1 | grep -E "FAILED|BUILD|Tests "`
Expected: all 8 tests pass, `BUILD SUCCESSFUL`.

- [ ] **Step 1.5: Commit**

```bash
git add src/main/kotlin/io/github/barsia/speqa/editor/ui/DragAutoScroll.kt src/test/kotlin/io/github/barsia/speqa/editor/ui/DragAutoScrollTest.kt
git commit -m "Add pure DragAutoScroll edge-zone math"
```

---

## Task 2: Spec entry for auto-scroll behavior

**Files:**
- Modify: `docs/specs/2026-04-06-speqa-design.md`

The project CLAUDE.md requires a meaningful spec edit before any `.kt` edit in a session. This task satisfies that hook for the rest of the plan.

- [ ] **Step 2.1: Add the spec bullet**

Find the "Step actions" / drag-handle region (search for "drag to reorder" and "stepDragActive"). Append this bullet after the existing drag-handle behavior bullets:

```markdown
- **Drag auto-scroll:** when the user drags a step card whose current bounds overlap the top or bottom edge zone of the preview viewport (zone size `DragAutoScroll.DEFAULT_EDGE_ZONE_DP`, 48dp), the preview scrolls in that direction. Speed is proportional to how far the item penetrates the zone, capped at `DragAutoScroll.DEFAULT_MAX_SPEED_DP_PER_FRAME` (12dp/frame). Implementation: `TestCasePreview` owns `scrollState` and exposes the scroll container's `boundsInWindow()` to `StepsSection`; `StepsSection` tracks the dragged `Box`'s `boundsInWindow()` and runs a `LaunchedEffect` loop keyed on `draggedIndex` that calls `scrollState.scrollBy(delta)` on each frame via `withFrameNanos`. The per-frame delta is added to `dragOffsetY` so the dragged card stays glued to the pointer while the viewport scrolls underneath. `scrollBy` is a programmatic API — it works even though user scroll is disabled via `verticalScroll(enabled = !stepDragActive)` during drag. Edge-zone math lives in the pure `DragAutoScroll` object and is covered by `DragAutoScrollTest` (no Compose dependencies).
```

- [ ] **Step 2.2: Commit**

```bash
git add docs/specs/2026-04-06-speqa-design.md
git commit -m "Spec drag auto-scroll behavior"
```

---

## Task 3: Expose viewport bounds and `scrollState` from `TestCasePreview` to `StepsSection`

**Files:**
- Modify: `src/main/kotlin/io/github/barsia/speqa/editor/ui/TestCasePreview.kt`

Currently `scrollState` is local to `TestCasePreview` and `StepsSection` has no viewport awareness. We expose two things:
1. The `scrollState` itself (needed for `scrollBy`).
2. A lambda returning the scroll container's current `Rect` in window coordinates (needed for edge-zone math).

- [ ] **Step 3.1: Add viewport bounds state in `TestCasePreview`**

Read `src/main/kotlin/io/github/barsia/speqa/editor/ui/TestCasePreview.kt` around line 107–165.

Add imports (top of file, grouped with existing `androidx.compose.ui.*` imports):

```kotlin
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
```

Then replace the existing scroll `Column` block (currently around line 159–163):

```kotlin
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState, enabled = !stepDragActive)
                .padding(start = SpeqaLayout.pagePadding, end = SpeqaLayout.pagePadding, top = SpeqaLayout.compactGap, bottom = SpeqaLayout.pagePadding),
            verticalArrangement = Arrangement.spacedBy(SpeqaLayout.blockGap),
        ) {
```

with:

```kotlin
        var viewportCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { viewportCoordinates = it }
                .verticalScroll(scrollState, enabled = !stepDragActive)
                .padding(start = SpeqaLayout.pagePadding, end = SpeqaLayout.pagePadding, top = SpeqaLayout.compactGap, bottom = SpeqaLayout.pagePadding),
            verticalArrangement = Arrangement.spacedBy(SpeqaLayout.blockGap),
        ) {
```

`onGloballyPositioned` is placed **before** `verticalScroll` so the measured bounds represent the visible viewport, not the full scrolled content. If it were after, bounds would include the hidden scrolled area.

Note: `mutableStateOf` and `remember`, `getValue`/`setValue` are already imported in this file; verify with a quick grep if compilation complains.

- [ ] **Step 3.2: Pass `scrollState` and viewport lambda into `StepsSection`**

Locate the `StepsSection(` call (currently around line 256–265). Replace it with:

```kotlin
                StepsSection(
                    testCase = testCase,
                    onPatch = patch,
                    project = project,
                    tcFile = file,
                    focusRequestStepIndex = focusRequestStepIndex,
                    onFocusRequestStepIndexChange = { focusRequestStepIndex = it },
                    onStepDragActiveChange = { stepDragActive = it },
                    attachmentRevision = attachmentRevision,
                    scrollState = scrollState,
                    viewportBounds = { viewportCoordinates?.takeIf { it.isAttached }?.boundsInWindow() },
                )
```

The lambda captures `viewportCoordinates` by reference each call — no need for `rememberUpdatedState`. `isAttached` guards against calling `boundsInWindow()` on a detached node during disposal.

- [ ] **Step 3.3: Compile**

Compilation will fail because `StepsSection` doesn't yet accept these params — that's expected. Skip the build until Task 4.

- [ ] **Step 3.4: No commit yet**

Leave changes staged but uncommitted. This commit is combined with Task 4 because `TestCasePreview` can't compile alone.

---

## Task 4: Accept new params in `StepsSection` and track dragged item bounds

**Files:**
- Modify: `src/main/kotlin/io/github/barsia/speqa/editor/ui/StepsSection.kt`

- [ ] **Step 4.1: Add imports**

Append to the existing import block at the top:

```kotlin
import androidx.compose.foundation.ScrollState
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
```

- [ ] **Step 4.2: Extend the function signature**

Replace the existing parameter list (currently `StepsSection.kt:39–48`) with:

```kotlin
@Composable
internal fun StepsSection(
    testCase: TestCase,
    onPatch: (TestCase, PatchOperation) -> Unit,
    project: Project? = null,
    tcFile: VirtualFile? = null,
    focusRequestStepIndex: Int = -1,
    onFocusRequestStepIndexChange: (Int) -> Unit = {},
    onStepDragActiveChange: (Boolean) -> Unit = {},
    attachmentRevision: Long = 0L,
    scrollState: ScrollState? = null,
    viewportBounds: () -> Rect? = { null },
) {
```

`scrollState` and `viewportBounds` are nullable/default so headless tests or non-scrolling hosts still compile.

- [ ] **Step 4.3: Track the dragged item's `LayoutCoordinates`**

Inside `StepsSection`, just after the existing `var itemHeights by remember { … }` line (around `StepsSection.kt:51`), add:

```kotlin
    var draggedItemCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
```

Then modify the per-step `Box` (currently around `StepsSection.kt:110–134`) — specifically its `.onGloballyPositioned` block — to **also** capture coordinates when this index is the dragged one. Replace the existing `.onGloballyPositioned` block:

```kotlin
                            .onGloballyPositioned { coordinates ->
                                if (draggedIndex >= 0) return@onGloballyPositioned
                                val h = coordinates.size.height
                                if (itemHeights[index] != h) {
                                    itemHeights = itemHeights + (index to h)
                                }
                            }
```

with:

```kotlin
                            .onGloballyPositioned { coordinates ->
                                if (draggedIndex == index) {
                                    draggedItemCoordinates = coordinates
                                }
                                if (draggedIndex >= 0) return@onGloballyPositioned
                                val h = coordinates.size.height
                                if (itemHeights[index] != h) {
                                    itemHeights = itemHeights + (index to h)
                                }
                            }
```

The split ordering is deliberate: we update `draggedItemCoordinates` even when a drag is active (that's the whole point), but we skip the `itemHeights` refresh during drag to keep drop-target math stable.

- [ ] **Step 4.4: Reset coordinates on drag end**

Find the existing `onDragEnd()` function inside `StepsSection` (currently around `StepsSection.kt:69–84`) and add a reset at its top, right after `val tc = currentTestCase`:

Replace:

```kotlin
    fun onDragEnd() {
        val fromIndex = draggedIndex
        val currentOffsetY = dragOffsetY
        val tc = currentTestCase
        draggedIndex = -1
        dragOffsetY = 0f
        onStepDragActiveChange(false)
```

with:

```kotlin
    fun onDragEnd() {
        val fromIndex = draggedIndex
        val currentOffsetY = dragOffsetY
        val tc = currentTestCase
        draggedIndex = -1
        dragOffsetY = 0f
        draggedItemCoordinates = null
        onStepDragActiveChange(false)
```

- [ ] **Step 4.5: Compile**

Run: `./gradlew compileKotlin 2>&1 | grep -E "^e:|BUILD"`
Expected: `BUILD SUCCESSFUL`. Any error means a missing import or a typo — fix and re-run before continuing.

- [ ] **Step 4.6: No commit yet**

Hold — the auto-scroll loop in Task 5 makes this commit a coherent, working change.

---

## Task 5: Auto-scroll loop with `withFrameNanos`

**Files:**
- Modify: `src/main/kotlin/io/github/barsia/speqa/editor/ui/StepsSection.kt`

This is the core behavior: while a drag is active, each frame compute the desired delta, call `scrollState.scrollBy(delta)`, and add the scrolled amount to `dragOffsetY` so the dragged card stays attached to the pointer.

- [ ] **Step 5.1: Add imports**

Append to `StepsSection.kt` imports:

```kotlin
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalDensity
```

- [ ] **Step 5.2: Read density for dp→px conversion**

Inside `StepsSection`, just after `var draggedItemCoordinates by remember { … }` from Step 4.3, add:

```kotlin
    val density = LocalDensity.current
    val edgeZonePx = with(density) { DragAutoScroll.DEFAULT_EDGE_ZONE_DP.dp.toPx() }
    val maxSpeedPxPerFrame = with(density) { DragAutoScroll.DEFAULT_MAX_SPEED_DP_PER_FRAME.dp.toPx() }
```

- [ ] **Step 5.3: Add the auto-scroll `LaunchedEffect`**

Inside `StepsSection`, after the `LaunchedEffect(pendingAddStepFocus) { … }` block (currently `StepsSection.kt:62–67`), insert a new effect:

```kotlin
    LaunchedEffect(draggedIndex) {
        if (draggedIndex < 0) return@LaunchedEffect
        val state = scrollState ?: return@LaunchedEffect
        while (true) {
            withFrameNanos { }
            if (draggedIndex < 0) break
            val itemBounds = draggedItemCoordinates
                ?.takeIf { it.isAttached }
                ?.boundsInWindow()
                ?: continue
            val viewport = viewportBounds() ?: continue
            val delta = DragAutoScroll.computeScrollDelta(
                itemTop = itemBounds.top,
                itemBottom = itemBounds.bottom,
                viewportTop = viewport.top,
                viewportBottom = viewport.bottom,
                edgeZonePx = edgeZonePx,
                maxSpeedPxPerFrame = maxSpeedPxPerFrame,
            )
            if (delta == 0f) continue
            val consumed = state.scrollBy(delta)
            // Keep the dragged card glued to the pointer: shift our internal offset
            // by the amount the viewport actually scrolled (`consumed`, may be less
            // than `delta` at the scroll ends).
            if (consumed != 0f) {
                dragOffsetY += consumed
            }
        }
    }
```

Keying on `draggedIndex` alone is intentional — `scrollState` and `viewportBounds` are captured by reference. Re-keying on those would restart the loop on every recomposition.

**Note on `scrollBy`'s sign and `dragOffsetY`:** `ScrollState.scrollBy(positive)` scrolls the content **up** (viewport moves down). A step card being dragged toward the bottom edge has viewport moving down underneath it — to keep it under the pointer, `dragOffsetY` must increase by the same amount we scrolled. `scrollBy(positive) → consumed > 0 → dragOffsetY += consumed` is correct; symmetric for negative. Verify this manually in Step 5.6 — if the card drifts, flip the sign on `dragOffsetY +=`.

- [ ] **Step 5.4: Compile**

Run: `./gradlew compileKotlin 2>&1 | grep -E "^e:|BUILD"`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5.5: Run the existing test suite to confirm nothing regressed**

Run: `./gradlew test 2>&1 | grep -E "FAILED|BUILD|Tests "`
Expected: all tests pass, including the new `DragAutoScrollTest`. If a pre-existing test failed, the failure is unrelated to this plan — investigate via `./gradlew test --tests <ClassName>`.

- [ ] **Step 5.6: Manual smoke test in sandbox**

Run: `./gradlew runIde 2>&1 | tail -5` (start in background if preferred).

In the sandbox IDE:
1. Open or create a `.tc.md` test case with ~15 steps so the preview scrolls.
2. Scroll to the middle of the steps list.
3. Grab the drag handle on a middle step. Drag toward the top of the preview window — the preview should auto-scroll up, and the dragged step should stay under the pointer (not drift).
4. Drag toward the bottom — preview auto-scrolls down.
5. Drop the step; verify it lands at the expected index and the scroll position is sensible.
6. Repeat on both light and dark themes.

Pass criteria:
- Auto-scroll kicks in within ~48dp of either edge.
- Dragged card visibly follows the pointer; does not lag or drift.
- Releasing the pointer commits the reorder at the correct index.
- No jitter, no infinite scroll past top/bottom bounds (`scrollBy` returns `0f` at bounds).

Fail signals and fixes:
- Card drifts away from pointer as it scrolls → sign flip on `dragOffsetY +=` (swap to `-=`). Update Step 5.3 accordingly.
- Auto-scroll never fires → check `viewportBounds()` is non-null (log once from inside the `LaunchedEffect`); confirm `onGloballyPositioned` on `TestCasePreview`'s `Column` is BEFORE `verticalScroll`, not after.
- Auto-scroll runs forever after drop → confirm `draggedItemCoordinates = null` in `onDragEnd()`; the `LaunchedEffect` exits on `draggedIndex < 0`.

- [ ] **Step 5.7: Commit Tasks 3 + 4 + 5 together**

```bash
git add src/main/kotlin/io/github/barsia/speqa/editor/ui/TestCasePreview.kt src/main/kotlin/io/github/barsia/speqa/editor/ui/StepsSection.kt
git commit -m "Auto-scroll preview when dragging steps near viewport edges"
```

---

## Task 6: Final verification

**Files:** none modified — this task is verification only.

- [ ] **Step 6.1: Full build and tests**

Run: `./gradlew build 2>&1 | tail -20`
Expected: `BUILD SUCCESSFUL`. If it fails, address the root cause before closing out.

- [ ] **Step 6.2: Regression check on drag without scrolling**

In the sandbox IDE, open a short test case (3–4 steps, fits entirely in the viewport). Drag and drop steps. The existing drop-target indicator, reordering, and drop-at-index behavior must still work — auto-scroll should never engage because the item bounds stay well inside the viewport.

- [ ] **Step 6.3: Regression check on `TestRunPanel`**

`TestRunPanel` has its own preview and drag handling (not in scope here). Open a `.tr.md` file and confirm nothing regressed — drag behavior there is unchanged because `TestRunPanel` does not wire `scrollState`/`viewportBounds`.

- [ ] **Step 6.4: Final commit check**

Run: `git log --oneline -5`
Expected: three new commits — one for the pure function + tests, one for the spec, one for the UI wiring.

---

## Self-Review Notes

- **Spec coverage:** the single spec bullet in Task 2 covers the behavior, the constants, the implementation split (pure function vs Compose wiring), and why `scrollBy` works under `enabled = false`.
- **Placeholder scan:** every step has exact file paths, exact imports, and exact code. No TBDs, no "handle edge cases", no "similar to above".
- **Type consistency:**
  - `DragAutoScroll.computeScrollDelta` signature appears identically in Tasks 1.3 and 5.3.
  - `DEFAULT_EDGE_ZONE_DP` / `DEFAULT_MAX_SPEED_DP_PER_FRAME` constant names match between Tasks 1.3, 2.1, and 5.2.
  - `viewportBounds: () -> Rect?` signature matches between Tasks 3.2 and 4.2.
  - `scrollState: ScrollState?` matches between Tasks 3.2 and 4.2.

## Risks

1. **Sign of `dragOffsetY +=` is empirical.** Compose `ScrollState.scrollBy` direction vs `translationY` direction must agree. The plan chooses `+=` based on convention; Step 5.6 validates. If wrong, the card drifts — flip the sign.
2. **Viewport `onGloballyPositioned` placement.** Must be BEFORE `verticalScroll` in the modifier chain so `boundsInWindow()` returns the viewport, not the scrolled content. Task 3.1 places it correctly; do not move it.
3. **Re-keying the `LaunchedEffect`.** Keying on anything other than `draggedIndex` (e.g., `scrollState`, `dragOffsetY`) would restart the loop mid-drag and is wrong. Task 5.3 keys on `draggedIndex` only.
4. **`scrollBy` at scroll bounds.** `scrollBy` returns the actually consumed delta (clamped to [0, maxValue]). The plan handles this by compensating `dragOffsetY` with `consumed`, not `delta`. At bounds, `consumed == 0f` and nothing moves — the correct outcome.

---

## Execution Handoff

Plan complete and saved to `docs/plans/2026-04-17-drag-autoscroll.md`. Two execution options:

**1. Subagent-Driven (recommended)** — I dispatch a fresh subagent per task, review between tasks, fast iteration.

**2. Inline Execution** — Execute tasks in this session using executing-plans, batch execution with checkpoints.

Which approach?
