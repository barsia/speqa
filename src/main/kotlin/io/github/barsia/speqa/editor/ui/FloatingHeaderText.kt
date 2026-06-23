// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package io.github.barsia.speqa.editor.ui

/**
 * Composes the floating-header display string from the panel's id-prefix
 * (`"TC-"` / `"TR-"`), id, and title. Falls back to [untitled] when both id
 * and title are blank.
 */
fun floatingHeaderText(idPrefix: String, id: String, title: String, untitled: String): String {
  val trimmedTitle = title.trim()
  val hasId = id.isNotBlank()
  val hasTitle = trimmedTitle.isNotEmpty()
  return when {
    hasId && hasTitle -> "$idPrefix$id · $trimmedTitle"
    hasId -> "$idPrefix$id"
    hasTitle -> trimmedTitle
    else -> untitled
  }
}
