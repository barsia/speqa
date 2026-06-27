# Design: Create a test run from multiple test cases

Status: design agreed (brainstorm), pending implementation plan.

## Goal

Let a user create a test run that covers several test cases at once, picked by their
properties (priority / tags / environment), while keeping the existing ability to run
a single test case. The run is one `.tr.md` file. The empty Test Runs tab and its
header gain a single entry point that opens an adaptive Create Test Run dialog.

## Why

Today a test run mirrors exactly one test case (a flat list of step results). A manual
QA works through a *set* of cases in a session and needs a per-case verdict and a real
report ("8 High cases: 7 passed, 1 failed"). A flat list of all steps loses the case
boundaries and per-case results, so the run model must hold multiple cases.

## Scope and phasing

This is two sub-features. Phase 1 is a prerequisite for Phase 2.

- **Phase 1 - Multi-case run model (foundation).** Extend the test run so one `.tr.md`
  holds an ordered list of case sections, each with its own steps, verdicts, and a
  per-case result. Update model, file format, parser, serializer, document patcher, and
  the run editor (sections with drag-and-drop reordering). A single-case run is just a
  run with one section, so the existing single-case flow keeps working.
- **Phase 2 - Create-from-test-cases flow (UI).** The Test Runs tab entry point and the
  adaptive Create Test Run dialog that selects test cases and produces one multi-case run.

Each phase gets its own implementation plan. Phase 1 ships first.

---

## Phase 1: multi-case run model

### Data model

A test run becomes a list of **case sections** plus run-level metadata.

- Run level: `id`, `title`, `runner`, `startedAt`, `finishedAt`, overall `result`
  (aggregated from sections), run-level `comment`.
- Case section (per included test case): the source case identity (id and title), the
  imported context for that case (description / preconditions / tags / environment /
  links / attachments, subject to import options), its step results (the existing
  `StepResult` list: action, expected, tickets, links, verdict, comment, attachments),
  and a **per-case result** (pass / fail / blocked / not started), aggregated from its
  steps or set explicitly.

A **single-case run = one section.** Existing single-case `.tr.md` files are read as a
run with one section, so they keep opening and editing correctly (backward compatible).

### File format (`.tr.md`)

Each section is rendered the way a test case scenario already reads in Markdown (the
"shared scenario readability contract" in the main spec is preserved): a per-case
heading carrying the case title/id, that case's body (description / preconditions /
steps with verdict and comment), and a per-case result line. The exact section
delimiter and result-line syntax are defined during Phase 1 planning; constraints:
the file stays understandable on GitHub, round-trips losslessly, and a one-section run
serializes close to today's single-case `.tr.md`.

### Parser / serializer / patcher

`TestRunParser`, `TestRunSerializer`, and `DocumentRangeLocator` / `DocumentPatcher`
gain a section level (run -> sections -> steps). Targeted patching must keep working
per-section so editor edits stay surgical.

### Run editor

The run editor renders each case as a section: case heading, per-case result, and its
steps (reusing the existing step renderer with verdict pills). Sections are
**collapsible**, and **whole sections can be reordered by drag-and-drop** (the existing
step drag-and-drop pattern, lifted to the section level). The run header shows overall
progress and aggregated result (for example "3 / 8 cases done").

---

## Phase 2: create-from-test-cases flow

### Entry points (SpeQA tool window, Test Runs tab)

- **Header action (persistent):** a `+ Create test run` action in the Test Runs tab
  title bar, placed **first** (before the filter facets) with a separator, since it is
  the primary action and the facets only refine the displayed run list. Enabled when at
  least one test case exists; disabled with a "Create a test case first" tooltip
  otherwise.
- **Empty-state CTA:** when no runs exist, the tab body shows a prominent
  `+ Create test run` button (for discoverability). When no test cases exist, the button
  is shown disabled with the hint "Create a test case first - there is nothing to run yet."
- **Single-case (preserved):** the existing "run this test case" entry (test case editor
  / Test Cases tab) opens the same dialog with that one case pre-checked, producing a
  one-section run.

There are deliberately **no facet buttons in the tool window**. All facet/filter logic
lives in the dialog, which adapts to what exists.

### Create Test Run dialog

Extends the current `RunCreationDialog`.

- **Adaptive filters:** Priority / Tags / Environment controls, each shown only if at
  least one test case has a value for that facet (so "tags but no priority" shows only a
  Tags filter). Nothing is pre-selected when opened from the generic header action; when
  opened from a specific context (future), a facet value may be pre-filled.
- **Filter semantics:** AND across facets; within Tags or Environment, match-any. This
  matches the tool-window filter predicate (`matchesFilter`) so the behavior is consistent.
- **Live case list:** all test cases that match the active filters, each a checkbox.
  Defaults to **all checked**. A **Select all / Clear** control at the top; individual
  cases can be unchecked. The footer shows "N cases selected -> 1 test run (N sections)".
- **Import options (optional, per-section):** the existing checkboxes - import tags,
  environment, tickets, links, attachments - are kept because they carry illustrations
  and ticket context. They are optional (may be left unchecked) and apply per section
  (each section imports from its own source case).
- **Title:** default carries date/time so repeats are naturally distinct, e.g.
  "High - 2026-06-26 14:30" when a single facet value is active, otherwise
  "Test Run - 2026-06-26 14:30". Editable. Titles may repeat over time (running "High"
  weekly is normal); uniqueness is enforced on the **file name** (auto-suffix on collision).
- **Destination / file name:** as in today's dialog.
- **Create:** writes ONE `.tr.md` run with a section per checked case (Phase 1 model),
  importing per the selected options, then opens it in the run editor.

---

## Edge cases

- Filters match zero cases: Create is disabled, footer reads "0 cases selected".
- Filtering by a facet excludes cases without a value for it (consistent with `matchesFilter`).
- No test cases at all: header action and empty-state button are disabled with the hint.
- Backward compatibility: existing single-case `.tr.md` opens as a one-section run.

## Out of scope (for now)

- Reporting / dashboards beyond the run editor's per-case results and aggregate.
- Adding or removing case sections in an existing run after creation (possible later).
- Changing the Test Cases tab beyond the existing single-case run entry.

## Open items for planning

- Exact `.tr.md` section delimiter and per-case result-line syntax (Phase 1).
- How per-case result aggregates to the overall run result (all-pass = pass, any-fail = fail, etc.).
- Migration detail for the one-section serialization to stay close to today's format.
