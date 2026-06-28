---
id: 1
title: Functional round-trip through the core SpeQA workflow
priority: critical
status: draft
tags:
  - round-trip
  - smoke
---

A single happy-path walk through the product: create a project, author and edit test cases, run a single and a multi-case run, and browse the tool window. A failure anywhere is an early signal that the core workflow is broken. Detailed per-feature cases live outside `smoke/`.

Preconditions:

The SpeQA plugin is installed

Scenario:

1. 1. Open "File | New | Project"
   2. Select the "Test Cases Project" generator
   3. Click "Create"
   > The new project opens with "test-cases/" and "test-runs/" folders and a starter "login-happy-path.tc.md"

2. 1. Right-click a folder in the Project view and choose "New | SpeQA Test Case"
   2. Enter a name and confirm
   > The new `.tc.md` opens in the SpeQA editor with a source pane and a preview pane

3. 1. In the preview, click "Add tag" under "Tags"
   2. Type "regression" and press "Enter"
   > A "regression" chip appears and the source `tags:` list gains it

4. Create a second test case via "New | SpeQA Test Case"
   > A second `.tc.md` exists in the project

5. 1. Right-click a `.tc.md` and choose "Run Test Case"
   2. Confirm the "Create test run" dialog with "OK"
   3. Set the first step's verdict to "Passed"
   > A `.tr.md` is created under "test-runs/" and the step shows "Passed"

6. 1. Open the "SpeQA" tool window and switch to the "TCs" tab
   2. Right-click the "test-cases" folder and choose "Create test run"
   3. Click "Select all" and confirm
   > A multi-case `.tr.md` opens in the run editor with one section per selected case

7. On the "TCs" tab, open the "Priority" filter and select a value, then click "Clear all filters"
   > The list narrows to the chosen priority and "Clear all filters" restores the full list
