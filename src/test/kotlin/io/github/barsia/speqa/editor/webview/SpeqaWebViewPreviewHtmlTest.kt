package io.github.barsia.speqa.editor.webview

import org.junit.Assert.assertTrue
import org.junit.Test

class SpeqaWebViewPreviewHtmlTest {
  private val html: String by lazy {
    SpeqaWebViewPreviewSupport.buildInlinedPreviewHtml("light")
  }

  @Test
  fun `styles scrollbars with theme colors`() {
    assertTrue(html.contains("--scrollbar-thumb:"))
    assertTrue(html.contains("--scroll-indicator-thumb:"))
    assertTrue(html.contains("scrollbar-color: var(--scrollbar-thumb) var(--scrollbar-track);"))
    assertTrue(html.contains("::-webkit-scrollbar-thumb"))
    assertTrue(html.contains("html.dark,\nbody.dark {"))
    assertTrue(html.contains("background: var(--bg);\n  scrollbar-width: none;"))
    assertTrue(html.contains("document.documentElement.classList.toggle(\"dark\", state.theme === \"dark\");"))
    assertTrue(!html.contains("speqa/testCase/rendered"))
    assertTrue(html.contains("html::-webkit-scrollbar,\nbody::-webkit-scrollbar"))
    assertTrue(html.contains("<div class=\"scroll-indicator\" id=\"scrollIndicator\" aria-hidden=\"true\">"))
    assertTrue(html.contains("function showScrollIndicator()"))
    assertTrue(html.contains("scrollIndicator.classList.add(\"is-visible\");"))
    assertTrue(html.contains("scrollIndicator.classList.remove(\"is-visible\");"))
    assertTrue(html.contains("if (isDocumentScrollEvent(event)) showScrollIndicator();"))
  }

  @Test
  fun `uses one shared narrow breakpoint for header and step columns`() {
    assertTrue(html.contains("@media (min-width: 321px)"))
    assertTrue(!html.contains("@media (min-width: 561px)"))
    assertTrue(html.contains("@media (max-width: 560px)"))
    assertTrue(html.contains("grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);"))
    assertTrue(html.contains("@media (max-width: 320px)"))
    assertTrue(mediaBlock("max-width: 320px").contains(".form-grid,\n  .two-column-grid {\n    grid-template-columns: 1fr;"))
    assertTrue(!mediaBlock("max-width: 560px").contains(".form-grid,\n  .two-column-grid {\n    grid-template-columns: 1fr;"))
  }

  @Test
  fun `keeps step metadata responsive to step layout`() {
    assertTrue(html.contains("class=\"step-tools-action\""))
    assertTrue(html.contains("class=\"step-tools-expected\""))
    assertTrue(html.contains("class=\"step-tools-section\" data-step-tools-section=\"tickets\""))
    assertTrue(html.contains("class=\"step-tools-section\" data-step-tools-section=\"links\""))
    assertTrue(html.contains("class=\"step-tools-section\" data-step-tools-section=\"attachments\""))
    assertTrue(html.contains(".step-tools-section"))
    assertTrue(html.contains("grid-template-columns: repeat(3, minmax(0, 1fr));"))
    assertTrue(html.contains("grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);"))
    assertTrue(html.contains("grid-template-columns: minmax(0, 1fr);"))
  }

  @Test
  fun `places id and dates above title row`() {
    assertTrue(html.contains("<div class=\"id-dates-row\">"))
    assertTrue(html.contains("<div class=\"title-row\">"))
    assertTrue(html.contains("<span class=\"case-id-prefix\">' + escapeHtml(state.idPrefix || \"TC-\") + '</span>"))
    assertTrue(html.contains("<textarea class=\"title-input\" data-field=\"title\" rows=\"1\""))
    assertTrue(html.contains(".id-dates-row {\n  display: flex;"))
    assertTrue(html.contains(".title-row {\n  display: block;"))
    assertTrue(html.contains(".case-id-prefix {\n  color: var(--muted);\n}"))
    assertTrue(html.contains(".title-input {\n  display: block;\n  width: 100%;\n  min-height: 26px;"))
    assertTrue(html.contains("max-height: 48px;"))
    assertTrue(html.contains("font-size: 17px;"))
    assertTrue(html.contains("font-weight: 400;\n  line-height: 22px;"))
    assertTrue(html.contains(".case-id-input {\n  width: 2.4ch;\n  height: 22px;"))
    assertTrue(html.contains("color: var(--text);\n  font-size: 12px;"))
    assertTrue(html.contains("height: 22px;\n  line-height: 22px;"))
    assertTrue(html.contains("scheduleTextareaResize();"))
    assertTrue(html.contains("el.classList.contains(\"title-input\") ? Math.min(nextHeight, titleMaxHeight()) : nextHeight"))
    assertTrue(html.contains("function titleMaxHeight()"))
    assertTrue(!html.contains("Markdown preview"))
  }

  @Test
  fun `uses anchored custom dropdowns for priority and status`() {
    assertTrue(!html.contains("<select data-field=\"priority\""))
    assertTrue(!html.contains("<select data-field=\"status\""))
    assertTrue(html.contains("choiceDropdown(\"priority\", state.priority)"))
    assertTrue(html.contains("choiceDropdown(\"status\", state.status)"))
    assertTrue(html.contains("class=\"choice-button\""))
    assertTrue(html.contains("role\", \"listbox\""))
    assertTrue(html.contains("role\", \"option\""))
    assertTrue(html.contains("function positionChoiceDropdown(anchor, menu)"))
    assertTrue(html.contains("const openUp = naturalHeight > below && above > below;"))
    assertTrue(html.contains("const preferredTop = openUp ? rect.top - gap - height : rect.bottom + gap;"))
    assertTrue(html.contains("const left = Math.max(margin, Math.min(rect.left, window.innerWidth - width - margin));"))
    assertTrue(html.contains(".choice-chevron {\n  display: inline-flex;\n  align-items: center;\n  justify-content: center;"))
    assertTrue(html.contains("flex: 0 0 16px;\n  width: 16px;\n  height: 100%;"))
    assertTrue(html.contains(".choice-chevron svg {\n  display: block;"))
    assertTrue(html.contains("window.__KWRY__.notify(methods.fieldChanged, { field: field, value: value });"))
    assertTrue(html.contains("if (event.key === \"ArrowDown\""))
    assertTrue(html.contains("if (event.key === \"Escape\")"))
  }

  @Test
  fun `starts markdown editors as single line placeholder fields`() {
    assertTrue(html.contains("markdownEditor(\"description\", 'data-body=\"description\" rows=\"1\" placeholder=\"Type a description\" spellcheck=\"true\"', state.description, \"Type a description\")"))
    assertTrue(html.contains("markdownEditor(\"preconditions\", 'data-body=\"preconditions\" rows=\"1\" placeholder=\"No preconditions\" spellcheck=\"true\"', state.preconditions, \"No preconditions\")"))
    assertTrue(html.contains("markdownEditor(\"step-action\", 'data-step-field=\"action\" data-step-index=\"' + step.index + '\" rows=\"1\" placeholder=\"Describe the action\""))
    assertTrue(html.contains("markdownEditor(\"step-expected\", 'data-step-field=\"expected\" data-step-index=\"' + step.index + '\" rows=\"1\" placeholder=\"Add the expected result\""))
    assertTrue(html.contains("class=\"markdown-editor' + emptyClass + '\" data-markdown-editor=\"' + kind + '\" contenteditable=\"true\""))
    assertTrue(html.contains("data-placeholder=\"' + escapeHtml(placeholder || \"\") + '\""))
    assertTrue(html.contains(".markdown-editor.is-empty {\n  height: 34px;\n  min-height: 34px;\n  overflow: hidden;"))
    assertTrue(html.contains(".markdown-editor.is-empty::before"))
    assertTrue(html.contains("function resizeAllTextareas()"))
    assertTrue(html.contains("function scheduleTextareaResize()"))
    assertTrue(html.contains("window.addEventListener(\"resize\", function()"))
    assertTrue(html.contains("scheduleTextareaResize();\n  updateScrollIndicator();"))
    assertTrue(html.contains("scheduleTextareaResize();"))
  }

  @Test
  fun `renders markdown code inside rich editable fields`() {
    assertTrue(html.contains("--code-bg:"))
    assertTrue(html.contains("--code-block-bg:"))
    assertTrue(html.contains(".markdown-editor code"))
    assertTrue(html.contains(".markdown-code-block"))
    assertTrue(html.contains(".markdown-code-block {\n  position: relative;\n  margin: 6px 0;"))
    assertTrue(html.contains(".markdown-code-language"))
    assertTrue(html.contains(".markdown-code-copy"))
    assertTrue(html.contains(".markdown-code-copy {\n  position: absolute;\n  top: 2px;"))
    assertTrue(html.contains(".markdown-code-copy svg"))
    assertTrue(html.contains(".markdown-code-copy-icon-check {\n  display: none;"))
    assertTrue(html.contains(".markdown-code-copy[data-copied=\"true\"] .markdown-code-copy-icon-copy"))
    assertTrue(html.contains(".markdown-code-copy[data-copied=\"true\"] .markdown-code-copy-icon-check"))
    assertTrue(html.contains("pointer-events: none;\n  cursor: pointer;"))
    assertTrue(html.contains("transition: none;\n  contain: layout paint;"))
    assertTrue(html.contains(".markdown-code-block pre {\n  margin: 0;\n  padding: 8px 10px;\n  overflow-x: auto;"))
    assertTrue(html.contains("padding: 0 34px 0 10px;"))
    assertTrue(html.contains(".markdown-code-content"))
    assertTrue(html.contains(".markdown-inline-code"))
    assertTrue(html.contains("function isCodeFenceLine(line)"))
    assertTrue(html.contains("function renderCodeBlock(lines, language, fenceIndent)"))
    assertTrue(html.contains("function countBackticks(text, start)"))
    assertTrue(html.contains("function nextSingleBacktick(text, start)"))
    assertTrue(html.contains("function renderInlineMarkdown(text)"))
    assertTrue(html.contains("function renderMarkdown(markdown)"))
    assertTrue(html.contains("const fence = isCodeFenceLine(line);"))
    assertTrue(html.contains("html += renderCodeBlock(codeLines, codeLanguage, codeIndent);"))
    assertTrue(html.contains("function markdownEditor(kind, attrs, value, placeholder)"))
    assertTrue(html.contains("data-code-block=\"true\" data-code-language=\"' + escapeHtml(languageName) + '\" data-code-indent=\"' + escapeHtml(indent) + '\""))
    assertTrue(html.contains("data-copy-code=\"true\" data-copied=\"false\" aria-label=\"Copy code block\" data-tooltip=\"Copy code block\""))
    assertTrue(html.contains("class=\"markdown-code-copy-icon markdown-code-copy-icon-copy\""))
    assertTrue(html.contains("class=\"markdown-code-copy-icon markdown-code-copy-icon-check\""))
    assertTrue(html.contains("icon(\"copy\")"))
    assertTrue(html.contains("if (name === \"check\")"))
    assertTrue(html.contains("data-inline-code=\"true\""))
    assertTrue(html.contains("function serializeMarkdownEditor(editor)"))
    assertTrue(html.contains("const indent = element.dataset.codeIndent || \"\";"))
    assertTrue(html.contains("const indentedCode = code.split(\"\\n\").map(function(line) { return indent + line; }).join(\"\\n\");"))
    assertTrue(html.contains("return indent + \"```\" + language + \"\\n\" + indentedCode + \"\\n\" + indent + \"```\";"))
    assertTrue(html.contains("function indentedCodeText(text, indent)"))
    assertTrue(html.contains("function indentedCodeOffset(text, offset, indent)"))
    assertTrue(html.contains("function formatMarkdownEditor(editor, preserveSelection)"))
    assertTrue(html.contains("const CARET_MARKER = \"\\uE000\";"))
    assertTrue(html.contains("function markdownSelectionOffset(root)"))
    assertTrue(html.contains("function renderMarkdownWithCaret(root, raw, offset)"))
    assertTrue(html.contains("function insertMarkdownTextAtSelection(editor, text)"))
    assertTrue(html.contains("function applyMarkdownRawEdit(editor, nextRaw, nextOffset)"))
    assertTrue(html.contains("function continueMarkdownListOnEnter(raw, offset)"))
    assertTrue(html.contains("const match = line.match(/^(\\s*)([-*]|\\d+\\.)\\s(.*)$/);"))
    assertTrue(html.contains("if (!body.trim()) {\n    return {\n      raw: raw.slice(0, bounds.start) + raw.slice(bounds.end),"))
    assertTrue(html.contains("const nextMarker = ordered\n    ? indent + (parseInt(marker, 10) + 1) + \". \"\n    : indent + marker + \" \";"))
    assertTrue(html.contains("const listContinuation = continueMarkdownListOnEnter(raw, safeOffset);"))
    assertTrue(html.contains("applyMarkdownRawEdit(editor, listContinuation.raw, listContinuation.offset);"))
    assertTrue(html.contains("function focusAdjacentTextField(field, direction)"))
    assertTrue(html.contains("function handleMarkdownKeydown(editor, event)"))
    assertTrue(html.contains("if (event.key !== \"Enter\") return;"))
    assertTrue(html.contains("if (!serializeMarkdownEditor(editor).trim()) return;"))
    assertTrue(html.contains("insertMarkdownTextAtSelection(editor, \"\\n\");"))
    assertTrue(html.contains("editor.addEventListener(\"keydown\", function(event)"))
    assertTrue(html.contains("app.querySelectorAll(\"[data-markdown-editor]\").forEach(bindMarkdownEditor);"))
    assertTrue(html.contains("return \"`\" + editableText(element) + \"`\";"))
    assertTrue(html.contains("return indent + \"```\" + language + \"\\n\" + indentedCode + \"\\n\" + indent + \"```\";"))
    assertTrue(html.contains("return parts.join(\"\\n\");"))
    assertTrue(!html.contains("class=\"markdown-source\""))
  }

  @Test
  fun `copies rendered markdown code blocks without moving editor focus`() {
    assertTrue(html.contains("function codeCopyButtonFromTarget(target)"))
    assertTrue(html.contains("element.closest(\"[data-copy-code]\")"))
    assertTrue(html.contains("function codeBlockTextForCopy(button)"))
    assertTrue(html.contains("block.querySelector(\"[data-code-content]\")"))
    assertTrue(html.contains("replaceAll(CARET_MARKER, \"\")"))
    assertTrue(html.contains("codeCopyDiagnostic: \"speqa/testCase/codeCopyDiagnostic\""))
    assertTrue(html.contains("codeBlockCopyRequested: \"speqa/testCase/codeBlockCopyRequested\""))
    assertTrue(html.contains("function logCodeCopySelection(stage, button)"))
    assertTrue(html.contains("window.__KWRY__.notify(methods.codeCopyDiagnostic, params);"))
    assertTrue(html.contains("markdownOffset: editor ? markdownSelectionOffset(editor) : null"))
    assertTrue(html.contains("markdownLength: editor ? serializeMarkdownEditor(editor).length : -1"))
    assertTrue(html.contains("logCodeCopySelection(\"mousedown\", button);"))
    assertTrue(html.contains("logCodeCopySelection(\"click-before\", button);"))
    assertTrue(html.contains("window.__KWRY__.notify(methods.codeBlockCopyRequested, { text: text });"))
    assertTrue(html.contains("logCodeCopySelection(\"copy-native-requested\", button);"))
    assertTrue(html.contains("logCodeCopySelection(\"copy-success-before-feedback\", button);"))
    assertTrue(html.contains("function setCodeCopyButtonState(button, copied)"))
    assertTrue(html.contains("button.setAttribute(\"aria-label\", copied ? \"Code block copied\" : \"Copy code block\");"))
    assertTrue(!html.contains("function restoreCodeCopySelection(button)"))
    assertTrue(!html.contains("requestAnimationFrame(function() {\n    restoreSelectionAfterClipboardFallback(selectionState);"))
    assertTrue(!html.contains("button.innerHTML = copied ? icon(\"check\") : icon(\"copy\");"))
    assertTrue(!html.contains("button.innerHTML = icon(\"copy\");"))
    assertTrue(!html.contains("button.textContent = copied ? \"Copied\" : \"Copy\";"))
    assertTrue(!html.contains("function writeTextToClipboard(text)"))
    assertTrue(!html.contains("function fallbackCopyTextToClipboard(text)"))
    assertTrue(!html.contains("document.execCommand(\"copy\")"))
    assertTrue(html.contains("function handleCodeBlockCopyMouseDown(event)"))
    assertTrue(html.contains("event.preventDefault();\n  event.stopPropagation();"))
    assertTrue(html.contains("function handleCodeBlockCopyClick(event)"))
    assertTrue(html.contains("copyCodeBlockToClipboard(button);"))
    assertTrue(html.contains("document.addEventListener(\"mousedown\", handleCodeBlockCopyMouseDown, true);"))
    assertTrue(html.contains("document.addEventListener(\"click\", handleCodeBlockCopyClick, true);"))
  }

  @Test
  fun `empty scenario renders Add step inside the dashed frame under No steps yet without a period`() {
    assertTrue(html.contains("'<div class=\"empty empty-steps\">'"))
    assertTrue(html.contains("'<div class=\"empty-steps-text\">No steps yet</div>'"))
    assertTrue(html.contains(".empty-steps {"))
    assertTrue(html.contains("flex-direction: column;"))
    assertTrue(html.contains("align-items: center;"))
    assertTrue(!html.contains("No steps yet."))
  }

  @Test
  fun `defers markdown reformatting while the preview editor has native focus`() {
    assertTrue(html.contains("function caretCodeBlockIndex(editor)"))
    assertTrue(html.contains("function installCaretCodeBlockSync()"))
    assertTrue(html.contains("document.addEventListener(\"selectionchange\", function()"))
    assertTrue(html.contains("editor.dataset.caretCodeBlock"))
    assertTrue(html.contains("if (editor.dataset.composing === \"true\") return;"))
    assertTrue(html.contains("if (current === stored) return;"))
    assertTrue(!html.contains("formatMarkdownEditor(editor, true);"))
    assertTrue(html.contains("editor.addEventListener(\"input\", function(event)"))
    assertTrue(html.contains("updateMarkdownEmptyState(editor);\n    editor.dataset.caretCodeBlock = String(caretCodeBlockIndex(editor));"))
    assertTrue(html.contains("editor.addEventListener(\"blur\", function()"))
    assertTrue(html.contains("if (!composing) formatMarkdownEditor(editor, false);"))
    assertTrue(html.contains("installCaretCodeBlockSync();"))
  }

  @Test
  fun `handles preview editor undo redo before WebKit moves caret`() {
    assertTrue(html.contains("const markdownHistoryByField = new Map();"))
    assertTrue(html.contains("function markdownHistoryState(editor)"))
    assertTrue(html.contains("markdownHistoryByField.set(key, history);"))
    assertTrue(html.contains("function markdownHistoryKey(editor)"))
    assertTrue(html.contains("function pushMarkdownHistoryEntry(editor, before, after)"))
    assertTrue(html.contains("function rememberMarkdownEditBeforeInput(editor, event)"))
    assertTrue(html.contains("function recordMarkdownEditAfterInput(editor)"))
    assertTrue(html.contains("function recordMarkdownProgrammaticEdit(editor, before)"))
    assertTrue(html.contains("function synchronizeMarkdownHistory(editor)"))
    assertTrue(html.contains("history.currentRaw = after.raw;"))
    assertTrue(html.contains("synchronizeMarkdownHistory(editor);"))
    assertTrue(html.contains("function handleMarkdownUndoRedoKeydown(editor, event)"))
    assertTrue(html.contains("event.preventDefault();\n  event.stopPropagation();"))
    assertTrue(html.contains("const source = redo ? history.redo : history.undo;"))
    assertTrue(html.contains("const raw = redo ? entry.afterRaw : entry.beforeRaw;"))
    assertTrue(html.contains("const offset = redo ? entry.afterOffset : entry.beforeOffset;"))
    assertTrue(html.contains("applyMarkdownRawEdit(editor, raw, offset);"))
    assertTrue(html.contains("if (handleMarkdownUndoRedoKeydown(editor, event)) return;"))
    assertTrue(html.contains("rememberMarkdownEditBeforeInput(editor, event);"))
    assertTrue(html.contains("recordMarkdownEditAfterInput(editor);"))
  }

  @Test
  fun `pastes preview clipboard text exactly without WebKit smart spaces`() {
    assertTrue(html.contains("previewPasteRequested: \"speqa/testCase/previewPasteRequested\""))
    assertTrue(html.contains("pastePreviewText: \"speqa/testCase/pastePreviewText\""))
    assertTrue(html.contains("function handlePreviewTextPaste(event)"))
    assertTrue(html.contains("event.clipboardData.getData(\"text/plain\")"))
    assertTrue(html.contains("event.preventDefault();\n  notifyPreviewTextFocus(true);"))
    assertTrue(html.contains("insertPlainTextAtPreviewSelection(field, text);"))
    assertTrue(html.contains("pendingPreviewPasteTarget = capturePreviewTextFocus();"))
    assertTrue(html.contains("window.__KWRY__.notify(methods.previewPasteRequested);"))
    assertTrue(html.contains("function pastePreviewText(params)"))
    assertTrue(html.contains("restorePreviewTextFocus(target);"))
    assertTrue(html.contains("function insertPlainTextAtPreviewSelection(field, text)"))
    assertTrue(html.contains("document.execCommand(\"insertText\", false, text)"))
    assertTrue(html.contains("const before = markdownHistorySnapshot(field);\n    if (execCommandInsertText(text))"))
    assertTrue(html.contains("recordMarkdownProgrammaticEdit(field, before);"))
    assertTrue(html.contains("field.setRangeText(text, start, end, \"end\");"))
    assertTrue(html.contains("window.__KWRY__.subscribe(methods.pastePreviewText, pastePreviewText);"))
    assertTrue(html.contains("document.addEventListener(\"paste\", handlePreviewTextPaste, true);"))
  }

  @Test
  fun `highlights fenced code blocks via bundled hljs but skips the focused block`() {
    // highlight.min.js is inlined into the HTML blob by buildInlinedPreviewHtml, so the
    // external <script src> tag is gone; the bundled hljs runtime is detectable via its
    // global registration call inside the inlined body.
    assertTrue(!html.contains("<script src=\"highlight.min.js\"></script>"))
    assertTrue(html.contains("Highlight.js v"))
    assertTrue(html.contains("function highlightCode(code, language)"))
    assertTrue(html.contains("hljs.highlight(code, { language: language, ignoreIllegals: true }).value"))
    assertTrue(html.contains("const hasCaret = code.indexOf(CARET_MARKER) >= 0;"))
    assertTrue(html.contains("hasCaret ? escapeHtml(code) : highlightCode(code, language)"))
    assertTrue(html.contains("class=\"markdown-code-content hljs\""))
    assertTrue(html.contains(".markdown-code-content .hljs-keyword"))
    assertTrue(html.contains(".markdown-code-content .hljs-string"))
    assertTrue(html.contains(".markdown-code-content .hljs-comment"))
    assertTrue(html.contains("--hljs-keyword:"))
  }

  @Test
  fun `slightly speeds up native caret blink where supported`() {
    assertTrue(html.contains("--caret-blink-duration: .68s;"))
    assertTrue(!html.contains("caret-color: var(--accent);"))
    assertTrue(html.contains("@keyframes speqa-caret-blink"))
    assertTrue(html.contains("caret-color: currentColor;"))
    assertTrue(html.contains("@supports (caret-animation: manual)"))
    assertTrue(html.contains("caret-animation: manual;"))
    assertTrue(html.contains("animation: speqa-caret-blink var(--caret-blink-duration) steps(1, end) infinite;"))
  }

  @Test
  fun `moves title focus forward on enter`() {
    assertTrue(html.contains("if (el.dataset.field === \"title\") {"))
    assertTrue(html.contains("if (event.key === \"Enter\") {\n        event.preventDefault();\n        focusAdjacentTextField(el, 1);"))
  }

  @Test
  fun `reports preview text focus so the editor caret can be suppressed`() {
    assertTrue(html.contains("previewTextFocusChanged: \"speqa/testCase/previewTextFocusChanged\""))
    assertTrue(html.contains("let previewTextFocusActive = false;"))
    assertTrue(html.contains("function isPreviewTextInput(element)"))
    assertTrue(html.contains("element instanceof HTMLInputElement ||"))
    assertTrue(html.contains("element instanceof HTMLTextAreaElement ||"))
    assertTrue(html.contains("element.closest(\"[data-markdown-editor]\")"))
    assertTrue(html.contains("function notifyPreviewTextFocus(active)"))
    assertTrue(html.contains("window.__KWRY__.notify(methods.previewTextFocusChanged, { active: active });"))
    assertTrue(html.contains("document.addEventListener(\"focusin\", updatePreviewTextFocus);"))
    assertTrue(html.contains("document.addEventListener(\"focusout\", function()"))
    assertTrue(html.contains("window.addEventListener(\"blur\", function()"))
  }

  @Test
  fun `forwards control editing shortcuts from focused preview text fields`() {
    assertTrue(html.contains("nativeTextEditingCommand: \"speqa/testCase/nativeTextEditingCommand\""))
    assertTrue(html.contains("function nativeTextEditingCommandForEvent(event)"))
    assertTrue(html.contains("if (!event.ctrlKey || event.metaKey || event.altKey || event.shiftKey) return null;"))
    assertTrue(html.contains("if (!isPreviewTextInput(target)) return null;"))
    assertTrue(html.contains("if (key === \"c\") return \"copy\";"))
    assertTrue(html.contains("if (key === \"v\") return \"paste\";"))
    assertTrue(html.contains("if (key === \"x\") return \"cut\";"))
    assertTrue(html.contains("event.preventDefault();"))
    assertTrue(html.contains("window.__KWRY__.notify(methods.nativeTextEditingCommand, { command: command });"))
    assertTrue(html.contains("document.addEventListener(\"keydown\", forwardNativeTextEditingShortcut, true);"))
  }

  @Test
  fun `normalizes WebKit double click word copy from preview text fields`() {
    assertTrue(html.contains("normalizedPreviewCopy: \"speqa/testCase/normalizedPreviewCopy\""))
    assertTrue(html.contains("function previewTextFieldForClipboard(eventTarget)"))
    assertTrue(html.contains("function selectedPreviewText(field)"))
    assertTrue(html.contains("function normalizeCopiedPreviewText(text)"))
    assertTrue(html.contains("const trimmed = value.replace(/[ \\t\\u00a0]$/, \"\");"))
    assertTrue(html.contains("if (!trimmed || /\\s/.test(trimmed)) return value;"))
    assertTrue(html.contains("event.clipboardData.setData(\"text/plain\", normalized);"))
    assertTrue(html.contains("window.__KWRY__.notify(methods.normalizedPreviewCopy, { text: normalized });"))
    assertTrue(html.contains("if (normalized === text) return;"))
    assertTrue(html.contains("document.addEventListener(\"copy\", handlePreviewTextCopy, true);"))
  }

  @Test
  fun `places add step action after the steps list`() {
    assertTrue(html.contains("<div class=\"step-add-row\"><button class=\"add-button\" type=\"button\" data-add-step>"))
    assertTrue(html.contains(".step:last-child {\n  padding-bottom: 6px;"))
    assertTrue(html.contains(".steps + .step-add-row {\n  margin-top: 4px;"))
    assertTrue(html.contains(".add-button svg,\n.section-action svg {\n  width: 13px;\n  height: 13px;"))
    assertTrue(html.contains("stroke-width: 1.6;"))
  }

  @Test
  fun `preserves scroll and focuses the new step action after add step`() {
    assertTrue(html.contains("let pendingFocusStepIndex = null;"))
    assertTrue(html.contains("let pendingScrollY = null;"))
    assertTrue(html.contains("function requestAddStep()"))
    assertTrue(html.contains("pendingFocusStepIndex = (state.steps || []).length;"))
    assertTrue(html.contains("pendingScrollY = window.scrollY;"))
    assertTrue(html.contains("function restorePendingAddStepFocus()"))
    assertTrue(html.contains("const scrollY = pendingScrollY;"))
    assertTrue(html.contains("window.scrollTo(0, scrollY);"))
    assertTrue(html.contains("const editor = app.querySelector('[data-markdown-editor][data-step-field=\"action\"][data-step-index=\"' + stepIndex + '\"]');"))
    assertTrue(html.contains("focusMarkdownEditor(editor);"))
    assertTrue(html.contains("textarea.focus({ preventScroll: true });"))
    assertTrue(html.contains("textarea.setSelectionRange(textarea.value.length, textarea.value.length);"))
    assertTrue(html.contains("addButton.addEventListener(\"click\", requestAddStep);"))
  }

  @Test
  fun `preserves preview scroll across document snapshot rerenders`() {
    assertTrue(html.contains("const shouldRestoreScroll = pendingFocusStepIndex === null;"))
    assertTrue(html.contains("const renderScrollY = shouldRestoreScroll ? window.scrollY : null;"))
    assertTrue(html.contains("if (renderScrollY !== null) restoreRenderScroll(renderScrollY);"))
    assertTrue(html.contains("function maxDocumentScrollY()"))
    assertTrue(html.contains("document.documentElement.scrollHeight - window.innerHeight"))
    assertTrue(html.contains("function restoreRenderScroll(scrollY)"))
    assertTrue(html.contains("const target = Math.min(scrollY, maxDocumentScrollY());"))
    assertTrue(html.contains("window.scrollTo(0, Math.min(target, maxDocumentScrollY()));"))
  }

  @Test
  fun `preserves focused preview text field across document snapshot rerenders`() {
    assertTrue(html.contains("const shouldRestorePreviewTextFocus = !next || next.restorePreviewTextFocus !== false;"))
    assertTrue(html.contains("const focusedTextField = shouldRestoreScroll && shouldRestorePreviewTextFocus ? capturePreviewTextFocus() : null;"))
    assertTrue(html.contains("if (focusedTextField) restorePreviewTextFocus(focusedTextField);"))
    assertTrue(html.contains("else if (!shouldRestorePreviewTextFocus) notifyPreviewTextFocus(false);"))
    assertTrue(html.contains("function fieldIdentitySelector(field)"))
    assertTrue(html.contains("function capturePreviewTextFocus()"))
    assertTrue(html.contains("function restorePreviewTextFocus(snapshot)"))
    assertTrue(html.contains("field.focus({ preventScroll: true });"))
    assertTrue(html.contains("field.setSelectionRange(start, end, selection.direction || \"none\");"))
    assertTrue(html.contains("renderMarkdownWithCaret(field, raw, offset);"))
    assertTrue(html.contains("notifyPreviewTextFocus(true);"))
  }

  @Test
  fun `supports bidirectional preview scroll sync`() {
    assertTrue(html.contains("previewScrolled: \"speqa/testCase/previewScrolled\""))
    assertTrue(html.contains("scrollToFraction: \"speqa/testCase/scrollToFraction\""))
    assertTrue(html.contains("const SCROLL_SYNC_SUPPRESS_MS = 220;"))
    assertTrue(html.contains("let previewScrollFrame = 0;"))
    assertTrue(html.contains("let suppressPreviewScrollUntil = 0;"))
    assertTrue(html.contains("function previewScrollFraction()"))
    assertTrue(html.contains("window.__KWRY__.notify(methods.previewScrolled, { fraction: previewScrollFraction() });"))
    assertTrue(html.contains("function scrollToFraction(params)"))
    assertTrue(html.contains("window.scrollTo(0, Math.round(clamped * maxDocumentScrollY()));"))
    assertTrue(html.contains("window.addEventListener(\"scroll\", function(event)"))
    assertTrue(html.contains("schedulePreviewScrolled(event);"))
    assertTrue(html.contains("window.__KWRY__.subscribe(methods.scrollToFraction, scrollToFraction);"))
  }

  @Test
  fun `uses muted fixed-size standalone icons for metadata links`() {
    assertTrue(html.contains("class=\"link-icon\" aria-hidden=\"true\""))
    assertTrue(html.contains("flex: 0 0 13px;"))
    assertTrue(html.contains("width: 13px;\n  height: 13px;"))
    assertTrue(html.contains(".resource-link,\n.step-tool {\n  display: inline-flex;\n  align-items: center;"))
    assertTrue(html.contains("line-height: 1.35;"))
    assertTrue(html.contains("height: 13px;\n  transform: translateY(-1px);\n  color: currentColor;"))
    assertTrue(html.contains("M9 3.5h3.5V7M12.5 3.5 7.75 8.25"))
    assertTrue(html.contains("A1.75 1.75 0 0 1 4.75 3h6.5"))
    assertTrue(html.contains("m5.2 8.75 4.05-4.05"))
    assertTrue(html.contains("data-open-link"))
    assertTrue(html.contains("openLink: \"speqa/testCase/openLink\""))
  }

  @Test
  fun `shows full value overflow tooltips instead of generic saved item titles`() {
    assertTrue(html.contains("class=\"overflow-tooltip\" id=\"overflowTooltip\" role=\"tooltip\" aria-hidden=\"true\""))
    assertTrue(html.contains(".overflow-tooltip {\n  position: fixed;"))
    assertTrue(html.contains("border-radius: 8px;"))
    assertTrue(html.contains("function overflowTooltipAttr(value)"))
    assertTrue(html.contains("data-overflow-tooltip=\"' + escapeHtml(value) + '\""))
    assertTrue(html.contains("function isOverflowTooltipNeeded(target)"))
    assertTrue(html.contains("target.scrollWidth > target.clientWidth + 1"))
    assertTrue(html.contains("function positionOverflowTooltip(target)"))
    assertTrue(html.contains("overflowTooltip.textContent = text;"))
    assertTrue(html.contains("document.addEventListener(\"pointerover\", handleOverflowTooltipPointerOver, true);"))
    assertTrue(html.contains("document.addEventListener(\"pointerout\", handleOverflowTooltipPointerOut, true);"))
    assertTrue(html.contains("document.addEventListener(\"focusin\", handleOverflowTooltipFocusIn, true);"))
    assertTrue(html.contains("document.addEventListener(\"focusout\", handleOverflowTooltipFocusOut, true);"))
    assertTrue(html.contains("hideOverflowTooltip();\n  const shouldRestoreScroll = pendingFocusStepIndex === null;"))
    assertTrue(html.contains("'<span' + overflowTooltipAttr(title) + '>' + escapeHtml(title) + '</span></a>'"))
    assertTrue(html.contains("'<span' + overflowTooltipAttr(ticket) + '>' + escapeHtml(ticket) + '</span></button>'"))
    assertTrue(html.contains("'<span' + overflowTooltipAttr(title) + '>' + escapeHtml(title) + '</span></button>'"))
    assertTrue(html.contains("labelEl.dataset.overflowTooltip = label;"))
    assertTrue(!html.contains("title=\"Open ticket. Right click to edit or delete.\""))
    assertTrue(!html.contains("title=\"Open link. Right click to edit or delete.\""))
    assertTrue(!html.contains("title=\"Open attachment. Right click to edit or delete.\""))
  }

  @Test
  fun `shows add actions for links and attachments`() {
    assertTrue(html.contains("data-add-link"))
    assertTrue(html.contains("data-add-attachment"))
    assertTrue(html.contains("addLink: \"speqa/testCase/addLink\""))
    assertTrue(html.contains("addAttachment: \"speqa/testCase/addAttachment\""))
  }

  @Test
  fun `uses hover revealed edit delete actions for header links and attachments`() {
    assertTrue(html.contains("class=\"metadata-action-row\""))
    assertTrue(html.contains("class=\"resource-link\""))
    assertTrue(html.contains("class=\"item-actions\""))
    assertTrue(html.contains(".link-list li,\n.attachment-list li {\n  min-width: 0;\n  width: 100%;"))
    assertTrue(html.contains(".metadata-action-row {\n  position: relative;\n  display: grid;\n  grid-template-columns: minmax(0, 1fr);\n  align-items: start;\n  gap: 3px;\n  width: 100%;"))
    assertTrue(html.contains("padding-right: var(--resource-actions-width);"))
    assertTrue(html.contains("overflow: hidden;"))
    assertTrue(html.contains("function itemActions(kind, itemIndex, label)"))
    assertTrue(html.contains("data-edit-link"))
    assertTrue(html.contains("data-delete-link"))
    assertTrue(html.contains("data-edit-attachment"))
    assertTrue(html.contains("data-delete-attachment"))
    assertTrue(html.contains("editLink: \"speqa/testCase/editLink\""))
    assertTrue(html.contains("deleteLink: \"speqa/testCase/deleteLink\""))
    assertTrue(html.contains("editAttachment: \"speqa/testCase/editAttachment\""))
    assertTrue(html.contains("deleteAttachment: \"speqa/testCase/deleteAttachment\""))
    assertTrue(html.contains(".metadata-action-row:hover .item-actions"))
    assertTrue(html.contains(".metadata-action-row:focus-within .item-actions"))
  }

  @Test
  fun `opens saved tickets links and attachments through IDE bridge`() {
    assertTrue(html.contains("data-open-ticket"))
    assertTrue(html.contains("data-open-link"))
    assertTrue(html.contains("data-open-attachment"))
    assertTrue(html.contains("openTicket: \"speqa/testCase/openTicket\""))
    assertTrue(html.contains("openAttachment: \"speqa/testCase/openAttachment\""))
  }

  @Test
  fun `uses bridge actions for step metadata controls`() {
    assertTrue(html.contains("data-add-step-ticket"))
    assertTrue(html.contains("data-add-step-link"))
    assertTrue(html.contains("data-add-step-attachment"))
    assertTrue(html.contains("data-step-ticket-index"))
    assertTrue(html.contains("data-step-link-index"))
    assertTrue(html.contains("data-step-attachment-index"))
    assertTrue(html.contains("class=\"step-tool-row is-saved\""))
    assertTrue(html.contains("class=\"step-tool-actions\""))
    assertTrue(html.contains("data-edit-step-ticket"))
    assertTrue(html.contains("data-delete-step-ticket"))
    assertTrue(html.contains("data-edit-step-link"))
    assertTrue(html.contains("data-delete-step-link"))
    assertTrue(html.contains("data-edit-step-attachment"))
    assertTrue(html.contains("data-delete-step-attachment"))
    assertTrue(html.contains("addStepTicket: \"speqa/testCase/addStepTicket\""))
    assertTrue(html.contains("addStepLink: \"speqa/testCase/addStepLink\""))
    assertTrue(html.contains("addStepAttachment: \"speqa/testCase/addStepAttachment\""))
    assertTrue(html.contains("editStepTicket: \"speqa/testCase/editStepTicket\""))
    assertTrue(html.contains("deleteStepTicket: \"speqa/testCase/deleteStepTicket\""))
    assertTrue(html.contains("editStepLink: \"speqa/testCase/editStepLink\""))
    assertTrue(html.contains("deleteStepLink: \"speqa/testCase/deleteStepLink\""))
    assertTrue(html.contains("editStepAttachment: \"speqa/testCase/editStepAttachment\""))
    assertTrue(html.contains("deleteStepAttachment: \"speqa/testCase/deleteStepAttachment\""))
  }

  @Test
  fun `uses pencil and trash buttons for saved step metadata`() {
    assertTrue(html.contains("function stepItemActions(kind, stepIndex, itemIndex, label)"))
    assertTrue(html.contains("icon(\"edit\")"))
    assertTrue(html.contains("icon(\"trash\")"))
    assertTrue(html.contains("data-item-index"))
    assertTrue(html.contains("class=\"step-tool-action is-edit\""))
    assertTrue(html.contains(".step-tool-action svg {\n  display: block;\n  width: 13px;\n  height: 13px;"))
    assertTrue(html.contains(".step-tool-action.is-edit svg {\n  transform: translateY(.5px);"))
    assertTrue(html.contains("opacity: 0;"))
    assertTrue(html.contains("pointer-events: none;"))
    assertTrue(html.contains(".step-tool-row:hover .step-tool-actions"))
    assertTrue(html.contains(".step-tool-row:focus-within .step-tool-actions"))
    assertTrue(html.contains(".step-tool-action.danger:hover"))
  }

  @Test
  fun `aligns saved resource action icons with the leading link icon`() {
    assertTrue(html.contains("--resource-action-size: 19px;"))
    assertTrue(html.contains("--resource-action-gap: 1px;"))
    assertTrue(html.contains("--resource-actions-width: 39px;"))
    assertTrue(html.contains(".step-main,\n.step-tools,\n.step-tools-action,\n.step-tools-expected {\n  min-width: 0;"))
    assertTrue(html.contains(".step-tool-row {\n  position: relative;\n  display: grid;\n  grid-template-columns: minmax(0, 1fr);\n  align-items: start;"))
    assertTrue(html.contains(".item-actions,\n.step-tool-actions {\n  position: absolute;\n  top: -1px;\n  right: 0;"))
    assertTrue(html.contains("display: block;\n  width: var(--resource-actions-width);\n  height: var(--resource-action-size);"))
    assertTrue(html.contains("transition: none;"))
    assertTrue(html.contains("contain: layout paint;"))
    assertTrue(html.contains("width: var(--resource-actions-width);"))
    assertTrue(html.contains(".step-tool-action.is-edit {\n  left: 0;"))
    assertTrue(html.contains(".step-tool-action.danger {\n  left: calc(var(--resource-action-size) + var(--resource-action-gap));"))
    assertTrue(html.contains("flex: 0 0 var(--resource-action-size);"))
    assertTrue(html.contains("appearance: none;"))
  }

  @Test
  fun `moves step delete action into drag handle context menu`() {
    assertTrue(html.contains("data-step-menu"))
    assertTrue(html.contains("Delete step"))
    assertTrue(!html.contains("data-step-tool=\"delete\""))
  }

  @Test
  fun `keeps step metadata actions at normal text weight`() {
    assertTrue(html.contains(".step-tools {\n  display: grid;"))
    assertTrue(html.contains("font-weight: 400;"))
  }

  @Test
  fun `uses the same font size for header and step metadata links`() {
    assertTrue(html.contains("--meta-link-font-size: 13px;"))
    assertTrue(html.contains("font-size: var(--meta-link-font-size);"))
    assertTrue(html.contains("--meta-link-font-size: 12px;"))
    assertTrue(!html.contains(".step-tools {\n    align-items: flex-start;\n    font-size: 11px;"))
  }

  @Test
  fun `renders environment and tags as editable chip clouds`() {
    assertTrue(html.contains("metadataCloud(state.environment, \"environment\", \"environment\")"))
    assertTrue(html.contains("metadataCloud(state.tags, \"tags\", \"tag\")"))
    assertTrue(html.contains("data-add-metadata=\"environment\""))
    assertTrue(html.contains("data-add-metadata=\"tags\""))
    assertTrue(html.contains("className = \"metadata-popover\""))
    assertTrue(html.contains("showMetadataPopover(button)"))
    assertTrue(html.contains("window.__KWRY__.notify(methods.addMetadata, { field: field, value: value })"))
    assertTrue(html.contains("width: min(196px, calc(100vw - 12px));"))
    assertTrue(html.contains(".metadata-popover input:focus {\n  border-color: color-mix(in srgb, var(--accent) 55%, var(--border));\n  box-shadow: none;"))
    assertTrue(html.contains("data-edit-metadata"))
    assertTrue(html.contains("data-delete-metadata"))
    assertTrue(html.contains("dismissMetadataMatches: \"speqa/testCase/dismissMetadataMatches\""))
    assertTrue(html.contains("event.stopPropagation();\n      const rect = button.getBoundingClientRect();"))
    assertTrue(html.contains("function dismissNativeMetadataMatches()"))
    assertTrue(html.contains("dismissNativeMetadataMatches();"))
    assertTrue(!html.contains("<input data-list=\"environment\""))
    assertTrue(!html.contains("<input data-list=\"tags\""))
  }

  @Test
  fun `does not draw duplicate preview edge inside webview`() {
    assertTrue(!html.contains("--preview-edge"))
    assertTrue(!html.contains("preview-edge-shadow"))
    assertTrue(!html.contains(".shell::before"))
    assertTrue(!html.contains("border-left: 1px solid var(--preview-edge)"))
    assertTrue(!html.contains("margin-left: 5px"))
  }

  @Test
  fun `keeps run button footprint while using lighter icon stroke`() {
    assertTrue(html.contains(".run-button {\n  width: 30px;\n  height: 30px;"))
    assertTrue(html.contains("stroke-width=\"1.5\""))
  }

  @Test
  fun `renders text url links inline with span and data-link-url`() {
    assertTrue(html.contains(".markdown-inline-link {"))
    assertTrue(html.contains("if (ch === \"[\") {"))
    assertTrue(html.contains("data-inline-link=\"true\""))
    assertTrue(html.contains("data-link-url=\"' + escapeHtml(url) + '\""))
    assertTrue(html.contains("if (element.matches(\"[data-inline-link]\"))"))
    assertTrue(html.contains("linkText.indexOf(\"[\") < 0"))
  }

  @Test
  fun `supports alt inline delimiters _italic_, __bold__ and single tilde strike`() {
      assertTrue(html.contains("if (ch === \"_\" && source[i + 1] === \"_\") {"))
      assertTrue(html.contains("if (ch === \"_\" && source[i + 1] !== \"_\") {"))
      assertTrue(html.contains("if (ch === \"~\" && source[i + 1] !== \"~\") {"))
      assertTrue(html.contains("data-inline-delim=\"__\""))
      assertTrue(html.contains("data-inline-delim=\"_\""))
      assertTrue(html.contains("data-inline-delim=\"~\""))
      assertTrue(html.contains("element.dataset.inlineDelim || \"**\""))
      assertTrue(html.contains("element.dataset.inlineDelim || \"*\""))
      assertTrue(html.contains("element.dataset.inlineDelim || \"~~\""))
  }

  @Test
  fun `wraps selection with markdown link when pasted text is a url`() {
      assertTrue(html.contains("function maybeWrapPastedUrl(field, pasted) {"))
      assertTrue(html.contains("/^https?:\\/\\/\\S+$/"))
      assertTrue(html.contains("\"[\" + selectionText + \"](\" + url + \")\""))
      assertTrue(html.contains("const insertText = maybeWrapPastedUrl(field, text) || text;"))
  }

  @Test
  fun `defines markdown-popover css base and modifiers`() {
      assertTrue(html.contains(".markdown-popover {"))
      assertTrue(html.contains(".markdown-popover--selection {"))
      assertTrue(html.contains(".markdown-popover--link-row {"))
      assertTrue(html.contains(".markdown-popover--link-form {"))
      assertTrue(html.contains(".markdown-popover-button {"))
      assertTrue(html.contains(".markdown-popover-button.is-active {"))
      assertTrue(html.contains(".markdown-popover-input {"))
      assertTrue(html.contains(".markdown-popover-input.is-invalid {"))
  }

  @Test
  fun `defines createMarkdownPopoverRoot helper`() {
      assertTrue(html.contains("function createMarkdownPopoverRoot(modifier) {"))
      assertTrue(html.contains("document.body.appendChild(root);"))
  }

  @Test
  fun `defines inline format DOM helpers with history snapshot integration`() {
      assertTrue(html.contains("function inlineFormatSpec(format) {"))
      assertTrue(html.contains("function applyInlineFormat(field, range, format) {"))
      assertTrue(html.contains("function removeInlineFormat(field, range, format) {"))
      assertTrue(html.contains("function mergeAdjacent(field) {"))
      assertTrue(html.contains("if (format === \"bold\") return { tag: \"strong\", attr: \"data-inline-bold\" };"))
      assertTrue(html.contains("if (format === \"italic\") return { tag: \"em\", attr: \"data-inline-italic\" };"))
      assertTrue(html.contains("if (format === \"strike\") return { tag: \"s\", attr: \"data-inline-strike\" };"))
      assertTrue(html.contains("if (format === \"code\") return { tag: \"code\", attr: \"data-inline-code\" };"))
      assertTrue(html.contains("wrapper.setAttribute(spec.attr, \"true\");"))
      assertTrue(html.contains("field.querySelectorAll(\"[data-inline-bold], [data-inline-italic], [data-inline-strike], [data-inline-code]\")"))
  }

  @Test
  fun `defines link DOM helpers for create edit and remove`() {
      assertTrue(html.contains("function applyLink(field, range, url) {"))
      assertTrue(html.contains("function updateLinkSpan(field, span, newText, newUrl) {"))
      assertTrue(html.contains("function removeLinkSpan(field, span) {"))
      assertTrue(html.contains("span.className = \"markdown-inline-link\";"))
      assertTrue(html.contains("span.setAttribute(\"data-inline-link\", \"true\");"))
      assertTrue(html.contains("span.dataset.linkUrl = url;"))
  }

  @Test
  fun `defines bindLinkPopover with hover row and edit form modes`() {
      assertTrue(html.contains("function bindLinkPopover() {"))
      assertTrue(html.contains("createMarkdownPopoverRoot(\"link-row\")"))
      assertTrue(html.contains("createMarkdownPopoverRoot(\"link-form\")"))
      assertTrue(html.contains("document.body.addEventListener(\"mouseover\","))
      assertTrue(html.contains("document.body.addEventListener(\"mouseout\","))
      assertTrue(html.contains("function showFormForRange(field, range) {"))
      assertTrue(html.contains("function showFormForLink(field, span) {"))
      assertTrue(html.contains("function showHoverRow(field, span) {"))
      assertTrue(html.contains("function hideLinkPopover()"))
      assertTrue(html.contains("formRoot.setAttribute(\"aria-label\", \"Edit link\");"))
      assertTrue(html.contains("notify(methods.openLink, { url: state.url })"))
      assertTrue(html.contains("if (!/^https?:\\/\\/\\S+$/.test(url)) {"))
  }

  @Test
  fun `defines bindSelectionToolbar with toggle buttons and active detection`() {
      assertTrue(html.contains("function bindSelectionToolbar(linkPopover) {"))
      assertTrue(html.contains("createMarkdownPopoverRoot(\"selection\")"))
      assertTrue(html.contains("function selectionInsideEditor(selection) {"))
      assertTrue(html.contains("function detectActiveFormats(range, field) {"))
      assertTrue(html.contains("root.setAttribute(\"role\", \"toolbar\");"))
      assertTrue(html.contains("root.setAttribute(\"aria-label\", \"Format selection\");"))
      assertTrue(html.contains("bold: \"Bold\","))
      assertTrue(html.contains("italic: \"Italic\","))
      assertTrue(html.contains("strike: \"Strikethrough\","))
      assertTrue(html.contains("code: \"Code\","))
      assertTrue(html.contains("rafToken = requestAnimationFrame(function() {"))
      assertTrue(html.contains("linkPopover.showFormForRange(current.field, current.range);"))
      assertTrue(html.contains("btn.addEventListener(\"mousedown\", function(e) { e.preventDefault(); });"))
  }

  @Test
  fun `wires markdown popovers into one-time init and lifecycle`() {
      assertTrue(html.contains("const speqaLinkPopover = bindLinkPopover();"))
      assertTrue(html.contains("const speqaSelectionToolbar = bindSelectionToolbar(speqaLinkPopover);"))
      assertTrue(html.contains("function hideAllMarkdownPopovers() {"))
      assertTrue(html.contains("speqaLinkPopover.hide();"))
      assertTrue(html.contains("speqaSelectionToolbar.hide();"))
      assertTrue(html.contains("window.addEventListener(\"scroll\", hideAllMarkdownPopovers, true);"))
      assertTrue(html.contains("window.addEventListener(\"resize\", hideAllMarkdownPopovers);"))
      assertTrue(html.contains("if (event.key === \"Escape\") hideAllMarkdownPopovers();"))
  }

  @Test
  fun `icon defines toolbar svg entries for bold italic strike bulleted numbered code codeBlock link`() {
      assertTrue(html.contains("if (name === \"bold\") {"))
      assertTrue(html.contains("if (name === \"italic\") {"))
      assertTrue(html.contains("if (name === \"strike\") {"))
      assertTrue(html.contains("if (name === \"bulleted\") {"))
      assertTrue(html.contains("if (name === \"numbered\") {"))
      assertTrue(html.contains("if (name === \"code\") {"))
      assertTrue(html.contains("if (name === \"codeBlock\") {"))
      assertTrue(html.contains("if (name === \"link\") {"))
      assertTrue(html.contains("M4.5 3.5h4.5a2.25 2.25 0 0 1 0 4.5H4.5z"))
      assertTrue(html.contains("M9.5 3.5l-3 9"))
      assertTrue(html.contains("d=\"M2.5 8h11\""))
      assertTrue(html.contains("cy=\"4\" r=\"1\" fill=\"currentColor\""))
      assertTrue(html.contains("M5.5 4.5L2.5 8l3 3.5M10.5 4.5L13.5 8l-3 3.5"))
      assertTrue(html.contains("<rect x=\"1.5\" y=\"2.5\" width=\"13\" height=\"11\" rx=\"1.5\""))
      assertTrue(html.contains("M5.5 6.5L4 8a2.5 2.5 0 0 0 3.5 3.5L9 10"))
  }

  @Test
  fun `defines block-level toggle helpers and line marker regexes`() {
      assertTrue(html.contains("function rangeRawOffsets(field, range) {"))
      assertTrue(html.contains("function selectedLineSlice(raw, start, end) {"))
      assertTrue(html.contains("const BULLETED_LINE_RE = /^(\\s*)[-*]\\s/;"))
      assertTrue(html.contains("const NUMBERED_LINE_RE = /^(\\s*)\\d+\\.\\s/;"))
      assertTrue(html.contains("const ANY_LIST_MARKER_RE = /^(\\s*)([-*]|\\d+\\.)\\s/;"))
  }

  @Test
  fun `defines applyListToggle that strips or prepends list markers per line`() {
      assertTrue(html.contains("function applyListToggle(field, range, ordered) {"))
      assertTrue(html.contains("const matchRegex = ordered ? NUMBERED_LINE_RE : BULLETED_LINE_RE;"))
      assertTrue(html.contains("const marker = ordered ? (counter + \". \") : \"- \";"))
      assertTrue(html.contains("applyMarkdownRawEdit(field, newRaw, newOffset);"))
  }

  @Test
  fun `selection toolbar buttons render svg icons via data-tooltip not native title`() {
      assertTrue(html.contains("function makeButton(format, iconName, label, onClick) {"))
      assertTrue(html.contains("btn.insertAdjacentHTML(\"afterbegin\", icon(iconName));"))
      assertTrue(html.contains("btn.setAttribute(\"data-tooltip\", label);"))
      assertTrue(html.contains("makeButton(\"bold\", \"bold\", \"Bold\""))
      assertTrue(html.contains("makeButton(\"italic\", \"italic\", \"Italic\""))
      assertTrue(html.contains("makeButton(\"strike\", \"strike\", \"Strikethrough\""))
      assertTrue(html.contains("makeButton(\"code\", \"code\", \"Code\""))
      assertTrue(html.contains("makeButton(\"link\", \"link\", \"Link\""))
  }

  @Test
  fun `defines applyCodeBlockToggle that wraps or unwraps triple backtick fences`() {
      assertTrue(html.contains("function applyCodeBlockToggle(field, range) {"))
      assertTrue(html.contains("const insideAtStart = isMarkdownCodeFenceOffset(raw, sel.start);"))
      assertTrue(html.contains("if (isCodeFenceLine(lines[i])) { fenceBefore = i; break; }"))
      assertTrue(html.contains("lines.splice(slice.lastIdx + 1, 0, \"```\");"))
      assertTrue(html.contains("lines.splice(slice.firstIdx, 0, \"```\");"))
  }

  @Test
  fun `selection toolbar has block-level buttons in slack order`() {
      assertTrue(html.contains("makeButton(\"bulleted\", \"bulleted\", \"Bulleted list\""))
      assertTrue(html.contains("makeButton(\"numbered\", \"numbered\", \"Numbered list\""))
      assertTrue(html.contains("makeButton(\"codeBlock\", \"codeBlock\", \"Code block\""))
      assertTrue(html.contains("applyListToggle(current.field, current.range, false)"))
      assertTrue(html.contains("applyListToggle(current.field, current.range, true)"))
      assertTrue(html.contains("applyCodeBlockToggle(current.field, current.range)"))
      val toolbarBlock = html.substringAfter("function bindSelectionToolbar(linkPopover) {")
          .substringBefore("function ")
      val order = listOf("\"bold\"", "\"italic\"", "\"strike\"", "\"bulleted\"", "\"numbered\"", "\"code\"", "\"codeBlock\"", "\"link\"")
      var idx = -1
      for (key in order) {
          val next = toolbarBlock.indexOf(key, idx + 1)
          assertTrue(next > idx)
          idx = next
      }
  }

  @Test
  fun `detectActiveFormats tracks bulleted numbered and codeblock active state`() {
      assertTrue(html.contains("bulleted: false, numbered: false, codeBlock: false"))
      assertTrue(html.contains("active.bulleted = lineRange.length > 0 && lineRange.every"))
      assertTrue(html.contains("active.numbered = lineRange.length > 0 && lineRange.every"))
      assertTrue(html.contains("active.codeBlock = isMarkdownCodeFenceOffset(raw, sel.start);"))
  }

  private fun mediaBlock(query: String): String {
    val marker = "@media ($query) {"
    val start = html.indexOf(marker)
    if (start < 0) return ""
    var depth = 0
    for (index in start until html.length) {
      when (html[index]) {
        '{' -> depth += 1
        '}' -> {
          depth -= 1
          if (depth == 0) return html.substring(start, index + 1)
        }
      }
    }
    return html.substring(start)
  }

  @Test
  fun `link popover hover row uses svg icons and data-tooltip`() {
      assertTrue(html.contains("openBtn.insertAdjacentHTML(\"afterbegin\", icon(\"externalLink\"));"))
      assertTrue(html.contains("editBtn.insertAdjacentHTML(\"afterbegin\", icon(\"edit\"));"))
      assertTrue(html.contains("unlinkBtn.insertAdjacentHTML(\"afterbegin\", icon(\"remove\"));"))
      assertTrue(html.contains("openBtn.setAttribute(\"data-tooltip\", \"Open in browser\");"))
      assertTrue(html.contains("editBtn.setAttribute(\"data-tooltip\", \"Edit link\");"))
      assertTrue(html.contains("unlinkBtn.setAttribute(\"data-tooltip\", \"Unlink\");"))
  }

  @Test
  fun `parseListMarker returns indent marker and body for list lines`() {
    assertTrue(html.contains("function parseListMarker(line) {"))
    assertTrue(html.contains("const match = String(line || \"\").match(ANY_LIST_MARKER_RE);"))
    assertTrue(html.contains("if (!match) return null;"))
    assertTrue(html.contains("return { indent: match[1], marker: match[0].slice(match[1].length), body: line.slice(match[0].length) };"))
  }

  @Test
  fun `renderMarkdown emits markdown-list-item with data attributes for list lines`() {
      assertTrue(html.contains("const parsedList = parseListMarker(line);"))
      assertTrue(html.contains("if (parsedList) {"))
      assertTrue(html.contains("class=\"markdown-line markdown-list-item\" data-markdown-line=\"true\""))
      assertTrue(html.contains("' data-list-marker=\"' + escapeHtml(parsedList.marker) + '\"'"))
      assertTrue(html.contains("' data-list-indent=\"' + depth + '\"'"))
      assertTrue(html.contains("' style=\"--md-list-indent: ' + depth + '\">'"))
  }

  @Test
  fun `markdown-list-item CSS renders bullet glyph and literal numbered marker`() {
      assertTrue(html.contains(".markdown-list-item {"))
      assertTrue(html.contains("padding-left: calc(20px + var(--md-list-indent, 0) * 18px);"))
      assertTrue(html.contains(".markdown-list-item::before {"))
      assertTrue(html.contains("content: attr(data-list-marker);"))
      assertTrue(html.contains("font-variant-numeric: tabular-nums;"))
      assertTrue(html.contains(".markdown-list-item[data-list-marker=\"- \"]::before,"))
      assertTrue(html.contains(".markdown-list-item[data-list-marker=\"* \"]::before"))
      assertTrue(html.contains("content: \"\\2022\";"))
  }

  @Test
  fun `serializeMarkdownEditor prefixes list-item lines with indent and marker`() {
      assertTrue(html.contains("element.classList && element.classList.contains(\"markdown-list-item\")"))
      assertTrue(html.contains("const listMarker = element.dataset.listMarker || \"\";"))
      assertTrue(html.contains("const listDepth = Number(element.dataset.listIndent || 0);"))
      assertTrue(html.contains("const listIndent = \" \".repeat(Math.max(0, listDepth) * 2);"))
      assertTrue(html.contains("return listIndent + listMarker + inner;"))
  }

  @Test
  fun `rawLengthAndSelection adds prefix length for markdown-list-item nodes`() {
      assertTrue(html.contains("if (element.matches(\"[data-list-marker]\")) {"))
      assertTrue(html.contains("const listMarker = element.dataset.listMarker || \"\";"))
      assertTrue(html.contains("const listDepth = Number(element.dataset.listIndent || 0);"))
      assertTrue(html.contains("const prefixLength = Math.max(0, listDepth) * 2 + listMarker.length;"))
      assertTrue(html.contains("length: inner.length + prefixLength,"))
      assertTrue(html.contains("offset: inner.found ? inner.offset + prefixLength : inner.length + prefixLength,"))
  }

  @Test
  fun `omits initial-snapshot script when no snapshot supplied`() {
    val html = SpeqaWebViewPreviewSupport.buildInlinedPreviewHtml("light")
    assertTrue(!html.contains("id=\"speqa-initial-snapshot\""))
  }

  @Test
  fun `embeds initial-snapshot script when snapshot supplied`() {
    val html = SpeqaWebViewPreviewSupport.buildInlinedPreviewHtml(
      theme = "dark",
      initialSnapshotJson = """{"theme":"dark","title":"hello"}""",
    )
    assertTrue(html.contains("<script type=\"application/json\" id=\"speqa-initial-snapshot\">"))
    assertTrue(html.contains("\"title\":\"hello\""))
    val headEnd = html.indexOf("</head>")
    val scriptIdx = html.indexOf("id=\"speqa-initial-snapshot\"")
    assertTrue(scriptIdx in 0..<headEnd)
  }

  @Test
  fun `escapes inner closing script tag inside initial snapshot json`() {
    val html = SpeqaWebViewPreviewSupport.buildInlinedPreviewHtml(
      theme = "light",
      initialSnapshotJson = """{"x":"a</script>b"}""",
    )
    assertTrue(!html.contains("\"a</script>b\""))
    assertTrue(html.contains("\"a<\\/script>b\""))
  }
}
