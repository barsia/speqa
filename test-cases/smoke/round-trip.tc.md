---
id: 1
title: Functional round-trip through the core SpeQA workflow
priority: critical
status: ready
tags:
  - "smoke"
---

A single happy-path walk through the SpeQA plugin: create a project, author and edit test cases, create a single and a multi-case run, and browse the tool window.

Preconditions:

The [SpeQA plugin](https://plugins.jetbrains.com/plugin/31268-speqa--test-management-system) is installed.

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

4. 1. Right-click the "test-cases" folder again and choose "New | SpeQA Test Case"
   2. Enter a different name and confirm
   > A second `.tc.md` exists under "test-cases/"

5. 1. Open a `.tc.md` and click the Run button (the green Play icon, "Start a manual test run") in the test case header
   2. Confirm the "Create test run" dialog with "OK"
   > A `.tr.md` is created under "test-runs/" and opens in the run editor

6. On the first step of the run, click "Passed"
   > The first step is marked "Passed"

7. 1. Open the "SpeQA" tool window
   2. Open the "TRs" tab
   3. Click the "+" button at the top and choose "Create test run"
   4. Click "Select all" and confirm
   > A multi-case `.tr.md` opens in the run editor with one section per selected test case

8. In the "SpeQA" tool window, on the "TCs" tab, open the "Priority" filter and select a value
   > The list shows only test cases with that priority

9. Click "Clear all filters"
   > The full list of test cases is restored
