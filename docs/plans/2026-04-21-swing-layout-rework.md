# Swing panel layout rework — match Compose reference

**Date:** 2026-04-21
**Status:** Proposed
**Scope:** Rewrite the composition of `TestCasePanel` and `TestRunPanel` to match the Compose reference (screenshot `Screenshot 2026-04-21 at 11.49.31.png`). Leaf widgets (primitives, chips, rows, step cards, drag/reorder, Markdown pane, patch ops, popups) stay. Only the top-level panel composition plus a few new small layout helpers change.

## Compose reference layout (from screenshot)

From top to bottom inside the scroll viewport:

1. **Header utility row** (single horizontal line): `TC- <id>` inline-editable + `📅 Created <date>` + `📅 Updated <date>` + `▶ Run` on the far right.
2. **Title row**: large bold title text + pencil edit icon on the right.
3. **Horizontal divider** directly under the title.
4. **Priority | Status** two-column row: small uppercase caption (`PRIORITY`, `STATUS`) above each, dropdown below.
5. **Environment | Tags** two-column row: same caption pattern, with a `+` icon aligned right in each column's header line; values are chip clouds below.
6. **Links | Attachments** two-column row: same pattern, each column's body is a vertical list of rows (or empty when none).
7. **Description** — small uppercase caption, then text area below.
8. **Preconditions** — small uppercase caption, then text area below.
9. **Scenario** — small uppercase caption, then the step cards + `+ Add step` at the end.

Step card (no × delete button, no always-on drag handle — drag handle appears on hover):

```
 01   [action text area        ] [expected text area       ]
      🎫 Add ticket…  🔗 Add link        📎 Attach file
```

Existing ticket chips / links / attachments render above their respective add-action when present.

## New / adjusted primitives

Under `editor/ui/primitives/`:

1. **`SectionCaption.kt`** — `fun sectionCaption(text: String): JBLabel` returning an all-caps small muted label (font size `Label.font - 1`, color `Label.disabledForeground`, `tracking +1 px` via `HTML.Attribute` if feasible or just uppercase text).

2. **`TwoColumnRow.kt`** — `fun twoColumnRow(leftCaption: String, rightCaption: String, leftBody: JComponent, rightBody: JComponent, leftHeaderAction: JComponent? = null, rightHeaderAction: JComponent? = null): JComponent`. Internal layout:
   ```
   GridBagLayout 2x2
   row 0: [leftCaption] [leftHeaderAction?] | [rightCaption] [rightHeaderAction?]
   row 1: [leftBody                          ] | [rightBody                         ]
   ```
   Gap between columns: `JBUI.scale(16)`. Caption→body gap: `JBUI.scale(4)`.

3. **`DateIconLabel.kt`** — `fun dateIconLabel(icon: Icon, label: String, tooltip: String? = null): JComponent`. `JBLabel` with `setIconTextGap(JBUI.scale(4))`.

4. **`HeaderAddIconButton.kt`** — small `ActionButton` wrapper that renders a `+` icon (`AllIcons.General.Add`) with hand cursor. Used as `leftHeaderAction` / `rightHeaderAction` in `TwoColumnRow`.

## New panel-level composite widgets

Under `editor/ui/` (keep them close to `TestCasePanel`):

5. **`HeaderUtilityRow.kt`** — `JPanel(GridBagLayout)` composing:
   - Left: existing `InlineEditableIdRow` (unchanged API — already works).
   - Middle-left: `dateIconLabel(AllIcons.General.InspectionsEye or a calendar icon, createdLabel, tooltip)` — gets a calendar-like icon from `AllIcons`. If no perfect match, use `AllIcons.General.Date` or `IconLoader.getIcon("/icons/created.svg", …)` if the SVG exists in resources.
   - Middle-right: same pattern for updated.
   - Far right: `SpeqaIconButton(AllIcons.Actions.Execute, "Run test case", onRun)` — green play icon.
   - Horizontal glue between middle and right so Run floats right.

6. **`InlineEditableTitleRow.kt`** — `JPanel(BorderLayout)` with:
   - Center: editable text. Read-mode: `JBLabel(title, bold large)`; edit-mode: `JBTextField(title)`. Toggle on pencil click or title click.
   - East: pencil `SpeqaIconButton(AllIcons.Actions.Edit, "Edit title", onToggleEdit)`.
   - Commit on Enter / focus loss. Esc cancels.

7. **`PriorityComboBox.kt`** / **`StatusComboBox.kt`** — `ComboBox<Priority>` / `ComboBox<Status>` with a `ListCellRenderer` that capitalizes enum labels. `onChange: (enum) -> Unit` callback via `addItemListener` filtered to `ItemEvent.SELECTED`.

## Step meta-row icons

Update `editor/ui/steps/StepMetaRow.kt`:
- `TicketBlock` add button: `[🎫 icon] Add ticket` — use `AllIcons.Nodes.Tag` or a custom SVG from `resources/icons/` if `ticket.svg` is bundled.
- `LinkBlock` add button: `[🔗 icon] Add link` — `AllIcons.General.Web` or existing `chainLink.svg`.
- `AttachmentBlock` add button: `[📎 icon] Attach file` — `AllIcons.General.Attachment` (standard platform icon).

Check `src/main/resources/icons/` first for existing SVGs (`ticket.svg`, `chainLink.svg`, `paperClip.svg`) — reuse them via `IconLoader.getIcon("/icons/ticket.svg", SpeqaBundle::class.java)` if present.

## Step drag handle hover visibility

In `StepCard.kt`: install a `MouseListener` on the card that toggles `dragHandle.isVisible = true` on `mouseEntered` / `false` on `mouseExited`. Also toggle on focus change — keep visible while any child of the card has focus. Initial state: invisible.

## TestCasePanel composition (rewritten)

Top-down vertical `BoxLayout.Y_AXIS` inside `JBUI.Borders.empty(12)`:

```
headerUtilityRow                       ← HeaderUtilityRow
verticalStrut(6)
titleRow                               ← InlineEditableTitleRow
verticalStrut(4)
surfaceDivider()
verticalStrut(10)
twoColumnRow(PRIORITY body=priorityCombo, STATUS body=statusCombo)
verticalStrut(10)
twoColumnRow(
  ENVIRONMENT body=environmentCloud  header=+env,
  TAGS        body=tagCloud           header=+tag,
)
verticalStrut(10)
twoColumnRow(
  LINKS       body=linkList          header=+link,
  ATTACHMENTS body=attachmentList    header=+attach,
)
verticalStrut(10)
sectionCaption(DESCRIPTION) + descriptionSection (EditableBodyBlockSection)
verticalStrut(10)
sectionCaption(PRECONDITIONS) + preconditionsSection
verticalStrut(10)
sectionCaption(SCENARIO) + stepsSection
```

The `+`-in-section-header design means `TagCloud` and friends no longer need their own "+" button when they're used in a section with a `HeaderAddIconButton`. Add an optional `hideAddButton: Boolean = false` to `TagCloud`, `LinkList`, `AttachmentList`, `EnvironmentCloud` so the section header owns the add action. When pressed, the header button calls back into the widget (`cloud.startAdd()` / `list.startAdd()` — new public methods) to trigger the same inline editor or file chooser.

Drop `ReferencesStrip` — the Compose layout doesn't have this strip. The Links/Attachments sections are always visible as a 2-column row (empty bodies just show nothing).

Drop the existing `SpeqaEditorToolbar` — Run button moves into `HeaderUtilityRow`.

## TestRunPanel composition (rewritten)

Analogous structure. Columns may differ where the run model differs from test-case model:
- Header row: `TR-<id>` + `📅 Started` + `📅 Finished` + verdict result badge (Passed/Failed/…) instead of Run button.
- Title row: read-only title from source test case.
- Priority / Status — hidden for run (runs don't carry priority/status).
- Runner / Environment two-column row (RUNNER body=textfield, ENVIRONMENT body=cloud).
- Tags solo row.
- Links / Attachments two-column row.
- Description / Preconditions read-only via `MarkdownReadOnlyPane`.
- Scenario: step result cards with verdict dropdown + comment (existing Step-5 `StepResultCard` embedded within; just restyle the outer composition to match).

## Execution plan

1. Create / update primitives: `SectionCaption`, `TwoColumnRow`, `DateIconLabel`, `HeaderAddIconButton`. ~120 LOC total.
2. Create panel-level widgets: `HeaderUtilityRow`, `InlineEditableTitleRow`, `PriorityComboBox`, `StatusComboBox`. ~250 LOC.
3. Update `TagCloud`, `LinkList`, `AttachmentList` to support `hideAddButton` + expose `startAdd()`. ~30 LOC delta.
4. Rewrite `TestCasePanel.buildLayout()` to use the new structure. ~250 LOC.
5. Rewrite `TestRunPanel.buildLayout()` analogously. ~300 LOC.
6. Add SVG icon lookups + update `StepMetaRow` add buttons. ~20 LOC.
7. Drag-handle hover visibility in `StepCard`. ~15 LOC.
8. Remove now-unused `ReferencesStrip` usage from panels (keep the file — it's small and may be useful later; or delete if dead).
9. Remove `SpeqaEditorToolbar` from panel composition; move Run-button action into `HeaderUtilityRow`. Delete `SpeqaEditorToolbar.kt` if nothing else uses it.
10. Compile + tests green. Sandbox smoke.

## Non-goals

- Do NOT touch `DocumentPatcher`, `SpeqaPreviewEditor`, `TestRunEditor`, `ScrollSyncController`, or any parser/model files.
- Do NOT reintroduce Compose.
- Do NOT add new patch-operation types.
- Do NOT change drag-reorder mechanics, live-preview decorator, metadata popups, image cache, commit-flash, or focus policy. They're fine.
