# SpeQA — Product Requirements Document & Design Spec

> **Renamed from SpeQA to SpeQA** — "Specification-driven QA". Speak quality.

**Date:** 2026-04-06
**Status:** Approved
**Approach:** Custom Editor + Swing UI (Approach A)

---

## 1. Product Overview

**SpeQA** — IntelliJ IDEA plugin for manual QA engineers and automation testers. A convenient tool for creating, editing, and running manual test cases directly inside the IDE.

**Target audience:**
- Manual QA engineers (primary)
- Automation testers who draft manual cases before automating

**Distribution:** JetBrains Marketplace (public plugin)
**License:** Apache License 2.0 (copyright holder: SpeQA Contributors)

**Open-source release hygiene:**
- All debug logging (`SpeqaDebug` logger, `SpeqaDebugLog` utility) is removed from production code. Debug logging is a development aid, not shipped in releases
- No personal names in test data or examples; use generic identifiers like "QA Engineer"
- No absolute file paths in documentation
- `.gitignore` excludes secrets and credential files (`.env*`, `*.pem`, `*.key`, `*.p12`, `*.jks`, `local.properties`)
- `BrowserUtil.browse(link.url)` opens any URL without scheme restriction — the user owns their test case content and controls what links they add
- JetBrains Marketplace: plugin icons 40x40, semver versioning, signPlugin/publishPlugin config, vendor email
- `SpeqaFrontmatterSchemaProviderFactory` uses no debug logging; it was only used during initial development
- `SpeqaPreviewEditor` uses no debug logging; `refreshLog` and `debugLog` declarations and all their `.warn(...)` calls are removed. The `writeFromPreview` and `patchFromPreview` methods retain their functional logic without log statements. The fallback catch block in `patchFromPreview` still performs full serialization on failure but without logging the exception
- `SpeqaFrontmatterSchemaProviderFactory` and its private `SpeqaFrontmatterSchemaFileProvider` contain no logger declarations or log calls; the `isAvailable` method returns the match result directly
- `LinkList.kt` validates URLs before opening: `BrowserUtil.browse` is called only when `link.url.matches(Regex("^https?://.*"))`. Link deletion shows `Messages.showOkCancelDialog` confirmation using `dialog.removeLink.message` / `dialog.removeLink.title` bundle keys; only removes the link when user clicks OK

**Tech stack:**
- Kotlin 2.3.x, IntelliJ Platform SDK (sandbox/build target: IntelliJ IDEA 2026.1)
- Plugin compatibility baseline: build branches `253`–`263` (IntelliJ 2025.3.4–2026.3)
- `Disposer.isDisposed()` is deprecated but replacement not available in target range. Use `@Suppress("DEPRECATION")` at call sites.
- Avoid internal APIs: use `StartupManager.runWhenProjectIsInitialized` (public API) instead of `runAfterOpened` (internal). Wrap file open in it to ensure FileEditorManager is ready. Callers don't need extra `invokeLater`; `openInitialTestCase` handles timing internally. Also selects the file in the project tree via `ProjectView.selectPsiElement`. The Add Step button sits inside the steps block column (same `blockGap` as between steps). All SpeqaDebug diagnostic logging removed from all files.
- Avoid scheduled-for-removal APIs: use `FileChooser.chooseFile` + `addActionListener` on `TextFieldWithBrowseButton`.
- Use `runReadAction<T> {}` instead of `ReadAction.compute<T, Throwable> {}`. Suppress deprecation since replacement requires coroutines not available in sync context.
- All source files use `Speqa` prefix (no legacy names).
- When the user clicks "+ Add step" or "+ Expected", focus moves to the new input field immediately: the newly created `StepCard` (or its expected text area) requests focus once it is added to the panel.
- File template name in `addKind()` must exactly match the `.ft` filename: `SpeQA Test Case.tc.md`.
- `SpeqaSplitEditor` overrides `setState` as no-op (same pattern as `TestRunSplitEditor`). Do not pass explicit layout to constructor - rely on default `SHOW_EDITOR_AND_PREVIEW`.
- `DirectoryProjectGeneratorBase` type parameter must be `Any` (not `Unit`) — WebStorm calls `generateProject` via Java reflection, and `Object` cannot be cast to `kotlin.Unit` across classloaders.
- `generateProject` must wrap VFS mutations in `runWriteAction` — not all IDEs provide a write action context for `DirectoryProjectGeneratorBase.generateProject`.

### Editor tab entry point (three-dot menu)

For `.tc.md` and `.tr.md` files, the three-dot menu (`EditorTabsEntryPoint`) contains:
- **Sync Scroll** — toggle scroll synchronization between text and preview
- **About SpeQA** — compact dialog with plugin logo (scaled 2.5x) on the left; on the right: "SpeQA" title, "Test Management System" in small font, version, vendor name+email, and links (Docs, GitHub, Author). Close button only (uses `javax.swing.Action.NAME` to rename OK action, Two buttons: "Copy and Close" (OK action, default) and "Close" (cancel action renamed). Menu item uses grey Q icon. No email shown. All info panel children left-aligned. Logo top-aligned via `NORTH` in a wrapper panel. Dialog 20% wider. Links row ordered specific → general: Docs (`https://barsia.github.io/speqa`) | GitHub (plugin repo) | Author (`https://barsia.github.io`). Below: icon+text links for Report Bug (bug.svg) and Request Feature (feature.svg) Layout uses `GridBagLayout` for precise alignment. Links use `HyperlinkLabel`. Bug/Feature links use icon `JBLabel` + `HyperlinkLabel` pairs in a sub-panel for reliable icon rendering. Version shown next to title without "Version" prefix (e.g. "SpeQA v0.1.0"). Subtitle has top padding for visual separation from title. Issue links (bug/feature with icons) before the Docs/GitHub/Author row. All links use smaller font (Label.font - 2f). Equal spacing around subtitle. Bug/Feature use `createClickableIconLink` — single clickable panel (icon + HyperlinkLabel) with hand cursor on entire area. Subtitle spacing: 2px top, 2px bottom. Bug URL prefills `environment` field (e.g. "SpeQA 0.1.0 | IntelliJ IDEA 2026.1.1") via URL query param. Uses `fullApplicationName` (already includes version). `.yml` templates. Both URLs constructed once in `actionPerformed` and passed to helpers. Subtitle, version, and vendor labels use font size - 1f. Subtitle/version use disabledForeground; vendor uses `JBColor` blending disabled and normal foreground at 50%. Logo has small top padding to align with title. Outer panel uses reduced top padding.
- UI layer: pure Swing + JBUI for custom editor surfaces, Kotlin UI DSL v2 for dialogs and settings. `TestCasePanel` (at `editor/ui/TestCasePanel.kt`) and the test-run panel (built by `run/TestRunEditor.kt`) are `JPanel`-based surfaces mounted by `SpeqaPreviewEditor` / `TestRunEditor` inside a `JBScrollPane`. Shared read-only inline Markdown rendering is owned by `editor/ui/primitives/MarkdownPane.kt` and `MarkdownEditablePane.kt`, fed by `org.intellij.markdown` parser output; SpeQA does not own any custom inline-Markdown parser. The UI is native Swing only: `build.gradle.kts` declares no Compose/Jewel dependencies and `src/` contains no `androidx.compose.*` or `org.jetbrains.jewel.*` references.
- **Swing engineering rules:**
    - All interactive elements call `handCursor()` (helper in `editor/ui/primitives/HandCursor.kt`) — never rely on the bare Swing default cursor on custom panels. Standard `JButton` / `JBTextField` / `ComboBox` use their native L&F cursors.
    - Every `JPanel` that roots a focusable subtree installs `SpeqaFocusTraversalPolicy` (from `editor/ui/primitives/SpeqaFocusTraversalPolicy.kt`) and sets `isFocusCycleRoot = true`. The policy skips hidden components and any child flagged with client property `speqa.excludeFromTabChain = true`.
    - `MarkdownReadOnlyPane` is the single shared read-only Markdown renderer across test-case and test-run previews. All read-only body text, step action / expected read mode, and attachment markdown preview go through this one class: no ad-hoc `JLabel.setText("<html>…")` for user-authored content.
    - **Body block placeholder (Description / Preconditions empty state).** When `EditableBodyBlockSection` has no text in read mode, it renders an empty-state placeholder as a plain italic `JBLabel` using `UIUtil.getContextHelpForeground()`, not via `MarkdownReadOnlyPane`. Reason: piping the placeholder through the Markdown renderer inherits the editor foreground color (`EditorColorsManager.globalScheme.defaultForeground`), which is the same brightness as real content text and makes the hint look like a filled value. A dimmed `getContextHelpForeground()` matches the IntelliJ-native placeholder treatment used in `JBTextField`/form inputs. The two placeholder strings differ per field: `placeholder.descriptionBlock = "Write context, intent, or notes for the tester"`, `placeholder.preconditionsBlock = "Write the setup conditions or prerequisites"`.
    - **`InlineEditableTitleRow` caret anchoring.** After every transition that leaves the field in read-mode (`buildField` constructor, `setTitle`, `commit`, `cancel`), the caret position is reset to `0` so the field always shows the start of the title text. Without this, `setText` defaults the caret to the document end, causing the JTextField horizontal-scroll mechanism to reveal the tail (or middle) of a long title rather than the beginning - visually manifesting as the title appearing centered/clipped on both sides.
    - **`InlineEditableIdRow` stable inline editing.** The ID header control must be a single-line custom-laid-out Swing row, not a `FlowLayout` row. It must not change row height, baseline, horizontal text position, or font weight when entering edit mode. Assigned IDs use the normal label font weight. The `TC-` / `TR-` prefix is never editable and remains a separate label in both read and edit modes; only the numeric suffix is editable. In read mode the numeric value is a tight `JBLabel` so the pencil sits immediately after the visible ID text with no text-field column reserve. In edit mode a transparent `JBTextField` edits only the numeric suffix and the trailing action stays immediately after the numeric text. The row's preferred width and height are computed from the maximum read/edit child sizes, independent of the current mode, and `doLayout` aligns text-bearing children on one shared baseline. It must never wrap into two visual rows.
    - **Preview typography / icon consistency.** Preview body text, inline inputs, labels, and read-only Markdown all use the same UI label font family/size (`JBFont.label()`) and normal label foreground (`JBColor.foreground()`), unless a component has an explicit semantic state (disabled/help text, link, error, selected verdict). Muted inline action icons must be flat-tinted to `Label.disabledForeground` while preserving source alpha; they must not be rendered by applying whole-icon opacity (`IconLoader.getTransparentIcon`) because semi-transparent dark glyphs look like uneven shadows in light themes and do not match muted text labels.
    - **Theme switching without mixed light/dark preview state.** Speqa preview editors (`.tc.md` and `.tr.md`) apply the active visible editor canvas background (`EditorColorsManager.activeVisibleScheme.defaultBackground`, falling back to `globalScheme` only when the active visible scheme is not available yet) to every owning wrapper that can show through behind the panel: the root editor component, `JBScrollPane`, `JViewport`, and the preview panel itself. They listen to both Look and Feel changes and editor color-scheme changes because the IDE can emit those separately during light/dark switching; reading `EditorColorsManager.globalScheme.defaultBackground` only from a LAF callback can leave the viewport on the previous theme while child controls already use the new foreground/control colors.
    - **Single-line rows for links, attachments, and tickets.** `LinkRow`, `AttachmentRow`, and the ticket-chip containers in the test-case header and in each step must never wrap their contents to a second visual line regardless of the preview width. Titles/filenames truncate with the native `JLabel` ellipsis; trailing action icons stay pinned at the row's right edge. Non-destructive actions such as link edit remain visible. Destructive remove actions use a lightweight close `X`, not a trash can, and are visible only while the row or removable action is hovered or keyboard-focused. Hover visibility is exclusive across removable rows and follows the current pointer location, not just component-local Swing enter/exit ordering; moving directly from one link/attachment/ticket row to another must leave the remove action visible on the current row only. Their right-side slot is always reserved so hover/focus never changes row width, truncation, wrapping, or neighboring layout. The implementation pattern for `LinkRow` and `AttachmentRow`: root `BorderLayout` with `WEST` = leading icon (fixed), `CENTER` = title/name `JBLabel` with `minimumSize = Dimension(0, prefHeight)` so `BorderLayout` can shrink it below its preferred width and let the JLabel paint "...", `EAST` = actions panel (`FlowLayout`-wrapped edit button plus fixed removable-action slots). `FlowLayout` at the row root is forbidden because it wraps overflowing children to a second line that gets clipped by the fixed row height. Each row also pins `minimumSize`/`maximumSize` to `preferredSize.height` so the enclosing `BoxLayout.Y_AXIS` in `LinkList`/`AttachmentList` does not stretch or squash individual rows into overlapping bounds.
    - **Self-refresh for step meta-row after local edits.** When the user mutates a step's `tickets`, `links`, or `attachments` from the preview, `StepCard.updateStep` refreshes its own `StepMetaRow` via `metaRow.setData(...)` before forwarding `onChange` upward. The enclosing `SpeqaPreviewEditor` suppresses the document-reparse path for its own patches (`suppressDocumentRefresh` counter), so `TestCasePanel.updateFrom` would otherwise never round-trip a fresh `setStep` back into the card, leaving the newly added chip/row invisible in the preview even though the text editor already shows the change. The refresh is gated by an equality check on the three meta-row collections so per-keystroke text edits on `action` / `expected` do not rebuild the meta row on every keypress.
    - **Position-aware tooltip on the date label.** `DateIconLabel` is a single `JBLabel` (no wrapping `JPanel`) so the icon and the date text live in one Swing component and read as one hover area. Tooltip text is computed per cursor position by overriding `getToolTipText(MouseEvent)`: (1) hover over the icon glyph always shows the short label ("Created"); (2) hover over the date text returns `null` (no tooltip) when the date is fully visible - the value is already legible inline; (3) hover over the date text shows the full label plus the date value ("Created 2026-05-19") when Swing has ellipsis-clipped it to the laid-out width. Returning `null` from `getToolTipText` suppresses the popover for that region while keeping the icon's tooltip alive on the same component.
    - **Wrap-aware preferred height for step action / expected text areas.** `multiLineInput` in `SpeqaTextFields.kt` overrides `getPreferredSize()` on its `JBTextArea` so the height reflects the wrap count at the actual rendered width, not the natural (single-line) width. Without this, `JTextArea(lineWrap = true)` outside a `JScrollPane` reports `rows * rowHeight` as its preferred height because it has no width to wrap against; the parent `BorderLayout.NORTH` cell then sizes the textarea to one row tall while the painted text wraps to 2-3 rows, clipping the tail. The override calls the UI's root `View.setSize(currentWidth, MAX)` and reads `getPreferredSpan(Y_AXIS)`. A `ComponentListener` calls `revalidate()` on each width change so the first layout pass updates the height after the real width is known.
    - **Uniform icon brightness for all inline action icons.** Every secondary-action icon across Speqa is flat-tinted to the current `Label.disabledForeground` color while preserving the source icon alpha. `speqaIconButton`'s `muted` parameter defaults to `true` so callers do not need to opt in; the result is that edit pencils (links, attachments, title, ID), delete/trash and chip-close icons, section-header `+` buttons (tags, environment, links, attachments), `+` buttons inside `TagCloud`/`TicketRow`, the `mutedActionLabel` leading icons on "Add ticket ID" / "Add link" / "Attach file", and the hover-revealed drag-handle icon on step cards all share a single visual weight without semi-transparent shadow artifacts in light themes. There is no separate primary vs muted brightness for inline actions; semantic danger icons still use the destructive color.
    - **Run action icon colour.** The "Start a manual test run" header action is a primary semantic action, not a secondary inline edit/remove control. It uses `AllIcons.Actions.Execute` with its native IntelliJ green play colour and must not be passed through the muted icon tinting path.
    - **Section captions in `twoColumnRow` stay pinned to the top.** The two-column row wraps each column in a `BoxLayout.Y_AXIS` panel (caption header, caption gap, body). Because the enclosing `GridLayout(1, 2)` equalises cell heights to the taller column, the shorter column would otherwise let `BoxLayout` redistribute the leftover vertical space across its children, visually dropping the caption below its sibling when one side has content and the other is empty (e.g. `LINKS` populated, `ATTACHMENTS` empty). The fix is two-fold: the header panel overrides `getMaximumSize` to pin its height to its preferred height so `BoxLayout` cannot stretch it, and each column ends with `Box.createVerticalGlue()` so any extra cell height pools at the bottom. With this, `LINKS` / `ATTACHMENTS` (and the other paired captions) always sit on the same baseline regardless of which column has more content.
    - **Tag and ticket clouds wrap onto multiple rows.** `TagCloud` and `TicketRow` lay out their chips with `WrapLayout` (from `editor/ui/primitives/WrapLayout.kt`), not plain `FlowLayout`. `FlowLayout` computes `preferredLayoutSize` as a single row regardless of how its children actually wrap, so when the enclosing `GridLayout`-equalised cell is only tall enough for one row the wrapped chips on the second row are clipped and visually disappear. `WrapLayout` overrides `preferredLayoutSize`/`minimumLayoutSize` to measure the wrapped rows at the current target width, so the cloud grows vertically to fit every chip.
    - **Link row visual details.** The leading icon in `LinkRow` is the project-owned `/icons/chainLink.svg` (the same chain-link icon used before the Swing migration), not `AllIcons.Ide.Link` (external-link chevron) and not `AllIcons.General.Web` (globe). The title label uses `JBColor.namedColor("Link.activeForeground", JBColor.BLUE)` and carries the full URL as its tooltip, so the previous explicit host/domain chip next to the title is dropped: at narrow widths the domain chip competed with the title for horizontal space and pushed the action icons off the row.
    - `CommitFlash.flash(component)` (from `editor/ui/primitives/CommitFlash.kt`) is the API for pulsing a field background after a programmatic document patch. `CommitFlash.enabled` is a global kill-switch (default `false`); when `false`, `flash()` is a no-op. The feature is currently disabled because the pulse distracts during normal editing and no AI-patch workflow exists yet that would benefit from it. For steps: when `previous.steps != case.steps` (content equality, uid excluded), structural changes (size, tickets, links, attachments, expected-nullability) trigger `stepsSection.setSteps(...)` (full rebuild); text-only changes (same count, only action/expected text differs) call `stepsSection.updateStepsInPlace(newSteps)` which delegates to `StepCard.setStep()` on each card - no component rebuild, no `revalidate`, no scroll jump. `setStep` skips updating `actionArea` / `expectedArea` if the field is the current focus owner (user is actively typing); this prevents a stale-document parse from resetting the caret to 0 mid-keystroke.
    - **Preview-panel DataProvider for IDE undo/redo routing.** `SpeqaPreviewEditor` and `TestRunEditor` each register a `DataManager.registerDataProvider` on their root `JPanel` (`component`). The provider exposes `CommonDataKeys.PROJECT`, `CommonDataKeys.VIRTUAL_FILE`, and `PlatformCoreDataKeys.FILE_EDITOR` (-> `this`). `HOST_EDITOR` is intentionally NOT exposed: exposing it routes typed characters into the underlying text editor on the left and breaks input in every Swing text field. `FILE_EDITOR` alone is sufficient for `UndoAction` / `RedoAction` to locate the `FileEditor` via `PlatformCoreDataKeys.FILE_EDITOR.getData(dataContext)`, after which `UndoDocumentUtil.getDocumentReferences(editor)` resolves the document via `editor.getFile()` -> `FileDocumentManager.getDocument(file)`. However, `UndoRedoAction.getUndoManager()` first checks whether the focused component is a `JTextComponent`, and if so returns `SwingUndoManagerWrapper` - which only knows about Swing-level text edits, not document-level `CommandProcessor` commands. To prevent this Swing-undo intercept, every `JBTextArea` and `JBTextField` created by `singleLineInput` / `multiLineInput` in `SpeqaTextFields.kt` sets `ClientProperty.put(field, UndoRedoAction.IGNORE_SWING_UNDO_MANAGER, true)`. This causes `getUndoManager` to fall through the `JTextComponent` guard and return the project's `UndoManager`, which holds the undo stack for `CommandProcessor` commands. With both fixes in place, Cmd+Z while focus is on any Swing text field in the preview correctly undoes the last document mutation.
    - **Preview inline markdown undo refresh.** `MarkdownEditablePane` fields route Cmd+Z / Cmd+Shift+Z to the parent `FileEditor`'s project `UndoManager`, not to the transient editor field document. When that undo/redo mutates the backing markdown document, the preview editor records during the synchronous `documentChanged` event that the next refresh came from preview-initiated undo/redo. That refresh bypasses the normal 300 ms debounce and is scheduled on the next EDT turn, so the raw editor and preview converge immediately after the undo/redo command finishes. The refresh also force-syncs focused inline markdown fields (`action`, `expected`, description/preconditions, and run comments). Normal editor-driven refresh keeps the existing debounce and focus guard so external text edits do not overwrite the field the user is actively typing in. When a focused inline field is synced, caret restoration maps the old offset through the changed text span using common prefix/suffix, rather than reusing the same numeric offset blindly; this keeps the caret at the undo/redo edit location instead of jumping to the next line when the restored text is shorter or has different line breaks.
    - **Self-write suppression uses a counter, not a boolean.** `SpeqaPreviewEditor` and `TestRunEditor` each hold a `suppressDocumentRefresh: Int` counter (not `Boolean`). Every call to `writeFromPreview` or `patchFromPreview` increments it before scheduling `invokeLater` and decrements it in the `finally` block. The document listener skips the refresh timer when the counter is `> 0`. Using a boolean caused a race: rapid edits schedule multiple `invokeLater` writes; the first `finally` reset the flag to `false`, but the second write then fired `documentChanged` with suppression off, triggering a spurious `updateFrom` call and a visible blue `CommitFlash` on every keystroke.
    - **Fenced code block indentation round-trip.** `MarkdownPane.splitFencedSegments()` captures the per-block leading whitespace of the opening fence (`FENCED_BLOCK` regex group 1) and stores it on `Segment.Code` alongside `language` and `code`. The interior `code` body is stored verbatim (the JBTextArea shows the user-visible body without any baked-in indent). The top-level `reassembleCodeSegment(indent, fence, body)` reconstructs each code segment by emitting `indent + "```lang"`, then each line of the body prefixed with `indent` (unless the body line already starts with `indent`, to avoid double-prefixing when the indent is already present), then `indent + "```"`. Round-trip is covered by `MarkdownPaneFencedRoundTripTest` for 0-, 3-, and tab-indented blocks. Without preserving fence-line indent, fences inside nested list items (e.g. `   ```json` under a 3-space-indented `3. ...` list continuation) collapse to column 0 after the preview re-serializes the description, breaking the surrounding list block and producing a visual indent regression in the raw `.tc.md`.
    - **Single undo step per preview keystroke.** `SpeqaPreviewEditor.patchFromPreview` (and the parallel `TestRunEditor.patchFromPreview`) coalesces the `List<DocumentEdit>` returned by `DocumentPatcher.patch` into one logical document mutation per command. The shared helper `DocumentPatcher.applyEditsAsOneReplace(document, edits)` sorts the edits by offset, asserts they are non-overlapping (overlapping edits indicate a patcher bug and surface as `IllegalStateException`), reads `document.getText(TextRange(start, end))` covering the minimal span, applies each edit in reverse against that in-memory string, and emits exactly one `document.replaceString(start, end, mergedText)`. Every keystroke must be reversible with one `Cmd+Z`; multiple `replaceString` calls inside one `CommandProcessor.executeCommand` still register as separate undo entries in the IDE's undo manager and break this contract.
    - **Fine-grained `PatchOperation` emissions.** `TestCasePanel` exposes an optional `onPatch: (PatchOperation) -> Unit` callback alongside `onChange(TestCase)`. Child widgets route their mutations through a `PatchOperation` from `parser/DocumentPatcher.kt` whenever a dedicated op exists for the mutated field: `SetFrontmatterField` for title/id, `SetFrontmatterList` for tags, `SetLinks` / `SetAttachments` for doc-level link and attachment lists, `SetDescription` / `SetPreconditions` for body blocks, `SetStepAction` / `SetStepExpected` / `SetStepTickets` / `SetStepLinks` / `SetStepAttachments` for step fields, `AddStep` / `DeleteStep` / `ReorderSteps` for step structure. `SpeqaPreviewEditor` routes each emission through `patchFromPreview(op)` which preserves scroll via a `ScrollSyncController.PanelScrollPosition`, not a bare scrollbar value: the snapshot stores both the current value and the distance from the bottom of the scroll range, so appending content below a bottom-aligned viewport keeps the preview bottom-aligned instead of restoring to the old absolute offset. Any editor-driven refresh, preview-driven full write, or preview-driven patch that can re-layout the preview must open a bidirectional scroll-sync suppression window before the re-layout/write starts, so preview scrollbar model adjustments cannot mirror back into the native editor and native editor caret/layout adjustments cannot mirror into the preview. Restoring a preserved preview position must account for Swing's transient scrollbar model states during re-layout: `ScrollSyncController` recomputes the target from the latest `maximum - visibleAmount`, keeps a short-lived pending restore subscribed to scrollbar model changes, and reapplies the target as the range settles instead of accepting temporary clamps. A pending restore is canceled by the next native document mutation or by an explicit user-driven scroll sync in either direction. Native-editor document mutations additionally guard the next native-editor visible-area movement burst from editor→preview sync; caret-follow scrolling caused by typing a new step marker such as `7.` must not move the preview, while later deliberate editor scrolling still syncs normally after the burst settles. Editor-driven preview refreshes must not apply a transient structural step-list shrink produced by incomplete Markdown while the user is typing a new numbered step at the end of the scenario; an unfinished trailing marker such as `7.` / `7. ` keeps the current preview model until the marker becomes a parsed step append or a stable real deletion. When an editor-driven parse changes the step list by appending exactly one step at the end and leaving existing steps unchanged, `StepsSection` appends only the new `StepCard` before the Add Step row instead of rebuilding the whole step list; full rebuilds are reserved for non-append structural changes such as delete, reorder, metadata structure changes, or inserting in the middle. The whole-document `writeFromPreview(newCase)` path remains only as a fallback for fields without a dedicated op (currently: none on the test-case side under the current model). `TestRunPanel` still uses `writeFromPreview(newRun)` because `DocumentPatcher` has no test-run operations; adding run-side ops is tracked separately and is not on the critical path.
    - **`stepsStructurallyChanged` does not use `uid` for step identity.** `TestStep.uid` is a session-local drag-and-drop identity counter excluded from `equals()`/`hashCode()`. The parser always creates fresh `TestStep` instances with new auto-incremented uids; comparing `old[i].uid != new[i].uid` would always be true after any parse, causing `CommitFlash.flash(stepsSection)` on every refresh-timer tick. The structural change check instead compares size, per-step tickets/links/attachments, and whether `expected` changed between null and non-null.
    - Text-in-field focus across external patches is preserved by `updateFrom(model)` diffing against the current model before mutating children: text-only changes update the affected field in place; structural changes (list insert/remove/reorder) trigger `setX(...)` rebuilds.
- JVM 21

**UI implementation constraint:**
- Form inputs, multiline editors, selectors, and interactive controls must use IntelliJ/Swing components (`JBTextField`, `JBTextArea`, `JButton`, `ComboBox`, `JBScrollPane`, `JBPanel`) and derive colors/borders from IntelliJ UI defaults rather than custom component stacks.
- Section headers and grouped labels use SpeQA's Swing primitives and IntelliJ theme colors.
- Standard IntelliJ Markdown files (`.md`) and their native preview must remain completely untouched by Speqa: no custom providers, no custom colors, no surface overrides
- Speqa-specific editors for `.tc.md` and `.tr.md` must inherit the active editor canvas background, not `Panel.background`, so custom surfaces visually merge with the native IntelliJ editor instead of looking like a separate settings panel
- The plugin must never change the IDE's global editor color scheme, Look and Feel, or Markdown appearance defaults in sandbox or runtime startup code. Any theme used by Speqa must be read from the current IDE/editor state, never written back globally
- `.tc.md` and `.tr.md` use Speqa-owned file types bound to `MarkdownLanguage` so the left-side native editor keeps full Markdown behavior (syntax highlighting, blockquote/code-block coloring, etc.). A `SpeqaFrontmatterInjectionSuppressor` (`MultiHostInjector` registered with `order="first"`) intercepts YAML injection into `MarkdownFrontMatterHeader` elements and skips it for Speqa files — this prevents the `AssertionError` in `CompletionInitializationUtil` and frontmatter edits reverting that occur when IntelliJ's YAML injection cannot properly sync the injected document back to the host for custom file types
- `.tc.md` and `.tr.md` must be treated by the IDE as ordinary Markdown files, not as custom `LanguageFileType` replacements. SpeQA recognizes them by file name suffix and overlays its split editor / icons / schema behavior on top of the native Markdown file type. This preserves native Markdown editor affordances in the left editor, including selection-based markdown actions that are tied to the platform's real Markdown file type.
- **Left-editor provider parity:** The left side of SpeQA split editors must be created through the same PSI-aware text-editor path that the Markdown plugin uses (`PsiAwareTextEditorProvider`), not through the plain `TextEditorProvider`. Otherwise the file may look like Markdown but still miss editor behaviors that depend on the markdown-aware text-editor integration.
- **Dynamic-install editor reopen:** Because `.tc.md` / `.tr.md` are ordinary Markdown files, a file that is already open when the plugin is installed or enabled at runtime stays bound to the platform Markdown editor (whose preview is JCEF-based), which the platform does not re-evaluate on dynamic load. `SpeqaDynamicEditorReopener`, a `DynamicPluginListener` registered via `<applicationListeners>`, reopens any already-open SpeQA file on the plugin's own `pluginLoaded` event so the tab switches to the SpeQA split editor without an IDE restart. It must reopen only files whose current editor is not already a SpeQA split editor, so a normal project open (plugin present at startup) causes no churn.
- **Visual markdown fidelity:** Read-only text in SpeQA visual editors must reflect inline Markdown authoring from the left editor instead of exposing raw markup. At minimum, strong emphasis, emphasis, and inline code are rendered visually in description/preconditions blocks and in step action/expected text for both test-case preview and test-run preview. The visual editor does not need to expose full Markdown authoring UI, but it must not silently drop formatting or show raw `**...**` markup in read-only text.
- **Shared renderer rule:** inline Markdown rendering in visual editors is implemented through one shared read-only renderer (`editor/ui/primitives/MarkdownPane.kt`). Test-case preview and test-run preview must consume the same renderer so formatting support does not drift between the two panels.
- **Preview soft-wrap without indicators:** the editable inline-markdown fields in the preview (`MarkdownEditablePane`, used for step action, step expected, description, and preconditions) enable soft wrap so long lines wrap inside the narrow field, but they suppress the editor's soft-wrap indicator glyphs (the wrap arrows drawn at the end of a wrapped visual line and the start of its continuation). Wrapping behavior is kept; only the indicator painting is turned off. This is scoped to the preview's embedded `EditorTextField` instances only - the main Markdown text editor on the left keeps its standard soft-wrap behavior and indicators unchanged.
- **Read-only preview parity:** any read-only body text or step text in the test-case panel uses the same shared Markdown renderer that the test-run panel uses. This keeps the read-only preview path faithful to Markdown even where the editable path uses plain text inputs.
- **Renderer test contract:** the shared inline Markdown renderer has unit tests for plain text, bold, italic, inline code, nested bold+italic (`**_email_**`), multiple formatted spans in one line, and unmatched markers. The nested case must render `email` with both bold and italic styling while the visible text remains `email` without raw markup characters.
- **Test visibility rule for renderer logic:** the inline-rendering logic may be `internal` to support direct JVM assertions over the produced styled-text spans; it does not need to be part of any broader public UI API.
- **Nested emphasis requirement:** inline emphasis parsing must recognize underscore emphasis inside strong emphasis (`**_email_**`) in addition to the simple asterisk forms. Rendering only `*italic*` while leaving `_italic_` markers visible is incorrect for SpeQA visual previews.
- **Strikethrough requirement:** inline Markdown rendering also supports strikethrough (`~~text~~`) and nested combinations with emphasis/strong emphasis, including cases like `~~into~~ _**~~the~~**_`. The visible text must drop all raw markup characters while preserving the combined span styles on the affected substring.
- **Strikethrough test coverage:** renderer tests include both a simple `~~text~~` case and a mixed nested case where the same visible token carries strikethrough, bold, and italic spans simultaneously.

**Localization constraint:**
- All user-visible strings (labels, placeholders, tooltips, error messages, empty state text) must be defined in `messages/SpeqaBundle.properties` and accessed via the resource bundle — never hardcoded in Kotlin code

**Visual design principles:**
- Shared Swing primitives (dividers, section labels/captions, utility action labels) are defined once under `editor/ui/primitives/` and reused across the preview and test-run panels - no duplicates.
- Inline action controls use `SpeqaIconButton` with IntelliJ platform icons (e.g. `AllIcons.Actions.Close`), never text-symbol buttons.
- Page padding minimum 16dp. Text field inner padding minimum 6dp horizontal / 4dp vertical
- Cards use background fill only — no explicit borders. Borders only on text input fields
- Consistent spacing constants from `SpeqaLayout` — form, preview, and test run panel must use the same gap values for equivalent structural elements
- Color tokens live in one replaceable surface, `SpeqaThemeColors` (`editor/ui/theme/SpeqaThemeColors.kt`). Each token is a `JBColor(light, dark)` that resolves to the variant matching the current IDE theme at paint time (e.g. the verdict pill tints `verdictPassedBackground` / `verdictFailedBackground` / `verdictSkippedBackground` / `verdictBlockedBackground` and the selected-verdict foreground). UI code must pull colors from this object or from IntelliJ `UIManager` / `EditorColorsManager` directly, never from raw `Color(...)` literals embedded at call sites. Semantic states such as destructive/error tints read IntelliJ named colors (e.g. `Component.errorFocusColor`).
- Adding a step or body block in the form editor auto-scrolls to the new element and auto-focuses the action input of new steps

**Accessibility / semantics:**
- Section labels, the edit/toggle icons, the `AttachmentRow` primary action, and the step drag handle are all real focusable/actionable Swing components (built via `SpeqaIconButton`, focusable rows, and the shared focus-traversal policy) so assistive technology and keyboard navigation reach them.
- **Drag auto-scroll:** when the user drags a step card whose current bounds overlap the top or bottom edge zone of the preview viewport (zone size `DragAutoScroll.DEFAULT_EDGE_ZONE_DP`, 48px), the preview scrolls in that direction. Speed is proportional to how far the item penetrates the zone, capped at `DragAutoScroll.DEFAULT_MAX_SPEED_DP_PER_FRAME` (12px/frame). The pure edge-zone math lives in the `DragAutoScroll` object (`computeScrollDelta`) and is covered by `DragAutoScrollTest`. During an active drag, `DragReorderSupport` repeatedly applies the computed delta to the `JBScrollPane` vertical scrollbar model and shifts the dragged ghost by the same amount so the card stays under the pointer while the viewport scrolls underneath.
- **Drag live-preview reorder:** while a step is being dragged, the steps between the dragged index and the current drop target shift out of the way so the user can see where the card will land. `LivePreviewReorderDecorator` wraps each step card in a `LivePreviewWrapper` and animates each neighbour's vertical visual offset toward its target on a Swing timer (`tick`), so the shift is smooth rather than instantaneous. The pure shift target is computed by `livePreviewTargetOffset`. The dragged card itself is painted as a floating ghost; `livePreviewShouldPaintCard` decides which wrappers paint their content during the drag, returning, and idle phases.
- **Preserve editor scroll across preview-initiated patches:** when the preview patches the document (e.g. `PatchOperation.ReorderSteps`), IntelliJ's text editor can auto-scroll to follow the caret whose offset moves with the `replaceString` call. `SpeqaPreviewEditor.patchFromPreview` snapshots `textEditor.scrollingModel.verticalScrollOffset` before the write and restores it with `disableAnimation` + `scrollVertically(preservedOffset)` immediately after, so the markdown editor stays where the user left it. `ScrollSyncController` additionally opens a short bidirectional sync-suppression window around the write so a brief caret-follow scroll between `replaceString` and the restore does not mirror onto the preview.

---

## 2. Feature Summary

| Feature | Description |
|---------|-------------|
| File format | `.tc.md` (test cases), `.tr.md` (test runs) — Markdown + YAML frontmatter, custom `SpeqaLanguage` with Markdown-delegating syntax highlighter |
| Storage | Files in project directory, versioned via Git |
| Configurable root path | User sets root directory for test cases in plugin settings |
| Test case fields | Title, Priority, Status, Environment, Tags, Body Blocks (Description + Preconditions), Attachments, Links, Scenario steps (action + optional expected per step) |
| Test case editor | Speqa split editor: native text editor (left) + interactive test-case panel (right) |
| Test run editor | Speqa split editor: native text editor (left) + interactive test-run panel (right) |
| Native Markdown default | Regular `.md` stays fully native IntelliJ Markdown |
| Project View integration | Custom icons for `.tc.md` / `.tr.md`, status-colored |
| Create test case | Context menu (New → Speqa Test Case) + Action/shortcut |
| Default template | One built-in template for new test cases |
| Soft validation | Warnings in gutter for missing required fields, non-blocking |
| Test run | Step-by-step execution with Passed/Failed/Skipped per step |
| Theme integration | Speqa side panels follow the current IDE editor theme without hardcoded light colors or custom panel tint |
| Native editor compatibility | `.tc.md` and `.tr.md` open only in Speqa split editors that embed the native IntelliJ text editor on the left; regular `.md` stays fully native IntelliJ Markdown |

---

## 3. Test Case File Format (.tc.md)

```markdown
---
title: "Login with valid credentials"
priority: major          # critical | major | normal | low
status: draft           # draft | ready | deprecated
environment:
  - "Chrome 120, macOS 14"
  - "Firefox 121, Windows 11"
tags:
  - auth
  - smoke
---

This case verifies the standard login flow for active users.

Preconditions:

1. User account exists in the system
2. User is on the login page

Extra context for the tester.

Pre-conditions:

* Feature flag `new-login-flow` is enabled

Attachments:

[attachments/login/screenshot.png]

Links:

[Jira ticket](https://jira.example.com/TC-123)
[Design doc](https://figma.com/file/abc123)

Scenario:

1. Type "testuser@example.com" into the email field  
   > Email field accepts input, no validation errors

2. Type "SecureP@ss123" into the password field  
   > Password is masked, no validation errors

3. Click the "Login" button  
   > User is redirected to the dashboard
```

**Key decisions:**
- Extension `.tc.md` remains ordinary Markdown under the IDE's native Markdown file type. SpeQA recognizes the suffix and overlays split-editor, icon, and schema behavior without replacing the platform Markdown type. A `SpeqaFrontmatterInjectionSuppressor` still prevents YAML injection into frontmatter headers for SpeQA files.
- **Shared scenario readability contract:** test cases and test runs use one common scenario shape so the raw Markdown stays understandable in GitHub and the same structure can be rendered predictably in SpeQA preview. A scenario step is always read top-to-bottom as: numbered action line, optional expected blockquote lines, optional attachment lines. Test runs extend that same shape with execution metadata after the expected/attachment block; they do not invent a different base step grammar.
- **Markdown attachment-path navigation:** In the left native editor for `.tc.md` and `.tr.md`, standard inline links and images use ordinary file-relative Markdown paths so segmented `Cmd` + hover, `Cmd` + click, rename, and move refactorings are handled by the native Markdown plugin without SpeQA-specific destination supplementation.
- **Previous implementation failure and required replacement:** Project-root-relative attachment paths forced SpeQA to supplement `MarkdownLinkDestination` resolution. That duplicated native Markdown behavior, produced false unresolved warnings such as `Cannot resolve directory 'test-cases'`, and weakened rename/move integration. The current product does not use project-root-relative attachment paths. Attachment paths are authored file-relative specifically so the native Markdown plugin remains the single source of truth for standard markdown link/image navigation and refactoring.
- **Contributor integration detail:** SpeQA contributes custom file references only for bare-bracket attachment syntax on `MarkdownLinkLabel`, because native Markdown does not interpret `[attachments/foo/bar.png]` as a file destination. SpeQA must not contribute `MarkdownLinkDestination` references for ordinary markdown links or images in `.tc.md` / `.tr.md`.
- **Verification contract for native-vs-SpeQA references:** Automated verification covers three contracts. Standard Markdown links and images in SpeQA files resolve natively with no extra SpeQA references. Bare-bracket attachment labels still expose one reference per visible path segment. External link labels and image alt text never become local file references.
- **Bare-bracket attachment syntax support:** SpeQA writes non-image attachments as bare bracket paths (`[attachments/foo/bar.png]`) and image attachments as standard Markdown image links (`![name](attachments/foo/bar.png)`). Both forms use file-relative paths. Native-editor segment navigation and hover underline must continue to work for both authoring forms, but only the bare-bracket form needs SpeQA's custom `MarkdownLinkLabel` file references. The file-reference builder must account for the leading `[` in label ranges so the visible segment underline starts on the first path character, not on the bracket.
- **Reference-set construction detail:** The SpeQA file-reference builder uses a normal IntelliJ `FileReferenceSet` only for `MarkdownLinkLabel`, with `startInElement = 1` so ranges align to the visible path inside brackets. It must not extend contexts with the project root.
- **Implementation typing detail:** The contributor returns the `FileReferenceSet` segments to the IntelliJ reference API as `PsiReference[]` even though the concrete array elements are native `FileReference` instances. SpeQA must not wrap them in custom non-file references for bare-bracket navigation.
- **Platform API detail for file references:** In the current IntelliJ baseline, `FileReferenceSet.allReferences` is already an array. The contributor adapts that native array directly to `PsiReference[]`; it must not assume a Kotlin collection API on top of it.
- **Primary navigation source:** Once segmented file references are present for SpeQA attachment paths, `SpeqaGotoFileHandler` must not stay registered for `.tc.md` / `.tr.md` attachment navigation. Leaving the goto handler active can still make `Cmd` + hover behave as a single whole-element navigation target even when the PSI references are segmented. Native-editor hover and click behavior must come from the file references alone.
- **Attachment reference scope:** SpeQA file references apply only to bare-bracket attachment labels (`MarkdownLinkLabel`). A normal Markdown destination such as `![screenshot.png](attachments/foo.png)` must stay fully native. A normal Markdown link label or image alt text such as `[report.pdf](https://example.com)` or `![screenshot.png](attachments/foo.png)` must never become a local file-navigation source from its label text.
- **URL-encoded destination handling:** Standard Markdown destinations may contain URL-encoded segments such as `%20`, and native Markdown must continue to resolve them correctly because the stored path is file-relative to the host file. SpeQA's bare-bracket label references keep their visible segmented ranges aligned to the encoded text in the editor.
- **Attachment reference verification contract:** Tests verify the no-hijack rule by checking the reference returned at the rendered label offset in the Markdown PSI/editor host, rather than assuming a specific intermediate PSI class for external-link labels.
- **Non-attachment label safety:** The no-hijack verification covers both external-link labels and image alt text, since neither should ever resolve as local file references from the visible label text.
- **Run file naming contract:** Test-run creation normalizes the chosen file name to the SpeQA test-run extension (`.tr.md`) and resolves collisions against existing files in the chosen destination before creating the new file. The normalization is deterministic so it can be covered by unit tests without opening the dialog UI.
- **Run file naming helper:** The deterministic normalization lives in `TestRunSupport`, alongside the timestamp-based default-name helper, so editor code and tests share one source of truth for extension normalization and collision suffixes.
- **Run creation flow:** `startTestRun` computes existing names from the chosen destination directory before showing defaults and again normalizes the final requested name before file creation. The write action never attempts `createChildData()` with an unchecked raw dialog value.
- **Run import-options UX:** `RunCreationDialog` includes five independent import checkboxes: `Tags`, `Environment`, `Tickets`, `Links`, and `Attachments`. Defaults: Tags = enabled, Environment = enabled, Tickets = disabled, Links = disabled, Attachments = disabled. If the source test case has no data for a category, the corresponding checkbox is disabled, forced to the unchecked state, and exposes a hover tooltip explaining that there is nothing to import. The dialog request payload carries these choices as `RunImportOptions`, and `startTestRun` computes the per-category availability from the parsed source `TestCase` before opening the dialog. Availability must match what `TestRunSupport.createInitialRun(...)` can actually import in the current product slice: for `Links`, that means top-level test-case links only, not step-level links.
- **Run import-options layout:** the five import checkboxes in `RunCreationDialog` are shown in a compact two-column grid instead of one long vertical stack. The section label stays above the grid, and the checkboxes retain their existing order: `Tags`, `Environment`, `Tickets`, `Links`, `Attachments`.
- **Run destination validation UX:** `RunCreationDialog` validates the destination path inside the popup, not only after submit. A destination that resolves outside the project root shows an inline error in the dialog and keeps the `Create` button disabled until the user changes the path to a valid project-contained destination. The same disabled/error state applies immediately after browse selection or manual typing; invalid input must not close the popup or defer feedback until after clicking `Create`.
- **Run destination error visibility:** The invalid-destination message is always visible in the dialog body while the path is invalid. It is not hover-only tooltip text on the input field. The user must be able to understand why `Create` is disabled without moving the pointer over the field.
- **Run destination error formatting:** The always-visible destination error text is sentence-style without a trailing period, and its left edge aligns with the actual text-start inside the destination input, including the field's internal left inset, rather than the left edge of the form label column or the outer component border.
- **Run dialog error alignment rule:** All inline validation messages in `RunCreationDialog` align to the same actual text-start as their corresponding input content after Swing layout is applied. The offset is derived from the rendered component geometry, not approximated from label-string width alone.
- **Run destination helper contract:** Destination validation uses one shared normalization rule for both manual typing and browse-picked absolute paths. Absolute paths under the project root are converted to project-relative form for the request model. Relative paths are resolved against the project root and rejected if normalization escapes it (for example via `..` segments). The dialog request object exposes only the normalized project-relative destination string.
- **Run file-name validation UX:** `RunCreationDialog` validates `File name` inside the same popup. Empty names, whitespace-only names, path separators, `.` / `..`, and cross-platform-invalid filename characters keep the `Create` button disabled and show an always-visible inline error directly under the file-name field.
- **Run file-name helper contract:** File-name validation runs on the raw user input before extension normalization. The dialog may still append `.tr.md` later through `TestRunSupport.normalizeRunFileName`, but the pre-normalized base input must already be a legal single filename, not a path fragment.
- **Header metadata performance:** Test-case header metadata uses a cached created-at resolver keyed by file path/timestamp. The preview refresh path may recompute the cheap updated-at label on each document change, but it must not spawn a fresh Git process for every refresh of the same unchanged file.
- **Created-at resolver implementation:** The created-at cache lives in a dedicated resolver object in the editor support layer. Cache entries are invalidated when the file timestamp changes, which keeps repeated refreshes cheap without freezing stale created-at values after file replacement.
- **ID index initialization:** `SpeqaIdIndex` is a `FileBasedIndex`, built and kept current by the platform, so there is no project scan, startup activity, or readiness flag to manage for ids. `SpeqaIds` queries are guarded against dumb mode and return empty / not-duplicate while indexes are unavailable.
- **Error-report submission UX:** The IDE report flow must tell the user whether the error report was actually delivered. When the network submission fails or the client rejects the payload, the submitter returns a failure status instead of always reporting `NEW_ISSUE`.
- **Error-report verification contract:** Automated tests may substitute the transport with a deterministic stub so the submitter can be verified for both success and failure outcomes without performing real network requests.
- **Error-report transport hook:** `SpeqaSentryClient` exposes a package-visible test hook for the final transport call. Production uses the normal HTTP sender; tests can replace it to return success or failure deterministically.
- **Error-report completion mapping:** `SpeqaErrorReportSubmitter` maps the transport result to `SubmittedReportInfo.SubmissionStatus.NEW_ISSUE` on success and `SubmittedReportInfo.SubmissionStatus.FAILED` on failure, using the same asynchronous executor path in both cases.
- **New project opening behavior:** After the SpeQA new-project wizard creates the scaffold, the IDE opens the single generated sample test case (`sample-login.tc.md`) automatically and selects it as the only initially focused editor tab. Opening happens from the wizard/project-generator completion path, not from editor startup code, so the first visible editor is the normal SpeQA split editor with native Markdown on the left and preview on the right.
- **Scaffold result contract:** `SpeqaProjectScaffold.generate(...)` returns the created sample test-case `VirtualFile` so both new-project entry points can reuse the same post-create open-file behavior without duplicating file lookup logic.
- **Wizard write-action typing:** The new-project wizard captures the scaffold result through a typed write action (`runWriteAction<VirtualFile?>`) so the created sample file can be opened immediately after setup without ambiguous `Unit` inference.
- **Theme-switch background:** `SpeqaPreviewEditor` and `TestRunEditor` subscribe to `LafManagerListener` and `EditorColorsListener` and re-read the editor canvas background via `EditorColorsManager.getInstance().activeVisibleScheme.defaultBackground`, falling back to `globalScheme` only when the active visible scheme is not available yet. They reassign it to the root `JPanel`, `JBScrollPane`, `JViewport`, and mounted `TestCasePanel`. The mounted panel refresh runs `SwingUtilities.updateComponentTreeUI(this)` before reapplying the editor background so Swing UI delegate updates cannot overwrite the preview canvas color.
- Speqa identifies `.tc.md` and `.tr.md` through dedicated file types and custom split editors; it must not replace the native Markdown file type registration for regular `.md`
- The built-in file template for `.tc.md` must generate valid Markdown exactly as required by the parser: frontmatter, ordered body blocks, then step list items
- YAML frontmatter holds only scalar and list metadata: id (optional int), title (required), priority, status, environment, tags (all optional)
- **ID system:** `id` is an optional integer in frontmatter, displayed as `TC-N` / `TR-N` (prefix is UI-only). Duplicate detection and id allocation are backed by a project `FileBasedIndex` (`SpeqaIdIndex`) keyed by `"TC:<id>"` / `"TR:<id>"` over `.tc.md` / `.tr.md` files. Because the platform reindexes unsaved documents at query time, detection reflects the live editor buffer and surfaces through the daemon's normal highlighting pass (its native debounce), with no separate scan, VFS listener, or daemon restart. `getContainingFiles` is an over-approximation, so every candidate file is re-verified against its current parsed id before being counted. Queries are guarded against dumb mode (return "not duplicate" / empty while indexes are unavailable).
- **ID generation:** the next free id is the smallest positive integer not present in the index's key set for that type (`SpeqaIds.nextFreeId`). Allocation does not reserve in memory; because the index reflects unsaved buffers, a freshly written or quick-fixed id is visible to the next allocation query. Auto-assigned at file creation for TC and TR.
- **ID uniqueness scope:** ids are allocated by a per-project sequential counter, which by design cannot prevent collisions when test cases are created in parallel on separate branches: each branch continues numbering from the same base. An id is therefore unique within a single tree but is NOT a stable cross-branch identifier. After a merge two different files can carry the same id, and resolving that collision renumbers one of them. The product does not expose ids as external reference keys, so renumbering on merge is an accepted operation. Collisions are not prevented at creation; they are detected and resolved after the merge has produced the combined tree
- **ID in header UI:** replaces the "Test Case" label in the utility row. Width adapts to content (intrinsic). When assigned: `TC-N` with pencil/checkmark icon (same pattern as title edit — fixed-size 20dp container, 12dp icon). Click on `TC-` or number enters edit mode. Enter commits, Esc cancels. Auto-commit on focus loss (same as title). **ID tooltips by state:** unassigned → "Click to assign ID" (red placeholder, click assigns next free); duplicate → "TC-N is already in use" (red text); unique → "Edit ID" on entire row; editing → "Save" on checkmark icon only, no pointer cursor on the text/prefix area. Same "Save" tooltip is used for the title checkmark icon during editing
- **ID duplicate detection:** `SpeqaIds.isDuplicate()` returns true when the index reports more than one file carrying the same key. The annotator underlines `id:` in frontmatter and offers an `Assign next free ID` quick fix. The quick fix allocates via `SpeqaIds.nextFreeId()` and writes the new value. The next free id is read from the index key set, which lags unsaved in-editor edits, so resolving several files that share one id is better done with the batch resolver than by applying the per-file fix repeatedly. Editor header shows red text + tooltip. Does not block saving.
- **Batch duplicate-ID resolver:** an explicit user action `Resolve duplicate test-case IDs` scans the current project tree, groups files that share an id, and renumbers the duplicates. It is origin-agnostic: it reasons only about the current tree, never about git branches or which side came from main, so it works identically for merge-produced duplicates and for duplicates created locally across separate editor windows. Within each collision group the file with the earliest creation time keeps the contested number; the rest are ordered by creation time and assigned fresh ids that are free across the whole tree. Creation time is the git author-date of the commit that introduced the file (the same value the header `Created` uses), with a stable fallback (filesystem creation time, then path order) so that two machines resolving an identical tree produce an identical result. The action presents a preview plan (group, file, old id to new id) and applies all renumbering in a single write action only on explicit confirmation; it never auto-edits files. Each displaced file moves to the first id that is free across the whole tree and across assignments already made in the same pass, so resolution never creates a new duplicate and never shifts non-duplicate files. The resolver is reachable from the Tools > SpeQA menu and from an editor banner (see below); the banner offers `Review duplicates` (opens the preview dialog) and `Resolve all` (applies directly without the dialog).
- **Duplicate-ID banner:** `DuplicateIdNotificationProvider` (an `EditorNotificationProvider`) shows a warning banner on any open `.tc.md` / `.tr.md` editor when the project has at least one duplicated id of that type. A single duplicated id is also surfaced by the per-file underline and quick fix; the banner is the project-level, batch-oriented affordance and stays visible while editing any test case so the problem is discoverable where the inline inspection is not. It carries two actions, `Review duplicates` and `Resolve all`, both delegating to the shared `DuplicateIdResolution`. `DuplicateIdBannerRefresher` (a `ProjectActivity`) refreshes the banner via `EditorNotifications.updateAllNotifications()` on a 300 ms debounce after document changes to Speqa files.
- **Field presence rule:** only `title` is required. All other frontmatter fields (id, priority, status, environment, tags) are optional. If a field key is absent from frontmatter, it is treated as "not set" (`null` in the data model). If a field key is present — even with an empty or default value — it is treated as "set"
- **Serializer rule:** only non-null fields are written to frontmatter. A `TestCase` with `id = null` produces no `id:` line; with `priority = null` produces no `priority:` line
- **Preview visibility rule (refined):** Title is always shown. For all other fields: if the value is `null` (absent from frontmatter), the section is hidden in preview. If the value is non-null (present in frontmatter, even if empty/default), the section is shown
- The Markdown body before the steps marker is an ordered sequence of blocks. That order must be preserved in the form editor, preview, and serializer.
- A `Preconditions` block starts only with a paragraph-opening marker line: `Preconditions:` or `Pre-conditions:`
- Everything before the steps marker that does not start with one of those markers is a `Description` block
- Preconditions content includes everything from the marker line until the next body block marker or the `Scenario:` marker — including numbered lists, bulleted lists, free text, multiple paragraphs separated by blank lines, and mixed content. A paragraph after a preconditions list still belongs to the same preconditions block, not a new description block
- There is no frontmatter variant for preconditions and no `## Preconditions` heading syntax
- **Scenario marker:** the test-case step section begins with a `Scenario:` marker line (case-insensitive). Everything before this marker is body blocks. Everything after is the numbered step list. If no `Scenario:` marker is present, the file has no steps
- Steps are serialized as a numbered list (`N. `) after the `Scenario:` marker. The first action line always lives on the numbered line itself.
- Expected result is an optional blockquote (`>`) under a step — can be omitted for individual steps or placed after a group of steps to cover them all. In both form and preview panels expected result is displayed without the `>` prefix, without extra left indent — it sits directly under the step action text (aligned with the action, not the step number)
- **Scenario formatting for GitHub and preview:** the canonical `.tc.md` write format keeps the step action, expected block, and attachment block as separate physical lines. SpeQA does not collapse them into one inline sentence. A visual-editor newline inside step action or expected text is serialized as a Markdown hard break: two trailing spaces at the end of the preceding raw line. The ordinary text editor therefore represents one visual line break as `␠␠\n`. This gives GitHub a readable block structure and gives SpeQA one deterministic parsing/rendering model.
- The parser must preserve all Markdown content within a step action verbatim — it must not strip or ignore lines based on formatting (e.g. `- ` sub-lists, indented code, etc.)
- There is no separate global expected-result section in `.tc.md`; expected outcomes live on steps via blockquotes
- **Attachment data model:** `Attachment(val path: String)` represents a single file attachment referenced by its path relative to the Markdown file that contains it. There is no project-root-relative mode and no legacy fallback path interpretation. `TestStep` carries one shared `attachments: List<Attachment>` list (default empty) for all step-level files. `TestCase` carries a top-level `attachments: List<Attachment>` field (declared after `tags`, before `bodyBlocks`, default empty) for case-level attachments. All attachment lists default to empty so existing files without attachments parse without changes.
- **Attachment resolution and copy contract:** `AttachmentSupport.resolveFile` resolves only from the current markdown file's parent directory. `AttachmentSupport.copyFileToAttachments` copies into the configured attachments folder under that same parent directory and stores the resulting relative path from the markdown file directory, not from the project root. A nested test case such as `test-cases/mcp/sample-login.tc.md` therefore stores copied attachments as `attachments/sample-login/...`.
- **Attachment write contract:** `AttachmentSupport.copyFileToAttachments` performs VFS writes and is called only inside an IntelliJ write action. Tests that exercise the helper must wrap the call in `runWriteAction` rather than invoking it directly from the EDT.
- **Run attachment rebase contract:** `TestRunSupport.createInitialRun` rebases every copied attachment path from the source `.tc.md` directory to the target `.tr.md` directory before serializing the run. If a case at `test-cases/mcp/sample-login.tc.md` stores `attachments/sample-login/screenshot.png` and the run is created under `test-runs/`, the run stores `../test-cases/mcp/attachments/sample-login/screenshot.png`. This applies equally to top-level attachments and to per-step action/expected attachments.
- **Attachment parsing — general (case-level):** An optional `Attachments:` section (case-insensitive marker `^[Aa]ttachments:\s*$`, may appear between body blocks and `Scenario:`) lists case-level attachments. The parser recognises two line formats: Markdown image/link syntax `![alt](path)` or `[text](path)` matched by `^!?\[([^\]]*)\]\(([^)]+)\)$` (path is extracted from group 2), and bare bracket syntax `[path]` matched by `^\[([^\]]+)\]$` (path extracted from group 1). Lines that do not match either format are ignored. The section ends at `Scenario:` or end of body. `bodyBeforeScenarioMarker` stops at `Attachments:` in addition to `Scenario:` so that attachment lines are not fed into body block parsing.
- **Attachment parsing — per-step:** Within the scenario section, attachment lines (matching the same two formats) that appear anywhere inside a step block are collected into that step's shared `attachments` list. Attachment lines are never appended to the action or expected text. The attachment list is flushed onto the current step when a new step starts or at end of the scenario section.
- **Attachment serialization — general (case-level):** `TestCaseSerializer` writes an `Attachments:` section if `testCase.attachments` is non-empty. The section is inserted after all body blocks (and their trailing blank line) and before the `Scenario:` section. Each attachment is written using bare bracket syntax: `[path]`. The section ends with a blank line before `Scenario:`. A private `StringBuilder.appendAttachment(attachment: Attachment, indent: String = "")` helper emits `$indent[${attachment.path}]` followed by a newline.
- **Attachment serialization — per-step:** For each step, `appendStep` writes attachment lines after the expected-result blockquote. Step attachments are rendered as normal Markdown links/images on their own indented lines. The serializer does not interleave attachments before the expected block in the canonical format.
- **Attachment UI - panel integration:** The test-case panel receives `project` and `file` from `SpeqaPreviewEditor` so attachment paths can be resolved relative to the `.tc.md` file. A general "Attachments" section (label `label.attachments`) is rendered between the Preconditions block and the steps section. Adding/removing attachments updates `testCase.attachments`. Clicking an attachment opens it via `FileEditorManager`. The section header is always shown so the section is discoverable even when empty.
- **Attachment UI — StepCard integration:** `StepCard` accepts optional `attachments: List<Attachment>`, `onAttachmentsChange: (List<Attachment>) -> Unit`, `project: Project?`, `tcFile: VirtualFile?`, and `onOpenFile: (Attachment) -> Unit` parameters (all with safe defaults). A single shared attachment block is rendered in the step metadata row; the UI does not expose separate "attach to action" or "attach to expected" affordances. Attachments are only rendered when both `project` and `tcFile` are non-null (guarded by `if` check).
- **Attachment UI — StepsSection integration:** `StepsSection` accepts optional `project: Project?` and `tcFile: VirtualFile?` parameters (after `onTestCaseChange`), forwarded to each `StepCard`. Attachment change callbacks update the step's `attachments` via `testCase.copy(steps = testCase.steps.updated(index, step.copy(...)))`. The `onOpenFile` callback resolves the attachment strictly relative to the current markdown file via `AttachmentSupport.resolveFile` and opens it with `FileEditorManager`. `TestCasePreview` passes its `project` and `file` parameters to `StepsSection` as `project` and `tcFile`.
- **Link data model:** `data class Link(val title: String, val url: String)` defined in `TestCase.kt` immediately after the `Attachment` data class. `TestCase` carries a top-level `links: List<Link> = emptyList()` field (declared after `attachments`, before `bodyBlocks`). The link list defaults to empty so existing files without links parse without changes.
- **Link file format:** An optional `Links:` section (case-insensitive marker `^[Ll]inks:\s*$`) appears in the document body after `Attachments:` and before `Scenario:`. Each link line uses standard Markdown link syntax: `[title](url)` matched by `LINK_PATTERN` (`^\[([^\]]+)\]\(([^)]+)\)$`). Lines that do not match are ignored. The section ends at the next section marker (`Scenario:`, `Attachments:`) or end of body.
- **Link parsing (TestCaseParser):** `parseLinks(body)` scans for `LINKS_MARKER` (`^[Ll]inks:\s*$`) and collects `Link(title, url)` entries from lines matching `LINK_PATTERN` (`^\[([^\]]+)\]\(([^)]+)\)$`). Blank lines are skipped; the section ends at `SCENARIO_MARKER` or end of body. `bodyBeforeScenarioMarker` stops at `LINKS_MARKER` in addition to `ATTACHMENTS_MARKER` and `SCENARIO_MARKER` so link lines are not fed into body block parsing. `parseGeneralAttachments` stops at `LINKS_MARKER` (in addition to `SCENARIO_MARKER`). The `parse()` return includes `links = parseLinks(body)`.
- **Link serialization (TestCaseSerializer):** If `testCase.links` is non-empty, writes `Links:\n\n` then each link as `[title](url)\n` via `appendLink(link: Link)`, followed by a trailing blank line. Section placed after Attachments and before Scenario. Body blocks trailing-blank-line condition includes `testCase.links.isNotEmpty()`. The Attachments trailing blank line condition also considers `testCase.links.isNotEmpty()` so there is a blank line between Attachments and Links when both are present. The `appendLink(link: Link)` private helper emits `[title](url)` followed by a newline, matching the same pattern as `appendAttachment`.
- **Link test coverage:** Parser tests verify: parsing links section with multiple links. Serializer tests verify: links round-trip preservation (serialize then parse preserves Link title and URL), section ordering between Attachments and Scenario. Locator tests verify: links marker and body ranges detected correctly. Patcher tests verify: replacing links, deleting links section, inserting links into document without links section.
- **DocumentRangeLocator — Links:** `DocumentLayout` gains `linksMarkerRange: TextRange?` and `linksBodyRange: TextRange?` (placed after `attachmentsBodyRange`, before `stepsMarkerRange`). The locator adds a `LINKS_MARKER` regex (`^[Ll]inks:\s*$`) and recognizes it as a section marker during the scanning loop. The `"links"` case in the section parsing `when` block uses the same `findBodyRange` helper as other sections. Section ordering for scanning: preconditions, attachments, links, steps. The description-area marker detection also checks `LINKS_MARKER` to avoid mistaking link markers for description text. The `DocumentLayout` return includes `linksMarkerRange` and `linksBodyRange`. Implementation: the scanning loop, section parsing `when` block, description marker check, and return statement are all updated together.
- **DocumentPatcher — Links:** `PatchOperation` gains `SetLinks(links: List<Link>)`. Import `io.github.barsia.speqa.model.Link`. When the section exists and links are non-empty, replaces `linksBodyRange` with `[title](url)\n` lines; if body is null but marker exists, inserts after marker. When the section exists and links are empty, deletes the entire section (marker + body + surrounding blank lines). When no section exists and links are non-empty, inserts `Links:\n\n[title1](url1)\n[title2](url2)\n\n` before the Scenario marker (or at EOF). When no section and empty list, no-op. `formatDocumentLinks` formats each link as `[title](url)\n`. `findLinksInsertOffset` determines the insert position by looking for the Scenario marker. `buildSetLinksEdits`, `formatDocumentLinks`, `findLinksInsertOffset` follow the same pattern as `buildSetAttachmentsEdits`.
- **Link UI - layout:** The Attachments and Links sections are displayed side by side below the Preconditions section, separated by `SpeqaLayout.blockGap`: an equal-width ATTACHMENTS column (header + `AttachmentList`) on the left and an equal-width LINKS column (header + `LinkList`) on the right. `LinkList` receives `project` for dialog support.
- **Scenario label parity:** Test-case files, preview headers, form section titles, empty-state copy, project scaffolds, and file templates all use `Scenario` as the canonical label. SpeQA must not serialize or render `Steps:` for test cases anywhere in the current product.
- **Default template parity:** the built-in default test-case markdown from `SpeqaDefaults.DEFAULT_TEMPLATE` also uses `Scenario:` so a newly created in-memory/default case matches the parser and preview contract even before any file template is applied.
- **Link UI - LinkList:** A vertical list of `LinkRow`s spaced by `tightGap`, followed by an add-link button. The add button uses `SpeqaIconButton` with the `AllIcons.General.Web` icon and tooltip `tooltip.addLink`. Clicking it opens `AddEditLinkDialog` via `invokeLater`; on OK the new link is appended. Clicking a link row opens the URL via `BrowserUtil.browse` only if the URL matches `^https?://.*`. Editing a row opens `AddEditLinkDialog` pre-filled; on OK the link is replaced in place. Deleting shows a confirmation dialog (`Messages.showOkCancelDialog` with `dialog.removeLink.message` / `dialog.removeLink.title`); the link is removed only when the user confirms.
- **Link UI - LinkRow:** A single non-wrapping row with a leading link icon, the title text (accent color, truncated with an ellipsis), an always-visible edit pencil (`SpeqaIconButton`), and a hover/focus-revealed remove `X` in a fixed right-side slot. Clicking the row opens the URL in the system browser; the edit button opens `AddEditLinkDialog` pre-filled with the current link. The row tooltip shows the full URL. It uses the same hover/focus interaction pattern as `AttachmentRow` and is a focusable, button-like element for accessibility.
- **Link UI — AddEditLinkDialog.kt:** `AddEditLinkDialog(project, title, initialTitle, initialUrl)` extends `DialogWrapper`. Uses `com.intellij.ui.dsl.builder.panel` DSL for layout with two labeled rows: Title and URL text fields. OK button enabled only when URL is non-blank (via `doValidateAll`). If title is blank on OK, defaults to the URL value. Companion `show(project, editLink?)` method returns `Link?` (null on cancel). Dialog title from `SpeqaBundle.message("dialog.addLink.title")` or `"dialog.editLink.title"`.
- **Attachment filename truncation:** `AttachmentRow` displays the filename through a private `middleTruncate(name, maxLength=25)` helper that preserves the file extension by inserting `...` in the middle of an over-long name (e.g. `very-long-filename...pdf`). The native label ellipsis remains as a fallback for narrow widths. For extensionless names, the full name is treated as the base with `...` appended after truncation.
- **Attachment removable action:** `AttachmentRow` uses the shared removable-row action pattern: the row reserves a fixed right-side slot, renders a hover/focus-revealed destructive close `X` in that slot, and never uses a trash icon for removing the attachment reference. While the cursor is over the remove action, attachment preview popovers are suppressed so the remove tooltip and click target remain usable.
- **Attachment test coverage:** `TestCaseParserTest` verifies: (1) parsing a general `Attachments:` section with bare bracket syntax yields `testCase.attachments`; (2) step attachments before or after the expected block both land in the same shared `step.attachments`; (3) attachment lines never leak into action or expected text; (4) standard Markdown link/image syntax (`[text](path)` and `![alt](path)`) in the `Attachments:` section is parsed correctly. `TestCaseSerializerTest` verifies a round-trip (`round trip preserves attachments`): a `TestCase` with case-level attachments and shared step attachments serializes and re-parses with all attachment paths and counts preserved.
- **Environment normalization contract:** `environment` has the same data type in both `TestCase` and `TestRun`: `List<String>`. A scalar frontmatter value, quoted or unquoted, always means exactly one environment entry, even when it contains commas (`environment: "test1, env20"` and `environment: test1, env20` both mean one environment). Multiple environments are expressed only as a YAML list. Filtering, lookup, serialization, and UI editing always operate on these parsed list entries; SpeQA must never split a scalar environment value on commas.
- **Interactive chip behavior in test cases:** In `TestCasePreview`, each tag chip and each environment chip is a primary navigation control. Left-click filters SpeQA test cases by the clicked normalized value. Hover uses a short sentence-case tooltip (`Show cases with this tag` / `Show cases with this environment`). Right-click opens a context menu. For tags the menu contains `Find Test Cases With This Tag`, `Copy Tag`, and `Remove`. For environments the menu contains `Find Test Cases With This Environment`, `Copy Environment`, and `Remove`. In editable chip clouds, the edit pencil is always visible as the non-destructive affordance. Its leading gap must match the trailing gap from the pencil to the visible chip edge, so the icon reads as balanced inside the chip. Hover/focus on the pencil changes only the pencil icon tint; it must not paint a button background inside the chip. The destructive remove affordance is progressive-disclosure UI: a readable corner close glyph centered on the chip fill's top-right border with a small inward horizontal inset, visible only while the chip or any child action is hovered or keyboard-focused. Chip hover state is exclusive across chips and must follow the current pointer location, not just individual Swing enter/exit event ordering: entering one chip clears stale hover from the previously hovered chip, and any later pointer movement outside the active chip clears that active hover. Moving directly from one chip to another must leave the remove affordance visible on the current chip only. The chip always reserves a small transparent corner hit area for that control, so the control is clickable and sits on the border rather than inside the filled chip. That reserved area is stable and not hover-dependent, so hover/focus never changes chip width, wrap decisions, or neighboring chip positions. Delete/Backspace on a focused chip still removes the value.
- **Interactive chip behavior in test runs:** In `TestRunPanel`, both `tags` and `environment` are editable metadata collections rendered in the run header using the same chip-editor interaction model as test-case metadata, while preserving the existing test-run header layout. Users can add and remove values inline in the run. Existing chips remain navigable metadata values: left-click filters by that value, hover uses the short sentence-case tooltip, and right-click opens the same metadata context actions as the equivalent chip in test cases.
- **Filter result source:** Tag/environment filtering is not `Find in Files` and not raw text grep. SpeQA resolves matches from a project-level frontmatter index that maps normalized tag/environment values to `.tc.md` and `.tr.md` files. The result UI may use an IDE-native chooser/popup, but the candidate set must come from this normalized index.
- **Index scope and parsing:** The project index tracks four maps: tag → test cases, tag → test runs, environment → test cases, environment → test runs. The index reads frontmatter only and accepts all supported normalized forms for both fields. `allKnownTags` and `allKnownEnvironments` used for test-case suggestions remain sourced from test cases only; test-run-only values must not pollute creation/edit suggestions.
- **Tag/environment index threading:** `SpeqaTagRegistry` must never read file contents on the EDT. `allKnownTags`, `allKnownEnvironments`, and metadata-click lookups read only the latest in-memory snapshot. If the registry is not initialized yet, the first full scan is scheduled in the background and the UI receives an empty-or-stale snapshot until it completes. Preview composition and popup opening must not synchronously trigger VFS reads.
- **Tag-registry async completion:** The background scan completion path must use IntelliJ-platform non-blocking-read callbacks that exist in the current target SDK. The previous attempted `whenComplete` future-style hook is not available on this promise type and must not be used.
- **Tag-registry test contract:** Tests for `SpeqaTagRegistry` must treat initialization as asynchronous. They should wait for the indexed snapshot to become populated instead of assuming `ensureInitialized()` performs a synchronous full scan.
- **Tooltip and context-menu wording:** Hover tooltips on metadata chips are intentionally shorter than context-menu items and use sentence case (`Show cases with this tag`, `Show cases with this environment`, `Show runs with this tag`). Context-menu items keep the explicit target nouns (`Tag` vs `Environment`, `Test Cases` vs `Test Runs`) because they stand alone outside the hovered chip context. Delete affordances in test cases use the short label `Remove`; the chip text itself already identifies the value being removed.
- **Popup row left alignment:** Both lines in the metadata result popup are left-aligned to the same text edge. The secondary path line must not inherit centered `BoxLayout` alignment from Swing defaults.
- **Platform popup/clipboard integration:** Chip context menus use IntelliJ platform popup/data APIs that are available in the current target platform (`CopyPasteManager` from AWT clipboard support, `DataManager` from `com.intellij.ide`). The implementation must target currently available platform APIs rather than older package locations.
- **Click handling:** Metadata chips handle a primary (left) click as the filter action and a secondary (right) click as the context menu. Both gestures are handled by the chip's Swing mouse listener; the right-click path opens the chip context menu without blocking the normal left-click filter path.
- **Chooser consistency:** Clicking a metadata chip always opens the same result chooser UI, even when there is only one matching file. SpeQA must not silently “re-open” the current file or switch behavior based on result count.
- **Environment add text:** The add button tooltip/content description and inline placeholder for the `Environment` field in test cases use environment-specific wording (`Add environment`) instead of tag wording.
- **Popup result contract:** Chip click opens a result popup every time, even when the only match is the currently opened file. The popup explicitly marks the current file and keeps the match list visually padded and styled to fit the SpeQA editor aesthetic rather than the raw default list renderer.
- **Current-file presence rule:** The current file is included in the popup results and marked as current instead of being filtered out or silently reopened. This confirms that the lookup worked even when there are no additional matches.
- **Context-menu reliability:** Right-click on a metadata chip must always open the chip context menu. If popup scheduling is needed to avoid event-cycle conflicts, it must preserve the exact screen position and remain reliable on desktop.
- **Popup row presentation:** Metadata result rows use a custom renderer with explicit insets and a secondary status label for the current file. The popup may still be an IDE-native `JBPopup`, but its inner list presentation must be intentionally styled rather than relying on the raw default chooser row.
- **Popup title wording:** Metadata result popup titles use sentence case and include the clicked value, e.g. `Test cases with tag "auth"` or `Test runs with environment "Chrome 120"`. The title is truncated with an ellipsis when needed rather than expanding the popup indefinitely.
- **Popup width limit:** The metadata result popup has a bounded width. Long tag/environment values, titles, file names, and secondary location text are ellipsized instead of stretching the popup wider than the intended layout.
- **Two-line result rows:** Each result row uses at most two lines. Line 1 shows the SpeQA ID and title as separately styled pieces, not one flat string. Line 2 shows only the relative file path. There is no third line and no duplicated file name/path split across multiple rows.
- **Destructive remove action:** Removing a tag or environment from a test case remains a destructive action and requires confirmation. The context menu entry stays short (`Remove`), but the confirmation dialog names the value being removed.

---

## 4. Test Run File Format (.tr.md)

Test run is self-contained — stores all data needed to display and execute the run. No reference to the originating test case file.

**File naming:** `{tc-stem}_{YYYY-MM-DD_HH-mm-ss}.tr.md`
Example: test case `sample-login.tc.md` → run `sample-login_2026-04-11_17-07-08.tr.md`

```markdown
---
id: 1
title: "Login with valid credentials"
priority: major
started_at: 2026-04-06T14:30:00
finished_at: 2026-04-06T14:35:00
result: failed          # not_started | passed | failed | blocked
manual_result: false    # true if user manually overrode result
environment: "Chrome 120, macOS 14"
runner: "QA Engineer"
tags:
  - auth
  - smoke
---

User must have a valid account.

Preconditions:

Browser is open to login page.

Links:

[Jira Ticket](https://jira.example.com/123)

Attachments:

![screenshot.png](attachments/screenshot.png)

Scenario:

1. Enter valid username  
   > Email field accepts input  
   > No validation error is shown  
   - passed

2. Enter valid password  
   > Password is masked  
   - passed

3. Click Login button  
   > User is redirected to dashboard  
   > User avatar is visible in the header
   ![screenshot.png](attachments/screenshot.png)  
   - failed

   Comment:  
   Got 500 error instead of redirect.  
   Retry shows the same server error.

Overall: needs investigation.
```

**Step format in `.tr.md`:**
- Steps are preceded by a `Scenario:` marker line followed by a blank line.
- Line 1: `N. {action first line}` — action text (first line)
- Expected lines (optional, one or more): `   > {expected line}` — expected result block. Multiline expected results are written as multiple consecutive `>` lines. A visual-editor newline inside expected text is serialized as a Markdown hard break with two trailing spaces on the preceding raw line.
- Attachment lines (optional): `   ![name](path)` (images) or `   [path]` (files) written after the expected block.
- Verdict line (optional): `   - {verdict}` — verdict (`passed`/`failed`/`skipped`/`blocked`) as a nested bullet after the expected/attachment block. The previous `— verdict` line format is obsolete and not supported in the current product.
- Comment block (optional): starts with a blank line, then `   Comment:`, and is followed by zero or more indented comment body lines. A visual-editor newline inside the comment is serialized as a Markdown hard break with two trailing spaces on the preceding raw line. Repeating `Comment:` for every line is obsolete and not supported in the current product.
- Text after the last step (outside any step block) is the overall `comment` field.
- The `## Summary` section is not part of the `.tr.md` format and is never written or parsed.
- **Balanced readability contract:** SpeQA keeps the execution block on its own lines after the expected/attachment block instead of collapsing expected, verdict, and comment into one inline sentence. This keeps the raw Markdown inspectable in GitHub and preserves unambiguous parsing in SpeQA.
- **Comment spacing contract:** the step-level `Comment:` block is always visually separated from the preceding part of the step by one empty raw line, even when the step has only action plus comment. This avoids serializing `Comment:` as if it were just another action continuation line.
- **Overall comment spacing contract:** the overall run comment is separated from the scenario by exactly one empty raw line. SpeQA must not add a second synthetic blank line on top of a user-created trailing empty line in the last step comment.
- **Comment block termination rule:** a step-level `Comment:` block continues only across indented continuation lines and blank separator lines that still belong to the step. The first non-indented non-step line after a comment block terminates step parsing and belongs to the overall run comment, not to the last step comment.
- **Multiline text contract:** `action`, `expected`, and `comment` are all multiline-capable. In raw Markdown, a visual-editor newline inside one logical block is represented by two trailing spaces before the physical newline. The parser must round-trip these hard breaks back into `\n` in the model, and the serializer must restore the trailing-space Markdown representation when writing.
- **Indent normalization rule:** when parsing step content from raw Markdown, SpeQA strips only the list-continuation indent that belongs to the step structure itself. The parser must not keep those structural spaces in `action`, `expected`, or `comment` model values.
- **Scenario parity with test cases:** the test-run step body intentionally reuses the same numbered action block, expected blockquote structure, and attachment placement as `.tc.md`. The only run-specific addition is execution metadata after the expected/attachment block (`- verdict` and `Comment:` block). SpeQA preview must therefore show the test-run step in the same visual reading order as the test-case step, with run-only execution controls layered after the shared action/expected content rather than replacing it with a different layout grammar.
- **Visual step parity:** On non-narrow widths, `TestRunPanel` step rows use the same two-column `action | expected` content layout and the same responsive breakpoint behavior as `StepCard`. The run row keeps its execution controls (`verdict` and step comment) below that shared content area, but the scenario part of the step must look like the same object in both editors.
- **Shared step frame:** `ScenarioStepFrame` owns the common step shell for both editors: a left step-number gutter, a wide breakpoint that switches between stacked and two-column content, and the outer spacing between the scenario content and the metadata/execution blocks. `StepCard` and `StepResultRow` provide their own content into that frame, but neither is allowed to reimplement the wide/narrow shell independently.
- **Metadata row contract:** `StepMetaRow` remains generic and context-agnostic. It renders tickets, links, and attachments from the data and callbacks the caller supplies, so the same row can be reused unchanged in both the test-case editor and the run panel.
- **Ticket removable action:** Ticket ID chips use the same removable-row action pattern as links and attachments: their remove control is a hover/focus-revealed close `X` in a fixed right-side slot, not an always-visible trash can. The slot remains reserved at rest, so ticket rows do not resize or rewrap when the remove action appears.
- **Ticket activation tooltip:** Ticket chips are primary activation controls that open the configured tracker URL in the browser. Their hover tooltip must describe that action instead of duplicating the visible ticket ID. When the ticket ID is fully visible, the tooltip is `Open in browser`; when Swing ellipsis clips the ticket ID, the tooltip is `Open <full ticket ID> in browser` so the tooltip both explains the action and reveals the hidden value.
- **Metadata add-action alignment:** the three empty-state add actions in `StepMetaRow` (`Add ticket ID`, `Add link`, `Attach file`) use one shared visual contract. All three must have the same fixed interactive row height and vertical centering as saved ticket/link/attachment rows, so switching a column from placeholder to saved value does not move the text baseline up or down. The ticket inline input uses the same row-height metric as the add action it replaces.
- **Metadata narrow-width contract:** when the step frame is in narrow mode (`< 440dp`, the same breakpoint where `action` and `expected` stack vertically), the metadata row keeps the three add actions on one line instead of letting them wrap or collapse into unequal widths. In that mode, the ticket, link, and attachment add actions each occupy one equal-width third of the row, render as single-line labels, and truncate with ellipsis when the text does not fit. The narrow contract applies to both test-case and test-run steps through the shared `StepMetaRow`.
- **Run step editability matrix:** In `TestRunPanel`, the whole step stays editable except for run-derived execution summary fields. `action`, `expected`, `tickets`, `links`, and `attachments` are all editable inline in the run step. At the run level, `title`, `runner`, `tags`, `environment`, top-level `links`, top-level `attachments`, `description`, `preconditions`, and the overall run comment remain editable. The run header keeps its existing overall structure; parity applies to the step body, not to the entire test-run screen.
- **No legacy syntax support:** the current product does not support the older `— verdict` line, repeated one-line `Comment: text` entries, or step attachments placed before the expected block as alternate write formats. Migration to the new Markdown shape is explicit; parser and serializer tests cover only the current canonical format.
- **Preview rendering contract for run steps:** `TestRunPanel` must render `step.comment` as part of the step content block, directly after verdict and in the same vertical flow as action, expected, and attachments. The preview must not hide the stored comment behind a separate interpretation layer that makes the markdown step appear truncated. Inline editing controls may still exist, but the visible step structure must match the canonical markdown order from the file.
- **Comment editing stability:** step-comment edit mode in `TestRunPanel` is UI state, not content-derived state. It must not reset on every `step.comment` value change. The user must be able to freely edit a comment down to an empty string and keep focus in the same input until they explicitly leave edit mode.
- **Comment editability rule:** a visible step comment block in `TestRunPanel` is always directly editable. SpeQA does not support a separate read-only comment presentation mode for step comments. If the comment block is shown, clicking it places the caret and typing edits the same field immediately.
- **Step comment spacing:** in `TestRunPanel`, the visible `Comment` label keeps a slightly looser gap to its text field than the default compact item spacing. The label and input use the same vertical rhythm as other small section headers inside the run step, so the field does not feel glued to the caption.
- **Comment presence indication:** the step comment toggle must indicate stored comment content whenever `step.comment.isNotBlank()`, regardless of whether the comment block is currently expanded or collapsed. The current product uses two signals together: accent tint on the balloon icon and a small accent badge dot on the same icon button.
- **Comment toggle focus styling:** the comment toggle remains keyboard-focusable, but its accent focus ring is shown only for keyboard navigation focus (for example via `Tab` / `Shift+Tab`). Mouse click focus on the toggle must not display the same ring, because the stored-comment tint/badge already communicates state and the mouse-focused ring is visually misleading there.
- **Comment toggle implementation detail:** the run-step comment toggle uses a keyboard-focus-only ring configuration of `SpeqaIconButton`, so the control remains tabbable while pointer interaction does not show the same ring.
- **Comment toggle hide behavior:** collapsing the comment block must not programmatically re-request focus on the comment toggle. After hide, focus follows the platform's natural result of the triggering interaction; SpeQA must not add an extra focus transition that reintroduces the ring on pointer-driven collapse.
- **Swing StepCard run-mode step sync:** when a step's `action`, `expected`, `tickets`, `links`, or `attachments` change in run mode, `TestCasePanel` must update both `currentCase.steps` and `currentRun.stepResults` (preserving each step's `verdict`/`comment`) before any patch is dispatched. Otherwise a later full-snapshot serialization sees the stale run snapshot and reverts the edit. Per-field patches still flow through `StepsSection.emitStepFieldPatches`.
- **Run-mode delete ordering:** in `StepsSection`, the run-mode card `onDelete` must call `onStepsChange(next)` *before* `onStepPatch(DeleteStep)` so the parent panel updates `currentRun.stepResults` first. Otherwise `TestRunEditor.patchFromPreview` captures the stale snapshot as `current` and any `DocumentPatcher.patch` fallback to whole-document serialization reintroduces the deleted step. This mirrors the CASE-mode delete order.
- **Shared run-step identity:** in RUN mode, `TestCasePanel` owns the synthetic `TestStep` wrappers that represent each `StepResult` row. `updateFromRun` builds the wrappers via `buildRunSteps(results)` (preserving each row's `uid` by position from the previous wrappers and minting fresh uids for newly added rows), assigns them to `currentCase.steps`, and hands the same list to `StepsSection.setRunStepResults(runSteps, results)` / `updateRunStepResultsInPlace(runSteps, results)`. `StepsSection` no longer mints its own uids in run mode. This guarantees that `onStepsChange.prevSteps` and the steps emitted by `StepsSection` share uid identity, so the uid-keyed `resultByUid` lookup correctly preserves `verdict` / `comment` on the very first edit, reorder, delete, or duplicate.
- **Run-mode structural step ops contract:** `TestCasePanel.onStepsChange` in RUN mode rebuilds `currentRun.stepResults` by mapping each new `TestStep` to its previous `StepResult` via `TestStep.uid`, not by list index. Steps with no prior `uid` (newly added or duplicated) get a fresh `StepResult` with `verdict=NONE` / `comment=""`; existing steps keep their verdict and comment regardless of how they were reordered. This guarantees that reorder/duplicate/delete do not shift verdict/comment to the wrong step before the run snapshot is persisted via `onRunPatch`.
- **Verdict tint color tokens:** the four step-verdict tint colors live in `editor/ui/theme/SpeqaThemeColors.kt` as `verdictPassedBackground` / `verdictFailedBackground` / `verdictSkippedBackground` / `verdictBlockedBackground` (plus `verdictSelectedForeground`). Each is a `JBColor.namedColor("Speqa.Verdict.{Passed|Failed|Skipped|Blocked}.background", JBColor(light, dark))` so IDE themes can override them via UIManager keys. UI call sites (`StepVerdictRow`, `StepCard`) must reference these tokens from `SpeqaThemeColors`; raw `Color(...)` literals are forbidden at call sites.
- **Tag/environment case-insensitive uniqueness:** in `TagCloud` and `AddTagPopup`, value comparisons against the current selection (chip dedup, "Create '…'" gate, post-pick guard) are case-insensitive so that, e.g., adding `bug` when `Bug` already exists does not create a duplicate chip.
- **Swing StepCard comment toggle:** in the Swing `StepCard` (run mode), the step comment field is collapsed by default for steps with no stored comment, and expanded by default when `step.comment.isNotBlank()`. The toggle button (`AllIcons.General.Balloon`, `speqaIconButton`) is placed as the fifth element in the same horizontal verdict row, after the four verdict chips. The comment section (label + textarea) is added to `contentPanel` below the verdict row and shown/hidden via `isVisible`. A `CommentDotPanel` wrapper overlays a 4 px accent dot on the top-right of the icon when `hasStoredComment` is true. The `setRunComment` setter refreshes `hasStoredComment` and repaints the wrapper so the dot updates immediately.
- Attachment lines (`![...](...)`, `[...](...)`-style image/link, or bare `[path]`) inside step blocks are recognized by the parser and routed into the step's shared `attachments` list instead of accumulating into action, expected, or comment text. Pattern: `STEP_ATTACHMENT_PATTERN = Regex("""^!?\[.*]\(.*\)$|^\[.*]$""")`.
- `parseOverallComment` also recognizes attachment lines (matching `STEP_ATTACHMENT_PATTERN`) inside step blocks as step-related content (updates `lastStepLine`), preventing them from leaking into the overall comment.

**Parser changes:**
- `parseStepResults` no longer looks for `## Step Results` heading. Instead, it parses the entire body after frontmatter, stopping at any `## ` heading or end of file. `parseStepResults` uses a `collectingAction` boolean state: set `true` after matching a step line, during which non-blank trimmed lines that do not match expected (`>`), verdict (`—`/`-`), attachment, or next step patterns are appended to the action with `\n`. `collectingAction` is set `false` when any of those patterns match. `STEP_ATTACHMENT_PATTERN` (`^!?\[.*]\(.*\)$|^\[.*]$`) matches attachment lines and causes them to be skipped (not added to action/expected/comment).
- Multiline expected: all consecutive `>` lines before the verdict are accumulated (joined with `\n`) as `expected`. The parser does not strip any synthetic `Expected:` prefix because that prefix is not part of the current `.tr.md` format. `TestRunPanel` `summary` and `onSummaryChange` parameters removed; the summary section UI block (the `if (summary.isNotBlank()) { ... }` block) is removed entirely. `TestRunTest` removes the `assertEquals("", testRun.summary)` assertion. `TestRunSerializerTest` removes the `omits Summary section when summary is blank` and `writes Summary section when summary is not blank` test methods (and their `summary = ...` constructor arguments).
- The `summary` field is removed from `TestRun` data class. The parser does not extract `## Summary`. Header date icons: `calendarCreated.svg` for the Created `DateIconLabel`, `calendarUpdated.svg` for the Started `DateIconLabel` (previously both used `calendarCreated.svg`). `TestRunEditor` removes `summary` mutable state, the `onSummaryChange` callback and its call site, the `summary = snapshotSummary` field from `saveToDocument`'s `TestRun(...)` constructor call, and `snapshotSummary` local variable. The `refreshTimer` block no longer assigns `summary = parsed.summary`. The `summary = summary` and `onSummaryChange = { ... }` lines in the `TestRunPanel(...)` composable call are removed.

**Serializer changes:**
- Serializer writes full test run content: frontmatter (with `priority` if non-null, no `status`), body blocks, links section, attachments section, `Scenario:` marker, numbered steps, and overall comment.
- Step comments are written as explicit comment lines: `step.comment.lines().forEach { line -> appendLine("   Comment: $line") }`.
- Multiline expected results are written as multiple `>` lines: `step.expected.lines().forEach { line -> appendLine("   > $line") }`.
- Body blocks, links, and attachments are written in the same format as test case files (reusing `TestCaseSerializer` patterns). Serializer imports `Attachment`, `DescriptionBlock`, `Link`, `PreconditionsBlock`, `TestCaseBodyBlock` from model.

**Editable metadata in test runs:**
- `TestRun` carries `tags: List<String> = emptyList()` and `environment: List<String> = emptyList()` with the same collection semantics as `TestCase`. Both may be imported from the source test case during run creation and both remain editable afterwards in the run header.
- `TestRunSupport.createInitialRun` accepts explicit import options from `RunCreationDialog`. It always copies body blocks and step `action` / `expected`. It conditionally copies `tags`, `environment`, top-level `links`, top-level `attachments`, and step `tickets` / `links` / `attachments` according to the chosen checkboxes. No imported category may be silently dropped during `TestCase -> TestRun` creation.
- `TestRunSerializer` and `TestRunParser` both use list semantics for `tags` and `environment`. For `environment`, a scalar frontmatter value remains one list entry even if it contains commas; only a YAML list means multiple environments.
- Serializer and parser are intentionally strict here: there is no backwards compatibility layer for `Steps:`, `>>`, or bold verdict lines in `.tr.md`. Any migration of old files must happen as an explicit rewrite, not as hidden runtime compatibility logic.
- `TestRunEditor` owns mutable state for `title`, `tags`, `environment`, top-level `links`, top-level `attachments`, and `stepResults`. Re-parsing the document updates that live state, and `saveToDocument()` serializes the current edited run state rather than the initial imported snapshot.
- `TestRunPanel` renders editable header metadata for `tags` and `environment` using the same chip-editor interaction model as test-case metadata while preserving the existing run-header layout.

**Verdict format change:**
- The serializer writes `   — {verdict}` (plain text, no `**bold**` markers).
- The parser accepts only the plain current format.
- `TestRunSerializerTest` is updated: tests that checked for `— **verdict**` now check for `— verdict` (no asterisks). The `omits verdict line for NONE` test is updated to check no `—` marker is present (rather than checking for `**`).

**Write-safe document updates in TestRunEditor:**
- `TestRunEditor.saveToDocument` wraps the `CommandProcessor.executeCommand` + `runWriteAction` call inside `ApplicationManager.getApplication().invokeLater({ ... }, ModalityState.defaultModalityState())` to ensure writes always occur on the EDT with correct modality, preventing `TransactionGuardImpl` exceptions when `saveToDocument` is triggered from a UI event callback.

**Storage:** `.tr.md` files are created in the designated test runs directory from plugin settings (default: `test-runs/`)

**Data model (Task 1 changes):**
- `StepVerdict` enum: `NONE("")` is the first entry (no verdict set), followed by `PASSED`, `FAILED`, `SKIPPED`, `BLOCKED("blocked")`. `fromString` returns `NONE` for blank or unknown strings (not SKIPPED).
- `RunResult` enum: `NOT_STARTED("not_started")` is the first entry, followed by `PASSED`, `FAILED`, `BLOCKED`. `fromString` returns `NOT_STARTED` for "not_started" and as the default for unknown values. `NOT_STARTED` is display-only — not shown in the `ListComboBox` dropdown items (only Passed/Failed/Blocked are selectable).
- `StepResult` has fields: `action`, `expected`, `tickets: List<String> = emptyList()`, `links: List<Link> = emptyList()`, `verdict`, `comment`, `attachments: List<Attachment> = emptyList()`. Default verdict is `NONE`. The shared scenario-content fields mirror the corresponding `TestStep` fields so a run can preserve and edit the same metadata categories without silent loss.
- `TestRun` has fields: `id`, `title`, `tags`, `priority: Priority? = null`, `manualResult`, `startedAt`, `finishedAt`, `result`, `environment`, `runner`, `bodyBlocks: List<TestCaseBodyBlock> = emptyList()`, `links: List<Link> = emptyList()`, `attachments: List<Attachment> = emptyList()`, `comment: String = ""`, `stepResults`. `priority` is copied from the test case at run creation. `bodyBlocks` and step `action` / `expected` are always copied from the test case as the initial run snapshot, but all three remain editable afterwards inside the run. `tags`, `environment`, top-level `links`, top-level `attachments`, and step `tickets` / `links` / `attachments` are imported according to the user's run-creation options and remain editable afterwards. The `comment` field stores an overall run comment (text after the last step in the file body).
- `TestRun.startedAt` is `LocalDateTime? = null` (nullable — null means not started). `TestRun.finishedAt` is `LocalDateTime? = null` (nullable — null means not finished). Default `result` is `RunResult.NOT_STARTED`.
- `TestRunSupport.createInitialRun` accepts explicit import options from `RunCreationDialog`. It always copies `priority`, `bodyBlocks`, and step `action` / `expected`. It conditionally copies `tags`, `environment`, top-level `links`, top-level `attachments`, and step `tickets` / `links` / `attachments` based on the chosen checkboxes. Sets `startedAt = null` (not started yet). No longer passes `startedAt`, `finishedAt`, or `result` explicitly — they use defaults.
- `TestRunSupport.deriveRunResult` returns `RunResult.NOT_STARTED` instead of null when no meaningful verdicts exist. Return type is `RunResult` (non-nullable).
- `TestCasePanel` (run mode) auto-manages `startedAt` / `finishedAt` / `manualResult` / overall `result` whenever a step verdict or the overall run-result combo changes. The pure helper `RunAutoTimestamps.apply(next, manualResultOverride)` computes the new state from the proposed next run: `startedAt` is set to `LocalDateTime.now()` the first time any step has a non-`NONE` verdict or the user picks a terminal result manually, and never reset afterwards. `finishedAt` is set to `LocalDateTime.now()` when the last `NONE`-verdict step transitions to evaluated, sticks while all steps remain evaluated, and resets to `null` if any step rolls back to `NONE` (unless the user manually picked a terminal result). Picking the run-result combo flips `manualResult = true` (cleared back to `false` when the user picks `NOT_STARTED`); when `manualResult` is true and the result is `PASSED`/`FAILED`/`BLOCKED`, `finishedAt` is forced to a value (set to `now()` if currently null). The overall `result` is auto-derived from step verdicts via `TestRunSupport.deriveRunResult` whenever `manualResult` is `false`: no evaluated steps -> `NOT_STARTED`, partial -> `IN_PROGRESS`, fully evaluated -> `FAILED` if any step is `FAILED`, else `BLOCKED` if any step is `BLOCKED`, else `PASSED` (with `SKIPPED` treated as neutral). When `manualResult` is `true`, the user's chosen `result` is preserved verbatim and step verdicts no longer override it. The verdict / result emit sites compare the incoming and outgoing state: if any of `startedAt`, `finishedAt`, `manualResult`, or `result` changed, the emission falls back to the whole-document write path (no targeted op), so the YAML frontmatter is rewritten with the new keys. When none of those fields changed, the targeted patch op (`SetRunStepVerdict` / `SetRunVerdict`) is used as before.
- `TestRunPanel` header shows Created date (from file, passed as `createdLabel: String`), Started date (if `startedAt != null`), Finished date (if `finishedAt != null`). Created date resolved via `resolveGitCreatedInstant` (same as test case preview).
- Serializer only writes `started_at` and `finished_at` if non-null. Parser defaults `startedAt` to null if not present.

**Full content in test run files:**
- Test run files carry a self-contained snapshot of the test-case scenario. `bodyBlocks` and step `action` / `expected` are always present. `tags`, `environment`, top-level `links`, top-level `attachments`, and step `tickets` / `links` / `attachments` are present only if imported at creation time or later added by the user while editing the run.
- Serializer writes body blocks after frontmatter (before steps), then links section (`Links:\n\n[title](url)\n`) when the run has top-level links, then attachments section (`Attachments:\n\n` with image/file format) when the run has top-level attachments, then `Scenario:` marker followed by numbered steps, then overall comment if non-blank.
- Step comments are written only as explicit `Comment:` lines: `step.comment.lines().forEach { line -> appendLine("   Comment: $line") }`.
- Parser step comment logic is explicit-only: after a verdict line, only `Comment:` lines are collected as step comments. Plain indented text remains action continuation unless it matches expected, attachment, or the next step.
- Ambiguity rule: a plain indented continuation line such as `   **wwwwwwww**` remains part of the step action even when it appears after richly formatted inline Markdown in the action. Without the explicit `Comment:` marker, it must never become a step comment.
- `parseOverallComment` tracks `inStepBlock` (set true on step line, reset on blank line). Any non-blank line starting with `"   "` while `inStepBlock` is true is treated as step-related content and updates `lastStepLine`. Attachment lines matching `STEP_ATTACHMENT_PATTERN` inside step blocks also update `lastStepLine`, so they never leak into the overall comment field.
- **`extractStepSection` strictness:** the parser starts step parsing only after the explicit `Scenario:` marker. Numbered list items before `Scenario:` are always body content; there is no fallback start from the first numbered line.
- **Step attachment parsing in test run:** attachment lines inside step blocks are parsed into the step's shared `attachments` list regardless of whether they appear before or after expected blockquote lines. The parser uses `parseAttachmentLine` to extract `Attachment(path)` from each matched line. The attachment list is flushed onto the current step when a new step starts or at end of section, matching the same flush pattern as `TestCaseParser.parseSteps`.
- Bare-bracket attachments keep their literal path text. `[report.pdf]` parses as `Attachment("report.pdf")`; the parser must not invent an `attachments/` prefix.
- Parser extracts `Links:` section (parsing `[title](url)` lines), `Attachments:` section, and body blocks (text between frontmatter and the first section marker or `Scenario:`).
- Parser reads `priority` from frontmatter via `Priority.fromString` (null if key absent). `status` parsing and `Status` import removed.
- Overall comment: any non-blank text after the last step is parsed as `comment`. Parser imports `Priority`, `Status`, `Attachment`, `Link`, `DescriptionBlock`, `PreconditionsBlock`, `TestCaseBodyBlock`, `PreconditionsMarkerStyle` from model.

**Next button and actions row removed:**
- `TestRunPanel` no longer has `onNext`, `currentStepIndex` parameters or the actions row (review prompt text + Next button).
- `TestRunEditor` no longer tracks `currentStepIndex` state.

**StepCard layout:**
- The step meta row lays out its ticket, link, and attachment columns with even spacing and no vertical hairlines between columns.

**Test run step row visuals:**
- Each run step row matches the test-case step shell (gutter width and wide/narrow breakpoint) and paints a verdict-colored left bar at the outer edge, tinted from `SpeqaThemeColors` verdict backgrounds per verdict (`NONE` paints no bar).
- The expected text in a run step row renders directly as read-only Markdown without a separate label, so the run row stays visually aligned with the test-case step shell while keeping the field non-editable.

**Comment sync bug fix:**
- `TestRunEditor.saveToDocument` sets `suppressDocumentRefresh = true` before `invokeLater`, and resets it inside the `invokeLater` block AFTER the write completes in the `finally` block. Previous implementation had correct placement but the timing was verified and confirmed correct.
- `TestRunSupport.createInitialRun` no longer takes `testCaseFileName`. It copies `testCase.title` into `TestRun.title` and `step.expected.orEmpty()` into each `StepResult.expected`. Methods `resolveLinkedTestCase`, `synthesizeTestCase`, `prettifyTitle`, `testCaseStem`, and `mergeStepResults` are removed. The private `anyIndexed` extension is also removed. `completedStepIndexes` now checks `StepVerdict.NONE` instead of `SKIPPED` for the "not yet acted on" condition.
- `TestRunEditorProvider` no longer calls `resolveLinkedTestCase`; passes only `initialRun` (no `testCase`) to `TestRunEditor`. `TestRunEditor` no longer takes a `TestCase` constructor parameter; replaces `testCase.title` with `initialRun.title`, `testCase.steps.size` with `initialRun.stepResults.size`, `testCase.environment` with empty list for now, and initializes `stepResults` directly from `initialRun.stepResults` without merging.
- `TestRunPanel` replaces `testCase: TestCase` parameter with `title: String` and `environmentOptions: List<String>`. All `testCase.*` references updated accordingly. The `TestCase` import and unused `UtilityText` import are removed.
- `TestRunParser` removes `test_case` field parsing from frontmatter. It adds `title` parsing: `title = SpeqaMarkdown.parseScalar(meta["title"])`. Default verdict in `parseStepResults` fallback changes from `SKIPPED` to `NONE`.
- `TestRunSerializer` removes `test_case:` line from frontmatter output. It writes `title:` (from `TestRun.title`) as the second frontmatter field after `id`. `TestRunEditorProvider.createEditor` removes `resolveLinkedTestCase` call and `linkedTestCase` variable; passes `(project, file, document, initialRun)` to `TestRunEditor`.

**Task 4 — `TestRunParser` rewrite to parse the current strict multi-line step format:**
- `TestRunParser.parse` parses the current `.tr.md` format where steps are multi-line: `N. {action}` starts a step, `> {text}` lines before verdict accumulate into `expected`, `— {verdict}` sets verdict, and `Comment: {text}` lines accumulate into the step comment. Steps without a verdict line get `StepVerdict.NONE`.
- The parser uses `STEP_PATTERN` (`^\d+\.\s+(.+)$`), `VERDICT_PATTERN` (`^[—-]\s*(passed|failed|skipped|blocked)$`, case-insensitive), `EXPECTED_PATTERN` (`^>\s?(.*)$`), and `COMMENT_PATTERN` (`^Comment:\s?(.*)$`, case-insensitive).
- `parseStepResults` tracks a `verdictSeen` boolean per step so explicit `Comment:` lines become step comments while plain indented non-marker text stays in the action.
- `parseDateTime` signature changes: `fieldName` parameter is removed; invalid timestamps now return `null` via `runCatching { ... }.getOrNull()` instead of throwing. This makes the parser lenient for corrupted or hand-edited files.
- `TestRunParserTest` is rewritten around the strict format: `title` from frontmatter, `manual_result` flag (both true and default false), step with expected and verdict, step without verdict gets `NONE`, explicit `Comment:` lines after verdict, `blocked` verdict, multiple steps, empty content returning defaults, multiline action, attachments inside steps, and the absence of implicit fallback parsing without `Scenario:`.

**Task 3 — `TestRunSerializer` rewrite to the current strict multi-line step format:**
- `TestRunSerializer.serialize` produces the multi-line step format: action on numbered line (`N. {action}`), optional `   > {expected}` lines on the next lines (omitted when blank), optional `   — {verdict}` (plain text, no bold) on the next line (omitted entirely when verdict is `NONE`), optional comment lines as `   Comment: {line}` after the verdict line.
- `manual_result: true` is written only when `testRun.manualResult` is true; the line is omitted when false.
- `id:` and `finished_at:` are written only when the corresponding fields are non-null.
- `TestRunSerializer.appendStepResult` is a private `StringBuilder` extension that emits the action line, optional expected lines, optional verdict line, optional action/expected attachments, and optional `Comment:` lines using the current strict format.
- `TestRunSerializerTest` is rewritten to test the current strict format: title in frontmatter (not `test_case`), `manual_result` only when true, `Scenario:` marker, step with expected and verdict on separate lines, verdict line omitted for `NONE`, `Comment:` lines after verdict, and `blocked` verdict serialization. Legacy assertions for `Steps:`, `>>`, or bold verdicts are removed.
- `SpeqaEditorSupport.startTestRun` calls `TestRunSupport.createInitialRun(testCase, startedAt)` without `testCaseFileName`.
- `TestRunSupport.nextRunFileName` signature changes to `nextRunFileName(testCaseFileName: String, now: LocalDateTime, existingNames: Set<String>): String`. It strips the `.tc.md` suffix from `testCaseFileName` to get the stem, formats the timestamp as `yyyy-MM-dd_HH-mm-ss`, and produces `{stem}_{timestamp}.tr.md`. On collision it appends `-2`, `-3`, etc. before the extension. `SpeqaEditorSupport.startTestRun` passes `testCaseFile.name` as `testCaseFileName`. `TestRunSupportTest` adds two tests: `nextRunFileName includes test case stem` verifies `sample-login.tc.md` at `2026-04-11_17-07-08` produces `sample-login_2026-04-11_17-07-08.tr.md`; `nextRunFileName avoids duplicate` verifies a `-2` suffix is appended when the first candidate already exists.
- `TestCaseTest` gains five new `StepVerdict` tests: NONE label, BLOCKED label, fromString("") → NONE, fromString("garbage") → NONE, fromString("blocked") → BLOCKED.
- `TestRunTest`, `TestRunParserTest`, `TestRunSerializerTest` are updated: `testCaseFile` references replaced with `title`, default verdict updated to `NONE`, `StepResult` positional constructor calls updated to use named params `(action=, verdict=, comment=)` since `expected` is now the second field.

**Task 2 — `deriveRunResult` and `currentStepIndex` refactor (removes `completedStepIndexes`):**
- `TestRunSupport.deriveRunResult(stepResults, completedStepIndexes)` is replaced by `deriveRunResult(stepResults: List<StepResult>): RunResult`. It filters out `NONE` and `SKIPPED` verdicts; returns `RunResult.NOT_STARTED` when no meaningful steps remain; returns `FAILED` if any meaningful step is `FAILED`; returns `BLOCKED` if any is `BLOCKED`; otherwise returns `PASSED`.
- `TestRunSupport.defaultCurrentStepIndex(stepResults, completedStepIndexes)` is replaced by `currentStepIndex(stepResults: List<StepResult>): Int`. It returns the index of the first step whose verdict is `NONE`, or `lastIndex.coerceAtLeast(0)` if all steps have been acted on.
- `TestRunSupport.completedStepIndexes` method is removed. Verdict is the single source of truth.
- `TestRunEditor` is updated: `completedStepIndexes` state is removed; calls to `deriveRunResult` and `currentStepIndex` use the new signatures.
- `TestRunSupportTest` is added with 10 unit tests covering all `deriveRunResult` and `currentStepIndex` scenarios.
- `TestRunSupport.kt` replaces the old `deriveRunResult`/`completedStepIndexes`/`defaultCurrentStepIndex` triad with the new `deriveRunResult(List<StepResult>): RunResult` and `currentStepIndex(List<StepResult>): Int` methods.
- `TestRunEditor.kt` removes the `completedStepIndexes` mutable state field entirely; replaces all calls to the old two-arg `deriveRunResult` with the new one-arg version (with `?: initialRun.result` fallback); replaces `defaultCurrentStepIndex(stepResults, completedStepIndexes)` with `currentStepIndex(stepResults)`; removes all `completedStepIndexes = ...` mutation sites; removes the `completedStepIndexes` parameter passed to `TestRunPanel`. The onStepVerdictChange and onStepCommentChange callbacks no longer mutate `completedStepIndexes`; onFinish no longer resets it.
- `TestRunPanel.kt` removes the `completedStepIndexes: Set<Int>` parameter. Progress count is derived inline as `stepResults.count { it.verdict != StepVerdict.NONE }` to replace the old `completedStepIndexes.size` reference on the progress label.

---

## 5. Architecture

### Component Overview

```
+-- File Recognition & Icons -----------------------------+
|  TestCaseFileType       - .tc.md (MarkdownLanguage)     |
|  TestRunFileType        - .tr.md (MarkdownLanguage)     |
|  SpeqaFrontmatterInj.  - LanguageInjectionPerformer     |
|    Suppressor             skips YAML inj. for .tc/.tr   |
|  SpeqaFrontmatterSchema - JSON Schema for frontmatter   |
|    ProviderFactory        overrides Markdown's generic;  |
|                           tags/env accept string+array   |
|  (fix: move Attach file below Expected field)            |
|  SpeqaIcons             - icon loader singleton         |
|  SpeqaIconProvider      - status-colored Project View   |
+---------------------------------------------------------+

+-- Editors ----------------------------------------------+
|  SpeqaEditorProvider    - FileEditorProvider for .tc.md  |
|  SpeqaSplitEditor       - TextEditorWithPreview         |
|  SpeqaPreviewEditor     - Swing panel for split         |
|  SpeqaEditorSupport     - shared parse/write/run logic  |
+---------------------------------------------------------+

+-- Editor UI (Swing) ------------------------------------+
|  TestCasePanel          - interactive test-case panel   |
|  StepsSection           - editable step list (shared)   |
|  StepCard               - step action + expected row    |
|  primitives/*           - theme colors, shared inputs   |
|  MarkdownEditablePane   - inline markdown editor; built  |
|                           on EditorTextField bound to    |
|                           the real opened Project (never |
|                           the default project), so       |
|                           FileStatusManager, recent      |
|                           files, and UndoManager work.   |
|                           Project is threaded from the   |
|                           FileEditor down through        |
|                           TestCasePanel, StepsSection,   |
|                           StepCard, and                  |
|                           EditableBodyBlockSection.      |
+---------------------------------------------------------+

+-- Data Model -------------------------------------------+
|  TestCase               - title, priority, status,      |
|                           environment, tags, attachments,|
|                           links, bodyBlocks, steps       |
|  Link                   - title, url                    |
|  TestCaseBodyBlock      - sealed: DescriptionBlock,     |
|                           PreconditionsBlock             |
|  TestStep               - action, expected,             |
|                           expectedGroupSize             |
|  TestRun                - run results + step verdicts   |
|  SpeqaDefaults          - extensions, default template  |
+---------------------------------------------------------+

+-- Parser / Serializer ----------------------------------+
|  TestCaseParser         - .tc.md -> TestCase            |
|  TestCaseSerializer     - TestCase -> .tc.md            |
|  TestRunParser          - .tr.md -> TestRun             |
|  TestRunSerializer      - TestRun -> .tr.md             |
|  SpeqaMarkdown          - shared YAML/frontmatter utils |
|  DocumentRangeLocator   - char-offset range mapping for |
|                           in-place document patching     |
+---------------------------------------------------------+

**DocumentRangeLocator** — Phase 1 of document patching migration. Provides `DocumentLayout` with character-offset ranges (`TextRange`) for every structural element in a `.tc.md` document: frontmatter fields, description, preconditions, attachments section, links section, steps marker, and per-step sub-ranges (number, action, expected, attachments). This enables surgical text replacements in the editor document without full re-serialization. The locator operates on normalized text (`\n` line endings) and builds a `lineStartOffsets` array for O(1) offset lookup. It is a pure function with no IntelliJ platform dependencies. Comprehensive JUnit 4 tests cover: full documents, frontmatter-only, description without preconditions, preconditions without description, extra blank lines, multiline actions, steps without expected results, document-level and step-level attachments, empty documents, documents without frontmatter, multi-digit step numbers, CRLF normalization, and range non-overlap verification.

**DocumentPatcher** — Phase 2 of document patching migration. Converts a `PatchOperation` into a `List<DocumentEdit>` — minimal character-offset edits that the caller applies in reverse offset order to preserve positions. Uses `DocumentRangeLocator` internally to find target ranges. Each `DocumentEdit` specifies `offset: Int`, `length: Int` (chars to delete), and `replacement: String` (text to insert). The patcher accepts a single `PatchOperation` via `patch(text, operation)` and returns `List<DocumentEdit>`. Fields: `offset`, `length`, `replacement`.

`PatchOperation` is a sealed interface with variants: `SetFrontmatterField` (scalar), `SetFrontmatterList` (YAML list), `SetDescription`, `SetPreconditions` (uses `PreconditionsMarkerStyle`), `SetStepAction`, `SetStepExpected`, `AddStep`, `DeleteStep`, `ReorderSteps`, `SetAttachments`, `SetStepActionAttachments`, `SetStepExpectedAttachments`, `SetLinks`.

Frontmatter field operations (implemented first):
- **SetFrontmatterField** — edit/add/remove scalar fields (id, title, priority, status). When the field exists and value is non-null, replaces `wholeRange` with `key: value\n` (title uses `SpeqaMarkdown.quoteYamlScalar`). When field exists and value is null, deletes `wholeRange`. When field does not exist and value is non-null, inserts before the close delimiter at the canonical position (field order: id, title, priority, status, environment, tags — insertion point is determined by finding which subsequent fields already exist). When field does not exist and value is null, no-op.
- **SetFrontmatterList** — same add/edit/delete logic but formats as YAML list (`key:\n  - "val1"\n  - "val2"\n`). Empty list serializes as `key: []\n`. Null value deletes the field.

Body block operations (implemented in Phase 2):
- **SetDescription(markdown)** — replace/insert/delete the description text block. When the description exists and markdown is non-blank, replaces `descriptionRange` content. When the description exists and markdown is blank, deletes `descriptionRange` plus surrounding blank lines. When no description exists and markdown is non-blank, inserts after the frontmatter close delimiter (before preconditions/steps) with format `\nmarkdown\n\n`. When no description and markdown is blank, no-op.
- **SetPreconditions(markerStyle, markdown)** — replace/insert/delete the preconditions block. When preconditions exist and markdown is non-blank, replaces only `preconditionsBodyRange` (preserves the marker line). When preconditions exist and markdown is blank, deletes both marker and body ranges plus surrounding blank lines. When no preconditions and markdown is non-blank, inserts before steps/attachments with format `\nMarker:\n\nmarkdown\n\n`. When no preconditions and markdown is blank, no-op.

Step operations:
- **SetStepAction(stepIndex, action)** — replaces `steps[stepIndex].actionRange` with new action text. For multiline actions, continuation lines use 3-space indent (matching `N. ` prefix width from `TestCaseSerializer.appendStep`). The replacement text does not include a trailing newline — it replaces only the action content within `actionRange`.
- **SetStepExpected(stepIndex, expected)** — when expected range exists and new value is non-null, replaces `expectedRange` with `   > line1\n   > line2\n` formatted lines. When expected range exists and new value is null, deletes `expectedRange`. When no expected range exists and new value is non-null, inserts formatted expected lines after action (and action attachments if present). When no expected range and null value, no-op.
- **AddStep(step)** — if steps section exists, appends formatted step at end (`\nN. action\n   > expected\n` where N = steps.size + 1). If no steps section, inserts `Steps:\n\nN. action\n   > expected\n` before EOF.
- **DeleteStep(stepIndex)** — deletes `steps[stepIndex].wholeRange` plus any preceding blank line. Renumbers all subsequent steps by finding each step's `numberRange` and replacing the digit text.
- **ReorderSteps(fromIndex, toIndex)** — performs a true move, not a swap. The patcher removes the source step block, inserts it at the destination slot, and renumbers every affected step in the moved span so the markdown order matches the preview model after drag-and-drop.

Step operations (SetStepAction, SetStepExpected, AddStep, DeleteStep, ReorderSteps), body block operations (SetDescription, SetPreconditions), and attachment operations (SetAttachments, SetStepActionAttachments, SetStepExpectedAttachments) are fully implemented. The patcher uses a single-operation `patch(text, PatchOperation)` API. Imports: `io.github.barsia.speqa.model.Attachment`, `PreconditionsMarkerStyle`, `TestStep`. `FIELD_ORDER` constant: id, title, priority, status, environment, tags. All model types use fully qualified imports. The implementation keeps each patch branch mutually exclusive: once a section/range-presence case is handled, later branches assume the opposite state instead of repeating unreachable `null`/empty checks. `SetStepExpected` does not read the original document text because it only rewrites or inserts the expected block based on parsed layout ranges.

**SetLinks(links)** — document-level link patching. When the section exists and links are non-empty, replaces `linksBodyRange` with `[title](url)\n` lines. When the section exists and links are empty, deletes the entire section (marker line + body + surrounding blank lines). When no section exists and links are non-empty, inserts `Links:\n\n[title1](url1)\n[title2](url2)\n\n` before the Steps marker (or at EOF). When no section and empty list, no-op. The insert position is determined by `findLinksInsertOffset` which inserts before the Steps marker. `formatDocumentLinks` formats each link as `[title](url)\n`. `DocumentPatcherLinksTest` covers 5 tests: editing existing links, adding links section to a document without one, removing links section entirely, adding links to a document with attachments and steps, removing links from a document with attachments and steps.

**SetAttachments(attachments)** — document-level attachment patching. When the section exists and attachments are non-empty, replaces `attachmentsBodyRange` with `[path]\n` lines. When the section exists and attachments are empty, deletes the entire section (marker line + body + surrounding blank lines). When no section exists and attachments are non-empty, inserts `Attachments:\n\n[path1]\n[path2]\n\n` before the Steps marker (or at EOF). When no section and empty list, no-op. **SetStepActionAttachments(stepIndex, attachments)** — per-step action attachments. When `actionAttachmentsRange` exists and attachments are non-empty, replaces the range with `   [path]\n` lines (3-space indent). When range exists and empty list, deletes the range. When no range and non-empty, inserts after action text (before expected). When no range and empty, no-op. **SetStepExpectedAttachments(stepIndex, attachments)** — same pattern but for `expectedAttachmentsRange`, inserted after expected lines. `DocumentPatcherAttachmentTest` covers 7 tests: editing document-level attachments, adding attachments section to a document without one, removing attachments section entirely, adding action attachments to a step without them, removing action attachments from a step, adding expected attachments to a step without them, removing expected attachments from a step.

The patcher preserves all whitespace in unchanged parts of the document — edits are purely additive/subtractive at specific offsets. JUnit 4 tests in `DocumentPatcherTest` cover 10 frontmatter operations: editing existing title, editing existing priority, adding priority to a document without it, removing status field, adding tags list, editing existing tags list, removing environment field with continuation lines, adding id field before title, title with special characters (quotes/backslashes), and no-op when removing a non-existent field. The `applyEdits` helper sorts edits by descending offset and applies them via StringBuilder.replace. Tests verify unchanged regions are byte-identical using assertEquals. `DocumentPatcherStepTest` covers step operations: editing step action (single-line and multiline with 3-space indent), adding/editing/removing expected results, adding steps (with and without existing steps section), deleting steps with renumbering, reordering steps with number updates, preserving surrounding blank lines, and multiline expected (multiple > lines). The `renumberStepText` helper uses `Regex.find` + manual replacement to change the leading step number. `DocumentPatcherBodyTest` fully migrated to single-operation API and `PreconditionsMarkerStyle.PRECONDITIONS` enum values. Test `applyEdits` helper uses `DocumentEdit.length` and `DocumentEdit.replacement` field names. Test documents include trailing newlines so actionRange includes the newline. Edge-case tests use explicit string construction. `DocumentPatcherStepTest` migrated to single-operation `patch(text, PatchOperation)` API; removed `listOf` wrapper from all test calls. `buildDeleteStepEdits` and `buildReorderStepsEdits` return edits sorted by descending offset since the patcher no longer sorts globally. `StepsSection` relies on `PatchOperation.ReorderSteps` plus `calculateTargetIndex` for drag reordering; no separate list `swap` helper is part of the active implementation. `TestRunParser` attachment/link regexes keep only the escapes required for literal markdown delimiters; character classes use plain `]` when legal so IDE inspections stay clean while matching the same markdown shapes.

+-- Project Integration ----------------------------------+
|  SpeqaSettings          - PersistentStateComponent      |
|  SpeqaSettingsConfigurable - Settings UI page           |
|  CreateTestCaseAction   - New -> Speqa Test Case        |
+---------------------------------------------------------+

+-- ID Registry ------------------------------------------+
|  IdType                 - TEST_CASE / TEST_RUN enum     |
|  SpeqaIdIndex           - FileBasedIndex: id to files   |
|  SpeqaIds               - index query facade            |
|  IdStateHolder          - shared editor ID state + timer|
+---------------------------------------------------------+

+-- Tag Registry -----------------------------------------+
|  SpeqaTagRegistry       - project-level tag/env scan    |
|                           + VFS sync, lazy init         |
+---------------------------------------------------------+

+-- Validation -------------------------------------------+
|  SpeqaAnnotator         - soft warnings for .tc.md      |
+---------------------------------------------------------+

+-- Test Run ---------------------------------------------+
|  TestRunEditorProvider  - FileEditorProvider for .tr.md  |
|  TestRunSplitEditor     - TextEditorWithPreview         |
|  TestRunEditor          - Swing run panel               |
|  TestRunPanel           - step-by-step execution panel  |
|  TestRunSupport         - run file creation, resolution |
|  RunTestCaseAction      - launch run from .tc.md        |
+---------------------------------------------------------+

+-- Attachment Support -----------------------------------+
|  AttachmentSupport      - VFS utilities for attachments |
|    resolveAttachmentsDir(project, tcFile): String       |
|      -> "<defaultAttachmentsFolder>/<tcName>" (no ext)|
|    resolveFile(project, contextFile, att): VirtualFile? |
|      -> resolves strictly from contextFile.parent.     |
|    copyFileToAttachments(project, tcFile, src):         |
|      Attachment? -> copies src into attachments dir,    |
|      deduplicates name (base_1.ext, base_2.ext, ...),  |
|      returns Attachment with file-relative path         |
|    deleteFile(project, contextFile, att): Boolean       |
|      -> resolves via context file parent, deletes file  |
|    isImage(attachment): Boolean                         |
|      -> true for png/jpg/jpeg/gif/svg/webp/bmp/ico      |
+---------------------------------------------------------+

+-- Link UI ----------------------------------------------+
|  LinkRow               - single link line               |
|    link: Link                                           |
|    onClick: () -> Unit  (open in browser)               |
|    onEdit: (() -> Unit)? = null  (open edit dialog;     |
|      when null, edit button is not rendered)            |
|    onDelete: (() -> Unit)? = null (remove link;         |
|      when null, delete button is not rendered)          |
|    Shows link icon (General.Web, 16dp), title as        |
|    accent-colored text (12sp, truncated ellipsis),      |
|    edit pencil icon (AllIcons.Actions.Edit, 16dp) only  |
|    when onEdit != null; delete GC icon only when        |
|    onDelete != null. Uses hoverable + handOnHover.      |
|  LinkList              - vertical list of links          |
|    links: List<Link>                                    |
|    onLinksChange: (List<Link>) -> Unit                  |
|    Renders LinkRow per link spaced by tightGap,         |
|    followed by add button (link icon).                  |
|    Add button opens AddEditLinkDialog.                  |
|    Edit on LinkRow opens AddEditLinkDialog pre-filled.  |
|  AddEditLinkDialog     - modal dialog for link add/edit |
|    Two fields: Title + URL. DialogWrapper-based.        |
+---------------------------------------------------------+

+-- Attachment UI ----------------------------------------+
|  AttachmentRow          - single attachment line        |
|    attachment: Attachment                               |
|    onClick: () -> Unit  (open in IDE)                   |
|    onDelete: (() -> Unit)? = null (remove/delete dialog;|
|      when null, delete button and its sibling Spacer    |
|      are not rendered — readonly mode)                  |
|    Shows file icon (image vs generic, 16dp), filename   |
|    as accent-colored link text (truncated ellipsis),    |
|    and a red trash icon (AllIcons.Actions.GC, 16dp)     |
|    only on row hover, only when onDelete != null.       |
|    Uses hoverable + clickableWithPtr                    |
|  AttachmentList         - vertical list of attachments  |
|    attachments: List<Attachment>                        |
|    project: Project                                     |
|    tcFile: VirtualFile                                  |
|    onAttachmentsChange: (List<Attachment>) -> Unit      |
|    onOpenFile: (Attachment) -> Unit                     |
|    Renders AttachmentRow per attachment spaced by       |
|    tightGap, followed by QuietActionText "+ Attachment" |
|    Delete shows dialog: "Remove link only" / "Delete    |
|    file" / Cancel via Messages.showDialog. "Delete file"|
|    runs AttachmentSupport.deleteFile in a write action. |
|    File picker uses FileChooserDescriptorFactory        |
|    .createAllButJarContentsDescriptor(); chosen file    |
|    copied via AttachmentSupport.copyFileToAttachments   |
|    inside runWriteAction.                               |
+---------------------------------------------------------+
```

**Attachment live refresh on deletion:** `SpeqaPreviewEditor` subscribes to `VirtualFileManager.VFS_CHANGES` via `BulkFileListener` in its `init` block (connected via `project.messageBus.connect(this)`). When any `VFileDeleteEvent` or `VFileCreateEvent` is detected, the preview re-parses the document and refreshes the attachment rows. This causes each `AttachmentRow` to re-evaluate `isMissing` via `AttachmentSupport.resolveFile`, so deleted files immediately appear as missing (red) without reopening the editor.

**Drag and drop file support:** `SpeqaPreviewEditor` registers a `javax.swing.TransferHandler` on the preview panel in its `init` block. `canImport` accepts `DataFlavor.javaFileListFlavor` only. `importData` reads the file list, resolves each `java.io.File` via `LocalFileSystem`, copies each to the attachments folder via `AttachmentSupport.copyFileToAttachments` inside a `runWriteAction` scheduled with `invokeLater`, appends new `Attachment` entries to `parsed.testCase.attachments`, and writes back via `writeFromPreview` using command name `"Speqa: Add attachments"`. Dropping zero recognized files is a no-op.

**Attachment rename refactoring:** When a `.tc.md` file is renamed, the plugin automatically keeps attachment data consistent. `AttachmentRefactoringListener` (a `BulkFileListener`) is subscribed to `VirtualFileManager.VFS_CHANGES` on the project message bus by `AttachmentRefactoringStartup.execute()`. On each `VFilePropertyChangeEvent` where `propertyName == "name"` and the old name ends with `.tc.md`:
1. If the file stem changed (e.g. `foo.tc.md` → `bar.tc.md`), and an attachments subdirectory `<defaultAttachmentsFolder>/<oldStem>` exists in the same parent directory, it is renamed to `<defaultAttachmentsFolder>/<newStem>` via a VFS `rename` in a `runWriteAction`.
2. All occurrences of the string `<defaultAttachmentsFolder>/<oldStem>/` in the markdown document are replaced with `<defaultAttachmentsFolder>/<newStem>/` using `CommandProcessor.executeCommand` with name `"Speqa: Update attachment paths"`.
If the stem did not change (e.g. only case differs in an OS-insensitive rename that Speqa detects as equal stems) the listener is a no-op. `AttachmentRefactoringStartup` subscribes `AttachmentRefactoringListener` via `project.messageBus.connect().subscribe(VirtualFileManager.VFS_CHANGES, ...)` on project startup.

**Compound extension rename selection:** `.tc.md` and `.tr.md` are compound extensions — IntelliJ treats the last segment (`.md`) as the extension and pre-selects everything before it (e.g. `login-flow.tc`) in the Rename dialog. SpeQA overrides this so only the base name (`login-flow`) is selected:
- `SpeqaRenamePsiFileProcessor` (a `RenamePsiElementProcessor`) intercepts Refactor → Rename for files whose name ends with `.tc.md` or `.tr.md` via `canProcessElement()`. It overrides `createRenameDialog()` to return `SpeqaRenameDialog`.
- `SpeqaRenameDialog` extends `RenameDialog` and overrides `createCenterPanel()`. After `super.createCenterPanel()` initializes the name suggestions field, it calls `preselectExtension(0, stem.length)` where `stem` is the filename without the compound extension (computed by `SpeqaDefaults.speqaStem()`). This ensures the text cursor selects only the base name portion.
- `SpeqaRenameInputValidator` (a `RenameInputValidatorEx`) prevents the user from accidentally removing the compound extension during rename. It matches `PsiFile` elements and validates that the new name still ends with the original `.tc.md` or `.tr.md` extension. The error message uses bundle key `rename.error.extensionChanged`.

The "Expected result" label inside `StepCard` and `TestRunPanel` uses `tightGap` (6dp) vertical spacing to the text field below it, giving more visual breathing room than the default `itemGap` (2dp). Both use bundle key `label.expectedResult`.
- `SpeqaDefaults.speqaExtension(fileName)` returns `"tc.md"` or `"tr.md"` if the filename has a Speqa compound extension, `null` otherwise. `SpeqaDefaults.speqaStem(fileName)` returns the base name without the compound extension. Both are tested in `SpeqaDefaultsTest`. Rename processor and validator are tested in `SpeqaRenameTest` (a `BasePlatformTestCase`).

All user-visible strings in `SpeqaBundle.properties` use **sentence case** (only the first word capitalized) except proper nouns like SpeQA and GitHub. This applies to labels, tooltips, dialog titles, menu items, and button labels.
- All three classes live in `io.github.barsia.speqa.refactoring` and are registered in `plugin.xml` as `renamePsiElementProcessor` and `renameInputValidator`. The utility functions live in `SpeqaDefaults`.

### Data Flow

1. User opens `.tc.md` -> `SpeqaEditorProvider` opens a split editor built on `TextEditorWithPreview`
2. Left side is the native IntelliJ text editor; right side is the Speqa test-case panel
3. `TestCaseParser` reads file -> creates `TestCase` model (with `bodyBlocks` for Description/Preconditions and `steps` for the numbered list)
4. Changes in the right panel update `TestCase` -> `TestCaseSerializer` writes back to the document via `invokeLater` to ensure write-safe EDT context
5. File on disk is the single source of truth; `DocumentListener` triggers re-parse on external changes
6. Run button calls `startTestRun()` which creates a `.tr.md` in the `.runs/` subfolder and opens the Speqa test-run split editor

### Sync Between Modes

- The native editor mode controls come from `TextEditorWithPreview`: `Editor`, `Split`, and `Preview`
- Speqa must not introduce a second bottom-level editor switcher or supplemental editor tab for `.tc.md` / `.tr.md`
- `Editor / Split / Preview` must stay integrated into the normal top editor strip with tabs, like the native Markdown editor
- Speqa split editors must disable only the floating-toolbar presentation of layout controls. They must not replace the tab-integrated controls with a separate standalone toolbar row
- Within Split view: `DocumentListener` on the text editor triggers panel re-parse with 300ms debounce; interactive controls on the right write back to the document immediately

---

## 6. UI: Test Case Panel

The right-side test-case panel is the primary Speqa authoring surface. It must read like a compact TMS record editor, not a settings page and not a note-taking app. The canvas is dense, task-focused, and optimized for writing and reviewing QA steps quickly. The step workspace is the visual center of the panel; the header is compact and secondary.

```
+-- Toolbar -----------------------------------------------+
| [> Run]                                                  |
+----------------------------------------------------------+
| Title: [ Login with valid credentials_______________ ]   |
| Status: [ Draft v ]    Priority: [ High v ]              |
| Environment: [ Chrome 120 ] [ Firefox 121 ] [+ Add]      |
| Tags:        [ auth ] [ smoke ] [+ Add]                  |
|                                                          |
| Context                                                  |
| Description                                              |
| [Free-form markdown body for case description.........] |
| Preconditions                                           |
| [Free-form markdown body for preconditions............] |
|                                                          |
| Steps                                                    |
| 1 | Type "testuser@example.com" into the email field    |
|   | > Email field accepts input, no validation errors    |
|   | [Add step below]                                     |
| 2 | Type "SecureP@ss123" into the password field        |
|   | > Password is masked, no validation errors          |
|   | [Add step below]                                     |
+----------------------------------------------------------+
```

**UX decisions:**
- **Overall composition:** the right-side test-case panel is a compact TMS record editor. The header is intentionally shallow; the step workspace is the part of the canvas that should dominate attention and vertical space
- **Horizontal spacing:** left/right panel padding must stay tight and feel editor-like, not page-like. Default outer panel padding is `16dp`, and header/content share that same width through the full panel height
- **Header structure:** the header is split into three layers. First: a compact utility row with the ID field (intrinsic width, e.g. `TC-5` with pencil/checkmark icon, or red placeholder if unassigned), created date (calendar+ icon), updated date (calendar-refresh icon), and a run action — no generic "Test Case" label. Date icons use custom calendar SVGs with theme-aware tint; full "Created/Updated" label shown on hover via tooltip. Second: the test-case title as the primary line, with a compact pencil edit affordance. Third: an adaptive metadata grid for status, priority, environment, tags, and any future compact frontmatter fields. The title row and metadata grid both start on the same left axis as the utility row content; neither may keep an extra indent from an empty label gutter. The header must not introduce a generic intermediate heading such as `Metadata`; the fields themselves are the structure
- **Date policy:** `Created` uses Git first-commit timestamp when available for tracked files; otherwise it falls back to filesystem creation time, then file timestamp as a final fallback. `Updated` reflects the latest local file timestamp so the header shows current working-tree edits even before a commit
- **Run action:** the header uses the platform play icon, not a text button. Tooltip: short and factual, no product-name phrasing. Clicking opens a confirmation dialog with prefilled destination path and run filename
- **Run dialog:** compact, no verbose descriptions. Destination field is a `TextFieldWithBrowseButton` that shows the relative path from project root to the target directory (defaults to the directory containing the `.tc.md` file) and opens a directory chooser on browse. The filename defaults to the next generated run filename. The user can change both fields. Enter accepts and continues
- **Title row:** the title is displayed as content first, with a compact pencil edit affordance. The pencil icon has a tooltip ("Edit title" in view mode, "Save" in edit mode — same shared "Save" tooltip as the ID checkmark). Activating it makes the text editable in-place — no layout shift, no size change, no jump. The text stays at the same position and size (20sp SemiBold), cursor appears at the end. Long titles wrap to multiple lines in both view and edit modes — no truncation, no horizontal scroll. The pencil/confirm icon is aligned to the top of the first line, not centered vertically — it stays fixed regardless of how many lines the title wraps to. The icon must not float above the text baseline. While editing, the pencil becomes a confirmation affordance. Clicking the confirm icon without making changes exits edit mode (no-op commit). `Enter` or focus loss commits the new title, `Esc` cancels the edit, and committing writes the frontmatter `title`. On successful commit the confirm icon flashes green once as visual feedback
- **Metadata grid:** four fields (Priority, Status, Environment, Tags) must show as 4, 2, or 1 per row — never 3. Implementation: two paired Rows (Priority+Status, Environment+Tags) stacked in a Column. Each Row uses `BoxWithConstraints` to check available width: if wide enough for both cells (2 × minCellWidth + gap), show side by side; otherwise stack vertically. This guarantees even pairs at every width
- **Metadata truncation:** metadata values never wrap to a second line. If a value does not fit in the available width of its column, it truncates with ellipsis
- **Header metadata presentation:** status and priority remain interactive compact selectors. Environment and tags use dense single-line header controls or summaries, never wrapping chip rows inside the header grid
- **Shared canvas width:** the header band and the content workspace must occupy the same visual width. The header must not read as a wider hero card floating above a narrower content area. Their left and right edges, internal padding, and main content axis are shared
- **Alignment rules:** all fields align to an 8dp grid. Header padding is 16dp, gaps between controls are 8dp, the gap between header rows is 12dp, and the gap from the header block to the first content section is 12dp in both the test-case preview and the test-run panel. Left edges of the title, body blocks, and step list align on the same vertical axis
- **Header exit spacing:** The visual gap after the last metadata/header row must match the gap between rows inside the header. SpeQA must not add extra bottom breathing room inside the rounded header surface after `Tags`/`Environment`/`Runner`; otherwise the distance to `Description` becomes larger than the internal header rhythm. The header surface therefore keeps horizontal padding and top padding, but no extra bottom padding after the final row.
- **Control sizing:** status and priority selectors are compact and predictable, with no wide empty surfaces. Metadata values in the header grid must visually share one control system with text inputs and chip rows
- **Header discipline:** the header must feel like a compact control strip, not a secondary panel. Inputs, selectors, and chip rows should share compatible heights and visual weight so the header reads as one system
- **Title prominence:** title is the most prominent field, but it is still a field, not a hero banner. The text should be slightly larger and denser than the rest of the header, with a single-line edit affordance
- **Tab order:** Title -> Status -> Priority -> Environment -> Tags -> Body Blocks -> Steps
- **Body blocks:** `Description` and `Preconditions` remain a secondary context area above the steps. They render as stacked Markdown editors with section labels, not as cards or notebook-style callouts. Inline editing via pencil/checkmark icon (same pattern as title). Auto-commit on focus loss: when the user clicks away from the text area, changes are saved automatically. Text input areas for body blocks have inner content padding so text does not touch the border, and the read-mode text uses the same padding so it does not shift when toggling between view and edit modes.
- **Step workspace:** each step is a full-width row with a fixed number gutter (step number plus drag handle) and a main content column. All content in the main column sits at the same indent (no staircase). Context menus on the drag handle and expected icon use `JBPopupMenu` + `JMenuItem` with a trash icon for the delete actions (right-click on the action field or drag handle for "Delete step"; right-click on the expected field for "Delete expected result"). The main column contains, in order: the action field, action attachments if any, the expected slot (the test icon is always shown to the left of expected content; when expected is absent a quiet "Expected" affordance is shown to add it back), and the ticket + attachments row. Steps and expected results can also be deleted via the keyboard. Both action and expected fields are multiline. The step list occupies the majority of the canvas height.
- **Step reordering:** drag-and-drop only (no arrow buttons). The drag handle is a custom SVG icon (`dragHandle.svg`, two columns of three dots) with a "Drag to reorder" tooltip and pointer cursor on hover. The drop target index is computed from the actual ordered step cards only, never from spacer/wrapper components or the trailing add button. The drag ghost is anchored using the press point converted into the dragged card's coordinate space, so the ghost stays under the pointer from the first drag frame and the drop-threshold / auto-scroll math uses the same geometry the user sees (`DragReorderSupport`, `DragDropIndexMath`). Reorder recognition starts only after a small drag slop; a press-and-release on the handle without crossing that threshold must not reorder and must not move the preview scroll position. While a drag is active, normal preview scrolling is suppressed and re-enabled on drag end.
- **Ghost snapshot clarity:** the Swing drag ghost snapshot is rendered into an offscreen `BufferedImage` using the current desktop font-rendering hints when available (`awt.font.desktophints`) and quality fallbacks otherwise (`KEY_TEXT_ANTIALIASING`, `KEY_FRACTIONALMETRICS`, `KEY_ANTIALIASING`, `KEY_RENDERING`, `KEY_STROKE_CONTROL`). On HiDPI displays, the snapshot buffer uses device-pixel dimensions (`logical size * graphicsConfiguration.defaultTransform scale`) while the ghost component still paints it at the logical card size. The goal is that drag ghosts preserve the same text sharpness as the in-place step card instead of looking soft or blurry during drag.
- **Step field commit:** step action and expected fields auto-save on focus loss (same pattern as Description/Preconditions body blocks). No explicit save button needed — editing is inline and continuous. When the preview writes changes to the document, the document-listener must NOT re-parse the document back into `parsed` state, because the round-trip through Markdown serialization/parsing is lossy for trailing blank lines in step fields. The preview editor uses a `suppressDocumentRefresh` flag: set `true` before `document.setText`, reset after — the `documentListener` skips `refreshTimer.restart()` when suppressed. This prevents the "Enter then newline disappears" bug where `parseSteps` skips blank continuation lines. The suppression is implemented via a private `writeFromPreview` helper method in `SpeqaPreviewEditor` that replaces all `writeTestCaseToDocument` calls within the preview editor class (Assign ID, Update title, Update preview)
- **Step field vertical alignment:** single-line step field text and placeholder sit at the vertical center of the field's minimum height, not pinned to the top.
- **Step deletion affordance:** deleting a step or clearing its expected result is offered through the step's context menu (and keyboard), not via always-visible trash icons. The destructive delete action is tinted with the destructive theme color and carries the `tooltip.deleteStep` / `tooltip.deleteExpected` strings from the bundle.
- **Step deletion policy:** deleting a step always removes the step and its expected result together. If the step has a non-blank expected result, a confirmation dialog is shown before deletion (`dialog.deleteStep.title` / `dialog.deleteStep.message`, via `Messages.showOkCancelDialog`). If expected is null or blank, the step is deleted immediately without confirmation. Deleting only the expected result never requires confirmation. When `expected` is `null`, the expected field and label are hidden and a quiet "+ Expected" affordance is shown to add it back; clearing expected sets `expected = null`, fully removing the field. The presence of the expected field is determined by the `>` blockquote marker in Markdown: when `expected` is not null (even if empty), the serializer writes at least one `>` line so a round-trip preserves the field, and the parser keeps `expected = ""` (not null) when it encounters a `>` line with no text after it. The annotator clamps all `TextRange` values to `[0, text.length)` to avoid `PluginException` when annotation targets fall at the end of the file (the `warn` helper accepts `textLength` and clamps; all callers pass `text.length`). `findStepRange` searches only after the `Steps:` marker, not the full file, so numbered lists in preconditions are not matched as step lines; when step content is empty, it underlines the step number prefix (e.g. `3.`) rather than an invisible newline. The annotator registration uses `language="Markdown"` to avoid duplicate warnings from running on both the Markdown PsiFile and the injected YAML PsiFile in the same document.
- **Step action affordances:** the per-step "+ Attachment" affordance belongs to the expected block, not the action field; it sits on the expected label row, right-aligned to the text field edge. The step-level attachment control is an icon button using the project `/icons/paperclip.svg` icon with a muted tint and a tooltip, not a text button.
- **Step-level links:** Each `TestStep` carries an optional `links: List<Link> = emptyList()` field. In Markdown the links serialize as a single `   Links: url1 [title1], url2 [title2]` indented line (3-space indent, matching `Ticket:`), placed after the `Ticket:` line (or after attachments if no ticket). A link with a blank title is emitted as the bare URL with no bracket suffix. The parser matches `^\s*Links:\s*(.+)$` (case-insensitive). The raw value is split on top-level commas (commas inside `[...]` brackets do not split); each entry is trimmed. If the entry contains `[`, the URL is the substring before the first `[` (trimmed), the title is the substring between the first `[` and the last `]`; if no `[`, the entire entry is treated as URL with an empty title. `currentLinks: List<Link>` accumulates in `parseSteps` and is flushed onto the current step when a new step starts and at end of loop — parallel to `currentTicket`. `PatchOperation.SetStepLinks(stepIndex, links)` and `buildSetStepLinksEdits` follow the same pattern as `SetStepTicket`. `TestStep.equals` and `hashCode` include `links`. `TestCaseSerializerTest` adds `round trip preserves step links` and `round trip preserves step link without title` tests. No UI changes in this model/parser/serializer layer — UI is a separate task.
- **Ticket linking for steps:** Each test step can carry an optional `ticket` field (`String?`) of comma-separated ticket IDs (e.g. `"PROJ-456, PROJ-789"`). It serializes as a `   Ticket: PROJ-456, PROJ-789` indented line after the expected + attachments block, and the parser matches `^\s*Ticket:\s*(.+)$` (case-insensitive). `TestStep.ticket` and `StepResult.ticket` both carry the value, and `TestRunSupport.createInitialRun()` copies it from `TestStep` to `StepResult`. The same `Ticket:` line is handled in the `.tr.md` parser/serializer (after attachments, before the verdict line). `DocumentRangeLocator` tracks the line via `StepLayout.ticketRange`, and `PatchOperation.SetStepTicket(stepIndex, ticket)` does targeted document patching (replace, insert, or remove when null/blank). Round-trips are covered by `TestCaseParserTest` / `TestCaseSerializerTest`.
- **Ticket display and entry:** In the preview, the step shows a ticket row under the expected text: each ID renders as a clickable link (opened via `BrowserUtil.browse`) and an inline edit affordance lets the user enter or change IDs. Input accepts multiple IDs separated by commas, spaces, or semicolons, normalized to comma-separated on save. The ticket URL is built from the configured tracker: `SpeqaSettings` stores the selected tracker (YouTrack, Linear, or Custom) plus a custom URL, and `resolveTicketUrl(id)` maps the tracker to its base URL (custom URL used for Custom) and concatenates the ID. The settings page uses a combo box for the tracker plus a custom URL field shown only when Custom is selected. The ticket row, its links/edit control, and the attachment control participate in keyboard Tab order.
- **Step deletion edit hygiene:** `buildDeleteStepEdits` consumes the blank lines both before and after the step so removing a step does not leave double blank lines between the remaining steps. The step-deletion confirmation message states that the expected result is removed too (`dialog.deleteStep.message`).
- **List auto-continuation in multi-line fields:** All multi-line preview text fields (Action, Expected result, Description, Preconditions, and run comments) support automatic list continuation. When the user presses Enter on a line starting with a markdown list marker (`- `, `* `, or `N. `), the next line automatically receives the continuation marker (same bullet or incremented number). Pressing Enter on an empty marker (e.g. `- ` or `3. ` with no content after) removes the marker and exits list mode. The current Swing implementation is owned by `MarkdownEditablePane`: it installs a key listener on the embedded editor's content component that, on plain Enter (no modifiers), runs the pure `ListContinuation.onEnter(text, caret)` against the editor document and applies the resulting replacement through a single `runWriteAction { document.replaceString(...) }` plus a caret move. The IntelliJ Markdown plugin's own Enter handler does not reach the light virtual document backing `EditorTextField`, so the behaviour must be installed explicitly. The same pure helper is also used by the legacy `MarkdownPane` (read-mostly prose pane) so both surfaces stay in lockstep.
- **Selection formatting toolbar in preview text fields:** Every `MarkdownEditablePane` shows a compact Slack-like floating toolbar only when the user has a non-empty selection inside that field. The toolbar actions are Bold, Italic, Strike, Inline code, Code block, Bullet list, and Numbered list. Underline is intentionally not supported because the `.tc.md` / `.tr.md` source remains Markdown-first and there is no standard Markdown underline marker. Toolbar labels and tooltips come from `SpeqaBundle.properties`; controls use IntelliJ/JB colors and pointer cursor behavior. Toolbar controls are compact square icon buttons using the native Markdown editor icon set where available (`MarkdownIcons.EditorActions` for bold, italic, strike, inline code, bullet list, and numbered list); the code-block fallback icon uses a smaller inline-code glyph inside a small frame so it reads as a block version of inline code and fits cleanly inside a 16x16 glyph box. Native Swing minimum button widths must not make the floating panel read like a full toolbar. The toolbar must prefer a position above the selection, fall back below when there is not enough space above, and never cover the selected text range.
- **Selection formatting storage contract:** Formatting actions edit the inline field's Markdown source, then the existing preview-to-document patch path persists the result to the backing file. Bold wraps the selection with `**`, italic with `_`, strike with `~~`, and inline code with backticks. Code block is block-level: applying it expands the operation to the full selected line range and writes fenced triple backticks on their own lines, so a fenced block never starts or ends in the middle of a paragraph line. Re-applying the same inline action to text already enclosed by that action's delimiters removes those delimiters instead of nesting duplicate Markdown markers; re-applying code block from inside an existing fenced block removes that block's fences. Bullet and numbered list actions apply per selected line, preserving leading indentation and adding `- ` or incrementing `1. `, `2. ` prefixes; applying the same list action to lines already using that list style removes the markers. After applying a toolbar action, the toolbar closes and the caret lands after the formatted range; it must not immediately re-open from the programmatic selection/caret update. Each formatting operation is a single logical local field mutation and relies on the existing preview document patch command for undo/redo.
- **Inline formatting WYSIWYG contract:** `MarkdownEditablePane` hides raw inline formatting delimiters for bold (`**`), italic (`_`), strike (`~~`), inline code (`` ` ``), and fenced code blocks using editor fold regions, and applies visual text attributes to the formatted content. The backing document still stores Markdown markers, but the focused preview field should display styled text, not raw delimiters, after a toolbar action or shortcut. Inline code renders as a compact, non-interactive code token: it keeps the Markdown code-span foreground/background and adds a thin theme border without hover styling. Inline code is literal text, so a URL inside backticks (e.g. `` `https://example.com` ``) must never receive link styling. The embedded editor colors text with the bundled Markdown highlighting lexer, which - unlike the full parser used for the read-only HTML preview - tags a bare URL inside single backticks as a GFM autolink and would otherwise paint it with the hyperlink foreground and underline. The inline-code attributes therefore neutralize that link styling: they reassert the code-span foreground and overdraw the autolink underline in the token's own background color (the code-span background when present, otherwise the editor's default background) so no link color or underline appears inside inline code. This suppression is scoped to SpeQA's `MarkdownEditablePane` only; the native IntelliJ Markdown editor is left untouched. The inline-code border is drawn by a custom renderer rather than a `BOXED` text effect so soft-wrapped inline code does not produce broken nested rectangles at wrap boundaries. When inline code wraps, each visual-line fragment gets a compact border around the actual rendered text fragment only; the border must not stretch to the editor's right edge or make inline code look like a code block. The token needs both a symmetric internal inset between text and border and a tiny external visual gap from adjacent prose, but those two measurements must stay separate. Inline inlays reserve the combined internal+external width, while the renderer expands/fills only the internal token area; the external part remains unpainted so ordinary spaces before/after the inline code stay visually readable. The first visual-line fragment must preserve both the leading external gap and the leading internal inset; wrapped continuation fragments have no adjacent prose before them and use only the internal inset. The leading padding inlay sits at the visible code content start; the trailing padding inlay sits after the folded closing delimiter so punctuation or spaces after the code span are pushed outside the token instead of being painted inside it. The border must not extend into adjacent normal spaces before or after the inline code, because those spaces are the only visible separation after the backtick delimiters are folded away. For code blocks, the opening fence line is folded away; the newline before the closing fence remains part of the visible code body, while the closing fence line and its following newline are folded together when that following newline exists. This keeps the block boundary visible without exposing a misleading empty line that the user can delete and accidentally break the closing fence. When a fenced code block is indented as part of surrounding Markdown structure, the common fence indent is WYSIWYG chrome and must be folded away from every content line that carries it; additional indentation beyond the fence indent is code content and must remain visible. Because common fence indent is invisible chrome in preview, Backspace/Delete at the visible start of a code line must consume the key instead of deleting the hidden indent or hidden opening fence; structural indent must be edited in the text editor, while extra code indentation remains editable in preview. Code-block chrome must provide its own stable compact internal padding from the border, and the horizontal padding must visually match the top inset so the code does not look off-center; rendered padding must never depend on leading Markdown indentation. The code body uses code-block colors and must render as one rectangular block with a shared background and border across wrapped visual lines; it must not use per-text `BOXED` effects that create broken inline rectangles around each wrapped line. Code blocks are block-level containers, but short blocks must not stretch to the editor's right edge: block width is based on the right edge of the widest actually rendered code line plus the trailing padding, capped by the visible editor width. The renderer must use editor layout coordinates rather than standalone `FontMetrics.stringWidth(...)`, because folded indent and padding inlays are part of the rendered geometry. Custom code-block renderer geometry must be stable for a given editor state and must not derive block `x`, `y`, width, or height from the current dirty paint clip; Swing's graphics clip may limit what is drawn, but it must never change where the block belongs. `MarkdownEditablePane` is an auto-height, soft-wrapped inline editor, so its embedded IntelliJ editor must not retain internal horizontal or vertical scroll offsets; editor creation and programmatic text sync normalize the scrolling model to `(0, 0)` before painting. Any WYSIWYG refresh that adds/removes fold regions or custom range renderers must invalidate and repaint the field and nearby preview containers immediately, so editing one field cannot leave stale partially painted neighboring step fields until the user scrolls. Fold-region/highlighter ownership is tied to the currently live embedded `EditorEx`; refresh must never remove fold regions through a different editor's `FoldingModel`, and scheduled refreshes must no-op after the embedded editor is disposed. Restored previews must create `MarkdownEditablePane` fields in a valid initial layout state the first time; repeated post-startup reapplication of the same WYSIWYG folding/highlighter setup is not an acceptable fix for initial bad layout. When the embedded editor's text is replaced programmatically (`setTextSuppressing`), all current fold regions (both the WYSIWYG folds owned by `MarkdownEditablePane` and any platform-managed Markdown folds created by the language's `FoldingBuilder`) along with all active inlays must be removed inside a `runBatchFoldingOperation` before `EditorTextField.setText` is called; stale fold regions and inlays cause `VisualLineFragmentsIterator` and `EditorSizeManager` to crash during the synchronous document change events fired by the full-document `replaceString`. List markers remain visible Markdown structure in this version.
- **Selection formatting shortcuts:** The toolbar is the primary UX. Low-risk shortcuts are registered only on `MarkdownEditablePane` editor components: Cmd/Ctrl+B for Bold, Cmd/Ctrl+I for Italic, and Cmd/Ctrl+Shift+X for Strike. More ambiguous shortcuts for code/list actions are not registered because they collide more easily with IntelliJ platform actions.
- **Step field sizing:** step action and expected fields start at single-line height (`SpeqaLayout.controlHeight`, 28dp) but expand to fit multiline content as the user types or as wrapped text grows. Body block fields (Description, Preconditions) start taller and likewise grow with their content.
- **Add step button:** the "+ Add Step" button appears below the last step, not in the section header. Full text "+ Add Step" (bundle key `form.addStep`). After adding, auto-scroll to the new step and auto-focus its action field
- **Step actions:** row actions are compact glyph-like controls aligned to the far right of the row. They must be materially quieter than the action text and must not read like full buttons or inline links competing with content
- **Expected result styling:** expected text uses the same font size as step action (14sp) but a distinct color (`expectedForeground` from `SpeqaThemeColors`, mapped to `MarkdownHighlighterColors.CODE_BLOCK` with fallback to `BLOCK_QUOTE` — matching the Markdown editor's code block / blockquote text color). It sits under the action line only when present — if a step has no `> ` blockquote, no expected result placeholder is shown
- **Step number styling:** step numbers use the same font size (14sp) and line height (20sp) as step action text so they align on the same baseline. Step number column width is 24dp
- **Environment & Tags:** displayed as a tag cloud - each value is an individual pill/chip laid out in a wrapping cloud (`WrapLayout`). Chips have compact pill styling with subtle fill and a compact remove affordance (x icon, muted by default, bright on hover)
- **Chip coloring:** tag chips have a subtle tinted background color derived deterministically from `tag.hashCode()` mapped to a fixed palette of 8 muted colors. The same tag always gets the same color across restarts — no persistence needed. Environment chips use the neutral `subtleSurface` color (no tinting) to visually distinguish them from tags
- **Chip remove button:** the `x` icon on each chip has a tooltip "Remove {tag}" on hover. On hover the remove icon gets a circular background tint using the action hover color (`SpeqaThemeColors.actionHover`) to indicate interactivity and match standard button affordances
- **Tags empty state:** when no tags exist, a `+` icon is shown instead. Clicking it opens the tag input
- **Tag input with autocomplete:** clicking `+` opens a floating text-field popup anchored just below the `+` button, overlaying the content without pushing it down. As the user types, matching existing tags from across all test cases in the project are suggested in a dropdown below the input. The user can select a suggestion or type a new tag. Enter adds the tag and clears the input for the next tag; Escape closes the input.
- **Environment** follows the same chip cloud + autocomplete pattern as tags
- **Project-wide tag/environment registry (`SpeqaTagRegistry`):** a project-level service (`@Service(Service.Level.PROJECT)`) that maintains two sets — all known tags and all known environments across the project. Initialized lazily on first access by scanning all `.tc.md` files. Kept current via `BulkFileListener` filtered to `.tc.md` files — on file change/create/delete, re-parses the affected file's frontmatter and updates the sets. Editors read `allKnownTags` and `allKnownEnvironments` from this service. The service parses only YAML frontmatter (not full Markdown body) for performance
- **Preconditions block:** stores the original marker spelling (`Preconditions:` or `Pre-conditions:`) and free-form Markdown content
- **Preview rendering of body blocks:** the preview shows block type as section title ("Description", "Preconditions") and only the user-written Markdown content below. The structural marker (`Preconditions:`) must NOT appear in the rendered preview — it is a format detail, not content. If there is only one block of a given type, omit the index suffix (show "Preconditions" not "Preconditions 1")
- **Preview visibility rule:** the preview always shows the full TMS field set: Title, Priority, Status, Environment, Tags, Description, Preconditions, and Steps. If data is missing in frontmatter or Markdown, the preview still renders the field/section with an explicit empty-state value such as `Not set`, `No tags`, `No preconditions`, or `No steps`
- **Tag/environment popup focus restoration:** opening the tag/environment add popup does not reset keyboard traversal context. If the popup closes without committing a value, `Escape` restores focus to the add-button anchor, `Tab` / `Shift+Tab` dismiss the popup and continue traversal from that anchor in the corresponding direction, and app deactivation/reactivation must still leave the anchor as the resume point instead of dropping focus out of the visual editor.
- **Popup-input focus continuity:** making the closed-state add button focusable must not break the popup-input focus path: the tag/environment add popup still auto-focuses its own input field when it opens.
- **Commit flash:** inline-editable fields (title, ID) show a brief background pulse on successful commit (only when the value actually changed), via the shared `CommitFlash` helper (`editor/ui/primitives/CommitFlash.kt`). The effect is gated behind `CommitFlash.enabled` (default off).
- **Visual style:** dense and utilitarian, with a clear hierarchy between header, context, and steps. Prefer spacing, alignment, and surface tint over decorative chrome
- **Action styling:** lightweight controls may use subtle tinted micro-surfaces, but only when they improve scanability. They should never look heavier than the action or expected-result fields they modify
- **Border strategy:** explicit borders only on text inputs and the header container. Step rows, body block containers, and chips use background fill, whitespace, and subtle dividers only where needed to preserve scanability. Editable text fields show a focus ring: a neutral border at rest, the accent border color when focused, and no visible border in read-only mode.
- **Control density:** destructive and reorder actions must be visually quiet. Compact controls are preferred over large outlined text buttons for per-item actions
- **Section labels:** uppercase muted labels, semi-bold with slight letter-spacing, single-line with ellipsis overflow (never wrapping to a second line). Implemented as a shared section-label primitive reused across the preview and run panels. Quiet action labels (e.g. "+ Add Step") use the same uppercase, letter-spaced style. The drag handle icon on each step shows a tooltip on hover with the reorder hint from the bundle.
- **Preview layout:** no card wrappers — title, metadata, body blocks, and steps render directly on surface. All sections use a consistent vertical layout: section label above, value below. No side-by-side label–value pairs for body blocks. Steps are separated by subtle dividers and whitespace. Expected results are visually secondary
- **Spacing:** The preview and test-run panels use a denser top-level rhythm than before: `12dp` between the header block and the next content block, and `12dp` between adjacent top-level content sections. Internal block padding stays `12dp`. Corner radius stays `8dp` for soft content blocks and the header surface.
- **Theme colors:** all from `SpeqaThemeColors`, anchored to the editor color scheme that matches the current UI theme, not blindly to `EditorColorsManager.globalScheme`. Speqa resolves this through a shared typed helper built on the `schemeForCurrentUITheme` accessor, so a dark UI theme does not accidentally render on a light editor canvas due to a stale or mismatched global scheme. No hardcoded colors
- **Toolbar policy:** the embedded Speqa toolbar must not duplicate platform-provided editor mode switching controls or header actions. The run action lives in the header, not in a second standalone toolbar row
- **Attachment image preview:** hovering an `AttachmentRow` filename shows a rich image preview using the shared `RichTooltip` host and `AttachmentPreviewPopover` content when a project and tcFile are available; for missing files it falls back to a plain text tooltip; otherwise no tooltip is shown. SVG attachments are rendered via `ImageLoader.loadFromUrl` (public platform API, not the internal `SVGLoader`). Non-SVG images use `ImageIO.read`. The tooltip host must keep the preview stable on narrow editor widths: once shown, the popover must not enter a hover leave/re-enter loop just because the popup overlaps or sits close to the hovered filename. The position provider must therefore keep the preview inside the visible window area and prefer a placement that does not cover the anchor row when space below is insufficient. Image loading/caching must not be relied on to mask placement instability; even fully cached previews must remain visually steady. Image previews use tight tooltip chrome: the image itself sits edge-to-edge inside the popup without the generic inner content padding that text tooltips use, and the popup border is intentionally visible enough to read as a separate floating surface against both light and dark editor backgrounds. The popup size adapts to the image aspect ratio within the preview cap instead of forcing every image into a fixed-width frame, so narrow assets do not show large empty bands on the left and right. **Popup exclusivity:** `AttachmentList` owns a single `PopupSlot` shared across all its `AttachmentRow` children. When any row shows a popup it cancels the slot's previous popup first, so moving the cursor from one attachment chip to another never produces two simultaneous popups. **Image corner clipping:** the image preview panel paints the `BufferedImage` directly inside a `paintComponent` override using a `Graphics2D` clip set to a `RoundRectangle2D` - no intermediate `JBLabel` child is used. This ensures the rounded clip actually cuts the image pixel corners; a `JBLabel` child approach fails because Swing's `RepaintManager` can supply the child with a fresh rectangular `Graphics` that ignores the parent's clip.
- **Attachment image load + cache:** `AttachmentPreviewPopover` must never decode image bytes on the EDT. Image loading is delegated to the project-scoped `AttachmentImageCache` service, which runs `ImageIO.read` (raster) or `ImageLoader.loadFromUrl` (SVG) and aspect-preserving scaling on `AppExecutorUtil.getAppExecutorService()` and dispatches the result back to the EDT via `SwingUtilities.invokeLater`. The cache is an LRU keyed by `(VirtualFile.url, modificationStamp, targetWidth, targetHeight)` with a fixed entry-count cap, so edits to the underlying file automatically invalidate its cached render on the next hover. Repeated hovers of the same attachment at the same target size return instantly from the cache (synchronous EDT callback). While a load is in flight, the popover shows a muted "Loading preview…" placeholder sized to the same dimensions as the final image so the popup does not resize on arrival; on failure it shows a muted "Preview unavailable" label. A monotonic token per popup instance cancels stale callbacks when the popup is dismissed before the image arrives so closed popups never mutate a disposed component. Only image attachments are cached -- Markdown and text previews remain cheap to reload and are not cached.

---

## 7. UI: Split View

Left panel: standard IntelliJ Markdown editor. Right panel: interactive Swing test-case panel that mirrors the same compact TMS layout defined above.

```
+-- Toolbar -----------------------------------------------+
| [> Run]                                                  |
+-----------------------------+----------------------------+
|  ---                        | +- Preview ---------------+|
|  title: "Login with valid   | | Title: Login with valid  ||
|    credentials"             | | credentials              ||
|  priority: major            | | Status: [Draft v]       ||
|  status: draft              | | Priority: [Major v]     ||
|  environment:               | | Environment: [Chrome]   ||
|    - "Chrome 120, macOS"    | | Tags: [auth] [smoke]    ||
|  tags:                      | |                          ||
|    - auth                   | | Context                  ||
|    - smoke                  | | Description              ||
|                             | | This case verifies...    ||
|  Preconditions:             | | Preconditions            ||
|  1. User account exists     | | Feature flag enabled     ||
|  2. User is on login page   | |                          ||
|                             | | Steps                    ||
|  1. Type "test.." into      | | 1 | Type "test.." into.. ||
|     > Email field accepts   | |   | > Email field acc... ||
|                             | | 2 | Type "pass..." into. ||
|  2. Type "pass..." into     | |   | > Password is masked ||
|     > Password is masked    | |                          ||
+-----------------------------+----------------------------+
```

**Key decisions:**
- **Left panel:** standard IntelliJ text editor with Markdown-like syntax highlighting (via `SpeqaSyntaxHighlighterFactory` delegating to Markdown's lexer). No YAML injection in frontmatter — previous `MarkdownLanguage` binding caused an `AssertionError` in `CompletionInitializationUtil` and frontmatter edits reverting because the injected YAML document could not properly sync with the host document for custom file types
- **Right panel:** Swing test-case panel with a compact metadata header on top and a dense step workspace below. It should not read like a rendered article or a markdown notebook
- **Preview completeness:** unlike a publication preview, the TMS preview is an operational summary. It always shows every major field and section, even when the source file omitted that field, so the user can immediately see what is missing from the case
- **Shared canvas width:** the preview header uses the same horizontal canvas width as the document sections and step workspace below so the right pane reads as one structured surface instead of stacked cards of different widths
- **Interactive selectors:** status and priority remain interactive dropdowns in the preview
- **Inline editing in preview:** Environment, Tags, Description, and Preconditions sections show a pencil icon next to the section label. Clicking the pencil toggles inline editing mode (TextField for Environment/Tags as comma-separated values, TextArea for Description/Preconditions). A save icon (floppy disk, `AllIcons.Actions.MenuSaveall`) confirms and exits edit mode; Escape cancels. The field must not jump or resize when entering/exiting edit mode — the edit input occupies the same space as the read-only value. The pencil/save toggle is a shared reusable composable (`EditToggleIcon`) with tooltip on hover ("Edit" when not editing, "Save" when editing). The pencil icon has a resting alpha of 0.5 (50% opacity) to provide better visibility than pure muted state, and becomes fully opaque (alpha 1.0) when the icon itself is hovered — this keeps the UI clean while making edit affordances more discoverable. The save icon (when in edit mode) is always fully visible. This applies to all edit icons: section fields, metadata cells, title, and ID. Steps are not editable from the preview — they are edited in the form editor or text editor. **Hover background on icon affordances:** all pencil/save icon containers (`EditToggleIcon`, title pencil in `InlineEditableTitleRow`, ID pencil in `InlineEditableIdRow`, tag cloud `+` button) show `SpeqaThemeColors.actionHover` background on hover or focus, matching the standard `SpeqaIconButton` hover behavior. For the title pencil, the flash green tint takes priority over hover background — the background uses a `when` expression: flash green tint when `showFlash`, `actionHover` when hovered or focused, `Color.Transparent` otherwise. **Icon container sizing:** pencil/save icon containers use `size(20.dp)` with `size(12.dp)` icons (4dp padding) for comfortable focus ring spacing — the previous `size(16.dp)` left only 2dp around the icon, making the border feel cramped. The ID edit-mode save icon container also uses `size(20.dp)`. The ID view-mode pencil icon Box and ID edit-mode save icon Box both use `size(20.dp)` with hover background (`actionHover` on hover/focus, flash green tint when flashing). `EditToggleIcon` background: `actionHover` when hovered or focused, `Color.Transparent` otherwise, applied before the border in the modifier chain
- **Bidirectional sync:** edit Markdown -> preview updates; change a control in the preview -> Markdown updates. **Critical invariant:** when an editable field emits a change, it must compare against the current external value before writing back, not a stale captured value. A stale comparison causes spurious `writeFromPreview` calls when the external value changes from text-editor edits, rewriting the entire document (including frontmatter) and reverting the user's in-progress changes.
- **Document formatting preservation:** Preview→document writes must NOT reformat user whitespace. The preview uses `patchFromPreview(updatedTestCase, PatchOperation, commandName)` which calls `DocumentPatcher.patch(document.text, operation)` to get a list of `DocumentEdit` values, then applies them via `document.replaceString()` in descending offset order. This preserves all whitespace in unchanged parts of the document. On patch failure (e.g. malformed document), `patchFromPreview` falls back to full serialization via `document.setText(TestCaseSerializer.serialize(testCase))`. The old `writeFromPreview` method is retained only as this fallback. Each preview callback emits a specific `PatchOperation`: frontmatter field changes use `SetFrontmatterField`, tag/environment changes use `SetFrontmatterList`, description/preconditions use `SetDescription`/`SetPreconditions`, step edits use `SetStepAction`/`SetStepExpected`/`AddStep`/`DeleteStep`/`ReorderSteps`, and attachment changes use `SetAttachments`/`SetStepActionAttachments`/`SetStepExpectedAttachments`. The `TestCasePreview` and `StepsSection` composables accept `onPatch: (TestCase, PatchOperation) -> Unit` instead of `onTestCaseChange: (TestCase) -> Unit`, so each edit site provides both the updated model and the operation needed to patch the document. The drag-drop attachment handler in `SpeqaPreviewEditor.init` copies files inside a `runWriteAction` block, then calls `patchFromPreview` outside it to avoid nested write actions
- **Resilient parsing:** `TestCaseParser.parse()` must never throw. Malformed `---` delimiters fall back to treating entire content as body. When SnakeYAML fails to parse frontmatter, `parseYamlMap` progressively removes the offending line (identified from the SnakeYAML exception's line number, or by brute-force one-by-one removal) and retries SnakeYAML until it succeeds or all lines are exhausted. This preserves every valid field while losing only the truly broken line(s) — no custom regex YAML parser needed. The preview must always remain interactive regardless of parse errors in individual fields
- **Debounce:** Markdown -> Preview sync with ~300ms delay while typing
- **Splitter:** draggable divider, position persisted in settings
- **Theme behavior:** preview surfaces, chips, dividers, and text must use theme-aware colors that match the current IDE editor appearance rather than fixed light-theme values or `Panel.background`
- **Component policy:** preview-side interactive selectors should use native IntelliJ/Swing components where practical, with custom Swing layout primitives only where they produce a more polished editor experience.
- **Container policy:** the preview should avoid stacked generic cards. It should look like a single authoring surface with one clear header band and a dense execution workspace.
- **Alignment rules:** the panel must preserve one consistent title, control, and step gutter system so split mode feels like one editor rather than a stitched-together preview
- **Sticky title trail:** the compact top title trail in `TestCasePreview` behaves like a web-style sticky header. It is overlaid at the top of the preview viewport and appears only after the main header title row has fully scrolled above the visible viewport top edge (i.e., when the title row's bottom edge is above the viewport). It stays hidden while any part of the real title row is still visible. The `FloatingHeaderHost` reads the dynamic anchor y-coordinate (bottom of `headerUtilityRow + titleRow` combined) on every scroll event via an `anchorYProvider` callback, so the threshold automatically adjusts after layout changes. A small hysteresis band (show when `scrollValue > anchorY + showBuffer`, hide when `scrollValue < anchorY - hideBuffer`) prevents the bar from oscillating when scroll-position restores on document edits briefly cross the threshold.
- **Sticky title trail content:** the sticky trail includes the test-case ID before the title when an ID exists, using the same `TC-` prefix as the main header. The trail still falls back to just the title for unassigned cases.
- **Sticky title trail motion:** the sticky title trail does not pop in or disappear abruptly. When its visibility condition changes, it enters and exits with a soft motion: fade plus a small vertical slide over roughly `220ms`. The animation is decorative only; it must not change the underlying visibility rule based on title bounds.
- **Sticky title width contract:** when the sticky title trail is visible, its title text uses the full remaining horizontal space after the progress indicator. It must not be artificially truncated by placeholder spacers while there is still free room on the right.
- **Sticky trail reuse in test runs:** `TestRunPanel` uses the same sticky-trail overlay component and the same visibility / motion rules as `TestCasePreview`, rather than a separate bespoke sticky header implementation.
- **Sticky run trail content:** the run sticky trail shows the run ID before the title when available, using the same `TR-` prefix as the main run header. On the right it shows run progress as completed steps over total steps, where a step counts as completed when its verdict is no longer `NONE`.
- **Run scroll-consumption parity:** the run panel consumes wheel-scroll events at the scroll-viewport boundary the same way the test-case panel does. When the user keeps scrolling after the run panel itself reaches the top or bottom, the scroll gesture must not leak upward into IntelliJ editor tabs or other outer Swing containers.
- **Root surface policy:** the preview pane background must match the active editor canvas color, including empty areas outside controls and content blocks. It must not introduce a custom panel-colored backdrop around native editor content
- **Toolbar policy:** Split mode selection stays in the platform editor controls; the embedded Speqa toolbar must not duplicate it
- **Scroll synchronization:** In split mode, the text editor (left) and visual editor (right) keep their scroll positions synchronized by step anchor. The synchronization is bidirectional.
  - **Step-anchored sync:** `TestStep.sourceLine` (1-based, set by `TestCaseParser`, excluded from `equals`/`hashCode`) records the document line of each step marker. `StepsSection` exposes `stepSourceLine(index)` and `cardAbsoluteY(index)` (Y relative to the `JViewport` content root, resolved via `SwingUtilities.convertPoint`). `ScrollSyncController` holds a `stepsSection` reference wired after panel init. When anchors are available and the layout has been done (`cardAbsoluteY(0) > 0`), sync interpolates between adjacent step card Y positions based on the editor's current logical line (`xyToLogicalPosition`). When the editor reaches its maximum scroll, the preview is clamped to its maximum. Falls back to fraction-based sync when anchors are unavailable (e.g. `TestRunEditor` steps have `sourceLine = 0`).
  - **Editor to preview:** `VisibleAreaListener` fires on each visible-area change. Events are coalesced: only one `invokeLater` is queued at a time; subsequent events while a sync is pending are dropped. The listener does not mirror events that only change due to document reflow (guarded by `EditorMutationScrollGuard`).
  - **Preview to editor:** the preview scroll bar `AdjustmentListener` coalesces events the same way and calls `logicalPositionToXY` + `scrollVertically` on EDT via `invokeLater`.
  - **Feedback prevention:** 220ms time-window suppression after each sync event in each direction.
  - **Toggle:** `ToggleScrollSyncAction` in `EditorTabsEntryPoint` menu (the "..." button on editor tab). Setting stored in `SpeqaSettings.scrollSyncEnabled` (project-level, default `true`). `ScrollSyncController` checks the flag before processing events.
- **Comment icon indicator:** The comment balloon icon always uses `foreground` tint. A 4dp accent dot (offset 1dp inward from top-right corner) is the sole signal for a stored comment
- **Comment field visibility:** Default collapsed on file open, even when a comment exists. The icon click always toggles visibility symmetrically. On expand, the field is focused at end of text. No collapsed/expanded state is persisted — always resets to collapsed on reopen
- **Verdict chip styling:** Selected chips: verdict-colored background (alpha 0.22), verdict-colored text, verdict-colored border (alpha 0.45). Unselected chips: `chipSurface` background, `mutedForeground` text, `divider` border. No font weight change on selection. Per-verdict colors: Passed = `passedIndicator`, Failed = `destructive`, Skipped = `skippedIndicator` (`Component.infoForeground`), Blocked = `accent`. Vertical side bar uses the same full-opacity verdict color
- **Exclusive editor policy:** Both `SpeqaEditorProvider` and `TestRunEditorProvider` use `FileEditorPolicy.HIDE_OTHER_EDITORS` to suppress the Markdown plugin's split editor for `.tc.md` and `.tr.md` files

---

## 8. Test Run UI

Step-by-step test execution with result recording. The test run panel uses the same design language as the test case editor: flat layout with `SectionHeaderWithDivider` for sections, shared `SpeqaThemeColors` and `SpeqaLayout` constants, no bordered section cards.

**Test run is self-contained.** All data needed to display and execute the run is stored in the `.tr.md` file itself — title, steps with action and expected result, environment, runner, verdicts, comments, summary. No reference to the originating test case file. The `test_case` frontmatter field is removed.

**Run file naming:** `{tc-stem}_{YYYY-MM-DD_HH-mm-ss}.tr.md` where `{tc-stem}` is the test case filename without `.tc.md`. Example: test case `sample-login.tc.md` → run `sample-login_2026-04-11_17-07-08.tr.md`. Stored in the designated test runs directory from plugin settings.

```
+-- Header (headerSurface bg, rounded) -------------------+
| TR-1            Created: ...  Started: ...  Finished: ...|
| Login with valid credentials (18sp SemiBold)             |
| Progress: 1/3   [Result: passed]                         |
+----------------------------------------------------------+
|                                                          |
| ENVIRONMENT ─────────  RUNNER ───────────                |
| [Chrome 120] [macOS]   [runner input____________]        |
| [environment input__]                                    |
|                                                          |
| STEP RESULTS ────────                                    |
| 01 | Type "testuser@..." into email field                |
|    | Expected: Email field accepts input                 |
|    | [Passed] [Failed] [Skipped] [Blocked]  💬           |
| ─── divider ───                                          |
| 02 | Type "SecureP@ss" into password                      |
|    | Expected: Password is masked                        |
|    | [Passed] [Failed] [Skipped] [Blocked]  💬           |
| ─── divider ───                                          |
| 03 | Click the "Login" button                            |
|    | Expected: Redirect to dashboard                     |
|    | [Passed] [Failed] [Skipped] [Blocked]  💬           |
|                                                          |
| SUMMARY ─────────────                                    |
| [summary text area_____________________]                 |
|                                                          |
|                                                          |
+----------------------------------------------------------+
```

**Header:**
- No "Test Run" utility text — starts with TR-ID
- Header padding: `contentInset` horizontal/vertical (matches test case header)
- TR-ID left, dates right: Created (from file/git), Started (if `startedAt != null`), Finished (if `finishedAt != null`) — separate bundle keys, not reusing Created/Updated from test case
- Title (18sp SemiBold) — stored in test run, not fetched from test case
- Progress on one row
- Overall result displayed as a `ListComboBox` in header row — user can manually override. `NOT_STARTED` shown as display text but not in dropdown items (only Passed/Failed/Blocked are selectable).

**Step verdict (5 states):**
- Empty (no verdict) — initial state, step not yet processed. Stored as empty string in `.tr.md`
- `passed` — step passed
- `failed` — step failed
- `skipped` — consciously skipped by tester
- `blocked` — step is blocked

**StepVerdict enum:** `NONE`, `PASSED`, `FAILED`, `SKIPPED`, `BLOCKED`. `NONE` is the default (no verdict set). `NONE.label = ""` (empty string in serialization)

**Step result row:**
- Action text (14sp Medium)
- Expected result on separate line (shown only when `step.expected.isNotBlank()`), displayed the same way as in test case preview: `expectedForeground` color from `SpeqaThemeColors`, 14sp fontSize, 20sp lineHeight, no `>` prefix, no "Expected:" label, no extra padding — so the tester sees what to verify with the same visual treatment as the test case itself
- 4 verdict chips in a Row: Passed, Failed, Skipped, Blocked
- Comment button (`SpeqaIconButton` with `AllIcons.General.Balloon`) at the end of the verdict chips row has two behaviors. When `step.comment` is blank, it toggles `showComment` and acts as the add-comment action. When `step.comment.isNotBlank()`, it no longer hides the comment block; instead, if the comment field is not focused, clicking the balloon explicitly requests focus for the field and places the caret at the end of the existing comment. If the caret is already in the comment field, clicking the balloon does nothing.
- Stored-comment indication: when `step.comment.isNotBlank()`, the balloon icon switches to an active accent tint and shows a small accent badge dot. This indicates that the step has a stored comment.
- Comment balloon hover affordance: the balloon always shows a tooltip on hover. The tooltip text has three explicit states: `Add comment` when the step comment is empty and the field is hidden, `Hide comment field` when the step comment is empty and the field is already shown, and `Edit comment` when a stored comment already exists.
- The comment input (placeholder `run.addComment`) is hidden by default and shown when the comment toggle is activated.
- The comment placeholder is vertically centered and left-aligned within the field's minimum height.
- **Test-run accessibility contract:** step-row verdict chips and the comment toggle participate in Tab order as button controls; pointer-only interaction is not acceptable there. When the comment toggle opens the step comment, or when it re-focuses an existing comment, focus moves into the comment field automatically. While the comment field is visible, keyboard traversal stays within the step row in logical order (verdicts -> comment toggle -> comment field -> next step/section) rather than restarting from the top of the run panel.
- **Split-editor state safety:** `SpeqaSplitEditor` and `TestRunSplitEditor` are stateless from the IDE restore perspective. They must not forward persisted editor state restoration into the embedded IntelliJ text editor, because that path currently re-enters folding restoration on EDT and triggers `SlowOperations.assertSlowOperationsAreAllowed`. SpeQA preview editors already treat state as ephemeral (`setState = Unit`), so the split editor itself must also ignore incoming `FileEditorState` instead of delegating to `TextEditorWithPreview.setState`.
- **Split-editor diagnostics cleanup:** temporary `SpeqaDebug` warnings used during EDT restore investigation are not part of the product. Once the investigation step is complete, split editors return to silent `setState = Unit` behavior.

**Run step row contract:** each run step row is built from its index and `StepResult`, and reports verdict changes and comment changes back to the editor (`onVerdictChange`, `onCommentChange`).

**Run header:**
- Date tooltips use bundle keys `run.tooltip.started` (for startedAt) and `run.tooltip.finished` (for finishedAt) instead of `preview.created`/`preview.updated`
- If `result != null`, show a clickable result chip in the progress row. The chip cycles PASSED→FAILED→BLOCKED→PASSED on click and calls `onResultOverride`. Color: verdictPassed/verdictFailed/verdictBlocked from `SpeqaThemeColors`.

**`TestRunPanel` new signature adds:**
- `result: RunResult?` — current run result (null if not yet determined)
- `manualResult: Boolean` — whether result was manually overridden
- `onResultOverride: (RunResult) -> Unit` — called when user clicks result chip to override

**`TestRunEditor` result wiring:** the editor holds `manualResult` and an overridden-result value. The result shown in the header is the overridden value when `manualResult` is true, otherwise the auto-derived value from `TestRunSupport.deriveRunResult(stepResults)`. Clicking the result chip sets `manualResult = true`, updates the overridden value, and saves. `saveToDocument()` persists the overridden result when manual, otherwise the auto-derived (or prior) result, plus the `manualResult` flag.
- The step comment toggle uses the `AllIcons.General.Balloon` icon.

**Overall result auto-calculation:**
- All steps NONE → `NOT_STARTED` (no interactions yet)
- Some steps have verdict, some NONE → `IN_PROGRESS`
- All steps have verdict (ignoring SKIPPED for final calc): at least one `failed` → `FAILED`; at least one `blocked` (no failed) → `BLOCKED`; otherwise → `PASSED`
- `NOT_STARTED` and `IN_PROGRESS` are display-only, not selectable in dropdown
- Dropdown for final results only. NOT_STARTED/IN_PROGRESS = muted text. All-SKIPPED = PASSED. NONE+verdict = IN_PROGRESS

**Manual result override:** The result chip is always visible in the header. When no result is determined yet (all steps NONE/SKIPPED), it shows a muted chip on `chipSurface` background. Clicking cycles through PASSED → FAILED → BLOCKED (from empty state, first click sets PASSED). After manual override, auto-calculation is disabled. Stored as `manual_result: true` flag in frontmatter.

**UI polish rules for test run panel:**
- Expected result in step rows is displayed exactly as in the test-case preview: same expected-result color and line height, no `>` prefix, no "Expected:" label, and no extra left indent or padding.
- Button labels (e.g. `Next`, `Finish`) are single-line and truncate with an ellipsis rather than wrapping; all interactive controls show the hand cursor on hover.
- The step comment field is compact (about 36px minimum height) with a vertically centered placeholder.
- Tags in the run header render as a wrapping cloud of chips reusing the same chip component as the test-case metadata.
- The run header shows a "Result:" muted label plus a result selector. In `.tr.md`, expected text is serialized as `> {text}` lines without an "Expected:" prefix, and the parser accumulates consecutive `>` lines before the verdict as the expected text. There is no `summary` field in the test-run model.

**`completedStepIndexes` removed.** Verdict is the single source of truth for each step. No separate tracking of "completed" steps.

**Self-contained data model (`TestRun`):**
- `id: Int?` — unique run ID
- `title: String` — copied from test case at creation
- `tags: List<String>` — imported from test case when selected at run creation; editable in run
- `priority: Priority?` — copied from test case at creation (readonly in run)
- ~~`status`~~ **removed** — test runs do not have draft/ready/deprecated status; this field was incorrectly copied from the test case model. Removed from `TestRun` data class, serializer, parser, support, panel, and editor.
- `startedAt: LocalDateTime?` — null means not started; set on first interaction
- `finishedAt: LocalDateTime?` — null means not finished; auto-set when all steps have verdict
- `result: RunResult` — auto-calculated or manually set. `NOT_STARTED` when no verdicts set
- `manualResult: Boolean` — whether result was manually overridden
- `environment: List<String>` — same scalar-or-list frontmatter semantics as test cases; editable in run. A scalar environment value always means exactly one list entry, even when it contains commas
- `runner: String`
- `bodyBlocks: List<TestCaseBodyBlock>` — description + preconditions, copied from test case at creation and editable in run
- `links: List<Link>` — imported from test case when selected at run creation; editable in run
- `attachments: List<Attachment>` — imported from test case when selected at run creation; editable in run
- `stepResults: List<StepResult>` — each step with full data
- `comment: String` — overall run comment (editable, shown after steps)
- No `testCaseFile` field, no `summary` field

**`StepResult` data model:**
- `action: String` — copied from test case step at creation
- `expected: String` — copied from test case step expected result at creation
- `tickets: List<String>` — imported from test case step when selected at run creation; editable in run
- `links: List<Link>` — imported from test case step when selected at run creation; editable in run
- `verdict: StepVerdict` — default `NONE`
- `comment: String` — default empty
- `attachments: List<Attachment>` — imported from test case step when selected at run creation; editable in run

**Run parser/serializer parity:** `TestRunParser` and `TestRunSerializer` preserve top-level environment/tags as scalar-or-list YAML fields, keep top-level links and attachments separate, and preserve per-step tickets, links, action attachments, and expected attachments without merging them into a lossy shared list. `TestRunSupport.createInitialRun(...)` imports only the categories enabled by `RunImportOptions`, rebases any imported attachments to the target run directory, and keeps step links intact when link import is enabled.

`calendarFinished.svg`. `%20`. Theme repaint on theme change. Links no extra `\n`. GotoDeclarationHandler debug logging removed.

**TestRunEditor — live metadata state (Task 3):**
- `TestRunEditor` tracks `tags`, `environment`, top-level `links`, top-level `attachments`, `runner`, and `comment` as mutable state derived from the current document, not from the initial import snapshot.
- `refreshTimer` block updates those live fields when the document changes externally, so manual edits in the `.tr.md` file are reflected back into the editor state.
- `saveToDocument` captures the current mutable run state and passes `tags`, `environment`, top-level `links`, top-level `attachments`, `runner`, `comment`, and edited `stepResults` into `TestRun(...)` before serialization. Round-trip writes must preserve user edits made inside the run instead of reverting to the initial imported snapshot.
- `TestRunPanel` call site passes the live mutable values for `priority`, `bodyBlocks`, `links`, `attachments`, `tags`, `environment`, and `comment`, plus callbacks that update local state and then call `saveToDocument()`.

**TestRunPanel — full content display (Task 7):**
- `TestRunPanel` signature includes: `priority: Priority?`, `bodyBlocks: List<TestCaseBodyBlock>`, `links: List<Link>`, `attachments: List<Attachment>`, `comment: String`, `onCommentChange: (String) -> Unit`. The `status` parameter and `Status` import were removed.
- **Header structure:** The header `Column` (with `compactGap` spacing and `headerSurface` background) contains: ID + dates row, Title, one compact operational row with `Progress`, `Overall result`, and the editable `Runner` field on the same line, then a two-column metadata row `Environment | Tags`. The run header keeps its compact overall structure, but `tags` and `environment` are editable collections instead of read-only snapshots.
- **Run title editing:** the test-run title uses the same inline-editing mechanics as the test-case title. `InlineEditableTitleRow` is reused: pencil to enter edit mode, `Enter` to commit, `Escape` to cancel, blur to commit, and focus return to the title affordance after save/cancel.
- **No orphan tag strip in runs:** when a run has no tags yet, the header must not render a divider-only `TagCloud` row with an isolated `+` control. Empty tags use a compact add affordance in the same metadata zone without a standalone horizontal strip.
- **Empty run tags state:** when a run has no tags, the header follows the same `TagCloud` empty state as the test-case preview: no textual placeholder such as `No tags`, only the normal section label, divider, and add affordance.
- **Shared metadata suggestions:** `Environment` and `Tags` in both test cases and test runs use the same `TagCloud` picker behavior and the same suggestion sources. `allKnownEnvironments` and `allKnownTags` come from the shared `SpeqaTagRegistry` snapshot, so a value discoverable in the test-case editor is discoverable in the test-run editor too.
- **Tag/environment popup dismiss focus rule:** when the tag/environment popup closes from keyboard navigation (`Enter`, `Escape`, `Tab`, `Shift+Tab`), focus returns to the `+` anchor as the traversal resume point. When the popup closes because of a mouse click outside, the `+` anchor may remain the logical focus owner but must suppress the visible keyboard-focus ring, matching the way pointer-triggered focus behaves for the shared add affordances elsewhere in the editor. This rule applies equally in test cases and test runs.
- **Runner commit rule:** the inline `Runner` field in test runs commits on `Enter` in addition to the existing live update path. Pressing `Enter` should not leave the edited value in a transient unsaved visual state.
- **Interactive-control focus safety in runs:** background click handling in `TestRunPanel` exists only to absorb clicks on genuinely empty surface. It must never steal or immediately clear focus from interactive descendants such as the tags add button, the environment add button, the runner field, verdict chips, or step metadata actions.
- Inside the header, top-level `Links | Attachments` are laid out side by side with the same structure as `TestCasePreview`: each column uses `SectionHeaderWithDivider` plus a `HeaderAddIconButton` in the header actions, and the list body renders existing items only (`showAddButton = false`). The run keeps full editability (open + add/edit/delete), but the section chrome and placement match the test case.
- **Header pair alignment contract:** the two-column rows `Environment | Tags` and `Links | Attachments` use the same inter-column gap and the same `weight(1f)` cell widths, so the second column starts on the same horizontal grid line in both rows.
- After the header (which already contains the metadata and top-level references), `bodyBlocks` are rendered with the same editable section pattern as the test-case preview: `DescriptionBlock` and `PreconditionsBlock` use `EditableBodyBlockSection`, preserve the same spacing (`blockGap`, `12dp`), and commit directly into the run file.
- **Top-level run references add affordance:** in the run-level `Attachments` and `Links` sections, the empty-state add affordance is icon-only. The section header already names the metadata type, so the body action shows only the plus icon / attachment icon without inline text labels like `Add link` or `Attach file`.
- Shared `AttachmentRow`. `GotoDeclarationHandler` `contains`. Both editors: mutable `ideBackground`, `isOpaque = true`, `repaint()` in LafListener.
- `StepResultRow` uses the same shared visual grammar as `StepCard`: step-number gutter, two-column `action | expected` content area on wide widths, stacked layout on narrow widths, then the step metadata / execution controls below that shared content block. In the run row, `action`, `expected`, and the editable step metadata use the same inline editing primitives as the test-case step where applicable, while verdict/comment controls stay in the run-only footer.
- **Expected-enter navigation parity:** keyboard navigation from the run step follows the same contract as the test-case editor for the shared step content. Pressing `Enter` in `action` moves into `expected`. Pressing `Enter` in `expected` moves to the next step's `action`; for the last step it falls through to the overall run comment field rather than to step metadata actions such as `Add ticket ID`.
- **Run step content must never collapse to gutter-only rows:** on initial run creation, each `StepResultRow` must visibly render its copied `action`, optional `expected`, and verdict/comment controls. The UI may not degrade to rows that show only the step number and divider while the backing `.tr.md` file still contains the step content.
- After the Steps section, an overall run comment section shows a section header (bundle key `run.comment`) plus a multiline comment field (placeholder `run.commentPlaceholder`).
- The run step number is left-aligned consistently with the test-case step card (no extra leading padding).

**Header:** Progress/result layout stays in the current run header, `Runner` is inline on that same operational row, and editable `environment` / `tags` live directly below it as a two-column metadata pair. Top-level `Links` / `Attachments` also stay in the header and visually match the test-case preview. `TestRunPanel` root remains `.background(SpeqaThemeColors.surface)` for theme-switch parity.

**Key decisions:**
- **Where:** Test Run opens as a new editor tab (same as `.tc.md` editor but in run mode). The `.tr.md` file is created immediately and the tab shows the run UI.
- **Launch:** run starts from the header play action in the Speqa test-case panel or from right-click `.tc.md` -> "Run Test Case"
- **Navigation:** removed — no Next button or current step tracking; user can review steps in any order
- **Verdict chips:** Each chip has a subtle verdict-colored background even when unselected, providing a visual hint of its meaning. `VerdictChip` accepts `unselectedBackground: Color` in addition to `selectedBackground`. Passed uses `commitFlash.copy(alpha = 0.04f)`, Failed uses `destructive.copy(alpha = 0.04f)`, Skipped uses `mutedForeground.copy(alpha = 0.04f)`, Blocked uses `accent.copy(alpha = 0.04f)`. When selected, the chip uses the full `selectedBackground` (8% opacity semantic colors: `verdictPassed`, `verdictFailed`, `verdictSkipped`, `verdictBlocked`). Environment chips in the environment section still use the old pattern (`chipSurface` default for `unselectedBackground`).
- **Shared primitives:** the run panel reuses the shared Swing primitives (section headers, surface dividers, utility labels, text inputs) plus `SpeqaLayout` and `SpeqaThemeColors`, so it stays visually consistent with the test-case panel.
- **Persistence:** `.tr.md` file created in designated test runs directory from settings
- **Date tooltips:** "Started" for startedAt, "Finished" for finishedAt — bundle keys `run.tooltip.started` and `run.tooltip.finished`
- **Verdict and comment labels:** "Blocked" verdict chip label uses bundle key `run.blocked`. Comment button label uses bundle key `run.addComment`
- **History:** multiple `.tr.md` files = run history for one test case

---

## 9. Project Wizard

**New Project → SpeQA Test Project** creates a ready-to-use project with sample structure:

```
my-speqa-project/
├── test-cases/
│   └── sample-login.tc.md        # sample test case (id: 1)
└── test-runs/                    # empty, ready for test runs
```

**Key decisions:**
- Wizard icon is a 16x16 version of the plugin logo (`icons/speqa16.svg`) with reduced padding so the Q✓ symbol fills the icon area, and a thin white border around the rounded rectangle. Loaded via `IconLoader` as `SpeqaIcons.PluginIcon`
- Wizard appears in the New Project dialog alongside other project types
- Default folder names: `test-cases/` and `test-runs/`
- Sample test case demonstrates all fields: title, priority (normal), status (draft), environment, tags, preconditions, and steps with expected results
- `test-runs/` directory is created empty — the user creates test runs via the Run action. `SpeqaProjectScaffold.generate()` creates only the directory, no sample `.tr.md` file. The `SAMPLE_TEST_RUN` constant is removed

**Title editing focus:** when the title row enters edit mode, focus moves into its text field reliably; the field's read/edit state is controlled by toggling editability, not by removing it from the focus system, so requesting focus on entering edit mode always succeeds.
- The user can change the project name and location in the standard IntelliJ project creation dialog

---

## 9a. Test Cases Tool Window

A dedicated **SpeQA** tool window provides a curated, test-case-centric view of the project, separate from the platform Project view. It answers "what test cases exist and how are they organized" without the noise of source files, configs, or test runs.

**Placement & availability:**
- Anchored **top-left** (`anchor="left"`, `secondary="false"`), so it docks above the Project view stripe area.
- Title: "SpeQA" (from `SpeqaBundle`). Tool window icon reuses the SpeQA plugin/file stamp icon.
- The tool window is available only when the project contains a `test-cases/` directory; otherwise it is hidden (`ToolWindowFactory.shouldBeAvailable`).

**Tree contents - the contract:**
- **Root** is the project's `test-cases/` directory; the root node itself is not shown - its children are the top level of the tree.
- **Folders are sections.** Every subdirectory under `test-cases/` is shown as a folder node, **including empty folders and folders that (transitively) contain no test cases** (when no filter is active; see "Filters" below). The folder hierarchy mirrors the on-disk directory structure exactly.
- **Test cases are leaves labeled by `title`.** Each `.tc.md` file is a leaf node whose displayed text is the test case's `title` parsed from YAML frontmatter (not the file name). A file with a blank/absent title falls back to `"Untitled Test Case"`. The test-case stamp icon (matching its status) is shown.
- **Non-`.tc.md` files are hidden.** Regular files (`README.md`, attachment images, `.tr.md` test runs, etc.) never appear in this tree.

**Ordering within a node:** folders first (by name), then test cases ordered by `title`, both case-insensitive and natural-sort (so `Step 2` precedes `Step 10`). This ordering plus the `.tc.md`-only / folders-first filtering is a pure function covered by unit tests.

**Interaction:** double-click or Enter on a test-case leaf opens the file via `FileEditorManager` (which routes to the SpeqaEditorProvider split editor). Folder nodes expand/collapse. Standard platform speed-search (type-to-filter) is available because the tree is built on the platform tree framework.

**Filters:** a header above the tree provides a multi-facet filter across four facets: **status**, **priority**, **tags**, and **environment**.

Facets and their values:
- **Status** - single-select enum: `All statuses` (default) plus Draft / Ready / Deprecated, each rendered with its stamp icon (`All statuses` has no icon).
- **Priority** - single-select enum: `All priorities` (default) plus Critical / Major / Normal / Low.
- **Tags** - multi-select over the project's known tags (from `SpeqaTagRegistry.allTags`), picked via the existing tag autocomplete/chip UI.
- **Environment** - multi-select over the project's known environments (`SpeqaTagRegistry.allEnvironments`), same picker UI.

Matching semantics (pure function `matchesFilter(summary, filter)`, unit-tested):
- A facet with no selection does not constrain results (status/priority `null`, or an empty tag/environment set).
- Status and priority match when the test case's value equals the selection.
- Tags match when the test case has **at least one** of the selected tags (OR within the facet); environment matches the same way.
- Active facets are combined with **AND** - a test case is shown only if it satisfies every active facet.

Header UI:
- The four facet triggers - one each for Status, Priority, Tags, and Environment - live in the tool-window **title bar** (next to the tool-window name/gear), installed via `ToolWindow.setTitleActions`. Each is an `AnAction` carrying the facet's icon and a tooltip naming its facet. Clicking a title-bar button opens a small `JBPopup` scoped to that one facet, anchored underneath the clicked button:
  - Status and Priority popups list `All <facet>` plus the facet's values (status values carry their stamp icon); picking a value sets that facet and closes the popup.
  - Tags and Environment popups reuse the editor's `TagCloud` + `AddTagPopup` autocomplete picker (fed by `SpeqaTagRegistry`), letting the user add/remove several values; the popup stays open while picking.
  - A facet whose facet is active renders in the native selected/highlighted state (via `Toggleable`) so the active facets are visible at a glance.
- A "clear all" title-bar action appears only when at least one facet is active and resets every facet.
- Just above the tree, a row of removable chips shows each active selection (status value, priority value, each tag, each environment); each chip carries an always-visible close button that clears that one selection. The chip row is hidden when no filter is active.
- Changes apply live: any facet change rebuilds the tree from the root (`treeModel.invalidateAsync()`), refreshes the chip row, and nudges the title toolbar (`ActivityTracker.inc()`) so the facet active-highlight and clear-all visibility update immediately.

Tree behavior under a filter:
- With no active filter (the default) everything is shown, including empty folders, exactly as described above.
- With any active filter, a test case leaf is shown only if it satisfies `matchesFilter`. A folder is shown only if its subtree (recursively) contains at least one matching test case; folders that would become empty under the filter are hidden.

The filter is session-only state held in memory by the tool window; it is not persisted and resets to "no filter" when the project is reopened. Because the tree builds its children on a background thread while the filter is mutated on the EDT, the filter's tag and environment selections are stored as immutable snapshots swapped atomically on each change, so off-thread reads never observe a partially mutated collection. The leaf-level predicate is covered by unit tests; folder pruning and the popup/chip UI are verified by smoke test. All filter labels come from `SpeqaBundle`.

**Live updates:** the tree reflects file-system changes under `test-cases/` without manual refresh. Creating, deleting, renaming, or moving `.tc.md` files and folders updates the tree; editing a `.tc.md` file's `title` updates that leaf's label. Parsed titles are cached per file and invalidated on content change so the tree does not re-read disk on every repaint.

**Implementation:** built on the IntelliJ platform tree framework (`AbstractTreeStructure` plus `StructureTreeModel` plus `Tree`) - the same machinery as the Project view - for native theming, async loading, and speed-search. Nodes live under a dedicated tool-window package. The factory subscribes to VFS changes scoped to `test-cases/` via the tool window's `Disposable`.

**Out of scope (first version):** context menu (new/delete/rename), drag & drop, and grouping by tags/priority. These may be added later.

---

## 10. Creating Test Cases

**Context menu:** Right-click in Project View -> New -> Speqa Test Case -> dialog with file name -> creates file from default template.
Entered names are normalized to the Speqa test-case extension automatically:
- `name` -> `name.tc.md`
- `name.md` -> `name.md.tc.md`
- `name.tc` -> `name.tc.tc.md`
- `name.tc.md` stays unchanged

**Action/shortcut:** Registered action "Create Speqa Test Case" with assignable shortcut (no default to avoid conflicts).

**Default template** (trailing spaces after `Preconditions:`, `1.`, and `>` for cursor positioning):

```markdown
---
title: "Untitled Test Case"
priority: normal
status: draft
environment: []
tags: []
---

Preconditions: 

Steps:

1. 
   > 

```

---

## 11. Icons

### Plugin icon (`pluginIcon.svg`)
- 40x40 SVG: rounded green background, postage stamp with perforation (slightly rotated), checkmark in top-left corner, circular seal with "SpeQA" banner (JetBrains Mono Bold, converted to SVG path for font-independent rendering) in bottom-right corner

### Project View file icons
All file icons are 16x16 SVG postage stamps with perforation dots along all edges, slightly rotated (-10°) to match the plugin icon style.

**`.tc.md` files — test case stamp icon:**
- Draft — gray (`#6E6E6E`)
- Ready — green (`#3E9B4F`)
- Deprecated — muted red (`#C45A5A`) with diagonal strikethrough

**`.tr.md` files — test run icon:**
- Passed — green checkmark
- Failed — red cross
- Blocked — orange stop

### Editor date icons
Created and updated dates in the test case header use custom calendar SVG icons (`calendarCreated.svg`, `calendarUpdated.svg`) with theme-aware tint color matching the muted text. Full label shown on hover via tooltip.

---

## 12. Validation (SpeqaAnnotator)

Soft warnings only — inline underline on the relevant text range, never block saving. No file-level banners — all warnings must be anchored to a specific text range in the editor.

| Condition | Warning |
|-----------|---------|
| `title` is empty or "Untitled Test Case" | "Test case title is not set" |
| `preconditions` appears in frontmatter | "Preconditions must be defined in markdown body blocks, not frontmatter" |
| `## Preconditions` heading appears | "Use `Preconditions:` or `Pre-conditions:` blocks instead of headings" |
| No step list items are present in the Markdown body | "Test case has no steps" |
| Step has empty action (list item text) | "Step is missing action" — underline from after `N. ` to end of line so the cursor lands at the input position |
| No steps have a blockquote (`>`) for expected result | "Test case has no expected results for any step" — underline from after `> ` to end of blockquote line so the cursor lands at the input position |
| `status: ready` with unfilled required fields | "Test case marked as Ready but has incomplete fields" |

The duplicate-id warning ("Duplicate test case ID: TC-N") carries an `Assign next free ID` quick fix (intention action). Applying it rewrites the `id:` line in frontmatter with the smallest free integer for the file's id type.

---

## 13. Plugin Settings

Location: Settings -> Tools -> Speqa

| Setting | Default | Description |
|---------|---------|-------------|
| Default priority | `normal` | critical / major / normal / low. Test run panel uses the same page padding as the test case preview (start/end = `pagePadding`, top = `compactGap`, bottom = `pagePadding`) |
| Default status | `draft` | Status for new test cases |
| Default environments | `[]` | Pre-configured environment values |
| Attachments folder | `attachments` | Folder name for test case attachments (relative to test case file). Stored as `defaultAttachmentsFolder` in `SpeqaSettings.State` (default `"attachments"`). Blank input resets to the default. Exposed via `var defaultAttachmentsFolder: String` property accessor on `SpeqaSettings`. Rendered in `SpeqaSettingsConfigurable` as a `JTextField(24)` row (`attachmentsFolderField`) with label `settings.defaultAttachmentsFolder` and comment `settings.defaultAttachmentsFolder.comment`. `isModified()` includes `attachmentsFolderField.text != settings.defaultAttachmentsFolder`. `apply()` sets `settings.defaultAttachmentsFolder = attachmentsFolderField.text.trim().ifEmpty { SpeqaSettings.DEFAULT_ATTACHMENTS_FOLDER }`. `reset()` sets `attachmentsFolderField.text = settings.defaultAttachmentsFolder`. |

---

## 14. Error Reporting (Sentry)

**Goal:** Allow users to report plugin exceptions to the SpeQA team via the standard IntelliJ "Report to Plugin Author" dialog. Reports go to Sentry for deduplication and grouping.

**User experience:**
- When an exception occurs in SpeQA code, the IDE error dialog shows a "Report to SpeQA" button
- Before sending, the user sees the stacktrace and can add a comment
- A privacy notice is displayed in the dialog: "Error reports are sent to SpeQA's error tracker. Only exception class names, method names, and line numbers are included. File paths, project data, and personal information are not sent."
- Reporting is always explicit — the user clicks the button each time, no automatic/background reporting

**Stacktrace sanitization:**
- Before sending, strip all absolute file paths from the stacktrace — keep only class name, method name, and line number
- Strip exception messages that may contain file paths or user data — send only the exception class name
- Include: plugin version, IDE version (build number), OS name/version, Java version
- Never include: file paths, project paths, user name, hostname, file contents

**Integration approach — Sentry HTTP API (no SDK):**
- No `sentry-java` dependency — send events directly via Sentry Envelope API (single HTTP POST)
- DSN is embedded in plugin code as a constant (standard practice for client-side error reporting; Sentry uses server-side rate limiting)
- HTTP call runs on IntelliJ's managed thread pool (`AppExecutorUtil.getAppExecutorService()`), fire-and-forget with a reasonable timeout (~5s)
- On network failure — silently ignore, do not show additional errors to the user. Consumer callback receives `NEW_ISSUE` status regardless of send outcome (fire-and-forget)
- User comment is trimmed and limited to 1000 characters before sending

**What to create:**

| File | Purpose |
|------|---------|
| `SpeqaErrorReportSubmitter.kt` | `ErrorReportSubmitter` implementation — sanitizes stacktrace, formats Sentry envelope JSON, sends HTTP POST, shows privacy notice via `getPrivacyNoticeText()` |
| `SpeqaSentryClient.kt` | Thin HTTP client — builds the Sentry envelope payload, sends to Sentry ingest endpoint, handles timeouts/failures silently |
| `SpeqaStacktraceSanitizer.kt` | Strips file paths and sensitive data from `Throwable` stacktrace; returns `SanitizedStacktrace` with `exceptionClass`, empty `exceptionMessage`, and a list of `SanitizedFrame(module, function, lineno)` |

**`SpeqaErrorReportSubmitter` implementation:**
- `class SpeqaErrorReportSubmitter : ErrorReportSubmitter()` — registered as `errorReportSubmitter` extension in `plugin.xml`
- `getReportActionText()` returns `SpeqaBundle.message("error.report.actionText")`
- `getPrivacyNoticeText()` returns `SpeqaBundle.message("error.report.privacyNotice")`
- `submit(events, additionalInfo, parentComponent, consumer)` — extracts the first event's throwable, sanitizes it via `SpeqaStacktraceSanitizer.sanitize(throwable)`, collects plugin version through the public IntelliJ plugin manager API, IDE build string, OS name/version, Java version, then calls `SpeqaSentryClient.send(...)` on a new background `Thread` (not EDT); after sending, calls `consumer.consume(SubmittedReportInfo(SubmittedReportInfo.SubmissionStatus.NEW_ISSUE))`; returns `false` immediately if `events` is empty or the first event has no throwable, `true` otherwise

**`SpeqaStacktraceSanitizer` data model:**
- `SanitizedFrame(module: String, function: String, lineno: Int)` — one stack frame; `module` is the fully-qualified class name, `function` is the method name, `lineno` is the source line number (0 if unavailable)
- `SanitizedStacktrace(exceptionClass: String, exceptionMessage: String, frames: List<SanitizedFrame>)` — the full sanitized result
- `exceptionMessage` is always `""` — exception messages may contain file paths or user data and are never sent
- `module` and `function` are derived from `StackTraceElement.className` and `StackTraceElement.methodName` — they never contain file-system path separators (`/` or `\`)
- Chained causes (`Throwable.cause`) are not recursively included; only the top-level throwable's stack frames are sanitized
- An empty stacktrace (zero elements) is handled gracefully — `frames` is empty, no exception is thrown

**`SpeqaSentryClient` implementation:**
- `object SpeqaSentryClient` — singleton HTTP client for sending error reports to Sentry
- DSN is a private constant pointing to the SpeQA Sentry project (EU region: `o4510727659716608.ingest.de.sentry.io`)
- Uses `java.net.http.HttpClient` (JDK 21) — no Sentry SDK dependency
- `send(...)` is the public entry point; calls `buildEnvelope(...)`, derives the ingest URL from the DSN, and POSTs the envelope; all exceptions are caught silently — reporting failures never reach the user
- `buildEnvelope(...)` is package-visible for unit testing; constructs a 3-line Sentry envelope:
  - Line 1 (envelope header): `{"event_id":"<uuid>","dsn":"<dsn>"}`
  - Line 2 (item header): `{"type":"event","length":<byte-length>,"content_type":"application/json"}`
  - Line 3 (event payload): JSON object with `event_id`, `timestamp`, `level`, `exception.values[0]` (type + stacktrace frames), `tags` (plugin_version, ide_version, os, java_version), and optional `contexts.feedback.message` when `userComment` is non-blank
- Frame objects in the payload contain only `module`, `function`, `lineno` — never `filename` or `abs_path`
- Private `jsonString(value)` helper escapes backslash, double-quote, newline, carriage-return, and tab characters. Covered by unit tests for special character escaping in user comments and frame data
- Private `extractIngestUrl(dsn)` parses the DSN URI and returns `<scheme>://<host>/api/<projectId>/envelope/`
- Implementation file: `src/main/kotlin/io/speqa/speqa/error/SpeqaSentryClient.kt`

**Registration in `plugin.xml`:**
```xml
<extensions defaultExtensionNs="com.intellij">
    <errorReportSubmitter implementation="io.github.barsia.speqa.error.SpeqaErrorReportSubmitter"/>
</extensions>
```

**Sentry project configuration:**
- Create a "SpeQA" project (Kotlin platform) in the existing Sentry account
- Copy the DSN into `SpeqaSentryClient.kt` as a constant
- Configure alert rules in Sentry as needed (new issue, regression, spike)

**Bundle keys (SpeqaBundle.properties):**
- `error.report.privacyNotice` — privacy notice text shown in the report dialog
- `error.report.thankYou` — message shown after successful submission

---

## 15. Markdown Link Navigation in SpeQA Files

SpeQA files (`.tc.md`, `.tr.md`) are ordinary Markdown files again. That means IntelliJ's built-in Markdown plugin owns Cmd+click and segmented hover behavior for standard link/image destinations such as `[text](path)` and `![alt](path)`.

**`SpeqaLinkDestinationReferenceContributor`** — a `PsiReferenceContributor` registered for `MarkdownLanguage` that supplements native Markdown only for SpeQA's bare-bracket attachment syntax, where the path lives in a `MarkdownLinkLabel` PSI host (`[attachments/foo/bar.png]`) instead of a standard `MarkdownLinkDestination`.

**Rules:**
- Applies only in SpeQA files identified by `.tc.md` / `.tr.md` suffix — regular `.md` files continue to use only the built-in Markdown contributors
- Registers references only for bare-bracket attachment labels; standard Markdown link/image destinations remain exclusively native Markdown behavior
- Skips `http://` and `https://` URLs — web links are not resolved to local files
- Resolution order: (1) relative to the containing file's parent directory, (2) relative to the project base path
- Returns segmented `FileReference` instances so Cmd+hover underlines only the currently hovered path segment
- Never exposes local file references from ordinary link labels or image alt text

**Implementation file:** `src/main/kotlin/io/speqa/speqa/editor/SpeqaLinkDestinationReferenceContributor.kt`

**Registration in `plugin.xml`:**
```xml
<psi.referenceContributor language="Markdown"
                          implementation="io.github.barsia.speqa.editor.SpeqaLinkDestinationReferenceContributor"/>
```

---

### Install Test Case Writer Skill

- **Tools > SpeQA > Add Test Case Writer Skill** copies bundled `test-case-writer-skill.md` to `.claude/skills/test-case-writer/SKILL.md` in the current project.
- `SpeqaProjectScaffold.installSkill(baseDir)` copies the bundled skill to `.claude/skills/test-case-writer/SKILL.md` relative to `baseDir`.
- The bundled `test-case-writer` skill treats `Attachments:` sections as user-managed. It must not invent attachments, create files, or add attachment paths on its own. It preserves existing attachment references unless the user explicitly asks to change them, and only adds new attachment references when exact paths are provided.
- If the file already exists, shows a confirmation dialog before overwriting.
- Shows a balloon notification on success or failure.
- Action is only enabled when a project is open.
- The New Project wizard is registered through two extension points so that SpeQA appears in every supported IDE:
  - `newProjectWizard.generator` → `SpeqaNewProjectWizard`. Consumed by IDEA's "New Project" dialog. Chains `NewProjectWizardBaseStep` → `GitNewProjectWizardStep` → `SpeqaAssetsStep`, giving IDEA users the Git-init checkbox (from the platform step) and the Install-Skill checkbox (from `SpeqaAssetsStep`).
  - `directoryProjectGenerator` → `SpeqaProjectGenerator`. Consumed by WebStorm, PyCharm, PhpStorm, RubyMine, and other non-IDEA JetBrains IDEs. Extends `WebProjectTemplate<SpeqaProjectSettings>` and returns a `GeneratorPeerImpl` that renders both the Install-Skill checkbox and the Create-Git-repository checkbox. `buildUI(settingsStep)` registers both checkboxes with `settingsStep.addSettingsComponent(...)` so they appear in the right settings panel of the New Project dialog. `SpeqaProjectGenerator.initializeGitRepository(project, baseDir)` uses the native Git4Idea APIs (plugin dependency `Git4Idea` is required in `plugin.xml`; the plugin is bundled in every JetBrains IDE). Synchronously during `generateProject`: writes a root `.gitignore` matching the layout WebStorm Empty Project ships — IDE block (`.idea`), OS block (`.DS_Store`, `Thumbs.db`), secrets block (`.env`, `.env.*`, `*.pem`, `*.key`, `*.p12`, `*.jks`, `local.properties`), and logs block (`*.log`). The whole `.idea/` is ignored at the repo root, so the user-state `workspace.xml` and IDE's shareable configuration files both stay out of the index — same contract as Empty Project. `.idea/.gitignore` created by the IDE remains in place but is effectively moot once the parent folder is ignored. calls `GitRepositoryInitializer.initRepository(baseDir.toNioPath())` to initialize the repo using the Git path configured in IDE settings; refreshes VFS; registers the project root as a Git mapping in `ProjectLevelVcsManager.directoryMappings` so the IDE recognizes the repo immediately (status bar, Changes view, Git tool window). The `git add` step is deferred to `StartupManager.runAfterOpened` and runs on a pooled thread after the project is fully opened. Staging is done through `Git.getInstance().runCommand(GitLineHandler(project, baseDir, GitCommand.ADD, "-A"))`: a single `git add -A` stages only files not matched by the root `.gitignore` — scaffold files and the root `.gitignore` itself. `.idea/` is excluded because the root `.gitignore` lists it, matching Empty Project behaviour. After staging, `repository.update()` refreshes the in-memory index state and `VcsDirtyScopeManager.markEverythingDirty()` triggers the IDE to re-read file statuses so Project View paints newly-staged files green. Failures during init or add are swallowed so the rest of project creation still completes, and the user can initialize git manually afterwards.
- `SpeqaAssetsStep` shows the skill checkbox (default: checked) aligned with the Git checkbox via `row("")`. Reuses `action.Speqa.InstallSkill.text` bundle key. When checked, calls `SpeqaProjectScaffold.installSkill(baseDir)` during project setup.
- `SpeqaProjectSettings.installSkill: Boolean = true` carries the peer's choice from `SpeqaProjectGeneratorPeer` to `SpeqaProjectGenerator.generateProject`, which calls `SpeqaProjectScaffold.installSkill(baseDir)` only when the checkbox stays selected.

---

## 15a. Swing popup contracts (TagCloud autocomplete + MetadataPicker)

Two popups are implemented natively via `JBPopupFactory`:

- **TagCloud add popup** (`editor/ui/chips/AddTagPopup.kt`): a standalone floating popup anchored to the add button (not inline in the cloud). The popup contains a search field pinned at the top and a scrollable list below. The list shows all known project tags/environments from `SpeqaTagRegistry` minus `currentTags`, filtered by case-insensitive substring of the search text. Already-selected tags are hidden (not de-emphasized) so the user cannot pick a duplicate. When the query is non-blank and no existing pickable tag exactly matches it (case-insensitive), a synthetic `"+ Create '<query>'"` row is prepended to the list. The create-option is not shown when a match exists. The search field is focused on open; `Down`/`Up` move list selection; `Enter` picks the highlighted row (or creates if the create-option is selected); `Esc` closes the popup. Mouse click on a row also picks it and dismisses the popup. The popup is created via `JBPopupFactory.createComponentPopupBuilder(...).setRequestFocus(true).createPopup()` and shown at a `RelativePoint` below the anchor button. `TagCloud.startAdd()` delegates entirely to `AddTagPopup.show()`. The now-obsolete `showInput()` and `dismissInput()` methods are removed from `TagCloud`.
- **Metadata picker popup** (`editor/ui/chips/MetadataPickerPopup.kt`): anchored below a component (chip row / link / ticket input), lists matching `.tc.md` / `.tr.md` files resolved via `SpeqaTagRegistry`-adjacent indexes (or a caller-supplied `List<VirtualFile>`). Rows are rendered with a custom `ListCellRenderer` that shows optional ID text, the file title, and a truncated relative path via the pure `indexedFileMatchFrom` / `IndexedFileMatchDisplay` helpers in `MetadataActions.kt`. Keyboard `Enter` or mouse click invokes `onPick(VirtualFile)` and closes the popup. Empty results render a muted "no matches" label using bundle key `metadata.noMatches`. The current file is marked with `metadata.current` rather than being filtered out.
- **Bundle keys involved:** `tagCloud.addTag`, `tagCloud.searchPlaceholder`, `tagCloud.createNew`, `metadata.noMatches`, `metadata.current`, plus the existing `metadata.popupTitle.*` family for titles when used.
- **Threading:** tag lookup goes through `allKnownTags` on the already-initialized `SpeqaTagRegistry`; the popup must not perform synchronous VFS reads on the EDT.

## 15c. Run-side fine-grained patch operations

`DocumentPatcher` supports run-side `PatchOperation` variants so `TestRunEditor` avoids re-serializing the whole document on every panel change. The Swing `TestRunPanel` emits a per-field operation through an `onPatch: (TestRun, PatchOperation) -> Unit` callback that mirrors the test-case `onPatch` contract. `TestRunEditor.patchFromPreview(run, op, commandName)` applies the edits in a single `runWriteAction` while preserving editor + panel vertical scroll offsets symmetrically with `writeFromPreview`. Full-document `saveToDocument()` remains the fallback for operations without a fine-grained op (e.g. structural changes or initial construction).

Supported run-side variants, all sharing the existing locator/replace-range style in `DocumentPatcher`:

- `SetRunVerdict(verdict: RunResult?)` — frontmatter `result:` scalar. `null` removes the field.
- `SetRunner(name: String)` — frontmatter `runner:` quoted scalar.
- `SetRunTags(tags: List<String>)` — frontmatter `tags:` YAML list.
- `SetRunEnvironment(environment: List<String>)` — frontmatter `environment:` YAML list.
- `SetRunStepVerdict(stepIndex: Int, verdict: StepVerdict)` — rewrites the `- verdict` trailing line inside the step body, appending a new one if absent and stripping it when verdict is `NONE`.
- `SetRunStepComment(stepIndex: Int, comment: String)` — rewrites the `Comment:` block that trails the step body; empty string deletes the block.
- `SetRunLinks(links: List<Link>)` — reuses the test-case `SetLinks` edit path (run files share the same Links section grammar).
- `SetRunAttachments(attachments: List<Attachment>)` — reuses the test-case `SetAttachments` edit path.

Fields skipped: `priority`, `started_at`, `finished_at`, `manual_result`, `title`, and `id` are not currently edited from `TestRunPanel`; the full-document fallback covers them if they ever change. There is no run-side `build` scalar in the model.

## 15d. Tag/environment chip click + context menu (Swing)

`TagChip` exposes optional `onClick`, `onEdit`, and `onDelete` callbacks. Left-click on the chip body invokes `onClick`; the always-visible pencil invokes `onEdit`; Delete/Backspace on a focused chip invokes `onDelete`. The pencil's leading gap matches the trailing gap from the pencil to the visible chip edge, and pencil hover/focus changes the icon tint without painting a button background. The destructive remove affordance is a readable top-right close glyph centered on the chip fill's border with a small inward horizontal inset. Hover state is exclusive across tag chips and is reconciled against the current pointer location, not only against the last component-local event. Entering a chip clears stale hover from the previously hovered chip, and subsequent pointer movement outside the active chip clears it even if Swing did not deliver the expected exit sequence through child components or overlays. This prevents child-component transitions and direct chip-to-chip movement from leaving multiple stale remove affordances visible. A small transparent corner hit area is always reserved so the overlay is clickable without changing layout on hover/focus. `TagCloud` accepts a `MetadataScope` parameter (`TEST_CASES` or `TEST_RUNS`) and, when a `Project` is available, wires chips to:

- **Left-click:** open `showMetadataMatches(...)` with candidates from `SpeqaTagRegistry.findTestCasesByTag` (or `findTestRunsByTag` when scope is `TEST_RUNS`; analogous for environment via `findTestCasesByEnvironment` / `findTestRunsByEnvironment`).
- **Right-click:** a `JPopupMenu` with two entries: "Show test cases with tag X" (`metadata.findTestCasesWithTag`) and "Show test runs with tag X" (`metadata.findTestRunsWithTag`). Both entries open the same `showMetadataMatches` popup, scoped per entry.

`TestCasePanel` passes `MetadataScope.TEST_CASES` for its tag cloud; `TestRunPanel` passes `MetadataScope.TEST_RUNS` for both its tag cloud and its environment cloud. When `project` is `null`, chips have no click/menu wiring and stay purely decorative. Candidate-source kind (`TAG` vs `ENVIRONMENT`) is a second `TagCloud` parameter driving which registry index is queried.

## 15b. Steps drag-reorder live-preview

While a step is being dragged, neighbour cards above/below animate with `translateY = ±(cardHeight + gap)` to open the landing slot. `gap` is the real inter-step spacer used by the container (currently the scaled 6 px vertical strut), not a hardcoded zero, so the opened slot matches the eventual drop position. The dragged source card itself must not continue painting in its original wrapper while the glass-pane ghost is active; otherwise the newly opened slot is visually occluded by the stale in-place card. During drag, the ghost is the only visible representation of the dragged step and the original wrapper remains as layout-only space. The same rule applies during `Esc` cancel return animation: while the ghost flies back to the source slot, the source wrapper stays hidden until the ghost is disposed, preventing a temporary double-image of the same step. The sibling shift is implemented by moving the wrapper component itself relative to its layout-assigned base bounds, not by translating child painting inside the wrapper's own clip rectangle; otherwise large upward/downward shifts clip the action/expected area and only partial fragments of the card remain visible near the source slot. Implemented by `LivePreviewReorderDecorator` (in `editor/ui/steps/LivePreviewReorderDecorator.kt`) wrapping each card in a translating wrapper panel and smoothing offsets via a 60 fps `javax.swing.Timer` (16 ms). Per-frame easing is a critically-damped lerp: `current += (target - current) * 0.25`, snapping to zero when within `0.5 px` of a zero target. The pure per-sibling offset decision lives in `livePreviewTargetOffset` and is covered by `LivePreviewReorderMathTest`. The drop-indicator line in `StepsSection.paintChildren` is suppressed while `livePreview.isActive()` is true — the opening slot IS the indicator. The ghost + auto-scroll + drop math remain unchanged; live-preview is purely visual and additive. Disable via `StepsSection.setLivePreviewEnabled(false)` — the ghost + drop-indicator path remains the baseline when disabled. On drop or `Esc`-cancel, sibling offsets animate back to `0` immediately; on cancel, the dragged source wrapper becomes visible only after the ghost return animation completes. The timer stops when every entry is at rest.

## 15e. Swing panel layout

The Swing `TestCasePanel` renders both `.tc.md` and `.tr.md` content in a single vertical `BoxLayout.Y_AXIS` viewport with page padding of 12 px. The panel takes a `PanelMode` constructor argument (`CASE` or `RUN`) which selects between test-case-frontmatter wiring and test-run-frontmatter wiring while keeping a single renderer. `TestRunPanel` is being retired; the run editor mounts `TestCasePanel(mode = PanelMode.RUN)` and passes `onRunChange` / `onRunPatch` callbacks. The layout is shared between the case and run surfaces (with run-specific substitutions noted below).

**Run-mode constructor wiring.** In `PanelMode.RUN`, the panel ignores the `onChange: (TestCase) -> Unit` and `onPatch: ((TestCase, PatchOperation) -> Unit)?` callbacks (caller passes no-op `{ _ -> }`) and instead emits through `onRunChange: (TestRun) -> Unit` and `onRunPatch: ((TestRun, PatchOperation) -> Unit)?`. The id row uses `IdType.TEST_RUN`; the run-result combo replaces the case-mode status combo at the same grid slot (`Result` caption vs. `Status` caption); a Runner text field and a Progress label are added as a two-column row between the title divider and the priority/result row; the header utility row carries `TR-`-prefixed id plus Started / Finished dates and no trailing button (the Run button only exists in case mode). Steps render with `runMode = true` via `StepsSection`, which constructs each `StepCard` in `StepMode.RUN`: the drag handle is fully functional - right-click opens a context menu with Move Up / Move Down / Duplicate / Delete actions, and drag-reorder is attached via `DragReorderSupport` exactly as in case mode. There is no "Add step" button. The `StepMetaRow` hides per-item edit/delete plus the Add ticket / Add link / Attach file buttons (via `readOnly = true` threaded through `TicketChip`, `LinkList`/`LinkRow`, `AttachmentList`/`AttachmentRow`). Structural step mutations in run mode emit `PatchOperation.ReorderSteps` / `DeleteStep` / `AddStep` through `onStepPatch`, which is wired in `TestCasePanel` to `onRunPatch` when `mode == PanelMode.RUN`. Each run step gets a 4-toggle `StepVerdictRow` (Passed / Failed / Skipped / Blocked) and a single-line comment field beneath the meta row; clicking the pressed verdict button clears the verdict to `NONE`. Description and Preconditions emit `PatchOperation.SetDescription` / `SetPreconditions` in both modes because `.tc.md` and `.tr.md` share the same body-block layout in `DocumentPatcher`. Run-side id edits go through `onRunChange` (no patch op) because `DocumentPatcher` exposes no `SetRunId`. The old `TestRunPanel` class is deleted; `TestRunEditor` mounts `TestCasePanel(mode = PanelMode.RUN)` directly.

**Top-down order inside the scroll viewport:**

1. **Header utility row.** A single horizontal line composed of (left→right): `InlineEditableIdRow` (`TC-<id>` / `TR-<id>`), a small calendar-created label (`icons/calendarCreated.svg` + formatted timestamp), a small calendar-updated (for cases) / calendar-finished (for runs) label, a horizontal glue pushing the trailing control to the right, and a trailing action button — `Run` (`AllIcons.Actions.Execute`) on test cases, the overall verdict combo on test runs.
2. **Title row.** Large bold title with a trailing pencil edit icon. Read-mode: `JBLabel`. Edit-mode: `JBTextField`. Toggling happens on pencil click or Enter/Space; Enter commits, Esc cancels, focus-loss commits.
3. **Surface divider.** 1 px separator via `surfaceDivider()`.
4. **Priority / Status** two-column row (test cases only; test runs skip this row). Each column header shows a `SectionCaption` (uppercase, `Label.disabledForeground`, `font.size - 1`); the body is a `ComboBox<Priority>` / `ComboBox<Status>` with a capitalizing cell renderer. The preview status dropdown order is fixed to `Ready`, `Draft`, `Deprecated` regardless of enum declaration order.
5. **Environment / Tags** two-column row. Each column header has a caption plus a trailing small `+` icon button (`HeaderAddIconButton`, icon `AllIcons.General.Add`). The body is a `TagCloud` configured for `MetadataKind.ENVIRONMENT` (left) and `MetadataKind.TAG` (right). The `+` button triggers `TagCloud.startAdd()`; the cloud's own internal add-button is suppressed via `hideAddButton = true`.
6. **Links / Attachments** two-column row. Header captions plus `HeaderAddIconButton` on the right of each column. Body is a `LinkList` / `AttachmentList` with `hideAddButton = true`; the header button calls the list's public `startAdd()` which opens `AddEditLinkDialog` / `FileChooser` respectively.
7. **Description.** `SectionCaption` then `EditableBodyBlockSection` for cases, `MarkdownReadOnlyPane` for runs.
8. **Preconditions.** Same pattern as Description.
9. **Scenario.** `SectionCaption` then the `StepsSection` (cases) or vertical stack of `StepResultCard`s (runs). Each case-side step card lays out as `[nn] [action area | expected area]` with a meta-row of `[ticket.svg Add ticket…] [chainLink.svg Add link] [paperclip.svg Attach file]`.

**Caption style.** `SectionCaption` returns a `JBLabel` with text uppercased, `foreground = Label.disabledForeground`, and `font = Label.font.deriveFont(size - 1f)`. Callers never uppercase manually.

**Two-column grid contract.** `twoColumnRow(leftCaption, rightCaption, leftBody, rightBody, leftHeaderAction?, rightHeaderAction?)` returns a `JPanel(GridBagLayout)` with two columns of equal weight. Row 0 holds caption + optional header action per column; row 1 holds the body, spanning the column width. Inter-column gap: `JBUI.scale(16)`. Caption-to-body gap: `JBUI.scale(4)`.

**Header `+` buttons vs. widget-internal `+` buttons.** When a `TagCloud` / `LinkList` / `AttachmentList` is used inside a two-column section with a header `+` button, the widget is constructed with `hideAddButton = true` and the header button drives the widget's `startAdd()` public API. The inline-input / dialog / chooser flow is unchanged — only the button location moves.

**Metadata chip colours in Swing previews.** Tags and environments use the same neutral theme-aware chip fill (`ActionButton.hoverBackground`) rather than per-value palette colours. The fill should differ naturally between light and dark themes through IntelliJ UI defaults, but values must not be tinted by tag name. Empty states such as "No tags" / "No environments" use disabled foreground text and no chip fill.

**Drag-handle hover visibility.** `StepCard.dragHandle` starts hidden (`isVisible = false`). It becomes visible whenever the mouse pointer is anywhere inside the card hierarchy (tracked via a nested `MouseListener` counter attached to all descendants) or any descendant has keyboard focus (tracked via a recursive `FocusListener`). On mouse-exit with no focused child, it hides again. The drag affordance is rendered only on hover/focus.

**Step meta-row icons.** The three add-action buttons inside `StepMetaRow` are prefixed with 16 px SVG icons loaded from `src/main/resources/icons/`: `ticket.svg` (Add ticket), `chainLink.svg` (Add link), `paperclip.svg` (Attach file). The icon and label share a single `FlowLayout` row with a `JBUI.scale(4)` gap between them.

**Deleted surfaces.** `SpeqaEditorToolbar` and `ReferencesStrip` are removed: the Run button lives in the header utility row, and the Links/Attachments sections are always-visible columns inside the two-column grid rather than collapsible counters.

---

## 16. Out of Scope (Future)

- Integration with external TMS (TestRail, Zephyr, qase.io)
- Test suites / test plans (grouping multiple cases into a plan)
- Custom templates
- Reporting / dashboards
- Bulk operations (mass status change, bulk run)
- Export (HTML, PDF, CSV)
- Collaboration features
- CI/CD integration
