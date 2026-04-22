# Swing Migration Plan — Compose Multiplatform → Pure Swing

**Date:** 2026-04-21
**Status:** Proposed
**Scope:** Full UI rewrite from Compose Multiplatform / Jewel to pure Swing + JBUI, with Kotlin UI DSL v2 for dialogs and settings. No legacy branches, no compatibility shims, Compose dependencies removed from `build.gradle.kts` in the final step.

---

## 1. Goals and non-goals

**Goals**
- Single UI technology across the plugin: Swing + JBUI for custom surfaces, Kotlin UI DSL v2 for dialogs and `Configurable`s.
- Feature parity with the current Compose implementation, with two explicit exceptions (see §1 non-goals).
- All pure-logic modules and their unit tests preserved verbatim.
- Compose / Jewel / `composeUI()` removed from the Gradle build in the final commit of the migration branch.
- No intermediate state where `main` contains both Compose and Swing code paths.

**Non-goals (explicit UX trade-offs)**
- **Live-preview reorder (neighbors animate out of the way during step drag)** — not ported initially. Replaced by platform-native pattern: ghost + horizontal drop-indicator line + auto-scroll. May be reintroduced as an isolated decorator (`LivePreviewReorderDecorator`) after migration stabilises; adding it later is additive and does not require touching the drag skeleton.
- **Custom inline Markdown parser** (`InlineMarkdownRenderer` with its own emphasis/strikethrough/nested-span logic) — removed. Replaced by `org.intellij.markdown` parser + `JBHtmlPane`. Tests covering nested `**_email_**`, `~~…~~`, etc. are deleted because parsing is no longer owned by SpeQA.

---

## 2. Target architecture

### 2.1 One UI technology

- Custom editor surfaces (`TestCasePanel`, `TestRunPanel`, step cards, chips, attachments, links, references, toolbar): **pure Swing + JBUI**.
- Dialogs (`AddEditLinkDialog`, `RunCreationDialog`) and `SpeqaSettingsConfigurable`: **Kotlin UI DSL v2** (`com.intellij.ui.dsl.builder.panel`).
- Pre-existing Swing-based components (`SpeqaRenameDialog`, `AboutSpeqaAction`) unchanged.

### 2.2 Package layout after migration

```
editor/
  ui/
    TestCasePanel.kt                — JPanel, replaces TestCasePreview
    primitives/
      SpeqaPanels.kt                — SectionHeader, SurfaceDivider, CardSurface
      SpeqaTextFields.kt            — PlainTextInput, MultiLineInput wrappers
      SpeqaIconButton.kt            — ActionButton wrapper, hand-cursor by default
      MarkdownReadOnlyPane.kt       — JBHtmlPane wrapper
      HandCursor.kt                 — JComponent.handCursor() helper
      FocusTrail.kt                 — (pure) focus history stack
      DeleteFocusRestorer.kt        — Swing implementation over pure decision
      SpeqaFocusTraversalPolicy.kt  — LayoutFocusTraversalPolicy subclass
      CommitFlash.kt                — background colour pulse via Timer
    steps/
      StepsSection.kt               — container JPanel (BoxLayout.Y_AXIS)
      StepCard.kt                   — JPanel per step
      StepMetaRow.kt                — actor / verdict row
      DragReorderSupport.kt         — ghost + drop-indicator + auto-scroll
      DragDropIndexMath.kt          — (pure) target-index calculation
      DragAutoScroll.kt             — (pure) edge-zone speed calculation
    chips/
      TicketChip.kt
      TagCloud.kt
      MetadataChipActions.kt
    attachments/
      AttachmentList.kt
      AttachmentRow.kt
      AttachmentPreviewPopover.kt   — JBPopup
      AttachmentPreviewSizing.kt    — (pure)
    links/
      LinkList.kt
      LinkRow.kt
    references/
      ReferencesStrip.kt
    RichTooltipPlacement.kt         — (pure) placement math
    EditorToolbar.kt                — JToolBar
  dialogs/
    AddEditLinkDialog.kt            — Kotlin UI DSL v2
    RunCreationDialog.kt            — Kotlin UI DSL v2
  SpeqaPreviewEditor.kt             — mounts TestCasePanel directly
  SpeqaSplitEditor.kt               — unchanged (TextEditorWithPreview)
  ScrollSyncController.kt           — VisibleAreaListener ↔ AdjustmentListener
  IdStateHolder.kt                  — unchanged
run/
  TestRunPanel.kt                   — JPanel
  TestRunEditor.kt
  TestRunEditorProvider.kt
  TestRunSplitEditor.kt             — unchanged (TextEditorWithPreview)
  TestRunSupport.kt                 — unchanged
  RunTestCaseAction.kt              — unchanged
settings/
  SpeqaSettingsConfigurable.kt      — Kotlin UI DSL v2
```

Deleted: `LazyComposeMountController.kt`, `InlineMarkdownRenderer*`, `EditorPrimitives.kt` (replaced by `primitives/` package), all Jewel/Compose-specific focus / semantics / modifier helpers.

### 2.3 State management principles

- Each panel holds a reference to the current `TestCase` / `TestRun` in a field.
- UI changes call `onChange(newModel)`. Document patching stays on the existing `DocumentPatcher` / `PatchOperation` pipeline — unchanged.
- Document changes flow back via `updateFrom(model)` which **diffs against the current model** and updates only affected components, so focus is preserved inside fields being edited.
- No recomposition model. Explicit `rebuildSteps()`, `rebuildLinks()`, `rebuildAttachments()` are called only on structural changes (add/remove/reorder), never on per-keystroke text updates.

### 2.4 Compose → Swing mapping reference

| Compose / Jewel | Swing / JBUI |
|---|---|
| `JewelComposePanel` | `JBScrollPane(panel)` |
| `Column { }` / `Row { }` | `JPanel` + `BoxLayout` |
| `TextField`, `PlainTextInput` | `JBTextField` / `JBTextArea` in `JBScrollPane` |
| `ListComboBox` | IntelliJ `ComboBox<T>` |
| `IconButton` | `ActionButton` via `SpeqaIconButton` |
| `rememberUpdatedState(x)` | direct field read inside listener (always current) |
| `mutableStateOf` | field + explicit `revalidate()` / `repaint()` |
| `LaunchedEffect` | `javax.swing.Timer` / `Alarm` / `invokeLater` |
| `onGloballyPositioned` | `getBounds()` / `getLocationOnScreen()` |
| `Modifier.semantics { heading() }` | `AccessibleContext.setAccessibleRole(HEADING)` |
| `Modifier.clickableWithPointer()` | `handCursor()` + `MouseListener` |
| `Modifier.focusable()` | `setFocusable(true)` |
| Jewel `Text` with Markdown | `JBHtmlPane` + `org.intellij.markdown` |
| `animateFloatAsState` | `javax.swing.Timer` interpolator |
| `ScrollState` | `JBScrollPane.verticalScrollBar.value` |
| `Color(0xFF…)` / raw colour | `JBColor` or `UIManager.getColor(...)` |
| `focusProperties { previous = … }` | component order in tree + `FocusTraversalPolicy` |

---

## 3. Drag-and-drop reorder

**File:** `editor/ui/steps/DragReorderSupport.kt` (~250 LOC).

```kotlin
class DragReorderSupport(
    private val container: JPanel,
    private val scrollPane: JBScrollPane,
    private val onReorder: (fromIndex: Int, toIndex: Int) -> Unit,
) {
    fun attachHandle(card: JComponent, dragHandle: JComponent, index: () -> Int)
    fun detach()
}
```

**Mechanics**
1. Capture: `mousePressed` on drag-handle snapshots the card via `paint(Graphics)` into a `BufferedImage`; the ghost is added to `IdeGlassPane` at cursor position with alpha ≈ 0.85.
2. Move: `mouseDragged` moves ghost, computes `dropTargetIndex` via pure `DragDropIndexMath.calculateTargetIndex` (threshold 0.7 of adjacent card height, identical to current implementation).
3. Indicator: `StepsSection.paintChildren` draws a 2 px line in `SpeqaThemeColors.dropTarget` between the two cards at `dropTargetIndex`.
4. Auto-scroll: reused pure `DragAutoScroll.speed(...)` with 48 px edge zone and 12 px/frame cap; `javax.swing.Timer(16)` adjusts `scrollPane.verticalScrollBar.value`.
5. Drop: `mouseReleased` removes ghost, calls `onReorder(from, to)`, which goes through `PatchOperation.ReorderSteps` (unchanged).
6. Cancel: `Esc` or drop outside container → animated return to origin (`Timer`, 150 ms), then ghost removal. No model change.

**Preservation rules**
- Model mutates only on drop. Visual-only state during drag.
- `scrollBar.valueIsAdjusting = true` while auto-scroll is active. Existing `ScrollSyncController.suppressEditorToComposeSync(220 ms)` stays as post-drop safety net.
- Keyboard reorder: `Alt+Up` / `Alt+Down` action on focused step card (new, ~20 LOC).

**Pure modules preserved**
- `DragDropIndexMath` (extracted from current `StepsSection`) — `DropIndexMathTest` carries over unchanged.
- `DragAutoScroll` — unchanged; `DragAutoScrollTest` unchanged.

---

## 4. Focus and keyboard navigation

Current Compose code relies on an extensive graph of `focusRequester` / `focusProperties { previous / next }` wiring plus `QuietActionText` with manual Tab handling. All of this is replaced by:

1. **`SpeqaFocusTraversalPolicy`** (~80 LOC) — subclass of `LayoutFocusTraversalPolicy`. Installed on `TestCasePanel` and `TestRunPanel`. Obeys component insertion order with a small set of overrides:
   - Skip hidden placeholder buttons (`+ Add step`, `+ Expected`) unless visible.
   - Drag-handle is focusable but excluded from Tab chain (activation via Space/Enter triggers keyboard reorder mode).
   Unit-tested with a pure `FocusTraversalPolicyTest` over a mock component tree.

2. **`FocusTrail`** — pure logic kept verbatim. Integration swaps `onFocusChanged` for `FocusListener.focusGained` on `SpeqaTextField` / `SpeqaTextArea` base classes.

3. **`DeleteFocusRestorer`** — pure decision `nextFocusTargetAfterDelete(deletedIndex, sizeBefore)` retained; `DeleteFocusTargetTest` carries over. Swing implementation calls `requestFocusInWindow()` via `SwingUtilities.invokeLater` on the chosen target, mirroring the Compose contract. Used by `TicketChip`, `LinkRow`, `AttachmentRow`, `StepCard`.

4. **Focus ring** — native Look & Feel handles standard components. Custom clickable panels (chips, rows) paint focus via `DarculaUIUtil.paintFocusBorder` in `paintComponent`.

5. **`QuietActionText` replacement** — `LinkLabel<Unit>` (IntelliJ API) with muted foreground. Tab / Enter / Space work out of the box; no custom key handling required.

6. **`BackgroundFocusSinkPolicy`** — replaced by `setFocusable(true)` on the root panel plus `requestFocusInWindow()` on click-in-empty-area. Standard Swing idiom.

Eliminated: every `focusRequester`, `actionReverseEntryFocusRequester`, `expectedForwardEntryFocusRequester`, `stepActionReverseEntryFocusRequesters[]`, `focusProperties { … }` site in the current codebase (several hundred lines across `StepCard` and `StepsSection`).

---

## 5. Markdown, scroll-sync, hover, commit-flash, theming

**Inline Markdown (read-only text).** `MarkdownReadOnlyPane.setMarkdown(src)`:
```
org.intellij.markdown.parser.MarkdownParser(CommonMarkFlavourDescriptor())
  .buildMarkdownTreeFromString(src)
org.intellij.markdown.html.HtmlGenerator(src, ast, flavour).generateHtml()
JBHtmlPane.setText(html)
```
Single shared class across test-case preview and test-run preview (preserves the "shared renderer" contract from the current spec). Selection, copy, and link clicks handled by `JBHtmlPane` (hyperlinks routed through `BrowserUtil`).

**Scroll-sync.** Symmetrical to current implementation:
- `VisibleAreaListener` on left text editor → map leading line to target `JComponent` in right panel → `scrollRectToVisible(target.bounds)`.
- `AdjustmentListener` on right `JBScrollPane.verticalScrollBar` → inverse.
- `ScrollSyncController` suppress windows (220 ms around patches) carry over. `preservedVerticalOffset` snapshot/restore around `patchFromPreview` carries over.

**Hover popovers.** `JBPopup` triggered by `MouseListener.mouseEntered`/`mouseExited` with an `Alarm` (250 ms delay). Content: `JLabel(ImageIcon)` for images, `MarkdownReadOnlyPane` for markdown files. Placement math (`RichTooltipPlacement`) and sizing (`AttachmentPreviewSizing`) are pure and carry over.

**Commit-flash.** `CommitFlash.flash(component)`: `javax.swing.Timer(16 ms, duration 500 ms)` interpolates `setBackground` from `SpeqaThemeColors.commitFlash` back to the base component background. Triggered from the same patch-observer path as today.

**Theming.** `SpeqaThemeColors` stays as a single object; values switch from `androidx.compose.ui.graphics.Color` to `java.awt.Color` / `JBColor`. Sources:
- `accent`, `accentSubtle` → `JBUI.CurrentTheme.Link.Foreground.ENABLED` / derived.
- `destructive` → `UIManager.getColor("Component.errorFocusColor")`.
- `commitFlash` → `UIManager.getColor("Component.focusColor")`.
- `verdictPassed/Failed/Skipped/Blocked` → alpha-blended versions of the above, via `ColorUtil.withAlpha`.
- `dropTarget` aliases `accent`.
- Editor canvas background → `EditorColorsManager.getInstance().globalScheme.defaultBackground`.

On theme change: `LafManagerListener` + `EditorColorsListener` → rebuild cached colours and call `SwingUtilities.updateComponentTreeUI(root)`.

**Cursor policy.** All interactive custom panels use `handCursor()` (our helper). Standard Swing buttons and text fields use their L&F defaults. The existing project rule "all interactive elements show hand cursor" is preserved.

---

## 6. Pure modules and tests

**Carried over unchanged (Kotlin-only, no Compose imports):**
- `parser/TestCaseParser`, `parser/TestCaseSerializer`, `parser/TestRunParser`, `parser/TestRunSerializer`, `parser/DocumentPatcher`, `parser/DocumentRangeLocator`, `parser/SpeqaMarkdown`.
- `model/TestCase`, `model/TestRun`, `model/SpeqaDefaults`.
- `registry/*`, `refactoring/*`, `filetype/*`, `validation/SpeqaAnnotator`, `error/*`, `settings/SpeqaSettings`.
- `editor/IdStateHolder`, `editor/ScrollSyncController` (API unchanged; internals swap Compose scroll for Swing scroll).
- `editor/AttachmentRefactoringListener`, `editor/AttachmentSupport`, `editor/SpeqaLinkDestinationReferenceContributor`, `editor/SpeqaGotoFileHandler`, `editor/SpeqaEditorSupport`.
- `run/TestRunSupport`, `run/RunTestCaseAction`, `run/TestRunEditorProvider`, `run/TestRunSplitEditor`.

**Extracted / preserved pure modules (currently embedded in Compose files):**
- `DragAutoScroll` — already pure, moves to `steps/` package.
- `DragDropIndexMath` — extracted from `StepsSection.kt`.
- `DeleteFocusRestorer` — pure decision `nextFocusTargetAfterDelete` retained; Swing wrapper added.
- `FocusTrail` — pure stack retained.
- `RichTooltipPlacement` — already pure.
- `AttachmentPreviewSizing` — already pure.

**New tests:**
- `FocusTraversalPolicyTest` — pure tree traversal assertions.
- `MarkdownReadOnlyPaneTest` — smoke: input markdown → generated HTML contains `<strong>`, `<em>`, `<code>`, `<del>` tags for the representative samples.
- `CommitFlashTest` — pure interpolator test.

**Deleted tests:**
- `InlineMarkdownRendererTest` (parser ownership moves to platform).
- Any `JewelComposePanel`-specific smoke tests, if present.

---

## 7. Build changes (final commit of the migration branch)

Remove from `build.gradle.kts`:
```kotlin
plugins {
    // id("org.jetbrains.kotlin.plugin.compose") version "2.3.0"   ← delete
}
dependencies {
    intellijPlatform {
        // composeUI()                                              ← delete
    }
}
```

All Jewel / `androidx.compose.*` imports must be zero across `src/`. Verification: `grep -r "androidx.compose\|org.jetbrains.jewel" src/` returns empty.

---

## 8. Migration strategy — single feature branch, ordered commits

**Branch:** `swing-migration`. Lives off `main`; merged once at the end. Main never contains a mixed UI state.

**Ordered commits** (each compiles, each runs in sandbox). Revised after Step 1 investigation: existing dialogs (`AddEditLinkDialog`, `RunCreationDialog`) and `SpeqaSettingsConfigurable` are **already pure Swing** — no Compose in them — so converting them to Kotlin UI DSL v2 is deferred as optional polish and is not on the critical path of removing Compose. The remaining UI code is replaced bottom-up: leaf widgets first, then rows, then composite sections, then the two top-level panels, then the final editor-flip + Compose removal.

1. **Primitives, pure modules, Markdown pane, drag scaffolding.** ✅ Completed in commit `8b94d45`.
   - `editor/ui/primitives/*`: `HandCursor`, `SpeqaPanels`, `SpeqaTextFields`, `SpeqaIconButton`, `CommitFlash`, `MarkdownReadOnlyPane`, `SpeqaFocusTraversalPolicy`, `FocusTrail`, `DeleteFocusRestorer`.
   - `editor/ui/steps/`: `DragDropIndexMath` (pure + test), `DragReorderSupport` (scaffolding).
   - Existing `DragAutoScroll.kt` stays in place — moved when old `StepsSection` is deleted.
   - API deviations logged in commit message: `MarkdownReadOnlyPane` uses `JEditorPane` + `UIUtil.getHTMLEditorKit()` (not `JBHtmlPane` — unstable across 253-263); GFM flavour used for strikethrough.

2. **Chip tier: `TicketChip`, `TagCloud`, `InlineEditableIdRow`, `MetadataChipActions` logic.**
   - New Swing widgets under `editor/ui/chips/`.
   - Extract pure logic from `MetadataChipActions` (`truncateWithEllipsis`, `toIndexedFileMatch`) into a separate file with unit tests; Swing-facing popup actions separate.
   - No wiring to existing panels yet.

3. **Row tier: `AttachmentRow` / `AttachmentList` / `AttachmentPreviewPopover`; `LinkRow` / `LinkList`; `ReferencesStrip`.**
   - Swing ports under `editor/ui/attachments/`, `editor/ui/links/`, `editor/ui/references/`.
   - Reuses `DeleteFocusRestorer` (Swing).

4. **Step tier: `StepCard`, `StepMetaRow`, `StepsSection`, body-block section.**
   - Swing ports under `editor/ui/steps/` (`StepsSection` uses `DragReorderSupport` from Step 1).
   - `EditableBodyBlockSection` + `mergeBodyBlocks`/`replaceBodyBlocks` ported (latter two are already pure, move unchanged).

5. **Panel tier: `TestCasePanel`, new `TestRunPanel`, `EditorToolbar`.**
   - Swing composites assembling Steps 2–4 pieces.

6. **Editor flip + scroll-sync port + Compose deletion + build cleanup.** Single commit, atomic:
   - `SpeqaPreviewEditor` mounts `TestCasePanel` (Swing) instead of `JewelComposePanel`.
   - `TestRunEditor` mounts the new `TestRunPanel` (Swing).
   - `ScrollSyncController` internals switched to `JBScrollPane.verticalScrollBar` + `AdjustmentListener`; `preservedVerticalOffset` snapshot/restore preserved.
   - Delete all `@Composable` files and `LazyComposeMountController.kt`, old `EditorPrimitives.kt`.
   - Remove `kotlin.plugin.compose` and `composeUI()` from `build.gradle.kts`.
   - Verify `grep -r "androidx.compose\|org.jetbrains.jewel" src/` is empty.

7. **Spec update.**
   - Rewrite the UI-technology section of `docs/specs/2026-04-06-speqa-design.md` to describe the Swing implementation as the current state.
   - Remove obsolete rules tied to Compose (`rememberUpdatedState`, `clickableWithPointer`, `JewelComposePanel`, stale-closure Compose-effect rule).
   - Add Swing-specific rules: `handCursor()`, `SpeqaFocusTraversalPolicy`, `MarkdownReadOnlyPane` as the single shared read-only renderer, `CommitFlash` utility.

**Deferred (not on the Compose-removal critical path):** converting existing Swing dialogs (`AddEditLinkDialog`, `RunCreationDialog`, `SpeqaSettingsConfigurable`) from imperative `GridBagLayout`/`FormBuilder` to Kotlin UI DSL v2. Optional polish after Step 7.

**Before merge.** Rebase, squash any scaffolding-only commits that contain code deleted later in the branch, so `main` receives a clean forward-only sequence without legacy helpers.

---

## 9. Risks and mitigations

1. **Scroll-sync parity.** The 220 ms suppression windows and the `preservedVerticalOffset` snapshot/restore must behave identically. Mitigation: manual smoke scenario plus an integration-style test that patches a document and asserts `scrollBar.value` is unchanged.

2. **Custom focus ring fidelity.** Chip / row / attachment panels must look native on both Darcula and IntelliJ Light. Mitigation: use `DarculaUIUtil.paintFocusBorder`; visual check in both themes before each commit.

3. **Theme change in flight.** Swing requires `updateComponentTreeUI`. Mitigation: subscribe to `LafManagerListener` on the panel roots; invalidate cached colours in `SpeqaThemeColors`.

4. **Long step lists (50+).** Swing without virtualization could be slow. Current real-world cap is ≤20 steps; not optimising initially. If needed later, a `JBList` with a rich `ListCellRenderer` for read-only steps is a self-contained follow-up.

5. **Editable content inside reorderable containers.** `JBList`/`JBTable` editors are awkward for multi-field cards. Mitigation: use plain `JPanel` children in a `BoxLayout` container; drag via `DragReorderSupport`, not via `TransferHandler` on a list.

---

## 10. Acceptance checklist (manual, sandbox)

- Tab / Shift+Tab walk the full case editor and the full run editor in insertion order, in both themes.
- Drag-reorder of steps: ghost follows cursor, drop-indicator line is visible, auto-scroll triggers at top/bottom edges, drop commits, undo restores.
- Delete focus restoration on single / middle / last / only element for tickets, tags, links, attachments, steps.
- Commit flash fires on external document patches.
- Inline Markdown rendering (bold, italic, code, strike, nested) in all read-only text in both panels.
- Scroll-sync in both directions; no jitter after preview-initiated patch.
- Hover attachment preview: images render, markdown files render, placement respects viewport edges.
- Link click opens in external browser via `BrowserUtil`, URL scheme validated.
- Rename attachment updates all markdown references.
- Theme change on the fly (Darcula ↔ Light) repaints all panels correctly, no leftover colours.
- Both `.tc.md` and `.tr.md` files open through split editor; native left editor works (Markdown highlighting, completion) unaffected.
- `SpeqaSettingsConfigurable` opens and saves.
- Sandbox runs against IntelliJ 2026.1 with plugin verifier (`recommended()`).
- `grep -r "androidx.compose\|org.jetbrains.jewel" src/` returns no matches.
- `./gradlew compileKotlin` and `./gradlew test` green.

---

## 11. Out of scope for this migration

- Live-preview neighbor animation during drag (tracked as follow-up; ~400–500 LOC, self-contained decorator).
- Virtualization of step lists (not needed at current scale).
- Any feature additions not present in the current Compose implementation.
- Changes to parser/model/serialization layer.
