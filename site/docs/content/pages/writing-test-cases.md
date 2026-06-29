---
title: Writing Your First Test Case
---

Test cases in SpeQA are written in Markdown with an interactive preview form. Learn how to write clear, actionable test cases.

## Opening a Test Case

Open any `.tc.md` file in your project. SpeQA automatically shows the split editor:

- **Left panel**: Raw Markdown source
- **Right panel**: Interactive form preview

## Test Case Structure

A complete test case includes:

### Header metadata

- **Title** - what you are testing (e.g. "User can log in with email and password")
- **Priority** - Critical, Major, Normal, or Low (dropdown)
- **Status** - Draft, Ready, or Deprecated (dropdown)
- **Environment** - where the test runs (e.g. `Chrome 122, macOS`)
- **Tags** - free-form labels for organizing tests (e.g. `smoke`, `regression`)

### Body sections

The preview always shows every section, even when empty, so you can see what is missing. The sections appear in a fixed order:

1. **Description** - context and intent for the tester
2. **Links** - references to tickets, documentation, or related tests
3. **Attachments** - screenshots, logs, or other files
4. **Preconditions** - setup required before execution starts
5. **Scenario** - the numbered steps. Each step has an **Action** (what the tester does) and an optional **Expected result** (what should happen)

See [Test Case Properties](./test-case-properties.md) for tags, environments, links, and attachments in depth.

## Filling in the Form

The right panel is an interactive form, not a read-only render.

- **Metadata** - pick Priority and Status from their dropdowns. For Environment and Tags, click the **+** next to the section to add a value.
- **Description and Preconditions** - click the pencil icon next to the section to edit, and the save (disk) icon (or `Escape` to cancel) to finish. While editing text, a small formatting toolbar offers **bold**, *italic*, ~~strikethrough~~, `inline code`, code blocks, and bullet/numbered lists; you can also add links.
- **Scenario steps** - click **Add step** to create an action and expected-result pair. Inside a step, press `Enter` in the action to jump to its expected result, and `Enter` in the expected result to move to the next step. Use the drag handle on a step to reorder, duplicate, or delete it.

Everything is **saved automatically** as you type. Edits on the left (Markdown) and right (form) stay in sync both ways.

### Inline links

Markdown links inside editable text (descriptions, preconditions, step actions and expected results) render as styled link text with a small open-link icon after them, tinted to the link color.

- **Create a link** - select the text you want to turn into a link, then click the **Link** button on the formatting toolbar (it sits right after the code-block button). A dialog opens with **Text** and **URL** fields; confirm to wrap the selection as a link.
- **Open a link** - click the open-link icon after the link, or hold `Ctrl` (`Cmd` on macOS) and click the link text. The URL opens in your browser.
- **View or edit a link** - click the link text (without a modifier) to open a small popup showing the link text and the URL. Click the URL to open it in the browser, or click **Edit** to reopen the Text + URL dialog and change either value.

## Example Test Case

Here's what a complete test case looks like:

**In the form (right panel):**
- Title: "User can log in with valid credentials"
- Description: "Verify the login flow with email and password"
- Preconditions:
  - User account exists in the system
  - User is logged out
- Steps:
  1. Action: "Navigate to the login page"
     Result: "Login form is displayed with email and password fields"
  2. Action: "Enter email address in the email field"
     Result: "Email is entered and visible in the field"
  3. Action: "Enter password in the password field"
     Result: "Password is entered (shown as dots)"
  4. Action: "Click the 'Sign In' button"
     Result: "User is logged in and redirected to the dashboard"
- Tags: `smoke`, `critical`

## Editing Markdown Directly

You can also edit the raw Markdown on the left panel. The format uses YAML frontmatter for metadata and a plain Markdown body for content:

```markdown
---
id: TC-001
title: "User can log in with valid credentials"
priority: normal
status: ready
tags:
  - smoke
  - critical
---

Brief description of what you're testing.

Preconditions:

- User account exists
- User is logged out

Scenario:

1. Navigate to the login page
   > Login form is displayed with email and password fields

2. Enter email address in the email field
   > Email is entered and visible in the field

3. Click the Sign In button
   > User is logged in and redirected to the dashboard
```

Changes in the Markdown automatically update the form on the right, and vice versa.

## Best Practices

### Write Clear Actions
- Be specific: "Enter 'john@example.com' in the Email field" (good) vs. "Type something" (bad)
- Include UI element names when possible
- Use active voice: "Click the Submit button" not "The Submit button is clicked"

### Write Testable Expected Results
- Be measurable: "Page title shows 'Dashboard'" not "Page looks right"
- Include what should NOT happen: "Error message does not appear"
- Be specific about state: "Email field is disabled" vs. "Field is inactive"

### Use Preconditions
- Set up the testing environment clearly
- Avoid putting preconditions in Step 1
- Example good preconditions:
  - "User account exists and is active"
  - "Browser cache is cleared"
  - "App is in version 2.0 or higher"

### Organize with Tags
- `smoke` - Run first, basic functionality
- `critical` - Must pass before release
- `regression` - Verify old bugs don't return
- `api` - API testing
- `ui` - UI testing
- Add custom tags for your project needs

## Running the Test Case

When the case is ready, click the green **Run** (play) button in the editor header (tooltip: **Start a manual test run**) to execute it and record results. See [Running and Tracking Tests](./running-tests.md).

## Writing Faster with Claude Code

If you have installed the [Test Case Writer skill](./claude-code-skills.md), you can ask Claude Code to draft or refine cases for you:

- "Write a test case for user registration"
- "Expand the login test with error scenarios"
- "Create test cases for the checkout flow"

The skill generates properly formatted `.tc.md` files that you can then refine in the SpeQA editor.

## Saving Test Cases

Test cases are **saved automatically** as you type. The `.tc.md` file is updated in real time.

## What's Next?

Once you've written your test case:

1. [Run the test](./running-tests.md) to see results
2. Add attachments, links, tags, or environments (see [Test Case Properties](./test-case-properties.md))
3. Create additional test cases for other flows

## Tips & Troubleshooting

**Form is empty when I open a new test case:**
- Click in each field to start editing
- The form updates as you type in the Markdown on the left

**Can't add more steps:**
- Click **Add step** below the existing steps
- Each step needs an Action and an optional Expected result

**Markdown shows differently on the form:**
- Some Markdown features (links, bold, code) are preserved
- The form prioritizes readability for test execution

**Want to see examples?**
- Check existing `.tc.md` files in your project
- Look at the Markdown view to understand the format
