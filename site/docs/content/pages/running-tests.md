---
title: Running and Tracking Tests
---

A **test run** is a recorded execution of one or more test cases. SpeQA stores each run as a `.tr.md` file and opens it in an interactive run editor where you record a verdict for every step. A single run can cover several test cases at once.

## Two Ways to Start a Run

### From a single test case (Run button)

1. Open a `.tc.md` test case file.
2. In the editor header, click the green **Run** (play) button. Its tooltip reads **Start a manual test run**.
   - You can also right-click a `.tc.md` file in the project or the SpeQA tool window and choose **Run Test Case**.
3. The **Create test run** dialog opens. Choose what to import from the source case (see [Import options](#import-options)), pick the destination folder and file name, then click **Create**.

This produces a run with a single test case section.

### From the tool window (multiple cases)

1. Open the **SpeQA** tool window and switch to the **TRs** tab.
2. Click the **+** action in the tab's title bar, or use the **Create test run** button shown in the empty state.
3. The **Create test run** dialog opens with every available test case listed.
4. Tick the cases you want to include (or use **Select all** / **Clear**), narrow the list with the filter row if needed, set the run title, destination, and file name, then click **Create**.

The new run contains one section per selected test case.

After a run is created, SpeQA opens it in the run editor, activates the **TRs** tab, and selects the new run there, leaving the keyboard focus in the editor so you can start recording verdicts right away.

## Import Options

The Create test run dialog lets you copy material from the source test cases into the run. All six options are **off by default** - a run starts as a clean result sheet and you opt into copying what you need:

- **Description** - the case's description and preconditions
- **Tags**
- **Environment**
- **Tickets** - ticket references on steps
- **Links**
- **Attachments**

An option is disabled when the source case has nothing of that kind to import.

## The Run Editor

The run editor mirrors the test case layout. At the top, a header shows the run **ID**, title, created/started/finished timestamps, **Runner**, **Environment**, **Tags**, and the overall **Result**. Below the header, each test case appears as its own section.

### Recording step verdicts

Every step has four verdict buttons:

- **Passed**
- **Failed**
- **Skipped**
- **Blocked**

Click a verdict to set it. Click the currently selected verdict again to clear it back to *not set*. Each step also has a comment button (the balloon icon) for recording an observation - especially useful on failed or blocked steps.

### Per-case result

Each case section header shows a **result pill**. It is normally derived automatically from that case's step verdicts, but you can click it to override the result manually (Passed, Failed, Blocked, In progress, Not started), or choose **Auto (from steps)** to go back to the derived value. A small indicator marks a result that was set manually.

### Overall run result

The header **Result** is a dropdown showing the aggregate of the per-case results. Picking a value pins a manual override for the whole run (Passed, Failed, or Blocked); picking **Not started** clears the override and returns to the automatic aggregate. While overridden, a manual indicator appears next to the dropdown. The aggregate is computed as follows:

- All steps unset -> **Not started**
- Some steps have a verdict, some do not -> **In progress**
- All steps have a verdict (Skipped ignored for the final result): any **Failed** -> **Failed**; otherwise any **Blocked** -> **Blocked**; otherwise -> **Passed**

### Managing cases in the run

For a run that covers several cases, the header offers:

- **Expand all** / **Collapse all** to open or close every case section at once. Sections start collapsed for fast loading.
- A drag handle on each section header so you can reorder the cases.

A **Reset results** button (next to the Result control) clears every step verdict and every case/run result back to *not started* after a confirmation. Step text, comments, and metadata are preserved. It is disabled when the run is already untouched.

## Run History

Each run is its own `.tr.md` file. Running the same test case again creates a new run file, so the set of `.tr.md` files under your test-runs folder is the execution history. Open any run file directly to review its recorded verdicts and comments; you can edit them after the fact.

Browse runs in the **TRs** tab of the [SpeQA tool window](./tool-window.md), where you can filter them by result, priority, tags, and environment.

## Attachments and Evidence

A run keeps the same attachment and link sections as a test case, at both the run level and per step. Add a screenshot or log file to document what you observed. See [Test Case Properties](./test-case-properties.md) for how attachments and links work.

## Troubleshooting

**The Run button does nothing / no run is created:**
- Make sure you completed the Create test run dialog and clicked **Create** (an invalid destination keeps the button disabled).
- Confirm the destination folder is inside the project.

**The TRs "+" / Create test run button is disabled:**
- A run needs at least one test case. Create a test case first.

**Can't find my run file:**
- Runs are saved in the destination you chose in the dialog (the test-runs folder by default).
- Look in the **TRs** tab of the SpeQA tool window - the newly created run is selected there.

## What's Next?

- Organize and filter runs in the [SpeQA tool window](./tool-window.md)
- Learn about [Test Case Properties](./test-case-properties.md) to enrich your evidence
- Set up the [Test Case Writer skill](./claude-code-skills.md) to write cases faster
