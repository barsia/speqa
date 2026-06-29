# Preview Link Management Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let users view, open, create, and edit Markdown links from the SpeQA preview without touching the source editor.

**Architecture:** Design in `docs/plans/2026-06-29-preview-link-management.md`. Pure logic (markdown apply, click routing, URL validation) lives in small testable units; the UI (tooltip popup, toolbar button, icon click, modal dialog) is wired in `MarkdownEditablePane`. Link edits go through the existing preview-to-document patch path, like the other toolbar formatting actions.

**Tech Stack:** Kotlin, IntelliJ Platform (EditorEx, inlays/folds, `JBPopup`, `DialogWrapper`, `BrowserUtil`, `IconUtil`), Kotlin UI DSL v2, JUnit 4 + `BasePlatformTestCase`. All user-visible strings via `SpeqaBundle.properties`. No em dashes in `.md`.

## File structure

- Create `editor/ui/primitives/LinkMarkdown.kt` - pure: wrap a selection as `[text](url)`, replace an existing link span, locate the link span at an offset.
- Modify `editor/ui/primitives/MarkdownWysiwygRanges.kt` - add `linkTargetAt(text, offset)` (Text / Icon / None) over the existing `inlineLinks` / `linkUrlAt` / `linkUrlAtIconOffset`.
- Create `editor/ui/LinkDialog.kt` - modal `DialogWrapper` with Text + URL fields and URL validation; returns `(text, url)`.
- Modify `editor/ui/primitives/MarkdownEditablePane.kt` - toolbar "Link" button, the click tooltip popup, click routing (icon -> open, text -> popup), icon recolor.
- Modify `messages/SpeqaBundle.properties` - button tooltip, dialog title/labels, validation message.
- Modify `docs/specs/2026-04-06-speqa-design.md` and `site/docs/content/pages/` - contract + user docs.

## Parallelization

Tasks 1, 2, 3 touch disjoint files and are independent - run them as parallel subagents. Tasks 4, 5, 6 all modify `MarkdownEditablePane.kt`, so run them **sequentially** (one subagent at a time) on top of 1-3 to avoid conflicts. Task 7 (docs) runs last.

---

### Task 1: Pure link-markdown apply

**Files:** Create `src/main/kotlin/io/github/barsia/speqa/editor/ui/primitives/LinkMarkdown.kt`; Test `src/test/kotlin/io/github/barsia/speqa/editor/ui/primitives/LinkMarkdownTest.kt`.

Mirror `MarkdownSelectionFormatter`'s shape (study it first). Provide:
- `data class Result(val text: String, val selectionStart: Int, val selectionEnd: Int)`
- `fun linkSpanAt(text: CharSequence, offset: Int): IntRange?` - the full `[...](...)` span containing `offset`, else null (reuse the `inlineLink` regex from `MarkdownWysiwygRanges` or duplicate it locally; keep `http(s)` only).
- `fun applyLink(text, selStart, selEnd, linkText, url): Result` - if the selection sits inside an existing link span, replace that whole span with `[linkText](url)`; otherwise replace `[selStart, selEnd)` with `[linkText](url)`. Caret/selection land on the new visible `linkText`.

- [ ] **Step 1: Write failing tests** in `LinkMarkdownTest.kt`:
  - wraps a plain selection: `applyLink("see x", 4, 5, "x", "https://e.com")` -> text `see [x](https://e.com)`, selection over `x`.
  - replaces an existing link: text `a [old](https://o) b`, offset inside it, `applyLink(..., "new", "https://n")` -> `a [new](https://n) b`.
  - `linkSpanAt` returns the span for an offset in the text/url, null outside; null for a non-http target.
- [ ] **Step 2:** Run `./gradlew test --tests "*LinkMarkdownTest"` -> FAIL (unresolved `LinkMarkdown`).
- [ ] **Step 3:** Implement `LinkMarkdown` minimally to pass.
- [ ] **Step 4:** Run the test -> PASS.
- [ ] **Step 5:** Commit `feat: pure link-markdown apply (LinkMarkdown)`.

---

### Task 2: Click offset to link target

**Files:** Modify `MarkdownWysiwygRanges.kt`; Test `MarkdownWysiwygRangesTest.kt`.

Add `sealed interface LinkTarget { data class OpenUrl(val url: String); data class EditText(val url: String); object None }` and `fun linkTargetAt(text, offset): LinkTarget`:
- if `linkUrlAtIconOffset(text, offset) != null` -> `OpenUrl` (the icon),
- else if `linkUrlAt(text, offset) != null` -> `EditText` (inside the link text),
- else `None`.

- [ ] **Step 1:** Failing tests: icon offset (`closeEnd`) -> OpenUrl; an offset inside the link text -> EditText; outside -> None.
- [ ] **Step 2:** Run `./gradlew test --tests "*MarkdownWysiwygRangesTest"` -> FAIL.
- [ ] **Step 3:** Implement `linkTargetAt` reusing the existing helpers.
- [ ] **Step 4:** Run -> PASS.
- [ ] **Step 5:** Commit `feat: classify a click offset as link icon, text, or none`.

---

### Task 3: Modal Link dialog

**Files:** Create `src/main/kotlin/io/github/barsia/speqa/editor/ui/LinkDialog.kt`; Modify `SpeqaBundle.properties`; Test `LinkDialogTest.kt` (pure validation only).

A `DialogWrapper` built with Kotlin UI DSL v2: a "Text" field and a "URL" field, OK disabled until the URL is a valid `http(s)://...`. Expose the result as `data class LinkInput(val text: String, val url: String)` and a static `fun edit(project, initialText, initialUrl): LinkInput?` (null on cancel). Extract URL validation to a pure `fun isValidLinkUrl(s: String): Boolean` so it is unit-testable.

Bundle keys: `dialog.link.title`, `dialog.link.text`, `dialog.link.url`, `dialog.link.invalidUrl`, `toolbar.link.tooltip`.

- [ ] **Step 1:** Failing test for `isValidLinkUrl`: accepts `https://e.com`, rejects empty / `ftp://...` / `e.com`.
- [ ] **Step 2:** Run -> FAIL.
- [ ] **Step 3:** Implement `LinkDialog` + `isValidLinkUrl` + bundle keys (use the project's Kotlin UI DSL v2 and `DialogWrapper` patterns; see idea-plugin-dev).
- [ ] **Step 4:** Run -> PASS; `./gradlew compileKotlin` green.
- [ ] **Step 5:** Commit `feat: shared modal Link dialog (text + url)`.

---

### Task 4: Toolbar "Link" button (after Code block, before lists)

**Files:** Modify `MarkdownEditablePane.kt` (the floating formatting toolbar) + the formatting action enum/handler; Modify `SpeqaBundle.properties` if not already added.

Add a "Link" action to the existing toolbar between "Code block" and "Bullet list". On click, if there is a non-empty selection: open `LinkDialog.edit(project, selectionText, "")`; on a result, compute `LinkMarkdown.applyLink(...)` and write it through the SAME path the other toolbar actions use (the `WriteCommandAction` + document replace already used by `applyMarkdownFormatting`). Reuse `applyMarkdownFormatting`'s write/caret/toolbar-close mechanics; do not invent a new persistence path. Icon: `AllIcons.Ide.Link` (or a Markdown link glyph if one exists); tooltip from `toolbar.link.tooltip`. Hand cursor via the toolbar's existing button helper.

- [ ] **Step 1:** Study `applyMarkdownFormatting` and the toolbar button construction.
- [ ] **Step 2:** Add the Link button + handler. With no selection the button is disabled (match how the toolbar already gates).
- [ ] **Step 3:** `./gradlew compileKotlin test` green.
- [ ] **Step 4:** Commit `feat: toolbar Link button creates a link from the selection`.

---

### Task 5: Link tooltip popup (non-modal)

**Files:** Modify `MarkdownEditablePane.kt`.

A `showLinkPopup(editor, range)` that opens a **non-modal** `JBPopup` anchored above the link (below if no room), persistent (does not close on mouse move), closing on click-outside or Escape. Content (a small panel): the link **text** (label), the **URL** as a clickable label (hand cursor; click -> `BrowserUtil.browse(url)` then close), and an **Edit** button. Edit opens `LinkDialog.edit(project, currentText, currentUrl)`; on a result, apply via `LinkMarkdown.applyLink` replacing the existing span (same write path as Task 4), then close the popup. Strings via the bundle.

- [ ] **Step 1:** Implement `showLinkPopup` (use `JBPopupFactory.createComponentPopupBuilder`, `setRequestFocus(false)` for non-modal, `setCancelOnClickOutside(true)`, `setCancelKeyEnabled(true)`; anchor with `show(RelativePoint...)`). Reuse the existing inline-text-fragment geometry to place it above the link.
- [ ] **Step 2:** `./gradlew compileKotlin test` green.
- [ ] **Step 3:** Commit `feat: link tooltip popup with clickable URL and Edit`.

---

### Task 6: Click routing + icon recolor

**Files:** Modify `MarkdownEditablePane.kt` (`installLinkFollowing`, `OpenLinkIconRenderer`).

Rewire the existing `installLinkFollowing` mouse handling through `MarkdownWysiwygRanges.linkTargetAt`:
- plain left-click, target `OpenUrl` (icon) -> `BrowserUtil.browse`, consume;
- plain left-click, target `EditText` (link text) -> `showLinkPopup`, consume (do not place the caret);
- Ctrl/Cmd+click on link text -> `BrowserUtil.browse` (existing), consume;
- hand cursor over icon or modifier-hover over link text (existing).

Recolor the icon: in `OpenLinkIconRenderer.paint`, draw `IconUtil.colorize(External_link_arrow, linkColor)` where `linkColor` is the link foreground used by `linkAttributes()` (pass it into the renderer).

- [ ] **Step 1:** Update `installLinkFollowing` to branch on `linkTargetAt`; keep Ctrl/Cmd+click.
- [ ] **Step 2:** Pass the link color into `OpenLinkIconRenderer` and colorize.
- [ ] **Step 3:** `./gradlew compileKotlin test` green.
- [ ] **Step 4:** Commit `feat: route link clicks (icon opens, text shows popup) and tint the icon to the link color`.

---

### Task 7: Spec and user docs

**Files:** Modify `docs/specs/2026-04-06-speqa-design.md` (inline-link WYSIWYG contract ~line 819) and `site/docs/content/pages/` (a links section).

Document: click text -> non-modal tooltip (text, clickable URL, Edit); click icon (tinted to link color) -> open; Ctrl/Cmd+click -> open; toolbar Link + popup Edit share the modal dialog and write through the preview patch path.

- [ ] **Step 1:** Update the spec contract.
- [ ] **Step 2:** Update the user docs page(s); keep `tree.yml` consistent; no em dashes.
- [ ] **Step 3:** Commit `docs: preview link management (popup, toolbar, dialog)`.

---

## Self-Review

- **Spec coverage:** click-text-popup (T5,T6), click-icon-open (T6), Ctrl/Cmd+click (kept, T6), icon recolor (T6), toolbar Link (T4), shared modal dialog (T3, used by T4+T5), markdown apply via patch path (T1, T4, T5), docs (T7). All design points covered.
- **Type consistency:** `LinkMarkdown.applyLink`/`Result`, `linkTargetAt`/`LinkTarget`, `LinkDialog.edit`/`LinkInput`, `isValidLinkUrl` are referenced consistently across tasks.
- **Existing work:** the committed link rendering/following/icon and the uncommitted icon-`closeEnd` fix are the substrate; Task 6 supersedes the open-on-plain-click stub with the popup routing.
