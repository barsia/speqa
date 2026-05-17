# LinuxWebKitGtkBridge

JNI bridge from the Speqa plugin's Kotlin code to WebKit2GTK on Linux. Built as a
`cdylib`; the resulting `liblinux_webkitgtk_bridge.so` is renamed to
`libLinuxWebKitGtkBridge.so` and packaged under `native/linux/wk41/` or
`native/linux/wk40/` in the plugin JAR depending on which Cargo feature was
selected.

## Variants

The bridge is built twice — once against WebKitGTK 4.1 (Ubuntu 22.04+, Fedora,
Debian 12+) and once against 4.0 (Ubuntu 20.04, Debian 11, RHEL 8/9). At runtime
the plugin probes `ldconfig -p` and loads the matching `.so`. Only one variant
is loaded per process; libsoup2 (4.0) and libsoup3 (4.1) cannot coexist in the
same address space.

## Requirements

- Linux x86_64.
- System packages: `pkg-config`, `libwebkit2gtk-4.1-dev`, `libwebkit2gtk-4.0-dev`,
  `libgtk-3-dev`, `libx11-dev`, `build-essential`.
- Rust toolchain: `rustup default stable-x86_64-unknown-linux-gnu`.

On Ubuntu / Debian:

```
sudo apt-get install -y pkg-config libwebkit2gtk-4.1-dev libwebkit2gtk-4.0-dev \
                        libgtk-3-dev libx11-dev build-essential
```

Note: `libwebkit2gtk-4.0-dev` is available on Ubuntu 22.04 but has been dropped
from Ubuntu 24.04. The release CI pins `ubuntu-22.04` for this reason.

## Build

From the repository root on a Linux host:

```
./gradlew buildLinuxWebKitGtkBridge41 buildLinuxWebKitGtkBridge40
```

Each task invokes `cargo build --release` in this directory with the matching
feature flag and a disjoint `--target-dir`. The Gradle `processResources` task
copies both `.so` files into the plugin distribution automatically.

Standalone builds (for debugging):

```
cd native/LinuxWebKitGtkBridge
cargo build --release --no-default-features --features webkit41 --target-dir target-wk41
cargo build --release --no-default-features --features webkit40 --target-dir target-wk40
```

## API delta between 4.0 and 4.1

Only the JavaScript evaluation call differs:

| 4.1                                       | 4.0                                                                          |
|-------------------------------------------|------------------------------------------------------------------------------|
| `webkit_web_view_evaluate_javascript`     | `webkit_web_view_run_javascript`                                             |
| `webkit_web_view_evaluate_javascript_finish` returning `JSCValue*` | `webkit_web_view_run_javascript_finish` returning `WebKitJavascriptResult*`, then `webkit_javascript_result_get_js_value`, then `webkit_javascript_result_unref` |

Both are unified behind `js_eval_async` / `js_eval_finish` in `src/lib.rs`,
gated by `#[cfg(feature = "webkit41")]` / `#[cfg(feature = "webkit40")]`.
Note that `WebKitJavascriptResult` is a GBoxed, not a GObject — release it via
`webkit_javascript_result_unref`, never `g_object_unref`.

## JNI symbol naming

All `#[no_mangle] pub extern "system" fn Java_...` symbols must match the Kotlin
package of `LinuxWebKitGtkBridge`: `io.github.barsia.speqa.webview.internal.linux`.
A contract test (`LinuxWebKitGtkBridgeJniSymbolsTest`) guards this and also
verifies the cfg-gated extern blocks and shim functions exist for both features.

## Rendering

On both X11 and Wayland the bridge renders WebKit content into an offscreen
`GtkOffscreenWindow`, captures the resulting pixel buffer, and hands it to
`SwingWebViewHostPanel.setSnapshotImage`, which paints it as a regular Swing
image inside the editor.

This is reliable across both display servers because the bridge never embeds
a foreign GTK widget as an X11 child of the JBR top-level window. Embedded
X11 children of a JBR/Swing parent do not receive proper expose / focus
events under either GNOME / Mutter compositing or vanilla X11, and the
WebKitGTK render output rarely reaches the X11 pixmap. The native X11
overlay path remains in the codebase for a future re-enable but is not
reached at runtime.

Trade-offs:
  * No GPU-accelerated scrolling — refresh runs at the snapshot cadence
    (~30 fps when content is dirty, idle otherwise).
  * Input events are dispatched through the GTK widget tree on the
    offscreen window; keyboard focus and text input are handled by Swing
    over the painted bitmap.

## CI build & glibc floor

CI builds both `wk41` and `wk40` variants on `ubuntu-22.04` using
[`cargo-zigbuild`](https://github.com/rust-cross/cargo-zigbuild), which uses
zig as a cross-linker to pin the produced `.so`'s glibc symbol-version floor
independent of the host glibc.

| Variant | Build target                       | Floor      | Reason                                          |
|---------|------------------------------------|------------|-------------------------------------------------|
| `wk41`  | `x86_64-unknown-linux-gnu.2.35`    | GLIBC_2.35 | Ubuntu 22.04 / Debian 12 baseline               |
| `wk40`  | `x86_64-unknown-linux-gnu.2.28`    | GLIBC_2.28 | RHEL 8 baseline; covers Ubuntu 20.04 / Debian 11 |

After building, CI runs `objdump -T` on each `.so` and fails the workflow if
any referenced `GLIBC_*` symbol exceeds the declared floor — so a regression
in the build pipeline cannot produce a binary that would `UnsatisfiedLinkError`
on its target distros.

Local builds (`./gradlew buildLinuxWebKitGtkBridge40 buildLinuxWebKitGtkBridge41`)
use plain `cargo build` and inherit the developer's host glibc, since the
binary will only run on that machine.
