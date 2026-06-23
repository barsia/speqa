---
title: Using Claude Code Skills
---

Speed up test case writing by using AI-powered Claude Code skills designed for your test case project.

## What is a Claude Code Skill?

A Claude Code skill is an AI assistant configured specifically for your test case project. It understands:

- Your project structure and testing context
- SpeQA's `.tc.md` test case format
- Your existing test cases (to maintain consistency)
- Your custom tags, naming conventions, and best practices

Instead of starting from scratch, the skill helps you:

- Write test cases faster with AI suggestions
- Refine and expand test cases
- Review test case quality
- Generate test cases from requirements or user stories
- Cover edge cases and error scenarios

## Setting Up a Skill

### Option 1: Auto-Generated Skill (Recommended)

When you create a new test case project using the wizard:

1. Go to **File > New > Test Case Project**
2. Check **"Create Claude Code Skill"** in the wizard
3. Complete the project creation
4. The skill is automatically generated and ready to use

The auto-generated skill:
- Pre-configured for your project
- Knows about your project structure
- Can analyze your existing test cases

### Option 2: Manual Skill Creation

For an existing project without a skill:

1. Go to **Tools > SpeQA > Create Test Writing Skill**
   - Or: **SpeQA menu > Generate Skill**

2. In the Create Skill dialog:
   - **Skill Name**: Give it a meaningful name (e.g., `login-flow-tests`, `api-tests`)
   - **Description**: What does this skill help with? (e.g., "Write and manage login flow test cases")
   - **Include Existing Tests**: Check this to have the skill analyze your current `.tc.md` files
   - **Project Scope**: Select which folders contain test cases

3. Click **Create Skill**

4. The skill appears in `.claude/skills/` and is ready to use

## Using a Test Writing Skill

### Invoking the Skill

Open Claude Code in your IDE or terminal and use the skill:

```
/[skill-name]
```

Examples:
- `/login-flow-tests`
- `/api-tests`
- `/checkout-tests`

Or ask Claude Code for a specific task:

```
/login-flow-tests Write a test case for password reset flow
```

### Common Tasks

#### Write a New Test Case

```
/my-tests Write a test case for the user registration flow
```

The skill generates a properly formatted `.tc.md` file with:
- Clear title and description
- Step-by-step actions and expected results
- Preconditions
- Appropriate tags

#### Expand an Existing Test Case

```
/my-tests Expand the login test to include error scenarios like wrong password and locked account
```

The skill adds:
- Additional steps for edge cases
- More detailed expected results
- Error handling scenarios

#### Review Test Case Quality

```
/my-tests Review this test case for clarity and completeness: [paste or reference the file]
```

The skill checks:
- Are steps clear and actionable?
- Are expected results measurable?
- Are preconditions included?
- Does it follow project conventions?

#### Generate Tests from Requirements

```
/my-tests Create test cases for this user story: "As a user, I want to reset my password via email"
```

The skill creates multiple test cases covering:
- Happy path (successful reset)
- Error scenarios (invalid email, expired link)
- Edge cases (multiple resets, concurrent requests)

#### Create Related Test Cases

```
/my-tests Based on our login test, create test cases for: logout flow, session timeout, and remember me functionality
```

The skill generates related tests with consistent:
- Naming conventions
- Step structure
- Tags and organization

## File Location and Organization

Skills are stored in your project at:

```
.claude/
  skills/
    [your-project-name].md      # Your test writing skill
    other-skills/               # Other project skills
```

### Using the Generated Skill Files

The skill file (`.md`) contains:

- Project context and conventions
- Examples of your existing test cases
- Instructions for Claude on how to write tests for your project
- Custom metadata about your testing approach

You can edit the skill file directly to:
- Add more context about your project
- Include additional examples
- Update naming conventions
- Refine the style guidelines

## Skill Best Practices

### Keep Skills Updated

As your project evolves:
- Add new test cases to the examples the skill knows about
- Update project conventions in the skill file
- Regenerate the skill if your testing approach changes

### Provide Context

When asking the skill to write tests:
- Include relevant requirements or user stories
- Reference specific features or flows
- Mention any edge cases you care about
- Describe your project's testing priorities

### Review Generated Tests

The skill generates solid starting points, but always:
- Read the generated test cases
- Verify they match your project's style
- Add project-specific details
- Test them to confirm they work

## Integrating Skills into Your Workflow

### Workflow Example

1. **Receive requirement or bug report**
2. **Ask the skill** to generate test cases:
   ```
   /my-tests Create test cases for this issue: [description]
   ```
3. **Review the generated tests** in your IDE
4. **Refine** if needed (edit in SpeQA editor)
5. **Save** to your project
6. **Run the tests** (see [Running Tests](./running-tests.md))
7. **Commit** the test cases to Git

### In Code Review

When reviewing test cases:
```
/my-tests Review these test cases for: coverage, clarity, and maintainability
```

The skill can provide feedback on:
- Whether tests cover the main flow and edge cases
- If steps are clear and unambiguous
- Whether expected results are measurable
- Best practice suggestions

## Troubleshooting

**Skill doesn't appear in Claude Code:**
- Make sure it was created in `.claude/skills/`
- Restart Claude Code or your IDE
- Check the skill file exists and is readable

**Generated test cases don't match my project style:**
- Edit the skill file to include more examples
- Add project-specific guidelines to the skill description
- Regenerate the skill with `Tools > SpeQA > Create Test Writing Skill`

**Skill doesn't know about my existing tests:**
- When creating or regenerating the skill, check **"Include Existing Tests"**
- This allows the skill to analyze and learn from your current test cases

**Can't invoke the skill:**
- Open Claude Code (not a chat, but the IDE's Claude Code feature)
- Make sure you're using the correct skill name: `/skill-name`
- Check that Claude Code is properly installed

## What's Next?

- Learn about [Writing Test Cases](./writing-test-cases.md) to understand the format
- Explore [Test Case Properties](./test-case-properties.md) like attachments and links
- Check out [Running Tests](./running-tests.md) to execute your generated test cases

## Advanced: Customizing Skills

If you want to fully customize a skill:

1. Open `.claude/skills/[skill-name].md` in your IDE
2. Edit the skill instructions and examples
3. Save the file
4. The changes take effect immediately in Claude Code

Common customizations:
- Add examples of your project's test case format
- Include specific naming conventions
- Add project-specific tags
- Document any custom metadata fields
