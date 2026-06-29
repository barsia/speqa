---
title: Installation
---

Install SpeQA in your JetBrains IDE in just a few clicks.

## Option 1: Quick Install from Marketplace (Recommended)

The easiest way to get SpeQA is directly from the JetBrains plugin marketplace. You can also open the plugin's [marketplace page](https://plugins.jetbrains.com/plugin/31268-speqa--test-management-system) and install it from there.

### Steps

1. Open your JetBrains IDE (IntelliJ IDEA, WebStorm, PyCharm, PhpStorm, RubyMine, etc.)
2. Navigate to **Settings/Preferences > Plugins** (or use the menu: IDE > Preferences > Plugins on macOS)
3. Click the **Marketplace** tab
4. Search for **"SpeQA"**
5. Click the **Get** button
6. Click **Install** to confirm
7. Restart the IDE when prompted

That's it! SpeQA is now active in your IDE.

## Option 2: Manual Install from File

If you have a plugin JAR file:

1. Open **Settings/Preferences > Plugins**
2. Click the gear icon and select **Install Plugin from Disk**
3. Navigate to the SpeQA `.jar` file and select it
4. Click **Install**
5. Restart the IDE

## Verify Installation

After installation, you should see SpeQA features available:

- Right-click a folder in your project and look for the **New > Test Case** option
- The **SpeQA** tool window button appears on the left stripe (see [the tool window guide](./tool-window.md))
- A **Test Cases Project** generator appears in **File > New > Project**
- Open any `.tc.md` file (test case) and you should see the split editor with preview

## Troubleshooting

**Plugin doesn't appear after install:**
- Restart the IDE completely (not just close the settings dialog)
- Check that the IDE version is compatible (usually recent versions are supported)

**Right-click menu doesn't show "New > Test Case":**
- Make sure you're right-clicking inside a project folder, not on the IDE itself
- Try opening a project folder first if you haven't already

**Need help?**
- Check the [Getting Started](./index.md) guide
- See [Creating a Test Case Project](./creating-project.md) for next steps

## What's Next?

Once installed, [create your first test case project](./creating-project.md) using the wizard.
