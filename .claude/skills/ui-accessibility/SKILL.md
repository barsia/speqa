---
name: ui-accessibility
description: Accessibility review guidance for any UI that appears in IntelliJ-based IDEs, including platform UI, product UI, and plugin UI across all UI stacks. Use when creating, editing, or reviewing UI components, dialogs, settings panels, tool windows, popups, forms, custom components, keyboard navigation, focus behavior, labels/names/descriptions, validation, dynamic feedback, color contrast, scaling, or screen reader support.
---

# IntelliJ UI Accessibility

Use this skill when creating, changing, or reviewing UI in an IntelliJ-based IDE, including plugin UI. Do not use it just because a feature has a UI surface if the work is not on the UI itself. It covers accessibility expectations for Swing, Kotlin UI DSL, and other UI stacks, plus review and verification checks for keyboard use, screen readers, focus, labels, dynamic feedback, contrast, scaling, and localization.

## Primary References

- [JetBrains IntelliJ Platform accessibility guide](https://plugins.jetbrains.com/docs/intellij/accessibility.html) - upstream source for IntelliJ-specific APIs, tools, and expectations.

Follow WCAG 2.2 as the primary accessibility standard.

## Swing And Kotlin UI DSL

Accessible context details are relevant only for Swing-based UI, including Kotlin UI DSL.

- For custom Swing components and custom renderers, provide the correct accessible name, role, state/value, and property-change notifications.
- Accessible names are often inferred from component text, tooltip text, or `JLabel.setLabelFor()`. Set or override the accessible name only when the inferred name is absent, ambiguous, or incorrect.
- Do not include the component role in the accessible name. For complex custom components, combine visible title, subtitle, icon meaning, and other visible parts needed to understand the component.
- Modify `AccessibleContext` and its properties only when implicit metadata is missing, insufficient, or incorrect.
- Prefer direct `component.getAccessibleContext().accessibleName = ...` / `accessibleDescription = ...` for assigning one already-known value.
- Use `AccessibleContextUtil` when it adds value: copying metadata from another component, combining multiple accessible strings, avoiding duplicate descriptions, setting an accessible parent, or normalizing multiline text for screen readers.
- Do not add accessibility properties or fire accessibility events preemptively. Rely on Swing/Kotlin UI DSL defaults when names, roles, state, values, focus behavior, and events are already correct; customize them only to fix a concrete guideline violation or missing screen-reader signal.
- Use `accessibleDescription` in rare cases for supplementary text that already exists in the UI but is not otherwise read when the related component receives focus, such as comments, banners, hints, warnings, or inline explanations. Do not invent a separate description from scratch or duplicate the visible label/state.
- Use `AccessibleRole.LABEL` for plain text content and `AccessibleRole.TEXT` for editable or selectable text fields/text areas. Use the specific button role that matches behavior: `PUSH_BUTTON`, `RADIO_BUTTON`, `TOGGLE_BUTTON`, or `HYPERLINK`.
- Override accessible state when custom state such as checked, selected, expanded, or editable is not exposed correctly. Fire accessible property-change events when state, selection, value, text changes must be announced to assistive technologies.
- For custom components from scratch, check similar Swing components for which `AccessibleAction`, `AccessibleText`, `AccessibleSelection`, `AccessibleValue`, or other `Accessible*` interfaces they implement before choosing interfaces manually.
- Screen readers automatically announce accessible property changes of the focused component. Use `AccessibleAnnouncerUtil.announce()` for the most important changes outside the focused component or changes that do not fit existing property-change support.
- Check `AccessibleAnnouncerUtil.isAnnouncingAvailable()` when code depends on announcement support.
- Use `ScreenReader.isActive()` only for behavior that should change specifically for screen reader users.

## Other UI Stacks

- For Compose, JCEF, or other non-Swing UI, apply the same accessibility goals with that stack's own semantics, focus, keyboard, and testing mechanisms.

## Review Workflow

Check the UI before finishing code changes:

- Keyboard-only operation works, focus order is predictable, and focus does not get trapped. Only interactive components are keyboard-focusable, and they can be activated with Space or Enter when focused.
- Dialogs cycle predictably with `Tab` and `Shift+Tab`; `Escape` closes dialogs/popups or moves focus back from a tool window to the editor when that is the expected exit path.
- Non-interactive labels and panels are not focusable unless critical important information would otherwise be unavailable to screen reader users; gate that behavior with `ScreenReader.isActive()`.
- New popups, modal dialogs, and dynamic content either receive focus or have a keyboard path to reach them. Do not move focus unexpectedly while users interact with lists or dropdowns.
- Container components such as lists, trees, tables, support the arrow-key navigation between items.
- Important dynamic feedback, such as validation results, search results, and background task completion, is announced or otherwise reachable.
- Color is not the only signal; contrast, focus visibility, and font/UI scaling remain usable.
- User-visible accessibility text is localized through message bundles.
- The UI adapts to the IDE zoom level.

## Verification

- Walk the UI using only the keyboard.
- Test with a supported screen reader when practical: NVDA or JAWS on Windows, VoiceOver on macOS.
- Use UI Inspector to examine accessible names, descriptions, roles, and states. Enable "Show Accessibility Issues" to investigate suspected issues or validate custom UI behavior; it is not normally required for routine verification.
- In code reviews or final notes, explicitly state what accessibility paths were checked and what remains manual.

## Keyboard & Focus for Custom Interactive UI

Custom Swing components (chips, rows, drag handles, inline editors) lose the keyboard and focus affordances native widgets get for free. Expect and build these:

- **Focus-visible ring, not always-on.** Show a focus ring only when the last interaction was the keyboard, never on a mouse click. Track input modality once (an `IdeEventQueue` dispatcher / a small app service) and gate the ring on it (the `FocusEvent.Cause` plus the modality flag). A ring that flashes on every click reads as noise.
- **One Tab stop per removable item.** A tag chip or a link/attachment/ticket row is a SINGLE Tab stop; its per-item glyph buttons (edit/delete) are OUT of the Tab chain - reachable by keys, not Tab: `Delete` removes, `F2` edits, `Enter` activates. This keeps traversal short instead of visiting every tiny glyph.
- **Restore focus after a delete.** When a row is removed, move focus to the neighbor (same index, or the previous if it was last, or the add-button when the list empties) - never drop focus to nowhere. Extract the choice as a pure function (`nextFocusTargetAfterDelete(index, sizeBefore)`), unit-test it, and route it through one reusable restorer instead of re-implementing per list.
- **Return focus to the trigger** after a dialog or popup closes, and after deleting the focused item - the keyboard user should never have to re-find their place.
- **Make custom affordances operable.** A drag handle must be focusable and openable by `Space`/`Enter` (its reorder menu) at parity with right-click; a plain left-click should open it too. Anything the mouse can do must have a keyboard path.
- **Tab navigates between fields, it does not type.** Inside a multi-line editor pane embedded in a form, `Tab` must move to the next field, not insert a tab/spaces, or keyboard users get trapped indenting text.

Tooltips are mouse-hover only - never put a key hint like "press Space" in a tooltip (a keyboard user never sees it); put key affordances in the accessible description.
