# wk40 glibc-floor + cause-chain rendering Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship a `wk40` WebKitGTK bridge variant that actually loads on its target distros (Ubuntu 20.04 / Debian 11 / RHEL 8), and make the unsupported-panel surface the underlying cause instead of hiding it behind a misleading outer message.

**Architecture:** Switch the Linux CI build to `cargo-zigbuild` with explicit glibc target versions (`x86_64-unknown-linux-gnu.2.28` for wk40, `x86_64-unknown-linux-gnu.2.35` for wk41) on the existing `ubuntu-22.04` runner. Add a post-build `objdump` guard that fails CI if the produced `.so` references a newer glibc symbol than the declared floor. Separately, change `rootFailureMessage` to walk the full exception cause chain so users see both the outer "bridge failed to load" message and the inner `UnsatisfiedLinkError` GLIBC reason.

**Tech Stack:** Rust 1.x with `cargo-zigbuild` (zig as cross-linker), GitHub Actions, GNU binutils (`objdump`), Kotlin 1.9, JUnit 5.

---

## Background — why this plan exists

The user is on Ubuntu 20.04 (glibc 2.31) with `libwebkit2gtk-4.0.so.37` installed. The runtime probe correctly selects `Wk40`. Loading the bundled `.so` fails:

```
java.lang.UnsatisfiedLinkError: .../libLinuxWebKitGtkBridge-...so:
  /lib/x86_64-linux-gnu/libc.so.6: version `GLIBC_2.32' not found
```

The bridge `.so` is built on `ubuntu-22.04` (glibc 2.35) per CI workflow commit `0971ec0`. The wk40 variant — explicitly the fallback for distros that ship `libwebkit2gtk-4.0` (Ubuntu 20.04 / Debian 11 / RHEL 8, glibc 2.28–2.31) — therefore self-defeats by requiring a newer glibc than its target hosts have.

Additionally, the recent "walk exception causes" fix (`27a4494`) made `rootFailureMessage` return the first non-blank message in the chain. That message is the outer `IllegalStateException("Failed to load Linux WebKitGTK bridge (Wk40). Soname expected on this host: libwebkit2gtk-4.0.so.37.")`, which is **misleading** — the soname is installed; the actual `UnsatisfiedLinkError` cause stays hidden.

---

## File structure

| File | Action | Responsibility |
|------|--------|----------------|
| `docs/superpowers/specs/2026-05-17-linux-webkitgtk-runtime-selection.md` | Modify | Document glibc floors per variant and that CI uses `cargo-zigbuild` |
| `src/main/kotlin/io/github/barsia/speqa/editor/webview/WebViewFailureMessage.kt` | Modify | Walk the entire cause chain; join with localized "Caused by:" separator |
| `src/test/kotlin/io/github/barsia/speqa/editor/webview/WebViewFailureMessageTest.kt` | Create | Cover single, distinct-chain, null-message, duplicate-message, empty cases |
| `src/main/resources/messages/SpeqaBundle.properties` | Modify | Add `webview.failure.causedBy` key |
| `.github/workflows/build-plugin.yml` | Modify | Install zig + cargo-zigbuild; `cargo zigbuild --target=…` per variant; copy outputs to `target-wk*/release/`; add `objdump` glibc-floor guard |
| `native/LinuxWebKitGtkBridge/README.md` | Modify | Note that CI uses zigbuild with pinned glibc floors |

The Gradle bridge tasks in `build.gradle.kts` stay unchanged: local dev builds with `cargo build`, output lands at `target-wkXX/release/liblinux_webkitgtk_bridge.so`. CI builds with `cargo zigbuild --target=…`, output lands at `target-wkXX/x86_64-unknown-linux-gnu/release/`; CI then copies the file up to `target-wkXX/release/` so the Gradle `processResources` step finds it at the expected path.

---

### Task 1: Update the spec

**Files:**
- Modify: `docs/superpowers/specs/2026-05-17-linux-webkitgtk-runtime-selection.md`

The project rule requires the spec to be edited before any `.kt` edit in a session, and the spec must describe current product state — so update the Build section to reflect the new build approach and the glibc floor commitment.

- [ ] **Step 1: Replace the Build section**

Find:

```markdown
## Build

CI (`.github/workflows/build-plugin.yml`) builds both variants on
`ubuntu-22.04`, since `libwebkit2gtk-4.0-dev` was dropped from the Ubuntu 24.04
archive. The artifact `linux-native` contains both `.so` files under
`target-wk41/release/` and `target-wk40/release/`.

Developers can build locally with `./gradlew buildLinuxWebKitGtkBridge41
buildLinuxWebKitGtkBridge40`. Each task skips cleanly with a lifecycle message
if the corresponding `webkit2gtk-4.x` pkg-config package is not installed.
```

Replace with:

```markdown
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
```

- [ ] **Step 2: Commit the spec change**

```bash
git add docs/superpowers/specs/2026-05-17-linux-webkitgtk-runtime-selection.md
git commit -m "spec: pin wk40/wk41 glibc floors and describe zigbuild CI flow"
```

Expected: commit succeeds; the PreToolUse spec-edit gate becomes satisfied for the rest of the session.

---

### Task 2: Add the failure-message bundle key

**Files:**
- Modify: `src/main/resources/messages/SpeqaBundle.properties`

- [ ] **Step 1: Add the key**

Append (or insert near the other webview-related keys — the existing `attachment.preview.unavailable` line is around line 291; add after that line if no better neighbour exists):

```properties
webview.failure.causedBy=Caused by: 
```

The trailing space is intentional — the separator is inserted between messages.

- [ ] **Step 2: Verify the key resolves (compile check is sufficient — no separate test)**

Skip until Task 3 when `WebViewFailureMessage.kt` consumes it; the compileKotlin run in Task 3 step 6 will fail if the key wiring is wrong.

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/messages/SpeqaBundle.properties
git commit -m "i18n: add webview.failure.causedBy bundle key"
```

---

### Task 3: Walk the full cause chain in `rootFailureMessage` (TDD)

**Files:**
- Create: `src/test/kotlin/io/github/barsia/speqa/editor/webview/WebViewFailureMessageTest.kt`
- Modify: `src/main/kotlin/io/github/barsia/speqa/editor/webview/WebViewFailureMessage.kt`

- [ ] **Step 1: Write the failing test file**

Create `src/test/kotlin/io/github/barsia/speqa/editor/webview/WebViewFailureMessageTest.kt`:

```kotlin
// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package io.github.barsia.speqa.editor.webview

import org.junit.Assert.assertEquals
import org.junit.Test

class WebViewFailureMessageTest {
  @Test
  fun `single throwable with message returns that message`() {
    val t = RuntimeException("boom")
    assertEquals("boom", rootFailureMessage(t))
  }

  @Test
  fun `chain with two distinct non-blank messages returns both joined with caused-by separator`() {
    val inner = UnsatisfiedLinkError("GLIBC_2.32 not found")
    val outer = IllegalStateException("Failed to load Linux WebKitGTK bridge (Wk40).", inner)
    assertEquals(
      "Failed to load Linux WebKitGTK bridge (Wk40). | Caused by: GLIBC_2.32 not found",
      rootFailureMessage(outer),
    )
  }

  @Test
  fun `outer with null message and inner with message returns just inner message`() {
    val inner = RuntimeException("inner reason")
    val outer = RuntimeException(null, inner)
    assertEquals("inner reason", rootFailureMessage(outer))
  }

  @Test
  fun `outer with blank message and inner with message returns just inner message`() {
    val inner = RuntimeException("inner reason")
    val outer = RuntimeException("   ", inner)
    assertEquals("inner reason", rootFailureMessage(outer))
  }

  @Test
  fun `duplicate messages in chain are deduplicated`() {
    val inner = RuntimeException("same")
    val outer = RuntimeException("same", inner)
    assertEquals("same", rootFailureMessage(outer))
  }

  @Test
  fun `chain with three distinct messages joins all`() {
    val a = RuntimeException("a")
    val b = RuntimeException("b", a)
    val c = RuntimeException("c", b)
    assertEquals("c | Caused by: b | Caused by: a", rootFailureMessage(c))
  }

  @Test
  fun `throwable with no message and no cause returns simple class name`() {
    val t = RuntimeException()
    assertEquals("RuntimeException", rootFailureMessage(t))
  }

  @Test
  fun `throwable with only blank messages in chain returns outer simple class name`() {
    val inner = RuntimeException("  ")
    val outer = IllegalStateException("", inner)
    assertEquals("IllegalStateException", rootFailureMessage(outer))
  }
}
```

Note: the test asserts the **literal English** separator " | Caused by: " — not the bundle lookup. This is deliberate; in unit-test scope `SpeqaBundle.message` resolves the English value. If the bundle key is missing or wrong, the assertion fails with a clear diff.

- [ ] **Step 2: Run the test to confirm it fails**

Run:
```bash
./gradlew test --tests 'io.github.barsia.speqa.editor.webview.WebViewFailureMessageTest' -i
```

Expected: 5 of 8 cases fail (single, null-outer, duplicate, blank-outer, no-message-no-cause cases pass with current impl; the three-distinct-chain, two-distinct-chain, and blank-only-chain cases fail because the current impl returns only the first non-blank message).

If the test does not run at all (e.g., test framework missing), check that the project already uses JUnit 5 — `find src/test -name "build.gradle*" -o -name "*.kt" | xargs grep -l "org.junit.jupiter" | head -3` should show existing usage.

- [ ] **Step 3: Rewrite `rootFailureMessage` to walk the full chain**

Replace `src/main/kotlin/io/github/barsia/speqa/editor/webview/WebViewFailureMessage.kt` contents with:

```kotlin
// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package io.github.barsia.speqa.editor.webview

import io.github.barsia.speqa.SpeqaBundle

internal fun rootFailureMessage(t: Throwable): String {
  val messages = generateSequence(t) { it.cause }
    .mapNotNull { it.message?.takeIf(String::isNotBlank) }
    .distinct()
    .toList()
  return when (messages.size) {
    0 -> t.javaClass.simpleName
    1 -> messages.single()
    else -> messages.joinToString(separator = " | ${SpeqaBundle.message("webview.failure.causedBy")}")
  }
}
```

Note the `SpeqaBundle` import: verify the actual package by greping if uncertain — `grep -rn "class SpeqaBundle\|object SpeqaBundle" src/main/kotlin | head -3`. Adjust the import statement to the actual path.

- [ ] **Step 4: Run the test to confirm all pass**

Run:
```bash
./gradlew test --tests 'io.github.barsia.speqa.editor.webview.WebViewFailureMessageTest' -i
```

Expected: all 8 tests pass.

If `chain with two distinct non-blank messages` fails because the bundle value differs from `"Caused by: "`, double-check the bundle key from Task 2 — it must be exactly `Caused by: ` with a single trailing space.

- [ ] **Step 5: Verify the full module still compiles**

Run:
```bash
./gradlew compileKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Run the rest of the test suite to confirm no regression**

Run:
```bash
./gradlew test
```

Expected: BUILD SUCCESSFUL. (If any pre-existing test was relying on `rootFailureMessage` returning only the outermost message, it would fail here — none are expected, but watch for surprises.)

- [ ] **Step 7: Commit**

```bash
git add src/main/kotlin/io/github/barsia/speqa/editor/webview/WebViewFailureMessage.kt \
        src/test/kotlin/io/github/barsia/speqa/editor/webview/WebViewFailureMessageTest.kt
git commit -m "webview: walk full cause chain when rendering the unsupported panel"
```

---

### Task 4: Switch Linux CI build to cargo-zigbuild with pinned glibc floors

**Files:**
- Modify: `.github/workflows/build-plugin.yml`

- [ ] **Step 1: Replace the `build-linux-so` job**

Find the existing job (lines ~32–65):

```yaml
  build-linux-so:
    name: Build Linux .so (webkit40 + webkit41)
    runs-on: ubuntu-22.04
    steps:
      - uses: actions/checkout@v6
      - name: Install system dependencies
        run: |
          sudo apt-get update
          sudo apt-get install -y --no-install-recommends \
            pkg-config \
            libwebkit2gtk-4.1-dev \
            libwebkit2gtk-4.0-dev \
            libgtk-3-dev \
            libx11-dev \
            build-essential
      - name: Install Rust (GNU)
        run: |
          rustup default stable-x86_64-unknown-linux-gnu
          rustc --version
          cargo --version
      - name: Build liblinux_webkitgtk_bridge.so (webkit41)
        working-directory: native/LinuxWebKitGtkBridge
        run: cargo build --release --no-default-features --features webkit41 --target-dir target-wk41
      - name: Build liblinux_webkitgtk_bridge.so (webkit40)
        working-directory: native/LinuxWebKitGtkBridge
        run: cargo build --release --no-default-features --features webkit40 --target-dir target-wk40
      - name: Upload .so artifacts
        uses: actions/upload-artifact@v7
        with:
          name: linux-native
          path: |
            native/LinuxWebKitGtkBridge/target-wk41/release/liblinux_webkitgtk_bridge.so
            native/LinuxWebKitGtkBridge/target-wk40/release/liblinux_webkitgtk_bridge.so
          if-no-files-found: error
```

Replace with:

```yaml
  build-linux-so:
    name: Build Linux .so (webkit40 + webkit41, zigbuild)
    runs-on: ubuntu-22.04
    env:
      WK40_GLIBC_FLOOR: "2.28"
      WK41_GLIBC_FLOOR: "2.35"
    steps:
      - uses: actions/checkout@v6
      - name: Install system dependencies
        run: |
          sudo apt-get update
          sudo apt-get install -y --no-install-recommends \
            pkg-config \
            libwebkit2gtk-4.1-dev \
            libwebkit2gtk-4.0-dev \
            libgtk-3-dev \
            libx11-dev \
            build-essential \
            binutils
      - name: Install Rust (GNU) + zigbuild
        run: |
          rustup default stable-x86_64-unknown-linux-gnu
          rustup target add x86_64-unknown-linux-gnu
          pip install --user cargo-zigbuild ziglang
          echo "$HOME/.local/bin" >> "$GITHUB_PATH"
          rustc --version
          cargo --version
          "$HOME/.local/bin/cargo-zigbuild" --version
      - name: Build liblinux_webkitgtk_bridge.so (webkit41, glibc>=${{ env.WK41_GLIBC_FLOOR }})
        working-directory: native/LinuxWebKitGtkBridge
        run: |
          cargo zigbuild --release --no-default-features --features webkit41 \
            --target "x86_64-unknown-linux-gnu.${WK41_GLIBC_FLOOR}" \
            --target-dir target-wk41
          mkdir -p target-wk41/release
          cp target-wk41/x86_64-unknown-linux-gnu/release/liblinux_webkitgtk_bridge.so \
             target-wk41/release/liblinux_webkitgtk_bridge.so
      - name: Build liblinux_webkitgtk_bridge.so (webkit40, glibc>=${{ env.WK40_GLIBC_FLOOR }})
        working-directory: native/LinuxWebKitGtkBridge
        run: |
          cargo zigbuild --release --no-default-features --features webkit40 \
            --target "x86_64-unknown-linux-gnu.${WK40_GLIBC_FLOOR}" \
            --target-dir target-wk40
          mkdir -p target-wk40/release
          cp target-wk40/x86_64-unknown-linux-gnu/release/liblinux_webkitgtk_bridge.so \
             target-wk40/release/liblinux_webkitgtk_bridge.so
      - name: Verify glibc floor — wk41 must not reference symbols above ${{ env.WK41_GLIBC_FLOOR }}
        working-directory: native/LinuxWebKitGtkBridge
        run: |
          SO=target-wk41/release/liblinux_webkitgtk_bridge.so
          MAX=$(objdump -T "$SO" | awk '{for (i=1;i<=NF;i++) if ($i ~ /^GLIBC_/) print $i}' | sed 's/^GLIBC_//' | sort -V | tail -1)
          echo "wk41 max GLIBC symbol: ${MAX:-<none>} (floor ${WK41_GLIBC_FLOOR})"
          if [ -z "$MAX" ]; then exit 0; fi
          HIGHEST=$(printf '%s\n%s\n' "$MAX" "$WK41_GLIBC_FLOOR" | sort -V | tail -1)
          if [ "$HIGHEST" != "$WK41_GLIBC_FLOOR" ]; then
            echo "::error::wk41 requires GLIBC_$MAX, exceeds declared floor GLIBC_${WK41_GLIBC_FLOOR}"
            exit 1
          fi
      - name: Verify glibc floor — wk40 must not reference symbols above ${{ env.WK40_GLIBC_FLOOR }}
        working-directory: native/LinuxWebKitGtkBridge
        run: |
          SO=target-wk40/release/liblinux_webkitgtk_bridge.so
          MAX=$(objdump -T "$SO" | awk '{for (i=1;i<=NF;i++) if ($i ~ /^GLIBC_/) print $i}' | sed 's/^GLIBC_//' | sort -V | tail -1)
          echo "wk40 max GLIBC symbol: ${MAX:-<none>} (floor ${WK40_GLIBC_FLOOR})"
          if [ -z "$MAX" ]; then exit 0; fi
          HIGHEST=$(printf '%s\n%s\n' "$MAX" "$WK40_GLIBC_FLOOR" | sort -V | tail -1)
          if [ "$HIGHEST" != "$WK40_GLIBC_FLOOR" ]; then
            echo "::error::wk40 requires GLIBC_$MAX, exceeds declared floor GLIBC_${WK40_GLIBC_FLOOR}"
            exit 1
          fi
      - name: Upload .so artifacts
        uses: actions/upload-artifact@v7
        with:
          name: linux-native
          path: |
            native/LinuxWebKitGtkBridge/target-wk41/release/liblinux_webkitgtk_bridge.so
            native/LinuxWebKitGtkBridge/target-wk40/release/liblinux_webkitgtk_bridge.so
          if-no-files-found: error
```

Key details:
- `pip install --user cargo-zigbuild ziglang` installs the `cargo-zigbuild` Cargo subcommand AND `ziglang` (Python wrapper that bundles a zig binary). This is the supported install path on Ubuntu runners — adding `$HOME/.local/bin` to `PATH` makes `cargo zigbuild` resolve via `cargo`'s subcommand discovery.
- `--target x86_64-unknown-linux-gnu.2.28` is zigbuild-specific syntax — vanilla `cargo` rejects it; this is why the subcommand is required.
- After zigbuild, the binary lands at `target-wkXX/x86_64-unknown-linux-gnu/release/`. The `cp` step mirrors it to `target-wkXX/release/` so the existing artifact-upload path and the downstream `package` job stay unchanged.
- The objdump guard parses lines like `0000000000000000      DF *UND*	0000000000000000  GLIBC_2.28  fprintf`, extracts the `GLIBC_` token, strips the prefix, sorts versions with `sort -V`, and compares the max against the floor. If the floor is unchanged across the comparison sort, it stays at the top — anything higher would surface as `HIGHEST != floor`.
- `binutils` is added explicitly to apt so `objdump` is guaranteed present.

- [ ] **Step 2: Verify the YAML parses**

Run:
```bash
python3 -c "import yaml,sys; yaml.safe_load(open('.github/workflows/build-plugin.yml')); print('ok')"
```

Expected: `ok`. If parse fails, fix the indentation before continuing.

- [ ] **Step 3: Commit**

```bash
git add .github/workflows/build-plugin.yml
git commit -m "ci: build wk40/wk41 with cargo-zigbuild + verify glibc floor via objdump"
```

---

### Task 5: Update native crate README

**Files:**
- Modify: `native/LinuxWebKitGtkBridge/README.md`

- [ ] **Step 1: Read current state**

Run:
```bash
cat native/LinuxWebKitGtkBridge/README.md
```

Note the existing section that describes CI build configuration (likely talks about ubuntu-22.04 host) — that text is what we are correcting.

- [ ] **Step 2: Add or update a "CI build & glibc floor" section**

If the README already has a CI/build section, replace its build-command paragraph; otherwise append a new section before the final newline. Use this content (adjust headings/level to fit the surrounding markdown):

```markdown
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
```

- [ ] **Step 3: Commit**

```bash
git add native/LinuxWebKitGtkBridge/README.md
git commit -m "docs: document zigbuild + glibc floor in native bridge README"
```

---

### Task 6: Verify end-to-end

- [ ] **Step 1: Compile the project**

Run:
```bash
./gradlew compileKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Run the focused test**

Run:
```bash
./gradlew test --tests 'io.github.barsia.speqa.editor.webview.WebViewFailureMessageTest'
```

Expected: 8/8 pass.

- [ ] **Step 3: Run the full suite**

Run:
```bash
./gradlew test
```

Expected: BUILD SUCCESSFUL with no regressions.

- [ ] **Step 4: Confirm git log shows the intended commits**

Run:
```bash
git log --oneline -8
```

Expected (top to bottom):
1. `docs: document zigbuild + glibc floor in native bridge README`
2. `ci: build wk40/wk41 with cargo-zigbuild + verify glibc floor via objdump`
3. `webview: walk full cause chain when rendering the unsupported panel`
4. `i18n: add webview.failure.causedBy bundle key`
5. `spec: pin wk40/wk41 glibc floors and describe zigbuild CI flow`
6. `27a4494 webview: walk exception causes when rendering the unsupported panel` (pre-existing)

- [ ] **Step 5: Note for the user — the actual fix lands when CI rebuilds**

The CI workflow change does not produce a new plugin ZIP automatically. After all commits are pushed, trigger `Build plugin` from the GitHub Actions tab (leave `release_tag` empty for a dry run). Download the ZIP, install it into the IDE, and confirm:
- The wk40 bridge loads on Ubuntu 20.04 (no more `GLIBC_2.32 not found`).
- If a load still fails for some other reason, the unsupported panel now shows both the outer message AND `Caused by: …` with the underlying cause.

---

## Self-review notes

- **Spec coverage**: spec Build section now mirrors what CI does (Task 1); spec change comes first per project rule (PreToolUse spec-edit gate). ✓
- **Placeholder scan**: every step contains real code/commands; no "TBD"/"similar to". ✓
- **Type consistency**: `rootFailureMessage` signature unchanged (`(Throwable) -> String`); call sites in `SpeqaWebViewPreviewPanel.kt:804` and `SpeqaWebViewRunPanel.kt:589` continue to work without modification. ✓
- **Bundle key**: `webview.failure.causedBy` introduced in Task 2 before being consumed in Task 3. ✓
- **Pre-existing rule violation noted, not fixed**: the literal `"SpeQA WebView preview is unavailable: …"` in the panel files is hardcoded and pre-existing. Out of scope here; flag for a follow-up.
- **TDD discipline**: Task 3 writes the test first, runs it to confirm failure with the current `firstOrNull()` implementation, then implements, then re-runs. ✓
- **Frequent commits**: one logical change per commit (spec, bundle, code+test, CI, README). ✓
