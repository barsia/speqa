---
id: 2
title: Create a test case and edit its fields in the preview
priority: critical
status: draft
tags:
  - editor
  - smoke
---

Creating a SpeQA test case opens it in the editor; edits made in the preview pane are written back to the `.tc.md` source.

Preconditions:

1. The SpeQA plugin is installed and a project is open

Scenario:

1. 1. Right-click a folder in the Project view and choose "New | SpeQA Test Case"
   2. Enter a file name
   3. Confirm the dialog
   > A new `.tc.md` file is created and opens in the SpeQA editor with a source pane and a preview pane

2. 1. Click the title in the preview and change it to "Renamed case"
   2. Move focus off the title to commit
   > The source pane shows `title: Renamed case`

3. 1. Click "Add tag" under "Tags"
   2. Type "regression"
   3. Press "Enter"
   > A "regression" tag chip appears and the source `tags:` list gains a "regression" entry

4. 1. Click "Add step"
   2. Enter an action and an expected result
   > The source `Scenario:` section gains a new numbered step with its `> ` expected result line

5. 1. Click "Add link"
   2. Enter a title and an "https://" URL
   3. Confirm with "Add"
   > A link row appears and the source gains a `Links:` entry placed before `Preconditions:`
