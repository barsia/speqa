---
id: 1
title: Functional round-trip through the core SpeQA workflow
priority: critical
status: ready
tags:
  - "smoke"
---

A single happy-path walk through the SpeQA plugin: create a project, author and edit test cases, run a single and a multi-case run, and browse the tool window.

Preconditions:

The SpeQA plugin is installed.

Scenario:

1. 1. On the Welcome screen, click "New Project" (or "File | New | Project" inside an open project)
   2. Select the "Test Cases Project" generator
   3. Click "Create"
   > 1. The project opens with a "test-cases/" folder
   > 2. The project has a "test-runs/" folder
   > 3. A starter "login-happy-path.tc.md" opens in the SpeQA editor

2. 1. Right-click the "test-cases" folder in the Project tool window and choose "New | SpeQA Test Case"
   2. Enter a name and confirm
   > 1. A new `.tc.md` is created under "test-cases/"
   > 2. It opens in the SpeQA editor showing a source pane and a preview pane

3. 1. In the preview, click "Add tag" under "Tags"
   2. Type "regression" and press "Enter"
   > 1. A "regression" chip appears in the preview
   > 2. The source `tags:` list gains "regression"

4. Create a second test case via "New | SpeQA Test Case"
   > A second `.tc.md` exists under "test-cases/"

5. 1. Right-click a `.tc.md` and choose "Run Test Case"
   2. Confirm the "Create test run" dialog with "OK"
   3. Set the first step's verdict to "Passed"
   > 1. A `.tr.md` is created under "test-runs/" and opens in the run editor
   > 2. The first step shows the "Passed" verdict

6. 1. Open the "SpeQA" tool window and switch to the "TCs" tab
   2. Right-click the "test-cases" folder and choose "Create test run"
   3. Click "Select all" and confirm
   > 1. A multi-case `.tr.md` opens in the run editor
   > 2. It has one section per selected test case

7. 1. On the "TCs" tab, open the "Priority" filter and select a value
   2. Click "Clear all filters"
   > 1. Selecting a priority narrows the list to matching test cases
   > 2. "Clear all filters" restores the full list
