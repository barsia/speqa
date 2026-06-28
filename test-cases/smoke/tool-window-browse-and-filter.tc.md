---
id: 4
title: Browse and filter test cases in the SpeQA tool window
priority: major
status: draft
tags:
  - tool-window
  - smoke
---

The SpeQA tool window lists the project's test cases and test runs on separate tabs, supports facet filters, and opens a case on double-click.

Preconditions:

The SpeQA plugin is installed and a project with several test cases and at least one test run is open

Scenario:

1. Open the "SpeQA" tool window
   > 1. It shows a "TCs" tab and a "TRs" tab
   > 2. The "TCs" tab lists the project's test cases

2. 1. Open the "Priority" filter on the "TCs" tab
   2. Select a single priority value
   > The list narrows to test cases with the selected priority

3. Click "Clear all filters"
   > All test cases are listed again

4. Double-click a test case in the list
   > The test case opens in the SpeQA editor

5. Switch to the "TRs" tab
   > The list shows the project's test runs
