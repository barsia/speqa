package io.github.barsia.speqa.editor.webview

import com.intellij.ide.BrowserUtil
import com.intellij.ide.ui.LafManagerListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.colors.EditorColorsListener
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.JBLabel
import com.intellij.util.messages.Topic
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.StartupUiUtil
import io.github.barsia.speqa.SpeqaBundle
import io.github.barsia.speqa.editor.AttachmentSupport
import io.github.barsia.speqa.editor.resolveTestCaseHeaderMeta
import io.github.barsia.speqa.editor.ui.AddEditLinkDialog
import io.github.barsia.speqa.editor.ui.chips.MetadataKind
import io.github.barsia.speqa.editor.ui.chips.showMetadataMatches
import io.github.barsia.speqa.model.Attachment
import io.github.barsia.speqa.model.RunResult
import io.github.barsia.speqa.model.StepVerdict
import io.github.barsia.speqa.model.TestRun
import io.github.barsia.speqa.parser.PatchOperation
import io.github.barsia.speqa.registry.SpeqaTagRegistry
import io.github.barsia.speqa.settings.SpeqaSettings
import io.github.barsia.speqa.webview.SwingWebViewHostPanel
import io.github.barsia.speqa.webview.WebViewFacadeFactory
import io.github.barsia.speqa.webview.WebViewFacadeWithBus
import io.github.barsia.speqa.webview.interop.WebViewMessageBus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Point
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import javax.swing.JComponent
import javax.swing.JPanel

internal class SpeqaWebViewRunPanel(
  private val project: Project,
  private val file: VirtualFile,
  initialRun: TestRun = TestRun(),
  private val onPatch: (TestRun, PatchOperation) -> Unit,
  private val onPreviewTextFocusChanged: (Boolean) -> Unit = {},
  private val onPreviewScrolled: (Float) -> Unit = {},
) : Disposable {
  private val scope = CoroutineScope(SupervisorJob())
  private val messageBusConnection = ApplicationManager.getApplication().messageBus.connect(this)
  private val root = JPanel(BorderLayout()).apply {
    background = previewBackground()
    // Non-opaque: native WebView mask holes (for IDE balloons/popups overlapping
    // the preview) must reveal the underlying IDE backdrop, not previewBackground.
    // Otherwise a light theme paints a high-contrast halo around balloon shadows.
    isOpaque = false
  }

  private var webView: WebViewFacadeWithBus? = null
  private var host: SwingWebViewHostPanel? = null
  private var current = initialRun
  private val scrollState = SpeqaWebViewScrollState()
  private var activeMetadataMatchesPopup: com.intellij.openapi.ui.popup.JBPopup? = null
  private var metadataFilterRequestId = 0

  val component: JComponent = root

  init {
    installWebView()
    messageBusConnection.subscribe(LafManagerListener.TOPIC as Topic<LafManagerListener>, LafManagerListener {
      applyPreviewBackground()
      publishSnapshot()
    })
    messageBusConnection.subscribe(EditorColorsManager.TOPIC as Topic<EditorColorsListener>, EditorColorsListener {
      applyPreviewBackground()
      publishSnapshot()
    })
  }

  fun updateFrom(run: TestRun) {
    current = run
    host?.setNativeTextInputFocusActive(false, force = true)
    publishSnapshot(restorePreviewTextFocus = false)
  }

  fun scrollToFraction(fraction: Float) {
    val bus = webView?.bus ?: return
    val clamped = scrollState.requestScroll(fraction) ?: return
    publishScrollFraction(bus, clamped)
  }

  private fun publishScrollFraction(bus: WebViewMessageBus, fraction: Float) {
    bus.publishRaw(
      SCROLL_TO_FRACTION_METHOD,
      buildJsonObject {
        put("fraction", fraction.toDouble())
      },
    )
  }

  private fun installWebView() {
    val created = try {
      createWebView()
    }
    catch (t: Throwable) {
      LOG.warn("Failed to create SpeQA WebView preview", t)
      showUnsupportedPanel(t)
      return
    }

    webView = created
    registerHandlers(created.bus)
    val host = SwingWebViewHostPanel(scope, created.facade, onPreviewTextFocusChanged)
    this.host = host
    root.add(host, BorderLayout.CENTER)
    created.facade.loadHtml(loadPreviewHtml())
  }

  private fun createWebView(): WebViewFacadeWithBus = when {
    SystemInfo.isMac -> WebViewFacadeFactory.createMacOsFacadeWithBus(scope)
    SystemInfo.isWindows -> WebViewFacadeFactory.createWindowsFacadeWithBus(scope)
    SystemInfo.isLinux -> WebViewFacadeFactory.createLinuxFacadeWithBus(scope)
    else -> error("SpeQA WebView preview is supported only on macOS, Windows, and Linux")
  }

  private fun registerHandlers(bus: WebViewMessageBus) {
    bus.subscribe(scope, READY_METHOD) {
      ApplicationManager.getApplication().invokeLater {
        val pendingScrollFraction = scrollState.markReady()
        publishSnapshot()
        pendingScrollFraction?.let { fraction -> publishScrollFraction(bus, fraction) }
      }
    }
    bus.subscribe(scope, PREVIEW_TEXT_FOCUS_CHANGED_METHOD) { params ->
      val active = params?.jsonObject?.get("active")?.jsonPrimitive?.booleanOrNull ?: return@subscribe
      ApplicationManager.getApplication().invokeLater {
        host?.setNativeTextInputFocusActive(active)
      }
    }
    bus.subscribe(scope, PREVIEW_SCROLLED_METHOD) { params ->
      val fraction = params
        ?.jsonObject
        ?.get("fraction")
        ?.jsonPrimitive
        ?.contentOrNull
        ?.toFloatOrNull()
        ?.coerceIn(0f, 1f)
        ?: return@subscribe
      ApplicationManager.getApplication().invokeLater {
        onPreviewScrolled(fraction)
      }
    }
    bus.subscribe(scope, FIELD_CHANGED_METHOD) { params ->
      ApplicationManager.getApplication().invokeLater { handleFieldChanged(params) }
    }
    bus.subscribe(scope, LIST_CHANGED_METHOD) { params ->
      ApplicationManager.getApplication().invokeLater { handleListChanged(params) }
    }
    bus.subscribe(scope, SET_STEP_VERDICT_METHOD) { params ->
      ApplicationManager.getApplication().invokeLater { handleSetStepVerdict(params) }
    }
    bus.subscribe(scope, SET_RUN_RESULT_METHOD) { params ->
      ApplicationManager.getApplication().invokeLater { handleSetRunResult(params) }
    }
    bus.subscribe(scope, OPEN_LINK_METHOD) { params ->
      ApplicationManager.getApplication().invokeLater { openLink(params) }
    }
    bus.subscribe(scope, OPEN_TICKET_METHOD) { params ->
      ApplicationManager.getApplication().invokeLater { openTicket(params) }
    }
    bus.subscribe(scope, OPEN_ATTACHMENT_METHOD) { params ->
      ApplicationManager.getApplication().invokeLater { openAttachment(params) }
    }
    bus.subscribe(scope, ADD_LINK_METHOD) {
      ApplicationManager.getApplication().invokeLater { addLink() }
    }
    bus.subscribe(scope, ADD_ATTACHMENT_METHOD) {
      ApplicationManager.getApplication().invokeLater { addAttachment() }
    }
    bus.subscribe(scope, EDIT_LINK_METHOD) { params ->
      ApplicationManager.getApplication().invokeLater { editLink(params) }
    }
    bus.subscribe(scope, DELETE_LINK_METHOD) { params ->
      ApplicationManager.getApplication().invokeLater { deleteLink(params) }
    }
    bus.subscribe(scope, EDIT_ATTACHMENT_METHOD) { params ->
      ApplicationManager.getApplication().invokeLater { editAttachment(params) }
    }
    bus.subscribe(scope, DELETE_ATTACHMENT_METHOD) { params ->
      ApplicationManager.getApplication().invokeLater { deleteAttachment(params) }
    }
    bus.subscribe(scope, ADD_METADATA_METHOD) { params ->
      ApplicationManager.getApplication().invokeLater { addMetadata(params) }
    }
    bus.subscribe(scope, EDIT_METADATA_METHOD) { params ->
      ApplicationManager.getApplication().invokeLater { editMetadata(params) }
    }
    bus.subscribe(scope, DELETE_METADATA_METHOD) { params ->
      ApplicationManager.getApplication().invokeLater { deleteMetadata(params) }
    }
    bus.subscribe(scope, FILTER_METADATA_METHOD) { params ->
      ApplicationManager.getApplication().invokeLater { filterMetadata(params) }
    }
    bus.subscribe(scope, DISMISS_METADATA_MATCHES_METHOD) {
      ApplicationManager.getApplication().invokeLater { dismissMetadataMatchesPopup() }
    }
    bus.subscribe(scope, NATIVE_TEXT_EDITING_COMMAND_METHOD) { params ->
      val command = params?.jsonObject?.get("command")?.jsonPrimitive?.contentOrNull ?: return@subscribe
      ApplicationManager.getApplication().invokeLater {
        host?.setNativeTextInputFocusActive(true)
        host?.dispatchNativeTextEditingCommand(command)
      }
    }
    bus.subscribe(scope, NORMALIZED_PREVIEW_COPY_METHOD) { params ->
      val text = params?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull ?: return@subscribe
      ApplicationManager.getApplication().invokeLater {
        writeSystemClipboardText(text)
        host?.setNativeTextInputFocusActive(true)
      }
    }
    bus.subscribe(scope, CODE_COPY_DIAGNOSTIC_METHOD) { params ->
      LOG.info("Code block copy diagnostic: ${params ?: "{}"}")
    }
    bus.subscribe(scope, CODE_BLOCK_COPY_REQUESTED_METHOD) { params ->
      val text = params?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull ?: return@subscribe
      ApplicationManager.getApplication().invokeLater {
        writeSystemClipboardText(text)
        host?.setNativeTextInputFocusActive(true)
      }
    }
    bus.subscribe(scope, PREVIEW_PASTE_REQUESTED_METHOD) {
      ApplicationManager.getApplication().invokeLater {
        val text = readSystemClipboardText()
        host?.setNativeTextInputFocusActive(true)
        bus.publishRaw(
          PASTE_PREVIEW_TEXT_METHOD,
          buildJsonObject {
            put("text", text)
          },
        )
      }
    }
  }

  private fun readSystemClipboardText(): String {
    return try {
      Toolkit.getDefaultToolkit().systemClipboard.getData(DataFlavor.stringFlavor) as? String ?: ""
    }
    catch (t: Throwable) {
      LOG.warn("Failed to read system clipboard for preview paste", t)
      ""
    }
  }

  private fun writeSystemClipboardText(text: String) {
    try {
      val selection = StringSelection(text)
      Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, null)
    }
    catch (t: Throwable) {
      LOG.warn("Failed to write normalized preview copy to system clipboard", t)
    }
  }

  private fun handleFieldChanged(params: JsonElement?) {
    val obj = params?.jsonObject ?: return
    val field = obj["field"]?.jsonPrimitive?.contentOrNull ?: return
    val value = obj["value"]?.jsonPrimitive?.contentOrNull.orEmpty()
    when (field) {
      "runner" -> emit(current.copy(runner = value), PatchOperation.SetRunner(value))
    }
  }

  private fun handleListChanged(params: JsonElement?) {
    val obj = params?.jsonObject ?: return
    val field = obj["field"]?.jsonPrimitive?.contentOrNull ?: return
    val value = obj["value"]?.jsonPrimitive?.contentOrNull.orEmpty()
    val values = splitCommaSeparated(value)
    when (field) {
      "environment" -> emit(current.copy(environment = values), PatchOperation.SetRunEnvironment(values))
      "tags" -> emit(current.copy(tags = values), PatchOperation.SetRunTags(values))
    }
  }

  private fun handleSetStepVerdict(params: JsonElement?) {
    val obj = params?.jsonObject ?: return
    val index = obj["index"]?.jsonPrimitive?.intOrNull ?: return
    val verdictLabel = obj["verdict"]?.jsonPrimitive?.contentOrNull.orEmpty()
    val verdict = if (verdictLabel.equals("none", ignoreCase = true)) StepVerdict.NONE
    else StepVerdict.fromString(verdictLabel)
    val change = SpeqaWebViewRunReducer.stepVerdictChanged(current, index, verdict) ?: return
    emit(change.run, change.primary, publishSnapshot = true)
    change.followups.forEach { onPatch(change.run, it) }
  }

  private fun handleSetRunResult(params: JsonElement?) {
    val obj = params?.jsonObject ?: return
    val resultLabel = obj["result"]?.jsonPrimitive?.contentOrNull.orEmpty()
    val result = RunResult.fromString(resultLabel)
    val change = SpeqaWebViewRunReducer.runResultPicked(current, result)
    emit(change.run, change.primary, publishSnapshot = true)
    change.followups.forEach { onPatch(change.run, it) }
  }

  private fun openLink(params: JsonElement?) {
    val url = params?.jsonObject?.get("url")?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
    if (url.matches(HTTP_URL_REGEX)) {
      BrowserUtil.browse(url)
    }
  }

  private fun openTicket(params: JsonElement?) {
    val ticket = params?.jsonObject?.get("ticket")?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
    if (ticket.isEmpty()) return
    BrowserUtil.browse(SpeqaSettings.getInstance(project).resolveTicketUrl(ticket))
  }

  private fun openAttachment(params: JsonElement?) {
    val path = params?.jsonObject?.get("path")?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
    if (path.isEmpty()) return
    val resolved = runReadActionBlocking {
      AttachmentSupport.resolveFile(project, file, Attachment(path))
    } ?: return
    OpenFileDescriptor(project, resolved).navigate(true)
  }

  private fun addLink() {
    val link = AddEditLinkDialog.show(project) ?: return
    val next = current.links + link
    emit(current.copy(links = next), PatchOperation.SetRunLinks(next), publishSnapshot = true)
  }

  private fun addAttachment() {
    val descriptor = FileChooserDescriptorFactory.createAllButJarContentsDescriptor()
    FileChooser.chooseFiles(descriptor, project, null) { chosen ->
      if (chosen.isEmpty()) return@chooseFiles
      val attachment = runWriteAction {
        AttachmentSupport.copyFileToAttachments(project, file, chosen.first())
      } ?: return@chooseFiles
      val next = current.attachments + attachment
      emit(current.copy(attachments = next), PatchOperation.SetRunAttachments(next), publishSnapshot = true)
    }
  }

  private fun editLink(params: JsonElement?) {
    val itemIndex = itemIndex(params) ?: return
    val existing = current.links.getOrNull(itemIndex) ?: return
    val edited = AddEditLinkDialog.show(project, editLink = existing) ?: return
    val next = current.links.toMutableList().apply { this[itemIndex] = edited }
    emit(current.copy(links = next), PatchOperation.SetRunLinks(next), publishSnapshot = true)
  }

  private fun deleteLink(params: JsonElement?) {
    val itemIndex = itemIndex(params) ?: return
    if (itemIndex !in current.links.indices) return
    val result = Messages.showOkCancelDialog(
      SpeqaBundle.message("dialog.removeLink.message"),
      SpeqaBundle.message("dialog.removeLink.title"),
      Messages.getOkButton(),
      Messages.getCancelButton(),
      Messages.getWarningIcon(),
    )
    if (result != Messages.OK) return
    val next = current.links.filterIndexed { index, _ -> index != itemIndex }
    emit(current.copy(links = next), PatchOperation.SetRunLinks(next), publishSnapshot = true)
  }

  private fun editAttachment(params: JsonElement?) {
    val itemIndex = itemIndex(params) ?: return
    if (itemIndex !in current.attachments.indices) return
    val descriptor = FileChooserDescriptorFactory.createAllButJarContentsDescriptor()
    FileChooser.chooseFiles(descriptor, project, null) { chosen ->
      if (chosen.isEmpty()) return@chooseFiles
      val attachment = runWriteAction {
        AttachmentSupport.copyFileToAttachments(project, file, chosen.first())
      } ?: return@chooseFiles
      val next = current.attachments.toMutableList().apply { this[itemIndex] = attachment }
      emit(current.copy(attachments = next), PatchOperation.SetRunAttachments(next), publishSnapshot = true)
    }
  }

  private fun deleteAttachment(params: JsonElement?) {
    val itemIndex = itemIndex(params) ?: return
    val attachment = current.attachments.getOrNull(itemIndex) ?: return
    val choice = Messages.showDialog(
      SpeqaBundle.message("dialog.removeAttachment.message"),
      SpeqaBundle.message("dialog.removeAttachment.title"),
      arrayOf(
        SpeqaBundle.message("dialog.removeAttachment.removeLink"),
        SpeqaBundle.message("dialog.removeAttachment.deleteFile"),
        Messages.getCancelButton(),
      ),
      0,
      Messages.getQuestionIcon(),
    )
    when (choice) {
      0 -> {
        val next = current.attachments - attachment
        emit(current.copy(attachments = next), PatchOperation.SetRunAttachments(next), publishSnapshot = true)
      }
      1 -> {
        runWriteAction { AttachmentSupport.deleteFile(project, file, attachment) }
        val next = current.attachments - attachment
        emit(current.copy(attachments = next), PatchOperation.SetRunAttachments(next), publishSnapshot = true)
      }
    }
  }

  private fun itemIndex(params: JsonElement?): Int? =
    params?.jsonObject?.get("itemIndex")?.jsonPrimitive?.intOrNull

  private fun addMetadata(params: JsonElement?) {
    val obj = params?.jsonObject ?: return
    val field = obj["field"]?.jsonPrimitive?.contentOrNull ?: return
    val values = metadataValues(field) ?: return
    val value = obj["value"]?.jsonPrimitive?.contentOrNull?.trim()
      ?: Messages.showInputDialog(project, metadataPrompt(field, isEdit = false), metadataTitle(field, isEdit = false), null)
        ?.trim()
        .orEmpty()
    if (value.isEmpty()) return
    updateMetadata(field, (values + value).distinct())
  }

  private fun editMetadata(params: JsonElement?) {
    val obj = params?.jsonObject ?: return
    val field = obj["field"]?.jsonPrimitive?.contentOrNull ?: return
    val index = obj["index"]?.jsonPrimitive?.intOrNull ?: return
    val values = metadataValues(field) ?: return
    val existing = values.getOrNull(index) ?: return
    val value = Messages.showInputDialog(
      project,
      metadataPrompt(field, isEdit = true),
      metadataTitle(field, isEdit = true),
      null,
      existing,
      null,
    )?.trim().orEmpty()
    if (value.isEmpty()) return
    updateMetadata(field, values.toMutableList().apply { this[index] = value }.distinct())
  }

  private fun deleteMetadata(params: JsonElement?) {
    val obj = params?.jsonObject ?: return
    val field = obj["field"]?.jsonPrimitive?.contentOrNull ?: return
    val index = obj["index"]?.jsonPrimitive?.intOrNull ?: return
    val values = metadataValues(field) ?: return
    if (index !in values.indices) return
    updateMetadata(field, values.filterIndexed { i, _ -> i != index })
  }

  private fun metadataValues(field: String): List<String>? = when (field) {
    "environment" -> current.environment
    "tags" -> current.tags
    else -> null
  }

  private fun updateMetadata(field: String, values: List<String>) {
    when (field) {
      "environment" -> emit(
        current.copy(environment = values),
        PatchOperation.SetRunEnvironment(values),
        publishSnapshot = true,
      )
      "tags" -> emit(
        current.copy(tags = values),
        PatchOperation.SetRunTags(values),
        publishSnapshot = true,
      )
    }
  }

  private fun metadataTitle(field: String, isEdit: Boolean): String {
    val action = if (isEdit) "Edit" else "Add"
    val noun = if (field == "tags") "Tag" else "Environment"
    return "$action $noun"
  }

  private fun metadataPrompt(field: String, isEdit: Boolean): String {
    val action = if (isEdit) "Edit" else "Add"
    val noun = if (field == "tags") "tag" else "environment"
    return "$action $noun:"
  }

  private fun filterMetadata(params: JsonElement?) {
    val obj = params?.jsonObject ?: return
    val kind = obj["kind"]?.jsonPrimitive?.contentOrNull ?: return
    val value = obj["value"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
    if (value.isEmpty()) return

    val metadataKind = when (kind) {
      "tag" -> MetadataKind.TAG
      "environment" -> MetadataKind.ENVIRONMENT
      else -> return
    }
    val registry = SpeqaTagRegistry.getInstance(project)
    val anchorPoint = Point(
      obj["x"]?.jsonPrimitive?.intOrNull ?: 0,
      obj["y"]?.jsonPrimitive?.intOrNull ?: root.height,
    )
    metadataFilterRequestId += 1
    val requestId = metadataFilterRequestId
    registry.whenInitialized {
      if (project.isDisposed || requestId != metadataFilterRequestId) return@whenInitialized
      val files = when (metadataKind) {
        MetadataKind.TAG -> registry.findTestCasesByTag(value)
        MetadataKind.ENVIRONMENT -> registry.findTestCasesByEnvironment(value)
      }
      dismissMetadataMatchesPopup(invalidatePendingRequest = false)
      activeMetadataMatchesPopup = showMetadataMatches(
        anchor = root,
        project = project,
        query = "",
        candidates = files,
        anchorPoint = anchorPoint,
        onPick = { file -> FileEditorManager.getInstance(project).openFile(file, true) },
      )
    }
  }

  private fun dismissMetadataMatchesPopup(invalidatePendingRequest: Boolean = true) {
    if (invalidatePendingRequest) {
      metadataFilterRequestId += 1
    }
    activeMetadataMatchesPopup?.cancel()
    activeMetadataMatchesPopup = null
  }

  private fun emit(run: TestRun, operation: PatchOperation, publishSnapshot: Boolean = false) {
    current = run
    if (publishSnapshot) {
      publishSnapshot()
    }
    onPatch(run, operation)
  }

  private fun publishSnapshot(restorePreviewTextFocus: Boolean = true) {
    val bus = webView?.bus ?: return
    if (!scrollState.isReady) return
    val meta = resolveTestCaseHeaderMeta(project, file)
    bus.publishRaw(
      SNAPSHOT_METHOD,
      SpeqaWebViewRunPayload.build(
        current,
        currentTheme(),
        createdLabel = meta.createdLabel,
        restorePreviewTextFocus = restorePreviewTextFocus,
      ),
    )
  }

  private fun currentTheme(): String = if (StartupUiUtil.isDarkTheme) "dark" else "light"

  private fun applyPreviewBackground() {
    root.background = previewBackground()
    root.repaint()
  }

  private fun previewBackground(): Color = EditorColorsManager.getInstance().globalScheme.defaultBackground

  private fun loadPreviewHtml(): String {
    val meta = resolveTestCaseHeaderMeta(project, file)
    val initialSnapshotJson = SpeqaWebViewRunPayload.build(
      run = current,
      theme = currentTheme(),
      createdLabel = meta.createdLabel,
      restorePreviewTextFocus = false,
    ).toString()
    return SpeqaWebViewPreviewSupport.buildInlinedPreviewHtml(currentTheme(), initialSnapshotJson)
  }

  private fun showUnsupportedPanel(t: Throwable) {
    // Restore opacity for the fallback UI: the non-opaque root exists solely to let native
    // WebView mask holes reveal the IDE backdrop. When WebView creation fails there is no
    // WebView and no mask, so the root must paint previewBackground itself; otherwise the
    // surrounding empty area would show through to the ancestor (typically the editor frame),
    // producing an unexpected backdrop behind the failure message.
    root.isOpaque = true
    root.add(
      JBLabel("SpeQA WebView preview is unavailable: ${rootFailureMessage(t)}").apply {
        border = JBUI.Borders.empty(12)
      },
      BorderLayout.NORTH,
    )
  }

  override fun dispose() {
    host?.setNativeTextInputFocusActive(false)
    dismissMetadataMatchesPopup()
    messageBusConnection.disconnect()
    webView?.facade?.close()
    scope.cancel()
  }

  private fun splitCommaSeparated(value: String): List<String> {
    return value
      .split(',')
      .map { it.trim() }
      .filter { it.isNotEmpty() }
  }

  companion object {
    private val LOG = logger<SpeqaWebViewRunPanel>()

    private const val SNAPSHOT_METHOD = "speqa/testCase/snapshot"
    private const val READY_METHOD = "speqa/testCase/ready"
    private const val PREVIEW_TEXT_FOCUS_CHANGED_METHOD = "speqa/testCase/previewTextFocusChanged"
    private const val PREVIEW_SCROLLED_METHOD = "speqa/testCase/previewScrolled"
    private const val SCROLL_TO_FRACTION_METHOD = "speqa/testCase/scrollToFraction"
    private const val FIELD_CHANGED_METHOD = "speqa/testCase/fieldChanged"
    private const val LIST_CHANGED_METHOD = "speqa/testCase/listChanged"
    private const val SET_STEP_VERDICT_METHOD = "speqa/testCase/setStepVerdict"
    private const val SET_RUN_RESULT_METHOD = "speqa/testCase/setRunResult"
    private const val OPEN_LINK_METHOD = "speqa/testCase/openLink"
    private const val OPEN_TICKET_METHOD = "speqa/testCase/openTicket"
    private const val OPEN_ATTACHMENT_METHOD = "speqa/testCase/openAttachment"
    private const val ADD_LINK_METHOD = "speqa/testCase/addLink"
    private const val ADD_ATTACHMENT_METHOD = "speqa/testCase/addAttachment"
    private const val EDIT_LINK_METHOD = "speqa/testCase/editLink"
    private const val DELETE_LINK_METHOD = "speqa/testCase/deleteLink"
    private const val EDIT_ATTACHMENT_METHOD = "speqa/testCase/editAttachment"
    private const val DELETE_ATTACHMENT_METHOD = "speqa/testCase/deleteAttachment"
    private const val ADD_METADATA_METHOD = "speqa/testCase/addMetadata"
    private const val EDIT_METADATA_METHOD = "speqa/testCase/editMetadata"
    private const val DELETE_METADATA_METHOD = "speqa/testCase/deleteMetadata"
    private const val FILTER_METADATA_METHOD = "speqa/testCase/filterMetadata"
    private const val DISMISS_METADATA_MATCHES_METHOD = "speqa/testCase/dismissMetadataMatches"
    private const val NATIVE_TEXT_EDITING_COMMAND_METHOD = "speqa/testCase/nativeTextEditingCommand"
    private const val NORMALIZED_PREVIEW_COPY_METHOD = "speqa/testCase/normalizedPreviewCopy"
    private const val CODE_COPY_DIAGNOSTIC_METHOD = "speqa/testCase/codeCopyDiagnostic"
    private const val CODE_BLOCK_COPY_REQUESTED_METHOD = "speqa/testCase/codeBlockCopyRequested"
    private const val PREVIEW_PASTE_REQUESTED_METHOD = "speqa/testCase/previewPasteRequested"
    private const val PASTE_PREVIEW_TEXT_METHOD = "speqa/testCase/pastePreviewText"
    private val HTTP_URL_REGEX = Regex("^https?://.*")
  }
}
