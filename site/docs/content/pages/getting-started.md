---
title: Getting Started
---

Welcome to SpeQA - a plugin for writing, organizing, and running manual test cases directly in your JetBrains IDE.

SpeQA brings test case management and execution into your IDE with:

- **Markdown editor** for test cases with an interactive preview
- **Multi-case test runs** - record Passed, Failed, Skipped, or Blocked per step across one or more cases in a single run
- **Tool window** - browse, search, and filter test cases and runs without digging through the project tree
- **Built-in links and attachments** - add screenshots and files as test evidence
- **Tags and environment management** - label your tests and runs with environments and tags
- **Claude Code skill for test case generation** - generate test cases from your ticket, requirements, or pull request
- **Git-friendly** - store test cases as `.tc.md` files, just like code

## Quick Path: 5 Minutes to Your First Test

1. [Install SpeQA](./installation.md) from the JetBrains Marketplace (click **Get** on the plugin page)
2. [Create a test case project](./creating-project.md) with the New Project wizard
3. [Write your first test case](./writing-test-cases.md) in the split editor
4. [Run the test](./running-tests.md) and track results
5. [Install the Test Case Writer skill](./claude-code-skills.md) to write test cases faster

## What's Next?

- **Want to automate?** Learn about the [Test Case Writer skill](./claude-code-skills.md)
- **Browsing your suite?** See the [tool window](./tool-window.md)
- **Need more fields?** Explore [Test Case Properties](./test-case-properties.md)

## Key Concepts

**Test Case (`.tc.md`)** - a set of preconditions, input values, execution steps, and expected results used to verify that a system feature works correctly (ISTQB definition). In SpeQA, stored as a Markdown file with structured test data.

**Test Run (`.tr.md`)** - the documented execution of a test case, recording actual results obtained and comparing them with expected results for each step. Includes pass/fail verdicts, comments, and attached evidence (screenshots, logs, etc.).

**Test case writer Claude Code skill** - an AI skill that helps you write test cases faster (optional automation).
