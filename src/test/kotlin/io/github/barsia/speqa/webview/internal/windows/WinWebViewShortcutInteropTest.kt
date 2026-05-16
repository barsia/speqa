package io.github.barsia.speqa.webview.internal.windows

import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.JPanel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class WinWebViewShortcutInteropTest {
  @Test
  fun `create key event maps WebView2 key down to AWT key pressed`() {
    val event = WinWebViewShortcutInterop.createKeyEvent(
      source = JPanel(),
      keyEventKind = WinWebViewShortcutInterop.KEY_EVENT_KIND_KEY_DOWN,
      virtualKey = VK_RETURN,
      modifierFlags = WinWebViewShortcutInterop.MODIFIER_CONTROL or WinWebViewShortcutInterop.MODIFIER_SHIFT,
      keyEventLParam = 0,
    )!!

    assertEquals(KeyEvent.KEY_PRESSED, event.id)
    assertEquals(KeyEvent.VK_ENTER, event.keyCode)
    assertEquals(InputEvent.CTRL_DOWN_MASK or InputEvent.SHIFT_DOWN_MASK, event.modifiersEx)
    assertEquals(KeyEvent.KEY_LOCATION_STANDARD, event.keyLocation)
  }

  @Test
  fun `create key event maps WebView2 key up to AWT key released`() {
    val event = WinWebViewShortcutInterop.createKeyEvent(
      source = JPanel(),
      keyEventKind = WinWebViewShortcutInterop.KEY_EVENT_KIND_SYSTEM_KEY_UP,
      virtualKey = VK_DELETE,
      modifierFlags = WinWebViewShortcutInterop.MODIFIER_ALT,
      keyEventLParam = 0,
    )!!

    assertEquals(KeyEvent.KEY_RELEASED, event.id)
    assertEquals(KeyEvent.VK_DELETE, event.keyCode)
    assertEquals(InputEvent.ALT_DOWN_MASK, event.modifiersEx)
  }

  @Test
  fun `create key event rejects unknown key event kind`() {
    assertNull(
      WinWebViewShortcutInterop.createKeyEvent(
        source = JPanel(),
        keyEventKind = 99,
        virtualKey = VK_RETURN,
        modifierFlags = 0,
        keyEventLParam = 0,
      ),
    )
  }

  @Test
  fun `key location distinguishes left right and numpad keys`() {
    val leftControl = WinWebViewShortcutInterop.createKeyEvent(JPanel(), WinWebViewShortcutInterop.KEY_EVENT_KIND_KEY_DOWN, VK_LCONTROL, 0, 0)!!
    val rightControl = WinWebViewShortcutInterop.createKeyEvent(JPanel(), WinWebViewShortcutInterop.KEY_EVENT_KIND_KEY_DOWN, VK_RCONTROL, 0, 0)!!
    val numpadZero = WinWebViewShortcutInterop.createKeyEvent(JPanel(), WinWebViewShortcutInterop.KEY_EVENT_KIND_KEY_DOWN, VK_NUMPAD0, 0, 0)!!

    assertEquals(KeyEvent.KEY_LOCATION_LEFT, leftControl.keyLocation)
    assertEquals(KeyEvent.KEY_LOCATION_RIGHT, rightControl.keyLocation)
    assertEquals(KeyEvent.KEY_LOCATION_NUMPAD, numpadZero.keyLocation)
  }

  @Test
  fun `shortcut candidate forwards IDE shortcuts and keeps browser editing shortcuts in webview`() {
    assertTrue(WinWebViewShortcutInterop.isShortcutCandidate(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK))
    assertTrue(WinWebViewShortcutInterop.isShortcutCandidate(KeyEvent.VK_F5, 0))
    assertTrue(WinWebViewShortcutInterop.isShortcutCandidate(KeyEvent.VK_ESCAPE, 0))

    assertFalse(WinWebViewShortcutInterop.isShortcutCandidate(KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK))
    assertFalse(WinWebViewShortcutInterop.isShortcutCandidate(KeyEvent.VK_INSERT, InputEvent.SHIFT_DOWN_MASK))
    assertFalse(WinWebViewShortcutInterop.isShortcutCandidate(KeyEvent.VK_SHIFT, InputEvent.SHIFT_DOWN_MASK))
    assertFalse(WinWebViewShortcutInterop.isShortcutCandidate(KeyEvent.VK_A, 0))
  }

  private companion object {
    private const val VK_RETURN: Int = 0x0D
    private const val VK_DELETE: Int = 0x2E
    private const val VK_NUMPAD0: Int = 0x60
    private const val VK_LCONTROL: Int = 0xA2
    private const val VK_RCONTROL: Int = 0xA3
  }
}
