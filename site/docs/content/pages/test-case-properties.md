---
title: Test Case Properties
---

Beyond the step-by-step scenario, a test case can carry metadata that helps you organize and navigate your test suite: tags, environments, external links, and file attachments.

## Tags

Tags are free-form labels you add to a test case for filtering and organization. Unlike Priority and Status (which are dedicated fields with fixed values), tags are entirely user-defined.

Common patterns:

- **Feature area**: `login-flow`, `checkout`, `payment`, `search`
- **Scope**: `smoke`, `regression`, `edge-case`
- **Platform**: `mobile`, `desktop`, `web`, `ios`, `android`
- **Team**: `team-auth`, `team-payments`

In the test case editor, scroll to the **Tags** section and type a tag name. Use hyphens instead of spaces: `smoke-test`, not `smoke test`.

In Markdown:

```markdown
tags: smoke, regression, login-flow
```

## Environments

Environments let you specify which infrastructure or configuration a test case targets. Where tags describe *what* is being tested, environments describe *where*: the deployment target, browser, device, or any runtime condition that affects how the test runs.

Common patterns:

- **Deployment stage**: `staging`, `production`, `dev`, `qa`
- **Browser**: `chrome`, `firefox`, `safari`, `edge`
- **Platform**: `ios`, `android`, `linux`, `windows`
- **Data set**: `empty-db`, `seeded`, `migrated`

In the test case editor, scroll to the **Environment** section and type a value. Multiple environments can be added to the same test case.

In Markdown:

```markdown
environment: staging, chrome
```

## Links

Links connect a test case to external resources such as bug tracker tickets or documentation pages.

Each link has a **title** and a **URL**. Click **Add link** in the preview and fill in both fields, or edit the Markdown directly:

```markdown
## Links
- [TICKET-123: Login requirements](https://tracker.example.com/TICKET-123)
- [API docs](https://docs.example.com/auth)
```

Links are useful for tracing which requirement or bug report a test case covers, and for navigating directly to the relevant ticket from within the IDE.

## Attachments

Attachments are files stored alongside the test case - screenshots, logs, design documents, or anything else relevant to the test.

Drag a file onto the attachments area in the preview, or click **Attach file**. SpeQA copies the file into an `attachments/` folder next to the test case and records the relative path in the Markdown.

Step-level attachments work the same way and are attached to a specific step rather than the test case as a whole.
