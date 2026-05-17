// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

use std::env;
use std::process::Command;

fn main() {
    println!("cargo:rerun-if-env-changed=PKG_CONFIG_PATH");
    let w40 = env::var_os("CARGO_FEATURE_WEBKIT40").is_some();
    let w41 = env::var_os("CARGO_FEATURE_WEBKIT41").is_some();
    let webkit_pkg = match (w40, w41) {
        (true, false) => "webkit2gtk-4.0",
        (false, true) => "webkit2gtk-4.1",
        (true, true) => panic!(
            "Enable exactly one of the `webkit40` / `webkit41` cargo features, not both. \
             Detected: webkit40=true, webkit41=true."
        ),
        (false, false) => panic!(
            "Enable exactly one of the `webkit40` / `webkit41` cargo features. \
             Detected: webkit40=false, webkit41=false."
        ),
    };
    emit_pkg_config(webkit_pkg);
    emit_pkg_config("gtk+-x11-3.0");
    emit_pkg_config("x11");
}

fn emit_pkg_config(package: &str) {
    let output = Command::new("pkg-config")
        .args(["--libs", package])
        .output()
        .unwrap_or_else(|error| panic!("failed to execute pkg-config for {package}: {error}"));

    if !output.status.success() {
        let stderr = String::from_utf8_lossy(&output.stderr);
        panic!("pkg-config --libs {package} failed: {stderr}");
    }

    let stdout = String::from_utf8_lossy(&output.stdout);
    for token in stdout.split_whitespace() {
        if let Some(path) = token.strip_prefix("-L") {
            println!("cargo:rustc-link-search=native={path}");
        } else if let Some(library) = token.strip_prefix("-l") {
            println!("cargo:rustc-link-lib={library}");
        } else if token == "-pthread" || token.starts_with("-Wl,") {
            println!("cargo:rustc-link-arg={token}");
        }
    }

    // pkg-config --libs omits -L for paths that the default GNU linker already searches
    // (e.g. /usr/lib/x86_64-linux-gnu). Linkers that don't search those system defaults
    // — notably zig's lld via cargo-zigbuild — fail to resolve the .so files. Emit the
    // package's libdir as an explicit link-search; redundant for stock ld, required for zig.
    let libdir_output = Command::new("pkg-config")
        .args(["--variable=libdir", package])
        .output()
        .unwrap_or_else(|error| panic!("failed to query libdir for {package}: {error}"));

    if libdir_output.status.success() {
        let libdir = String::from_utf8_lossy(&libdir_output.stdout).trim().to_string();
        if !libdir.is_empty() {
            println!("cargo:rustc-link-search=native={libdir}");
        }
    }
}
