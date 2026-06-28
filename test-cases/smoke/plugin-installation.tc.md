---
id: 1
title: Install the SpeQA plugin from Marketplace
priority: critical
status: ready
environment:
  - "IntelliJ IDEA"
tags:
  - installation
  - smoke
---

Installing SpeQA from Marketplace activates the SpeQA tool window and the `.tc.md` / `.tr.md` editors with their preview.

Links:

[SpeQA installation guide](https://barsia.github.io/speqa/docs/installation/)

Preconditions:

1. A supported JetBrains IDE (IntelliJ IDEA) is installed and open
2. The IDE has access to the JetBrains Marketplace

Scenario:

1. Open "Settings/Preferences | Plugins" and select the "Marketplace" tab
   > The Marketplace search field is shown

2. 1. Search for "SpeQA"
   2. Click "Get" on the "SpeQA - Test Management System" plugin
   3. Click "Install" to confirm
   > The plugin installs and a prompt to restart the IDE appears

3. Restart the IDE when prompted
   > 1. "SpeQA - Test Management System" is listed under "Settings/Preferences | Plugins | Installed"
   > 2. Right-clicking a project folder shows a "New | Test Case" menu item

4. Open any `.tc.md` file
   > The file opens in the SpeQA split editor with a Markdown source pane and a rendered preview, not the plain Markdown editor
