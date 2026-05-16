package io.github.barsia.speqa.editor.webview

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test

class SpeqaWebViewPreviewPanelTest {
  @Test
  fun `new preview step is blank so focus lands in an empty action field`() {
    val step = newPreviewStep()

    assertEquals("", step.action)
    assertNull(step.expected)
    assertEquals(emptyList<String>(), step.tickets)
    assertEquals(emptyList<Any>(), step.links)
    assertEquals(emptyList<Any>(), step.attachments)
  }

  @Test
  fun `webview preview is wired into editor scroll sync`() {
    val editorSource = source("src/main/kotlin/io/github/barsia/speqa/editor/SpeqaPreviewEditor.kt")

    assertTrue(editorSource.contains("scrollSync.attachFractionalPanel(webViewPreviewPanel::scrollToFraction)"))
    assertTrue(editorSource.contains("scrollSync.syncPanelToEditor()"))
    assertTrue(editorSource.contains("onPreviewScrolled = { fraction ->\n            scrollSync.onPanelScrolled(fraction)\n        }"))
  }

  @Test
  fun `webview panel exposes bidirectional scroll sync bridge`() {
    val panelSource = source("src/main/kotlin/io/github/barsia/speqa/editor/webview/SpeqaWebViewPreviewPanel.kt")

    assertTrue(panelSource.contains("private val onPreviewScrolled: (Float) -> Unit = {},"))
    assertTrue(panelSource.contains("private val scrollState = SpeqaWebViewScrollState()"))
    assertTrue(panelSource.contains("fun scrollToFraction(fraction: Float)"))
    assertTrue(panelSource.contains("val clamped = scrollState.requestScroll(fraction) ?: return"))
    assertTrue(panelSource.contains("SCROLL_TO_FRACTION_METHOD"))
    assertTrue(panelSource.contains("PREVIEW_SCROLLED_METHOD"))
    assertTrue(panelSource.contains("onPreviewScrolled(fraction)"))
  }

  @Test
  fun `editor driven snapshots do not restore native preview text focus`() {
    val panelSource = source("src/main/kotlin/io/github/barsia/speqa/editor/webview/SpeqaWebViewPreviewPanel.kt")

    assertTrue(panelSource.contains("host?.setNativeTextInputFocusActive(false, force = true)"))
    assertTrue(panelSource.contains("publishSnapshot(restorePreviewTextFocus = false)"))
    assertTrue(panelSource.contains("private fun publishSnapshot(restorePreviewTextFocus: Boolean = true)"))
    assertTrue(panelSource.contains("restorePreviewTextFocus = restorePreviewTextFocus"))
  }

  @Test
  fun `webview panel writes normalized preview copies to the native clipboard`() {
    val panelSource = source("src/main/kotlin/io/github/barsia/speqa/editor/webview/SpeqaWebViewPreviewPanel.kt")

    assertTrue(panelSource.contains("NORMALIZED_PREVIEW_COPY_METHOD"))
    assertTrue(panelSource.contains("bus.subscribe(scope, NORMALIZED_PREVIEW_COPY_METHOD)"))
    assertTrue(panelSource.contains("val text = params?.jsonObject?.get(\"text\")?.jsonPrimitive?.contentOrNull ?: return@subscribe"))
    assertTrue(panelSource.contains("writeSystemClipboardText(text)"))
    assertTrue(panelSource.contains("StringSelection(text)"))
    assertTrue(panelSource.contains("Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, null)"))
  }

  @Test
  fun `webview panel logs code copy diagnostics from the preview`() {
    val panelSource = source("src/main/kotlin/io/github/barsia/speqa/editor/webview/SpeqaWebViewPreviewPanel.kt")

    assertTrue(panelSource.contains("CODE_COPY_DIAGNOSTIC_METHOD"))
    assertTrue(panelSource.contains("private const val CODE_COPY_DIAGNOSTIC_METHOD = \"speqa/testCase/codeCopyDiagnostic\""))
    assertTrue(panelSource.contains("bus.subscribe(scope, CODE_COPY_DIAGNOSTIC_METHOD)"))
    assertTrue(panelSource.contains("LOG.info(\"Code block copy diagnostic: ${'$'}{params ?: \"{}\"}\")"))
  }

  @Test
  fun `webview panel writes code block copy requests to the native clipboard`() {
    val panelSource = source("src/main/kotlin/io/github/barsia/speqa/editor/webview/SpeqaWebViewPreviewPanel.kt")

    assertTrue(panelSource.contains("CODE_BLOCK_COPY_REQUESTED_METHOD"))
    assertTrue(panelSource.contains("private const val CODE_BLOCK_COPY_REQUESTED_METHOD = \"speqa/testCase/codeBlockCopyRequested\""))
    assertTrue(panelSource.contains("bus.subscribe(scope, CODE_BLOCK_COPY_REQUESTED_METHOD)"))
    assertTrue(panelSource.contains("val text = params?.jsonObject?.get(\"text\")?.jsonPrimitive?.contentOrNull ?: return@subscribe"))
    assertTrue(panelSource.contains("writeSystemClipboardText(text)"))
    assertTrue(panelSource.contains("host?.setNativeTextInputFocusActive(true)"))
  }

  @Test
  fun `webview panel returns native clipboard text for exact preview paste`() {
    val panelSource = source("src/main/kotlin/io/github/barsia/speqa/editor/webview/SpeqaWebViewPreviewPanel.kt")

    assertTrue(panelSource.contains("PREVIEW_PASTE_REQUESTED_METHOD"))
    assertTrue(panelSource.contains("PASTE_PREVIEW_TEXT_METHOD"))
    assertTrue(panelSource.contains("bus.subscribe(scope, PREVIEW_PASTE_REQUESTED_METHOD)"))
    assertTrue(panelSource.contains("val text = readSystemClipboardText()"))
    assertTrue(panelSource.contains("bus.publishRaw(\n          PASTE_PREVIEW_TEXT_METHOD,"))
    assertTrue(panelSource.contains("Toolkit.getDefaultToolkit().systemClipboard.getData(DataFlavor.stringFlavor) as? String"))
  }

  @Test
  fun `webview html bootstraps current dark theme before the first snapshot`() {
    val panelSource = source("src/main/kotlin/io/github/barsia/speqa/editor/webview/SpeqaWebViewPreviewPanel.kt")
    val inlinedHtml = SpeqaWebViewPreviewSupport.buildInlinedPreviewHtml("dark")

    assertTrue(panelSource.contains("SpeqaWebViewPreviewSupport.buildInlinedPreviewHtml(currentTheme())"))
    assertTrue(panelSource.contains("if (!scrollState.isReady) return"))
    assertTrue(!panelSource.contains("RENDERED_METHOD"))
    assertTrue(!inlinedHtml.contains("speqa/testCase/rendered"))
    assertTrue(inlinedHtml.contains("document.documentElement.classList.toggle(\"dark\", state.theme === \"dark\");"))
    assertTrue(inlinedHtml.contains("document.body.classList.toggle(\"dark\", state.theme === \"dark\");"))
    // Initial theme class is baked into the document root before the JS runs.
    assertTrue(inlinedHtml.contains("<html lang=\"en\" class=\"dark\">"))
    assertTrue(inlinedHtml.contains("<body class=\"dark\">"))
  }

  @Test
  fun `webview host uses editor background without waiting for js render acknowledgements`() {
    val panelSource = source("src/main/kotlin/io/github/barsia/speqa/editor/webview/SpeqaWebViewPreviewPanel.kt")
    val hostSource = source("src/main/kotlin/io/github/barsia/speqa/webview/SwingWebViewHostPanel.kt")
    val peerSource = source("src/main/kotlin/io/github/barsia/speqa/webview/internal/host/NativeWebViewHostPeer.kt")
    val macPeerSource = source("src/main/kotlin/io/github/barsia/speqa/webview/internal/mac/MacNativeWebViewHostPeer.kt")
    val winPeerSource = source("src/main/kotlin/io/github/barsia/speqa/webview/internal/windows/WinNativeWebViewHostPeer.kt")

    assertTrue(panelSource.contains("background = previewBackground()"))
    assertTrue(panelSource.contains("isOpaque = true"))
    assertTrue(panelSource.contains("EditorColorsManager.getInstance().globalScheme.defaultBackground"))
    assertTrue(!hostSource.contains("contentReady"))
    assertTrue(!hostSource.contains("setContentReady"))
    assertTrue(!peerSource.contains("updateContentReadiness"))
    assertTrue(!macPeerSource.contains("contentReady"))
    assertTrue(macPeerSource.contains("facade.setHidden(hostHidden || !positiveFrameApplied || frameTemporarilyInvalid)"))
    assertTrue(!winPeerSource.contains("contentReady"))
  }

  @Test
  fun `metadata match popup waits for registry and can be dismissed from webview clicks`() {
    val panelSource = source("src/main/kotlin/io/github/barsia/speqa/editor/webview/SpeqaWebViewPreviewPanel.kt")
    val registrySource = source("src/main/kotlin/io/github/barsia/speqa/registry/SpeqaTagRegistry.kt")
    val popupSource = source("src/main/kotlin/io/github/barsia/speqa/editor/ui/chips/MetadataPickerPopup.kt")

    assertTrue(panelSource.contains("private var activeMetadataMatchesPopup: com.intellij.openapi.ui.popup.JBPopup? = null"))
    assertTrue(panelSource.contains("private var metadataFilterRequestId = 0"))
    assertTrue(panelSource.contains("registry.whenInitialized {"))
    assertTrue(panelSource.contains("dismissMetadataMatchesPopup(invalidatePendingRequest = false)"))
    assertTrue(panelSource.contains("DISMISS_METADATA_MATCHES_METHOD"))
    assertTrue(registrySource.contains("fun whenInitialized(action: () -> Unit)"))
    assertTrue(popupSource.contains("): JBPopup"))
    assertTrue(popupSource.contains("return popupRef"))
    assertTrue(popupSource.contains("getCachedDocument(file)?.text"))
    assertTrue(!popupSource.contains("file.inputStream"))
  }

  @Test
  fun `scroll sync controller supports non swing fractional panels`() {
    val controllerSource = source("src/main/kotlin/io/github/barsia/speqa/editor/ScrollSyncController.kt")

    assertTrue(controllerSource.contains("fun attachFractionalPanel(scrollToFraction: (Float) -> Unit)"))
    assertTrue(controllerSource.contains("fun syncPanelToEditor()"))
    assertTrue(controllerSource.contains("fun onPanelScrolled(fraction: Float)"))
    assertTrue(controllerSource.contains("fractionalPanelSink"))
  }

  private fun source(path: String): String =
    File(System.getProperty("user.dir"), path).readText()
}
