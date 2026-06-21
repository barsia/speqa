---
title: Creating a Test Case Project
---

Learn how to set up a new test case project or add test cases to an existing project.

## Method 1: Create a New Test Case Project

The easiest way to start is using the Test Case Project Wizard, which sets up a structured folder for your tests and optionally generates a Claude Code skill for faster test writing.

### Steps

1. In your JetBrains IDE, go to **File > New > Test Case Project**
   - If you don't see this option, make sure SpeQA is installed (see [Installation](./installation.md))

2. The wizard opens with these options:
   - **Project Name**: Enter a name for your test case collection (e.g., `Login Tests`, `API Tests`)
   - **Location**: Choose where to create the project folder (default is your current workspace)
   - **Create Claude Code Skill**: *(Optional)* Check this box to auto-generate a Claude Code skill that helps you write test cases faster
   - **Description**: Add a brief description of what these tests cover

3. Click **Create** or **Finish**

The wizard will:
- Create a new folder with the project name
- Initialize the folder structure
- Generate a `.speqa` configuration file
- If enabled, auto-generate a Claude Code skill for test writing

### Auto-Generated Claude Code Skill

If you checked "Create Claude Code Skill" during project creation:

- A new Claude Code skill is automatically generated in your `.claude/skills/` directory
- The skill is pre-configured to understand your project's test structure
- You can immediately use the skill in Claude Code to write and refine test cases
- The skill learns from your existing test cases in the project

**How to use the auto-generated skill:**
1. Open Claude Code in your IDE or terminal
2. Type `/[your-project-name]-tests` to invoke the skill
3. Ask Claude to write, refine, or review test cases for your project

## Method 2: Add Test Cases to an Existing Project

If you already have a project and want to start writing test cases in it:

### Quick: Single Test Case

1. Right-click any folder in your project (or the project root)
2. Select **New > Test Case**
3. Enter a name for your test case (e.g., `login-flow`)
4. Press **Enter** or click **OK**

SpeQA creates a new `.tc.md` file and opens it in the split editor.

### Full: Create a Skill for Existing Project

To set up a Claude Code skill for test writing in an existing project:

1. In your project, go to **Tools > SpeQA > Create Test Writing Skill**
   - Alternatively: **SpeQA > Generate Skill** from the main menu

2. The Create Skill dialog opens:
   - **Skill Name**: Name for your skill (e.g., `my-project-tests`)
   - **Description**: What this skill helps with (e.g., "Write and manage test cases for the login flow")
   - **Include Existing Tests**: *(Optional)* Check to have the skill analyze your existing `.tc.md` files for context

3. Click **Create Skill**

The skill is generated in `.claude/skills/` and is immediately available for use in Claude Code.

## Project Structure

After creation, your test case project looks like:

```
my-test-project/
  .speqa/
    config.yml          # Project configuration
    metadata.json       # Project metadata
  tests/
    login-flow.tc.md    # Test case files
    checkout.tc.md
    ...
  test-runs/            # Automatically created when tests are run
    login-flow-2024-01-15.tr.md
    ...
```

- **`.tc.md` files** contain your test case definitions (Markdown format)
- **`.tr.md` files** contain test run records with pass/fail results
- The `.speqa/` folder stores project configuration

## What's Next?

Now that you've created a test case project:

1. [Write your first test case](./writing-test-cases.md)
2. [Learn to run and track tests](./running-tests.md)
3. *(Optional)* [Use Claude Code skill to speed up test writing](./claude-code-skills.md)
4. Explore [Advanced Features](./advanced-features.md) like attachments and links

## Troubleshooting

**"New > Test Case Project" option doesn't appear:**
- Check that SpeQA is installed (see [Installation](./installation.md))
- Restart the IDE if you just installed the plugin

**Skill didn't generate when creating the project:**
- You can manually create a skill later using **Tools > SpeQA > Create Test Writing Skill**
- See [Using Claude Code Skills](./claude-code-skills.md) for details

**Can't find the created project:**
- The project folder is created at the location you specified in the wizard
- Check your file explorer or IDE's project view
