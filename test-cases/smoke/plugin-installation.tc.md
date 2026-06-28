---
id: 1
title: Install the SpeQA plugin from Marketplace
priority: critical
status: draft
tags:
  - installation
  - smoke
---

Installing SpeQA from Marketplace activates the SpeQA tool window and the `.tc.md` / `.tr.md` editors.

Preconditions:

1. A compatible JetBrains IDE (IntelliJ IDEA) is installed and open
2. The IDE has access to the JetBrains Marketplace

Scenario:

1. Open "Settings | Plugins" and switch to the "Marketplace" tab
   > The Marketplace search field is shown

2. Search for "SpeQA" and install "SpeQA - Test Management System"
   > The plugin downloads and a button to restart the IDE appears

3. Restart the IDE
   > 1. "SpeQA - Test Management System" is listed under "Settings | Plugins | Installed" with no error
   > 2. A "SpeQA" tool window is available on the left tool window bar

4. Open any `.tc.md` file
   > The file opens in the SpeQA editor with a raw Markdown source pane and a rendered preview pane, not the plain Markdown editor
