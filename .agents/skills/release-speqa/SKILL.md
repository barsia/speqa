---
name: release-speqa
description: Use when the user asks to prepare/cut/publish a new SpeQA plugin release or bump the version.
user-invocable: true
---

# Releasing SpeQA

## Steps

### 1. Determine what's in the release

```bash
git log v0.X.Y..HEAD --oneline -- src/
```

Read each commit. Translate to user-facing language: what the user sees, not what the code does.

### 2. Bump the version

In `build.gradle.kts`, line `version = "X.Y.Z"` to the next minor.

### 3. Update CHANGELOG.md

Prepend a new section at the top, after the `# Changelog` heading.

**Format - plain prose bullets, no full stops:**

```markdown
## 0.1.8

- Soft wrap is now enabled by default in the Markdown editor for test cases and test runs
- Fixed selecting text inside a code block
- Fixed backspace on empty lines inside code blocks
- Hover over a code block to reveal a copy button
- Removing a link no longer asks for confirmation
```

Rules:
- One line per user-visible change; merge related tiny fixes into one entry
- No implementation details, no PR/commit references
- New features / behavior changes: describe what the user now experiences in present tense - "X now does Y", "Y is now enabled by default", "Hover over X to reveal Y"
- Bug fixes: start with "Fixed" followed by a gerund or noun phrase - "Fixed selecting text inside...", "Fixed backspace on empty lines..."
- Removals / simplifications: use "no longer" - "Removing a link no longer asks for confirmation"
- No "Fixed: " with a colon, no capitalisation after "Fixed", no trailing period

### 4. Update changeNotes in build.gradle.kts

Find the `changeNotes = """` block inside `intellijPlatform { pluginConfiguration { ... } }` and replace its content.

**Format - HTML, same entries as CHANGELOG but wrapped in a `<ul>`:**

```kotlin
changeNotes = """
    <h3>0.1.7</h3>
    <ul>
        <li>Backspace un-quotes a blockquote expected-result line in one keystroke</li>
        <li>Enter continues a numbered list inside an expected result block</li>
        <li>Native editor uses soft wrap by default for <code>.tc.md</code> files</li>
    </ul>
""".trimIndent()
```

Rules:
- Use `<code>` for file names, key sequences, and inline syntax
- Escape `>` as `&gt;` inside `<li>` when it appears as a character
- Only the current release's notes go in `changeNotes` (JetBrains Marketplace shows this as "What's New")

### 5. Verify the plugin

```bash
./gradlew verifyPlugin
```

Fix any reported issues before tagging. Common failures: internal API usage, missing plugin.xml declarations. If `verifyPlugin` passes, proceed.

### 6. Commit and tag

```bash
git add build.gradle.kts CHANGELOG.md
git commit -m "chore: release 0.1.7"
git tag v0.1.7
```

Do **not** push unless explicitly asked.

---

## What goes in release notes vs what doesn't

| Include                         | Omit                                       |
|---------------------------------|--------------------------------------------|
| User-visible behavior changes   | Internal refactors                         |
| New features                    | Test-only commits                          |
| Fixed bugs the user could hit   | Spec/doc-only changes                      |
| UX/editor behavior improvements | Performance wins with no observable effect |

## Tone reference (from existing releases)

New features / behavior:
- "Soft wrap is now enabled by default in the Markdown editor for test cases and test runs"
- "Duplicate test case IDs are highlighted as you type"
- "Hover over a code block to reveal a copy button"

Bug fixes:
- "Fixed selecting text inside a code block"
- "Fixed backspace on empty lines inside code blocks"

Removals / simplifications:
- "Removing a link no longer asks for confirmation"
- "Preview no longer jumps or flashes while you type"

Short, concrete, describes what the user now experiences. No trailing periods.
