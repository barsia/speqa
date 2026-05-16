# SpeQA WebView standalone

Standalone IntelliJ plugin project exported from the Ultimate workspace.

Original `/Users/Siarhei.Baradulia/Marketplace_IDE/speqa` was not modified.

Run locally with:

```bash
./gradlew runIde
```

On macOS the WebView preview uses WKWebView through the bundled SpeQA runtime. Windows/Linux native bridge binaries still need to be built and copied into `src/main/resources/native/windows` or `src/main/resources/native/linux` for packaged distribution.
