---
id: 2
title: Create a test run from a test case
priority: critical
status: draft
tags:
  - test-run
  - smoke
---

Running a test case opens the "Create test run" dialog, writes a `.tr.md` under "test-runs/", and opens it in the run editor where step verdicts can be set.

Preconditions:

- The SpeQA plugin is installed and a project is open
- A SpeQA test case (`.tc.md`) with at least one step exists in the project

Scenario:

1. Right-click the test case (`.tc.md`) in the Project view and choose "Run Test Case"
   > The "Create test run" dialog opens, with import checkboxes ("Description", "Tags", "Environment", "Tickets", "Links", "Attachments"), a "Destination" field defaulting to "test-runs", and a run file name

2. Confirm the dialog with "OK"
   > 1. A new `.tr.md` file is created under "test-runs/"
   > 2. The file opens in the SpeQA test run editor, listing the source test case's steps

3. Set the first step's verdict to "Passed"
   > The step is marked "Passed" and the run's overall progress reflects the recorded verdict
