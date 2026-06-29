---
title: Test Case Writer Skill for Claude Code
---

SpeQA ships a **Test Case Writer** skill for [Claude Code](https://docs.claude.com/en/docs/claude-code). Once installed in your project, you can ask Claude Code to draft and refine `.tc.md` test cases that follow SpeQA's format, instead of writing every case by hand.

## What the Skill Does

The Test Case Writer skill teaches Claude Code how SpeQA test cases are structured (YAML frontmatter plus a Markdown body with Description, Links, Attachments, Preconditions, and Scenario steps). With it, you can ask Claude Code to:

- Write a new test case from a ticket, user story, requirement, or bug report
- Expand an existing case with edge cases and error scenarios
- Review a case for clarity and completeness
- Keep generated cases consistent with the rest of your suite

The skill treats **Attachments** as user-managed: it never invents attachment files or paths, and it preserves existing attachment references unless you explicitly ask it to change them.

## Installing the Skill

There are two ways to add the skill to a project.

### During project creation

When you create a project with the **Test Cases Project** generator, the New Project dialog includes an **Add Test Case Writer Skill for Claude Code** checkbox (checked by default). Leave it checked and the skill is installed automatically. See [Creating a Test Case Project](./creating-project.md).

### In an existing project

Run **Tools > SpeQA > Add Test Case Writer Skill for Claude Code**. SpeQA copies the bundled skill into your project. If a copy already exists, SpeQA asks before overwriting it, and shows a notification when the skill has been added.

## Where the Skill Lives

The skill is installed at:

```
.claude/
  skills/
    speqa-test-cases/
      SKILL.md
```

Because it lives in your project under `.claude/`, you can commit it to Git so the whole team shares the same skill. You can also open `SKILL.md` and tailor its guidance to your project's conventions; edits take effect the next time Claude Code uses it.

## Using the Skill

Open Claude Code in your IDE or terminal with the project open, then ask for what you need, for example:

- "Write a test case for the password reset flow."
- "Expand the login test with locked-account and wrong-password scenarios."
- "Review this test case for clarity and completeness."

Claude Code applies the Test Case Writer skill and writes the result as `.tc.md` files in your project, which you can then open and refine in the SpeQA editor and [run](./running-tests.md).

## Troubleshooting

**The skill wasn't installed during project creation:**
- Add it later with **Tools > SpeQA > Add Test Case Writer Skill for Claude Code**.

**The menu action is disabled:**
- A project must be open for the action to run.

**Claude Code doesn't seem to use the skill:**
- Confirm `.claude/skills/speqa-test-cases/SKILL.md` exists in the project.
- Restart Claude Code so it picks up newly added skills.

## What's Next?

- Learn the [test case format](./writing-test-cases.md) the skill produces
- [Run](./running-tests.md) the generated cases and track results
- Explore [Test Case Properties](./test-case-properties.md)
