# WinWebView2Bridge

JNI bridge from the Speqa plugin's Kotlin code to Microsoft Edge WebView2 on Windows.
Built as a `cdylib`; the resulting `win_webview2_bridge.dll` is renamed to
`WinWebView2Bridge.dll` and packaged under `native/windows/` in the plugin JAR.

## Requirements

- Windows 10/11 with the Evergreen WebView2 Runtime installed (default on Windows 11; Windows 10 usually has it via Edge updates).
- Rust toolchain: `rustup default stable-x86_64-pc-windows-msvc`.
- Visual Studio Build Tools (MSVC v143 or newer), required by `webview2-com`.

## Build

From the repository root on a Windows host:

```
./gradlew buildWinWebView2Bridge
```

This invokes `cargo build --release` in this directory. The DLL ends up at
`native/WinWebView2Bridge/target/release/win_webview2_bridge.dll`. The Gradle
`processResources` task copies it into the plugin distribution automatically.

Standalone build (for debugging):

```
cd native/WinWebView2Bridge
cargo build --release
```

## JNI symbol naming

All `#[no_mangle] pub extern "system" fn Java_...` symbols must match the Kotlin
package of `WinWebView2Bridge`: `io.github.barsia.speqa.webview.internal.windows`.
Renaming the Kotlin package requires updating every symbol in `src/lib.rs`.
A contract test (`WinWebView2BridgeJniSymbolsTest`) guards this.

## Known limitations

The plugin requires the Evergreen WebView2 Runtime. Microsoft ships it by default
on Windows 11 and via Edge updates on most Windows 10 systems, but a clean Windows
10 image without modern Edge may be missing it. A future task should detect the
runtime at startup and either link to the Microsoft installer or fall back to a
read-only HTML preview. For now the plugin will log `WebView2 Runtime not found`
and the preview will fail to initialize.
