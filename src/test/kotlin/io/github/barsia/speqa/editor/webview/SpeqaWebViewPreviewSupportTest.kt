package io.github.barsia.speqa.editor.webview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeqaWebViewPreviewSupportTest {
  @Test
  fun `new step defaults to a blank editable action with no expected result`() {
    val step = SpeqaWebViewPreviewSupport.newStep()

    assertEquals("", step.action)
    assertNull(step.expected)
    assertEquals(emptyList<String>(), step.tickets)
    assertEquals(emptyList<Any>(), step.links)
    assertEquals(emptyList<Any>(), step.attachments)
  }

  @Test
  fun `dark initial theme marks html and body before the webview first paints`() {
    val themed = SpeqaWebViewPreviewSupport.withInitialTheme(
      """<html lang="en"><head></head><body><main></main></body></html>""",
      "dark",
    )

    assertTrue(themed.contains("""<html lang="en" class="dark">"""))
    assertTrue(themed.contains("""<body class="dark">"""))
  }

  @Test
  fun `light initial theme leaves source html untouched`() {
    val html = """<html lang="en"><body></body></html>"""

    assertEquals(html, SpeqaWebViewPreviewSupport.withInitialTheme(html, "light"))
  }

  @Test
  fun `inlines bundled script body to bypass WKWebView file access restrictions`() {
    val html = """<head><script src="highlight.min.js"></script></head>"""
    val body = "var hljs = {};"

    val inlined = SpeqaWebViewPreviewSupport.withInlinedScript(html, "highlight.min.js", body)

    assertTrue(inlined.contains("<script>\nvar hljs = {};\n</script>"))
    assertTrue(!inlined.contains("""<script src="highlight.min.js"></script>"""))
  }

  @Test
  fun `inlining is a no-op when the placeholder script tag is absent`() {
    val html = """<head></head>"""

    assertEquals(html, SpeqaWebViewPreviewSupport.withInlinedScript(html, "highlight.min.js", "var x;"))
  }

  @Test
  fun `inlining escapes embedded closing script tags so HTML stays well-formed`() {
    val html = """<head><script src="lib.js"></script></head>"""
    val body = """var s = "</script>";"""

    val inlined = SpeqaWebViewPreviewSupport.withInlinedScript(html, "lib.js", body)

    assertTrue(!inlined.contains("</script>\";"))
    assertTrue(inlined.contains("""<\/script>"""))
  }

  @Test
  fun `withInlinedStylesheet replaces link tag with inline style`() {
    val html = """<head><link rel="stylesheet" href="preview.css"></head>"""
    val css = ".step { color: red; }"
    val result = SpeqaWebViewPreviewSupport.withInlinedStylesheet(html, "preview.css", css)
    assertFalse(result.contains("""<link rel="stylesheet" href="preview.css">"""))
    assertTrue(result.contains("<style>"))
    assertTrue(result.contains(".step { color: red; }"))
    assertTrue(result.contains("</style>"))
  }

  @Test
  fun `withInlinedStylesheet is a no-op when the tag is absent`() {
    val html = """<head></head>"""
    val result = SpeqaWebViewPreviewSupport.withInlinedStylesheet(html, "preview.css", ".foo {}")
    assertEquals(html, result)
  }

  @Test
  fun `withInlinedStylesheet escapes a closing style sequence inside the css`() {
    val html = """<head><link rel="stylesheet" href="preview.css"></head>"""
    val css = "/* fake closer: </style */ .step {}"
    val result = SpeqaWebViewPreviewSupport.withInlinedStylesheet(html, "preview.css", css)
    assertFalse(result.contains("</style */"))
    assertTrue(result.contains("<\\/style"))
  }

  @Test
  fun `buildInlinedPreviewHtml inlines stylesheet, script, highlight, and applies theme`() {
    // The actual webview/test-case-preview/index.html is part of the classpath. The helper
    // is expected to inline the CSS and the JS so callers receive one self-contained blob.
    val html = SpeqaWebViewPreviewSupport.buildInlinedPreviewHtml("dark")
    // CSS came from preview.css and was inlined into a <style> block, not a <link href>.
    assertFalse(html.contains("<link rel=\"stylesheet\" href=\"preview.css\">"))
    assertTrue(html.contains("--scrollbar-thumb:"))
    // JS came from preview.js, inlined as <script>, not <script src="preview.js">.
    assertFalse(html.contains("<script src=\"preview.js\"></script>"))
    assertTrue(html.contains("window.__KWRY__"))
    // highlight.min.js was inlined too (existing pre-split behaviour).
    assertFalse(html.contains("<script src=\"highlight.min.js\"></script>"))
    // Dark theme was applied to the <html> and <body> roots.
    assertTrue(html.contains("<html lang=\"en\" class=\"dark\">"))
    assertTrue(html.contains("<body class=\"dark\">"))
  }

  @Test
  fun `pending scroll state stores clamped fraction until page is ready`() {
    val state = SpeqaWebViewScrollState()

    assertNull(state.requestScroll(1.7f))
    assertEquals(1f, state.markReady())
    assertNull(state.markReady())
  }

  @Test
  fun `pending scroll state keeps only the last request before ready`() {
    val state = SpeqaWebViewScrollState()

    assertNull(state.requestScroll(0.2f))
    assertNull(state.requestScroll(0.8f))

    assertEquals(0.8f, state.markReady())
  }

  @Test
  fun `ready scroll state publishes clamped requests immediately`() {
    val state = SpeqaWebViewScrollState()
    state.markReady()

    assertEquals(0f, state.requestScroll(-0.3f))
    assertEquals(0.42f, state.requestScroll(0.42f))
    assertEquals(1f, state.requestScroll(3f))
  }
}
