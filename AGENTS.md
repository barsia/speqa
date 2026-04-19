# AGENTS.md

## User communication

The user uses **voice input** (speech-to-text). Keep in mind:
- Messages may contain transcription artifacts: wrong words, missing punctuation, broken grammar, accidental language mixing (Russian/English)
- Interpret intent, not literal text — if a word looks wrong, consider what the user likely *said* (e.g. "сурепка" → "скрепка", "экспектед" → "expected")
- Do not ask for clarification on obvious transcription errors — fix them silently
- When quoting user instructions back, use the corrected version
- The user may switch between Russian and English mid-sentence — this is normal, not an error

## Required workflow

1. Start by editing the PRD/specification.
2. The PRD/specification must always describe the current product and implementation state.
3. The PRD/specification must not become a changelog, migration log, or history of edits.
4. After updating the PRD/spec, change the code so the implementation matches it.
5. **Spec-first per session.** The spec must be edited at least once before the first `.kt` edit in a session. A PreToolUse hook enforces this: it blocks `.kt` edits until the spec has been touched. The marker persists for the rest of the session — subsequent `.kt` edits don't require additional spec edits. Make the spec edit meaningful: describe what is changing and why, not a throwaway micro-addition.
6. Don't add "Generated with Claude Code" or "Co-Authored-By: Claude" to commit messages or PRs

## Documents to keep current

- [`docs/specs/2026-04-06-speqa-design.md`](docs/specs/2026-04-06-speqa-design.md) is the current product and UX specification.
- [`docs/plans/2026-04-06-speqa-implementation.md`](docs/plans/2026-04-06-speqa-implementation.md) is the implementation plan. Update it only when the plan changes, not as a completion log.

## Engineering rules

- **Never use destructive git commands** (`git checkout --`, `git reset`, `git restore`, `git stash`) to revert changes. Only the user can decide when to reset to a committed state. To undo a specific change, re-edit the file manually — don't touch git history or working tree via git commands.
- Fix root causes, not symptoms. **Never guess at fixes.** When a bug is not obvious, add diagnostic logging first (`Logger.getInstance("SpeqaDebug").warn(...)`) to understand what actually happens, read the logs, then fix based on evidence. Remove debug logging after the fix is confirmed. Multiple blind fix attempts waste time and erode trust.
- Do not keep legacy code, legacy branches in logic, or compatibility support for obsolete formats unless the current specification explicitly requires it.
- Do not break IntelliJ platform expectations: themes, editors, file templates, actions, and icons must behave natively.
- If editor UI behavior changes, verify light and dark themes and the standard Markdown/editor path, not only custom Speqa editors.
- Keep `plugin.xml`, file templates, actions, icons, and Kotlin implementation consistent with each other.
- Do not add non-English (e.g. Russian) comments or non-English text in code. Non-English text is allowed only in localization files.
- Never hardcode user-visible strings in Kotlin code. All UI text (labels, placeholders, tooltips, error messages, empty states) must be defined in `src/main/resources/messages/SpeqaBundle.properties` and accessed via the resource bundle. This enables future localization.
- All interactive elements must show a hand (pointer) cursor on hover. Use `Modifier.clickableWithPointer()` instead of `clickable()`, `SpeqaIconButton` instead of `IconButton`, and `Modifier.handOnHover()` on Jewel components that have their own click handling (e.g. `ListComboBox`). Never use bare `clickable` or `IconButton` in Speqa UI code.
- Use Jewel theme colors and IntelliJ UIManager colors — never hardcode color values. All colors must come from `SpeqaThemeColors` (which reads from `EditorColorsManager` and `UIManager`) or from Jewel theme tokens. For hover backgrounds, use `SpeqaThemeColors.actionHover` (reads `ActionButton.hoverBackground` from UIManager) to match native IntelliJ button hover. For surfaces, borders, foregrounds — use existing `SpeqaThemeColors` tokens that derive from the active editor/UI theme.
- **Stale closure prevention in Compose effects:** Any callback parameter or external value captured inside `LaunchedEffect`, `snapshotFlow.collect`, `DisposableEffect`, or `SideEffect` with a stable key (e.g., `remember`-ed object, `Unit`) MUST be wrapped with `rememberUpdatedState`. Without it, the closure captures the value from first composition and never updates, causing data loss/reverts. Pattern: `val currentCallback by rememberUpdatedState(callback)` then use `currentCallback` inside the effect. This applies to `onValueChange`, `onTestCaseChange`, `onFocusConsumed`, and any lambda parameter used in long-lived coroutines. Stale closures in `forEachIndexed` loops have the same issue — use `rememberUpdatedState` for the parent state and read `.value` inside lambdas.

## Verification before completion

- Run the smallest sufficient verification after every meaningful change.
- For Kotlin or Gradle changes, the baseline check is `./gradlew compileKotlin`.
- If plugin wiring, templates, editors, or sandbox setup changed, run the relevant additional verification or smoke test.
