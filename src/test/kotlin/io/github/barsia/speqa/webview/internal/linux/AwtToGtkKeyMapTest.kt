// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package io.github.barsia.speqa.webview.internal.linux

import java.awt.event.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Test

class AwtToGtkKeyMapTest {
  @Test
  fun `ascii letter a maps to gdk_key_a`() {
    assertEquals(0x061, AwtToGtkKeyMap.gdkKeyval(KeyEvent.VK_A, 'a'))
  }

  @Test
  fun `ascii letter A uppercase maps to gdk_key_A`() {
    assertEquals(0x041, AwtToGtkKeyMap.gdkKeyval(KeyEvent.VK_A, 'A'))
  }

  @Test
  fun `enter maps to gdk_key_Return`() {
    assertEquals(0xff0d, AwtToGtkKeyMap.gdkKeyval(KeyEvent.VK_ENTER, KeyEvent.CHAR_UNDEFINED))
  }

  @Test
  fun `backspace maps to gdk_key_BackSpace`() {
    assertEquals(0xff08, AwtToGtkKeyMap.gdkKeyval(KeyEvent.VK_BACK_SPACE, KeyEvent.CHAR_UNDEFINED))
  }

  @Test
  fun `tab maps to gdk_key_Tab`() {
    assertEquals(0xff09, AwtToGtkKeyMap.gdkKeyval(KeyEvent.VK_TAB, KeyEvent.CHAR_UNDEFINED))
  }

  @Test
  fun `escape maps to gdk_key_Escape`() {
    assertEquals(0xff1b, AwtToGtkKeyMap.gdkKeyval(KeyEvent.VK_ESCAPE, KeyEvent.CHAR_UNDEFINED))
  }

  @Test
  fun `arrow keys map to gdk_key_Left_Right_Up_Down`() {
    assertEquals(0xff51, AwtToGtkKeyMap.gdkKeyval(KeyEvent.VK_LEFT, KeyEvent.CHAR_UNDEFINED))
    assertEquals(0xff52, AwtToGtkKeyMap.gdkKeyval(KeyEvent.VK_UP, KeyEvent.CHAR_UNDEFINED))
    assertEquals(0xff53, AwtToGtkKeyMap.gdkKeyval(KeyEvent.VK_RIGHT, KeyEvent.CHAR_UNDEFINED))
    assertEquals(0xff54, AwtToGtkKeyMap.gdkKeyval(KeyEvent.VK_DOWN, KeyEvent.CHAR_UNDEFINED))
  }

  @Test
  fun `home and end map correctly`() {
    assertEquals(0xff50, AwtToGtkKeyMap.gdkKeyval(KeyEvent.VK_HOME, KeyEvent.CHAR_UNDEFINED))
    assertEquals(0xff57, AwtToGtkKeyMap.gdkKeyval(KeyEvent.VK_END, KeyEvent.CHAR_UNDEFINED))
  }

  @Test
  fun `page up and down map correctly`() {
    assertEquals(0xff55, AwtToGtkKeyMap.gdkKeyval(KeyEvent.VK_PAGE_UP, KeyEvent.CHAR_UNDEFINED))
    assertEquals(0xff56, AwtToGtkKeyMap.gdkKeyval(KeyEvent.VK_PAGE_DOWN, KeyEvent.CHAR_UNDEFINED))
  }

  @Test
  fun `delete maps to gdk_key_Delete`() {
    assertEquals(0xffff, AwtToGtkKeyMap.gdkKeyval(KeyEvent.VK_DELETE, KeyEvent.CHAR_UNDEFINED))
  }

  @Test
  fun `space maps to gdk_key_space`() {
    assertEquals(0x020, AwtToGtkKeyMap.gdkKeyval(KeyEvent.VK_SPACE, ' '))
  }

  @Test
  fun `unknown key with no char returns zero sentinel`() {
    assertEquals(0, AwtToGtkKeyMap.gdkKeyval(KeyEvent.VK_UNDEFINED, KeyEvent.CHAR_UNDEFINED))
  }
}
