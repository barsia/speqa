# Keyboard Navigation Fixes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make keyboard navigation in the SpeQA preview editor correct and coherent: multi-case runs keep focus, Tab moves between fields in the preview, chips/rows follow the Model-A interaction model (element is the Tab stop; Delete/F2/Enter act on it), focus is restored after delete, and dialogs/choosers return focus to their trigger.

**Architecture:** Five independent areas. Tasks 1 and 2 are isolated low-risk fixes against contracts that already exist in the spec (lines 56 and 813). Tasks 3 and 4 implement the approved Model-A sub-action model (spec "Chip/row sub-action keyboard model"). Task 5 fixes delete focus-restoration timing. Task 6 fixes dialog/chooser focus return. Swing focus traversal cannot be unit-tested, so each Swing change ends with an explicit manual IDE verification; pure decision helpers get JUnit tests.

**Tech Stack:** Kotlin, IntelliJ Platform SDK (Swing, `EditorTextField`/`EditorEx`, `ActionButton`, `AccessibleContext`), JUnit 4, Gradle (`./gradlew compileKotlin`, `./gradlew test`). Branch: current `main` (no worktree). Commits per task; do not push.

**Spec:** `docs/specs/2026-04-06-speqa-design.md` (contracts: "Chip/row sub-action keyboard model", "Dialog and chooser focus restoration", "Delete focus restoration timing", line 56 focus-cycle-root rule, line 813 preview-Tab rule).

**Cross-cutting note on the glyph-exclusion mechanism:** The spec mentions `SPEQA_EXCLUDE_FROM_TAB_CHAIN`. This plan instead sets `isFocusable = false` on the edit pencil and remove `X`, because that removes them from BOTH the Tab chain AND the accessibility focus tree (the consultation requires they not be separate a11y nodes), which the client-property approach does not. Task 7 aligns the spec wording.

---

### Task 1: Multi-case run keeps focus (focus cycle root)

Root cause: `RunMultiCasePanel` never sets `isFocusCycleRoot` / `focusTraversalPolicy`, so Tab is handled by the IDE's ancestor policy and leaves the editor. `TestCasePanel.kt:443-456` is the working reference.

**Files:**
- Modify: `src/main/kotlin/io/github/barsia/speqa/editor/ui/run/RunMultiCasePanel.kt` (the `init` block, around line 160-168)

- [ ] **Step 1: Add focus-cycle-root config to `RunMultiCasePanel.init`**

In the `init` block (after the existing `layout`/`border`/`buildLayout()` lines), add:

```kotlin
focusTraversalPolicy = io.github.barsia.speqa.editor.ui.primitives.SpeqaFocusTraversalPolicy()
isFocusCycleRoot = true
isFocusable = true
addMouseListener(object : java.awt.event.MouseAdapter() {
    override fun mousePressed(e: java.awt.event.MouseEvent) {
        requestFocusInWindow()
    }
})
```

(Prefer adding proper top-of-file imports for `SpeqaFocusTraversalPolicy`, `MouseAdapter`, `MouseEvent` instead of FQNs, matching the file's import style.)

- [ ] **Step 2: Compile**

Run: `./gradlew compileKotlin 2>&1 | grep -E "^e:|BUILD" | tail -5`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: IDE verification (manual)**

Open a test run with 2+ cases (multi-case / sectioned view). Click into the panel and press Tab repeatedly. Expected: focus cycles among the run header fields, the case expand/collapse controls, result pills, and expanded case bodies, and WRAPS within the editor instead of leaving it. Shift+Tab cycles backward. Collapsed case bodies are skipped.

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/io/github/barsia/speqa/editor/ui/run/RunMultiCasePanel.kt
git commit -m "fix: keep keyboard focus inside multi-case test run editor"
```

---

### Task 2: Tab moves between fields in the preview (not indent)

Root cause: `MarkdownEditablePane` embeds a multi-line `EditorTextField` that disables Tab focus-traversal; the pane installs Enter/Backspace/Delete handlers but none for Tab, so Tab falls through to the platform `EditorTab` (indent) action. `SpeqaTabActionHandler`/`ListIndent` are correctly scoped to real `.tc.md`/`.tr.md` source files and are NOT involved here.

**Files:**
- Modify: `src/main/kotlin/io/github/barsia/speqa/editor/ui/primitives/MarkdownEditablePane.kt` (`createEditor()`, around line 112-137; mirror `installListContinuation` around line 451-474)

- [ ] **Step 1: Call a new `installTabFocusTraversal(editor)` from `createEditor()`**

In `createEditor()`, immediately after the existing `installListContinuation(editor)` call, add:

```kotlin
installTabFocusTraversal(editor)
```

- [ ] **Step 2: Implement `installTabFocusTraversal`**

Add a private function modeled on `installListContinuation` (which uses `registerCustomShortcutSet` on `editor.contentComponent`):

```kotlin
private fun installTabFocusTraversal(editor: EditorEx) {
    val forward = object : com.intellij.openapi.actionSystem.AnAction() {
        override fun actionPerformed(e: com.intellij.openapi.actionSystem.AnActionEvent) {
            editor.contentComponent.transferFocus()
        }
    }
    forward.registerCustomShortcutSet(
        com.intellij.openapi.actionSystem.CustomShortcutSet.fromString("TAB"),
        editor.contentComponent,
    )
    val backward = object : com.intellij.openapi.actionSystem.AnAction() {
        override fun actionPerformed(e: com.intellij.openapi.actionSystem.AnActionEvent) {
            editor.contentComponent.transferFocusBackward()
        }
    }
    backward.registerCustomShortcutSet(
        com.intellij.openapi.actionSystem.CustomShortcutSet.fromString("shift TAB"),
        editor.contentComponent,
    )
}
```

(Use the file's existing import style; `EditorEx` is already imported. Binding only plain `TAB`/`shift TAB` leaves Ctrl+Tab Switcher untouched.)

- [ ] **Step 3: Compile**

Run: `./gradlew compileKotlin 2>&1 | grep -E "^e:|BUILD" | tail -5`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: IDE verification (manual)**

In a test case: focus a step Action field, press Tab. Expected: focus moves to the next field (Expected), Shift+Tab moves back; same for the run Comment field and body Description/Preconditions. In the raw `.tc.md` source editor (left pane), Tab still indents list items (unchanged). Enter still auto-continues lists in the preview (unchanged).

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/io/github/barsia/speqa/editor/ui/primitives/MarkdownEditablePane.kt
git commit -m "fix: Tab navigates between fields in the preview instead of indenting"
```

---

### Task 3: Model A for tag/environment chips

The chip already maps the keys (`tagChipKeyAction`: Enter/Space -> filter, F2 -> edit, Delete/Backspace -> remove). This task removes the pencil and `X` from the Tab chain and the a11y tree, names the keys, and sets the chip's accessible name/description.

**Files:**
- Modify: `src/main/kotlin/io/github/barsia/speqa/editor/ui/chips/TagChip.kt`
- Modify: `src/main/resources/messages/SpeqaBundle.properties`

- [ ] **Step 1: Add bundle strings**

Append to `SpeqaBundle.properties` (place near the existing `tagCloud.*` keys):

```properties
tagCloud.editTag.tooltip=Edit (F2)
tagCloud.removeTag.tooltip=Remove (Delete)
tagChip.a11y.tag=Tag {0}. Press Enter to filter, F2 to edit, Delete to remove.
tagChip.a11y.environment=Environment {0}. Press Enter to filter, F2 to edit, Delete to remove.
```

- [ ] **Step 2: Make the pencil and X non-focusable; key the tooltips**

In `TagChip`, where `editButton` (the `HoverTintIconButton`) is created, set `it.isFocusable = false` and set its tooltip to `SpeqaBundle.message("tagCloud.editTag.tooltip")`. Where `deleteButton` (the `CornerDeleteButton`) is created (the `.apply { ... }` block), set `isFocusable = false` and `toolTipText = SpeqaBundle.message("tagCloud.removeTag.tooltip")` (replacing the current `tagCloud.removeTag`).

```kotlin
// editButton creation:
).also {
    add(it)
    it.isFocusable = false
}
// deleteButton .apply block: add
isFocusable = false
toolTipText = SpeqaBundle.message("tagCloud.removeTag.tooltip")
```

The chip itself remains `isFocusable = (onClick != null)` (unchanged) and keeps `tagChipKeyAction` wiring (unchanged).

- [ ] **Step 3: Set the chip's AccessibleContext**

The `TagChip` constructor needs to know whether it is a tag or environment chip to choose the a11y string. `TagCloud` already has `metadataKind` (`TAG`/`ENVIRONMENT`); thread a `Boolean isEnvironment` (or reuse an existing kind param) into `TagChip`. Override `getAccessibleContext()`:

```kotlin
override fun getAccessibleContext(): javax.accessibility.AccessibleContext {
    if (accessibleContext == null) {
        accessibleContext = object : AccessibleJPanel() {
            override fun getAccessibleRole() = javax.accessibility.AccessibleRole.PUSH_BUTTON
            override fun getAccessibleName() = tag
            override fun getAccessibleDescription() = SpeqaBundle.message(
                if (isEnvironment) "tagChip.a11y.environment" else "tagChip.a11y.tag", tag,
            )
        }
    }
    return accessibleContext
}
```

(`AccessibleJPanel` is the inner class of `JPanel`; reference it as in `LinkRow.getAccessibleContext()`.)

- [ ] **Step 4: Compile**

Run: `./gradlew compileKotlin 2>&1 | grep -E "^e:|BUILD" | tail -5`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Run existing chip tests (must stay green)**

Run: `./gradlew test --tests "*TagChip*" --tests "*TagCloud*" 2>&1 | grep -E "FAILED|BUILD" | tail`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: IDE verification (manual)**

Tab through a tag/environment cloud. Expected: focus stops only on each chip and the `+` button, never on the pencil or `X`. On a focused chip: Delete/Backspace removes, F2 opens edit, Enter filters. Pencil/`X` still appear on hover and on chip focus, with tooltips "Edit (F2)" / "Remove (Delete)". Focus ring is on the chip only.

- [ ] **Step 7: Commit**

```bash
git add src/main/kotlin/io/github/barsia/speqa/editor/ui/chips/TagChip.kt src/main/kotlin/io/github/barsia/speqa/editor/ui/chips/TagCloud.kt src/main/resources/messages/SpeqaBundle.properties
git commit -m "feat: tag/environment chips use the Model-A keyboard model (keys on chip, glyphs out of Tab)"
```

---

### Task 4: Model A for link/attachment/ticket rows (top-level and step-level)

Rows currently map only Enter/Space -> activate. Add Delete -> remove and (where editable) F2 -> edit, and remove their pencil/`X` from the Tab chain. Extract a pure key-action helper (mirrors `tagChipKeyAction`) so it is unit-testable.

**Files:**
- Create: `src/main/kotlin/io/github/barsia/speqa/editor/ui/primitives/RemovableRowKeyAction.kt`
- Test: `src/test/kotlin/io/github/barsia/speqa/editor/ui/primitives/RemovableRowKeyActionTest.kt`
- Modify: `src/main/kotlin/io/github/barsia/speqa/editor/ui/primitives/RemovableRowAction.kt` (`RemovableRowActionSlot`)
- Modify: `src/main/kotlin/io/github/barsia/speqa/editor/ui/links/LinkRow.kt`
- Modify: `src/main/kotlin/io/github/barsia/speqa/editor/ui/attachments/AttachmentRow.kt`
- Modify: `src/main/kotlin/io/github/barsia/speqa/editor/ui/chips/TicketChip.kt`

- [ ] **Step 1: Write the failing test for the pure key helper**

Create `RemovableRowKeyActionTest.kt`:

```kotlin
package io.github.barsia.speqa.editor.ui.primitives

import java.awt.event.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Test

class RemovableRowKeyActionTest {
    private fun calls() = mutableListOf<String>()

    @Test
    fun `enter and space activate`() {
        val c = calls()
        assertEquals(true, removableRowKeyAction(KeyEvent.VK_ENTER, { c.add("a") }, { c.add("e") }, { c.add("d") }))
        assertEquals(true, removableRowKeyAction(KeyEvent.VK_SPACE, { c.add("a") }, { c.add("e") }, { c.add("d") }))
        assertEquals(listOf("a", "a"), c)
    }

    @Test
    fun `f2 edits only when an edit handler is present`() {
        val c = calls()
        assertEquals(true, removableRowKeyAction(KeyEvent.VK_F2, { c.add("a") }, { c.add("e") }, { c.add("d") }))
        assertEquals(false, removableRowKeyAction(KeyEvent.VK_F2, { c.add("a") }, null, { c.add("d") }))
        assertEquals(listOf("e"), c)
    }

    @Test
    fun `delete and backspace remove`() {
        val c = calls()
        assertEquals(true, removableRowKeyAction(KeyEvent.VK_DELETE, { c.add("a") }, { c.add("e") }, { c.add("d") }))
        assertEquals(true, removableRowKeyAction(KeyEvent.VK_BACK_SPACE, { c.add("a") }, { c.add("e") }, { c.add("d") }))
        assertEquals(listOf("d", "d"), c)
    }

    @Test
    fun `unhandled key returns false`() {
        assertEquals(false, removableRowKeyAction(KeyEvent.VK_A, {}, {}, {}))
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew test --tests "*RemovableRowKeyActionTest" 2>&1 | grep -E "FAILED|BUILD|error:|^e:" | tail`
Expected: FAIL / compile error (`removableRowKeyAction` not defined).

- [ ] **Step 3: Implement the pure helper**

Create `RemovableRowKeyAction.kt`:

```kotlin
package io.github.barsia.speqa.editor.ui.primitives

import java.awt.event.KeyEvent

/**
 * Pure key-to-action mapping for the Model-A removable row/chip: Enter/Space run the
 * primary action, F2 edits (when [onEdit] is present), Delete/Backspace remove. Returns
 * true when the key was handled (caller should consume the event).
 */
fun removableRowKeyAction(
    keyCode: Int,
    onActivate: (() -> Unit)?,
    onEdit: (() -> Unit)?,
    onDelete: (() -> Unit)?,
): Boolean = when (keyCode) {
    KeyEvent.VK_ENTER, KeyEvent.VK_SPACE -> { onActivate?.invoke(); onActivate != null }
    KeyEvent.VK_F2 -> { onEdit?.invoke(); onEdit != null }
    KeyEvent.VK_DELETE, KeyEvent.VK_BACK_SPACE -> { onDelete?.invoke(); onDelete != null }
    else -> false
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew test --tests "*RemovableRowKeyActionTest" 2>&1 | grep -E "FAILED|BUILD" | tail`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Make the shared remove X non-focusable**

In `RemovableRowActionSlot.init` (`RemovableRowAction.kt`), after `add(actionComponent)`, add `actionComponent.isFocusable = false`. (These remove buttons are never Tab stops under Model A; removal is via Delete on the row.)

- [ ] **Step 6: Wire keys + non-focusable pencil in LinkRow**

In `LinkRow`, replace the existing `addKeyListener { ... Enter/Space -> activate }` body with `if (removableRowKeyAction(e.keyCode, ::activate, ::openEdit, onDelete)) e.consume()`. Set the edit pencil button `isFocusable = false` where it is created. (`onDelete` is the constructor val at line 47.)

- [ ] **Step 7: Wire keys in AttachmentRow**

In `AttachmentRow`, replace the key listener body with `if (removableRowKeyAction(e.keyCode, ::activate, null, onDelete)) e.consume()` (attachments have no inline edit). `onDelete` is the constructor val at line 69.

- [ ] **Step 8: Wire keys in TicketChip**

In `TicketChip`, replace the key listener body with `if (removableRowKeyAction(e.keyCode, onActivate, null, onDelete)) e.consume()`. (`onActivate`/`onDelete` are constructor params.)

- [ ] **Step 9: AccessibleContext descriptions (optional within this task, low risk)**

For each row, set the accessible description to name the keys (add bundle strings `linkRow.a11y`, `attachmentRow.a11y`, `ticketChip.a11y` of the form `{0}. Press Enter to open, F2 to edit, Delete to remove.` / without F2 for attachments/tickets) in the existing `getAccessibleContext()` overrides (LinkRow/AttachmentRow already have one; add to TicketChip).

- [ ] **Step 10: Compile + run tests**

Run: `./gradlew compileKotlin 2>&1 | grep -E "^e:|BUILD" | tail; ./gradlew test --tests "*RemovableRowKeyActionTest" --tests "*LinkRow*" --tests "*AttachmentRow*" --tests "*Ticket*" 2>&1 | grep -E "FAILED|BUILD" | tail`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 11: IDE verification (manual)**

Tab through link/attachment/ticket rows (top-level and under a step). Expected: focus stops on each row (and the `+`), never on the pencil or `X`. Delete removes, F2 edits links, Enter opens. Glyphs still hover/focus-reveal.

- [ ] **Step 12: Commit**

```bash
git add src/main/kotlin/io/github/barsia/speqa/editor/ui/primitives/RemovableRowKeyAction.kt src/test/kotlin/io/github/barsia/speqa/editor/ui/primitives/RemovableRowKeyActionTest.kt src/main/kotlin/io/github/barsia/speqa/editor/ui/primitives/RemovableRowAction.kt src/main/kotlin/io/github/barsia/speqa/editor/ui/links/LinkRow.kt src/main/kotlin/io/github/barsia/speqa/editor/ui/attachments/AttachmentRow.kt src/main/kotlin/io/github/barsia/speqa/editor/ui/chips/TicketChip.kt src/main/resources/messages/SpeqaBundle.properties
git commit -m "feat: link/attachment/ticket rows use the Model-A keyboard model"
```

---

### Task 5: Restore focus after delete (timing + hidden add button)

Root cause: `DeleteFocusRestorer.onDeleted` runs (F) before the asynchronous document round-trip rebuilds the list (R), so focus is set on a stale component that R then discards. Steps work because they rebuild synchronously. Also, top-level sections hide their inline add button, so the only-item delete targets a non-showing button.

**Files:**
- Modify: `src/main/kotlin/io/github/barsia/speqa/editor/ui/chips/TagCloud.kt`
- Modify: `src/main/kotlin/io/github/barsia/speqa/editor/ui/links/LinkList.kt`
- Modify: `src/main/kotlin/io/github/barsia/speqa/editor/ui/attachments/AttachmentList.kt`
- Modify: `src/main/kotlin/io/github/barsia/speqa/editor/ui/steps/StepMetaRow.kt`
- Modify: `src/main/kotlin/io/github/barsia/speqa/editor/ui/TestCasePanel.kt`

- [ ] **Step 1: Make the section setters idempotent**

In `TagCloud.setTags`, `LinkList.setLinks`, `AttachmentList.setAttachments` (and the equivalent in `StepMetaRow` for tickets/links/attachments), add a guard at the top: `if (newValue == currentField) return` before mutating and rebuilding. This prevents the trailing async refresh from rebuilding a second time and wiping restored focus.

- [ ] **Step 2: Rebuild synchronously before the restorer fires**

In each section's `onDelete` handler (e.g. `TagCloud.kt:152-155`), compute the post-delete list, update the model (existing `onRemove`/`emit` call), then call the section's own setter with the new list synchronously, then call `restorer.onDeleted(index, sizeBefore)`. Example for `TagCloud`:

```kotlin
onDelete = {
    val remaining = tags.filterNot { it == tag }
    onRemove(tag)        // persists to the document (async round-trip)
    setTags(remaining)   // synchronous view rebuild; async refresh is now a no-op (idempotent)
    restorer.onDeleted(index, sizeBefore)
}
```

Apply the same shape to `LinkList`, `AttachmentList`, and the `StepMetaRow` delete handlers (each already has its `onRemove`/emit and `restorer.onDeleted`; insert the synchronous setter call between them).

- [ ] **Step 3: Give hidden-add-button sections a real focus target**

Add an optional `externalAddButton: JComponent? = null` constructor parameter to `TagCloud`, `LinkList`, `AttachmentList`. When `hideAddButton` is true, build the `DeleteFocusRestorer` with `addButton = externalAddButton ?: addButton`. In `TestCasePanel`, pass the corresponding section-header `+` (`headerAddIconButton`, around `TestCasePanel.kt:540-565`) as `externalAddButton` when constructing the top-level clouds/lists (around lines 272, 305, 340, 349).

- [ ] **Step 4: Compile + tests**

Run: `./gradlew compileKotlin 2>&1 | grep -E "^e:|BUILD" | tail; ./gradlew test 2>&1 | grep -E "FAILED|BUILD" | tail`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: IDE verification (manual)**

Delete a tag/link/attachment/ticket (both via Delete on the focused element and via mouse on the `X`). Expected: keyboard focus lands on the neighbor that took the slot (or the previous item if it was last, or the section-header `+` when the list becomes empty), never lost. Verify for top-level and step-level sections.

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/io/github/barsia/speqa/editor/ui/chips/TagCloud.kt src/main/kotlin/io/github/barsia/speqa/editor/ui/links/LinkList.kt src/main/kotlin/io/github/barsia/speqa/editor/ui/attachments/AttachmentList.kt src/main/kotlin/io/github/barsia/speqa/editor/ui/steps/StepMetaRow.kt src/main/kotlin/io/github/barsia/speqa/editor/ui/TestCasePanel.kt
git commit -m "fix: restore keyboard focus after deleting tags/links/attachments/tickets"
```

---

### Task 6: Restore focus after dialogs and choosers

Root cause: the Add/Edit Link dialog and the attachment file chooser do not return focus on close; `AddTagPopup` does not refocus its `+` anchor on keyboard dismissal and ignores Tab/Shift+Tab.

**Files:**
- Modify: `src/main/kotlin/io/github/barsia/speqa/editor/ui/links/LinkList.kt`, `src/main/kotlin/io/github/barsia/speqa/editor/ui/links/LinkRow.kt`, `src/main/kotlin/io/github/barsia/speqa/editor/ui/steps/StepMetaRow.kt` (link add/edit call sites)
- Modify: `src/main/kotlin/io/github/barsia/speqa/editor/ui/attachments/AttachmentList.kt` (chooser call site)
- Modify: `src/main/kotlin/io/github/barsia/speqa/editor/ui/chips/AddTagPopup.kt`

- [ ] **Step 1: Restore focus after the Add/Edit Link dialog**

At each site that opens `AddEditLinkDialog` (`LinkList.openAddDialog`, `LinkRow.openEdit`, the step-level link add in `StepMetaRow`), capture the control to return to before opening (the triggering row, or for the add case the section-header `+`), and after `AddEditLinkDialog.show(...)` returns (any outcome), call `trigger.requestFocusInWindow()` via `SwingUtilities.invokeLater { ... }`. Example:

```kotlin
private fun openEdit() {
    val returnTo = this
    ApplicationManager.getApplication().invokeLater {
        val edited = AddEditLinkDialog.show(project, editLink = link)
        if (edited != null) onEdited(edited)
        SwingUtilities.invokeLater { returnTo.requestFocusInWindow() }
    }
}
```

- [ ] **Step 2: Restore focus after the attachment chooser**

In `AttachmentList.openChooser` (the `startAdd`/`openChooser` path around line 158-171), capture the add affordance (the section-header `+` or the list's add button) before opening the `FileChooser`, and after the chooser callback completes (both picked and cancelled), `invokeLater { addButton.requestFocusInWindow() }`.

- [ ] **Step 3: AddTagPopup returns focus to the anchor and handles Tab**

Pass the `+` anchor into `AddTagPopup` (it is already created with the anchor in `TagCloud.startAdd`; thread it through). In `wireKeyboard`:
- on ESC and on Enter-commit, after `popup.cancel()`, call `SwingUtilities.invokeLater { anchor.requestFocusInWindow() }`;
- add `VK_TAB` and `VK_TAB` with shift: `popup.cancel()`, then `anchor.requestFocusInWindow()`, then `KeyboardFocusManager.getCurrentKeyboardFocusManager().focusNextComponent(anchor)` (or `focusPreviousComponent`) so traversal continues from the anchor.

(Also implement the click-outside ring-suppression rule from spec line 1065: track a `dismissedByClickOutside` flag set by the popup's cancel-on-mouse-outside path; when the popup is dismissed by an outside click, return logical focus to the anchor WITHOUT painting the keyboard focus ring, relying on the existing keyboard-cause focus gating (`isKeyboardFocusCause`).)

- [ ] **Step 4: Compile + tests**

Run: `./gradlew compileKotlin 2>&1 | grep -E "^e:|BUILD" | tail; ./gradlew test 2>&1 | grep -E "FAILED|BUILD" | tail`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: IDE verification (manual)**

Open Add link from the header `+`, press ESC. Expected: focus returns to the `+` (not lost). Edit a link, OK/Cancel: focus returns to the row. Open the tag popup, press ESC/Enter/Tab: focus returns to the `+` and Tab continues traversal. Open the attachment chooser and cancel: focus returns to the attachment add affordance.

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/io/github/barsia/speqa/editor/ui/links/LinkList.kt src/main/kotlin/io/github/barsia/speqa/editor/ui/links/LinkRow.kt src/main/kotlin/io/github/barsia/speqa/editor/ui/steps/StepMetaRow.kt src/main/kotlin/io/github/barsia/speqa/editor/ui/attachments/AttachmentList.kt src/main/kotlin/io/github/barsia/speqa/editor/ui/chips/AddTagPopup.kt
git commit -m "fix: return keyboard focus to the trigger after dialogs and the tag popup"
```

---

### Task 7: Align spec wording with the chosen mechanism

**Files:**
- Modify: `src/main/kotlin/io/github/barsia/speqa/editor/ui/... ` (none) and `docs/specs/2026-04-06-speqa-design.md`

- [ ] **Step 1: Update the "Chip/row sub-action keyboard model" bullet**

Change the phrase "kept out of the Tab chain via the `speqa.excludeFromTabChain` client property (`SPEQA_EXCLUDE_FROM_TAB_CHAIN`)" to "kept out of the Tab chain and the accessibility focus tree by being non-focusable (mouse-only affordances)". (No em dashes in the file.)

- [ ] **Step 2: Commit**

```bash
git add docs/specs/2026-04-06-speqa-design.md
git commit -m "docs: clarify chip/row glyphs are excluded from Tab by being non-focusable"
```

---

### Task 8: Focus ring for `mutedActionLabel` (Add ticket ID / Add link / Attach file)

Root cause: the step-level "Add ticket ID", "Add link", and "Attach file" affordances all use `mutedActionLabel`, which returns a raw `JBLabel` with no focus-ring painting (it is focusable and Enter/Space already activate it). One fix at the helper covers all three call sites.

**Files:**
- Modify: `src/main/kotlin/io/github/barsia/speqa/editor/ui/primitives/MutedActionLabel.kt`

- [ ] **Step 1: Paint the keyboard focus ring on the label**

Replace the plain `JBLabel(...)` instantiation with an anonymous subclass that paints `paintCompactFocusRing` when keyboard-focused, and add a `FocusAdapter` that gates on `isKeyboardFocusCause` (same pattern as `SpeqaActionButton` in `SpeqaIconButton.kt`):

```kotlin
var keyboardFocused = false
val label = object : JBLabel(text, mutedIcon, JBLabel.LEFT) {
    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        if (keyboardFocused) {
            val g2 = g.create() as Graphics2D
            try {
                paintCompactFocusRing(g2, width, height, JBUI.scale(4).toFloat())
            } finally {
                g2.dispose()
            }
        }
    }
}
label.addFocusListener(object : FocusAdapter() {
    override fun focusGained(e: FocusEvent) {
        keyboardFocused = isKeyboardFocusCause(e.cause)
        label.repaint()
    }
    override fun focusLost(e: FocusEvent) {
        keyboardFocused = false
        label.repaint()
    }
})
```

Add imports `paintCompactFocusRing`, `isKeyboardFocusCause` (same `primitives` package), `java.awt.Graphics`, `java.awt.Graphics2D`, `java.awt.event.FocusAdapter`, `java.awt.event.FocusEvent`, `com.intellij.util.ui.JBUI`. No call-site changes.

- [ ] **Step 2: Compile** -> `./gradlew compileKotlin 2>&1 | grep -E "^e:|BUILD" | tail -5` expects `BUILD SUCCESSFUL`.
- [ ] **Step 3: IDE verification** -> Tab to "Add ticket ID" / "Add link" / "Attach file": a thin focus ring appears; Enter/Space still activates.
- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/io/github/barsia/speqa/editor/ui/primitives/MutedActionLabel.kt
git commit -m "fix: keyboard focus ring on muted action labels (Add ticket/link/attachment)"
```

---

### Task 9: Drag-reorder handle keyboard behavior (DECISION: Option i)

The step drag handle (`StepCard.kt:227-267`, an anonymous focusable `JPanel`, tooltip `tooltip.dragToReorder`) is in the Tab chain but has no focus ring and no keyboard action (only right-click opens its Move Up / Move Down / Duplicate / Delete menu via `showHandleMenu`). So it is currently a dead Tab stop. CHOSEN: Option i (make it keyboard-operable).

**Files:**
- Modify: `src/main/kotlin/io/github/barsia/speqa/editor/ui/steps/StepCard.kt`
- Check/modify if it is a Tab stop: `src/main/kotlin/io/github/barsia/speqa/editor/ui/run/RunCaseSection.kt` (its drag handle)

- [ ] **Step 1: Paint the focus ring on keyboard focus.** Add a `keyboardFocused` field to the `dragHandle` anonymous `JPanel`; in its `paintComponent`, after the existing icon paint, if `keyboardFocused` call `paintCompactFocusRing(g2, width, height, JBUI.scale(4).toFloat())`. Add a `FocusAdapter` setting `keyboardFocused = isKeyboardFocusCause(e.cause)` on focusGained / `false` on focusLost, with `repaint()`. (Same pattern as `SpeqaActionButton`.)
- [ ] **Step 2: Keyboard activation.** Add a `KeyAdapter` to the handle: on `VK_SPACE` or `VK_ENTER`, open the existing reorder menu. Read `showHandleMenu` first; it is currently wired to mouse popup-trigger events. Make it invokable without a `MouseEvent` (show the `JBPopup`/menu relative to the handle component, e.g. at the handle's bounds), and call that from the key handler. `e.consume()` when handled. Do not change the menu's actions (Move Up / Move Down / Duplicate / Delete with their existing enablement guards).
- [ ] **Step 3:** If `RunCaseSection`'s drag handle is also a Tab stop (focusable and not excluded), apply the same ring + Space/Enter-to-menu treatment; if it is non-focusable (not a Tab stop), leave it.
- [ ] **Step 4: Compile** -> `BUILD SUCCESSFUL`.
- [ ] **Step 5: IDE verification** -> Tab to a step's drag handle: a focus ring shows; Space/Enter opens the Move menu; Move Up/Down reorders. Mouse drag still works.
- [ ] **Step 6: Commit** -> `git commit -m "feat: keyboard-operable step drag handle (focus ring + Space/Enter reorder menu)"`.

The same evaluation applies to the `RunCaseSection` drag handle if it is a Tab stop.

## Final verification

- [ ] Run the full suite: `./gradlew test 2>&1 | grep -E "FAILED|BUILD" | tail` -> `BUILD SUCCESSFUL`.
- [ ] Full IDE pass across light and dark themes: Tab through a test case and a multi-case run end to end; verify chips/rows are single Tab stops, Delete/F2/Enter work, focus is restored after delete and after dialogs, and the multi-case run never loses focus.
- [ ] Working tree contains only the intended files; do not push (await explicit user request).
