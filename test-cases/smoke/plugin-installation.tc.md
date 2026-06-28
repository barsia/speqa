---
id: 1
title: Install the built SpeQA plugin from disk
priority: critical
status: ready
environment:
  - "IntelliJ IDEA"
tags:
  - installation
  - smoke
---

A locally built, verified SpeQA distribution installs from disk and activates the "SpeQA Test Case" creation action and the `.tc.md` split editor with preview.

Links:

[SpeQA installation guide](https://barsia.github.io/speqa/docs/installation/)

Preconditions:

1. A supported JetBrains IDE (IntelliJ IDEA) is installed and open
2. The SpeQA plugin distribution is built with `./gradlew buildPlugin` and passes `./gradlew verifyPlugin`

Scenario:

1. 1. Open "Settings/Preferences | Plugins"
   2. Click the gear icon
   3. Choose "Install Plugin from Disk..."
   4. Select the built `.zip` from "build/distributions/"
   5. Restart the IDE when prompted
   > 1. "SpeQA - Test Management System" is listed under "Plugins | Installed"
   > 2. The "New" menu (File | New, or right-click any folder in the Project view) contains a "SpeQA Test Case" item

2. 1. Right-click a folder in the Project view and choose "New | SpeQA Test Case"
   2. Enter a file name
   3. Confirm the dialog
   > A new `.tc.md` file is created and opens in the SpeQA editor with a Markdown source pane and a rendered preview
