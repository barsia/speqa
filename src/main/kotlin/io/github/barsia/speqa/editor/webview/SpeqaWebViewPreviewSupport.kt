package io.github.barsia.speqa.editor.webview

import io.github.barsia.speqa.model.TestStep

internal object SpeqaWebViewPreviewSupport {
  fun newStep(): TestStep = TestStep()

  fun withInitialTheme(html: String, theme: String): String {
    if (theme != "dark") return html
    return html
      .replace("<html lang=\"en\">", "<html lang=\"en\" class=\"dark\">")
      .replace("<body>", "<body class=\"dark\">")
  }

  /**
   * Replaces `<script src="…"></script>` for [scriptSrc] with an inline `<script>` block
   * holding [scriptBody]. Required because WKWebView's `loadHTMLString:baseURL:` does not
   * grant read access to file:// resources, so relative `<script src=…>` is silently ignored.
   */
  fun withInlinedScript(html: String, scriptSrc: String, scriptBody: String): String {
    val tag = "<script src=\"$scriptSrc\"></script>"
    if (!html.contains(tag)) return html
    val safeBody = scriptBody.replace("</script", "<\\/script")
    return html.replace(tag, "<script>\n$safeBody\n</script>")
  }

  /**
   * Replaces `<link rel="stylesheet" href="..">` for [href] with an inline `<style>` block
   * holding [stylesheet]. Mirrors [withInlinedScript]; required because the WebView is fed a
   * single self-contained HTML blob without a baseURL, so relative `<link href=..>` would be
   * silently ignored.
   */
  fun withInlinedStylesheet(html: String, href: String, stylesheet: String): String {
    val tag = "<link rel=\"stylesheet\" href=\"$href\">"
    if (!html.contains(tag)) return html
    val safeBody = stylesheet.replace("</style", "<\\/style")
    return html.replace(tag, "<style>\n$safeBody\n</style>")
  }

  private const val PREVIEW_RESOURCE_ROOT = "webview/test-case-preview"
  private const val HIGHLIGHT_SCRIPT_NAME = "highlight.min.js"
  private const val STYLESHEET_NAME = "preview.css"
  private const val SCRIPT_NAME = "preview.js"

  /**
   * Reads the source preview HTML, inlines `preview.css`, `preview.js`, and `highlight.min.js`,
   * then applies the [theme] class to the document root. Returns a single self-contained
   * HTML blob suitable for `WKWebView.loadHTMLString:baseURL:nil` (and the Windows / Linux
   * equivalents that share the same blob-input contract).
   */
  fun buildInlinedPreviewHtml(theme: String): String {
    val skeleton = readResource("$PREVIEW_RESOURCE_ROOT/index.html")
    val css = readResource("$PREVIEW_RESOURCE_ROOT/$STYLESHEET_NAME")
    val js = readResource("$PREVIEW_RESOURCE_ROOT/$SCRIPT_NAME")
    val highlight = readResource("$PREVIEW_RESOURCE_ROOT/$HIGHLIGHT_SCRIPT_NAME")
    var html = withInlinedStylesheet(skeleton, STYLESHEET_NAME, css)
    html = withInlinedScript(html, SCRIPT_NAME, js)
    html = withInlinedScript(html, HIGHLIGHT_SCRIPT_NAME, highlight)
    return withInitialTheme(html, theme)
  }

  private fun readResource(path: String): String {
    val stream = SpeqaWebViewPreviewSupport::class.java.classLoader.getResourceAsStream(path)
                 ?: error("SpeQA WebView preview resource is missing: $path")
    return stream.reader(Charsets.UTF_8).use { it.readText() }
  }
}

internal class SpeqaWebViewScrollState {
  var isReady: Boolean = false
    private set

  private var pendingScrollFraction: Float? = null

  fun requestScroll(fraction: Float): Float? {
    val clamped = fraction.coerceIn(0f, 1f)
    if (isReady) return clamped
    pendingScrollFraction = clamped
    return null
  }

  fun markReady(): Float? {
    isReady = true
    val pending = pendingScrollFraction
    pendingScrollFraction = null
    return pending
  }
}
