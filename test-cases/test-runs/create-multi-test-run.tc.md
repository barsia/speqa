---
id: 4
title: Create a multi-case test run and record verdicts at every level
priority: critical
status: draft
tags:
  - test-run
  - smoke
---

A multi-case run groups several test cases into one `.tr.md`; verdicts are recorded per step, aggregated into an auto case result, and can be overridden manually.

Preconditions:

1. The SpeQA plugin is installed and a project is open
2. At least two SpeQA test cases (`.tc.md`), each with steps, exist in the project

Scenario:

1. 1. Open the "SpeQA" tool window
   2. Click "Create test run"
   > The "Create test run" dialog opens, listing the project's test cases with a "Select all" control

2. 1. Select two or more test cases
   2. Confirm the dialog
   > A multi-case `.tr.md` is created under "test-runs/" and opens in the run editor with one collapsible section per selected case

3. In the first case section, type a comment under its first step
   > The comment is written into the `.tr.md` source for that case

4. Set that first step's verdict to "Passed"
   > 1. The step shows "Passed"
   > 2. The case "Result" shows "Auto (from steps)" and reflects the step verdict

5. 1. Click the second case's "Result"
   2. Choose a result in the "Set case result" popup
   > The second case's result is set manually and no longer auto-derived from its steps

6. Click "Reset results" and confirm
   > Every step verdict and case result is cleared back to not started
