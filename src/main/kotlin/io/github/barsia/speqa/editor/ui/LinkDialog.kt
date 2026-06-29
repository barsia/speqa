// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package io.github.barsia.speqa.editor.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.panel
import io.github.barsia.speqa.SpeqaBundle
import javax.swing.JComponent

/** A link's display text and target URL, as entered in [LinkDialog]. */
data class LinkInput(val text: String, val url: String)

/**
 * True only for a non-blank `http://` or `https://` URL. Pure and top-level so the
 * validation contract can be tested without the IntelliJ platform.
 */
fun isValidLinkUrl(s: String): Boolean {
  val trimmed = s.trim()
  if (trimmed.isBlank()) return false
  return trimmed.startsWith("http://") || trimmed.startsWith("https://")
}

/**
 * Shared modal dialog to enter or edit a link's text and URL. The OK button is enabled
 * only while the URL satisfies [isValidLinkUrl]; the URL field shows an inline error
 * otherwise.
 */
class LinkDialog(
  project: Project,
  initialText: String,
  initialUrl: String,
) : DialogWrapper(project) {

  private var text: String = initialText
  private var url: String = initialUrl

  init {
    title = SpeqaBundle.message("dialog.link.title")
    init()
  }

  override fun createCenterPanel(): JComponent = panel {
    row(SpeqaBundle.message("dialog.link.text")) {
      textField()
        .bindText(::text)
        .focused()
        .columns(30)
    }
    row(SpeqaBundle.message("dialog.link.url")) {
      textField()
        .bindText(::url)
        .columns(30)
        .validationOnInput { field ->
          if (isValidLinkUrl(field.text)) null
          else ValidationInfo(SpeqaBundle.message("dialog.link.invalidUrl"), field)
        }
    }
  }

  /** Returns the entered values, applying the bound fields back first. */
  fun result(): LinkInput {
    return LinkInput(text = text.trim(), url = url.trim())
  }

  companion object {
    /**
     * Shows the modal dialog seeded with [initialText]/[initialUrl] and returns the entered
     * [LinkInput], or `null` if the user cancelled.
     */
    fun edit(project: Project, initialText: String, initialUrl: String): LinkInput? {
      val dialog = LinkDialog(project, initialText, initialUrl)
      return if (dialog.showAndGet()) dialog.result() else null
    }
  }
}
