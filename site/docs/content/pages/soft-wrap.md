---
title: Soft Wrap
---

Soft wrap makes long lines fold visually inside the editor without inserting actual line breaks into the file. The source stays untouched; only the display changes.

SpeQA enables soft wrap by default for all `.tc.md` and `.tr.md` files, so you can read full step descriptions and expected results without horizontal scrolling.

## Toggling Soft Wrap

There are two ways to toggle soft wrap for the current editor:

**From the gutter menu**

Right-click the left gutter (the narrow strip to the left of the line numbers) and choose **Soft-Wrap Current Editor**. The change applies only to the open editor tab, not to the file or to other tabs.

**From Find Action**

Press `⌘⇧A` on macOS or `Ctrl+Shift+A` on Windows/Linux to open Find Action, then type `soft-wrap` (the hyphen matters). You can apply it to the current file or globally.

## Wrap Indicators

When a line wraps, IntelliJ draws a small arrow at the wrap point. By default SpeQA hides these arrows in the preview fields to reduce visual noise, but they are visible in the raw text editor.

To show indicators only on the line where the cursor is - rather than on every wrapped line - open **Settings / Editor / General** and enable **Only show soft-wrap indicators for current line**. See the [Editor General settings](https://www.jetbrains.com/help/idea/settings-editor-general.html) in the JetBrains documentation for the full list of available options.

## Per-File Type Configuration

If you want to apply or remove soft wrap for other file types globally, go to **Settings / Editor / General / Soft-wrap these files** and add or remove extensions separated by semicolons. SpeQA already adds `.tc.md` and `.tr.md` to this list when the plugin loads.
