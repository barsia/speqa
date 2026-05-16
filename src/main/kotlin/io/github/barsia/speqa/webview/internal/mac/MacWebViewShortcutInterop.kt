// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package io.github.barsia.speqa.webview.internal.mac

import java.awt.event.InputEvent
import java.awt.event.KeyEvent

internal enum class MacWebViewEditCommand(val selectorName: String) {
  COPY("copy:"),
  PASTE("paste:"),
  CUT("cut:"),
  SELECT_ALL("selectAll:"),
  UNDO("undo:"),
  REDO("redo:"),
}

internal object MacWebViewShortcutInterop {
  fun commandNamed(command: String): MacWebViewEditCommand? =
    when (command) {
      "copy" -> MacWebViewEditCommand.COPY
      "paste" -> MacWebViewEditCommand.PASTE
      "cut" -> MacWebViewEditCommand.CUT
      "selectAll" -> MacWebViewEditCommand.SELECT_ALL
      "undo" -> MacWebViewEditCommand.UNDO
      "redo" -> MacWebViewEditCommand.REDO
      else -> null
    }

  fun shouldConsumeWithoutDispatch(event: KeyEvent): Boolean {
    if (event.id != KeyEvent.KEY_PRESSED) return false
    if (event.keyCode != KeyEvent.VK_Z) return false

    val relevantModifiers = event.modifiersEx and (
      InputEvent.META_DOWN_MASK or
        InputEvent.SHIFT_DOWN_MASK or
        InputEvent.CTRL_DOWN_MASK or
        InputEvent.ALT_DOWN_MASK
      )
    // Swallow undo / redo shortcuts (Cmd+Z, Cmd+Shift+Z, Ctrl+Z, Ctrl+Shift+Z) so
    // that AWT/IntelliJ's global Undo action does not steal focus away from the
    // WebView's text input. The shortcut is intentionally not redispatched to
    // WKWebView: a native `undo:` would operate on WebKit's local field history,
    // which diverges from the document-level undo that the IDE manages.
    return relevantModifiers == InputEvent.META_DOWN_MASK ||
           relevantModifiers == (InputEvent.META_DOWN_MASK or InputEvent.SHIFT_DOWN_MASK) ||
           relevantModifiers == InputEvent.CTRL_DOWN_MASK ||
           relevantModifiers == (InputEvent.CTRL_DOWN_MASK or InputEvent.SHIFT_DOWN_MASK)
  }

  fun commandFor(event: KeyEvent): MacWebViewEditCommand? {
    if (event.id != KeyEvent.KEY_PRESSED) return null
    if (event.keyCode == KeyEvent.VK_UNDEFINED || isModifierKey(event.keyCode)) return null

    val relevantModifiers = event.modifiersEx and (
      InputEvent.META_DOWN_MASK or
        InputEvent.SHIFT_DOWN_MASK or
        InputEvent.CTRL_DOWN_MASK or
        InputEvent.ALT_DOWN_MASK
      )
    if (relevantModifiers and InputEvent.ALT_DOWN_MASK != 0) return null

    return when (event.keyCode) {
      KeyEvent.VK_C -> if (isPlainCommand(relevantModifiers)) MacWebViewEditCommand.COPY else null
      KeyEvent.VK_V -> if (isPlainCommand(relevantModifiers)) MacWebViewEditCommand.PASTE else null
      KeyEvent.VK_X -> if (isPlainCommand(relevantModifiers)) MacWebViewEditCommand.CUT else null
      KeyEvent.VK_A -> if (relevantModifiers == InputEvent.META_DOWN_MASK) MacWebViewEditCommand.SELECT_ALL else null
      else -> null
    }
  }

  private fun isPlainCommand(modifiersEx: Int): Boolean {
    return modifiersEx == InputEvent.META_DOWN_MASK || modifiersEx == InputEvent.CTRL_DOWN_MASK
  }

  private fun isModifierKey(keyCode: Int): Boolean {
    return keyCode == KeyEvent.VK_SHIFT ||
           keyCode == KeyEvent.VK_CONTROL ||
           keyCode == KeyEvent.VK_ALT ||
           keyCode == KeyEvent.VK_META ||
           keyCode == KeyEvent.VK_ALT_GRAPH
  }
}
