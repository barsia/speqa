# Changelog

## 0.1.9

- Run several test cases together in a single multi-case test run
- Set the overall run result from a dropdown, with per-case manual overrides
- Reset all results, or expand and collapse all cases, in one action
- Create Test Run imports nothing by default; click a row to toggle a case
- Test case files now place Links before Preconditions
- Resolve Duplicate IDs now lists every file sharing an ID and marks the keeper
- The preview is now fully keyboard-navigable
- A focus ring now shows only for keyboard focus, not mouse clicks
- The step drag handle is now keyboard-operable
- Focus returns to where you were after closing a dialog or deleting a row
- Fixed the preview desyncing after undoing an added step
- Fixed title editing issues, including caret jumps and saved blank titles

## 0.1.8

- New SpeQA tool window on the left lists every test case or test run by its title, grouped by folder
- Filter test cases by status, priority, tags, and environment
- Fixed Tab adding stray spaces inside a step's expected result

## 0.1.7

- Soft wrap is now enabled by default in the Markdown editor for test cases and test runs
- Fixed keyboard handling when editing expected results in the Markdown editor
- Removing a tag, link, attachment, or environment value from the preview now takes effect immediately
- Deleting the last tag or environment value now removes the field from the file cleanly
- Fixed selecting text inside a code block
- Fixed backspace on empty lines inside code blocks
- Hover over a code block to reveal a copy button
- Removing a link no longer asks for confirmation
- Fixed scroll sync losing alignment on documents with code blocks or long expected results

## 0.1.6

- Changing a step's result during a test run keeps the rest of the steps intact
- Inline code stays literal: backticked URLs render as code, not links

## 0.1.5

- Duplicate test case IDs are highlighted as you type
- Resolve duplicate IDs one by one, or across the whole project at once
- Preview text no longer shows line-wrap arrows

## 0.1.4

- Preview no longer jumps or flashes while you type
- The preview keeps its scroll position across edits
- Typing a new step number no longer scrolls the preview away

## 0.1.3

- Rewritten on native Swing: faster startup, lower memory
- Test run view at parity with a test case
- Per-step verdict pills with colored fill and a left progress strip
- Tag and environment search popup
- Step comment auto-expands when set; dot indicator on the toggle
- Auto-continue blockquotes on Enter in Expected
- WYSIWYG inline editor with floating selection toolbar and formatting shortcuts
- Truncation tooltip on long dates
- Sticky header with slide-in animation

## 0.1.2

- Test cases and test runs now share a two-column step editor
- Test runs are fully editable
- Create Test Run optionally imports tags, environment, tickets, links, and attachments from the test case
- Attachment preview on hover
- Sticky headers for test cases and test runs
- New Project wizard: checkboxes to init Git and install the Claude Code skill
- `test-case-writer` skill rewritten with create/update flow and ISTQB-aligned priorities

## 0.1.1

- Redesigned preview (Composer): step-level links, two-column action/expected over three-column tickets/links/attachments, image preview on hover, Cmd+Enter new step, Alt+↑↓ reorder, focus-trail strip at top
- Ticket linking in test cases and test runs — attach bug tracker IDs to expected results, click to open in YouTrack, Linear, or your custom tracker
- Auto-continue numbered and bulleted lists on Enter in text fields in preview
- Rename selects only base name for `.tc.md` / `.tr.md`
- Better hover feedback on verdict buttons and comment toggle in test runs
- Polished UI text across the board
- Drag-and-drop step reordering with live preview and drop-target highlight
- Redesigned step card — flat layout, delete in context menu
- Bundled Claude Code skill for generating test cases from specs, designs and tickets

## 0.1.0 — Initial Release

- Split editor for `.tc.md` files — native text editor + interactive preview
- YAML frontmatter: id, title, priority, status, environment, tags
- Body blocks: description, preconditions (ordered, round-trip safe)
- Step-by-step editing with action, expected result, and attachments
- Test run execution with `.tr.md` files and pass/fail/blocked verdicts
- External links support with add/edit dialog and URL validation
- File attachments with drag-drop, missing file detection, and relink
- Targeted document patching — preserves user formatting
- Tag/environment autocomplete from project-wide registry
- Status-colored file icons in project view (draft/ready/deprecated)
- Resilient YAML parsing — broken fields don't crash the preview
- JSON Schema for frontmatter validation
- Soft validation warnings for incomplete test cases
