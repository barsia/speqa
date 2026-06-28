---
id: 5
title: Create a SpeQA project with the New Project wizard
priority: major
status: draft
tags:
  - wizard
  - smoke
---

The "Test Cases Project" generator scaffolds the SpeQA folder layout and installs the test-case skill into the new project.

Preconditions:

1. The SpeQA plugin is installed

Scenario:

1. 1. Open "File | New | Project"
   2. Select the "Test Cases Project" generator
   > The generator step shows an "Add Test Case Writer Skill for Claude Code" checkbox, selected by default

2. 1. Enter a project name and location
   2. Keep "Add Test Case Writer Skill for Claude Code" checked
   3. Click "Create"
   > 1. A "test-cases/smoke/" folder is created with a starter "plugin-installation.tc.md"
   > 2. A "test-runs/" folder is created
   > 3. The skill is installed at ".claude/skills/speqa-test-cases/SKILL.md"
