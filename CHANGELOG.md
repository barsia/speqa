# Changelog

## 0.1.1

- Ticket linking in test cases and test runs — attach bug tracker IDs to expected results, click to open in YouTrack, Linear, or your custom tracker
- Auto-continue numbered and bulleted lists on Enter in text fields in preview
- Rename selects only base name for `.tc.md` / `.tr.md`
- Better hover feedback on verdict buttons and comment toggle in test runs
- Polished UI text across the board
- Drag-and-drop step reordering with live preview and drop-target highlight
- Redesigned step card — flat layout, delete in context menu

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
