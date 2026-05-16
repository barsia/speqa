(function installKwryBridge() {
  if (window.__KWRY__ && window.__KWRY__.__installed) return;

  var JSONRPC_VERSION = "2.0";
  var HANDLER_CHANNEL = "webviewIpc";
  var subscribers = Object.create(null);

  function postToKotlin(frame) {
    try {
      if (window.chrome && window.chrome.webview) {
        window.chrome.webview.postMessage(JSON.stringify(frame));
        return;
      }
      var handler = window.webkit && window.webkit.messageHandlers && window.webkit.messageHandlers[HANDLER_CHANNEL];
      if (handler) handler.postMessage(JSON.stringify(frame));
    } catch (err) {
      console.error("[SpeQA] postMessage failed", err);
    }
  }

  window.__KWRY__ = {
    __installed: true,
    subscribe: function(method, handler) {
      (subscribers[method] || (subscribers[method] = [])).push(handler);
    },
    notify: function(method, params) {
      var frame = { jsonrpc: JSONRPC_VERSION, method: method };
      if (typeof params !== "undefined") frame.params = params;
      postToKotlin(frame);
    },
    __deliver: function(raw) {
      var frame = JSON.parse(raw);
      var handlers = subscribers[frame.method] || [];
      handlers.slice().forEach(function(handler) { handler(frame.params); });
    }
  };
})();

const methods = {
  snapshot: "speqa/testCase/snapshot",
  ready: "speqa/testCase/ready",
  previewTextFocusChanged: "speqa/testCase/previewTextFocusChanged",
  previewScrolled: "speqa/testCase/previewScrolled",
  scrollToFraction: "speqa/testCase/scrollToFraction",
  fieldChanged: "speqa/testCase/fieldChanged",
  listChanged: "speqa/testCase/listChanged",
  bodyChanged: "speqa/testCase/bodyChanged",
  stepChanged: "speqa/testCase/stepChanged",
  addStep: "speqa/testCase/addStep",
  deleteStep: "speqa/testCase/deleteStep",
  reorderStep: "speqa/testCase/reorderStep",
  openLink: "speqa/testCase/openLink",
  openTicket: "speqa/testCase/openTicket",
  openAttachment: "speqa/testCase/openAttachment",
  addLink: "speqa/testCase/addLink",
  addAttachment: "speqa/testCase/addAttachment",
  addStepTicket: "speqa/testCase/addStepTicket",
  addStepLink: "speqa/testCase/addStepLink",
  addStepAttachment: "speqa/testCase/addStepAttachment",
  editStepTicket: "speqa/testCase/editStepTicket",
  deleteStepTicket: "speqa/testCase/deleteStepTicket",
  editStepLink: "speqa/testCase/editStepLink",
  deleteStepLink: "speqa/testCase/deleteStepLink",
  editStepAttachment: "speqa/testCase/editStepAttachment",
  deleteStepAttachment: "speqa/testCase/deleteStepAttachment",
  editLink: "speqa/testCase/editLink",
  deleteLink: "speqa/testCase/deleteLink",
  editAttachment: "speqa/testCase/editAttachment",
  deleteAttachment: "speqa/testCase/deleteAttachment",
  addMetadata: "speqa/testCase/addMetadata",
  editMetadata: "speqa/testCase/editMetadata",
  deleteMetadata: "speqa/testCase/deleteMetadata",
  filterMetadata: "speqa/testCase/filterMetadata",
  dismissMetadataMatches: "speqa/testCase/dismissMetadataMatches",
  nativeTextEditingCommand: "speqa/testCase/nativeTextEditingCommand",
  normalizedPreviewCopy: "speqa/testCase/normalizedPreviewCopy",
  codeCopyDiagnostic: "speqa/testCase/codeCopyDiagnostic",
  codeBlockCopyRequested: "speqa/testCase/codeBlockCopyRequested",
  previewPasteRequested: "speqa/testCase/previewPasteRequested",
  pastePreviewText: "speqa/testCase/pastePreviewText",
  run: "speqa/testCase/run",
  setStepVerdict: "speqa/testCase/setStepVerdict",
  setRunResult: "speqa/testCase/setRunResult"
};

const SCROLL_SYNC_SUPPRESS_MS = 220;
const CARET_MARKER = "\uE000";
const PRIORITY_OPTIONS = [
  { value: "critical", label: "Critical" },
  { value: "major", label: "Major" },
  { value: "normal", label: "Normal" },
  { value: "low", label: "Low" }
];
const STATUS_OPTIONS = [
  { value: "draft", label: "Draft" },
  { value: "ready", label: "Ready" },
  { value: "deprecated", label: "Deprecated" }
];
const RUN_RESULT_OPTIONS = [
  { value: "not_started", label: "Not started" },
  { value: "in_progress", label: "In progress" },
  { value: "passed", label: "Passed" },
  { value: "failed", label: "Failed" },
  { value: "blocked", label: "Blocked" }
];
const app = document.getElementById("app");
const scrollIndicator = document.getElementById("scrollIndicator");
const scrollIndicatorThumb = document.getElementById("scrollIndicatorThumb");
const overflowTooltip = document.getElementById("overflowTooltip");
const focusTrail = document.getElementById("focusTrail");
const focusTrailTitle = document.getElementById("focusTrailTitle");
const focusTrailProgress = document.getElementById("focusTrailProgress");
let focusTrailObserver = null;
let state = null;
let lastSent = Object.create(null);
let draggedStepIndex = null;
let activeDragImage = null;
let activeContextMenu = null;
let activeMetadataPopover = null;
let activeChoiceDropdown = null;
let textareaResizeFrame = 0;
let pendingFocusStepIndex = null;
let pendingScrollY = null;
let pendingPreviewPasteTarget = null;
let previewTextFocusActive = false;
let previewScrollFrame = 0;
let suppressPreviewScrollUntil = 0;
let scrollIndicatorHideTimer = 0;
const markdownHistoryByField = new Map();

function escapeHtml(value) {
  return String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;");
}

function overflowTooltipAttr(value) {
  return ' data-overflow-tooltip="' + escapeHtml(value) + '"';
}

function csv(values) {
  return (values || []).join(", ");
}

function isCodeFenceLine(line) {
  const match = String(line || "").match(/^(\s*)```([A-Za-z0-9_-]+)?\s*$/);
  if (!match) return null;
  return {
    indent: match[1].length,
    language: match[2] || ""
  };
}

function stripCodeIndent(lines) {
  const nonBlank = lines.filter(function(line) { return line.trim() !== ""; });
  if (!nonBlank.length) return "";
  const indent = Math.min.apply(null, nonBlank.map(function(line) {
    const match = line.match(/^\s*/);
    return match ? match[0].length : 0;
  }));
  return lines.map(function(line) { return line.slice(indent); }).join("\n");
}

function highlightCode(code, language) {
  if (!language || typeof hljs === "undefined") return escapeHtml(code);
  if (!hljs.getLanguage(language)) return escapeHtml(code);
  try {
    return hljs.highlight(code, { language: language, ignoreIllegals: true }).value;
  } catch (_) {
    return escapeHtml(code);
  }
}

function renderCodeBlock(lines, language, fenceIndent) {
  const code = stripCodeIndent(lines);
  // Skip highlighting for the block containing the caret marker — keeps a
  // single text node inside <code> so caret restoration stays trivial and
  // hljs tokenizers don't see the U+E000 marker.
  const hasCaret = code.indexOf(CARET_MARKER) >= 0;
  const codeHtml = code
    ? (hasCaret ? escapeHtml(code) : highlightCode(code, language))
    : "<br>";
  const languageName = String(language || "");
  const indent = " ".repeat(Math.max(0, Number(fenceIndent) || 0));
  return '<div class="markdown-code-block" data-code-block="true" data-code-language="' + escapeHtml(languageName) + '" data-code-indent="' + escapeHtml(indent) + '">' +
    '<button class="markdown-code-copy" type="button" contenteditable="false" data-copy-code="true" data-copied="false" aria-label="Copy code block" data-tooltip="Copy code block">' +
      '<span class="markdown-code-copy-icon markdown-code-copy-icon-copy">' + icon("copy") + '</span>' +
      '<span class="markdown-code-copy-icon markdown-code-copy-icon-check">' + icon("check") + '</span>' +
    '</button>' +
    (languageName ? '<div class="markdown-code-language" contenteditable="false">' + escapeHtml(languageName) + '</div>' : "") +
    '<pre><code class="markdown-code-content hljs" data-code-content="true">' + codeHtml + '</code></pre>' +
  '</div>';
}

function countBackticks(text, start) {
  let count = 0;
  while (text[start + count] === "`") count += 1;
  return count;
}

function nextSingleBacktick(text, start) {
  let index = start;
  while (index < text.length) {
    const next = text.indexOf("`", index);
    if (next < 0) return -1;
    if (countBackticks(text, next) === 1) return next;
    index = next + countBackticks(text, next);
  }
  return -1;
}

function renderInlineMarkdown(text) {
  const source = String(text || "");
  let html = "";
  let i = 0;

  while (i < source.length) {
    const ch = source[i];

    // Inline code (single backtick pair): highest precedence, contents are literal.
    if (ch === "`" && countBackticks(source, i) === 1) {
      const end = nextSingleBacktick(source, i + 1);
      if (end >= 0) {
        html += '<code class="markdown-inline-code" data-inline-code="true">' + escapeHtml(source.slice(i + 1, end)) + "</code>";
        i = end + 1;
        continue;
      }
    }

    // Link: [text](url). url must not contain whitespace or `)`; text must not span lines.
    if (ch === "[") {
      const linkClose = source.indexOf("]", i + 1);
      if (linkClose > i + 1 && source[linkClose + 1] === "(") {
        const urlClose = source.indexOf(")", linkClose + 2);
        if (urlClose > linkClose + 2) {
          const linkText = source.slice(i + 1, linkClose);
          const url = source.slice(linkClose + 2, urlClose);
          if (linkText.indexOf("\n") < 0 && linkText.indexOf("[") < 0 && url.indexOf("\n") < 0 && !/\s/.test(url)) {
            html += '<span class="markdown-inline-link" data-inline-link="true" data-link-url="' + escapeHtml(url) + '">' +
              renderInlineMarkdown(linkText) +
              "</span>";
            i = urlClose + 1;
            continue;
          }
        }
      }
    }

    // Bold (**...**): must be checked before single-star italic.
    if (ch === "*" && source[i + 1] === "*") {
      const end = findInlineDelim(source, i + 2, "**");
      if (end > i + 2) {
        html += '<strong data-inline-bold="true">' + renderInlineMarkdown(source.slice(i + 2, end)) + "</strong>";
        i = end + 2;
        continue;
      }
    }

    // Bold (__...__): CommonMark alternative.
    if (ch === "_" && source[i + 1] === "_") {
      const end = findInlineDelim(source, i + 2, "__");
      if (end > i + 2) {
        html += '<strong data-inline-bold="true" data-inline-delim="__">' + renderInlineMarkdown(source.slice(i + 2, end)) + "</strong>";
        i = end + 2;
        continue;
      }
    }

    // Italic (*...*): single star, content cannot start/end with whitespace.
    if (ch === "*" && source[i + 1] !== "*") {
      const end = findInlineDelim(source, i + 1, "*");
      if (end > i + 1 && source[end + 1] !== "*") {
        const inner = source.slice(i + 1, end);
        if (inner.length > 0 && !/^\s|\s$/.test(inner)) {
          html += '<em data-inline-italic="true">' + renderInlineMarkdown(inner) + "</em>";
          i = end + 1;
          continue;
        }
      }
    }

    // Italic (_..._): underscore alternative (Slack / CommonMark).
    if (ch === "_" && source[i + 1] !== "_") {
      const end = findInlineDelim(source, i + 1, "_");
      if (end > i + 1 && source[end + 1] !== "_") {
        const inner = source.slice(i + 1, end);
        if (inner.length > 0 && !/^\s|\s$/.test(inner)) {
          html += '<em data-inline-italic="true" data-inline-delim="_">' + renderInlineMarkdown(inner) + "</em>";
          i = end + 1;
          continue;
        }
      }
    }

    // Strikethrough (~~...~~)
    if (ch === "~" && source[i + 1] === "~") {
      const end = findInlineDelim(source, i + 2, "~~");
      if (end > i + 2) {
        html += '<s data-inline-strike="true">' + renderInlineMarkdown(source.slice(i + 2, end)) + "</s>";
        i = end + 2;
        continue;
      }
    }

    // Strikethrough (~...~): Slack-style single tilde.
    if (ch === "~" && source[i + 1] !== "~") {
      const end = findInlineDelim(source, i + 1, "~");
      if (end > i + 1 && source[end + 1] !== "~") {
        const inner = source.slice(i + 1, end);
        if (inner.length > 0 && !/^\s|\s$/.test(inner)) {
          html += '<s data-inline-strike="true" data-inline-delim="~">' + renderInlineMarkdown(inner) + "</s>";
          i = end + 1;
          continue;
        }
      }
    }

    html += escapeHtml(ch);
    i += 1;
  }

  return html;
}

// Finds the next occurrence of `delim` after `from`, but not split across a newline.
// Returns -1 if not found on the same logical line.
function findInlineDelim(source, from, delim) {
  let index = from;
  while (index < source.length) {
    const next = source.indexOf(delim, index);
    if (next < 0) return -1;
    const between = source.slice(from, next);
    if (between.indexOf("\n") >= 0) return -1;
    return next;
  }
  return -1;
}

function renderMarkdown(markdown) {
  const value = String(markdown || "");
  if (!value) return "";

  const lines = value.split(/\r?\n/);
  let html = "";
  let codeLines = null;
  let codeLanguage = "";
  let codeIndent = 0;

  lines.forEach(function(line) {
    const fence = isCodeFenceLine(line);
    if (fence) {
      if (codeLines) {
        html += renderCodeBlock(codeLines, codeLanguage, codeIndent);
        codeLines = null;
        codeLanguage = "";
        codeIndent = 0;
      } else {
        codeLines = [];
        codeLanguage = fence.language;
        codeIndent = fence.indent;
      }
      return;
    }

    if (codeLines) {
      codeLines.push(line);
      return;
    }

    if (!line.trim()) {
      html += '<div class="markdown-spacer" data-markdown-spacer="true"><br></div>';
      return;
    }

    const parsedList = parseListMarker(line);
    if (parsedList) {
      const depth = Math.floor(parsedList.indent.length / 2);
      html += '<div class="markdown-line markdown-list-item" data-markdown-line="true"'
        + ' data-list-marker="' + escapeHtml(parsedList.marker) + '"'
        + ' data-list-indent="' + depth + '"'
        + ' style="--md-list-indent: ' + depth + '">'
        + renderInlineMarkdown(parsedList.body)
        + '</div>';
    } else {
      html += '<div class="markdown-line" data-markdown-line="true">' + renderInlineMarkdown(line) + '</div>';
    }
  });

  if (codeLines) {
    html += renderCodeBlock(codeLines, codeLanguage, codeIndent);
  }

  return html;
}

function markdownEditor(kind, attrs, value, placeholder) {
  const emptyClass = String(value || "").trim() ? "" : " is-empty";
  return '<div class="markdown-field" data-markdown-field="' + kind + '">' +
    '<div ' + attrs + ' class="markdown-editor' + emptyClass + '" data-markdown-editor="' + kind + '" contenteditable="true" tabindex="0" role="textbox" aria-multiline="true" data-placeholder="' + escapeHtml(placeholder || "") + '">' +
      renderMarkdown(value) +
    '</div>' +
  '</div>';
}

function icon(name) {
  if (name === "run") {
    return '<svg width="22" height="22" viewBox="0 0 16 16" aria-hidden="true" focusable="false">' +
      '<path d="M5.5 3.75v8.5L12 8 5.5 3.75Z" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linejoin="round"/>' +
    '</svg>';
  }
  if (name === "plus") {
    return '<svg width="20" height="20" viewBox="0 0 16 16" aria-hidden="true" focusable="false">' +
      '<path d="M8 3v10M3 8h10" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>' +
    '</svg>';
  }
  if (name === "chevronDown") {
    return '<svg width="14" height="14" viewBox="0 0 16 16" aria-hidden="true" focusable="false">' +
      '<path d="m4.25 6.25 3.75 3.75 3.75-3.75" fill="none" stroke="currentColor" stroke-width="1.45" stroke-linecap="round" stroke-linejoin="round"/>' +
    '</svg>';
  }
  if (name === "copy") {
    return '<svg width="13" height="13" viewBox="0 0 16 16" aria-hidden="true" focusable="false">' +
      '<path d="M6.25 5.25h5.25c.7 0 1.25.55 1.25 1.25v5.25c0 .7-.55 1.25-1.25 1.25H6.25C5.55 13 5 12.45 5 11.75V6.5c0-.7.55-1.25 1.25-1.25Z" fill="none" stroke="currentColor" stroke-width="1.35" stroke-linejoin="round"/>' +
      '<path d="M3.25 10.5H3a1.25 1.25 0 0 1-1.25-1.25V4A1.25 1.25 0 0 1 3 2.75h5.25A1.25 1.25 0 0 1 9.5 4v.25" fill="none" stroke="currentColor" stroke-width="1.35" stroke-linecap="round" stroke-linejoin="round"/>' +
    '</svg>';
  }
  if (name === "check") {
    return '<svg width="13" height="13" viewBox="0 0 16 16" aria-hidden="true" focusable="false">' +
      '<path d="m3.25 8.25 3 3.25 6.5-7" fill="none" stroke="currentColor" stroke-width="1.55" stroke-linecap="round" stroke-linejoin="round"/>' +
    '</svg>';
  }
  if (name === "externalLink") {
    return '<span class="link-icon" aria-hidden="true">' +
      '<svg width="13" height="13" viewBox="0 0 16 16" focusable="false">' +
        '<path d="M9 3.5h3.5V7M12.5 3.5 7.75 8.25" fill="none" stroke="currentColor" stroke-width="1.35" stroke-linecap="round" stroke-linejoin="round"/>' +
        '<path d="M6.5 4.5H4.25A1.25 1.25 0 0 0 3 5.75v6A1.25 1.25 0 0 0 4.25 13h6A1.25 1.25 0 0 0 11.5 11.75V9.5" fill="none" stroke="currentColor" stroke-width="1.35" stroke-linecap="round"/>' +
      '</svg>' +
    '</span>';
  }
  if (name === "ticket") {
    return '<span class="link-icon" aria-hidden="true">' +
      '<svg width="13" height="13" viewBox="0 0 16 16" focusable="false">' +
        '<path d="M3 4.75A1.75 1.75 0 0 1 4.75 3h6.5A1.75 1.75 0 0 1 13 4.75v1.6a1.6 1.6 0 0 0 0 3.3v1.6A1.75 1.75 0 0 1 11.25 13h-6.5A1.75 1.75 0 0 1 3 11.25v-1.6a1.6 1.6 0 0 0 0-3.3v-1.6Z" fill="none" stroke="currentColor" stroke-width="1.35" stroke-linejoin="round"/>' +
      '</svg>' +
    '</span>';
  }
  if (name === "attachment") {
    return '<span class="link-icon" aria-hidden="true">' +
      '<svg width="13" height="13" viewBox="0 0 16 16" focusable="false">' +
        '<path d="m5.2 8.75 4.05-4.05a2.1 2.1 0 0 1 2.97 2.97L7.3 12.6a3.15 3.15 0 0 1-4.45-4.45l5.25-5.25" fill="none" stroke="currentColor" stroke-width="1.35" stroke-linecap="round" stroke-linejoin="round"/>' +
      '</svg>' +
    '</span>';
  }
  if (name === "edit") {
    return '<svg width="12" height="12" viewBox="0 0 16 16" aria-hidden="true" focusable="false">' +
      '<path d="m4 11.75.75-2.75 5.7-5.7a1.15 1.15 0 0 1 1.65 0l.6.6a1.15 1.15 0 0 1 0 1.65L7 11.25l-3 .5Z" fill="none" stroke="currentColor" stroke-width="1.35" stroke-linejoin="round"/>' +
    '</svg>';
  }
  if (name === "remove") {
    return '<svg width="12" height="12" viewBox="0 0 16 16" aria-hidden="true" focusable="false">' +
      '<path d="M4.25 4.25 11.75 11.75M11.75 4.25 4.25 11.75" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>' +
    '</svg>';
  }
  if (name === "calendarCreated") {
    return '<svg width="12" height="12" viewBox="0 0 12 12" aria-hidden="true" focusable="false">' +
      '<rect x="1" y="2.5" width="10" height="8.5" rx="1" fill="none" stroke="currentColor" stroke-width="0.9"/>' +
      '<line x1="1" y1="5" x2="11" y2="5" stroke="currentColor" stroke-width="0.9"/>' +
      '<line x1="3.5" y1="1" x2="3.5" y2="3.5" stroke="currentColor" stroke-width="0.9" stroke-linecap="round"/>' +
      '<line x1="8.5" y1="1" x2="8.5" y2="3.5" stroke="currentColor" stroke-width="0.9" stroke-linecap="round"/>' +
      '<line x1="6" y1="6.5" x2="6" y2="9.5" stroke="currentColor" stroke-width="0.9" stroke-linecap="round"/>' +
      '<line x1="4.5" y1="8" x2="7.5" y2="8" stroke="currentColor" stroke-width="0.9" stroke-linecap="round"/>' +
    '</svg>';
  }
  if (name === "calendarUpdated") {
    return '<svg width="12" height="12" viewBox="0 0 12 12" aria-hidden="true" focusable="false">' +
      '<rect x="1" y="2.5" width="10" height="8.5" rx="1" fill="none" stroke="currentColor" stroke-width="0.9"/>' +
      '<line x1="1" y1="5" x2="11" y2="5" stroke="currentColor" stroke-width="0.9"/>' +
      '<line x1="3.5" y1="1" x2="3.5" y2="3.5" stroke="currentColor" stroke-width="0.9" stroke-linecap="round"/>' +
      '<line x1="8.5" y1="1" x2="8.5" y2="3.5" stroke="currentColor" stroke-width="0.9" stroke-linecap="round"/>' +
      '<path d="M4.5 8.5 6 7l1.5 1.5M6 7v3" fill="none" stroke="currentColor" stroke-width="0.9" stroke-linecap="round" stroke-linejoin="round"/>' +
    '</svg>';
  }
  if (name === "calendarFinished") {
    return '<svg width="12" height="12" viewBox="0 0 12 12" aria-hidden="true" focusable="false">' +
      '<rect x="1" y="2.5" width="10" height="8.5" rx="1" fill="none" stroke="currentColor" stroke-width="0.9"/>' +
      '<line x1="1" y1="5" x2="11" y2="5" stroke="currentColor" stroke-width="0.9"/>' +
      '<line x1="3.5" y1="1" x2="3.5" y2="3.5" stroke="currentColor" stroke-width="0.9" stroke-linecap="round"/>' +
      '<line x1="8.5" y1="1" x2="8.5" y2="3.5" stroke="currentColor" stroke-width="0.9" stroke-linecap="round"/>' +
      '<polyline points="4,8 5.5,9.5 8,6.5" fill="none" stroke="currentColor" stroke-width="0.9" stroke-linecap="round" stroke-linejoin="round"/>' +
    '</svg>';
  }
  if (name === "trash") {
    return '<svg width="13" height="13" viewBox="0 0 16 16" aria-hidden="true" focusable="false">' +
      '<path d="M3.5 4.5h9M6.25 4.5V3.25h3.5V4.5M5 6v6.25c0 .7.55 1.25 1.25 1.25h3.5c.7 0 1.25-.55 1.25-1.25V6" fill="none" stroke="currentColor" stroke-width="1.35" stroke-linecap="round" stroke-linejoin="round"/>' +
    '</svg>';
  }
  if (name === "bold") {
    return '<svg width="13" height="13" viewBox="0 0 16 16" aria-hidden="true" focusable="false">' +
      '<path d="M4.5 3.5h4.5a2.25 2.25 0 0 1 0 4.5H4.5z" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linejoin="round"/>' +
      '<path d="M4.5 8h5.25a2.5 2.5 0 0 1 0 5H4.5z" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linejoin="round"/>' +
    '</svg>';
  }
  if (name === "italic") {
    return '<svg width="13" height="13" viewBox="0 0 16 16" aria-hidden="true" focusable="false">' +
      '<path d="M6 3.5h5M5 12.5h5M9.5 3.5l-3 9" fill="none" stroke="currentColor" stroke-width="1.4" stroke-linecap="round"/>' +
    '</svg>';
  }
  if (name === "strike") {
    return '<svg width="13" height="13" viewBox="0 0 16 16" aria-hidden="true" focusable="false">' +
      '<path d="M11.25 5.5a2.5 2.5 0 0 0-3.5-1.25M5.75 11.25a2.5 2.5 0 0 0 3.5 1.25" fill="none" stroke="currentColor" stroke-width="1.4" stroke-linecap="round"/>' +
      '<path d="M2.5 8h11" fill="none" stroke="currentColor" stroke-width="1.4" stroke-linecap="round"/>' +
    '</svg>';
  }
  if (name === "bulleted") {
    return '<svg width="13" height="13" viewBox="0 0 16 16" aria-hidden="true" focusable="false">' +
      '<circle cx="3" cy="4" r="1" fill="currentColor"/>' +
      '<circle cx="3" cy="8" r="1" fill="currentColor"/>' +
      '<circle cx="3" cy="12" r="1" fill="currentColor"/>' +
      '<path d="M6 4h8M6 8h8M6 12h8" fill="none" stroke="currentColor" stroke-width="1.4" stroke-linecap="round"/>' +
    '</svg>';
  }
  if (name === "numbered") {
    return '<svg width="13" height="13" viewBox="0 0 16 16" aria-hidden="true" focusable="false">' +
      '<path d="M2 3v2M1.4 3.5l0.6-0.5" fill="none" stroke="currentColor" stroke-width="1.2" stroke-linecap="round" stroke-linejoin="round"/>' +
      '<path d="M1.5 7.3c0-0.5 0.6-0.6 1-0.3c0.3 0.3 0.4 0.6 0 1L1.5 9.3h2" fill="none" stroke="currentColor" stroke-width="1.2" stroke-linecap="round" stroke-linejoin="round"/>' +
      '<path d="M1.5 11c0.5-0.3 1.5-0.3 1.5 0.5c0 0.5-1 0.5-1 0.5s1 0 1 0.5c0 0.8-1 0.8-1.5 0.5" fill="none" stroke="currentColor" stroke-width="1.2" stroke-linecap="round" stroke-linejoin="round"/>' +
      '<path d="M6 4h8M6 8h8M6 12h8" fill="none" stroke="currentColor" stroke-width="1.4" stroke-linecap="round"/>' +
    '</svg>';
  }
  if (name === "code") {
    return '<svg width="13" height="13" viewBox="0 0 16 16" aria-hidden="true" focusable="false">' +
      '<path d="M5.5 4.5L2.5 8l3 3.5M10.5 4.5L13.5 8l-3 3.5M9 3.5l-2 9" fill="none" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" stroke-linejoin="round"/>' +
    '</svg>';
  }
  if (name === "codeBlock") {
    return '<svg width="13" height="13" viewBox="0 0 16 16" aria-hidden="true" focusable="false">' +
      '<rect x="1.5" y="2.5" width="13" height="11" rx="1.5" fill="none" stroke="currentColor" stroke-width="1.35"/>' +
      '<path d="M6.5 6.5L5 8l1.5 1.5M9.5 6.5L11 8l-1.5 1.5M8.5 6l-1 4" fill="none" stroke="currentColor" stroke-width="1.2" stroke-linecap="round" stroke-linejoin="round"/>' +
    '</svg>';
  }
  if (name === "link") {
    return '<svg width="13" height="13" viewBox="0 0 16 16" aria-hidden="true" focusable="false">' +
      '<path d="M7 9.5L9 7.5M5.5 6.5L4 8a2.5 2.5 0 0 0 3.5 3.5L9 10M10.5 9.5L12 8a2.5 2.5 0 0 0-3.5-3.5L7 6" fill="none" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" stroke-linejoin="round"/>' +
    '</svg>';
  }
  return "";
}

function chips(values, filterKind) {
  if (!values || values.length === 0) return "";
  return '<div class="chips">' + values.map(function(value) {
    if (!filterKind) return '<span class="chip">' + escapeHtml(value) + '</span>';
    return '<button class="chip" type="button" data-filter-kind="' + filterKind + '" ' +
      'data-filter-value="' + escapeHtml(value) + '">' + escapeHtml(value) + '</button>';
  }).join("") + '</div>';
}

function metadataCloud(values, field, filterKind) {
  if (!values || values.length === 0) return '<div class="empty-inline">No values</div>';
  return '<div class="metadata-cloud">' + values.map(function(value, index) {
    const escapedField = escapeHtml(field);
    const escapedIndex = escapeHtml(index);
    return '<span class="metadata-chip" data-metadata-chip data-metadata-field="' + escapedField + '" data-metadata-index="' + escapedIndex + '">' +
      '<button class="metadata-chip-label" type="button" data-filter-kind="' + filterKind + '" data-filter-value="' + escapeHtml(value) + '">' +
        escapeHtml(value) +
      '</button>' +
      '<button class="icon-button" type="button" data-tooltip="Edit ' + escapeHtml(value) + '" aria-label="Edit ' + escapeHtml(value) + '" data-edit-metadata data-metadata-field="' + escapedField + '" data-metadata-index="' + escapedIndex + '">' +
        icon("edit") +
      '</button>' +
      '<button class="icon-button" type="button" data-tooltip="Remove ' + escapeHtml(value) + '" aria-label="Remove ' + escapeHtml(value) + '" data-delete-metadata data-metadata-field="' + escapedField + '" data-metadata-index="' + escapedIndex + '">' +
        icon("remove") +
      '</button>' +
    '</span>';
  }).join("") + '</div>';
}

function sectionTitle(label, action) {
  return '<div class="section-title"><span>' + label + '</span><span class="title-line"></span>' +
    (action ? '<button class="section-action" type="button" data-tooltip="Add ' + label + '" aria-label="Add ' + label + '" ' + action + '>' + icon("plus") + '</button>' : "") +
    '</div>';
}

function choiceOptions(field) {
  if (field === "priority") return PRIORITY_OPTIONS;
  if (field === "runResult") return RUN_RESULT_OPTIONS;
  return STATUS_OPTIONS;
}

function renderRunProgressRow(state) {
  const steps = state.steps || [];
  const total = steps.length;
  const completed = steps.reduce(function(n, s) {
    const v = s && s.verdict;
    return n + (v && v !== "none" && v !== "" ? 1 : 0);
  }, 0);
  const valueText = state.runResult === "not_started"
    ? "Not started"
    : completed + "/" + total + " steps";
  return '<div class="run-progress-row">' +
    '<div class="section run-progress-left">' +
      '<label>Progress</label>' +
      '<span class="run-progress-value">' + escapeHtml(valueText) + '</span>' +
    '</div>' +
    '<div class="section run-progress-right">' +
      '<label>Runner</label>' +
      '<input class="runner-input" type="text" data-field="runner" value="' + escapeHtml(state.runner || "") + '" placeholder="Who is executing this run">' +
    '</div>' +
  '</div>';
}

function renderHeaderDates(state) {
  const dates = [];
  function appendDate(label, iconName, value) {
    const full = label + " " + value;
    const escapedLabel = escapeHtml(label);
    const escapedFull = escapeHtml(full);
    dates.push(
      '<span class="header-date">' +
        '<span class="header-date-icon" data-tooltip="' + escapedLabel + '" data-tooltip-overflow="' + escapedFull + '">' + icon(iconName) + '</span>' +
        '<span class="header-date-text">' + escapeHtml(value) + '</span>' +
      '</span>',
    );
  }
  if (state.createdLabel) appendDate("Created", "calendarCreated", state.createdLabel);
  if (state.updatedLabel) appendDate("Updated", "calendarUpdated", state.updatedLabel);
  if (state.startedLabel) appendDate("Started", "calendarUpdated", state.startedLabel);
  if (state.finishedLabel) appendDate("Finished", "calendarFinished", state.finishedLabel);
  return dates.length ? '<div class="header-dates">' + dates.join("") + '</div>' : "";
}

function choiceLabel(field, value) {
  const options = choiceOptions(field);
  const option = options.find(function(item) { return item.value === value; }) || options[0];
  return option.label;
}

function choiceDropdown(field, current) {
  const label = choiceLabel(field, current);
  const ariaLabel = field === "priority" ? "Priority" : (field === "runResult" ? "Run result" : "Status");
  return '<button class="choice-button" type="button" data-choice-field="' + field + '" data-choice-value="' + escapeHtml(current) + '" ' +
    'aria-haspopup="listbox" aria-expanded="false" aria-label="' + ariaLabel + '">' +
      '<span class="choice-button-label"' + overflowTooltipAttr(label) + '>' + escapeHtml(label) + '</span>' +
      '<span class="choice-chevron">' + icon("chevronDown") + '</span>' +
    '</button>';
}

function linkList(links) {
  if (!links || links.length === 0) return '<div class="empty-inline">No links</div>';
  return '<ul class="link-list">' + links.map(function(link, itemIndex) {
    const title = link.title || link.url;
    return '<li><div class="metadata-action-row">' +
      '<a class="resource-link" href="' + escapeHtml(link.url) + '" data-open-link="' + escapeHtml(link.url) + '">' +
        icon("externalLink") + '<span' + overflowTooltipAttr(title) + '>' + escapeHtml(title) + '</span></a>' +
      itemActions("link", itemIndex, title) +
    '</div></li>';
  }).join("") + '</ul>';
}

function attachmentList(attachments) {
  if (!attachments || attachments.length === 0) return '<div class="empty-inline">No attachments</div>';
  return '<ul class="attachment-list">' + attachments.map(function(attachment, itemIndex) {
    const title = shortName(attachment.path);
    return '<li><div class="metadata-action-row">' +
      '<button class="resource-link" type="button" data-open-attachment="' + escapeHtml(attachment.path) + '">' +
        icon("attachment") + '<span' + overflowTooltipAttr(title) + '>' + escapeHtml(title) + '</span></button>' +
      itemActions("attachment", itemIndex, title) +
    '</div></li>';
  }).join("") + '</ul>';
}

function itemActions(kind, itemIndex, label) {
  const escapedLabel = escapeHtml(label);
  return '<span class="item-actions">' +
    '<button class="step-tool-action is-edit" type="button" data-tooltip="Edit ' + escapedLabel + '" aria-label="Edit ' + escapedLabel + '" data-edit-' + kind + ' ' +
      'data-item-index="' + itemIndex + '">' + icon("edit") + '</button>' +
    '<button class="step-tool-action danger" type="button" data-tooltip="Delete ' + escapedLabel + '" aria-label="Delete ' + escapedLabel + '" data-delete-' + kind + ' ' +
      'data-item-index="' + itemIndex + '">' + icon("trash") + '</button>' +
  '</span>';
}

function shortName(path) {
  return String(path || "").split("/").pop() || path || "";
}

function render(next) {
  closeMetadataPopover();
  closeChoiceDropdown();
  hideOverflowTooltip();
  const shouldRestoreScroll = pendingFocusStepIndex === null;
  const shouldRestorePreviewTextFocus = !next || next.restorePreviewTextFocus !== false;
  const renderScrollY = shouldRestoreScroll ? window.scrollY : null;
  const focusedTextField = shouldRestoreScroll && shouldRestorePreviewTextFocus ? capturePreviewTextFocus() : null;
  lastSent = Object.create(null);
  state = next || {};
  document.documentElement.classList.toggle("dark", state.theme === "dark");
  document.body.classList.toggle("dark", state.theme === "dark");
  const steps = state.steps || [];

  app.innerHTML =
    '<div class="topbar">' +
      '<div class="id-dates-row">' +
        '<div class="case-meta">' +
          '<span class="case-id-prefix">' + escapeHtml(state.idPrefix || "TC-") + '</span>' +
          '<input class="case-id-input" data-field="id" value="' + escapeHtml(state.id) + '" inputmode="numeric">' +
        '</div>' +
        renderHeaderDates(state) +
        (state.mode === "run" ? "" : '<button class="run-button" type="button" data-run data-tooltip="Run test case" aria-label="Run test case">' + icon("run") + '</button>') +
      '</div>' +
      '<div class="title-row">' +
        '<textarea class="title-input" data-field="title" rows="1" spellcheck="true">' +
          escapeHtml(state.title) +
        '</textarea>' +
      '</div>' +
    '</div>' +
    '<div class="divider"></div>' +
    (state.mode === "run" ? renderRunProgressRow(state) : "") +
    '<div class="form-grid">' +
      '<div class="section"><label>Priority</label>' + choiceDropdown("priority", state.priority) + '</div>' +
      (state.mode === "run"
        ? '<div class="section"><label>Result</label>' + choiceDropdown("runResult", state.runResult) + '</div>'
        : '<div class="section"><label>Status</label>' + choiceDropdown("status", state.status) + '</div>') +
    '</div>' +
    '<div class="two-column-grid">' +
      '<div class="section">' +
        sectionTitle("Environment", 'data-add-metadata="environment"') +
        metadataCloud(state.environment, "environment", "environment") +
      '</div>' +
      '<div class="section">' +
        sectionTitle("Tags", 'data-add-metadata="tags"') +
        metadataCloud(state.tags, "tags", "tag") +
      '</div>' +
      '<div class="section">' + sectionTitle("Links", 'data-add-link') + linkList(state.links) + '</div>' +
      '<div class="section">' + sectionTitle("Attachments", 'data-add-attachment') + attachmentList(state.attachments) + '</div>' +
    '</div>' +
    '<div class="section wide">' +
      sectionTitle("Description", null) +
      markdownEditor("description", 'data-body="description" rows="1" placeholder="Type a description" spellcheck="true"', state.description, "Type a description") +
    '</div>' +
    '<div class="section wide">' +
      sectionTitle("Preconditions", null) +
      markdownEditor("preconditions", 'data-body="preconditions" rows="1" placeholder="No preconditions" spellcheck="true"', state.preconditions, "No preconditions") +
    '</div>' +
    '<div class="steps-head">' +
      sectionTitle("Scenario", null) +
    '</div>' +
    (steps.length
      ? '<div class="steps">' + steps.map(renderStep).join("") + '</div>' +
        (state.mode === "run"
          ? ''
          : '<div class="step-add-row"><button class="add-button" type="button" data-add-step>' + icon("plus") + '<span>Add step</span></button></div>')
      : '<div class="empty empty-steps">' +
          '<div class="empty-steps-text">No steps yet</div>' +
          (state.mode === "run"
            ? ''
            : '<button class="add-button" type="button" data-add-step>' + icon("plus") + '<span>Add step</span></button>') +
        '</div>');

  bindEvents();
  updateFocusTrailContent();
  setupFocusTrailObserver();
  if (focusedTextField) restorePreviewTextFocus(focusedTextField);
  else if (!shouldRestorePreviewTextFocus) notifyPreviewTextFocus(false);
  if (renderScrollY !== null) restoreRenderScroll(renderScrollY);
}

function stepTicketItems(step) {
  const tickets = step.tickets || [];
  const saved = tickets.map(function(ticket, itemIndex) {
    return '<div class="step-tool-row is-saved">' +
      '<button class="step-tool is-saved" type="button" aria-label="Open ticket ' + escapeHtml(ticket) + '" data-step-tool="ticket" ' +
        'data-open-ticket="' + escapeHtml(ticket) + '" data-step-index="' + step.index + '" data-step-ticket-index="' + itemIndex + '">' +
        icon("ticket") + '<span' + overflowTooltipAttr(ticket) + '>' + escapeHtml(ticket) + '</span></button>' +
      stepItemActions("ticket", step.index, itemIndex, ticket) +
    '</div>';
  }).join("");
  if (state.mode === "run") return saved;
  return saved + '<button class="step-tool" type="button" data-step-tool="ticket" data-add-step-ticket="' + step.index + '">' +
    icon("ticket") + '<span>Add ticket ID</span></button>';
}

function stepLinkItems(step) {
  const links = step.links || [];
  const saved = links.map(function(link, itemIndex) {
    const title = link.title || link.url;
    return '<div class="step-tool-row is-saved">' +
      '<a class="step-tool is-saved" href="' + escapeHtml(link.url) + '" aria-label="Open link ' + escapeHtml(title) + '" data-step-tool="link" ' +
        'data-open-link="' + escapeHtml(link.url) + '" data-step-index="' + step.index + '" data-step-link-index="' + itemIndex + '">' +
        icon("externalLink") + '<span' + overflowTooltipAttr(title) + '>' + escapeHtml(title) + '</span></a>' +
      stepItemActions("link", step.index, itemIndex, title) +
    '</div>';
  }).join("");
  if (state.mode === "run") return saved;
  return saved + '<button class="step-tool" type="button" data-step-tool="link" data-add-step-link="' + step.index + '">' +
    icon("externalLink") + '<span>Add link</span></button>';
}

function stepAttachmentItems(step) {
  const attachments = step.attachments || [];
  const saved = attachments.map(function(attachment, itemIndex) {
    const title = shortName(attachment.path);
    return '<div class="step-tool-row is-saved">' +
      '<button class="step-tool is-saved" type="button" aria-label="Open attachment ' + escapeHtml(title) + '" data-step-tool="attachment" ' +
        'data-open-attachment="' + escapeHtml(attachment.path) + '" data-step-index="' + step.index + '" data-step-attachment-index="' + itemIndex + '">' +
        icon("attachment") + '<span' + overflowTooltipAttr(title) + '>' + escapeHtml(title) + '</span></button>' +
      stepItemActions("attachment", step.index, itemIndex, title) +
    '</div>';
  }).join("");
  if (state.mode === "run") return saved;
  return saved + '<button class="step-tool" type="button" data-step-tool="attachment" data-add-step-attachment="' + step.index + '">' +
    icon("attachment") + '<span>Attach file</span></button>';
}

function stepItemActions(kind, stepIndex, itemIndex, label) {
  if (state.mode === "run") return "";
  const escapedLabel = escapeHtml(label);
  return '<span class="step-tool-actions">' +
    '<button class="step-tool-action is-edit" type="button" data-tooltip="Edit ' + escapedLabel + '" aria-label="Edit ' + escapedLabel + '" data-edit-step-' + kind + ' ' +
      'data-step-index="' + stepIndex + '" data-item-index="' + itemIndex + '">' + icon("edit") + '</button>' +
    '<button class="step-tool-action danger" type="button" data-tooltip="Delete ' + escapedLabel + '" aria-label="Delete ' + escapedLabel + '" data-delete-step-' + kind + ' ' +
      'data-step-index="' + stepIndex + '" data-item-index="' + itemIndex + '">' + icon("trash") + '</button>' +
  '</span>';
}

function renderStepVerdictRow(step, current) {
  const verdicts = ["passed", "failed", "skipped", "blocked"];
  return '<div class="step-verdict-row" data-step-verdict-row data-step-index="' + step.index + '">' +
    verdicts.map(function (v) {
      const pressed = (current === v) ? "true" : "false";
      const label = v.charAt(0).toUpperCase() + v.slice(1);
      return '<button type="button" class="step-verdict-button" data-verdict="' + v + '" aria-pressed="' + pressed + '">' + label + '</button>';
    }).join("") +
  '</div>';
}

function renderStep(step) {
  const number = String(step.index + 1).padStart(2, "0");
  const verdict = (state.mode === "run") ? (step.verdict || "none") : "";
  const verdictAttr = verdict ? ' data-step-verdict="' + escapeHtml(verdict) + '"' : "";
  return '<article class="step" data-step-index="' + step.index + '"' + verdictAttr + '>' +
    '<div class="step-gutter">' +
      '<div class="step-index">' + number + '</div>' +
      (state.mode === "run"
        ? ''
        : '<button class="drag-handle" type="button" draggable="true" ' +
          'data-drag-step="' + step.index + '" data-step-menu="' + step.index + '" data-tooltip="Drag step. Right click for actions." aria-label="Drag step">⠿</button>') +
    '</div>' +
    '<div class="step-main">' +
      '<div class="step-grid">' +
        markdownEditor("step-action", 'data-step-field="action" data-step-index="' + step.index + '" rows="1" placeholder="Describe the action" spellcheck="true"', step.action, "Describe the action") +
        markdownEditor("step-expected", 'data-step-field="expected" data-step-index="' + step.index + '" rows="1" placeholder="Add the expected result" spellcheck="true"', step.expected, "Add the expected result") +
      '</div>' +
      '<div class="step-tools">' +
        '<div class="step-tools-action">' +
          '<div class="step-tools-section" data-step-tools-section="tickets">' +
            stepTicketItems(step) +
          '</div>' +
          '<div class="step-tools-section" data-step-tools-section="links">' +
            stepLinkItems(step) +
          '</div>' +
        '</div>' +
        '<div class="step-tools-expected">' +
          '<div class="step-tools-section" data-step-tools-section="attachments">' +
            stepAttachmentItems(step) +
          '</div>' +
        '</div>' +
      '</div>' +
      (state.mode === "run" ? renderStepVerdictRow(step, verdict) : "") +
    '</div>' +
  '</article>';
}

function updateFocusTrailContent() {
  if (!state || !focusTrailTitle) return;
  const prefix = state.idPrefix || "TC-";
  const id = state.id ? String(state.id).trim() : "";
  const title = (state.title && state.title.trim()) || "Untitled";
  const titleText = id ? prefix + id + " · " + title : title;
  focusTrailTitle.textContent = titleText;
  if (focusTrailProgress) {
    if (state.mode === "run") {
      const steps = state.steps || [];
      const total = steps.length;
      const completed = steps.reduce(function(n, s) {
        const v = s && s.verdict;
        return n + (v && v !== "none" && v !== "" ? 1 : 0);
      }, 0);
      focusTrailProgress.textContent = "Progress: " + completed + "/" + total;
    } else {
      focusTrailProgress.textContent = "";
    }
  }
}

function setupFocusTrailObserver() {
  if (focusTrailObserver) {
    focusTrailObserver.disconnect();
    focusTrailObserver = null;
  }
  if (!focusTrail || typeof IntersectionObserver !== "function") return;
  const titleRow = app.querySelector(".title-row");
  if (!titleRow) {
    focusTrail.classList.remove("is-visible");
    focusTrail.setAttribute("aria-hidden", "true");
    return;
  }
  focusTrailObserver = new IntersectionObserver(function(entries) {
    const entry = entries[0];
    if (!entry) return;
    const offScreen = !entry.isIntersecting && entry.boundingClientRect.bottom < 0;
    if (offScreen) {
      focusTrail.classList.add("is-visible");
      focusTrail.setAttribute("aria-hidden", "false");
    } else {
      focusTrail.classList.remove("is-visible");
      focusTrail.setAttribute("aria-hidden", "true");
    }
  }, { threshold: 0 });
  focusTrailObserver.observe(titleRow);
}

function bindEvents() {
  app.querySelectorAll("[data-markdown-editor]").forEach(bindMarkdownEditor);
  app.querySelectorAll("[data-field]").forEach(function(el) {
    bindLiveCommit(el, "field:" + el.dataset.field, methods.fieldChanged, function() {
      return { field: el.dataset.field, value: fieldValue(el) };
    });
  });
  app.querySelectorAll("[data-list]").forEach(function(el) {
    bindLiveCommit(el, "list:" + el.dataset.list, methods.listChanged, function() {
      return { field: el.dataset.list, value: el.value };
    });
  });
  app.querySelectorAll("[data-body]").forEach(function(el) {
    bindLiveCommit(el, "body:" + el.dataset.body, methods.bodyChanged, function() {
      return { kind: el.dataset.body, value: editableValue(el) };
    });
  });
  app.querySelectorAll("[data-step-field]").forEach(function(el) {
    bindLiveCommit(el, "step:" + el.dataset.stepIndex + ":" + el.dataset.stepField, methods.stepChanged, function() {
      return {
        index: Number(el.dataset.stepIndex),
        field: el.dataset.stepField,
        value: editableValue(el)
      };
    });
  });
  const addButton = app.querySelector("[data-add-step]");
  if (addButton) addButton.addEventListener("click", requestAddStep);
  const runButton = app.querySelector("[data-run]");
  if (runButton) runButton.addEventListener("click", function() { window.__KWRY__.notify(methods.run); });
  app.querySelectorAll("[data-choice-field]").forEach(bindChoiceButton);
  app.querySelectorAll("[data-add-metadata]").forEach(function(button) {
    button.addEventListener("click", function(event) {
      event.stopPropagation();
      showMetadataPopover(button);
    });
  });
  app.querySelectorAll("[data-edit-metadata]").forEach(function(button) {
    button.addEventListener("click", function(event) {
      event.stopPropagation();
      window.__KWRY__.notify(methods.editMetadata, {
        field: button.dataset.metadataField,
        index: Number(button.dataset.metadataIndex)
      });
    });
  });
  app.querySelectorAll("[data-delete-metadata]").forEach(function(button) {
    button.addEventListener("click", function(event) {
      event.stopPropagation();
      window.__KWRY__.notify(methods.deleteMetadata, {
        field: button.dataset.metadataField,
        index: Number(button.dataset.metadataIndex)
      });
    });
  });
  app.querySelectorAll("[data-metadata-chip]").forEach(function(chip) {
    chip.addEventListener("contextmenu", function(event) {
      event.preventDefault();
      window.__KWRY__.notify(methods.editMetadata, {
        field: chip.dataset.metadataField,
        index: Number(chip.dataset.metadataIndex)
      });
    });
  });
  const addLinkButton = app.querySelector("[data-add-link]");
  if (addLinkButton) addLinkButton.addEventListener("click", function() { window.__KWRY__.notify(methods.addLink); });
  const addAttachmentButton = app.querySelector("[data-add-attachment]");
  if (addAttachmentButton) addAttachmentButton.addEventListener("click", function() { window.__KWRY__.notify(methods.addAttachment); });
  bindItemAction("[data-edit-link]", methods.editLink);
  bindItemAction("[data-delete-link]", methods.deleteLink);
  bindItemAction("[data-edit-attachment]", methods.editAttachment);
  bindItemAction("[data-delete-attachment]", methods.deleteAttachment);
  app.querySelectorAll("[data-open-link]").forEach(function(link) {
    link.addEventListener("click", function(event) {
      event.preventDefault();
      window.__KWRY__.notify(methods.openLink, { url: link.dataset.openLink });
    });
  });
  app.querySelectorAll("[data-open-ticket]").forEach(function(button) {
    button.addEventListener("click", function() {
      window.__KWRY__.notify(methods.openTicket, { ticket: button.dataset.openTicket });
    });
  });
  app.querySelectorAll("[data-open-attachment]").forEach(function(button) {
    button.addEventListener("click", function() {
      window.__KWRY__.notify(methods.openAttachment, { path: button.dataset.openAttachment });
    });
  });
  app.querySelectorAll("[data-add-step-ticket]").forEach(function(button) {
    button.addEventListener("click", function() {
      window.__KWRY__.notify(methods.addStepTicket, { index: Number(button.dataset.addStepTicket) });
    });
  });
  app.querySelectorAll("[data-add-step-link]").forEach(function(button) {
    button.addEventListener("click", function() {
      window.__KWRY__.notify(methods.addStepLink, { index: Number(button.dataset.addStepLink) });
    });
  });
  app.querySelectorAll("[data-add-step-attachment]").forEach(function(button) {
    button.addEventListener("click", function() {
      window.__KWRY__.notify(methods.addStepAttachment, { index: Number(button.dataset.addStepAttachment) });
    });
  });
  app.querySelectorAll("[data-step-verdict-row] [data-verdict]").forEach(function(button) {
    button.addEventListener("click", function() {
      const row = button.closest("[data-step-verdict-row]");
      if (!row) return;
      const index = Number(row.getAttribute("data-step-index"));
      const verdict = button.getAttribute("data-verdict");
      const wasPressed = button.getAttribute("aria-pressed") === "true";
      const nextVerdict = wasPressed ? "none" : verdict;
      window.__KWRY__.notify(methods.setStepVerdict, { index: index, verdict: nextVerdict });
    });
  });
  bindStepItemAction("[data-edit-step-ticket]", methods.editStepTicket);
  bindStepItemAction("[data-delete-step-ticket]", methods.deleteStepTicket);
  bindStepItemAction("[data-edit-step-link]", methods.editStepLink);
  bindStepItemAction("[data-delete-step-link]", methods.deleteStepLink);
  bindStepItemAction("[data-edit-step-attachment]", methods.editStepAttachment);
  bindStepItemAction("[data-delete-step-attachment]", methods.deleteStepAttachment);
  app.querySelectorAll("[data-step-ticket-index]").forEach(function(button) {
    button.addEventListener("contextmenu", function(event) {
      showContextMenu(event, [
        {
          label: "Open",
          action: function() {
            window.__KWRY__.notify(methods.openTicket, { ticket: button.dataset.openTicket });
          }
        },
        {
          label: "Edit",
          action: function() {
            window.__KWRY__.notify(methods.editStepTicket, {
              index: Number(button.dataset.stepIndex),
              itemIndex: Number(button.dataset.stepTicketIndex)
            });
          }
        },
        {
          label: "Delete",
          danger: true,
          action: function() {
            window.__KWRY__.notify(methods.deleteStepTicket, {
              index: Number(button.dataset.stepIndex),
              itemIndex: Number(button.dataset.stepTicketIndex)
            });
          }
        }
      ]);
    });
  });
  app.querySelectorAll("[data-step-link-index]").forEach(function(link) {
    link.addEventListener("contextmenu", function(event) {
      showContextMenu(event, [
        {
          label: "Open",
          action: function() {
            window.__KWRY__.notify(methods.openLink, { url: link.dataset.openLink });
          }
        },
        {
          label: "Edit",
          action: function() {
            window.__KWRY__.notify(methods.editStepLink, {
              index: Number(link.dataset.stepIndex),
              itemIndex: Number(link.dataset.stepLinkIndex)
            });
          }
        },
        {
          label: "Delete",
          danger: true,
          action: function() {
            window.__KWRY__.notify(methods.deleteStepLink, {
              index: Number(link.dataset.stepIndex),
              itemIndex: Number(link.dataset.stepLinkIndex)
            });
          }
        }
      ]);
    });
  });
  app.querySelectorAll("[data-step-attachment-index]").forEach(function(button) {
    button.addEventListener("contextmenu", function(event) {
      showContextMenu(event, [
        {
          label: "Open",
          action: function() {
            window.__KWRY__.notify(methods.openAttachment, { path: button.dataset.openAttachment });
          }
        },
        {
          label: "Edit",
          action: function() {
            window.__KWRY__.notify(methods.editStepAttachment, {
              index: Number(button.dataset.stepIndex),
              itemIndex: Number(button.dataset.stepAttachmentIndex)
            });
          }
        },
        {
          label: "Delete",
          danger: true,
          action: function() {
            window.__KWRY__.notify(methods.deleteStepAttachment, {
              index: Number(button.dataset.stepIndex),
              itemIndex: Number(button.dataset.stepAttachmentIndex)
            });
          }
        }
      ]);
    });
  });
  app.querySelectorAll("[data-step-menu]").forEach(function(button) {
    button.addEventListener("contextmenu", function(event) {
      showContextMenu(event, [
        {
          label: "Delete step",
          danger: true,
          action: function() {
            window.__KWRY__.notify(methods.deleteStep, { index: Number(button.dataset.stepMenu) });
          }
        }
      ]);
    });
  });
  app.querySelectorAll("[data-filter-kind]").forEach(function(button) {
    button.addEventListener("click", function(event) {
      event.stopPropagation();
      const rect = button.getBoundingClientRect();
      window.__KWRY__.notify(methods.filterMetadata, {
        kind: button.dataset.filterKind,
        value: button.dataset.filterValue,
        x: Math.round(event.clientX || rect.left),
        y: Math.round(event.clientY || rect.bottom)
      });
    });
  });
  app.querySelectorAll("textarea").forEach(function(el) {
    el.addEventListener("input", function() {
      resizeTextarea(el);
    });
  });
  scheduleTextareaResize();
  restorePendingAddStepFocus();
  bindStepDragAndDrop();
}

function normalizeEditableText(text) {
  return String(text || "").replaceAll("\u00a0", " ");
}

function editableText(element) {
  if (!element) return "";
  return normalizeEditableText(element.innerText !== undefined ? element.innerText : element.textContent);
}

function createMarkdownPopoverRoot(modifier) {
  const root = document.createElement("div");
  root.className = "markdown-popover markdown-popover--" + modifier;
  document.body.appendChild(root);
  return root;
}

function commitMarkdownPopoverEdit(field, before) {
  recordMarkdownProgrammaticEdit(field, before);
  field.dispatchEvent(new InputEvent("input", { bubbles: true, inputType: "insertReplacementText" }));
}

function inlineFormatSpec(format) {
  if (format === "bold") return { tag: "strong", attr: "data-inline-bold" };
  if (format === "italic") return { tag: "em", attr: "data-inline-italic" };
  if (format === "strike") return { tag: "s", attr: "data-inline-strike" };
  if (format === "code") return { tag: "code", attr: "data-inline-code" };
  return null;
}

function applyInlineFormat(field, range, format) {
  const spec = inlineFormatSpec(format);
  if (!spec || range.collapsed) return null;
  const text = range.toString();
  if (!text || !text.trim()) return null;
  const before = markdownHistorySnapshot(field);
  const wrapper = document.createElement(spec.tag);
  wrapper.setAttribute(spec.attr, "true");
  try {
    range.surroundContents(wrapper);
  } catch (e) {
    const contents = range.extractContents();
    wrapper.appendChild(contents);
    range.insertNode(wrapper);
  }
  mergeAdjacent(field);
  commitMarkdownPopoverEdit(field, before);
  const newRange = document.createRange();
  newRange.selectNodeContents(wrapper);
  return newRange;
}

function removeInlineFormat(field, range, format) {
  const spec = inlineFormatSpec(format);
  if (!spec) return null;
  let node = range.startContainer;
  let wrapper = null;
  while (node && node !== field) {
    if (node.nodeType === Node.ELEMENT_NODE && node.hasAttribute && node.hasAttribute(spec.attr)) {
      wrapper = node;
      break;
    }
    node = node.parentNode;
  }
  if (!wrapper) return null;
  // Insert comment markers at the range endpoints so we can restore
  // the selection after the unwrap. Comments are inert under
  // normalize (unlike empty text nodes that get pruned) and outlive
  // the wrapper removal as long as they sit inside its descendants.
  const startMarker = document.createComment("speqa-sel-start");
  const endMarker = document.createComment("speqa-sel-end");
  const endInsert = range.cloneRange();
  endInsert.collapse(false);
  endInsert.insertNode(endMarker);
  const startInsert = range.cloneRange();
  startInsert.collapse(true);
  startInsert.insertNode(startMarker);
  const before = markdownHistorySnapshot(field);
  const children = Array.from(wrapper.childNodes);
  wrapper.replaceWith.apply(wrapper, children);
  mergeAdjacent(field);
  commitMarkdownPopoverEdit(field, before);
  let newRange = null;
  if (startMarker.isConnected && endMarker.isConnected) {
    newRange = document.createRange();
    newRange.setStartAfter(startMarker);
    newRange.setEndBefore(endMarker);
  }
  if (startMarker.parentNode) startMarker.parentNode.removeChild(startMarker);
  if (endMarker.parentNode) endMarker.parentNode.removeChild(endMarker);
  field.normalize();
  return newRange;
}

function mergeAdjacent(field) {
  field.normalize();
  const attrs = ["data-inline-bold", "data-inline-italic", "data-inline-strike", "data-inline-code", "data-inline-delim"];
  function sameWrapper(a, b) {
    if (a.tagName !== b.tagName) return false;
    for (let i = 0; i < attrs.length; i++) {
      if (a.getAttribute(attrs[i]) !== b.getAttribute(attrs[i])) return false;
    }
    return true;
  }
  const wrappers = field.querySelectorAll("[data-inline-bold], [data-inline-italic], [data-inline-strike], [data-inline-code]");
  for (let i = 0; i < wrappers.length; i++) {
    const w = wrappers[i];
    let next = w.nextSibling;
    while (next && next.nodeType === Node.ELEMENT_NODE && sameWrapper(w, next)) {
      while (next.firstChild) w.appendChild(next.firstChild);
      const toRemove = next;
      next = next.nextSibling;
      toRemove.remove();
    }
  }
  field.normalize();
}

function applyLink(field, range, url) {
  if (range.collapsed) return null;
  const text = range.toString();
  if (!text) return null;
  const before = markdownHistorySnapshot(field);
  const span = document.createElement("span");
  span.className = "markdown-inline-link";
  span.setAttribute("data-inline-link", "true");
  span.dataset.linkUrl = url;
  try {
    range.surroundContents(span);
  } catch (e) {
    const contents = range.extractContents();
    span.appendChild(contents);
    range.insertNode(span);
  }
  mergeAdjacent(field);
  commitMarkdownPopoverEdit(field, before);
  const newRange = document.createRange();
  newRange.setStartAfter(span);
  newRange.collapse(true);
  return newRange;
}

function updateLinkSpan(field, span, newText, newUrl) {
  if (!span || !span.matches || !span.matches("[data-inline-link]")) return null;
  const before = markdownHistorySnapshot(field);
  span.dataset.linkUrl = newUrl;
  if (newText !== span.textContent) {
    span.textContent = newText;
  }
  mergeAdjacent(field);
  commitMarkdownPopoverEdit(field, before);
  const newRange = document.createRange();
  newRange.selectNodeContents(span);
  return newRange;
}

function removeLinkSpan(field, span) {
  if (!span || !span.matches || !span.matches("[data-inline-link]")) return null;
  const before = markdownHistorySnapshot(field);
  const children = Array.from(span.childNodes);
  const firstChild = children[0] || null;
  const lastChild = children[children.length - 1] || null;
  span.replaceWith.apply(span, children);
  mergeAdjacent(field);
  commitMarkdownPopoverEdit(field, before);
  if (firstChild && lastChild) {
    const newRange = document.createRange();
    newRange.setStartBefore(firstChild);
    newRange.setEndAfter(lastChild);
    return newRange;
  }
  return null;
}

function rangeRawOffsets(field, range) {
  if (!range || !field) return null;
  function offsetAt(container, off) {
    let acc = 0;
    const children = Array.from(field.childNodes);
    for (let i = 0; i < children.length; i++) {
      if (i > 0) acc += 1;
      const r = rawLengthAndSelection(children[i], container, off);
      if (r.found) return acc + r.offset;
      acc += r.length;
    }
    return acc;
  }
  const a = offsetAt(range.startContainer, range.startOffset);
  const b = offsetAt(range.endContainer, range.endOffset);
  return { start: Math.min(a, b), end: Math.max(a, b) };
}

function selectedLineSlice(raw, start, end) {
  const lines = String(raw || "").split("\n");
  let cum = 0;
  let firstIdx = 0, lastIdx = 0;
  let firstSet = false;
  for (let i = 0; i < lines.length; i++) {
    const lineEnd = cum + lines[i].length;
    if (!firstSet && lineEnd >= start) {
      firstIdx = i;
      firstSet = true;
    }
    if (lineEnd >= end) {
      lastIdx = i;
      return { firstIdx: firstSet ? firstIdx : 0, lastIdx: lastIdx, lines: lines };
    }
    cum = lineEnd + 1;
  }
  return { firstIdx: firstSet ? firstIdx : 0, lastIdx: lines.length - 1, lines: lines };
}

const BULLETED_LINE_RE = /^(\s*)[-*]\s/;
const NUMBERED_LINE_RE = /^(\s*)\d+\.\s/;
const ANY_LIST_MARKER_RE = /^(\s*)([-*]|\d+\.)\s/;

function parseListMarker(line) {
  const match = String(line || "").match(ANY_LIST_MARKER_RE);
  if (!match) return null;
  return { indent: match[1], marker: match[0].slice(match[1].length), body: line.slice(match[0].length) };
}

function applyListToggle(field, range, ordered) {
  const before = markdownHistorySnapshot(field);
  const raw = before.raw;
  const sel = rangeRawOffsets(field, range) || { start: before.offset, end: before.offset };
  const slice = selectedLineSlice(raw, sel.start, sel.end);
  const lines = slice.lines;
  const matchRegex = ordered ? NUMBERED_LINE_RE : BULLETED_LINE_RE;
  const allMatch = lines.slice(slice.firstIdx, slice.lastIdx + 1).every(function(l) {
    return matchRegex.test(l);
  });
  if (allMatch) {
    for (let i = slice.firstIdx; i <= slice.lastIdx; i++) {
      lines[i] = lines[i].replace(matchRegex, "$1");
    }
  } else {
    let counter = 1;
    for (let i = slice.firstIdx; i <= slice.lastIdx; i++) {
      const stripped = lines[i].replace(ANY_LIST_MARKER_RE, "$1");
      const indentMatch = stripped.match(/^\s*/);
      const indent = indentMatch ? indentMatch[0] : "";
      const body = stripped.slice(indent.length);
      const marker = ordered ? (counter + ". ") : "- ";
      lines[i] = indent + marker + body;
      counter++;
    }
  }
  const newRaw = lines.join("\n");
  let newOffset = 0;
  for (let i = 0; i <= slice.lastIdx; i++) {
    if (i > 0) newOffset += 1;
    newOffset += lines[i].length;
  }
  applyMarkdownRawEdit(field, newRaw, newOffset);
}

function applyCodeBlockToggle(field, range) {
  const before = markdownHistorySnapshot(field);
  const raw = before.raw;
  const sel = rangeRawOffsets(field, range) || { start: before.offset, end: before.offset };
  const slice = selectedLineSlice(raw, sel.start, sel.end);
  const lines = slice.lines;
  const insideAtStart = isMarkdownCodeFenceOffset(raw, sel.start);
  let newOffset;
  if (insideAtStart) {
    let fenceBefore = -1, fenceAfter = -1;
    for (let i = slice.firstIdx; i >= 0; i--) {
      if (isCodeFenceLine(lines[i])) { fenceBefore = i; break; }
    }
    if (fenceBefore < 0) return;
    for (let i = fenceBefore + 1; i < lines.length; i++) {
      if (isCodeFenceLine(lines[i])) { fenceAfter = i; break; }
    }
    if (fenceAfter < 0) return;
    lines.splice(fenceAfter, 1);
    lines.splice(fenceBefore, 1);
    const targetLast = Math.max(slice.lastIdx - 2, 0);
    newOffset = 0;
    for (let i = 0; i <= targetLast; i++) {
      if (i > 0) newOffset += 1;
      newOffset += lines[i].length;
    }
  } else {
    lines.splice(slice.lastIdx + 1, 0, "```");
    lines.splice(slice.firstIdx, 0, "```");
    const targetLast = slice.lastIdx + 2;
    newOffset = 0;
    for (let i = 0; i <= targetLast; i++) {
      if (i > 0) newOffset += 1;
      newOffset += lines[i].length;
    }
  }
  const newRaw = lines.join("\n");
  applyMarkdownRawEdit(field, newRaw, newOffset);
}

function serializeInlineNode(node) {
  if (node.nodeType === Node.TEXT_NODE) return normalizeEditableText(node.textContent);
  if (node.nodeType !== Node.ELEMENT_NODE) return "";
  const element = node;
  if (element.matches("[data-inline-code]")) {
    return "`" + editableText(element) + "`";
  }
  if (element.matches("[data-inline-link]")) {
    let text = "";
    element.childNodes.forEach(function(child) {
      text += serializeInlineNode(child);
    });
    return "[" + text + "](" + (element.dataset.linkUrl || "") + ")";
  }
  if (element.matches("[data-code-block]")) return serializeMarkdownNode(element);
  if (element.tagName === "BR") return "";
  const marker = inlineMarkerDelim(element);
  let text = "";
  element.childNodes.forEach(function(child) {
    text += serializeInlineNode(child);
  });
  return marker ? marker + text + marker : text;
}

function inlineMarkerDelim(element) {
  if (element.matches("[data-inline-bold]")) return element.dataset.inlineDelim || "**";
  if (element.matches("[data-inline-italic]")) return element.dataset.inlineDelim || "*";
  if (element.matches("[data-inline-strike]")) return element.dataset.inlineDelim || "~~";
  return "";
}

function serializeInlineChildren(element) {
  let text = "";
  element.childNodes.forEach(function(child) {
    text += serializeInlineNode(child);
  });
  return text;
}

function serializeMarkdownNode(node) {
  if (node.nodeType === Node.TEXT_NODE) return normalizeEditableText(node.textContent);
  if (node.nodeType !== Node.ELEMENT_NODE) return null;
  const element = node;
  if (element.matches("[data-code-block]")) {
    const language = element.dataset.codeLanguage || "";
    const indent = element.dataset.codeIndent || "";
    const codeElement = element.querySelector("[data-code-content]");
    const code = normalizeEditableText(codeElement ? codeElement.textContent : "");
    const indentedCode = code.split("\n").map(function(line) { return indent + line; }).join("\n");
    return indent + "```" + language + "\n" + indentedCode + "\n" + indent + "```";
  }
  if (element.matches("[data-markdown-spacer]") || element.tagName === "BR") return "";
  if (element.classList && element.classList.contains("markdown-list-item")) {
    const listMarker = element.dataset.listMarker || "";
    const listDepth = Number(element.dataset.listIndent || 0);
    const listIndent = " ".repeat(Math.max(0, listDepth) * 2);
    const inner = serializeInlineChildren(element);
    return listIndent + listMarker + inner;
  }
  return serializeInlineChildren(element);
}

function serializeMarkdownEditor(editor) {
  const parts = [];
  editor.childNodes.forEach(function(node) {
    const text = serializeMarkdownNode(node);
    if (text !== null) parts.push(text);
  });
  if (!parts.length) return normalizeEditableText(editor.textContent);
  return parts.join("\n");
}

function updateMarkdownEmptyState(editor, rawValue) {
  const value = rawValue === undefined ? serializeMarkdownEditor(editor) : rawValue;
  editor.classList.toggle("is-empty", !String(value || "").trim());
}

function textNodeOffset(root, targetNode, targetOffset) {
  const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT, markdownTextNodeFilter);
  let offset = 0;
  let node = walker.nextNode();
  while (node) {
    if (node === targetNode) return offset + targetOffset;
    offset += node.nodeValue.length;
    node = walker.nextNode();
  }
  return offset;
}

function inlineRawLengthAndSelection(element, targetNode, targetOffset) {
  let length = 0;
  const children = Array.from(element.childNodes);
  for (let index = 0; index < children.length; index += 1) {
    const child = children[index];
    const result = rawLengthAndSelection(child, targetNode, targetOffset);
    if (result.found) return { length: length + result.offset, offset: length + result.offset, found: true };
    length += result.length;
  }
  if (element === targetNode) {
    let offset = 0;
    for (let index = 0; index < Math.min(targetOffset, children.length); index += 1) {
      offset += rawLengthAndSelection(children[index], targetNode, targetOffset).length;
    }
    return { length: length, offset: offset, found: true };
  }
  return { length: length, offset: length, found: false };
}

function indentedCodeText(text, indent) {
  return normalizeEditableText(text).split("\n").map(function(line) { return indent + line; }).join("\n");
}

function indentedCodeOffset(text, offset, indent) {
  const value = normalizeEditableText(text);
  const safeOffset = Math.max(0, Math.min(Number(offset) || 0, value.length));
  let rawOffset = indent.length;
  for (let index = 0; index < safeOffset; index += 1) {
    rawOffset += 1;
    if (value[index] === "\n") rawOffset += indent.length;
  }
  return rawOffset;
}

function rawLengthAndSelection(node, targetNode, targetOffset) {
  if (node.nodeType === Node.TEXT_NODE) {
    const length = normalizeEditableText(node.textContent).length;
    return {
      length: length,
      offset: node === targetNode ? Math.min(targetOffset, length) : length,
      found: node === targetNode
    };
  }
  if (node.nodeType !== Node.ELEMENT_NODE) return { length: 0, offset: 0, found: false };
  const element = node;
  if (element.matches("[data-inline-code]")) {
    const textLength = normalizeEditableText(element.textContent).length;
    if (element.contains(targetNode)) {
      return {
        length: textLength + 2,
        offset: textNodeOffset(element, targetNode, targetOffset) + 1,
        found: true
      };
    }
    return { length: textLength + 2, offset: textLength + 2, found: false };
  }
  if (element.matches("[data-code-block]")) {
    const language = element.dataset.codeLanguage || "";
    const indent = element.dataset.codeIndent || "";
    const codeElement = element.querySelector("[data-code-content]");
    const code = normalizeEditableText(codeElement ? codeElement.textContent : "");
    const codeLength = indentedCodeText(code, indent).length;
    const prefixLength = indent.length + 3 + language.length + 1;
    const suffixLength = 1 + indent.length + 3;
    const length = prefixLength + codeLength + suffixLength;
    if (codeElement && codeElement.contains(targetNode)) {
      return {
        length: length,
        offset: prefixLength + indentedCodeOffset(code, textNodeOffset(codeElement, targetNode, targetOffset), indent),
        found: true
      };
    }
    return { length: length, offset: length, found: false };
  }
  if (element.matches("[data-markdown-spacer]") || element.tagName === "BR") {
    return { length: 0, offset: 0, found: element === targetNode };
  }
  if (element.matches("[data-inline-link]")) {
    const url = element.dataset.linkUrl || "";
    const inner = inlineRawLengthAndSelection(element, targetNode, targetOffset);
    // Raw form: [text](url) - `[` (1) + text + `](url)` (3 + url.length).
    const head = 1;
    const tail = 3 + url.length;
    return {
      length: inner.length + head + tail,
      offset: inner.found ? inner.offset + head : inner.length + head + tail,
      found: inner.found,
    };
  }
  if (element.matches("[data-list-marker]")) {
    const listMarker = element.dataset.listMarker || "";
    const listDepth = Number(element.dataset.listIndent || 0);
    const prefixLength = Math.max(0, listDepth) * 2 + listMarker.length;
    const inner = inlineRawLengthAndSelection(element, targetNode, targetOffset);
    return {
      length: inner.length + prefixLength,
      offset: inner.found ? inner.offset + prefixLength : inner.length + prefixLength,
      found: inner.found,
    };
  }
  const marker = inlineMarkerDelim(element);
  if (marker) {
    const inner = inlineRawLengthAndSelection(element, targetNode, targetOffset);
    const wrap = marker.length;
    return {
      length: inner.length + 2 * wrap,
      offset: inner.found ? inner.offset + wrap : inner.length + 2 * wrap,
      found: inner.found,
    };
  }
  return inlineRawLengthAndSelection(element, targetNode, targetOffset);
}

function markdownSelectionOffset(root) {
  const selection = window.getSelection();
  if (!selection || !selection.rangeCount || !root.contains(selection.anchorNode)) return null;
  if (selection.anchorNode === root) {
    const children = Array.from(root.childNodes);
    let offset = 0;
    for (let index = 0; index < Math.min(selection.anchorOffset, children.length); index += 1) {
      if (index > 0) offset += 1;
      offset += rawLengthAndSelection(children[index], selection.anchorNode, selection.anchorOffset).length;
    }
    return offset;
  }

  let offset = 0;
  const children = Array.from(root.childNodes);
  for (let index = 0; index < children.length; index += 1) {
    const child = children[index];
    if (index > 0) offset += 1;
    const result = rawLengthAndSelection(child, selection.anchorNode, selection.anchorOffset);
    if (result.found) return offset + result.offset;
    offset += result.length;
  }
  return serializeMarkdownEditor(root).length;
}

function restoreCaretMarker(root) {
  const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT, markdownTextNodeFilter);
  let node = walker.nextNode();
  while (node) {
    const index = node.nodeValue.indexOf(CARET_MARKER);
    if (index >= 0) {
      node.nodeValue = node.nodeValue.slice(0, index) + node.nodeValue.slice(index + CARET_MARKER.length);
      const range = document.createRange();
      const selection = window.getSelection();
      range.setStart(node, index);
      range.collapse(true);
      selection.removeAllRanges();
      selection.addRange(range);
      return true;
    }
    node = walker.nextNode();
  }
  return false;
}

function markdownTextNodeFilter(node) {
  const parent = node.parentElement;
  if (parent && parent.closest('[contenteditable="false"]')) return NodeFilter.FILTER_REJECT;
  return NodeFilter.FILTER_ACCEPT;
}

function renderMarkdownWithCaret(root, raw, offset) {
  const safeOffset = Math.max(0, Math.min(String(raw || "").length, offset));
  root.innerHTML = renderMarkdown(raw.slice(0, safeOffset) + CARET_MARKER + raw.slice(safeOffset));
  root.focus({ preventScroll: true });
  if (!restoreCaretMarker(root)) {
    const range = document.createRange();
    const selection = window.getSelection();
    range.selectNodeContents(root);
    range.collapse(false);
    selection.removeAllRanges();
    selection.addRange(range);
  }
}

function insertMarkdownTextAtSelection(editor, text) {
  const raw = serializeMarkdownEditor(editor);
  const offset = markdownSelectionOffset(editor);
  const safeOffset = offset === null ? raw.length : Math.max(0, Math.min(raw.length, offset));
  const nextRaw = raw.slice(0, safeOffset) + text + raw.slice(safeOffset);
  const before = markdownHistorySnapshot(editor);
  applyMarkdownRawEdit(editor, nextRaw, safeOffset + text.length);
  recordMarkdownProgrammaticEdit(editor, before);
}

function applyMarkdownRawEdit(editor, nextRaw, nextOffset) {
  editor.dataset.formatting = "true";
  try {
    renderMarkdownWithCaret(editor, nextRaw, nextOffset);
    updateMarkdownEmptyState(editor, nextRaw);
  } finally {
    delete editor.dataset.formatting;
  }
  markdownHistoryState(editor).currentRaw = String(nextRaw || "");
  editor.dispatchEvent(new Event("input", { bubbles: true }));
}

function markdownLineBounds(raw, offset) {
  const safeOffset = Math.max(0, Math.min(String(raw || "").length, offset));
  const start = raw.lastIndexOf("\n", Math.max(0, safeOffset - 1)) + 1;
  const nextBreak = raw.indexOf("\n", safeOffset);
  return {
    start: start,
    end: nextBreak < 0 ? raw.length : nextBreak
  };
}

function isMarkdownCodeFenceOffset(raw, offset) {
  const lineStart = markdownLineBounds(raw, offset).start;
  const lines = raw.slice(0, lineStart).split("\n");
  let inCodeBlock = false;
  lines.forEach(function(line) {
    if (isCodeFenceLine(line)) inCodeBlock = !inCodeBlock;
  });
  return inCodeBlock;
}

function continueMarkdownListOnEnter(raw, offset) {
  if (isMarkdownCodeFenceOffset(raw, offset)) return null;
  const bounds = markdownLineBounds(raw, offset);
  const line = raw.slice(bounds.start, bounds.end);
  const match = line.match(/^(\s*)([-*]|\d+\.)\s(.*)$/);
  if (!match) return null;

  const indent = match[1];
  const marker = match[2];
  const body = match[3];
  if (!body.trim()) {
    return {
      raw: raw.slice(0, bounds.start) + raw.slice(bounds.end),
      offset: bounds.start
    };
  }

  const ordered = /^\d+\.$/.test(marker);
  const nextMarker = ordered
    ? indent + (parseInt(marker, 10) + 1) + ". "
    : indent + marker + " ";
  const insert = "\n" + nextMarker;
  return {
    raw: raw.slice(0, offset) + insert + raw.slice(offset),
    offset: offset + insert.length
  };
}

function previewTextFields() {
  return Array.from(app.querySelectorAll('input[data-field], textarea[data-field], [data-markdown-editor]'))
    .filter(function(field) {
      return !field.disabled && field.offsetParent !== null;
    });
}

function focusTextField(field) {
  if (!field) return;
  if (field.matches && field.matches("[data-markdown-editor]")) {
    focusMarkdownEditor(field);
    return;
  }
  field.focus({ preventScroll: true });
  if (typeof field.setSelectionRange === "function") {
    const end = field.value.length;
    field.setSelectionRange(end, end);
  }
}

function focusAdjacentTextField(field, direction) {
  const fields = previewTextFields();
  const index = fields.indexOf(field);
  if (index < 0) return;
  const next = fields[index + direction];
  if (next) {
    focusTextField(next);
  } else {
    field.blur();
  }
}

function fieldIdentitySelector(field) {
  if (!field || !field.dataset) return null;
  if (field.dataset.field) {
    return '[data-field="' + cssAttributeValue(field.dataset.field) + '"]';
  }
  if (field.dataset.body) {
    return '[data-markdown-editor][data-body="' + cssAttributeValue(field.dataset.body) + '"]';
  }
  if (field.dataset.stepField !== undefined && field.dataset.stepIndex !== undefined) {
    return '[data-markdown-editor][data-step-field="' + cssAttributeValue(field.dataset.stepField) + '"][data-step-index="' + cssAttributeValue(field.dataset.stepIndex) + '"]';
  }
  return null;
}

function cssAttributeValue(value) {
  return String(value).replace(/\\/g, "\\\\").replace(/"/g, '\\"');
}

function capturePreviewTextFocus() {
  const active = document.activeElement;
  if (!active || !app.contains(active) || !isPreviewTextInput(active)) return null;
  const selector = fieldIdentitySelector(active);
  if (!selector) return null;
  const selection = active.matches && active.matches("[data-markdown-editor]")
    ? { start: markdownSelectionOffset(active), end: null }
    : {
        start: typeof active.selectionStart === "number" ? active.selectionStart : null,
        end: typeof active.selectionEnd === "number" ? active.selectionEnd : null,
        direction: active.selectionDirection || "none"
      };
  return { selector: selector, selection: selection };
}

function restorePreviewTextFocus(snapshot) {
  const field = snapshot && app.querySelector(snapshot.selector);
  if (!field) return;
  const selection = snapshot.selection || {};
  if (field.matches && field.matches("[data-markdown-editor]")) {
    const raw = serializeMarkdownEditor(field);
    const offset = selection.start === null || selection.start === undefined
      ? raw.length
      : Math.max(0, Math.min(raw.length, selection.start));
    renderMarkdownWithCaret(field, raw, offset);
    notifyPreviewTextFocus(true);
    return;
  }

  field.focus({ preventScroll: true });
  if (typeof field.setSelectionRange === "function") {
    const length = field.value.length;
    const start = selection.start === null || selection.start === undefined ? length : Math.max(0, Math.min(length, selection.start));
    const end = selection.end === null || selection.end === undefined ? start : Math.max(0, Math.min(length, selection.end));
    field.setSelectionRange(start, end, selection.direction || "none");
  }
  notifyPreviewTextFocus(true);
}

function handleMarkdownKeydown(editor, event) {
  if (event.key !== "Enter") return;
  event.preventDefault();
  if (!serializeMarkdownEditor(editor).trim()) return;
  const raw = serializeMarkdownEditor(editor);
  const offset = markdownSelectionOffset(editor);
  const safeOffset = offset === null ? raw.length : Math.max(0, Math.min(raw.length, offset));
  const listContinuation = continueMarkdownListOnEnter(raw, safeOffset);
  if (listContinuation) {
    const before = markdownHistorySnapshot(editor);
    applyMarkdownRawEdit(editor, listContinuation.raw, listContinuation.offset);
    recordMarkdownProgrammaticEdit(editor, before);
    return;
  }
  insertMarkdownTextAtSelection(editor, "\n");
}

function caretCodeBlockIndex(editor) {
  const selection = window.getSelection();
  if (!selection || selection.rangeCount === 0) return -1;
  const node = selection.getRangeAt(0).startContainer;
  if (!node || !editor.contains(node)) return -1;
  const blocks = editor.querySelectorAll("[data-code-block]");
  for (let i = 0; i < blocks.length; i += 1) {
    if (blocks[i].contains(node)) return i;
  }
  return -1;
}

function installCaretCodeBlockSync() {
  if (document.__caretCodeBlockSyncInstalled) return;
  document.__caretCodeBlockSyncInstalled = true;
  // Re-render the active editor when the caret moves between code blocks
  // without a typing event — so the previously-focused block picks up
  // syntax highlighting and the new one drops it for safe editing.
  document.addEventListener("selectionchange", function() {
    const active = document.activeElement;
    if (!active || !active.closest) return;
    const editor = active.closest("[data-markdown-editor]");
    if (!editor) return;
    if (editor.dataset.formatting === "true") return;
    if (editor.dataset.composing === "true") return;
    const current = caretCodeBlockIndex(editor);
    const stored = editor.dataset.caretCodeBlock !== undefined
      ? Number(editor.dataset.caretCodeBlock)
      : NaN;
    if (current === stored) return;
    editor.dataset.caretCodeBlock = String(current);
  });
}

function markdownHistoryState(editor) {
  const key = markdownHistoryKey(editor);
  if (!key) {
    if (!editor.__speqaMarkdownHistory) editor.__speqaMarkdownHistory = newMarkdownHistory();
    return editor.__speqaMarkdownHistory;
  }

  let history = markdownHistoryByField.get(key);
  if (!history) {
    history = newMarkdownHistory();
    markdownHistoryByField.set(key, history);
  }
  editor.__speqaMarkdownHistory = history;
  return history;
}

function newMarkdownHistory() {
  return { undo: [], redo: [], pending: null, currentRaw: undefined };
}

function markdownHistoryKey(editor) {
  return fieldIdentitySelector(editor);
}

function markdownHistorySnapshot(editor) {
  const raw = serializeMarkdownEditor(editor);
  const offset = markdownSelectionOffset(editor);
  return {
    raw: raw,
    offset: offset === null ? raw.length : offset
  };
}

function isTrackedMarkdownInput(event) {
  if (!event || event.isComposing) return false;
  const inputType = String(event.inputType || "");
  return inputType.indexOf("insert") === 0 || inputType.indexOf("delete") === 0;
}

function rememberMarkdownEditBeforeInput(editor, event) {
  if (!isTrackedMarkdownInput(event)) return;
  markdownHistoryState(editor).pending = markdownHistorySnapshot(editor);
}

function pushMarkdownHistoryEntry(editor, before, after) {
  const history = markdownHistoryState(editor);
  history.currentRaw = after.raw;
  if (!before || before.raw === after.raw) return;
  const last = history.undo[history.undo.length - 1];
  if (last &&
      last.beforeRaw === before.raw &&
      last.afterRaw === after.raw &&
      last.beforeOffset === before.offset &&
      last.afterOffset === after.offset) {
    return;
  }
  history.undo.push({
    beforeRaw: before.raw,
    beforeOffset: before.offset,
    afterRaw: after.raw,
    afterOffset: after.offset
  });
  history.redo = [];
  if (history.undo.length > 100) history.undo.shift();
}

function recordMarkdownEditAfterInput(editor) {
  const history = markdownHistoryState(editor);
  const before = history.pending;
  history.pending = null;
  const after = markdownHistorySnapshot(editor);
  pushMarkdownHistoryEntry(editor, before, after);
}

function recordMarkdownProgrammaticEdit(editor, before) {
  pushMarkdownHistoryEntry(editor, before, markdownHistorySnapshot(editor));
}

function synchronizeMarkdownHistory(editor) {
  const history = markdownHistoryState(editor);
  const raw = serializeMarkdownEditor(editor);
  if (history.currentRaw !== undefined && history.currentRaw !== raw) {
    history.undo = [];
    history.redo = [];
    history.pending = null;
  }
  history.currentRaw = raw;
}

function handleMarkdownUndoRedoKeydown(editor, event) {
  const key = String(event.key || "").toLowerCase();
  if (key !== "z" || event.altKey || (!event.metaKey && !event.ctrlKey)) return false;

  const redo = event.shiftKey;
  const history = markdownHistoryState(editor);
  const source = redo ? history.redo : history.undo;
  if (!source.length) return false;

  event.preventDefault();
  event.stopPropagation();

  const entry = source.pop();
  const target = redo ? history.undo : history.redo;
  target.push(entry);

  const raw = redo ? entry.afterRaw : entry.beforeRaw;
  const offset = redo ? entry.afterOffset : entry.beforeOffset;
  applyMarkdownRawEdit(editor, raw, offset);
  return true;
}

function formatMarkdownEditor(editor, preserveSelection) {
  if (editor.dataset.formatting === "true") return;
  const raw = serializeMarkdownEditor(editor);
  const offset = preserveSelection ? markdownSelectionOffset(editor) : null;
  const nextHtml = renderMarkdown(raw);
  updateMarkdownEmptyState(editor, raw);
  if (editor.innerHTML === nextHtml) {
    editor.dataset.caretCodeBlock = String(caretCodeBlockIndex(editor));
    return;
  }

  editor.dataset.formatting = "true";
  try {
    if (offset !== null) {
      renderMarkdownWithCaret(editor, raw, offset);
    } else {
      editor.innerHTML = nextHtml;
    }
    updateMarkdownEmptyState(editor, raw);
  } finally {
    delete editor.dataset.formatting;
  }
  editor.dataset.caretCodeBlock = String(caretCodeBlockIndex(editor));
}

function focusMarkdownEditor(editor) {
  if (!editor) return;
  const raw = serializeMarkdownEditor(editor);
  editor.dataset.formatting = "true";
  try {
    renderMarkdownWithCaret(editor, raw, raw.length);
    updateMarkdownEmptyState(editor, raw);
  } finally {
    delete editor.dataset.formatting;
  }
}

function bindLinkPopover() {
  const HOVER_DELAY = 200;
  const rowRoot = createMarkdownPopoverRoot("link-row");
  const formRoot = createMarkdownPopoverRoot("link-form");
  formRoot.setAttribute("role", "dialog");
  formRoot.setAttribute("aria-label", "Edit link");

  const urlSpan = document.createElement("span");
  urlSpan.className = "markdown-popover-url";
  const openBtn = document.createElement("button");
  openBtn.className = "markdown-popover-button";
  openBtn.type = "button";
  openBtn.setAttribute("aria-label", "Open in browser");
  openBtn.setAttribute("data-tooltip", "Open in browser");
  openBtn.insertAdjacentHTML("afterbegin", icon("externalLink"));
  const editBtn = document.createElement("button");
  editBtn.className = "markdown-popover-button";
  editBtn.type = "button";
  editBtn.setAttribute("aria-label", "Edit link");
  editBtn.setAttribute("data-tooltip", "Edit link");
  editBtn.insertAdjacentHTML("afterbegin", icon("edit"));
  const unlinkBtn = document.createElement("button");
  unlinkBtn.className = "markdown-popover-button is-danger";
  unlinkBtn.type = "button";
  unlinkBtn.setAttribute("aria-label", "Unlink");
  unlinkBtn.setAttribute("data-tooltip", "Unlink");
  unlinkBtn.insertAdjacentHTML("afterbegin", icon("remove"));
  rowRoot.appendChild(urlSpan);
  rowRoot.appendChild(openBtn);
  rowRoot.appendChild(editBtn);
  rowRoot.appendChild(unlinkBtn);

  const textRow = document.createElement("div");
  textRow.className = "markdown-popover-row";
  const textLabel = document.createElement("label");
  textLabel.textContent = "Text";
  const textInput = document.createElement("input");
  textInput.className = "markdown-popover-input";
  textInput.type = "text";
  textInput.setAttribute("aria-label", "Link text");
  textRow.appendChild(textLabel);
  textRow.appendChild(textInput);
  const urlRow = document.createElement("div");
  urlRow.className = "markdown-popover-row";
  const urlLabel = document.createElement("label");
  urlLabel.textContent = "URL";
  const urlInput = document.createElement("input");
  urlInput.className = "markdown-popover-input";
  urlInput.type = "text";
  urlInput.setAttribute("aria-label", "Link URL");
  urlRow.appendChild(urlLabel);
  urlRow.appendChild(urlInput);
  const actions = document.createElement("div");
  actions.className = "markdown-popover-actions";
  const formUnlinkBtn = document.createElement("button");
  formUnlinkBtn.className = "markdown-popover-button is-text is-danger";
  formUnlinkBtn.type = "button";
  formUnlinkBtn.textContent = "Unlink";
  const cancelBtn = document.createElement("button");
  cancelBtn.className = "markdown-popover-button is-text";
  cancelBtn.type = "button";
  cancelBtn.textContent = "Cancel";
  const saveBtn = document.createElement("button");
  saveBtn.className = "markdown-popover-button is-text is-primary";
  saveBtn.type = "button";
  saveBtn.textContent = "Save";
  actions.appendChild(formUnlinkBtn);
  actions.appendChild(cancelBtn);
  actions.appendChild(saveBtn);
  formRoot.appendChild(textRow);
  formRoot.appendChild(urlRow);
  formRoot.appendChild(actions);

  let state = null; // { mode, field, span?, range?, url }
  let hoverTimer = 0;
  let hideTimer = 0;

  function positionAt(rect, root) {
    root.classList.add("is-visible");
    const width = root.offsetWidth;
    const height = root.offsetHeight;
    const gap = 6;
    const margin = 8;
    let top = rect.bottom + gap;
    if (top + height > window.innerHeight - margin && rect.top - gap - height > margin) {
      top = rect.top - gap - height;
    }
    let left = Math.max(margin, Math.min(rect.left, window.innerWidth - width - margin));
    root.style.top = top + "px";
    root.style.left = left + "px";
  }

  function hideLinkPopover() {
    clearTimeout(hoverTimer); hoverTimer = 0;
    clearTimeout(hideTimer); hideTimer = 0;
    rowRoot.classList.remove("is-visible");
    formRoot.classList.remove("is-visible");
    state = null;
  }

  function showHoverRow(field, span) {
    if (state && state.mode === "form") return;
    const url = span.dataset.linkUrl || "";
    state = { mode: "row", field: field, span: span, url: url };
    urlSpan.textContent = url;
    urlSpan.title = url;
    positionAt(span.getBoundingClientRect(), rowRoot);
  }

  function showFormForLink(field, span) {
    clearTimeout(hoverTimer); hoverTimer = 0;
    clearTimeout(hideTimer); hideTimer = 0;
    rowRoot.classList.remove("is-visible");
    state = { mode: "form", field: field, span: span };
    textInput.value = span.textContent;
    urlInput.value = span.dataset.linkUrl || "";
    urlInput.classList.remove("is-invalid");
    positionAt(span.getBoundingClientRect(), formRoot);
    requestAnimationFrame(function() { urlInput.focus(); urlInput.select(); });
  }

  function showFormForRange(field, range) {
    clearTimeout(hoverTimer); hoverTimer = 0;
    clearTimeout(hideTimer); hideTimer = 0;
    rowRoot.classList.remove("is-visible");
    const text = range.toString();
    state = { mode: "form", field: field, range: range };
    textInput.value = text;
    urlInput.value = "";
    urlInput.classList.remove("is-invalid");
    positionAt(range.getBoundingClientRect(), formRoot);
    requestAnimationFrame(function() { urlInput.focus(); });
  }

  function openCurrentUrl() {
    if (!state || !state.url) return;
    window.__KWRY__.notify(methods.openLink, { url: state.url });
  }

  function unlinkCurrent() {
    if (!state || !state.span) return;
    removeLinkSpan(state.field, state.span);
    hideLinkPopover();
  }

  function saveForm() {
    const url = urlInput.value.trim();
    if (!/^https?:\/\/\S+$/.test(url)) {
      urlInput.classList.add("is-invalid");
      urlInput.focus();
      return;
    }
    const text = textInput.value;
    if (state.mode === "form" && state.span) {
      updateLinkSpan(state.field, state.span, text, url);
    } else if (state.mode === "form" && state.range) {
      if (text !== state.range.toString()) {
        state.range.deleteContents();
        const node = document.createTextNode(text);
        state.range.insertNode(node);
        state.range.selectNode(node);
      }
      applyLink(state.field, state.range, url);
    }
    hideLinkPopover();
  }

  [openBtn, editBtn, unlinkBtn, formUnlinkBtn, cancelBtn, saveBtn, urlSpan].forEach(function(el) {
    el.addEventListener("mousedown", function(e) { e.preventDefault(); });
  });
  urlSpan.addEventListener("click", openCurrentUrl);
  openBtn.addEventListener("click", openCurrentUrl);
  editBtn.addEventListener("click", function() {
    if (state && state.span) showFormForLink(state.field, state.span);
  });
  unlinkBtn.addEventListener("click", unlinkCurrent);
  formUnlinkBtn.addEventListener("click", unlinkCurrent);
  cancelBtn.addEventListener("click", hideLinkPopover);
  saveBtn.addEventListener("click", saveForm);
  [textInput, urlInput].forEach(function(input) {
    input.addEventListener("keydown", function(e) {
      if (e.key === "Enter") { e.preventDefault(); saveForm(); }
      if (e.key === "Escape") { e.preventDefault(); hideLinkPopover(); }
    });
  });

  document.body.addEventListener("mouseover", function(event) {
    const span = event.target && event.target.closest && event.target.closest("[data-inline-link]");
    if (!span) return;
    const field = span.closest("[data-markdown-editor]");
    if (!field) return;
    clearTimeout(hideTimer); hideTimer = 0;
    if (state && state.mode === "row" && state.span === span) return;
    if (state && state.mode === "form") return;
    clearTimeout(hoverTimer);
    hoverTimer = setTimeout(function() { showHoverRow(field, span); }, HOVER_DELAY);
  });
  document.body.addEventListener("mouseout", function(event) {
    const span = event.target && event.target.closest && event.target.closest("[data-inline-link]");
    if (!span) return;
    const next = event.relatedTarget;
    if (next && (rowRoot.contains(next) || formRoot.contains(next))) return;
    clearTimeout(hoverTimer); hoverTimer = 0;
    clearTimeout(hideTimer);
    hideTimer = setTimeout(function() {
      if (state && state.mode === "row") hideLinkPopover();
    }, HOVER_DELAY);
  });
  rowRoot.addEventListener("mouseenter", function() {
    clearTimeout(hideTimer); hideTimer = 0;
  });
  rowRoot.addEventListener("mouseleave", function() {
    clearTimeout(hideTimer);
    hideTimer = setTimeout(function() {
      if (state && state.mode === "row") hideLinkPopover();
    }, HOVER_DELAY);
  });
  document.body.addEventListener("click", function(event) {
    const span = event.target && event.target.closest && event.target.closest("[data-inline-link]");
    if (span && span.closest("[data-markdown-editor]")) {
      const field = span.closest("[data-markdown-editor]");
      showHoverRow(field, span);
      return;
    }
    if (rowRoot.contains(event.target) || formRoot.contains(event.target)) return;
    hideLinkPopover();
  });

  return {
    hide: hideLinkPopover,
    showFormForRange: showFormForRange,
  };
}

function bindSelectionToolbar(linkPopover) {
  const root = createMarkdownPopoverRoot("selection");
  root.setAttribute("role", "toolbar");
  root.setAttribute("aria-label", "Format selection");

  const labels = {
    bold: "Bold",
    italic: "Italic",
    strike: "Strikethrough",
    code: "Code",
    link: "Link",
    bulleted: "Bulleted list",
    numbered: "Numbered list",
    codeBlock: "Code block",
  };
  const buttons = {};

  makeButton("bold", "bold", "Bold");
  makeButton("italic", "italic", "Italic");
  makeButton("strike", "strike", "Strikethrough");
  makeButton("bulleted", "bulleted", "Bulleted list", function() {
    if (current) applyListToggle(current.field, current.range, false);
  });
  makeButton("numbered", "numbered", "Numbered list", function() {
    if (current) applyListToggle(current.field, current.range, true);
  });
  const blockSep = document.createElement("span");
  blockSep.className = "markdown-popover-separator";
  root.appendChild(blockSep);
  makeButton("code", "code", "Code");
  makeButton("codeBlock", "codeBlock", "Code block", function() {
    if (current) applyCodeBlockToggle(current.field, current.range);
  });
  makeButton("link", "link", "Link", onLinkClick);

  let current = null; // { field, range }
  let rafToken = 0;

  function makeButton(format, iconName, label, onClick) {
    const btn = document.createElement("button");
    btn.type = "button";
    btn.className = "markdown-popover-button";
    btn.dataset.format = format;
    btn.setAttribute("aria-label", label);
    btn.setAttribute("data-tooltip", label);
    btn.insertAdjacentHTML("afterbegin", icon(iconName));
    btn.addEventListener("mousedown", function(e) { e.preventDefault(); });
    btn.addEventListener("click", onClick || function() { onFormatClick(format); });
    root.appendChild(btn);
    buttons[format] = btn;
    return btn;
  }

  function selectionInsideEditor(selection) {
    if (!selection || selection.rangeCount === 0 || selection.isCollapsed) return null;
    const range = selection.getRangeAt(0);
    const anchorField = range.startContainer.parentElement && range.startContainer.parentElement.closest("[data-markdown-editor]");
    const focusField = range.endContainer.parentElement && range.endContainer.parentElement.closest("[data-markdown-editor]");
    if (!anchorField || anchorField !== focusField) return null;
    if (!range.toString().trim()) return null;
    return { field: anchorField, range: range };
  }

  function detectActiveFormats(range, field) {
    const active = {
      bold: false, italic: false, strike: false, code: false, link: false,
      bulleted: false, numbered: false, codeBlock: false,
    };
    const map = {
      "data-inline-bold": "bold",
      "data-inline-italic": "italic",
      "data-inline-strike": "strike",
      "data-inline-code": "code",
      "data-inline-link": "link",
    };
    let node = range.startContainer;
    while (node && node !== field) {
      if (node.nodeType === Node.ELEMENT_NODE && node.hasAttribute) {
        Object.keys(map).forEach(function(attr) {
          if (node.hasAttribute(attr) && node.contains(range.endContainer)) {
            active[map[attr]] = true;
          }
        });
      }
      node = node.parentNode;
    }
    try {
      const raw = serializeMarkdownEditor(field);
      const sel = rangeRawOffsets(field, range);
      if (sel) {
        const slice = selectedLineSlice(raw, sel.start, sel.end);
        const lineRange = slice.lines.slice(slice.firstIdx, slice.lastIdx + 1);
        active.bulleted = lineRange.length > 0 && lineRange.every(function(l) { return BULLETED_LINE_RE.test(l); });
        active.numbered = lineRange.length > 0 && lineRange.every(function(l) { return NUMBERED_LINE_RE.test(l); });
        active.codeBlock = isMarkdownCodeFenceOffset(raw, sel.start);
      }
    } catch (e) {}
    return active;
  }

  function position(range) {
    const rect = range.getBoundingClientRect();
    root.classList.add("is-visible");
    const width = root.offsetWidth;
    const height = root.offsetHeight;
    const gap = 6;
    const margin = 8;
    let top = rect.top - height - gap;
    if (top < margin) top = rect.bottom + gap;
    const center = rect.left + rect.width / 2;
    let left = Math.max(margin, Math.min(center - width / 2, window.innerWidth - width - margin));
    root.style.top = top + "px";
    root.style.left = left + "px";
  }

  function refresh() {
    const sel = window.getSelection();
    const found = selectionInsideEditor(sel);
    if (!found) {
      root.classList.remove("is-visible");
      current = null;
      return;
    }
    current = found;
    const active = detectActiveFormats(found.range, found.field);
    Object.keys(buttons).forEach(function(key) {
      buttons[key].classList.toggle("is-active", !!active[key]);
    });
    position(found.range);
  }

  function onFormatClick(format) {
    if (!current) return;
    const active = detectActiveFormats(current.range, current.field);
    let newRange;
    if (active[format]) {
      newRange = removeInlineFormat(current.field, current.range, format);
    } else {
      newRange = applyInlineFormat(current.field, current.range, format);
    }
    if (newRange) {
      const sel = window.getSelection();
      sel.removeAllRanges();
      sel.addRange(newRange);
    }
  }

  function onLinkClick() {
    if (!current) return;
    linkPopover.showFormForRange(current.field, current.range);
  }

  document.addEventListener("selectionchange", function() {
    if (rafToken) return;
    rafToken = requestAnimationFrame(function() {
      rafToken = 0;
      refresh();
    });
  });

  return {
    hide: function() { root.classList.remove("is-visible"); current = null; },
  };
}

function bindMarkdownEditor(editor) {
  let composing = false;
  installCaretCodeBlockSync();
  updateMarkdownEmptyState(editor);
  editor.dataset.caretCodeBlock = String(caretCodeBlockIndex(editor));
  synchronizeMarkdownHistory(editor);
  editor.addEventListener("compositionstart", function() {
    composing = true;
    editor.dataset.composing = "true";
  });
  editor.addEventListener("compositionend", function() {
    composing = false;
    delete editor.dataset.composing;
    updateMarkdownEmptyState(editor);
    editor.dataset.caretCodeBlock = String(caretCodeBlockIndex(editor));
  });
  editor.addEventListener("keydown", function(event) {
    if (handleMarkdownUndoRedoKeydown(editor, event)) return;
    handleMarkdownKeydown(editor, event);
  });
  editor.addEventListener("beforeinput", function(event) {
    rememberMarkdownEditBeforeInput(editor, event);
  });
  editor.addEventListener("input", function(event) {
    if (composing) return;
    updateMarkdownEmptyState(editor);
    editor.dataset.caretCodeBlock = String(caretCodeBlockIndex(editor));
    recordMarkdownEditAfterInput(editor);
  });
  editor.addEventListener("blur", function() {
    if (!composing) formatMarkdownEditor(editor, false);
  });
}

function requestAddStep() {
  pendingFocusStepIndex = (state.steps || []).length;
  pendingScrollY = window.scrollY;
  window.__KWRY__.notify(methods.addStep);
}

function isPreviewTextInput(element) {
  return element instanceof HTMLInputElement ||
    element instanceof HTMLTextAreaElement ||
    Boolean(element && element.closest && element.closest("[data-markdown-editor]"));
}

function previewTextFieldForClipboard(eventTarget) {
  const element = eventTarget && eventTarget.nodeType === Node.ELEMENT_NODE
    ? eventTarget
    : eventTarget && eventTarget.parentElement;
  if (element instanceof HTMLInputElement || element instanceof HTMLTextAreaElement) return element;
  return element && element.closest ? element.closest("[data-markdown-editor]") : null;
}

function selectedPreviewText(field) {
  if (!field) return "";
  if (field instanceof HTMLInputElement || field instanceof HTMLTextAreaElement) {
    const start = typeof field.selectionStart === "number" ? field.selectionStart : 0;
    const end = typeof field.selectionEnd === "number" ? field.selectionEnd : start;
    if (start === end) return "";
    return field.value.slice(Math.min(start, end), Math.max(start, end));
  }
  if (field.matches && field.matches("[data-markdown-editor]")) {
    const selection = window.getSelection();
    if (!selection || selection.isCollapsed || selection.rangeCount === 0) return "";
    if (!field.contains(selection.anchorNode) || !field.contains(selection.focusNode)) return "";
    return selection.toString();
  }
  return "";
}

function normalizeCopiedPreviewText(text) {
  const value = String(text || "");
  if (!/[ \t\u00a0]$/.test(value)) return value;
  const trimmed = value.replace(/[ \t\u00a0]$/, "");
  if (!trimmed || /\s/.test(trimmed)) return value;
  return trimmed;
}

function handlePreviewTextCopy(event) {
  const field = previewTextFieldForClipboard(event.target);
  if (!field || !event.clipboardData) return;

  const text = selectedPreviewText(field);
  if (!text) return;

  const normalized = normalizeCopiedPreviewText(text);
  if (normalized === text) return;

  event.clipboardData.setData("text/plain", normalized);
  window.__KWRY__.notify(methods.normalizedPreviewCopy, { text: normalized });
  event.preventDefault();
}

function execCommandInsertText(text) {
  if (typeof document.execCommand !== "function") return false;
  try {
    return document.execCommand("insertText", false, text);
  } catch (e) {
    return false;
  }
}

function insertPlainTextAtPreviewSelection(field, text) {
  if (!field) return;
  field.focus({ preventScroll: true });

  if (field.matches && field.matches("[data-markdown-editor]")) {
    const before = markdownHistorySnapshot(field);
    if (execCommandInsertText(text)) {
      recordMarkdownProgrammaticEdit(field, before);
      return;
    }
    insertMarkdownTextAtSelection(field, text);
    return;
  }

  if (execCommandInsertText(text)) return;

  if (field instanceof HTMLInputElement || field instanceof HTMLTextAreaElement) {
    const start = typeof field.selectionStart === "number" ? field.selectionStart : field.value.length;
    const end = typeof field.selectionEnd === "number" ? field.selectionEnd : start;
    field.setRangeText(text, start, end, "end");
    field.dispatchEvent(new InputEvent("input", { bubbles: true, inputType: "insertFromPaste", data: text }));
  }
}

function maybeWrapPastedUrl(field, pasted) {
  if (!field.matches || !field.matches("[data-markdown-editor]")) return null;
  const url = String(pasted || "").trim();
  if (!/^https?:\/\/\S+$/.test(url)) return null;
  const selection = window.getSelection();
  if (!selection || selection.rangeCount === 0 || selection.isCollapsed) return null;
  if (!field.contains(selection.anchorNode) || !field.contains(selection.focusNode)) return null;
  const selectionText = selection.toString();
  if (!selectionText) return null;
  if (selectionText.indexOf("]") >= 0) return null;
  return "[" + selectionText + "](" + url + ")";
}

function handlePreviewTextPaste(event) {
  const field = previewTextFieldForClipboard(event.target);
  if (!field) return;

  const text = event.clipboardData ? event.clipboardData.getData("text/plain") : "";
  event.preventDefault();
  notifyPreviewTextFocus(true);
  if (text) {
    const insertText = maybeWrapPastedUrl(field, text) || text;
    insertPlainTextAtPreviewSelection(field, insertText);
    return;
  }

  pendingPreviewPasteTarget = capturePreviewTextFocus();
  window.__KWRY__.notify(methods.previewPasteRequested);
}

function pastePreviewText(params) {
  const target = pendingPreviewPasteTarget || capturePreviewTextFocus();
  pendingPreviewPasteTarget = null;
  const text = params && params.text !== undefined && params.text !== null
    ? String(params.text)
    : "";
  if (!target || !text) return;
  restorePreviewTextFocus(target);
  const field = app.querySelector(target.selector);
  insertPlainTextAtPreviewSelection(field, text);
}

function codeCopyButtonFromTarget(target) {
  const element = target && target.nodeType === Node.ELEMENT_NODE
    ? target
    : target && target.parentElement;
  return element && element.closest ? element.closest("[data-copy-code]") : null;
}

function codeBlockTextForCopy(button) {
  const block = button && button.closest ? button.closest("[data-code-block]") : null;
  const code = block ? block.querySelector("[data-code-content]") : null;
  return normalizeEditableText(code ? code.textContent : "").replaceAll(CARET_MARKER, "");
}

function diagnosticElementName(element) {
  if (!element) return "";
  if (element.matches && element.matches("[data-copy-code]")) return "copy-button";
  if (element.matches && element.matches("[data-code-content]")) return "code-content";
  if (element.matches && element.matches("[data-code-block]")) return "code-block";
  if (element.matches && element.matches("[data-markdown-editor]")) return "markdown-editor";
  return String(element.tagName || element.nodeName || "").toLowerCase();
}

function diagnosticNodeName(node) {
  if (!node) return "";
  if (node.nodeType === Node.TEXT_NODE) {
    return "text@" + diagnosticElementName(node.parentElement);
  }
  return diagnosticElementName(node.nodeType === Node.ELEMENT_NODE ? node : node.parentElement);
}

function diagnosticEditorName(editor) {
  if (!editor) return "";
  const parts = [editor.dataset.markdownEditor || ""];
  if (editor.dataset.stepField) parts.push("stepField=" + editor.dataset.stepField);
  if (editor.dataset.stepIndex) parts.push("stepIndex=" + editor.dataset.stepIndex);
  return parts.filter(Boolean).join(",");
}

function logCodeCopySelection(stage, button) {
  const selection = window.getSelection();
  const active = document.activeElement;
  const editor = (active && active.closest && active.closest("[data-markdown-editor]")) ||
    (button && button.closest && button.closest("[data-markdown-editor]"));
  const block = button && button.closest ? button.closest("[data-code-block]") : null;
  const code = block ? block.querySelector("[data-code-content]") : null;
  const params = {
    stage: stage,
    activeElement: diagnosticElementName(active),
    editor: diagnosticEditorName(editor),
    copied: button ? String(button.dataset.copied || "") : "",
    rangeCount: selection ? selection.rangeCount : 0,
    collapsed: selection ? selection.isCollapsed : true,
    anchorNode: selection ? diagnosticNodeName(selection.anchorNode) : "",
    anchorOffset: selection ? selection.anchorOffset : -1,
    focusNode: selection ? diagnosticNodeName(selection.focusNode) : "",
    focusOffset: selection ? selection.focusOffset : -1,
    editorContainsAnchor: Boolean(editor && selection && editor.contains(selection.anchorNode)),
    editorContainsFocus: Boolean(editor && selection && editor.contains(selection.focusNode)),
    markdownOffset: editor ? markdownSelectionOffset(editor) : null,
    markdownLength: editor ? serializeMarkdownEditor(editor).length : -1,
    codeLength: code ? normalizeEditableText(code.textContent).replaceAll(CARET_MARKER, "").length : -1
  };
  try {
    window.__KWRY__.notify(methods.codeCopyDiagnostic, params);
  } catch (_) {
    // Diagnostics must never affect editing behavior.
  }
}

function setCodeCopyButtonState(button, copied) {
  if (!button) return;
  logCodeCopySelection(copied ? "state-copied-before" : "state-reset-before", button);
  button.dataset.copied = copied ? "true" : "false";
  button.setAttribute("aria-label", copied ? "Code block copied" : "Copy code block");
  button.setAttribute("title", copied ? "Copied" : "Copy code block");
  logCodeCopySelection(copied ? "state-copied-after" : "state-reset-after", button);
  if (button.__speqaCopyResetTimer) window.clearTimeout(button.__speqaCopyResetTimer);
  if (copied) {
    button.__speqaCopyResetTimer = window.setTimeout(function() {
      logCodeCopySelection("state-reset-timeout-before", button);
      button.dataset.copied = "false";
      button.setAttribute("aria-label", "Copy code block");
      button.setAttribute("title", "Copy code block");
      logCodeCopySelection("state-reset-timeout-after", button);
      button.__speqaCopyResetTimer = null;
    }, 1200);
  }
}

function copyCodeBlockToClipboard(button) {
  const text = codeBlockTextForCopy(button);
  if (!text) return;
  logCodeCopySelection("copy-start", button);
  try {
    window.__KWRY__.notify(methods.codeBlockCopyRequested, { text: text });
    logCodeCopySelection("copy-native-requested", button);
    logCodeCopySelection("copy-success-before-feedback", button);
    setCodeCopyButtonState(button, true);
  } catch (_) {
    logCodeCopySelection("copy-failure-before-feedback", button);
    setCodeCopyButtonState(button, false);
  }
}

function handleCodeBlockCopyMouseDown(event) {
  const button = codeCopyButtonFromTarget(event.target);
  if (!button) return;
  // Keep the markdown editor focused; otherwise WebKit blurs/re-renders the
  // contenteditable block before the copy click reaches the button.
  logCodeCopySelection("mousedown", button);
  event.preventDefault();
  event.stopPropagation();
}

function handleCodeBlockCopyClick(event) {
  const button = codeCopyButtonFromTarget(event.target);
  if (!button) return;
  logCodeCopySelection("click-before", button);
  event.preventDefault();
  event.stopPropagation();
  copyCodeBlockToClipboard(button);
  logCodeCopySelection("click-after", button);
}

function notifyPreviewTextFocus(active) {
  if (previewTextFocusActive === active) return;
  previewTextFocusActive = active;
  window.__KWRY__.notify(methods.previewTextFocusChanged, { active: active });
}

function updatePreviewTextFocus() {
  notifyPreviewTextFocus(isPreviewTextInput(document.activeElement));
}

function nativeTextEditingCommandForEvent(event) {
  if (!event.ctrlKey || event.metaKey || event.altKey || event.shiftKey) return null;
  const target = event.target;
  if (!isPreviewTextInput(target)) return null;

  const key = String(event.key || "").toLowerCase();
  if (key === "c") return "copy";
  if (key === "v") return "paste";
  if (key === "x") return "cut";
  return null;
}

function forwardNativeTextEditingShortcut(event) {
  const command = nativeTextEditingCommandForEvent(event);
  if (!command) return;

  event.preventDefault();
  event.stopPropagation();
  notifyPreviewTextFocus(true);
  window.__KWRY__.notify(methods.nativeTextEditingCommand, { command: command });
}

function dismissNativeMetadataMatches() {
  window.__KWRY__.notify(methods.dismissMetadataMatches);
}

function bindItemAction(selector, method) {
  app.querySelectorAll(selector).forEach(function(button) {
    button.addEventListener("click", function(event) {
      event.stopPropagation();
      window.__KWRY__.notify(method, {
        itemIndex: Number(button.dataset.itemIndex)
      });
    });
  });
}

function bindStepItemAction(selector, method) {
  app.querySelectorAll(selector).forEach(function(button) {
    button.addEventListener("click", function(event) {
      event.stopPropagation();
      window.__KWRY__.notify(method, {
        index: Number(button.dataset.stepIndex),
        itemIndex: Number(button.dataset.itemIndex)
      });
    });
  });
}

function overflowTooltipTarget(target) {
  const element = target && target.nodeType === Node.ELEMENT_NODE
    ? target
    : target && target.parentElement;
  if (!element || !element.closest) return null;
  // Header-date: hovering the truncated date text reroutes the tooltip to
  // the sibling icon, so there is exactly one tooltip anchor per row.
  const dateText = element.closest(".header-date-text");
  if (dateText) {
    const row = dateText.closest(".header-date");
    const iconSibling = row ? row.querySelector(".header-date-icon") : null;
    const overflows = dateText.scrollWidth > dateText.clientWidth + 1 || dateText.scrollHeight > dateText.clientHeight + 1;
    return overflows ? iconSibling : null;
  }
  const direct = element.closest("[data-tooltip], [data-overflow-tooltip]");
  if (direct) return direct;
  const owner = element.closest(".choice-button, .resource-link, .step-tool");
  return owner && owner.querySelector ? owner.querySelector("[data-overflow-tooltip]") : null;
}

function isOverflowTooltipNeeded(target) {
  if (target && target.hasAttribute && target.hasAttribute("data-tooltip")) return true;
  return target && (target.scrollWidth > target.clientWidth + 1 || target.scrollHeight > target.clientHeight + 1);
}

function positionOverflowTooltip(target) {
  if (!overflowTooltip || !target) return;
  let text = target.dataset.tooltip || target.dataset.overflowTooltip || "";
  // Special-case: header-date icon shows the short label by default, but the
  // full "Created <date>" when the sibling text is truncated with ellipsis.
  if (target.classList && target.classList.contains("header-date-icon") && target.dataset.tooltipOverflow) {
    const parent = target.parentElement;
    const textNode = parent ? parent.querySelector(".header-date-text") : null;
    const overflows = textNode && (textNode.scrollWidth > textNode.clientWidth + 1 || textNode.scrollHeight > textNode.clientHeight + 1);
    if (overflows) text = target.dataset.tooltipOverflow;
  }
  if (!text || !isOverflowTooltipNeeded(target)) {
    hideOverflowTooltip();
    return;
  }

  overflowTooltip.textContent = text;
  overflowTooltip.setAttribute("aria-hidden", "false");
  overflowTooltip.classList.add("is-visible");
  overflowTooltip.style.visibility = "hidden";
  overflowTooltip.style.left = "0px";
  overflowTooltip.style.top = "0px";

  const targetRect = target.getBoundingClientRect();
  const tooltipRect = overflowTooltip.getBoundingClientRect();
  const margin = 8;
  const gap = 5;
  const left = Math.max(margin, Math.min(targetRect.left, window.innerWidth - tooltipRect.width - margin));
  const belowTop = targetRect.bottom + gap;
  const top = belowTop + tooltipRect.height + margin <= window.innerHeight
    ? belowTop
    : Math.max(margin, targetRect.top - tooltipRect.height - gap);

  overflowTooltip.style.left = Math.round(left) + "px";
  overflowTooltip.style.top = Math.round(top) + "px";
  overflowTooltip.style.visibility = "";
}

function showOverflowTooltip(target) {
  positionOverflowTooltip(target);
}

function hideOverflowTooltip() {
  if (!overflowTooltip) return;
  overflowTooltip.classList.remove("is-visible");
  overflowTooltip.setAttribute("aria-hidden", "true");
  overflowTooltip.style.visibility = "";
}

function handleOverflowTooltipPointerOver(event) {
  const target = overflowTooltipTarget(event.target);
  if (target) showOverflowTooltip(target);
}

function handleOverflowTooltipPointerOut(event) {
  const target = overflowTooltipTarget(event.target);
  if (!target) return;
  if (event.relatedTarget && target.contains(event.relatedTarget)) return;
  // Treat the whole .header-date row as a single hover region so moving
  // between the icon and the truncated text does not flicker the tooltip.
  const row = target.closest && target.closest(".header-date");
  if (row && event.relatedTarget && row.contains(event.relatedTarget)) return;
  hideOverflowTooltip();
}

function handleOverflowTooltipFocusIn(event) {
  const target = overflowTooltipTarget(event.target);
  if (target) showOverflowTooltip(target);
}

function handleOverflowTooltipFocusOut(event) {
  const target = overflowTooltipTarget(event.target);
  if (target) hideOverflowTooltip();
}

function bindChoiceButton(button) {
  button.addEventListener("click", function(event) {
    event.stopPropagation();
    if (activeChoiceDropdown && activeChoiceDropdown.button === button) {
      closeChoiceDropdown();
      return;
    }
    showChoiceDropdown(button);
  });
  button.addEventListener("keydown", function(event) {
    if (event.key === "ArrowDown" || event.key === "Enter" || event.key === " ") {
      event.preventDefault();
      showChoiceDropdown(button, event.key === "ArrowDown" ? 1 : 0);
    }
    if (event.key === "ArrowUp") {
      event.preventDefault();
      showChoiceDropdown(button, -1);
    }
  });
}

function showChoiceDropdown(button, direction) {
  closeContextMenu();
  closeMetadataPopover();
  closeChoiceDropdown();

  const field = button.dataset.choiceField;
  const options = choiceOptions(field);
  const current = button.dataset.choiceValue || state[field];
  const selectedIndex = Math.max(0, options.findIndex(function(item) { return item.value === current; }));
  const initialIndex = direction === -1 ? options.length - 1 : selectedIndex;
  const menu = document.createElement("div");
  menu.className = "choice-menu";
  menu.setAttribute("role", "listbox");
  menu.setAttribute("aria-label", field === "priority" ? "Priority" : "Status");

  options.forEach(function(item, index) {
    const option = document.createElement("button");
    option.type = "button";
    option.className = "choice-option";
    option.textContent = item.label;
    option.dataset.choiceValue = item.value;
    option.dataset.choiceIndex = String(index);
    option.setAttribute("role", "option");
    option.setAttribute("aria-selected", item.value === current ? "true" : "false");
    option.addEventListener("click", function(event) {
      event.stopPropagation();
      chooseChoice(button, item.value);
    });
    menu.appendChild(option);
  });

  menu.addEventListener("click", function(event) {
    event.stopPropagation();
  });
  menu.addEventListener("keydown", function(event) {
    handleChoiceMenuKeydown(event);
  });

  document.body.appendChild(menu);
  activeChoiceDropdown = {
    button: button,
    menu: menu,
    options: options,
    activeIndex: initialIndex
  };
  button.setAttribute("aria-expanded", "true");
  positionChoiceDropdown(button, menu);
  focusChoiceOption(initialIndex);
}

function positionChoiceDropdown(anchor, menu) {
  const rect = anchor.getBoundingClientRect();
  const margin = 4;
  const gap = 3;
  menu.style.minWidth = Math.round(rect.width) + "px";
  menu.style.maxHeight = "260px";

  const naturalHeight = menu.offsetHeight;
  const below = window.innerHeight - rect.bottom - gap - margin;
  const above = rect.top - gap - margin;
  const openUp = naturalHeight > below && above > below;
  const available = Math.max(96, Math.min(260, openUp ? above : below));
  menu.style.maxHeight = available + "px";

  const width = menu.offsetWidth;
  const height = menu.offsetHeight;
  const left = Math.max(margin, Math.min(rect.left, window.innerWidth - width - margin));
  const preferredTop = openUp ? rect.top - gap - height : rect.bottom + gap;
  const top = Math.max(margin, Math.min(preferredTop, window.innerHeight - height - margin));
  menu.style.left = Math.round(left) + "px";
  menu.style.top = Math.round(top) + "px";
  menu.dataset.placement = openUp ? "top" : "bottom";
}

function focusChoiceOption(index) {
  if (!activeChoiceDropdown) return;
  const count = activeChoiceDropdown.options.length;
  const nextIndex = Math.max(0, Math.min(count - 1, index));
  activeChoiceDropdown.activeIndex = nextIndex;
  const options = activeChoiceDropdown.menu.querySelectorAll(".choice-option");
  options.forEach(function(option, optionIndex) {
    option.classList.toggle("is-active", optionIndex === nextIndex);
  });
  const target = options[nextIndex];
  if (target) target.focus({ preventScroll: true });
}

function handleChoiceMenuKeydown(event) {
  if (!activeChoiceDropdown) return;
  if (event.key === "Escape") {
    event.preventDefault();
    const button = activeChoiceDropdown.button;
    closeChoiceDropdown();
    button.focus({ preventScroll: true });
    return;
  }
  if (event.key === "Tab") {
    closeChoiceDropdown();
    return;
  }
  if (event.key === "ArrowDown") {
    event.preventDefault();
    focusChoiceOption(activeChoiceDropdown.activeIndex + 1);
    return;
  }
  if (event.key === "ArrowUp") {
    event.preventDefault();
    focusChoiceOption(activeChoiceDropdown.activeIndex - 1);
    return;
  }
  if (event.key === "Home") {
    event.preventDefault();
    focusChoiceOption(0);
    return;
  }
  if (event.key === "End") {
    event.preventDefault();
    focusChoiceOption(activeChoiceDropdown.options.length - 1);
    return;
  }
  if (event.key === "Enter" || event.key === " ") {
    event.preventDefault();
    const option = activeChoiceDropdown.options[activeChoiceDropdown.activeIndex];
    if (option) chooseChoice(activeChoiceDropdown.button, option.value);
  }
}

function chooseChoice(button, value) {
  const field = button.dataset.choiceField;
  const label = choiceLabel(field, value);
  const previous = button.dataset.choiceValue || state[field];
  closeChoiceDropdown();
  if (previous === value) {
    button.focus({ preventScroll: true });
    return;
  }
  state[field] = value;
  button.dataset.choiceValue = value;
  const labelEl = button.querySelector(".choice-button-label");
  if (labelEl) {
    labelEl.textContent = label;
    labelEl.dataset.overflowTooltip = label;
  }
  button.focus({ preventScroll: true });
  if (field === "runResult") {
    window.__KWRY__.notify(methods.setRunResult, { result: value });
  } else {
    window.__KWRY__.notify(methods.fieldChanged, { field: field, value: value });
  }
}

function closeChoiceDropdown() {
  if (activeChoiceDropdown) {
    activeChoiceDropdown.button.setAttribute("aria-expanded", "false");
    if (activeChoiceDropdown.menu.parentNode) {
      activeChoiceDropdown.menu.parentNode.removeChild(activeChoiceDropdown.menu);
    }
  }
  activeChoiceDropdown = null;
}

function bindLiveCommit(el, key, method, paramsFactory) {
  let composing = false;
  lastSent[key] = JSON.stringify(paramsFactory());

  function commitNow() {
    if (composing) return;
    notifyChanged(key, method, paramsFactory());
  }

  if (el.tagName === "SELECT") {
    el.addEventListener("change", commitNow);
    return;
  }

  el.addEventListener("compositionstart", function() { composing = true; });
  el.addEventListener("compositionend", function() {
    composing = false;
    commitNow();
  });
  el.addEventListener("input", function() {
    commitNow();
  });
  el.addEventListener("change", commitNow);
  el.addEventListener("blur", function() {
    commitNow();
  });
  if (el.dataset.field === "title") {
    el.addEventListener("keydown", function(event) {
      if (event.key === "Enter") {
        event.preventDefault();
        focusAdjacentTextField(el, 1);
      }
    });
  }
}

function fieldValue(el) {
  const value = editableValue(el);
  return el.dataset.field === "title" ? value.replace(/\s*\n\s*/g, " ") : value;
}

function editableValue(el) {
  if (el && el.matches && el.matches("[data-markdown-editor]")) return serializeMarkdownEditor(el);
  return el.value || "";
}

function resizeTextarea(el) {
  el.style.height = "auto";
  const borderBoxAdjustment = el.offsetHeight - el.clientHeight;
  const nextHeight = el.scrollHeight + borderBoxAdjustment;
  const cappedHeight = el.classList.contains("title-input") ? Math.min(nextHeight, titleMaxHeight()) : nextHeight;
  el.style.height = cappedHeight + "px";
}

function resizeAllTextareas() {
  app.querySelectorAll("textarea").forEach(resizeTextarea);
}

function scheduleTextareaResize() {
  if (textareaResizeFrame) cancelAnimationFrame(textareaResizeFrame);
  textareaResizeFrame = requestAnimationFrame(function() {
    textareaResizeFrame = 0;
    resizeAllTextareas();
    requestAnimationFrame(resizeAllTextareas);
  });
}

function maxDocumentScrollY() {
  return Math.max(0, document.documentElement.scrollHeight - window.innerHeight);
}

function updateScrollIndicator() {
  if (!scrollIndicator || !scrollIndicatorThumb) return false;
  const maxScroll = maxDocumentScrollY();
  if (maxScroll <= 0) {
    scrollIndicator.classList.remove("is-visible");
    return false;
  }
  const trackHeight = scrollIndicator.getBoundingClientRect().height;
  const documentHeight = document.documentElement.scrollHeight;
  const thumbHeight = Math.max(32, Math.round(trackHeight * window.innerHeight / documentHeight));
  const travel = Math.max(0, trackHeight - thumbHeight);
  const thumbTop = Math.round(travel * previewScrollFraction());
  scrollIndicatorThumb.style.height = thumbHeight + "px";
  scrollIndicatorThumb.style.transform = "translateY(" + thumbTop + "px)";
  return true;
}

function showScrollIndicator() {
  if (!updateScrollIndicator()) return;
  scrollIndicator.classList.add("is-visible");
  if (scrollIndicatorHideTimer) window.clearTimeout(scrollIndicatorHideTimer);
  scrollIndicatorHideTimer = window.setTimeout(function() {
    scrollIndicatorHideTimer = 0;
    scrollIndicator.classList.remove("is-visible");
  }, 650);
}

function suppressPreviewScrollNotify() {
  suppressPreviewScrollUntil = Date.now() + SCROLL_SYNC_SUPPRESS_MS;
}

function previewScrollFraction() {
  const maxScroll = maxDocumentScrollY();
  if (maxScroll <= 0) return 0;
  return Math.max(0, Math.min(1, window.scrollY / maxScroll));
}

function notifyPreviewScrolled() {
  previewScrollFrame = 0;
  if (Date.now() < suppressPreviewScrollUntil) return;
  window.__KWRY__.notify(methods.previewScrolled, { fraction: previewScrollFraction() });
}

function schedulePreviewScrolled(event) {
  if (!isDocumentScrollEvent(event)) return;
  if (previewScrollFrame) return;
  previewScrollFrame = requestAnimationFrame(notifyPreviewScrolled);
}

function isDocumentScrollEvent(event) {
  const target = event.target;
  return target === document ||
    target === window ||
    target === document.documentElement ||
    target === document.body;
}

function scrollToFraction(params) {
  const fraction = Number(params && params.fraction);
  if (!Number.isFinite(fraction)) return;
  const clamped = Math.max(0, Math.min(1, fraction));
  suppressPreviewScrollNotify();
  window.scrollTo(0, Math.round(clamped * maxDocumentScrollY()));
  showScrollIndicator();
  requestAnimationFrame(function() {
    suppressPreviewScrollNotify();
    window.scrollTo(0, Math.round(clamped * maxDocumentScrollY()));
    showScrollIndicator();
  });
}

function restoreRenderScroll(scrollY) {
  requestAnimationFrame(function() {
    const target = Math.min(scrollY, maxDocumentScrollY());
    suppressPreviewScrollNotify();
    window.scrollTo(0, target);
    showScrollIndicator();
    requestAnimationFrame(function() {
      suppressPreviewScrollNotify();
      window.scrollTo(0, Math.min(target, maxDocumentScrollY()));
      showScrollIndicator();
    });
  });
}

function restorePendingAddStepFocus() {
  if (pendingFocusStepIndex === null) return;
  const stepIndex = pendingFocusStepIndex;
  const scrollY = pendingScrollY;
  pendingFocusStepIndex = null;
  pendingScrollY = null;
  requestAnimationFrame(function() {
    if (scrollY !== null) {
      suppressPreviewScrollNotify();
      window.scrollTo(0, scrollY);
      showScrollIndicator();
    }
    const editor = app.querySelector('[data-markdown-editor][data-step-field="action"][data-step-index="' + stepIndex + '"]');
    if (editor) {
      focusMarkdownEditor(editor);
    } else {
      const textarea = app.querySelector('textarea[data-step-field="action"][data-step-index="' + stepIndex + '"]');
      if (!textarea) return;
      resizeTextarea(textarea);
      textarea.focus({ preventScroll: true });
      textarea.setSelectionRange(textarea.value.length, textarea.value.length);
    }
    if (scrollY !== null) {
      suppressPreviewScrollNotify();
      window.scrollTo(0, scrollY);
      showScrollIndicator();
    }
  });
}

function titleMaxHeight() {
  return window.matchMedia("(max-width: 560px)").matches ? 44 : 48;
}

function notifyChanged(key, method, params) {
  const payload = JSON.stringify(params);
  if (lastSent[key] === payload) return;
  lastSent[key] = payload;
  window.__KWRY__.notify(method, params);
}

function showContextMenu(event, items) {
  event.preventDefault();
  event.stopPropagation();
  closeContextMenu();
  closeMetadataPopover();
  closeChoiceDropdown();
  const menu = document.createElement("div");
  menu.className = "context-menu";
  menu.setAttribute("role", "menu");
  items.forEach(function(item) {
    const button = document.createElement("button");
    button.type = "button";
    button.textContent = item.label;
    button.setAttribute("role", "menuitem");
    if (item.danger) button.className = "danger";
    button.addEventListener("click", function(innerEvent) {
      innerEvent.stopPropagation();
      closeContextMenu();
      item.action();
    });
    menu.appendChild(button);
  });
  document.body.appendChild(menu);
  const rect = menu.getBoundingClientRect();
  const left = Math.max(4, Math.min(event.clientX, window.innerWidth - rect.width - 4));
  const top = Math.max(4, Math.min(event.clientY, window.innerHeight - rect.height - 4));
  menu.style.left = left + "px";
  menu.style.top = top + "px";
  activeContextMenu = menu;
  const first = menu.querySelector("button");
  if (first) first.focus({ preventScroll: true });
}

function closeContextMenu() {
  if (activeContextMenu && activeContextMenu.parentNode) {
    activeContextMenu.parentNode.removeChild(activeContextMenu);
  }
  activeContextMenu = null;
}

function showMetadataPopover(button) {
  closeContextMenu();
  closeMetadataPopover();
  closeChoiceDropdown();

  const field = button.dataset.addMetadata;
  const noun = field === "tags" ? "tag" : "environment";
  const popover = document.createElement("div");
  popover.className = "metadata-popover";
  popover.setAttribute("role", "dialog");

  const input = document.createElement("input");
  input.type = "text";
  input.placeholder = "Add " + noun;
  input.setAttribute("aria-label", "Add " + noun);
  popover.appendChild(input);

  popover.addEventListener("click", function(event) {
    event.stopPropagation();
  });
  input.addEventListener("keydown", function(event) {
    if (event.key === "Escape") {
      closeMetadataPopover();
      button.focus({ preventScroll: true });
      return;
    }
    if (event.key === "Enter") {
      const value = input.value.trim();
      if (!value) return;
      closeMetadataPopover();
      window.__KWRY__.notify(methods.addMetadata, { field: field, value: value });
    }
  });

  document.body.appendChild(popover);
  activeMetadataPopover = popover;
  positionMetadataPopover(button, popover);
  requestAnimationFrame(function() {
    input.focus({ preventScroll: true });
  });
}

function positionMetadataPopover(anchor, popover) {
  const rect = anchor.getBoundingClientRect();
  const margin = 4;
  const width = popover.offsetWidth;
  const height = popover.offsetHeight;
  const left = Math.max(margin, Math.min(rect.right - width, window.innerWidth - width - margin));
  const top = Math.max(margin, Math.min(rect.bottom + margin, window.innerHeight - height - margin));
  popover.style.left = left + "px";
  popover.style.top = top + "px";
}

function closeMetadataPopover() {
  if (activeMetadataPopover && activeMetadataPopover.parentNode) {
    activeMetadataPopover.parentNode.removeChild(activeMetadataPopover);
  }
  activeMetadataPopover = null;
}

function bindStepDragAndDrop() {
  app.querySelectorAll("[data-drag-step]").forEach(function(handle) {
    handle.addEventListener("dragstart", function(event) {
      draggedStepIndex = Number(handle.dataset.dragStep);
      event.dataTransfer.effectAllowed = "move";
      event.dataTransfer.setData("text/plain", String(draggedStepIndex));
      const step = handle.closest(".step");
      if (step) {
        step.classList.add("dragging");
        activeDragImage = buildDragImage(step);
        document.body.appendChild(activeDragImage);
        event.dataTransfer.setDragImage(activeDragImage, 18, 18);
      }
    });
    handle.addEventListener("dragend", function() {
      draggedStepIndex = null;
      clearDragImage();
      clearDropMarkers();
    });
  });

  app.querySelectorAll("[data-step-index]").forEach(function(step) {
    step.addEventListener("dragover", function(event) {
      if (draggedStepIndex === null) return;
      event.preventDefault();
      event.dataTransfer.dropEffect = "move";
      markDropTarget(step, event.clientY);
    });
    step.addEventListener("dragleave", function() {
      step.classList.remove("drop-before", "drop-after");
    });
    step.addEventListener("drop", function(event) {
      if (draggedStepIndex === null) return;
      event.preventDefault();
      const insertionIndex = dropInsertionIndex(step, event.clientY);
      const toIndex = finalStepIndex(draggedStepIndex, insertionIndex);
      if (toIndex !== draggedStepIndex) {
        window.__KWRY__.notify(methods.reorderStep, {
          fromIndex: draggedStepIndex,
          toIndex: toIndex
        });
      }
      draggedStepIndex = null;
      clearDragImage();
      clearDropMarkers();
    });
  });
}

function buildDragImage(step) {
  const clone = step.cloneNode(true);
  clone.classList.remove("dragging", "drop-before", "drop-after");
  clone.classList.add("drag-image");
  clone.querySelectorAll("textarea").forEach(function(area) {
    area.setAttribute("readonly", "true");
  });
  return clone;
}

function clearDragImage() {
  if (activeDragImage && activeDragImage.parentNode) {
    activeDragImage.parentNode.removeChild(activeDragImage);
  }
  activeDragImage = null;
}

function markDropTarget(step, clientY) {
  clearDropMarkers();
  const index = Number(step.dataset.stepIndex);
  const insertionIndex = dropInsertionIndex(step, clientY);
  step.classList.add(insertionIndex > index ? "drop-after" : "drop-before");
}

function dropInsertionIndex(step, clientY) {
  const index = Number(step.dataset.stepIndex);
  const rect = step.getBoundingClientRect();
  return clientY > rect.top + rect.height / 2 ? index + 1 : index;
}

function finalStepIndex(fromIndex, insertionIndex) {
  const count = (state.steps || []).length;
  const adjusted = fromIndex < insertionIndex ? insertionIndex - 1 : insertionIndex;
  return Math.max(0, Math.min(count - 1, adjusted));
}

function clearDropMarkers() {
  app.querySelectorAll(".step").forEach(function(step) {
    step.classList.remove("dragging", "drop-before", "drop-after");
  });
}

document.addEventListener("click", function() {
  closeContextMenu();
  closeMetadataPopover();
  closeChoiceDropdown();
  dismissNativeMetadataMatches();
});
document.addEventListener("keydown", function(event) {
  if (event.key === "Escape") {
    closeContextMenu();
    closeMetadataPopover();
    closeChoiceDropdown();
    dismissNativeMetadataMatches();
  }
});
window.addEventListener("scroll", function(event) {
  closeContextMenu();
  closeMetadataPopover();
  closeChoiceDropdown();
  hideOverflowTooltip();
  if (isDocumentScrollEvent(event)) showScrollIndicator();
  schedulePreviewScrolled(event);
}, true);
window.addEventListener("resize", function() {
  hideOverflowTooltip();
  scheduleTextareaResize();
  updateScrollIndicator();
  if (activeChoiceDropdown) positionChoiceDropdown(activeChoiceDropdown.button, activeChoiceDropdown.menu);
});
document.addEventListener("focusin", updatePreviewTextFocus);
document.addEventListener("focusout", function() {
  requestAnimationFrame(updatePreviewTextFocus);
});
document.addEventListener("keydown", forwardNativeTextEditingShortcut, true);
// JBR forwards AWT KEY_TYPED events whose `keyChar` is CHAR_UNDEFINED ('￿')
// for arrow keys / non-text keystrokes. WKWebView treats them as text input
// and inserts the codepoint into the focused editor; the font then paints it
// as the `.notdef` glyph (a crossed square). Drop these at beforeinput.
document.addEventListener("beforeinput", function(event) {
  const data = event.data;
  if (typeof data !== "string" || data.length === 0) return;
  for (let i = 0; i < data.length; i++) {
    const cp = data.charCodeAt(i);
    // Reject C0 controls (except \t and \n), C1 controls, BMP private-use,
    // and the non-character range ￰-￿.
    const isControl = (cp < 0x20 && cp !== 0x09 && cp !== 0x0A) || (cp >= 0x7F && cp <= 0x9F);
    const isPrivateUse = cp >= 0xE000 && cp <= 0xF8FF;
    const isNonCharacter = cp >= 0xFFF0 && cp <= 0xFFFF;
    if (isControl || isPrivateUse || isNonCharacter) {
      event.preventDefault();
      return;
    }
  }
}, true);
document.addEventListener("copy", handlePreviewTextCopy, true);
document.addEventListener("paste", handlePreviewTextPaste, true);
document.addEventListener("mousedown", handleCodeBlockCopyMouseDown, true);
document.addEventListener("click", handleCodeBlockCopyClick, true);
document.addEventListener("pointerover", handleOverflowTooltipPointerOver, true);
document.addEventListener("pointerout", handleOverflowTooltipPointerOut, true);
document.addEventListener("focusin", handleOverflowTooltipFocusIn, true);
document.addEventListener("focusout", handleOverflowTooltipFocusOut, true);
document.addEventListener("click", hideOverflowTooltip, true);
window.addEventListener("blur", function() {
  notifyPreviewTextFocus(false);
});

const speqaLinkPopover = bindLinkPopover();
const speqaSelectionToolbar = bindSelectionToolbar(speqaLinkPopover);
function hideAllMarkdownPopovers() {
  speqaLinkPopover.hide();
  speqaSelectionToolbar.hide();
}
window.addEventListener("scroll", hideAllMarkdownPopovers, true);
window.addEventListener("resize", hideAllMarkdownPopovers);
document.addEventListener("keydown", function(event) {
  if (event.key === "Escape") hideAllMarkdownPopovers();
});
window.__KWRY__.subscribe(methods.snapshot, function(snapshot) {
  hideAllMarkdownPopovers();
  render(snapshot);
});
window.__KWRY__.subscribe(methods.scrollToFraction, scrollToFraction);
window.__KWRY__.subscribe(methods.pastePreviewText, pastePreviewText);
window.addEventListener("DOMContentLoaded", function() {
  window.__KWRY__.notify(methods.ready);
});
