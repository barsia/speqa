# Snapshot-Mode Input Forwarding Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the Linux snapshot-mode preview interactive: clicks, drag-to-select, mouse wheel scrolling, and keyboard input must reach the offscreen WebKitGTK widget, so users can click into the preview, type into form fields, edit steps, and follow links.

**Architecture:** Swing-side listeners on `SwingWebViewHostPanel` capture mouse and key events for the snapshot-painted region and forward them through JNI to the native bridge. The bridge translates each event into a `GdkEvent` (Button / Motion / Scroll / Key) targeted at the WebView widget's `GdkWindow`, dispatched via `gtk_main_do_event`. After each input event the bridge schedules a fresh snapshot so visual feedback (caret, hover, scroll, layout) propagates back to Swing within a frame. The X11 native-overlay path is unaffected — it has direct OS input and doesn't need any of this.

**Tech Stack:** Kotlin / AWT (`MouseListener`, `MouseMotionListener`, `MouseWheelListener`, `KeyEventDispatcher`), JNI, Rust 1.x with GTK 3 / GDK 3 FFI, GdkEvent struct layouts, GdkDevice from `gdk_display_get_default_seat`.

---

## Why this plan exists (background)

After routing X11 through the snapshot backend (plan: `2026-05-17-linux-snapshot-always.md`) and seeding the panels with their real model (commit `a56368a`), the user reports preview is now visible and shows correct content on first paint — but **clicks and keypresses do nothing**.

Diagnosis:
- `SwingWebViewHostPanel` paints the snapshot bitmap delivered from native (`setSnapshotImage`) but never forwards mouse or key events.
- The native bridge has zero input-event entry points: `grep -n "fn.*[Mm]ouse\|GdkEvent\|gdk_event" native/LinuxWebKitGtkBridge/src/lib.rs` is empty.
- The WaylandSnapshot path was the only consumer of this code before the X11 reroute. Either Wayland users never tried interactive use, or the gap was known and accepted as a preview-only fallback. With X11 now using the same path, this is no longer acceptable.

The fix is to add a Swing → JNI → GDK event-injection pipeline. Coordinates translate by the existing `scale` factor; modifier and button state come from AWT event masks; key codes translate from Java `VK_*` to GDK keyvals through a small mapping table.

## File structure

| File | Action | Responsibility |
|------|--------|----------------|
| `native/LinuxWebKitGtkBridge/src/lib.rs` | Modify | New GdkEvent struct layouts (Button / Motion / Scroll / Key); new JNI exports `dispatchMouseButtonNative`, `dispatchMouseMotionNative`, `dispatchMouseScrollNative`, `dispatchKeyNative`; helper to obtain the pointer / keyboard `GdkDevice` from the display's default seat; snapshot scheduling on every dispatched event |
| `src/main/kotlin/io/github/barsia/speqa/webview/internal/linux/LinuxWebKitGtkBridge.kt` | Modify | Mirror the new JNI symbols as `@JvmStatic private external fun …` + thin wrapper functions |
| `src/main/kotlin/io/github/barsia/speqa/webview/internal/linux/LinuxWebKitWebViewFacade.kt` | Modify | Expose `dispatchMouseButton(handle, …)`, `dispatchMouseMotion(handle, …)`, `dispatchMouseScroll(handle, …)`, `dispatchKey(handle, …)` calling into the bridge with the same EDT discipline already used by `setBounds` |
| `src/main/kotlin/io/github/barsia/speqa/webview/internal/linux/LinuxWaylandSnapshotWebViewHostPeer.kt` | Modify | In `attach(host)`, install Mouse/MouseMotion/MouseWheel listeners + a `KeyEventDispatcher` on the host panel; map AWT events to native dispatch calls; remove the listeners on `detach()` |
| `src/main/kotlin/io/github/barsia/speqa/webview/internal/linux/AwtToGtkKeyMap.kt` | Create | Pure mapping from Java `KeyEvent.getExtendedKeyCode()` / `KeyEvent.getKeyChar()` to GDK keyval (`Int`). Table covers ASCII printable chars + the standard navigation/editing keys (Return, BackSpace, Delete, Tab, Escape, Left/Right/Up/Down, Home/End, PageUp/PageDown, Insert, F1..F12, plus modifier-only keys for proper modifier state tracking) |
| `src/test/kotlin/io/github/barsia/speqa/webview/internal/linux/AwtToGtkKeyMapTest.kt` | Create | JUnit 4 tests for the mapping table — ASCII chars, named keys, unknown keys returning a sentinel |
| `native/LinuxWebKitGtkBridge/Cargo.toml` | (no change expected) | Existing dependencies cover the FFI; no new crates needed |

The X11 native-overlay path (`LinuxX11NativeWebViewHostPeer.kt`) is NOT modified. That code stays dormant; if someone re-enables it in the future they have OS-level input for free.

---

## Constants reference

These integer constants are referenced throughout. Put them in lib.rs near the top with the other constants, and mirror as needed in Kotlin or the key-map file.

```
// GdkEventType
const GDK_BUTTON_PRESS: i32 = 4;
const GDK_2BUTTON_PRESS: i32 = 5;
const GDK_3BUTTON_PRESS: i32 = 6;
const GDK_BUTTON_RELEASE: i32 = 7;
const GDK_KEY_PRESS: i32 = 8;
const GDK_KEY_RELEASE: i32 = 9;
const GDK_MOTION_NOTIFY: i32 = 3;
const GDK_SCROLL: i32 = 31;

// GdkScrollDirection
const GDK_SCROLL_UP: i32 = 0;
const GDK_SCROLL_DOWN: i32 = 1;
const GDK_SCROLL_LEFT: i32 = 2;
const GDK_SCROLL_RIGHT: i32 = 3;
const GDK_SCROLL_SMOOTH: i32 = 4;

// GdkModifierType (relevant bits)
const GDK_SHIFT_MASK: u32 = 1 << 0;
const GDK_CONTROL_MASK: u32 = 1 << 2;
const GDK_MOD1_MASK: u32 = 1 << 3;     // Alt
const GDK_META_MASK: u32 = 1 << 28;     // Meta / Super
const GDK_BUTTON1_MASK: u32 = 1 << 8;
const GDK_BUTTON2_MASK: u32 = 1 << 9;
const GDK_BUTTON3_MASK: u32 = 1 << 10;
```

---

### Task 1: Native GDK event plumbing in lib.rs

**Files:**
- Modify: `native/LinuxWebKitGtkBridge/src/lib.rs`

This task adds the FFI surface to construct, fill, and dispatch GdkEvents — the foundation every subsequent task builds on. No JNI export yet, no Kotlin changes — just the Rust helpers and tests via cargo. We split this out so subsequent tasks can focus on one event type at a time.

- [ ] **Step 1: Add the GdkEvent struct layouts and constants**

Locate the existing `extern "C" { … }` block in `lib.rs` (around line 161). After the existing GTK / WebKit declarations, add (the constants go near the top of the file with other consts):

```rust
// Near top of file, with other constants
const GDK_BUTTON_PRESS: i32 = 4;
const GDK_2BUTTON_PRESS: i32 = 5;
const GDK_BUTTON_RELEASE: i32 = 7;
const GDK_MOTION_NOTIFY: i32 = 3;
const GDK_SCROLL: i32 = 31;
const GDK_KEY_PRESS: i32 = 8;
const GDK_KEY_RELEASE: i32 = 9;

const GDK_SCROLL_SMOOTH: i32 = 4;

const GDK_SHIFT_MASK: u32 = 1 << 0;
const GDK_CONTROL_MASK: u32 = 1 << 2;
const GDK_MOD1_MASK: u32 = 1 << 3;
const GDK_META_MASK: u32 = 1 << 28;
```

In the `extern "C"` block, add these declarations (alphabetised within the block for readability):

```rust
    fn gdk_display_get_default() -> *mut GdkDisplay;
    fn gdk_display_get_default_seat(display: *mut GdkDisplay) -> *mut GdkSeat;
    fn gdk_event_new(type_: i32) -> *mut GdkEventGeneric;
    fn gdk_event_free(event: *mut GdkEventGeneric);
    fn gdk_event_set_device(event: *mut GdkEventGeneric, device: *mut GdkDevice);
    fn gtk_main_do_event(event: *mut GdkEventGeneric);
    fn gdk_seat_get_pointer(seat: *mut GdkSeat) -> *mut GdkDevice;
    fn gdk_seat_get_keyboard(seat: *mut GdkSeat) -> *mut GdkDevice;
```

Add the opaque types near the other opaque types:

```rust
#[repr(C)] pub struct GdkDisplay { _private: [u8; 0] }
#[repr(C)] pub struct GdkSeat { _private: [u8; 0] }
#[repr(C)] pub struct GdkDevice { _private: [u8; 0] }
```

Add the typed event structs. CAUTION: the field layout must match GTK 3's `GdkEventButton`, `GdkEventMotion`, `GdkEventScroll`, `GdkEventKey` exactly. Use `#[repr(C)]`. (`GdkEventGeneric` below is a deliberately oversized union placeholder — `gdk_event_new` returns a heap allocation big enough for the largest event variant; casting to the specific struct pointer for writes is safe within that allocation.)

```rust
#[repr(C)]
pub struct GdkEventGeneric {
    pub type_: i32,
    pub window: *mut GdkWindow,
    pub send_event: i8,
    // Padding to cover the largest event variant; gdk_event_new() returns sizeof(union).
    _padding: [u8; 200],
}

#[repr(C)]
pub struct GdkEventButton {
    pub type_: i32,
    pub window: *mut GdkWindow,
    pub send_event: i8,
    pub time: u32,
    pub x: f64,
    pub y: f64,
    pub axes: *mut f64,
    pub state: u32,
    pub button: u32,
    pub device: *mut GdkDevice,
    pub x_root: f64,
    pub y_root: f64,
}

#[repr(C)]
pub struct GdkEventMotion {
    pub type_: i32,
    pub window: *mut GdkWindow,
    pub send_event: i8,
    pub time: u32,
    pub x: f64,
    pub y: f64,
    pub axes: *mut f64,
    pub state: u32,
    pub is_hint: i16,
    pub device: *mut GdkDevice,
    pub x_root: f64,
    pub y_root: f64,
}

#[repr(C)]
pub struct GdkEventScroll {
    pub type_: i32,
    pub window: *mut GdkWindow,
    pub send_event: i8,
    pub time: u32,
    pub x: f64,
    pub y: f64,
    pub state: u32,
    pub direction: i32,
    pub device: *mut GdkDevice,
    pub x_root: f64,
    pub y_root: f64,
    pub delta_x: f64,
    pub delta_y: f64,
    pub is_stop_and_flags: u32,
}

#[repr(C)]
pub struct GdkEventKey {
    pub type_: i32,
    pub window: *mut GdkWindow,
    pub send_event: i8,
    pub time: u32,
    pub state: u32,
    pub keyval: u32,
    pub length: i32,
    pub string: *mut std::os::raw::c_char,
    pub hardware_keycode: u16,
    pub group: u8,
    pub is_modifier_and_flags: u8,
}
```

- [ ] **Step 2: Add internal dispatch helpers**

In a fresh `fn` section near the existing `apply_*` helpers (around line 1085), add private helpers that the upcoming JNI exports will reuse. These are not unsafe at the type level but operate on raw pointers, so individual unsafe blocks are explicit:

```rust
fn snapshot_window(view: &NativeWebView) -> *mut GdkWindow {
    if view.webview.is_null() {
        return std::ptr::null_mut();
    }
    unsafe { gtk_widget_get_window(view.webview) }
}

fn default_pointer_device() -> *mut GdkDevice {
    unsafe {
        let display = gdk_display_get_default();
        if display.is_null() { return std::ptr::null_mut(); }
        let seat = gdk_display_get_default_seat(display);
        if seat.is_null() { return std::ptr::null_mut(); }
        gdk_seat_get_pointer(seat)
    }
}

fn default_keyboard_device() -> *mut GdkDevice {
    unsafe {
        let display = gdk_display_get_default();
        if display.is_null() { return std::ptr::null_mut(); }
        let seat = gdk_display_get_default_seat(display);
        if seat.is_null() { return std::ptr::null_mut(); }
        gdk_seat_get_keyboard(seat)
    }
}

fn dispatch_button_event(
    view: &NativeWebView,
    type_: i32,
    x: f64,
    y: f64,
    button: u32,
    state: u32,
) {
    if view.destroyed || view.webview.is_null() { return; }
    let window = snapshot_window(view);
    if window.is_null() { return; }
    let device = default_pointer_device();
    unsafe {
        let raw = gdk_event_new(type_);
        if raw.is_null() { return; }
        let event = raw as *mut GdkEventButton;
        (*event).type_ = type_;
        (*event).window = window;
        (*event).send_event = 1;
        (*event).time = 0;
        (*event).x = x;
        (*event).y = y;
        (*event).axes = std::ptr::null_mut();
        (*event).state = state;
        (*event).button = button;
        (*event).device = device;
        (*event).x_root = x;
        (*event).y_root = y;
        gtk_main_do_event(raw);
        gdk_event_free(raw);
    }
}

fn dispatch_motion_event(view: &NativeWebView, x: f64, y: f64, state: u32) {
    if view.destroyed || view.webview.is_null() { return; }
    let window = snapshot_window(view);
    if window.is_null() { return; }
    let device = default_pointer_device();
    unsafe {
        let raw = gdk_event_new(GDK_MOTION_NOTIFY);
        if raw.is_null() { return; }
        let event = raw as *mut GdkEventMotion;
        (*event).type_ = GDK_MOTION_NOTIFY;
        (*event).window = window;
        (*event).send_event = 1;
        (*event).time = 0;
        (*event).x = x;
        (*event).y = y;
        (*event).axes = std::ptr::null_mut();
        (*event).state = state;
        (*event).is_hint = 0;
        (*event).device = device;
        (*event).x_root = x;
        (*event).y_root = y;
        gtk_main_do_event(raw);
        gdk_event_free(raw);
    }
}

fn dispatch_scroll_event(
    view: &NativeWebView,
    x: f64,
    y: f64,
    delta_x: f64,
    delta_y: f64,
    state: u32,
) {
    if view.destroyed || view.webview.is_null() { return; }
    let window = snapshot_window(view);
    if window.is_null() { return; }
    let device = default_pointer_device();
    unsafe {
        let raw = gdk_event_new(GDK_SCROLL);
        if raw.is_null() { return; }
        let event = raw as *mut GdkEventScroll;
        (*event).type_ = GDK_SCROLL;
        (*event).window = window;
        (*event).send_event = 1;
        (*event).time = 0;
        (*event).x = x;
        (*event).y = y;
        (*event).state = state;
        (*event).direction = GDK_SCROLL_SMOOTH;
        (*event).device = device;
        (*event).x_root = x;
        (*event).y_root = y;
        (*event).delta_x = delta_x;
        (*event).delta_y = delta_y;
        (*event).is_stop_and_flags = 0;
        gtk_main_do_event(raw);
        gdk_event_free(raw);
    }
}

fn dispatch_key_event(
    view: &NativeWebView,
    type_: i32,
    keyval: u32,
    state: u32,
) {
    if view.destroyed || view.webview.is_null() { return; }
    let window = snapshot_window(view);
    if window.is_null() { return; }
    let device = default_keyboard_device();
    unsafe {
        let raw = gdk_event_new(type_);
        if raw.is_null() { return; }
        let event = raw as *mut GdkEventKey;
        (*event).type_ = type_;
        (*event).window = window;
        (*event).send_event = 1;
        (*event).time = 0;
        (*event).state = state;
        (*event).keyval = keyval;
        (*event).length = 0;
        (*event).string = std::ptr::null_mut();
        (*event).hardware_keycode = 0;
        (*event).group = 0;
        (*event).is_modifier_and_flags = 0;
        // Attach device through the public setter, since GdkEventKey doesn't have a device field directly.
        gdk_event_set_device(raw, device);
        gtk_main_do_event(raw);
        gdk_event_free(raw);
    }
}
```

- [ ] **Step 3: Verify cargo build**

```bash
cd /home/siarhei/speqa/speqa/native/LinuxWebKitGtkBridge && cargo build --release --no-default-features --features webkit40 --target-dir target-wk40 2>&1 | tail -10
```

Expected: `Finished` with no errors. If GDK functions are missing from system headers, the linker error will say `undefined reference to gdk_event_new` etc. — that means `libgtk-3-dev` headers might be incomplete; the symbols are in libgdk-3 which is already linked transitively via gtk+-x11-3.0.

- [ ] **Step 4: Commit**

```bash
cd /home/siarhei/speqa/speqa && git add native/LinuxWebKitGtkBridge/src/lib.rs && git commit -m "native(linux): add GdkEvent layouts + dispatch helpers for snapshot input"
```

---

### Task 2: Mouse button + motion forwarding

**Files:**
- Modify: `native/LinuxWebKitGtkBridge/src/lib.rs`
- Modify: `src/main/kotlin/io/github/barsia/speqa/webview/internal/linux/LinuxWebKitGtkBridge.kt`
- Modify: `src/main/kotlin/io/github/barsia/speqa/webview/internal/linux/LinuxWebKitWebViewFacade.kt`
- Modify: `src/main/kotlin/io/github/barsia/speqa/webview/internal/linux/LinuxWaylandSnapshotWebViewHostPeer.kt`

- [ ] **Step 1: Add JNI exports in Rust for mouse button + motion**

Below the existing `Java_io_github_barsia_speqa_webview_internal_linux_LinuxWebKitGtkBridge_clearFocusNative` (around line 654), add:

```rust
#[no_mangle]
pub extern "system" fn Java_io_github_barsia_speqa_webview_internal_linux_LinuxWebKitGtkBridge_dispatchMouseButtonNative(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    handle: jlong,
    x: jdouble,
    y: jdouble,
    button: jint,
    state: jint,
    is_press: jboolean,
) {
    let type_ = if is_press != 0 { GDK_BUTTON_PRESS } else { GDK_BUTTON_RELEASE };
    run_with_handle(&mut env, handle, move |native| {
        enqueue_gtk_task(move || {
            with_locked_view(&native, |view| {
                dispatch_button_event(view, type_, x, y, button as u32, state as u32);
            });
            request_snapshot_later(native.clone(), 16);
        })
    });
}

#[no_mangle]
pub extern "system" fn Java_io_github_barsia_speqa_webview_internal_linux_LinuxWebKitGtkBridge_dispatchMouseMotionNative(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    handle: jlong,
    x: jdouble,
    y: jdouble,
    state: jint,
) {
    run_with_handle(&mut env, handle, move |native| {
        enqueue_gtk_task(move || {
            with_locked_view(&native, |view| {
                dispatch_motion_event(view, x, y, state as u32);
            });
            request_snapshot_later(native.clone(), 16);
        })
    });
}
```

The `request_snapshot_later` (existing helper, line ~1295) coalesces multiple requests under a 16ms timer — spamming it during a fast drag is safe.

- [ ] **Step 2: Add Kotlin JNI bindings + wrappers in `LinuxWebKitGtkBridge.kt`**

After the existing `external fun clearFocusNative` (around line 64), add:

```kotlin
  @JvmStatic
  private external fun dispatchMouseButtonNative(
    handle: Long,
    x: Double,
    y: Double,
    button: Int,
    state: Int,
    isPress: Boolean,
  )

  @JvmStatic
  private external fun dispatchMouseMotionNative(
    handle: Long,
    x: Double,
    y: Double,
    state: Int,
  )
```

After the existing `fun clearFocus(...)` wrapper (around line 88), add:

```kotlin
  fun dispatchMouseButton(handle: Long, x: Double, y: Double, button: Int, state: Int, isPress: Boolean) =
    dispatchMouseButtonNative(handle, x, y, button, state, isPress)

  fun dispatchMouseMotion(handle: Long, x: Double, y: Double, state: Int) =
    dispatchMouseMotionNative(handle, x, y, state)
```

- [ ] **Step 3: Add facade-level forwarding in `LinuxWebKitWebViewFacade.kt`**

After the existing `internal fun clearFocus()` (around line 228), add:

```kotlin
  internal fun dispatchMouseButton(x: Double, y: Double, button: Int, state: Int, isPress: Boolean) {
    val handle = nativeHandle
    if (handle == 0L || state.get() != State.Active) return
    runOnEdt { LinuxWebKitGtkBridge.dispatchMouseButton(handle, x, y, button, state, isPress) }
  }

  internal fun dispatchMouseMotion(x: Double, y: Double, state: Int) {
    val handle = nativeHandle
    if (handle == 0L || state.get() != State.Active) return
    runOnEdt { LinuxWebKitGtkBridge.dispatchMouseMotion(handle, x, y, state) }
  }
```

Note the name collision: parameter `state: Int` shadows the field `state: AtomicReference<State>` inside the lambda body. Resolve by renaming the parameter:

```kotlin
  internal fun dispatchMouseButton(x: Double, y: Double, button: Int, modifierState: Int, isPress: Boolean) {
    val handle = nativeHandle
    if (handle == 0L || this.state.get() != State.Active) return
    runOnEdt { LinuxWebKitGtkBridge.dispatchMouseButton(handle, x, y, button, modifierState, isPress) }
  }

  internal fun dispatchMouseMotion(x: Double, y: Double, modifierState: Int) {
    val handle = nativeHandle
    if (handle == 0L || this.state.get() != State.Active) return
    runOnEdt { LinuxWebKitGtkBridge.dispatchMouseMotion(handle, x, y, modifierState) }
  }
```

- [ ] **Step 4: Wire AWT listeners in `LinuxWaylandSnapshotWebViewHostPeer.kt`**

Read the current `attach()` and `detach()` (around lines 17–41). Then replace them and add listener fields:

```kotlin
internal class LinuxWaylandSnapshotWebViewHostPeer(
  private val facade: LinuxWebKitWebViewFacade,
) : NativeWebViewHostPeer {

  private var attached = false
  private var lastAppliedFrame: AppliedFrame? = null
  private var snapshotHost: SwingWebViewHostPanel? = null
  private var mouseListener: MouseInputAdapter? = null
  private var lastScale: Double = 1.0

  override fun attach(host: Component): Boolean {
    val hostPanel = host as? SwingWebViewHostPanel ?: return false
    snapshotHost = hostPanel
    facade.setSnapshotHandler { width, height, pixels ->
      hostPanel.setSnapshotImage(width, height, pixels)
    }
    facade.attachOffscreen()
    attached = true
    lastAppliedFrame = null

    installInputListeners(hostPanel)

    scheduleFrameUpdate(host)
    facade.setHidden(false)
    SwingUtilities.invokeLater { scheduleFrameUpdate(host) }
    return true
  }

  override fun detach() {
    if (!attached) return
    val host = snapshotHost
    if (host != null && mouseListener != null) {
      host.removeMouseListener(mouseListener)
      host.removeMouseMotionListener(mouseListener)
    }
    mouseListener = null
    snapshotHost?.clearSnapshotImage()
    snapshotHost = null
    facade.setSnapshotHandler(null)
    facade.detach()
    attached = false
    lastAppliedFrame = null
  }

  override fun scheduleFrameUpdate(host: Component) {
    if (!attached) return
    val frame = AppliedFrame(host.width, host.height)
    if (frame == lastAppliedFrame) return
    lastAppliedFrame = frame
    val scale = host.graphicsConfiguration?.defaultTransform?.scaleX?.takeIf { it > 0.0 } ?: 1.0
    lastScale = scale
    facade.setBounds(0, 0, host.width, host.height, scale)
  }

  // ... unchanged updateVisibility, requestFocus, clearFocus, AppliedFrame ...

  private fun installInputListeners(host: SwingWebViewHostPanel) {
    val adapter = object : MouseInputAdapter() {
      override fun mousePressed(e: MouseEvent) {
        facade.dispatchMouseButton(
          x = e.x.toDouble() * lastScale,
          y = e.y.toDouble() * lastScale,
          button = awtButtonToGdk(e.button),
          modifierState = awtModifiersToGdk(e.modifiersEx),
          isPress = true,
        )
        host.requestFocusInWindow()
      }

      override fun mouseReleased(e: MouseEvent) {
        facade.dispatchMouseButton(
          x = e.x.toDouble() * lastScale,
          y = e.y.toDouble() * lastScale,
          button = awtButtonToGdk(e.button),
          modifierState = awtModifiersToGdk(e.modifiersEx),
          isPress = false,
        )
      }

      override fun mouseMoved(e: MouseEvent) {
        facade.dispatchMouseMotion(
          x = e.x.toDouble() * lastScale,
          y = e.y.toDouble() * lastScale,
          modifierState = awtModifiersToGdk(e.modifiersEx),
        )
      }

      override fun mouseDragged(e: MouseEvent) {
        facade.dispatchMouseMotion(
          x = e.x.toDouble() * lastScale,
          y = e.y.toDouble() * lastScale,
          modifierState = awtModifiersToGdk(e.modifiersEx),
        )
      }
    }
    mouseListener = adapter
    host.addMouseListener(adapter)
    host.addMouseMotionListener(adapter)
  }

  private fun awtButtonToGdk(awtButton: Int): Int = when (awtButton) {
    MouseEvent.BUTTON1 -> 1
    MouseEvent.BUTTON2 -> 2
    MouseEvent.BUTTON3 -> 3
    else -> 1
  }

  private fun awtModifiersToGdk(modifiersEx: Int): Int {
    var state = 0
    if ((modifiersEx and InputEvent.SHIFT_DOWN_MASK) != 0) state = state or GDK_SHIFT_MASK
    if ((modifiersEx and InputEvent.CTRL_DOWN_MASK) != 0) state = state or GDK_CONTROL_MASK
    if ((modifiersEx and InputEvent.ALT_DOWN_MASK) != 0) state = state or GDK_MOD1_MASK
    if ((modifiersEx and InputEvent.META_DOWN_MASK) != 0) state = state or GDK_META_MASK
    if ((modifiersEx and InputEvent.BUTTON1_DOWN_MASK) != 0) state = state or GDK_BUTTON1_MASK
    if ((modifiersEx and InputEvent.BUTTON2_DOWN_MASK) != 0) state = state or GDK_BUTTON2_MASK
    if ((modifiersEx and InputEvent.BUTTON3_DOWN_MASK) != 0) state = state or GDK_BUTTON3_MASK
    return state
  }

  companion object {
    private const val GDK_SHIFT_MASK = 1 shl 0
    private const val GDK_CONTROL_MASK = 1 shl 2
    private const val GDK_MOD1_MASK = 1 shl 3
    private const val GDK_META_MASK = 1 shl 28
    private const val GDK_BUTTON1_MASK = 1 shl 8
    private const val GDK_BUTTON2_MASK = 1 shl 9
    private const val GDK_BUTTON3_MASK = 1 shl 10
  }
}
```

Add the missing imports at the top of the file:

```kotlin
import java.awt.event.InputEvent
import java.awt.event.MouseEvent
import javax.swing.event.MouseInputAdapter
```

- [ ] **Step 2: Compile**

```bash
source "$HOME/.cargo/env" && cd /home/siarhei/speqa/speqa && ./gradlew compileKotlin --console=plain --no-daemon 2>&1 | tail -8
```

Expected: BUILD SUCCESSFUL. The Gradle native build task will pick up the Rust change automatically and re-cargo.

- [ ] **Step 3: Commit**

```bash
cd /home/siarhei/speqa/speqa && git add native/LinuxWebKitGtkBridge/src/lib.rs src/main/kotlin/io/github/barsia/speqa/webview/internal/linux/LinuxWebKitGtkBridge.kt src/main/kotlin/io/github/barsia/speqa/webview/internal/linux/LinuxWebKitWebViewFacade.kt src/main/kotlin/io/github/barsia/speqa/webview/internal/linux/LinuxWaylandSnapshotWebViewHostPeer.kt && git commit -m "linux(snapshot): forward mouse button + motion into the offscreen WebView"
```

---

### Task 3: Mouse wheel forwarding

**Files:**
- Modify: `native/LinuxWebKitGtkBridge/src/lib.rs`
- Modify: `src/main/kotlin/io/github/barsia/speqa/webview/internal/linux/LinuxWebKitGtkBridge.kt`
- Modify: `src/main/kotlin/io/github/barsia/speqa/webview/internal/linux/LinuxWebKitWebViewFacade.kt`
- Modify: `src/main/kotlin/io/github/barsia/speqa/webview/internal/linux/LinuxWaylandSnapshotWebViewHostPeer.kt`

- [ ] **Step 1: Rust JNI export for wheel**

Below the motion JNI export from Task 2, add:

```rust
#[no_mangle]
pub extern "system" fn Java_io_github_barsia_speqa_webview_internal_linux_LinuxWebKitGtkBridge_dispatchMouseScrollNative(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    handle: jlong,
    x: jdouble,
    y: jdouble,
    delta_x: jdouble,
    delta_y: jdouble,
    state: jint,
) {
    run_with_handle(&mut env, handle, move |native| {
        enqueue_gtk_task(move || {
            with_locked_view(&native, |view| {
                dispatch_scroll_event(view, x, y, delta_x, delta_y, state as u32);
            });
            request_snapshot_later(native.clone(), 16);
        })
    });
}
```

- [ ] **Step 2: Kotlin JNI mirror**

In `LinuxWebKitGtkBridge.kt`, after `dispatchMouseMotionNative`:

```kotlin
  @JvmStatic
  private external fun dispatchMouseScrollNative(
    handle: Long,
    x: Double,
    y: Double,
    deltaX: Double,
    deltaY: Double,
    state: Int,
  )
```

And wrapper:

```kotlin
  fun dispatchMouseScroll(handle: Long, x: Double, y: Double, deltaX: Double, deltaY: Double, state: Int) =
    dispatchMouseScrollNative(handle, x, y, deltaX, deltaY, state)
```

- [ ] **Step 3: Facade-level wrapper**

In `LinuxWebKitWebViewFacade.kt`, after `dispatchMouseMotion`:

```kotlin
  internal fun dispatchMouseScroll(x: Double, y: Double, deltaX: Double, deltaY: Double, modifierState: Int) {
    val handle = nativeHandle
    if (handle == 0L || this.state.get() != State.Active) return
    runOnEdt { LinuxWebKitGtkBridge.dispatchMouseScroll(handle, x, y, deltaX, deltaY, modifierState) }
  }
```

- [ ] **Step 4: Wire MouseWheelListener in host peer**

In `LinuxWaylandSnapshotWebViewHostPeer.installInputListeners` (added in Task 2), inside the same anonymous `MouseInputAdapter` (which extends MouseListener+MouseMotionListener), we need ALSO a MouseWheelListener. Since `MouseInputAdapter` does NOT implement `MouseWheelListener` in Swing, register a separate wheel listener:

```kotlin
    val wheelListener = java.awt.event.MouseWheelListener { e ->
      // AWT wheel rotation: positive = scroll down (away from user). GDK delta_y: positive = scroll down. Same sign convention.
      // Multiply by a sensible step so single notch ≈ pixelDelta a browser would expect.
      val scrollStep = 40.0 // pixels per wheel notch; matches the IDE's editor scroll step
      val deltaX = if (e.isShiftDown) e.preciseWheelRotation * scrollStep else 0.0
      val deltaY = if (e.isShiftDown) 0.0 else e.preciseWheelRotation * scrollStep
      facade.dispatchMouseScroll(
        x = e.x.toDouble() * lastScale,
        y = e.y.toDouble() * lastScale,
        deltaX = deltaX,
        deltaY = deltaY,
        modifierState = awtModifiersToGdk(e.modifiersEx),
      )
    }
```

Save this listener to a new private field (`private var wheelListener: java.awt.event.MouseWheelListener? = null`) so it can be removed on `detach()`. Update `attach()` to call `host.addMouseWheelListener(wheelListener)` and `detach()` to `host.removeMouseWheelListener(wheelListener)`.

- [ ] **Step 5: Compile + commit**

```bash
source "$HOME/.cargo/env" && cd /home/siarhei/speqa/speqa && ./gradlew compileKotlin --console=plain --no-daemon 2>&1 | tail -8
```

Then:

```bash
cd /home/siarhei/speqa/speqa && git add native/LinuxWebKitGtkBridge/src/lib.rs src/main/kotlin/io/github/barsia/speqa/webview/internal/linux/LinuxWebKitGtkBridge.kt src/main/kotlin/io/github/barsia/speqa/webview/internal/linux/LinuxWebKitWebViewFacade.kt src/main/kotlin/io/github/barsia/speqa/webview/internal/linux/LinuxWaylandSnapshotWebViewHostPeer.kt && git commit -m "linux(snapshot): forward mouse wheel into the offscreen WebView"
```

---

### Task 4: Keyboard forwarding + key-map (TDD)

**Files:**
- Create: `src/main/kotlin/io/github/barsia/speqa/webview/internal/linux/AwtToGtkKeyMap.kt`
- Create: `src/test/kotlin/io/github/barsia/speqa/webview/internal/linux/AwtToGtkKeyMapTest.kt`
- Modify: `native/LinuxWebKitGtkBridge/src/lib.rs`
- Modify: `src/main/kotlin/io/github/barsia/speqa/webview/internal/linux/LinuxWebKitGtkBridge.kt`
- Modify: `src/main/kotlin/io/github/barsia/speqa/webview/internal/linux/LinuxWebKitWebViewFacade.kt`
- Modify: `src/main/kotlin/io/github/barsia/speqa/webview/internal/linux/LinuxWaylandSnapshotWebViewHostPeer.kt`

- [ ] **Step 1: Failing tests for the key map**

Create `src/test/kotlin/io/github/barsia/speqa/webview/internal/linux/AwtToGtkKeyMapTest.kt`:

```kotlin
// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package io.github.barsia.speqa.webview.internal.linux

import java.awt.event.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Test

class AwtToGtkKeyMapTest {
  @Test
  fun `ascii letter a maps to gdk_key_a`() {
    assertEquals(0x061, AwtToGtkKeyMap.gdkKeyval(KeyEvent.VK_A, 'a'))
  }

  @Test
  fun `ascii letter A uppercase maps to gdk_key_A`() {
    assertEquals(0x041, AwtToGtkKeyMap.gdkKeyval(KeyEvent.VK_A, 'A'))
  }

  @Test
  fun `enter maps to gdk_key_Return`() {
    assertEquals(0xff0d, AwtToGtkKeyMap.gdkKeyval(KeyEvent.VK_ENTER, KeyEvent.CHAR_UNDEFINED))
  }

  @Test
  fun `backspace maps to gdk_key_BackSpace`() {
    assertEquals(0xff08, AwtToGtkKeyMap.gdkKeyval(KeyEvent.VK_BACK_SPACE, KeyEvent.CHAR_UNDEFINED))
  }

  @Test
  fun `tab maps to gdk_key_Tab`() {
    assertEquals(0xff09, AwtToGtkKeyMap.gdkKeyval(KeyEvent.VK_TAB, KeyEvent.CHAR_UNDEFINED))
  }

  @Test
  fun `escape maps to gdk_key_Escape`() {
    assertEquals(0xff1b, AwtToGtkKeyMap.gdkKeyval(KeyEvent.VK_ESCAPE, KeyEvent.CHAR_UNDEFINED))
  }

  @Test
  fun `arrow keys map to gdk_key_Left_Right_Up_Down`() {
    assertEquals(0xff51, AwtToGtkKeyMap.gdkKeyval(KeyEvent.VK_LEFT, KeyEvent.CHAR_UNDEFINED))
    assertEquals(0xff52, AwtToGtkKeyMap.gdkKeyval(KeyEvent.VK_UP, KeyEvent.CHAR_UNDEFINED))
    assertEquals(0xff53, AwtToGtkKeyMap.gdkKeyval(KeyEvent.VK_RIGHT, KeyEvent.CHAR_UNDEFINED))
    assertEquals(0xff54, AwtToGtkKeyMap.gdkKeyval(KeyEvent.VK_DOWN, KeyEvent.CHAR_UNDEFINED))
  }

  @Test
  fun `home and end map correctly`() {
    assertEquals(0xff50, AwtToGtkKeyMap.gdkKeyval(KeyEvent.VK_HOME, KeyEvent.CHAR_UNDEFINED))
    assertEquals(0xff57, AwtToGtkKeyMap.gdkKeyval(KeyEvent.VK_END, KeyEvent.CHAR_UNDEFINED))
  }

  @Test
  fun `page up and down map correctly`() {
    assertEquals(0xff55, AwtToGtkKeyMap.gdkKeyval(KeyEvent.VK_PAGE_UP, KeyEvent.CHAR_UNDEFINED))
    assertEquals(0xff56, AwtToGtkKeyMap.gdkKeyval(KeyEvent.VK_PAGE_DOWN, KeyEvent.CHAR_UNDEFINED))
  }

  @Test
  fun `delete maps to gdk_key_Delete`() {
    assertEquals(0xffff, AwtToGtkKeyMap.gdkKeyval(KeyEvent.VK_DELETE, KeyEvent.CHAR_UNDEFINED))
  }

  @Test
  fun `space maps to gdk_key_space`() {
    assertEquals(0x020, AwtToGtkKeyMap.gdkKeyval(KeyEvent.VK_SPACE, ' '))
  }

  @Test
  fun `unknown key with no char returns zero sentinel`() {
    assertEquals(0, AwtToGtkKeyMap.gdkKeyval(KeyEvent.VK_UNDEFINED, KeyEvent.CHAR_UNDEFINED))
  }
}
```

Run to confirm fail:

```bash
cd /home/siarhei/speqa/speqa && ./gradlew test --tests 'io.github.barsia.speqa.webview.internal.linux.AwtToGtkKeyMapTest' --console=plain --no-daemon 2>&1 | tail -10
```

Expected: compile failure (`AwtToGtkKeyMap` doesn't exist yet).

- [ ] **Step 2: Implement `AwtToGtkKeyMap`**

Create `src/main/kotlin/io/github/barsia/speqa/webview/internal/linux/AwtToGtkKeyMap.kt`:

```kotlin
// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package io.github.barsia.speqa.webview.internal.linux

import java.awt.event.KeyEvent
import org.jetbrains.annotations.ApiStatus

/**
 * Maps Java AWT key codes / chars to GDK keyvals so input from the IDE can be forwarded
 * into the offscreen WebKitGTK widget. Covers ASCII printable characters and the
 * navigation/editing keys that an editor preview needs in practice.
 *
 * For unmapped keys with no usable [Char] payload, returns `0` — the caller should
 * suppress the dispatch instead of sending a meaningless event.
 */
@ApiStatus.Internal
internal object AwtToGtkKeyMap {
  fun gdkKeyval(awtKeyCode: Int, awtKeyChar: Char): Int {
    namedKeys[awtKeyCode]?.let { return it }
    if (awtKeyChar != KeyEvent.CHAR_UNDEFINED && awtKeyChar.code in 0x20..0x7e) {
      return awtKeyChar.code
    }
    return 0
  }

  private val namedKeys: Map<Int, Int> = mapOf(
    KeyEvent.VK_ENTER to 0xff0d,
    KeyEvent.VK_BACK_SPACE to 0xff08,
    KeyEvent.VK_TAB to 0xff09,
    KeyEvent.VK_ESCAPE to 0xff1b,
    KeyEvent.VK_DELETE to 0xffff,
    KeyEvent.VK_INSERT to 0xff63,
    KeyEvent.VK_HOME to 0xff50,
    KeyEvent.VK_END to 0xff57,
    KeyEvent.VK_PAGE_UP to 0xff55,
    KeyEvent.VK_PAGE_DOWN to 0xff56,
    KeyEvent.VK_LEFT to 0xff51,
    KeyEvent.VK_UP to 0xff52,
    KeyEvent.VK_RIGHT to 0xff53,
    KeyEvent.VK_DOWN to 0xff54,
    KeyEvent.VK_F1 to 0xffbe,
    KeyEvent.VK_F2 to 0xffbf,
    KeyEvent.VK_F3 to 0xffc0,
    KeyEvent.VK_F4 to 0xffc1,
    KeyEvent.VK_F5 to 0xffc2,
    KeyEvent.VK_F6 to 0xffc3,
    KeyEvent.VK_F7 to 0xffc4,
    KeyEvent.VK_F8 to 0xffc5,
    KeyEvent.VK_F9 to 0xffc6,
    KeyEvent.VK_F10 to 0xffc7,
    KeyEvent.VK_F11 to 0xffc8,
    KeyEvent.VK_F12 to 0xffc9,
    KeyEvent.VK_SHIFT to 0xffe1,
    KeyEvent.VK_CONTROL to 0xffe3,
    KeyEvent.VK_ALT to 0xffe9,
    KeyEvent.VK_META to 0xffeb,
  )
}
```

Re-run:

```bash
cd /home/siarhei/speqa/speqa && ./gradlew test --tests 'io.github.barsia.speqa.webview.internal.linux.AwtToGtkKeyMapTest' --console=plain --no-daemon 2>&1 | tail -10
```

Expected: BUILD SUCCESSFUL, all 12 tests pass.

- [ ] **Step 3: Rust JNI export for key events**

Below the scroll JNI export:

```rust
#[no_mangle]
pub extern "system" fn Java_io_github_barsia_speqa_webview_internal_linux_LinuxWebKitGtkBridge_dispatchKeyNative(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    handle: jlong,
    keyval: jint,
    state: jint,
    is_press: jboolean,
) {
    if keyval == 0 { return; }
    let type_ = if is_press != 0 { GDK_KEY_PRESS } else { GDK_KEY_RELEASE };
    run_with_handle(&mut env, handle, move |native| {
        enqueue_gtk_task(move || {
            with_locked_view(&native, |view| {
                dispatch_key_event(view, type_, keyval as u32, state as u32);
            });
            request_snapshot_later(native.clone(), 16);
        })
    });
}
```

- [ ] **Step 4: Kotlin JNI mirror + facade wrapper**

In `LinuxWebKitGtkBridge.kt`:

```kotlin
  @JvmStatic
  private external fun dispatchKeyNative(handle: Long, keyval: Int, state: Int, isPress: Boolean)

  fun dispatchKey(handle: Long, keyval: Int, state: Int, isPress: Boolean) =
    dispatchKeyNative(handle, keyval, state, isPress)
```

In `LinuxWebKitWebViewFacade.kt`:

```kotlin
  internal fun dispatchKey(keyval: Int, modifierState: Int, isPress: Boolean) {
    val handle = nativeHandle
    if (handle == 0L || this.state.get() != State.Active) return
    runOnEdt { LinuxWebKitGtkBridge.dispatchKey(handle, keyval, modifierState, isPress) }
  }
```

- [ ] **Step 5: Wire KeyEventDispatcher in host peer**

In `LinuxWaylandSnapshotWebViewHostPeer`, add at the top of the class:

```kotlin
  private var keyDispatcher: java.awt.KeyEventDispatcher? = null
```

In `installInputListeners` (or a new sibling install method called from `attach`), add:

```kotlin
    val keyDispatcher = java.awt.KeyEventDispatcher { event ->
      // Only forward events when the snapshot host owns focus inside its window.
      if (event.component !== host && !javax.swing.SwingUtilities.isDescendingFrom(event.component, host)) {
        return@KeyEventDispatcher false
      }
      val keyval = AwtToGtkKeyMap.gdkKeyval(event.keyCode, event.keyChar)
      if (keyval == 0) return@KeyEventDispatcher false
      val isPress = when (event.id) {
        java.awt.event.KeyEvent.KEY_PRESSED -> true
        java.awt.event.KeyEvent.KEY_RELEASED -> false
        else -> return@KeyEventDispatcher false // KEY_TYPED is synthetic; PRESSED already covers char input
      }
      facade.dispatchKey(
        keyval = keyval,
        modifierState = awtModifiersToGdk(event.modifiersEx),
        isPress = isPress,
      )
      true // consume — we already dispatched
    }
    this.keyDispatcher = keyDispatcher
    java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(keyDispatcher)
```

In `detach()`, remove the dispatcher:

```kotlin
    keyDispatcher?.let {
      java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(it)
    }
    keyDispatcher = null
```

Make sure `host.isFocusable = true` and `host.focusTraversalKeysEnabled = false` are set in `attach()` so Tab reaches the WebView. Add right after `installInputListeners(hostPanel)`:

```kotlin
    hostPanel.isFocusable = true
    hostPanel.focusTraversalKeysEnabled = false
```

- [ ] **Step 6: Compile + run tests + commit**

```bash
source "$HOME/.cargo/env" && cd /home/siarhei/speqa/speqa && ./gradlew test --console=plain --no-daemon 2>&1 | tail -10
```

Expected: BUILD SUCCESSFUL, AwtToGtkKeyMapTest passes 12/12, nothing else broke.

```bash
cd /home/siarhei/speqa/speqa && git add native/LinuxWebKitGtkBridge/src/lib.rs src/main/kotlin/io/github/barsia/speqa/webview/internal/linux/LinuxWebKitGtkBridge.kt src/main/kotlin/io/github/barsia/speqa/webview/internal/linux/LinuxWebKitWebViewFacade.kt src/main/kotlin/io/github/barsia/speqa/webview/internal/linux/LinuxWaylandSnapshotWebViewHostPeer.kt src/main/kotlin/io/github/barsia/speqa/webview/internal/linux/AwtToGtkKeyMap.kt src/test/kotlin/io/github/barsia/speqa/webview/internal/linux/AwtToGtkKeyMapTest.kt && git commit -m "linux(snapshot): forward keyboard input + ASCII/named-key map for the offscreen WebView"
```

---

### Task 5: Local build + manual verification

**Files:** none modified.

- [ ] **Step 1: Local build**

```bash
source "$HOME/.cargo/env" && cd /home/siarhei/speqa/speqa && ./gradlew buildPlugin --console=plain --no-daemon 2>&1 | tail -5
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: User reinstalls / re-runs IDE**

Either `./gradlew runIde` (sandbox) or install the freshly built ZIP into the regular IDE. Open a `.tc` file.

- [ ] **Step 3: Mouse verification**

- Click into the preview — caret should appear in the right field (e.g., title or step body).
- Click on a step's body text — caret moves to that step.
- Drag-select a phrase — text should highlight, indicating press + motion + release reached the WebView.
- Scroll with the mouse wheel — content should scroll inside the preview pane.
- Right-click a link or selection — WebKit's native context menu should appear.

If any of these fail, do NOT silently move on. Report what failed.

- [ ] **Step 4: Keyboard verification**

- Click into a text field in the preview, then type characters — they appear.
- Backspace deletes the previous character.
- Arrow keys move the caret within the field.
- Enter creates a new line (or commits an action depending on the field).
- Tab moves focus to the next field within the preview.
- Esc cancels an active interaction (e.g., popup, edit).
- Ctrl+A selects all in a text field.

- [ ] **Step 5: Bidirectional check — edits trigger snapshot updates**

After typing inside the preview, switch focus to the source editor briefly and back. The source editor should reflect what was typed (the WebView's `onPatch` callback fires on field changes). The preview should remain in sync with no extra flicker.

- [ ] **Step 6: No crash and no SpeqaDebug**

```bash
tail -200 /home/siarhei/speqa/speqa/.intellijPlatform/sandbox/IU-2026.1/log/idea.log 2>/dev/null | grep -iE "speqa.webview|SpeqaDebug|wasn't closed|SIGABRT" | tail -30
```

Expected: lifecycle markers visible (`linux-webkitgtk-create — WebKitGTK ready`, snapshot sends), zero `SpeqaDebug` entries (we cleaned those up earlier), no abort warnings.

- [ ] **Step 7: If anything in steps 3–6 fails — diagnose first**

Add targeted `Logger.getInstance("SpeqaDebug").warn(...)` to the failing path (per project rule "add diagnostic logging first") and report findings. Do NOT guess fixes.

---

## Self-review notes

- **Spec coverage**: every event class users actually need for the preview UX is covered — mouse press / release / motion / wheel and keyboard press / release for the practical AWT key set. Out of scope (and documented as such): IME composition, drag-and-drop from outside the editor, touch / gesture events, accessibility tree forwarding. These should be separate plans if/when the underlying use case shows up.
- **Placeholder scan**: every step has concrete code, file paths, and exact commands. No "TBD" / "similar to" / "appropriate".
- **Type consistency**:
  - `awtModifiersToGdk(Int): Int` defined once in `LinuxWaylandSnapshotWebViewHostPeer`, used by Tasks 2, 3, and 4.
  - `AwtToGtkKeyMap.gdkKeyval(awtKeyCode: Int, awtKeyChar: Char): Int` defined in Task 4, used only there.
  - JNI symbol names match between Rust `extern "system" fn Java_io_github_barsia_speqa_webview_internal_linux_LinuxWebKitGtkBridge_<method>Native` and the Kotlin `@JvmStatic private external fun <method>Native(...)` declarations.
- **TDD discipline**: Task 4's pure-Kotlin map has TDD coverage. The native event-injection helpers in Task 1 don't have unit tests (JNI integration tests would require a live GTK display — out of scope for unit tests); they are validated end-to-end in Task 5 manual verification.
- **Project rules**:
  - One commit per logical change.
  - Diagnostic logging is NOT added speculatively; Step 7 of Task 5 reserves it for the diagnostic path if anything fails.
  - No Claude attribution in any commit.
  - Snapshot bumps (`request_snapshot_later`) keep the visible feedback within ~16 ms of user input.
- **Edge cases acknowledged**:
  - Modifier-only key presses (Shift, Ctrl, etc.) are forwarded so the WebView's modifier state machine stays consistent.
  - Wheel `isShiftDown` swaps to horizontal scroll — matches typical browser behaviour for Shift+wheel.
  - `KeyEvent.KEY_TYPED` (a synthetic AWT event that doesn't carry key code) is intentionally NOT forwarded; `KEY_PRESSED` + the keyval mapping cover printable input, which is what WebKit needs.
