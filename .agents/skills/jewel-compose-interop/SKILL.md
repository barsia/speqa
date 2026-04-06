---
name: jewel-compose-interop
description: Use when managing mutableStateOf across Swing/Compose boundaries, wiring Jewel themes with EditorColorsManager/UIManager, bridging EDT and Compose coroutines via MutableSharedFlow, optimizing verticalScroll rendering, debugging stale closures or missed recompositions in JewelComposePanel, or implementing lazy Compose panel mount in IntelliJ editors.
user-invocable: true
---

# Jewel / Compose / Swing Interop

Non-obvious patterns at the boundary of Swing EDT, Compose runtime, and Jewel theming in IntelliJ plugins. All patterns discovered through real bugs.

## When to activate

- State changes from Swing Timer don't trigger Compose recomposition
- Scroll sync between IntelliJ Editor and Compose ScrollState
- Stale closures in LaunchedEffect / snapshotFlow
- Theme colors not updating on IDE theme switch
- Compose panel blank on editor open

---

## State: Swing → Compose

### mutableStateOf works for simple cases

Writes from Swing Timer or DocumentListener to `mutableStateOf` trigger recomposition when Compose reads `.value` during composition:

```kotlin
private var parsed by mutableStateOf(parse(document.text))
private val refreshTimer = Timer(300) { parsed = parse(document.text) }
```

### mutableStateOf fails for continuous streams

`snapshotFlow { state.value }` and `LaunchedEffect(state)` may never fire for state changed from Swing Timer in JewelComposePanel. Use `MutableSharedFlow`:

```kotlin
private val _fraction = MutableSharedFlow<Float>(
    extraBufferCapacity = 1,
    onBufferOverflow = BufferOverflow.DROP_OLDEST,
)

// Swing: fire and forget
_fraction.tryEmit(value)

// Compose: always receives
LaunchedEffect(Unit) {
    _fraction.collectLatest { v -> scrollState.scrollTo(target) }
}
```

- `DROP_OLDEST` — latest value always gets through
- `collectLatest` — cancels in-progress suspend functions when newer value arrives
- `tryEmit` — thread-safe, never blocks

---

## State: Compose → Swing

Post to EDT via `invokeLater`, always check `isDisposed`:

```kotlin
ApplicationManager.getApplication().invokeLater {
    if (editor.isDisposed) return@invokeLater
    editor.scrollingModel.scrollVertically(targetY)
}
```

---

## Feedback Loop Prevention

Use time-window suppression, not boolean flags:

```kotlin
private var suppressUntil = 0L

suppressUntil = System.currentTimeMillis() + 220L  // before emitting
if (System.currentTimeMillis() < suppressUntil) return  // guard other direction
```

Boolean flags fail because Swing scroll events fire asynchronously — the flag is cleared before the listener processes.

---

## Stale Closure Prevention

Callbacks captured in `LaunchedEffect` with stable keys **must** use `rememberUpdatedState`:

```kotlin
val currentController by rememberUpdatedState(controller)
LaunchedEffect(scrollState) {
    snapshotFlow { scrollState.value }.collect { y ->
        currentController.onScroll(y)
    }
}
```

Without it, the closure captures the value from first composition forever. Applies to any lambda in long-lived coroutines.

---

## Theming

Read colors from `UIManager` with fallback — never hardcode hex values:

```kotlin
private fun themeColor(key: String, fallback: AwtColor): Color {
    val awt = UIManager.getColor(key) ?: fallback
    return Color(awt.red, awt.green, awt.blue, awt.alpha)
}
```

Force recomposition on theme switch via `LafManagerListener` + revision counter:

```kotlin
private var themeRevision by mutableStateOf(0L)
connection.subscribe(LafManagerListener.TOPIC, LafManagerListener { themeRevision++ })
// In Compose: read themeRevision to trigger recomposition
```

---

## Lazy Compose Mount

Don't create JewelComposePanel in the constructor — defer until displayable:

```kotlin
component.addHierarchyListener { event ->
    if ((event.changeFlags and HierarchyEvent.DISPLAYABILITY_CHANGED.toLong()) != 0L) {
        scheduleComposeMountIfNeeded()
    }
}
```

Prevents Compose initialization during editor restore when the panel isn't visible.

---

## Performance

**scrollTo vs animateScrollTo for sync:** Use `scrollTo` (instant) for continuous scroll sync. `animateScrollTo` suspends for ~300ms, blocking the collector and dropping intermediate values.

**Column + verticalScroll vs LazyColumn:** Use `Column` when all items need simultaneous composition (drag-reorder, position measurement). `LazyColumn` for 100+ items.

---

## Common Pitfalls

| Pitfall | Fix |
|---------|-----|
| `mutableStateOf` from Timer doesn't recompose | `MutableSharedFlow` + `collectLatest` |
| Scroll feedback loop | Time-window suppression (220ms) |
| `animateScrollTo` drops values | `scrollTo` + `collectLatest` |
| Stale closure in LaunchedEffect | `rememberUpdatedState` |
| Editor API called off EDT | `invokeLater` + `isDisposed` check |
| Hardcoded colors | `UIManager.getColor(key)` with fallback |
| Layout shift on state change | Color only, no `FontWeight` change |
