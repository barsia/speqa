---
title: Test Case Tool Window
---

The **SpeQA** tool window lists every test case and test run in your project so you can browse them, search by just starting to type, filter by their properties, and open them, all without digging through the project tree. It sits on the left tool window stripe and is available in every project.

## Opening the tool window

Click the **SpeQA** button on the left stripe to open the tool window. If the button is collapsed, hover the stripe and select **SpeQA**.

The tool window reads from the project's `test-cases/` and `test-runs/` directories. When a project has neither yet, the window is empty until you add one.

## Tabs

The tool window has two tabs:

- **Test Cases** - every `.tc.md` file under `test-cases/`
- **Test Runs** - every `.tr.md` file under `test-runs/`

Each tab has its own list and filters.

## The list

Items are shown by their **title** and grouped by the folder they live in, mirroring the directory structure. This lets you keep related items together (for example by feature area) and still see everything in one place.

To open an item, **double-click** it or select it and press **Enter**. It opens in the editor just like opening the file directly.

## Search by name

Start typing while the list is focused to filter it by name. Matching items stay visible and the first match is selected, so you can jump to one by typing part of its title.

## Filter by properties

The title bar shows four filter facets for the active tab. The first facet differs by tab; the other three are shared:

- **Status** on the Test Cases tab, or **Result** on the Test Runs tab - the item's primary state
- **Priority** - the item's priority
- **Tags** - any of the selected tags
- **Environment** - any of the selected environments

Click a facet to open its dropdown and pick values. Status (or Result) and Priority take a single value each. Tags and Environment accept several values at once; an item matches when it has at least one of the selected tags (or environments).

Facets combine with **and**: an item is shown only when it satisfies every active facet. For example, selecting Status `Ready`, Priority `High`, and tags `smoke` and `login-flow` shows ready, high-priority cases tagged with `smoke` or `login-flow`.

The active facet buttons are highlighted, and a **Clear filters** action appears in the title bar while any filter is set. Use it to reset all facets at once.

See [Test Case Properties](test-case-properties.md) for how status, priority, tags, and environments are set, and [Running and Tracking Tests](running-tests.md) for test run results.
