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

A locally built, verified SpeQA distribution installs from disk and its features become available in the IDE.

Links:

[SpeQA installation guide](https://barsia.github.io/speqa/docs/installation/)

Preconditions:

1. The SpeQA plugin distribution is built with `./gradlew buildPlugin` and passes `./gradlew verifyPlugin`
2. A supported JetBrains IDE (IntelliJ IDEA) is installed and open

Scenario:

1. 1. Open "Settings/Preferences | Plugins"
   2. Click the gear icon
   3. Choose "Install Plugin from Disk..."
   4. Select the built `.zip` from "build/distributions/"
   5. Restart the IDE when prompted
   > "SpeQA - Test Management System" is listed under "Plugins | Installed" with no error

2. Open any `.tc.md` file
   > 1. It opens in the SpeQA editor with a Markdown source pane and a rendered preview
   > 2. A "SpeQA" tool window is available and the "New" menu offers a "SpeQA Test Case" item
