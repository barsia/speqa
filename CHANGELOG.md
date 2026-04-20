# Changelog

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
