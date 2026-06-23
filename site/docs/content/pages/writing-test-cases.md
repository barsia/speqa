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

### Required Fields

**Title** - What are you testing?
- Example: "User can log in with email and password"

**Description** - Brief context (optional but recommended)
- Example: "Verify login flow with valid credentials"

**Scenario/Steps** - What actions does the tester perform?
- Each step includes:
  - **Action**: What the tester does (e.g., "Enter email in the email field")
  - **Expected Result**: What should happen (e.g., "Email is accepted without error")

### Optional Fields

- **Preconditions**: Setup required before testing (e.g., "User account exists", "Logged out of app")
- **Attachments**: Screenshots, files, or recordings
- **Links**: References to tickets, documentation, or related tests
- **Tags**: Labels for organizing tests (e.g., `smoke`, `critical`, `regression`)

## Filling in the Form

In the right panel, fill in each field:

1. **Click the field** to edit it
2. **Enter your content** - most fields support Markdown formatting
3. **For Steps**:
   - Click **Add Step** to create a new action/result pair
   - Enter the action (what the tester does)
   - Enter the expected result (what should happen)
   - Click the **+** button to add more steps

4. Save automatically as you type (no save button needed)

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
priority: Medium
status: Active
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

## Using Claude Code Skill

If you have a Claude Code skill for your project (see [Creating a Test Case Project](./creating-project.md)), you can speed up test writing:

1. Type `/[project-name]-tests` in Claude Code
2. Ask Claude to write or refine test cases:
   - "Write a test case for user registration"
   - "Expand the login test with error scenarios"
   - "Create test cases for the checkout flow"

The skill understands your project structure and generates test cases in SpeQA format.

## Saving Test Cases

Test cases are **saved automatically** as you type. The `.tc.md` file is updated in real-time.

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
- Click **Add Step** or the **+** button below the existing steps
- Each step needs an Action and an Expected Result

**Markdown shows differently on the form:**
- Some Markdown features (links, bold, code) are preserved
- The form prioritizes readability for test execution

**Want to see examples?**
- Check existing `.tc.md` files in your project
- Look at the Markdown view to understand the format
