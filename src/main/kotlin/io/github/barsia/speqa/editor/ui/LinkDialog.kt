// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package io.github.barsia.speqa.editor.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBTextField
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
 * Shared modal dialog to enter or edit a link's text and URL. The OK button is gated
 * by [doValidateAll], so it is disabled from the moment the dialog opens (while the URL
 * is empty or invalid) and stays disabled until a valid http(s) URL is entered.
 */
class LinkDialog(
    project: Project,
    initialText: String,
    initialUrl: String,
    isEdit: Boolean,
) : DialogWrapper(project) {

    private val textField = JBTextField(initialText, 30)
    private val urlField = JBTextField(initialUrl, 30)

    init {
        title = SpeqaBundle.message(
            if (isEdit) "dialog.link.title.edit" else "dialog.link.title.add",
        )
        init()
    }

    override fun getPreferredFocusedComponent(): JComponent = textField

    override fun createCenterPanel(): JComponent = panel {
        row(SpeqaBundle.message("dialog.link.text")) {
            cell(textField)
        }
        row(SpeqaBundle.message("dialog.link.url")) {
            cell(urlField)
                // Live inline feedback while typing; OK gating itself lives in doValidateAll.
                .validationOnInput {
                    if (isValidLinkUrl(urlField.text)) null
                    else ValidationInfo(SpeqaBundle.message("dialog.link.invalidUrl"), urlField)
                }
        }
    }

    // Runs on open and continuously, so OK stays disabled until the URL is valid.
    override fun doValidateAll(): List<ValidationInfo> {
        if (!isValidLinkUrl(urlField.text)) {
            return listOf(ValidationInfo(SpeqaBundle.message("dialog.link.invalidUrl"), urlField))
        }
        return emptyList()
    }

    /** Returns the entered values. */
    fun result(): LinkInput = LinkInput(text = textField.text.trim(), url = urlField.text.trim())

    companion object {
        /**
         * Shows the modal dialog seeded with [initialText]/[initialUrl] and returns the entered
         * [LinkInput], or `null` if the user cancelled. [isEdit] titles the dialog "Edit link" when
         * an existing link is being changed and "Add link" when a new link is being created.
         */
        fun edit(project: Project, initialText: String, initialUrl: String, isEdit: Boolean): LinkInput? {
            val dialog = LinkDialog(project, initialText, initialUrl, isEdit)
            return if (dialog.showAndGet()) dialog.result() else null
        }
    }
}
