// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package io.github.barsia.speqa.editor.ui

import com.intellij.util.ui.EmptyIcon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.Dimension
import javax.swing.JButton
import javax.swing.JLabel

class HeaderUtilityRowLayoutTest {
  @Test
  fun `trailing wrapper enforces no-shrink minimum width equal to preferred`() {
    val idChip = JLabel("TC-12")
    // Use a plain JButton as the trailing component so the test runs without
    // an IntelliJ platform context (speqaIconButton requires ActionManager).
    val run = JButton("Run").apply { preferredSize = Dimension(22, 22) }
    val row = HeaderUtilityRow(
      idChip = idChip,
      leftDateIcon = EmptyIcon.create(16),
      leftDateText = "2026-05-18",
      leftDateNormalTooltip = "Created",
      rightDateIcon = EmptyIcon.create(16),
      rightDateText = "2026-05-18",
      rightDateNormalTooltip = "Updated",
      trailing = run,
    )

    val trailingWrap = row.trailingContainer
    val pref: Dimension = trailingWrap.preferredSize
    val min: Dimension = trailingWrap.minimumSize
    assertEquals("trailing wrapper min width must equal pref width", pref.width, min.width)
    assertTrue("trailing wrapper pref width must be positive", pref.width > 0)
  }
}
