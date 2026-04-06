---
name: compose-a11y
description: Use when building interactive Compose Desktop UI that needs keyboard navigation, focus management, hover detection, screen reader semantics, context menus, scrollbars, or Swing focus interop in IntelliJ plugin panels. Triggers on Tab navigation, focus ring, onPreviewKeyEvent, onKeyEvent, pointer events, semantics, contentDescription, ComposePanel, SwingPanel, or accessibility review.
user-invocable: true
---

# Compose Desktop Keyboard Accessibility

Patterns for making Compose for Desktop UI (Jewel/IntelliJ plugins) fully keyboard-accessible. These patterns were validated through extensive testing in a real IntelliJ plugin and address Compose Desktop-specific behaviors that differ from Android Compose and web.

## Core principle

Every interactive element a mouse user can reach must also be reachable and operable via keyboard. Tab moves forward, Shift+Tab moves backward, Enter/Space activates.

---

## Focus sinks (clearing focus on background click)

When the user clicks empty space, focus should leave text fields without jumping to the first focusable element. `FocusManager.clearFocus(force = true)` doesn't work in Compose Desktop — it redirects focus to the first focusable child.

**Pattern:** invisible focus sink with `focusTarget()`:

```kotlin
val focusSinkRequester = remember { FocusRequester() }

Box(modifier = Modifier.pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = true)  // only background clicks
        focusSinkRequester.requestFocus()
    }
}) {
    Box(Modifier.size(0.dp).focusRequester(focusSinkRequester).focusTarget())
    // ... actual content
}
```

**Why `focusTarget()` not `focusable()`:** `focusable()` includes `bringIntoView` behavior — requesting focus on it triggers a smooth scroll animation toward the sink's position. Since the sink is at the top of the layout, this manifests as a slow, continuous upward scroll drift. `focusTarget()` makes the element a focus target without the scroll side effect.

**Why `requireUnconsumed = true`:** With `false`, every pointer event (including clicks on text fields and buttons) triggers `requestFocus()` on the sink. This causes unnecessary focus churn and can contribute to scroll drift. With `true`, only clicks on empty background space trigger the sink.

**The sink should handle Tab** to enter the focus chain:

```kotlin
.onPreviewKeyEvent { event ->
    if (event.type == KeyEventType.KeyDown && event.key == Key.Tab) {
        focusManager.moveFocus(
            if (event.isShiftPressed) FocusDirection.Previous else FocusDirection.Next
        )
        true
    } else false
}
```

---

## Tab navigation

### The Shift+Tab rule

Every `onPreviewKeyEvent` handler for `Key.Tab` **must** check `event.isShiftPressed`:

```kotlin
.onPreviewKeyEvent { event ->
    if (event.type == KeyEventType.KeyDown && event.key == Key.Tab) {
        focusManager.moveFocus(
            if (event.isShiftPressed) FocusDirection.Previous
            else FocusDirection.Next
        )
        true
    } else false
}
```

Forgetting the Shift check means Shift+Tab silently does nothing — the user gets stuck.

### `clickable()` triggers `bringIntoView` — the scroll drift trap

`clickable()` internally creates a `focusable()` node that includes `bringIntoView` behavior. When Tab/Shift+Tab moves focus to an element with `clickable()`, the scroll container smoothly scrolls to reveal it — causing persistent slow upward drift.

**The safe pattern for elements that need both click AND Tab focus:**

```kotlin
.onFocusChanged { isFocused = it.isFocused }
.onPreviewKeyEvent { /* Tab, Enter, Space */ }
.focusTarget()              // our focus target — no bringIntoView
.handOnHover()
.focusProperties { canFocus = false }  // kill clickable's internal focusable
.clickable { onClick() }    // pointer click only, no focus
```

Key ordering rules:
1. `focusTarget()` BEFORE `focusProperties` — our target is focusable
2. `focusProperties { canFocus = false }` BEFORE `clickable` — blocks clickable's internal focusable
3. `clickable` provides pointer event handling only

**Never use bare `.clickable()` on focusable elements** — it always creates an internal `focusable()` → `bringIntoView`. Either:
- Use the `focusTarget` + `focusProperties` + `clickable` pattern above, OR
- Use `clickableWithPointer()` which already sets `canFocus = false`, combined with `focusTarget()` before it

### Focus return after inline edit

When Escape or Enter ends an inline edit (e.g., editing a title or ID), focus must return to the trigger element (the pencil icon), not get lost. Use `FocusRequester`:

```kotlin
val pencilFocusRequester = remember { FocusRequester() }

LaunchedEffect(isEditing) {
    if (isEditing) {
        textFieldFocusRequester.requestFocus()
    } else {
        pencilFocusRequester.requestFocus()  // return focus to trigger
    }
}
```

Without this, focus falls to the focus sink and subsequent Tab starts from the beginning.

---

## Making icon buttons focusable

Jewel's `IconButton` uses `focusable()` internally, which triggers `bringIntoView` scroll. To avoid this while still supporting Tab focus, wrap `IconButton` in a `Box` with `focusTarget()`:

```kotlin
@Composable
fun FocusableIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var isFocused by remember { mutableStateOf(false) }
    val focusBorder = if (isFocused) accentColor else Color.Transparent
    val focusManager = LocalFocusManager.current

    Box(
        modifier = modifier
            .border(1.dp, focusBorder, RoundedCornerShape(4.dp))
            .onFocusChanged { isFocused = it.isFocused }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.Enter, Key.NumPadEnter, Key.Spacebar -> { onClick(); true }
                    Key.Tab -> {
                        focusManager.moveFocus(
                            if (event.isShiftPressed) FocusDirection.Previous
                            else FocusDirection.Next
                        )
                        true
                    }
                    else -> false
                }
            }
            .focusTarget(),
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier.focusProperties { canFocus = false },  // prevent double focus
        ) { content() }
    }
}
```

The outer `Box(focusTarget())` receives Tab focus and handles keys. The inner `IconButton` has `canFocus = false` so it doesn't create a second focus target or trigger `bringIntoView`.

---

## Hover-only elements must be visible when focused

Elements hidden with `alpha(0f)` when not hovered (like trash/delete icons) must become visible when they receive Tab focus. Otherwise keyboard users Tab into invisible elements.

```kotlin
var isTrashFocused by remember { mutableStateOf(false) }

FocusableIconButton(
    onClick = onDelete,
    modifier = Modifier
        .onFocusChanged { isTrashFocused = it.hasFocus }
        .alpha(if (isHovered || isTrashFocused) 1f else 0f),
) { /* icon */ }
```

Use `hasFocus` (not `isFocused`) on the external modifier because `isFocused` only reports the modifier node's own focus, while `hasFocus` includes descendants — and the actual `focusTarget()` is a descendant inside the component.

---

## Focus ring pattern

Use the theme's accent color for focus indicators. The ring should be visible only when the element has keyboard focus:

```kotlin
var isFocused by remember { mutableStateOf(false) }
val focusBorder = if (isFocused) ThemeColors.accent else Color.Transparent

Box(
    modifier = Modifier
        .border(1.dp, focusBorder, RoundedCornerShape(4.dp))
        .onFocusChanged { isFocused = it.isFocused }
        // ... other modifiers
)
```

Apply focus rings to: icon buttons, action text buttons, edit toggle icons, tag add/remove buttons, editable ID/title pencils, and any other interactive non-text-field element.

Text fields use a different pattern — accent-colored border replaces the normal border when focused:

```kotlin
val borderColor = when {
    readOnly -> Color.Transparent
    isFocused -> ThemeColors.accent
    else -> ThemeColors.border
}
```

---

## Popup focus management

When a popup opens via keyboard (Enter on a "+" button), the input inside must receive focus automatically:

```kotlin
val inputFocusRequester = remember { FocusRequester() }

LaunchedEffect(Unit) {
    inputFocusRequester.requestFocus()
}

EditableComboBox(
    modifier = Modifier.focusRequester(inputFocusRequester),
    // ...
)
```

The popup should close when focus leaves entirely:

```kotlin
Box(
    modifier = Modifier
        .onFocusChanged { state ->
            if (state.hasFocus) wasFocused = true
            if (!state.hasFocus && wasFocused) onDismiss()
        }
        .focusTarget(),
) {
    // popup content
}
```

Use `wasFocused` guard — without it, `onFocusChanged` fires with `hasFocus=false` on initial composition before the input receives focus, immediately dismissing the popup.

### Jewel components consume key events

Jewel `EditableComboBox` (and potentially other Jewel input components) consume ALL key events internally — Compose `onPreviewKeyEvent` modifiers on or above them **never fire**. Confirmed via logging: zero `onPreviewKeyEvent` callbacks when typing or pressing Escape.

**Workaround for Escape:** catch at AWT level via `KeyEventDispatcher`:

```kotlin
DisposableEffect(Unit) {
    val dispatcher = java.awt.KeyEventDispatcher { event ->
        if (event.id == KeyEvent.KEY_PRESSED && event.keyCode == KeyEvent.VK_ESCAPE) {
            onDismiss()
            true
        } else false
    }
    KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(dispatcher)
    onDispose {
        KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(dispatcher)
    }
}
```

This fires before Jewel sees the event. Clean up in `onDispose` is critical — without it, the dispatcher leaks and catches Escape globally.

---

## Compose Desktop gotchas

### Hover tracking with `clickable`

`MutableInteractionSource` + `collectIsHoveredAsState()` does not reliably track hover when combined with `clickable()`. The `clickable` modifier creates its own internal interaction source that intercepts hover events.

**Workaround:** Pass your interaction source to `clickable` directly:

```kotlin
val hoverSource = remember { MutableInteractionSource() }
val isHovered by hoverSource.collectIsHoveredAsState()

Modifier.clickable(
    interactionSource = hoverSource,
    indication = null,
    onClick = onClick,
)
```

### `FileChooser` from Compose handlers

`FileChooser.chooseFile()` cannot be called directly from a Compose click handler — it triggers an assertion failure because it requires a standard EDT call frame, not a Compose event-processing frame.

```kotlin
// Crashes:
onClick = { FileChooser.chooseFile(descriptor, project, null) }

// Works:
onClick = {
    ApplicationManager.getApplication().invokeLater {
        FileChooser.chooseFile(descriptor, project, null)
    }
}
```

### `awaitFirstDown` consumption

`awaitFirstDown(requireUnconsumed = false)` on a parent intercepts ALL pointer events including clicks on child elements. Use `requireUnconsumed = true` when you only want to handle clicks on empty background space.

### Scroll drift from `focusable()`

`focusable()` includes `bringIntoView` behavior. If a `focusable()` element is at position (0,0) and receives focus repeatedly (e.g., on every background click), the scroll container will slowly animate toward it, creating a persistent upward drift. Use `focusTarget()` for elements that need to receive focus without triggering scroll.

---

## Semantics for screen readers

Compose Multiplatform provides a semantic tree that assistive technologies (VoiceOver on macOS, JAWS/NVDA on Windows via Java Access Bridge) traverse instead of the raw UI tree. Adding semantics makes UI elements meaningful to screen readers.

### `contentDescription` for non-text elements

Icons, icon buttons, and visual-only elements need `contentDescription` so screen readers can announce them:

```kotlin
Icon(
    imageVector = Icons.Default.Delete,
    contentDescription = "Delete step",  // read by screen reader
)
```

For Jewel `Icon` with `IntelliJIconKey`, pass `contentDescription` parameter directly.

### `semantics` modifier for custom components

When composing custom interactive elements (not using standard Button/IconButton), declare their role and description explicitly:

```kotlin
Box(
    modifier = Modifier
        .clickable { onClick() }
        .semantics(mergeDescendants = true) {
            role = Role.Button
            contentDescription = "Click to add attachment"
        }
)
```

`mergeDescendants = true` combines the semantics of child elements into one node — screen readers announce the whole group as a single item instead of reading each child separately. Use this on container elements that represent a single interactive concept (a card, a row with icon + text, etc.).

### Traversal order

Control the order screen readers announce elements using `traversalIndex` and `isTraversalGroup`:

```kotlin
Box(
    modifier = Modifier.semantics {
        isTraversalGroup = true
        traversalIndex = -1f  // read before elements with default (0f)
    }
)
```

Lower `traversalIndex` values are read first. Useful for floating action buttons or priority controls that should be announced before the main content.

### Platform setup

| Platform | Status | Setup |
|----------|--------|-------|
| macOS | Supported | Works out of the box |
| Windows | Supported | Enable Java Access Bridge: `%JAVA_HOME%\bin\jabswitch.exe /enable`; include `jdk.accessibility` module in native distribution |
| Linux | Not supported | — |

For Windows native distributions, add to `build.gradle.kts`:

```kotlin
compose.desktop {
    application {
        nativeDistributions {
            modules("jdk.accessibility")
        }
    }
}
```

### Semantic properties reference

The framework maps `SemanticsNode` properties to platform accessibility APIs:

| SemanticsProperty | Android (AccessibilityNodeInfo) | iOS (UIAccessibilityElement) | Desktop |
|---|---|---|---|
| `ContentDescription` | `getContentDescription()` | `accessibilityLabel` | Java Access Bridge |
| `Text` / `EditableText` | `getText()` | `accessibilityLabel` (fallback) | — |
| `Selected` | `isSelected()` | `accessibilityTraits.selected` | — |
| `Heading` | heading role | `UIAccessibilityTraitHeader` | — |
| `OnClick` action | clickable | `UIAccessibilityTraitButton` | — |

iOS mapping uses lazy evaluation — `UIAccessibilityElement` instances are created on demand when VoiceOver queries the tree, not eagerly for all nodes.

### iOS-specific: simulator testing

For testing with VoiceOver in iOS simulator, enable synchronous accessibility sync:

```kotlin
fun MainViewController() = ComposeUIViewController(
    configure = {
        accessibilitySyncOptions = Always()  // sync semantic tree every frame
    }
) { App() }
```

Without `Always()`, accessibility updates may be delayed or missed in the simulator.

### iOS-specific: native view interop

When embedding UIKit views (`UIKitView`), control accessibility ownership:

```kotlin
// Compose owns accessibility (screen reader reads contentDescription):
UIKitView(
    factory = { MKMapView() },
    modifier = Modifier.semantics { contentDescription = "Map of NYC" },
    properties = UIKitInteropProperties(isNativeAccessibilityEnabled = false),
)

// Native UIKit owns accessibility (screen reader reads native a11y tree):
UIKitView(
    factory = { MKMapView() },
    properties = UIKitInteropProperties(isNativeAccessibilityEnabled = true),
)
```

### Testing accessibility

- **macOS:** Xcode → Open Developer Tool → Accessibility Inspector
- **Windows:** JAWS (Show Speech History) or NVDA (Speech Viewer)
- **iOS simulator:** VoiceOver + `accessibilitySyncOptions = Always()`
- **Android:** Accessibility Scanner app, TalkBack, Espresso accessibility checks

### UI testing with JUnit

Automated accessibility testing via `composeApp/src/desktopTest/kotlin`:

```kotlin
// build.gradle.kts
val desktopTest by getting {
    dependencies {
        implementation(compose.desktop.uiTestJUnit4)
        implementation(compose.desktop.currentOs)
    }
}
```

```kotlin
class AccessibilityTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `button is focusable and clickable`() {
        rule.setContent {
            MyButton(modifier = Modifier.testTag("myButton"))
        }
        rule.onNodeWithTag("myButton").assertExists()
        rule.onNodeWithTag("myButton").performClick()
    }
}
```

Run: `./gradlew desktopTest`

---

## Keyboard event handling

Compose Desktop provides two scopes for keyboard events and two modifier variants:

### `onPreviewKeyEvent` vs `onKeyEvent`

- **`onPreviewKeyEvent`**: fires BEFORE the default action. Use this for keyboard shortcuts and Tab handling — you can intercept and consume the event before a text field processes it.
- **`onKeyEvent`**: fires AFTER the default action. Use for post-processing or unhandled key fallbacks.

Both return `Boolean` — `true` = consumed, `false` = pass to next handler.

### Window-level key events

For global shortcuts that work regardless of focus:

```kotlin
Window(
    onPreviewKeyEvent = {
        if (it.isCtrlPressed && it.key == Key.S && it.type == KeyEventType.KeyDown) {
            save()
            true
        } else false
    }
) { /* content */ }
```

Available on `Window()`, `singleWindowApplication()`, and `DialogWindow()`.

### Key event properties

- `event.key` — `Key.Tab`, `Key.Enter`, `Key.Escape`, `Key.Spacebar`, etc.
- `event.type` — `KeyEventType.KeyDown`, `KeyEventType.KeyUp`
- `event.isCtrlPressed`, `event.isShiftPressed`, `event.isAltPressed`, `event.isMetaPressed`

Always check `event.type == KeyEventType.KeyDown` — without this, handlers fire twice (once on press, once on release).

---

## Mouse and pointer events

### Click variants

```kotlin
// Cross-platform, stable — primary button only:
Modifier.combinedClickable(
    onClick = { /* single */ },
    onDoubleClick = { /* double */ },
    onLongClick = { /* long */ },
)

// Desktop-only, experimental — any button + modifiers:
@OptIn(ExperimentalFoundationApi::class)
Modifier.onClick(
    matcher = PointerMatcher.mouse(PointerButton.Secondary),
    keyboardModifiers = { isAltPressed },
) { /* right-click + Alt */ }
```

**Gotcha:** `onClick` (experimental) has NO default `indication` or `semantics` — add them manually if needed. It also does NOT trigger on Enter key — keyboard activation must be handled separately.

### Hover detection

```kotlin
// Stable approach — pointerInput loop:
Modifier.pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent()
            // filter synthetic Move events on relayout
        }
    }
}
```

**Gotcha:** Compose Desktop sends synthetic `Move` events on each relayout. Filter by checking `event.type != PointerEventType.Move` when you only care about enter/exit/press.

### Raw AWT event access

```kotlin
@OptIn(ExperimentalComposeUiApi::class)
Modifier.onPointerEvent(PointerEventType.Press) {
    val screenLocation = it.awtEventOrNull?.locationOnScreen
}
```

---

## Context menus

### Custom context menu on any element

```kotlin
ContextMenuArea(items = {
    listOf(
        ContextMenuItem("Delete") { onDelete() },
        ContextMenuItem("Duplicate") { onDuplicate() },
    )
}) {
    // content that gets the context menu on right-click
}
```

### Built-in text context menu

- `TextField` gets cut/copy/paste/select-all automatically
- `Text` needs `SelectionContainer { Text("...") }` for copy support
- Add custom items via `ContextMenuDataProvider(items = { ... }) { content }`

---

## Swing interop considerations

When Compose runs inside `ComposePanel` (Jewel/IntelliJ), these constraints apply:

### Popups and dialogs

All Compose popups, tooltips, and context menus render WITHIN the `ComposePanel` bounds and are clipped to it. For IntelliJ plugins, prefer platform dialogs (`Messages.showDialog`, `DialogWrapper`) over Compose popup/dialog composables when the content might overflow.

### `SwingPanel` layering

`SwingPanel` is always rendered ABOVE Compose content. Compose UI underneath is clipped. Keep this in mind when mixing Swing and Compose components.

### Threading

- Compose click handlers run on EDT but in a Compose event-processing frame
- Swing APIs that expect a "clean" EDT frame (like `FileChooser.chooseFile()`) need `ApplicationManager.getApplication().invokeLater { }` wrapping
- `ComposePanel` must be created and added to the hierarchy on EDT

### Off-screen rendering (experimental)

Fixes rendering glitches when `ComposePanel` is shown/hidden/resized:

```kotlin
val composePanel = ComposePanel(renderSettings = RenderSettings.SwingGraphics)
// or globally: -Dcompose.swing.render.on.graphics=true
```

### Interop blending (experimental)

Fixes clipping/overlap issues with `SwingPanel`:

```kotlin
System.setProperty("compose.interop.blending", "true")  // before any Compose code
```

---

## Scrollbar accessibility

Desktop scrollbars are separate composables that share state with the scrollable container:

```kotlin
val scrollState = rememberScrollState()

Box {
    Column(Modifier.verticalScroll(scrollState).padding(end = 12.dp)) {
        // content
    }
    VerticalScrollbar(
        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
        adapter = rememberScrollbarAdapter(scrollState),
    )
}
```

For `LazyColumn`/`LazyRow`, use `rememberScrollbarAdapter(lazyListState)`.

**Gotcha:** Add `padding(end = 12.dp)` to content to avoid overlap with vertical scrollbar.
