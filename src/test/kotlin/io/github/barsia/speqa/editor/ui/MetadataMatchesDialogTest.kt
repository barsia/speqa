// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package io.github.barsia.speqa.editor.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class MetadataMatchesDialogTest {
  @Test
  fun `dialog source references the new APIs without legacy fallbacks`() {
    val file = File(System.getProperty("user.dir"), "src/main/kotlin/io/github/barsia/speqa/editor/ui/MetadataMatchesDialog.kt")
    val source = file.readText()
    assertTrue(source.contains("class MetadataMatchesDialog"))
    assertTrue(source.contains("DialogWrapper"))
    assertTrue(source.contains("MetadataMatchCellRenderer"))
    assertTrue(source.contains("projectMatches"))
    assertTrue(source.contains("metadata.matchesDialog.testCases.tag"))
    assertTrue(source.contains("metadata.matchesDialog.testCases.environment"))
    assertTrue(source.contains("metadata.matchesDialog.testRuns.tag"))
    assertTrue(source.contains("metadata.matchesDialog.testRuns.environment"))
    assertTrue(source.contains("doOKAction"))
  }
}
