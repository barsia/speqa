---
title: Advanced Features
---

Enhance your test cases with attachments, links, comprehensive tagging, and other powerful SpeQA features.

## Attachments

Add screenshots, videos, files, and other evidence to your test cases and test runs.

### Adding Attachments to Test Cases

During a test run, attach evidence for each step:

1. After marking a step (Passed, Failed, or Skipped)
2. Click **Add Attachment** (or camera icon)
3. Choose the attachment type:
   - **Screenshot**: Capture your screen, paste from clipboard, or upload an image
   - **File**: Attach logs, error reports, or other files
   - **Video**: Record or upload a screen recording (if supported)

4. Add an optional caption describing what the attachment shows
5. The attachment is linked to that specific test step

### Using Attachments

Attachments in test runs help:

- Document unexpected UI states when a test fails
- Show error messages and stack traces
- Provide visual proof that a feature works
- Record videos of complex user flows
- Attach logs from test execution

### Viewing Attachments

In a `.tr.md` test run file:
- Attachments appear under each step
- Click to view or download
- Attachments are embedded or linked in the Markdown

## Links

Reference related tickets, documentation, or other test cases within your test case.

### Adding Links

In the test case editor:

1. Open the Links section (if available in your IDE)
2. Click **Add Link**
3. Enter:
   - **URL**: Link to ticket, doc, or web page (e.g., `https://tickets.example.com/TICKET-123`)
   - **Type**: Choose a link type:
     - `relates to` - General relationship
     - `blocks` - This test is blocked by that issue
     - `depends on` - This test depends on something else
     - `verifies` - This test verifies that issue
   - **Label**: Short name for the link (optional)

4. Click **Add** or **Save**

Or edit the Markdown directly:

```markdown
## Links
- [TICKET-123: Login flow requirements](https://tickets.example.com/TICKET-123) - verifies
- [API Documentation](https://docs.api.example.com/auth) - relates to
```

### Using Links

Links help:

- Track which features/bugs are tested by each test case
- See dependencies between tests and tickets
- Navigate to related documentation
- Understand business requirements being tested
- Find blocked tests when a dependency fails

## Tags and Organization

Tags help organize and categorize your test cases.

### Common Tags

**Test Type**
- `ui` - User interface testing
- `api` - API testing
- `integration` - System integration testing
- `performance` - Performance testing
- `security` - Security testing

**Priority**
- `critical` - Must pass before release
- `high` - Important functionality
- `medium` - Standard feature
- `low` - Nice-to-have

**Scope**
- `smoke` - Quick sanity checks (run first)
- `regression` - Verify bugs don't return
- `new-feature` - Tests for new functionality
- `edge-case` - Unusual or boundary conditions

**Status**
- `wip` - Work in progress
- `todo` - Not yet implemented
- `blocked` - Can't test (dependency issue)
- `manual-only` - Cannot be automated

### Creating Custom Tags

Create tags specific to your project:

- `login-flow`, `checkout-flow`, `payment` - Feature areas
- `mobile`, `desktop`, `web` - Platforms
- `firefox`, `chrome`, `safari` - Browsers
- `v2.0`, `v2.1` - Product versions
- `team-payments`, `team-auth` - Teams responsible
- `customer-reported` - Source of test case

### Using Tags in Test Cases

In the editor:

1. Scroll to the **Tags** section
2. Click **Add Tag** or type directly
3. Enter tag name (no spaces, use hyphens: `smoke-test`)
4. Click to add

Or in Markdown:

```markdown
## Tags
`smoke` `critical` `login-flow` `v2.0`
```

### Filtering by Tags

If your IDE supports it:

1. Go to **Tools > SpeQA > Filter Tests**
2. Select tags to show/hide
3. View only test cases matching your filters

Useful filters:
- Show `critical` tests before release
- Show `smoke` tests for quick sanity checks
- Show `blocked` tests to track dependencies
- Show tests for a specific feature area

## Advanced Markdown Features

SpeQA test cases are just Markdown files, so you can use powerful formatting:

### Rich Text Formatting

```markdown
**Bold** for emphasis, *italic* for notes, `code` for technical terms
```

### Code Blocks

```markdown
### Step 1
- Action: Open browser and navigate to `https://example.com/login`
- Expected Result:
  ```
  GET https://example.com/login -> 200 OK
  Page loads with title "Sign In"
  ```
```

### Lists and Nested Content

```markdown
## Preconditions
1. User account created with email: john@example.com
2. Database verified (run migrations):
   - `npm run migrate:latest`
   - Check: `SELECT COUNT(*) FROM users` = 5 rows
3. Server running locally
```

### Tables

```markdown
## Test Data
| Field | Value | Notes |
|-------|-------|-------|
| Email | john@example.com | Valid format |
| Password | Test!123456 | Must be 8+ chars |
| Name | John Doe | Optional during signup |
```

## Test Case Templates

Create reusable templates for common test flows:

### Template Files

Store templates in your project:

```
tests/
  templates/
    login-template.tc.md
    api-flow-template.tc.md
    form-submission-template.tc.md
```

### Using Templates

1. Copy a template file with a new name
2. Edit the specific details for your test
3. Save as a new test case

Or use Claude Code skill:

```
/my-tests Create a test case based on the login-template.tc.md, but for password reset
```

## Organizing Large Test Projects

For projects with many test cases:

### Folder Structure

```
tests/
  authentication/
    login.tc.md
    logout.tc.md
    password-reset.tc.md
  checkout/
    cart-flow.tc.md
    payment-processing.tc.md
    order-confirmation.tc.md
  api/
    get-users.tc.md
    create-user.tc.md
```

### Naming Conventions

- Use kebab-case: `login-flow.tc.md` not `Login Flow.tc.md`
- Be descriptive: `user-registration-with-email-verification.tc.md`
- Include context: `api-get-user-by-id.tc.md`

### Cross-References

Link related test cases:

```markdown
## Related Tests
- [Authentication > Login](../authentication/login.md) - verifies
- [API > Get Users](../api/get-users.md) - depends-on
```

## Performance Optimization

For large test suites:

### Organize by Execution Time

Tag tests by how long they take:

- `smoke` - < 1 minute
- `quick` - 1-5 minutes
- `medium` - 5-15 minutes
- `slow` - > 15 minutes

Run smoke tests first for quick feedback.

### Parallel Test Execution

If your IDE supports it:

1. Group independent tests
2. Run groups in parallel
3. Combine results

## Reporting and Analytics

### Test Run Analysis

SpeQA tracks:

- **Pass rate**: Percentage of steps passing
- **Flaky tests**: Steps that pass sometimes, fail other times
- **Slow tests**: Steps taking longer than expected
- **Failure trends**: Which tests fail most often
- **Coverage**: Which features have test cases

### Generating Reports

Export test data for reporting:

1. Open a test case or test run
2. **File > Export > As JSON** or **As HTML**
3. Use in dashboards or reports

## Troubleshooting Advanced Features

**Attachments won't upload:**
- Check file size (limit depends on IDE)
- Ensure file format is supported
- Try a smaller image or different file

**Links don't work:**
- Verify URL format
- Check that referenced issues exist
- Make sure URL is accessible from your IDE

**Custom tags don't appear in filters:**
- Tags must be added to test cases first
- Try restarting the IDE
- Check IDE supports tag filtering

**Markdown formatting doesn't display correctly:**
- Some IDEs support different Markdown flavors
- Stick to basic Markdown for compatibility
- Test in the preview to verify rendering

## What's Next?

- Review [Running Tests](./running-tests.md) to collect evidence
- Explore [Claude Code Skills](./claude-code-skills.md) to generate test cases faster
- Share your test cases - they're just Markdown files in Git!
