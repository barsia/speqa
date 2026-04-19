---
name: test-case-writer
description: "Generate manual test cases in .tc.md format from feature descriptions, user stories, designs (Figma), YouTrack tickets, or bug reports"
---

# Test Case Writer

Generate well-structured manual test cases in `.tc.md` Markdown format. Focus on end-to-end scenarios that cover complete user workflows, not isolated unit checks.

## When to Use

- User describes a feature, user story, or bug and wants test cases
- User specifies a YouTrack ticket, Figma design, or other design reference and asks to write test cases
- User says "write test case", "create tc", "generate test case", "/test-case-writer"
- User provides a spec or acceptance criteria and wants them converted to test cases

## Output Format

Each test case is a `.tc.md` file with YAML frontmatter and Markdown body:

```markdown
---
id: 1
title: "Concise noun phrase describing what is tested"
priority: normal
status: draft
tags:
  - feature-area
  - test-type
---

Brief one-line description of what this test case verifies.

Preconditions:

1. First precondition
2. Second precondition

Scenario:

1. Concrete user action
   > Expected observable result

2. Next action
   > 1. First expected result
   > 2. Second expected result
```

## Rules

### Frontmatter
- **id**: Auto-increment integer. Scan existing `.tc.md` files in the target directory for the highest `id` and continue from `max + 1`. If no existing files, start from 1.
- **title**: Concise noun phrase describing what is tested. No dashes or separators needed. If title contains UI element names in quotes, wrap the whole title in single quotes for valid YAML: `title: 'Canceling "Add" dialog discards unsaved data'`. Good: "Add MCP Server with Command connection type", 'Canceling "Add" dialog discards unsaved data'. Bad: "Test login", "Login - verify credentials".
- **priority**: based on impact if the tested behavior fails:
- **priority**: based on user impact if this behavior fails in production (aligned with ISTQB risk-based testing):
  - `critical`: user cannot complete the scenario, no alternative path exists. Or: data loss, money loss, security breach
  - `major`: user can complete the scenario via a different path, but it requires extra steps or knowledge
  - `normal`: user notices the problem but it does not prevent completing the scenario via the current path
  - `low`: user does not notice during normal usage; visible only in rare scenarios or targeted inspection
- **status**: Always `draft` for new cases.
- **tags**: Relevant feature area and test type (smoke, crud, negative, edge-case, etc.).
- **environment**: Only include if the user explicitly asks for it or the test case is environment-specific (e.g., browser-dependent, OS-dependent). Universal test cases omit this field entirely.

### Description
- Optional. Only include if it adds information not already in the title.
- Never start with "This test case verifies" or similar boilerplate. Use a short noun phrase or imperative: "Adding a new MCP server with the Command connection type" or "Verify cancel discards unsaved changes".
- Placed between frontmatter and Preconditions. Omit entirely if the title is self-explanatory.

### Preconditions
- Numbered list of required state before the scenario starts.
- Include auth state, page/screen location, data setup.
- Skip if none required.

### Scenario Steps
- Each step = one concrete user action (click, type, navigate, drag).
- Expected results indented with `> ` under each step.
- Multiple expected results: number them with `> 1.`, `> 2.` etc. Single expected result needs no number.
- A step may omit expected result if it serves only as a navigation path to reach the actual verification point, not as a check itself. But the scenario must always end with an expected result, never with a bare action step.
- Steps must be specific: `Click the "Save" button` not `Save the form`.
- Expected results must be observable: "Success toast appears" not "Data is saved".
- No implementation details - describe what the user sees, not what the system does internally.

### Writing Style
- Action steps in imperative mood: "Click", "Enter", "Navigate to", "Select".
- Expected results describe visible state changes: "Dialog closes", "Table shows new row", "Error message appears".
- Be precise about UI elements: use exact labels, button names, field names, placeholders. Wrap UI element names in quotes: Click the "Save" button, Enter text into the "Name" field.
- One action per step. If a step has two actions, split it.
- Never use em dash (—) in generated test cases. Use regular dash (-) or rewrite the sentence.

## Example

Input: "Test that canceling the add dialog doesn't save data"

Output:

```markdown
---
id: 1
title: 'Canceling "Add" dialog discards unsaved data'
priority: normal
status: draft
tags:
  - crud
  - negative
---

Canceling the "Add" dialog discards all entered data without saving.

Preconditions:

1. User is logged in
2. User is on the list page

Scenario:

1. Click the "Add" button
   > "Add new item" dialog opens

2. Enter data into the Name and Description fields
   > Fields accept input

3. Click the "Cancel" button
   > 1. Dialog closes
   > 2. No new item appears in the list
   > 3. List remains unchanged

4. Reopen the "Add" dialog
   > All fields are empty - previously entered data is not retained
```

## Multiple Test Cases

When a feature needs multiple test cases, generate them as separate files. Name files with kebab-case matching the test scenario: `add-cancel.tc.md`, `add-success.tc.md`, `add-validation.tc.md`.

Prefer end-to-end scenarios that mirror real user workflows. Each test case should tell a story: user starts at point A, performs a sequence of actions, and arrives at a verifiable outcome.

Common test case patterns:
- **Happy path E2E**: complete workflow from entry to confirmed result (e.g., create item, verify it appears in the list, open it, verify content)
- **Negative E2E**: user makes a mistake or encounters an error, recovers, and completes the workflow
- **Cancel/Discard**: user starts a workflow, aborts, and verifies nothing changed
- **Validation**: required fields, format validation, boundary values within a real form submission flow
- **Cross-feature**: actions that span multiple features (e.g., create item, then use it in another context)
- **Permissions**: unauthorized access, role-based visibility across the workflow

## Asking Clarifying Questions

Ask only if the information is not derivable from the provided context (spec, design, ticket):
- Where to save the generated `.tc.md` files?
- What is the starting state if multiple entry points exist?
- Are there specific UI element labels not visible in the provided materials?
