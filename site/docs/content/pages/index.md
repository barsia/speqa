---
title: Getting Started
---

Welcome to SpeQA - a plugin for writing, organizing, and running manual test cases directly in your JetBrains IDE.

SpeQA brings test case management and execution into your IDE with:

- **Markdown editor** for test cases with interactive preview
- **Visual test run tracking** - mark steps as Passed, Failed, or Skipped in interactive preview
- **Built-in links and attachments** - add screenshots and files to test evidence
- **Tags and environment manegement** - mark your tests and runs with corresponding env and tags
- **Claude Code skill for test cases generation** - automatically generate test cases rom your ticket, requirements or Pull request
- **Git-friendly** - store test cases as `.tc.md` files, just like a code

## Quick Path: 5 Minutes to Your First Test

1. [Install SpeQA](./installation.md) from the JetBrains Marketplace (click **Get** directly from plugin page)
2. [Create a test case project](./creating-project.md) using the Welcome wizard
3. [Write your first test case](./writing-test-cases.md) in the split editor
4. [Run the test](./running-tests.md) and track results
5. [Generate a Claude Code skill](./claude-code-skills.md) to write test cases faster

## What's Next?

- **Want to automate?** Learn about [Claude Code Skills](./claude-code-skills.md)
- **Need power features?** Explore [Advanced Features](./advanced-features.md)

## Key Concepts

**Test Case (`.tc.md`)** - a set of preconditions, input values, execution steps, and expected results used to verify that a system feature works correctly (ISTQB definition). In SpeQA, stored as a Markdown file with structured test data.

**Test Run (`.tr.md`)** - the documented execution of a test case, recording actual results obtained and comparing them with expected results for each step. Includes pass/fail verdicts, comments, and attached evidence (screenshots, logs, etc.).

**Test case writer Claude Code skill** - an AI skill that helps you write test cases faster (optional automation).
