---
id: 3
title: Create a single test run and record verdicts
priority: critical
status: draft
tags:
  - test-run
  - smoke
---

Running a test case creates a single-case `.tr.md` run; its fields can be edited and a verdict recorded per step.

Preconditions:

1. The SpeQA plugin is installed and a project is open
2. A SpeQA test case (`.tc.md`) with at least one step exists in the project

Scenario:

1. Right-click the test case (`.tc.md`) in the Project view and choose "Run Test Case"
   > The "Create test run" dialog opens, with import checkboxes and a "Destination" field defaulting to "test-runs"

2. Confirm the dialog with "OK"
   > A new `.tr.md` is created under "test-runs/" and opens in the SpeQA run editor, listing the source steps

3. Type a comment under the first step
   > The comment is written into the `.tr.md` source

4. Set the first step's verdict to "Passed"
   > The step shows "Passed" and the run progress reflects the recorded verdict
