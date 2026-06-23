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

**Format - plain prose bullets, present tense, no full stops:**

```markdown
## 0.1.7

- Backspace on a step expected result line removes the full `> ` prefix in one keystroke
- Enter continues a numbered list inside an expected result block
- Native editor uses soft wrap by default for `.tc.md` and `.tr.md` files
```

Rules:
- One line per user-visible change; merge related tiny fixes into one entry
- No implementation details, no PR/commit references
- Match the tone and length of existing entries (see 0.1.4-0.1.6 in CHANGELOG.md)

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

- **0.1.6:** "Changing a step's result during a test run keeps the rest of the steps intact"
- **0.1.5:** "Duplicate test case IDs are highlighted as you type"
- **0.1.4:** "Preview no longer jumps or flashes while you type"

Short, present-tense, describes what the user now experiences.
