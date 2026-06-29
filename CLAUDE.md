# CLAUDE.md

## User communication

The user uses **voice input** (speech-to-text). Keep in mind:
- Messages may contain transcription artifacts: wrong words, missing punctuation, broken grammar, accidental language mixing (Russian/English)
- Interpret intent, not literal text — if a word looks wrong, consider what the user likely *said* (e.g. "сурепка" → "скрепка", "экспектед" → "expected")
- Do not ask for clarification on obvious transcription errors — fix them silently
- When quoting user instructions back, use the corrected version
- The user may switch between Russian and English mid-sentence — this is normal, not an error

## Required workflow

1. Investigate first when the task is a bug or unexpected behavior. Read the relevant code, reproduce or reason from evidence, and identify the root cause before proposing or applying a fix.
2. Before the first production `.kt` edit for a product or behavior change, update the relevant contract document so it describes the intended current behavior. This update must be meaningful, not a throwaway marker.
3. Use [`docs/specs/2026-04-06-speqa-design.md`](docs/specs/2026-04-06-speqa-design.md) for product, UX, editor behavior, file-format, or user-visible implementation contracts.
4. When a multi-step implementation plan is needed, create or update it under `docs/plans/`. Use a plan only while that work is in flight; do not use it as a completion log, and remove it once the work is shipped and reflected in the spec.
5. For small internal-only fixes that do not change product behavior, UX, file format, public contracts, plugin wiring, or user-visible implementation guarantees, a spec edit is not required. Prefer a focused regression test or a concise code comment near the invariant when that captures the contract better than the PRD.
6. The PRD/specification must describe the current product and implementation state. It must not become a changelog, migration log, or history of edits.
7. After updating any needed contract, change the code so the implementation matches it.
8. Don't add "Generated with Claude Code" or "Co-Authored-By: Claude" to commit messages or PRs

## Documents to keep current

- [`docs/specs/2026-04-06-speqa-design.md`](docs/specs/2026-04-06-speqa-design.md) is the current product and UX specification.
- Active implementation plans live under `docs/plans/`. Keep a plan current only while its work is in flight; once shipped, fold the resulting contract into the spec and remove the plan.

## Engineering rules

- **Never use destructive git commands** (`git checkout --`, `git reset`, `git restore`, `git stash`) to revert changes. Only the user can decide when to reset to a committed state. To undo a specific change, re-edit the file manually — don't touch git history or working tree via git commands.
- Fix root causes, not symptoms. **Never guess at fixes.** When a bug is not obvious, add diagnostic logging first (`Logger.getInstance("SpeqaDebug").warn(...)`) to understand what actually happens, read the logs, then fix based on evidence. Remove debug logging after the fix is confirmed. Multiple blind fix attempts waste time and erode trust.
- Do not keep legacy code, legacy branches in logic, or compatibility support for obsolete formats unless the current specification explicitly requires it.
- Do not break IntelliJ platform expectations: themes, editors, file templates, actions, and icons must behave natively.
- If editor UI behavior changes, verify light and dark themes and the standard Markdown/editor path, not only custom Speqa editors.
- Keep `plugin.xml`, file templates, actions, icons, and Kotlin implementation consistent with each other.
- Do not add non-English (e.g. Russian) comments or non-English text in code. Non-English text is allowed only in localization files.
- Never hardcode user-visible strings in Kotlin code. All UI text (labels, placeholders, tooltips, error messages, empty states) must be defined in `src/main/resources/messages/SpeqaBundle.properties` and accessed via the resource bundle. This enables future localization.
- All interactive elements must show a hand (pointer) cursor on hover. Apply the `handCursor()` extension from [`editor/ui/primitives/HandCursor.kt`](src/main/kotlin/io/github/barsia/speqa/editor/ui/primitives/HandCursor.kt) to any clickable Swing component, and use the `speqaIconButton` helper from [`editor/ui/primitives/SpeqaIconButton.kt`](src/main/kotlin/io/github/barsia/speqa/editor/ui/primitives/SpeqaIconButton.kt) for icon-only action buttons instead of a bare `JButton` or a `JLabel` mouse listener - it already wires the hand cursor, native tooltip, and keyboard activation. Never leave a clickable element with the default cursor.
- Never hardcode color values. All colors must come from `SpeqaThemeColors` (which reads from `EditorColorsManager` and `UIManager`) or directly from IntelliJ `UIManager` / `JBColor` named tokens. For hover backgrounds, surfaces, borders, and foregrounds, use existing `SpeqaThemeColors` tokens (or a `JBColor.namedColor(...)` lookup) that derive from the active editor/UI theme so light and dark themes both render correctly.
- **Delete-focus restoration in list UIs:** For any panel that renders a list of deletable rows plus an add-button (ticket chips, link rows, attachment rows, and future similar surfaces), use `DeleteFocusRestorer` from [`editor/ui/primitives/DeleteFocusRestorer.kt`](src/main/kotlin/io/github/barsia/speqa/editor/ui/primitives/DeleteFocusRestorer.kt) - don't roll per-site focus-restoration logic. Register each row component plus the add-button, then call `restorer.onDeleted(deletedIndex, sizeBefore)` from the row-removal handler. It moves focus to the neighbor (same index if not last, previous if last) or to the add-button when emptying the list, matching the A11y contract the rest of the editor already follows. The pure decision lives in `nextFocusTargetAfterDelete` and is covered by `DeleteFocusRestorerTest`.

## Verification before completion

- Run the smallest sufficient verification after every meaningful change.
- For Kotlin or Gradle changes, the baseline check is `./gradlew compileKotlin`.
- If plugin wiring, templates, editors, or sandbox setup changed, run the relevant additional verification or smoke test.
