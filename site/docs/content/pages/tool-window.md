---
title: Test Case Tool Window
---

The **SpeQA** tool window lists every test case and test run in your project so you can browse them, search by just starting to type, filter by their properties, and open them, all without digging through the project tree. It sits on the left tool window stripe and is available in every project.

## Opening the tool window

Click the **SpeQA** button on the left stripe to open the tool window. If the button is collapsed, hover the stripe and select **SpeQA**.

The tool window reads from the project's `test-cases/` and `test-runs/` directories. When a project has neither yet, each tab shows an empty-state panel with a button to create the first item.

## Tabs

The tool window has two tabs, shown next to the window name:

- **TCs** - every `.tc.md` test case under `test-cases/`
- **TRs** - every `.tr.md` test run under `test-runs/`

Each tab has its own list and its own filters; switching tabs does not carry filter state across.

## Creating items

Each tab's title bar starts with a **+** action:

- On **TCs**, **+** creates a new test case (it prompts for a name, generates the next ID, and opens the file).
- On **TRs**, **+** opens the **Create test run** dialog so you can build a run from one or more test cases. It is enabled only when at least one test case exists. See [Running and Tracking Tests](running-tests.md).

New items are placed relative to the current tree selection: a selected leaf creates a sibling in its folder, a selected folder creates the item inside it, and no selection creates the item in the tab's root directory. Right-clicking a node opens a context menu with Open, Run Test Case (on a test case), Create Test Run, Rename, Delete, and Reveal actions.

## The list

Items are shown by their **title** and grouped by the folder they live in, mirroring the directory structure. This lets you keep related items together (for example by feature area) and still see everything in one place.

To open an item, **double-click** it or select it and press **Enter**. It opens in the editor just like opening the file directly.

## Search by name

Start typing while the list is focused to filter it by name. Matching items stay visible and the first match is selected, so you can jump to one by typing part of its title.

## Filter by properties

The title bar shows four filter facets for the active tab. The first facet differs by tab; the other three are shared:

- **Status** on the TCs tab, or **Result** on the TRs tab - the item's primary state
- **Priority** - the item's priority
- **Tags** - any of the selected tags
- **Environment** - any of the selected environments

Click a facet to open its dropdown and pick values. Status (or Result) and Priority take a single value each. Tags and Environment accept several values at once; an item matches when it has at least one of the selected tags (or environments).

Facets combine with **and**: an item is shown only when it satisfies every active facet. For example, selecting Status `Ready`, Priority `Major`, and tags `smoke` and `login-flow` shows ready, major-priority cases tagged with `smoke` or `login-flow`.

The active facet buttons are highlighted, and a **Clear all filters** action appears in the title bar while any filter is set. Use it to reset all facets at once. A row of removable chips just above the list shows each active selection.

See [Test Case Properties](test-case-properties.md) for how status, priority, tags, and environments are set, and [Running and Tracking Tests](running-tests.md) for test run results.
