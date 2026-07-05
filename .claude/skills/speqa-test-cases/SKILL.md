---
name: speqa-test-cases
description: Use when the user asks to write, update, or review manual test cases in a SpeQA project (`.tc.md` files), based on a feature description, user story, spec, acceptance criteria, bug report, PR, Figma design, or a ticket from a tracker.
---

# SpeQA Test Cases

## Overview

Maintain a project's manual test-case base in `.tc.md` format (YAML frontmatter + Markdown body) - authoring new cases and keeping existing ones aligned with changing requirements. Focus on end-to-end user or client workflows, not isolated unit checks.

## When to Use

Two modes, same skill.

**Create** a new case - a new feature, screen, endpoint, or spec needs coverage. Invocations: "write a manual test case", "write cases", "write checks", "cover this with manual checks", "generate tc".

**Update** an existing case - bug fix (extend the case that should have caught it), changed requirements, renamed UI or API labels, revised acceptance criteria. Invocations: "update cases", "update manual test cases for this PR", "regenerate test cases", "review changes and update cases", "fix the tc after this change".

## When NOT to Use

- User wants automated tests (unit, integration, E2E code).
- User wants a test strategy, test plan, or coverage analysis document.

## Decide: New Case vs Update Existing

Scan the project's test-case directory by tags, title, preconditions, and scenario keywords. **Default: update the existing case if coverage exists - do not create duplicates.**

**The deciding factor is setup, not topic.** If the new behaviour is reachable from the same preconditions and setup as an existing case (same starting state, same navigation, same fixtures), extend that case with the extra steps or assertions instead of creating a second file - even if the behaviour feels like a separate concern. Create a new `.tc.md` only when covering the behaviour needs materially different preconditions or setup, or would bloat the case into two unrelated scenarios. Never author a new case whose assertions largely repeat one an existing case already makes (same label, same state, same menu): two cases can drift to different expected results where only one can be right. Before writing a new file, grep the test-case directory for the labels, menu, or state you are about to assert; if a case already exercises that setup, add to it.

- **Bug fix** - find the case that should have caught it; extend it (add the missing step or tighten an expected result). Create a new case only if the bug is orthogonal to existing coverage.
- **Changed requirements** - update every case that references the old behaviour; append the new ticket to `Links:`.
- **New feature / screen / endpoint** - create a new file; assign `id` per the Frontmatter rules.
- **Refactor without behavioural change** - no test-case change. Report "No test-case impact detected" and stop.

If a single change touches several cases, edit each rather than consolidating into one new file.

### Update discipline

- Minimal diff. Change only what the source changes; do not reword untouched steps or reorder sections for cosmetics.
- Preserve `id`. Never renumber.
- Preserve `status`. If the tester set `ready`, leave it as `ready` - do not demote to `draft` just because you edited.
- `Links:` is additive. Append new tickets or URLs; do not replace.
- If an update invalidates the whole scenario (not a tweak), create a new case for the new behaviour and set `status: deprecated` on the obsolete one (its `id` and body stay untouched). State the reason in the new case's description.

## File Shape

```markdown
---
id: 2
title: Concise noun phrase describing what is tested
priority: normal
status: draft
tags:
  - feature-area
  - test-type
---

Optional one-line description if the title is not self-explanatory.

Links:

[Ticket key or short title](https://...)

Preconditions:

1. First precondition
2. Second precondition

Scenario:

1. Concrete action
   > Expected observable result

2. 1. Next action
   2. One more action
   > 1. First expected result
   > 2. Second expected result
```

Section markers appear in this order when present: `Links:` (optional), `Attachments:` (optional), `Preconditions:` (optional), `Scenario:` (required).

## File Naming

Kebab-case matching the scenario: `login-happy-path.tc.md`, `login-locked-account.tc.md`. One scenario per file - generate separate files rather than stuffing several scenarios into one.

## Frontmatter

- **id** - required integer, globally unique across the project. Scan all existing `.tc.md` files, collect every `id` in use, pick the smallest positive integer not in that set. Gaps come first: if `1, 2, 4, 5` exist, the next new case takes `3`, not `6`. When no gaps exist, use `max + 1`. If no cases exist, start from `1`.
  - **Batch safety**: when the same run creates several new cases, treat each just-assigned `id` as taken before picking the next.
  - **Editing an existing case**: keep its `id` unchanged.
- **title** - required. Concise noun phrase. Write unquoted when possible. Wrap in double quotes only if the title starts with `"`/`'`, contains a colon followed by space, or has other YAML-special characters. If the title contains double quotes (UI element names), wrap the whole title in single quotes: `title: 'Canceling "Add" dialog discards unsaved data'`.
- **priority** - based on user or client impact if this behaviour fails in production, aligned with ISTQB risk-based testing:
  - `critical` - the scenario cannot be completed, no alternative path exists. Or: data loss, money loss, security breach, cross-tenant leak, contract break for live clients.
  - `major` - the scenario can be completed via a different path, but it requires extra steps or knowledge.
  - `normal` - the defect is noticed but does not prevent completion via the current path.
  - `low` - not noticed during normal usage; visible only in rare or targeted inspection.
- **status** - `draft` for newly generated cases. Use `deprecated` only when superseding an obsolete case (see [Update discipline](#update-discipline)). Never write `ready` - that value is reserved for the tester's manual review.
- **environment** - optional. Omit when the case is environment-agnostic (the common case). Include only when the user explicitly pins environments. Use the list form for multiple values:
  - `environment:` then indented `- "Chrome 120, macOS 14"` lines, or
  - scalar form `environment: "Chrome 120, macOS 14"` for a single value.
- **tags** - YAML list. Each case carries two tags: a **feature-area** tag (what is tested: `add-dialog`, `mcp-servers`, `login`) and a **scenario-kind** tag that names the shape of the check (e.g. `smoke`, `negative`, `validation`, `regression`, `migration`, `contract`, `permissions`). Stay consistent with tags already used in the project: before inventing a new tag, search existing `.tc.md` files for a tag with the same meaning and reuse it.

## Description

Any free Markdown between the frontmatter and the first section marker is the description. It is optional - omit it when the title is self-explanatory (the common case).

Start with the behaviour under test as a noun phrase or short statement of what the scenario demonstrates. Examples:

- `Canceling the "Add" dialog discards entered data and leaves the list unchanged.`
- `MCP server provisioning via the Command connection type, end to end.`

## Attachments

`Attachments:` sections are managed by the user. Do not invent attachments, create files, or add attachment paths on your own. Preserve existing attachment references unless the user explicitly asks to change them, and only add new ones when exact paths are provided.

## Preconditions

Marker line: exactly `Preconditions:`, followed by a blank line. Preconditions usually form an ordered setup sequence where each item builds on the previous, so number them `1.`, `2.` by default; use a bulleted list (`- `) only when the items are genuinely independent and order does not matter. A single precondition takes no list marker at all. Each item must be executable or verifiable - a command, a config key with a value, a role assignment, a feature flag state - not an abstract description. Include auth state, screen or service location, data setup. Omit the section if nothing is required.

### Minimal fixtures

The data preconditions are the smallest data set that exercises every assertion in the scenario. A tester pays for every entity and every record they have to create, so the fixture is derived from the assertions, not invented first and interpreted later.

- **Each entity and each number maps to a specific assertion, and that purpose is written inline** in parentheses on the item itself: `A project "Alpha" with exactly 1 collaborator (singular form of the collaborator counter)`. An item whose parenthetical you cannot write is deleted.
- **An exact value has exactly two legitimate sources.** Either a requirement source pins it (quote that value), or the tester chooses it - then use the smallest value that triggers the behaviour and mark it `e.g.`: `more than one collaborator, e.g. 2 (plural form of the counter)`.
- **A larger-than-minimal value is used when it proves a computation.** 3 items prove an overflow tag counts (`"+2"`) where 2 would show the trivial `"+1"`; state that justification inline. Without such a justification, the minimal value wins.
- **Use `exactly N` when an assertion reads the total** (a count badge, an empty state after deleting the last item, a "N selected" label); use `at least N` only when no assertion depends on the total.
- Entities that only support other entities (a parent team in a hierarchy, an owning workspace) are named as such and get no extra properties: `the parent team only anchors the path`.

Example - fixture where every line carries its assertion:

```markdown
4. Exactly 2 projects exist in the organization (the "Projects" tab count badge reads the total):
   - A project "Alpha" owned by a team "Web" nested under a parent team "Platform" (team path breadcrumb under the project name), with exactly 1 collaborator (singular form: "1 collaborator"); the parent team only anchors the path.
   - A project "Beta" whose owner team was deleted (the "Unknown team" placeholder), with more than one collaborator, e.g. 2 (plural form: "2 collaborators").
```

## Links

Marker line: exactly `Links:`, followed by a blank line, then one link per line: `[title](url)`. Placed before `Preconditions:` and `Scenario:`. URLs must start with `http://` or `https://`. Use the ticket key as the link title for ticket URLs (`[ABC-1234](https://youtrack.jetbrains.com/issue/ABC-1234)`). Use a short descriptive title for other links (`[Figma - MCP add dialog](https://...)`). When updating a case, append new links; do not replace existing ones. Do not fabricate URLs - omit the section if none exist. Only *requirement* sources belong in `Links:` (the ticket, design, spec, or decision the case verifies). Never link a pull request, commit, branch, or a runtime/agent status notification - those are implementation or status, not requirements; follow them back to the originating ticket or design and link that. A bug ticket is not a `Links:` entry either: flag it under the affected step with a `Ticket:` marker (see Scenario Steps), unless the case exists *because* of that bug, in which case the bug may be cited in `Links:` as its origin.

## Scenario Steps

- Marker line: `Scenario:` followed by a blank line, then steps numbered `1.`, `2.`, ….
- **Each step = exactly one state-changing action the tester performs on the system** (click, type, submit, invoke, navigate, etc.). Pure observation is never a step; anything the tester only *looks at* after an action belongs in that action's `> ...` block - including follow-up checks on state the earlier action left behind. If a step bundles two actions, either **split it** (each asserted action gets its own expected result) or **use the nested sub-list form** below (one shared expected result covering the whole chain). Traversal-only actions may have no expected result when using the "skip repeated detail" rule.
- Expected result = `> ` blockquote lines immediately under the action.
- One line per distinct, separately observable check. If a step verifies several things at once (a folder exists AND a file opens AND a chip appears), give each its own numbered line (`> 1.`, `> 2.`, …) or bullet (`> - `) - never pack several checks into one sentence joined by "and" or commas, because each result must be tickable on its own. A single check needs no marker.
- A scenario cannot end on a bare action step - the last step must include an expected result.
- Expected results must be observable from outside the system - what the user sees or what the client receives. No internal implementation details.
- Nested sub-actions are allowed when a logical step contains tightly coupled inputs. Indent the sub-list **and** its `> ` lines by the full width of the parent marker, including its trailing space - count the characters in `N. ` / `NN. `: **3 spaces for single-digit steps 1-9, 4 spaces for double-digit steps 10-99**. Do not pick one fixed indent for the whole scenario: a 3-space indent silently breaks the first time a step number reaches two digits, because the renderer then reads the sub-item as a new top-level item and reports an error like `Expected item number 11, but got 2`. When a scenario crosses step 10, re-check that every nested step's indent shifted from 3 to 4 spaces.
- The `Scenario:` marker stands alone - no parenthetical, label, or qualifier on that line (move any condition into the title or split the case). Parsers only recognise a bare `Scenario:`.
- Nothing follows the last step's expected results - no summary, `Note:`, or trailing prose (a per-step `Ticket:` marker, defined below, counts as part of that step, not trailing prose). Whole-case remarks go in the description above the sections.
- For text content, the element "states" or "displays" the copy - do not write "reads" (it anthropomorphises the UI).
- Step-local bug marker: when a step documents the *intended* behaviour but the product does not match it yet, add a plain `Ticket: ABC-1234` line directly under that step's expected results (the `Ticket:` marker takes bug tickets only; tasks and features go in `Links:`; for the origin-bug exception see Links). Remove it once the bug is fixed and the step passes.
- Skip repeated detail: when reaching a new assertion needs navigation or setup that another case already verifies, write those traversal steps with no `>` lines and put the `>` only on the step that checks the new behaviour.

Observation attached to the triggering action, not promoted to its own step:

```markdown
1. Click "Cancel" in the dialog
   > Dialog closes without saving
   > The field displays "200" and is no longer marked as changed
```

With a summary line:

```markdown
1. Add the first environment variable:
   1. Click "+ Add variable"
   2. Enter "DB_HOST" into the "Name" field
   3. Enter "localhost" into the "Value" field
   > A row with "DB_HOST" / "localhost" is displayed
```

Without a summary:

```markdown
1. 1. Fill in both mandatory fields
   2. Click the "Cancel" button
   > - Dialog closes
   > - No new server appears in the table
```

Double-digit parent step - sub-list and `> ` lines shift to 4 spaces (aligned under the text after `10. `):

```markdown
10. 1. Pick "Edit credentials" in the saved provider's context menu
    2. Enter `  https://api.openai.com  ` (leading and trailing spaces) into the "Endpoint" field
    3. Click "Connect"
    > 1. No validation error appears under the "Endpoint" field
    > 2. The provider connects using the trimmed endpoint
```

## Writing Style

- Actions in imperative mood naming a state-changing operation: "Click", "Enter", "Send", "Invoke", "Navigate to", "Publish", "Apply". Observation verbs ("Verify", "Check", "Confirm", "Ensure", "Observe") are not actions and cannot be steps.
- **One action per line.** Do not join two actions on one line with `, then`, `, and`, `;`, or an arrow. Two actions = two lines. If they share one expected-result block, use the nested sub-list form (the parent step holds the sub-actions as numbered children, the `>` block sits at parent indent and covers the chain). This is the correct format whenever a step performs more than one user action - not an optional stylistic variant.
  - Wrong: `3. Open a test case, then click the Run button`
  - Right:
    ```
    3. 1. Open a test case
       2. Click the Run button
       > [shared expected result]
    ```
- Wrap UI element names, endpoints, payload keys, and config keys in quotes or backticks consistent with the surrounding project.
- No em dash (`—`). Use `-` or rewrite the sentence.

Title examples (same intent, wrong vs right):

| Bad                        | Good                                             |
|----------------------------|--------------------------------------------------|
| `Test login`               | `Login with valid credentials`                   |
| `Cancel add dialog test`   | `'Canceling "Add" dialog discards unsaved data'` |
| `TC-12 MCP command server` | `Add MCP Server with Command connection type`    |

## Typical Scenario Shapes

- Happy path end-to-end - full workflow from entry to a confirmed result.
- Negative / error recovery - user hits an error and either recovers or fails safely.
- Cancel / discard - user aborts a workflow, verifies nothing changed.
- Validation - required fields, format, boundaries, inside a real submission flow.
- Cross-feature - actions spanning multiple features.
- Permissions / auth - role visibility and unauthorized access.

## Handling Ambiguity

Produce a deterministic result.

- **Save location** - place the new file next to the most-related existing `.tc.md` (by tags or feature area). If the project has no existing test cases yet, create `test-cases/<area>/` where `<area>` is the feature name taken from the change.
- **Scope** - narrowest reasonable interpretation: fewer cases, smaller edits.
- **Concrete tokens missing** (exact UI label, endpoint, payload key) - never fabricate. Skip the step rather than invent a value.
- **Requirement vs implementation** - a test case verifies *requirements*, not the current code. Requirement sources (tickets, designs, specs, acceptance criteria, requirement decisions in chat) are authoritative for every expected result and every quoted UI label. Code, PRs, and diffs are implementation: they help locate an exact endpoint, label, or response shape the requirement already calls for, but never justify an expected behaviour on their own. When code contradicts a requirement source, the requirement wins and the code is a candidate bug - do not author a case asserting the code's behaviour.
- **Conflicting requirement sources** - prefer the more specific and more recent one (a precise design node over a vague ticket line). If it cannot be resolved from the sources, do not author the conflicting expected result; report it instead.
