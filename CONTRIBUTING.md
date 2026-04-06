# Contributing to SpeQA

This guide covers the process for submitting changes.

## Issue-first workflow

Every contribution must be linked to a GitHub issue:

1. **Check existing issues** before creating a new one
2. **Create an issue** describing the bug or feature request
3. **Reference the issue** in your PR (e.g., `Fixes #42`)

Pull requests without a linked issue will not be reviewed or merged.

## Development setup

1. Fork and clone the repository, open it in IntelliJ IDEA
2. Create `local.properties` in the project root:
   ```
   ideaPath=/path/to/your/IntelliJ IDEA.app
   ```
3. Use the **Run IDE with Plugin** run configuration to launch a sandbox IDE

## Making changes

1. Create a feature branch from `main`: `git checkout -b feature/short-description`
2. Make your changes
3. Run `./gradlew compileKotlin` and `./gradlew test`
4. Test manually in the sandbox IDE
5. Commit with a clear message describing *why*, not just *what*

## Code guidelines

- **Kotlin** with Compose Multiplatform for UI
- **All user-visible strings** in `src/main/resources/messages/SpeqaBundle.properties`
  (never hardcode text in Kotlin)
- **No hardcoded colors** — use `SpeqaThemeColors` tokens derived from the IDE theme
- **Pointer cursor** on all interactive elements: `clickableWithPointer()`, `SpeqaIconButton`,
  `handOnHover()`
- **Jewel components** preferred for controls (dropdowns, text fields, buttons)
- Follow existing patterns in the codebase

## Pull request process

1. Keep PRs focused — one issue per PR
2. Fill in the PR template (summary, test plan, linked issue)
3. A maintainer will review and may request changes
4. Once approved, a maintainer will merge

## AI-assisted contributions

AI-assisted development is welcome. However, we have specific expectations:

**Quality expectations:**

- Contributors must review their code before submitting, whether written by AI or not
- PRs must follow project conventions. Explore the codebase first to understand patterns. Examples include:
  - UI text: all strings in `SpeqaBundle.properties`, never hardcoded
  - Colors: `SpeqaThemeColors` tokens from UIManager, never hex values
  - Compose: `rememberUpdatedState` for callbacks in long-lived effects,
    `clickableWithPointer` instead of `clickable`
  - Testing: verify both light and dark themes for UI changes
- Code must be readable and understandable by humans
- Commit messages and PR descriptions should be meaningful, not generic AI output
- Keep PRs focused — unrelated improvements don't belong in the same PR. Submit them separately
- Contributors are responsible for checking AI output for security issues
- You must understand and be able to explain every line of code you submit. If asked about your changes during review, "the AI wrote it" is not an acceptable answer

**Reviewable scope:**

- PRs must be reasonably sized for human review
- Large changes should be split into focused, logical PRs
- A PR touching dozens of files with thousands of lines is not reviewable — break it down

**What we will not accept:**

- Unreviewed AI output dumped for maintainers to fix
- Code without tests or with failing compilation
- Changes that ignore project conventions after being pointed to them
- PRs that don't respond to review feedback

PRs that violate these guidelines may be closed without further discussion.

## What we accept

- Bug fixes with clear reproduction steps in the linked issue
- Features discussed in an issue
- Documentation improvements
- Test coverage improvements

## What we don't accept without prior discussion

- Large refactors or architectural changes
- New dependencies
- Changes to the plugin's file format (`.tc.md`, `.tr.md`)
- UI redesigns

## License

By contributing, you agree that your contributions will be licensed under the [Apache License 2.0](LICENSE).
