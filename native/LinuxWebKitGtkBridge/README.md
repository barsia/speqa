# LinuxWebKitGtkBridge

JNI bridge from the Speqa plugin's Kotlin code to WebKit2GTK on Linux.
Built as a `cdylib`; the resulting `liblinux_webkitgtk_bridge.so` is renamed to
`libLinuxWebKitGtkBridge.so` and packaged under `native/linux/` in the plugin JAR.

## Requirements

- Linux x86_64. Tested on Ubuntu 22.04 and 24.04. Other distros need equivalent packages.
- System packages: `pkg-config`, `libwebkit2gtk-4.1-dev`, `libgtk-3-dev`, `libx11-dev`, `build-essential`.
- Rust toolchain: `rustup default stable-x86_64-unknown-linux-gnu`.

On Ubuntu / Debian:

```
sudo apt-get install -y pkg-config libwebkit2gtk-4.1-dev libgtk-3-dev libx11-dev build-essential
```

## Build

From the repository root on a Linux host:

```
./gradlew buildLinuxWebKitGtkBridge
```

This invokes `cargo build --release` in this directory. The .so ends up at
`native/LinuxWebKitGtkBridge/target/release/liblinux_webkitgtk_bridge.so`. The Gradle
`processResources` task copies it into the plugin distribution automatically.

Standalone build (for debugging):

```
cd native/LinuxWebKitGtkBridge
cargo build --release
```

## JNI symbol naming

All `#[no_mangle] pub extern "system" fn Java_...` symbols must match the Kotlin
package of `LinuxWebKitGtkBridge`: `io.github.barsia.speqa.webview.internal.linux`.
A contract test (`LinuxWebKitGtkBridgeJniSymbolsTest`) guards this.

## Wayland support

Real overlay rendering on top of the IntelliJ window is only available on X11.
Under Wayland the bridge falls back to a snapshot mode: the WebView renders into
an offscreen GTK window and bitmap snapshots are pushed back into Swing at a
limited refresh rate. This is a degraded experience by design; native Wayland
overlays require a different rendering pipeline and are out of scope.
