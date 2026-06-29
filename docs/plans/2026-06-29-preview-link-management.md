# Preview link management

Design for viewing, opening, creating, and editing Markdown links from the SpeQA preview (`MarkdownEditablePane`). Builds on the existing inline-link rendering (`inlineLinks` / `addLinkWysiwyg`), the open-link icon inlay, and Ctrl/Cmd+click following.

## Behaviour

A rendered inline link `[text](url)` in the preview supports:

1. **Click on the link text** opens a **link popup** (Slack-style, non-modal, floating above the link, below when there is no room above). It does not dismiss on mouse move; it closes on a click outside or Escape. The popup shows:
   - the link **text**,
   - the **URL** as a clickable label (clicking it opens the URL in the browser via `BrowserUtil.browse`),
   - an **Edit** button.
2. **Single click on the open-link icon** (the `External_link_arrow` inlay after the link) opens the URL in the browser directly, no popup. The icon is tinted to the link foreground colour via `IconUtil.colorize`.
3. **Ctrl/Cmd+click on the link text** opens the URL (existing behaviour, kept).

The link popup itself is a non-modal tooltip. Creating and editing a link share one **modal** **Link dialog** (a `DialogWrapper`), opened from the popup's Edit button or the toolbar:

4. **Formatting-toolbar "Link" button** (placed after "Code block", before the list buttons): with a non-empty selection, opens the Link dialog with Text prefilled from the selection and an empty URL; on OK it wraps the selection as `[text](url)`.
5. **Edit** in the popup opens the same Link dialog prefilled with the current text and URL; on OK it rewrites that link.

Plain click on the link text only opens the popup (it does not place the caret or start inline text editing); text is edited through the dialog.

## Components (isolated units)

- **`LinkDialog`** (`DialogWrapper`): two fields, Text and URL, with URL validation (must be `http(s)://...`). Returns `(text, url)` or cancelled. Shared by the toolbar button (create) and the popup Edit (update).
- **Link popup**: a non-modal `JBPopup` anchored above the clicked link, containing the text label, the clickable URL label (hand cursor), and the Edit button. Built and shown by `MarkdownEditablePane`.
- **Toolbar "Link" action**: a new button in the existing floating formatting toolbar, between Code block and Bullet list, opening `LinkDialog` for the selection and applying the result.
- **Link markdown apply** (pure): given the field text, a selection range, and `(text, url)`, produce the new field text and the resulting caret/selection - mirrors `MarkdownSelectionFormatter`. Reused for both create (wrap selection) and edit (replace an existing `[text](url)` span). Unit-tested.
- **Click routing** in the `MarkdownEditablePane` `EditorMouseListener`: a plain click resolves the editor offset to icon (-> open), link text (-> popup), or neither (default); Ctrl/Cmd+click on link text -> open. The offset-to-target decision is a pure helper over the parsed link ranges, unit-tested.
- **Icon tint** in `OpenLinkIconRenderer`: paint `IconUtil.colorize(External_link_arrow, linkColor)`.

## Data flow

Toolbar Link / popup Edit -> `LinkDialog` -> pure link-markdown apply -> field text mutation -> the existing preview-to-document patch path (`DocumentPatcher`) persists it, exactly like the other toolbar formatting actions. No new persistence path.

## Testing

- Pure: link-markdown apply (wrap selection; replace an existing link span; round-trip), URL validation, and the click offset-to-target decision. TDD, failing test first, mirroring `MarkdownWysiwygRangesTest` / `MarkdownSelectionFormatter` tests.
- UI (popup show/dismiss, icon click, dialog wiring) is verified by reasoning and a green build, per the testing approach used for the rest of `MarkdownEditablePane`.

## Out of scope / follow-up

- After implementation, update the spec (`docs/specs/2026-04-06-speqa-design.md`, inline-link WYSIWYG contract) and the user docs under `site/docs/` (a "links" section: render, open via icon/popup, create/edit via the toolbar).
- All user-visible strings (button tooltip, dialog labels/title, validation message) go through `SpeqaBundle.properties`.
