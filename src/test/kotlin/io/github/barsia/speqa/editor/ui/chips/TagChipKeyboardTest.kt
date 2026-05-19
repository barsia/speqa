// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package io.github.barsia.speqa.editor.ui.chips

import org.junit.Assert.assertEquals
import org.junit.Test
import java.awt.Rectangle
import java.awt.event.KeyEvent

class TagChipKeyboardTest {

  private fun dispatch(
    keyCode: Int,
    onClick: () -> Unit = {},
    onEdit: () -> Unit = {},
    onDelete: () -> Unit = {},
  ) = tagChipKeyAction(keyCode, onClick, onEdit, onDelete)

  @Test
  fun `Enter key invokes onClick`() {
    var clicks = 0
    dispatch(KeyEvent.VK_ENTER, onClick = { clicks++ })
    assertEquals(1, clicks)
  }

  @Test
  fun `Space key invokes onClick`() {
    var clicks = 0
    dispatch(KeyEvent.VK_SPACE, onClick = { clicks++ })
    assertEquals(1, clicks)
  }

  @Test
  fun `F2 key invokes onEdit`() {
    var edits = 0
    dispatch(KeyEvent.VK_F2, onEdit = { edits++ })
    assertEquals(1, edits)
  }

  @Test
  fun `Delete key invokes onDelete`() {
    var deletes = 0
    dispatch(KeyEvent.VK_DELETE, onDelete = { deletes++ })
    assertEquals(1, deletes)
  }

  @Test
  fun `Backspace key invokes onDelete`() {
    var deletes = 0
    dispatch(KeyEvent.VK_BACK_SPACE, onDelete = { deletes++ })
    assertEquals(1, deletes)
  }

  @Test
  fun `delete action is hidden at rest`() {
    assertEquals(false, shouldShowTagChipDeleteAction(hasDelete = true, hovered = false, chipFocused = false, deleteFocused = false))
  }

  @Test
  fun `delete action is visible on hover`() {
    assertEquals(true, shouldShowTagChipDeleteAction(hasDelete = true, hovered = true, chipFocused = false, deleteFocused = false))
  }

  @Test
  fun `delete action is visible when chip is keyboard focused`() {
    assertEquals(true, shouldShowTagChipDeleteAction(hasDelete = true, hovered = false, chipFocused = true, deleteFocused = false))
  }

  @Test
  fun `delete action is hidden when chip has no delete callback`() {
    assertEquals(false, shouldShowTagChipDeleteAction(hasDelete = false, hovered = true, chipFocused = true, deleteFocused = true))
  }

  @Test
  fun `delete action is centered on visual chip corner`() {
    val geometry = tagChipCornerDeleteGeometry(width = 80, height = 32, deleteButtonSize = 18)

    assertEquals(Rectangle(0, 9, 71, 23), geometry.fillBounds)
    assertEquals(Rectangle(60, 0, 18, 18), geometry.deleteButtonBounds)
    assertEquals(geometry.fillBounds.x + geometry.fillBounds.width - 2, geometry.deleteButtonBounds.x + geometry.deleteButtonBounds.width / 2)
    assertEquals(geometry.fillBounds.y, geometry.deleteButtonBounds.y + geometry.deleteButtonBounds.height / 2)
  }
}
