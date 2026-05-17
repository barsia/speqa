// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package io.github.barsia.speqa.webview.internal.linux

import java.awt.event.KeyEvent
import org.jetbrains.annotations.ApiStatus

/**
 * Maps Java AWT key codes / chars to GDK keyvals so input from the IDE can be forwarded
 * into the offscreen WebKitGTK widget. Covers ASCII printable characters and the
 * navigation/editing keys an editor preview needs in practice.
 *
 * For unmapped keys with no usable [Char] payload, returns `0` — the caller should
 * suppress the dispatch instead of sending a meaningless event.
 */
@ApiStatus.Internal
internal object AwtToGtkKeyMap {
  fun gdkKeyval(awtKeyCode: Int, awtKeyChar: Char): Int {
    namedKeys[awtKeyCode]?.let { return it }
    if (awtKeyChar != KeyEvent.CHAR_UNDEFINED && awtKeyChar.code in 0x20..0x7e) {
      return awtKeyChar.code
    }
    return 0
  }

  private val namedKeys: Map<Int, Int> = mapOf(
    KeyEvent.VK_ENTER to 0xff0d,
    KeyEvent.VK_BACK_SPACE to 0xff08,
    KeyEvent.VK_TAB to 0xff09,
    KeyEvent.VK_ESCAPE to 0xff1b,
    KeyEvent.VK_DELETE to 0xffff,
    KeyEvent.VK_INSERT to 0xff63,
    KeyEvent.VK_HOME to 0xff50,
    KeyEvent.VK_END to 0xff57,
    KeyEvent.VK_PAGE_UP to 0xff55,
    KeyEvent.VK_PAGE_DOWN to 0xff56,
    KeyEvent.VK_LEFT to 0xff51,
    KeyEvent.VK_UP to 0xff52,
    KeyEvent.VK_RIGHT to 0xff53,
    KeyEvent.VK_DOWN to 0xff54,
    KeyEvent.VK_F1 to 0xffbe,
    KeyEvent.VK_F2 to 0xffbf,
    KeyEvent.VK_F3 to 0xffc0,
    KeyEvent.VK_F4 to 0xffc1,
    KeyEvent.VK_F5 to 0xffc2,
    KeyEvent.VK_F6 to 0xffc3,
    KeyEvent.VK_F7 to 0xffc4,
    KeyEvent.VK_F8 to 0xffc5,
    KeyEvent.VK_F9 to 0xffc6,
    KeyEvent.VK_F10 to 0xffc7,
    KeyEvent.VK_F11 to 0xffc8,
    KeyEvent.VK_F12 to 0xffc9,
    KeyEvent.VK_SHIFT to 0xffe1,
    KeyEvent.VK_CONTROL to 0xffe3,
    KeyEvent.VK_ALT to 0xffe9,
    KeyEvent.VK_META to 0xffeb,
  )
}
