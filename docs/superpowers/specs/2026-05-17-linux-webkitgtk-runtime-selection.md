# Spec: Linux preview supports both WebKitGTK 4.0 and 4.1 runtimes

## Current behavior

The SpeQA preview on Linux loads a native bridge linked against either
WebKitGTK 4.1 (preferred) or WebKitGTK 4.0 (fallback). The plugin auto-selects
the matching native bridge variant at JVM load time:

1. On startup `LinuxWebKitGtkBridge.loadNativeLibrary` calls
   `LinuxWebKitGtkRuntime.selectPreferred` with an `ldconfig`-backed probe
   (`LdconfigLinuxWebKitGtkRuntimeProbe`).
2. The probe parses `ldconfig -p` output. It tries `ldconfig`, `/sbin/ldconfig`,
   and `/usr/sbin/ldconfig` in order. The match is exact against the first
   whitespace-delimited token of each line, so substring false positives are
   not possible.
3. `selectPreferred` returns `Wk41` if `libwebkit2gtk-4.1.so.0` is reported;
   otherwise `Wk40` if `libwebkit2gtk-4.0.so.37` is reported; otherwise `null`.
4. The matching `.so` is loaded from `native/linux/wk41/` or
   `native/linux/wk40/` inside the plugin JAR.
5. If neither runtime is installed, `loadNativeLibrary` throws
   `LinuxWebKitGtkMissingException` with an actionable message telling the user
   which package to install.

The Rust bridge crate exposes two Cargo features: `webkit41` (default) and
`webkit40`. The only WebKit API delta — the JavaScript evaluation call — is
hidden behind two `#[cfg]`-gated shims (`js_eval_async`, `js_eval_finish`) with
matching Rust-level signatures. The rest of the bridge is feature-agnostic. A
`compile_error!` guard in `lib.rs` rejects builds with both features enabled or
neither enabled, mirroring a `panic!` in `build.rs` that does the same.

## Supported distributions

| Distribution                | WebKitGTK soname              | Variant used |
|-----------------------------|-------------------------------|--------------|
| Ubuntu 20.04, Debian 11     | `libwebkit2gtk-4.0.so.37`     | `wk40`       |
| RHEL/CentOS 8/9             | `libwebkit2gtk-4.0.so.37`     | `wk40`       |
| Ubuntu 22.04, Debian 12     | `libwebkit2gtk-4.1.so.0`      | `wk41`       |
| Ubuntu 24.04, Fedora 36+    | `libwebkit2gtk-4.1.so.0`      | `wk41`       |

## Build

CI (`.github/workflows/build-plugin.yml`) builds both variants on `ubuntu-22.04`
via [`cargo-zigbuild`](https://github.com/rust-cross/cargo-zigbuild), which uses
zig as a cross-linker to pin the glibc symbol-version floor independent of the
host glibc. The runner is `ubuntu-22.04` because it is the latest Ubuntu LTS
that still carries `libwebkit2gtk-4.0-dev` in its archive.

Glibc floors:

| Variant | Build target                           | Floor       | Rationale                                                |
|---------|----------------------------------------|-------------|----------------------------------------------------------|
| `wk41`  | `x86_64-unknown-linux-gnu.2.35`        | GLIBC_2.35  | Lowest distro shipping `libwebkit2gtk-4.1`: Ubuntu 22.04 |
| `wk40`  | `x86_64-unknown-linux-gnu.2.28`        | GLIBC_2.28  | Lowest supported distro: RHEL 8                          |

CI verifies the floors post-build by scanning the produced `.so` with
`objdump -T` and asserting that no `GLIBC_*` versioned symbol exceeds the
declared floor. If the assertion fails the workflow exits non-zero, so the ZIP
never carries a binary that would `UnsatisfiedLinkError` on its target distros.

The artifact `linux-native` contains both `.so` files under
`target-wk41/release/` and `target-wk40/release/` (CI moves the zigbuild
outputs out of the `x86_64-unknown-linux-gnu/release/` subdirectory before
upload so Gradle `processResources` finds them at the same path used for
local builds).

Developers can build locally with `./gradlew buildLinuxWebKitGtkBridge41
buildLinuxWebKitGtkBridge40`. Local builds use plain `cargo build` (no
zigbuild) because glibc compatibility is irrelevant when the binary will
only run on the developer's own machine. Each task skips cleanly with a
lifecycle message if the corresponding `webkit2gtk-4.x` pkg-config package
is not installed.

## Non-goals

- Loading both variants in the same process (libsoup2 / libsoup3 cannot
  coexist).
- ARM64 Linux.
- WebKitGTK 6.0 (GTK4-based).
