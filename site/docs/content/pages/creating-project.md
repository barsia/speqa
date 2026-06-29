---
title: Creating a Test Case Project
---

Learn how to set up a new test case project or add test cases to an existing project.

## Method 1: Create a New Test Case Project

The quickest way to start is the **Test Cases Project** generator in the New Project wizard. It scaffolds the folder layout SpeQA expects and can install the Test Case Writer skill for Claude Code.

### Steps

1. In your JetBrains IDE, open **File > New > Project**.
2. In the list of project generators on the left, choose **Test Cases Project**.
   - If you don't see it, make sure SpeQA is installed (see [Installation](./installation.md)).
3. Set the **project name** and **location** in the standard fields.
4. Optional checkboxes:
   - **Add Test Case Writer Skill for Claude Code** (checked by default) - installs the [Test Case Writer skill](./claude-code-skills.md) into the project.
   - **Create Git repository** - initializes a Git repo for the project.
5. Click **Create**.

SpeQA scaffolds the project and opens the bundled sample test case (`sample-login.tc.md`) so you can see a complete example right away.

### Project Structure

A freshly created project looks like this:

```
my-test-project/
  test-cases/
    sample-login.tc.md     # bundled sample test case
  test-runs/               # empty, fills up as you create runs
```

- **`test-cases/`** holds your `.tc.md` test case files.
- **`test-runs/`** holds `.tr.md` run files. It starts empty; runs are added when you [create a test run](./running-tests.md).
- The sample test case demonstrates every field: title, priority, status, environment, tags, preconditions, and steps with expected results.

If you also enabled the skill, you'll find it at `.claude/skills/speqa-test-cases/SKILL.md`.

## Method 2: Add Test Cases to an Existing Project

You don't need the wizard to use SpeQA. Any folder works.

### Create a single test case

1. Right-click a folder in your project (or the project root).
2. Choose **New > Test Case**.
3. Enter a name and confirm.

SpeQA creates a `.tc.md` file from the built-in template, assigns the next free ID, and opens it in the split editor. You can also create a test case from the SpeQA tool window's **TCs** tab using its **+** action.

### Add the Test Case Writer skill

To install the Claude Code skill into an existing project, run **Tools > SpeQA > Add Test Case Writer Skill for Claude Code**. See [Test Case Writer Skill](./claude-code-skills.md) for details.

## What's Next?

1. [Write your first test case](./writing-test-cases.md)
2. [Run a test and track results](./running-tests.md)
3. Browse and filter your suite in the [SpeQA tool window](./tool-window.md)
4. Add [tags, environments, links, and attachments](./test-case-properties.md)

## Troubleshooting

**"Test Cases Project" doesn't appear in New Project:**
- Check that SpeQA is installed (see [Installation](./installation.md)).
- Restart the IDE if you just installed the plugin.

**The skill wasn't created with the project:**
- Add it later with **Tools > SpeQA > Add Test Case Writer Skill for Claude Code**.

**Can't find the created project:**
- The folder is created at the location you set in the wizard. Check your file explorer or the IDE's project view.
