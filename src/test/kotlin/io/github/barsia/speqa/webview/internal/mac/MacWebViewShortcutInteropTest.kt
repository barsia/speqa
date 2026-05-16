package io.github.barsia.speqa.webview.internal.mac

import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.io.File
import javax.swing.JPanel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MacWebViewShortcutInteropTest {
  @Test
  fun `command shortcuts map to WKWebView editing commands`() {
    assertEquals(MacWebViewEditCommand.COPY, command(KeyEvent.VK_C, InputEvent.META_DOWN_MASK))
    assertEquals(MacWebViewEditCommand.PASTE, command(KeyEvent.VK_V, InputEvent.META_DOWN_MASK))
    assertEquals(MacWebViewEditCommand.CUT, command(KeyEvent.VK_X, InputEvent.META_DOWN_MASK))
    assertEquals(MacWebViewEditCommand.SELECT_ALL, command(KeyEvent.VK_A, InputEvent.META_DOWN_MASK))
    assertNull(command(KeyEvent.VK_Z, InputEvent.META_DOWN_MASK))
    assertNull(command(KeyEvent.VK_Z, InputEvent.META_DOWN_MASK or InputEvent.SHIFT_DOWN_MASK))
  }

  @Test
  fun `undo redo shortcuts are consumed without sending a second AppKit command`() {
    assertTrue(consumeOnly(KeyEvent.VK_Z, InputEvent.META_DOWN_MASK))
    assertTrue(consumeOnly(KeyEvent.VK_Z, InputEvent.META_DOWN_MASK or InputEvent.SHIFT_DOWN_MASK))
    assertTrue(consumeOnly(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK))
    assertTrue(consumeOnly(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK or InputEvent.SHIFT_DOWN_MASK))
    assertFalse(consumeOnly(KeyEvent.VK_C, InputEvent.META_DOWN_MASK))
  }

  @Test
  fun `control editing shortcuts map to WKWebView editing commands`() {
    assertEquals(MacWebViewEditCommand.COPY, command(KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK))
    assertEquals(MacWebViewEditCommand.PASTE, command(KeyEvent.VK_V, InputEvent.CTRL_DOWN_MASK))
    assertEquals(MacWebViewEditCommand.CUT, command(KeyEvent.VK_X, InputEvent.CTRL_DOWN_MASK))
  }

  @Test
  fun `JS editing command names map to WKWebView editing commands`() {
    assertEquals(MacWebViewEditCommand.COPY, MacWebViewShortcutInterop.commandNamed("copy"))
    assertEquals(MacWebViewEditCommand.PASTE, MacWebViewShortcutInterop.commandNamed("paste"))
    assertEquals(MacWebViewEditCommand.CUT, MacWebViewShortcutInterop.commandNamed("cut"))
    assertEquals(MacWebViewEditCommand.SELECT_ALL, MacWebViewShortcutInterop.commandNamed("selectAll"))
    assertNull(MacWebViewShortcutInterop.commandNamed("save"))
  }

  @Test
  fun `non editing shortcuts stay in the IDE action system`() {
    assertNull(command(KeyEvent.VK_S, InputEvent.META_DOWN_MASK))
    assertNull(command(KeyEvent.VK_V, InputEvent.META_DOWN_MASK or InputEvent.ALT_DOWN_MASK))
    assertNull(command(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK))
    assertNull(
      MacWebViewShortcutInterop.commandFor(
        KeyEvent(
          JPanel(),
          KeyEvent.KEY_RELEASED,
          System.currentTimeMillis(),
          InputEvent.META_DOWN_MASK,
          KeyEvent.VK_V,
          KeyEvent.CHAR_UNDEFINED,
        ),
      ),
    )
  }

  @Test
  fun `bridge dispatches edit commands through AppKit responder chain`() {
    val source = File(
      System.getProperty("user.dir"),
      "src/main/kotlin/io/github/barsia/speqa/webview/internal/mac/WKWebViewBridge.kt",
    ).readText()

    assertTrue(source.contains("SEL_SEND_ACTION_TO_FROM"))
    assertTrue(source.contains("ID.NIL, webView"))
    assertFalse(source.contains("invoke(webView, command.selectorName"))

    val performEditCommandBody = source
      .substringAfter("fun performEditCommand")
      .substringBefore("\n  /**\n   * Releases")
    assertFalse(performEditCommandBody.contains("SEL_MAKE_FIRST_RESPONDER"))
  }

  private fun command(keyCode: Int, modifiersEx: Int): MacWebViewEditCommand? =
    MacWebViewShortcutInterop.commandFor(
      KeyEvent(
        JPanel(),
        KeyEvent.KEY_PRESSED,
        System.currentTimeMillis(),
        modifiersEx,
        keyCode,
        KeyEvent.CHAR_UNDEFINED,
      ),
    )

  private fun consumeOnly(keyCode: Int, modifiersEx: Int): Boolean =
    MacWebViewShortcutInterop.shouldConsumeWithoutDispatch(
      KeyEvent(
        JPanel(),
        KeyEvent.KEY_PRESSED,
        System.currentTimeMillis(),
        modifiersEx,
        keyCode,
        KeyEvent.CHAR_UNDEFINED,
      ),
    )
}
