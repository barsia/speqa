---
id: 3
title: Edit a test case in the preview and see it in the source
priority: critical
status: draft
tags:
  - editor
  - smoke
---

Editing fields in the SpeQA preview pane writes the change back to the raw `.tc.md` source, so the source and preview stay in sync.

Preconditions:

- The SpeQA plugin is installed and a project is open
- A test case (`.tc.md`) is open in the SpeQA editor, showing both the Markdown source pane and the preview pane

Scenario:

1. 1. Click the title in the preview and change it to "Renamed case"
   2. Move focus off the title to commit
   > The source pane shows "title: Renamed case" in the frontmatter

2. 1. Click "Add tag" under "Tags"
   2. Type "regression"
   3. Press "Enter"
   > 1. A "regression" tag chip appears in the preview
   > 2. The source "tags:" list gains a "regression" entry

3. 1. Click "Add step"
   2. Enter an action and an expected result for the new step
   > The source "Scenario:" section gains a new numbered step with its "> " expected result line

4. 1. Click "Add link"
   2. Enter a title and an "https://" URL in the dialog
   3. Confirm with "Add"
   > 1. A link row appears in the preview
   > 2. The source gains a "Links:" entry "[title](url)" placed before "Preconditions:"
