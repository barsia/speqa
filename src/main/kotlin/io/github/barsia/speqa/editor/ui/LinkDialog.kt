// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package io.github.barsia.speqa.editor.ui

import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.panel
import io.github.barsia.speqa.SpeqaBundle
import java.awt.datatransfer.DataFlavor
import javax.swing.JComponent

/** A link's display text and target URL, as entered in [LinkDialog]. */
data class LinkInput(val text: String, val url: String)

/**
 * True only for an `http://` or `https://` URL with at least one character after the scheme
 * (a bare `https://` would not match the inline-link render pattern and would leave raw
 * markup in the preview). Pure and top-level so the validation contract can be tested
 * without the IntelliJ platform.
 */
fun isValidLinkUrl(s: String): Boolean {
    val trimmed = s.trim()
    return listOf("http://", "https://").any { scheme ->
        trimmed.startsWith(scheme) && trimmed.length > scheme.length
    }
}

/**
 * Shared modal dialog to enter or edit a link's text and URL. The OK button is gated
 * by [doValidateAll], so it is disabled from the moment the dialog opens (while the text
 * is blank or the URL is empty/invalid) and stays disabled until both are valid.
 *
 * When adding a new link with no URL yet, the URL field is seeded from the clipboard if it
 * holds a valid http(s) URL, selected so typing replaces it. Initial focus goes to the URL
 * field whenever the text field is already filled (created from a selection, or editing).
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
        if (!isEdit && initialUrl.isBlank()) {
            clipboardLinkUrl()?.let {
                urlField.text = it
                urlField.selectAll()
            }
        }
        init()
    }

    private fun clipboardLinkUrl(): String? {
        val clipboard = runCatching {
            CopyPasteManager.getInstance().getContents<String>(DataFlavor.stringFlavor)
        }.getOrNull() ?: return null
        return clipboard.trim().takeIf(::isValidLinkUrl)
    }

    override fun getPreferredFocusedComponent(): JComponent =
        if (textField.text.isNotBlank()) urlField else textField

    override fun createCenterPanel(): JComponent = panel {
        row(SpeqaBundle.message("dialog.link.text")) {
            cell(textField)
                // Live inline feedback while typing; OK gating itself lives in doValidateAll.
                .validationOnInput {
                    if (textField.text.isNotBlank()) null
                    else ValidationInfo(SpeqaBundle.message("dialog.link.textRequired"), textField)
                }
        }
        row(SpeqaBundle.message("dialog.link.url")) {
            cell(urlField)
                .validationOnInput {
                    if (isValidLinkUrl(urlField.text)) null
                    else ValidationInfo(SpeqaBundle.message("dialog.link.invalidUrl"), urlField)
                }
        }
    }

    // Runs on open and continuously, so OK stays disabled until both fields are valid.
    override fun doValidateAll(): List<ValidationInfo> = buildList {
        if (textField.text.isBlank()) {
            add(ValidationInfo(SpeqaBundle.message("dialog.link.textRequired"), textField))
        }
        if (!isValidLinkUrl(urlField.text)) {
            add(ValidationInfo(SpeqaBundle.message("dialog.link.invalidUrl"), urlField))
        }
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
