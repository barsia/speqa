// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package io.github.barsia.speqa.webview.internal.mac

import com.intellij.ui.mac.foundation.Foundation
import com.intellij.ui.mac.foundation.Foundation.*
import com.intellij.ui.mac.foundation.ID
import io.github.barsia.speqa.webview.internal.WebViewLogger
import io.github.barsia.speqa.webview.internal.host.NativeOverlayClipShape
import io.github.barsia.speqa.webview.internal.host.NativeOverlayClippingPolicy
import com.sun.jna.Callback
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.ConcurrentHashMap

/**
 * Low-level JNA bridge to macOS `WKWebView` via the existing [Foundation] ObjC runtime.
 *
 * All methods in this object **must** be called on the macOS main thread.
 * The caller (typically [MacWebViewFacade]) is responsible for dispatching via
 * [io.github.barsia.speqa.webview.internal.MacMainThreadDispatcher].
 *
 * Uses `Foundation.invoke()` for all Objective-C message sends — no separate native library.
 */
@ApiStatus.Internal
internal object WKWebViewBridge {

  // region ObjC class names
  private const val CLS_WKWEBVIEW = "WKWebView"
  private const val CLS_WKWEBVIEW_CONFIGURATION = "WKWebViewConfiguration"
  private const val CLS_NSURL = "NSURL"
  private const val CLS_NSURLREQUEST = "NSURLRequest"
  private const val CLS_NSOBJECT = "NSObject"
  private const val CLS_NSAPPLICATION = "NSApplication"
  private const val CLS_NSVIEW = "NSView"
  private const val CLS_NSBEZIER_PATH = "NSBezierPath"
  private const val CLS_CASHAPE_LAYER = "CAShapeLayer"
  private const val CLS_CATRANSACTION = "CATransaction"
  private const val CLS_MASKABLE_WEBVIEW_CONTAINER = "SpeqaMaskableWebViewContainer"
  // endregion

  // region ObjC selectors (centralized, no scattered magic strings)
  private val SEL_ALLOC = createSelector("alloc")
  private val SEL_INIT = createSelector("init")
  private val SEL_INIT_WITH_FRAME = createSelector("initWithFrame:")
  private val SEL_RELEASE = createSelector("release")

  // WKWebViewConfiguration
  private val SEL_PREFERENCES = createSelector("preferences")
  private val SEL_USER_CONTENT_CONTROLLER = createSelector("userContentController")

  // WKPreferences
  private val SEL_SET_JAVA_SCRIPT_ENABLED = createSelector("setJavaScriptEnabled:")
  private val SEL_SET_VALUE_FOR_KEY = createSelector("setValue:forKey:")

  // WKWebView
  private val SEL_INIT_WITH_FRAME_CONFIGURATION = createSelector("initWithFrame:configuration:")
  private val SEL_CONFIGURATION = createSelector("configuration")
  private val SEL_LOAD_REQUEST = createSelector("loadRequest:")
  private val SEL_LOAD_HTML_STRING_BASE_URL = createSelector("loadHTMLString:baseURL:")
  private val SEL_EVALUATE_JAVASCRIPT = createSelector("evaluateJavaScript:completionHandler:")
  private val SEL_WINDOW = createSelector("window")
  private val SEL_SET_FRAME = createSelector("setFrame:")
  private val SEL_SET_HIDDEN = createSelector("setHidden:")
  private val SEL_SET_AUTORESIZING_MASK = createSelector("setAutoresizingMask:")
  private val SEL_HIT_TEST = createSelector("hitTest:")
  private val SEL_REMOVE_FROM_SUPERVIEW = createSelector("removeFromSuperview")
  private val SEL_SET_INSPECTABLE = createSelector("setInspectable:")
  private val SEL_RESPONDS_TO_SELECTOR = createSelector("respondsToSelector:")

  // NSWindow
  private val SEL_MAKE_FIRST_RESPONDER = createSelector("makeFirstResponder:")
  private val SEL_CONTENT_VIEW = createSelector("contentView")

  // NSApplication
  private val SEL_SHARED_APPLICATION = createSelector("sharedApplication")
  private val SEL_SEND_ACTION_TO_FROM = createSelector("sendAction:to:from:")

  // NSView
  private val SEL_ADD_SUBVIEW = createSelector("addSubview:")
  private val SEL_SET_WANTS_LAYER = createSelector("setWantsLayer:")
  private val SEL_LAYER = createSelector("layer")
  private val SEL_SET_MASK = createSelector("setMask:")

  // CATransaction
  private val SEL_BEGIN = createSelector("begin")
  private val SEL_COMMIT = createSelector("commit")
  private val SEL_FLUSH = createSelector("flush")
  private val SEL_SET_DISABLE_ACTIONS = createSelector("setDisableActions:")

  // NSBezierPath / CAShapeLayer
  private val SEL_BEZIER_PATH = createSelector("bezierPath")
  private val SEL_APPEND_BEZIER_PATH_WITH_RECT = createSelector("appendBezierPathWithRect:")
  private val SEL_APPEND_BEZIER_PATH_WITH_ROUNDED_RECT =
    createSelector("appendBezierPathWithRoundedRect:xRadius:yRadius:")
  private val SEL_MOVE_TO_POINT = createSelector("moveToPoint:")
  private val SEL_LINE_TO_POINT = createSelector("lineToPoint:")
  private val SEL_CLOSE_PATH = createSelector("closePath")
  private val SEL_SET_WINDING_RULE = createSelector("setWindingRule:")
  private val SEL_CG_PATH = createSelector("CGPath")
  private val SEL_SET_PATH = createSelector("setPath:")
  private val SEL_SET_FILL_RULE = createSelector("setFillRule:")

  // NSURL / NSURLRequest
  private val SEL_URL_WITH_STRING = createSelector("URLWithString:")
  private val SEL_REQUEST_WITH_URL = createSelector("requestWithURL:")

  // WKUserContentController
  private val SEL_ADD_SCRIPT_MESSAGE_HANDLER = createSelector("addScriptMessageHandler:name:")
  private val SEL_REMOVE_SCRIPT_MESSAGE_HANDLER = createSelector("removeScriptMessageHandlerForName:")

  // WKScriptMessage
  private val SEL_BODY = createSelector("body")
  // endregion

  /** Name used for the JS→JVM postMessage channel. JS calls: `window.webkit.messageHandlers.webviewIpc.postMessage(...)` */
  const val IPC_HANDLER_NAME = "webviewIpc"

  /**
   * Registered ObjC class acting as WKScriptMessageHandler. Created once, reused across instances.
   * The class name must be unique to avoid collisions with other ObjC runtime registrations.
   */
  private var messageHandlerClass: ID = ID.NIL
  private var maskableContainerClass: ID = ID.NIL

  /**
   * Callback reference kept alive to prevent GC while native code holds a function pointer.
   */
  @Suppress("unused") // prevent GC
  private var messageHandlerCallback: Callback? = null
  @Suppress("unused") // prevent GC
  private var containerHitTestCallback: Callback? = null

  /**
   * Per-webview callback registry. Key = the ObjC `self` pointer of the handler instance.
   * Value = callback invoked with the message body string.
   */
  private val messageHandlerCallbacks = java.util.concurrent.ConcurrentHashMap<Long, (String) -> Unit>()
  private val containerWebViews = ConcurrentHashMap<Long, ID>()
  private val containerFrames = ConcurrentHashMap<Long, NativeWebViewFrame>()
  private val containerOverlayClipShapes = ConcurrentHashMap<Long, List<NativeOverlayClipShape>>()

  /**
   * Creates and configures a new `WKWebView` instance.
   *
   * @param onMessage callback invoked on the main thread when JS calls `postMessage`
   * @return handles that must be passed to [release].
   */
  fun createWKWebView(onMessage: (String) -> Unit): WebViewHandles {
    val containerClass = if (NativeOverlayClippingPolicy.isEnabled()) {
      ensureMaskableContainerClassRegistered()
      maskableContainerClass
    }
    else {
      getObjcClass(CLS_NSVIEW)
    }

    // 1. Create WKWebViewConfiguration
    val configuration = invoke(invoke(getObjcClass(CLS_WKWEBVIEW_CONFIGURATION), SEL_ALLOC), SEL_INIT)

    // 2. Configure preferences
    val preferences = invoke(configuration, SEL_PREFERENCES)
    invoke(preferences, SEL_SET_JAVA_SCRIPT_ENABLED, true)
    // Enable developer extras for debugging in POC
    invoke(preferences, SEL_SET_VALUE_FOR_KEY, invoke("NSNumber", "numberWithBool:", true), nsString("developerExtrasEnabled"))

    // 3. Set up user content controller with message handler
    val userContentController = invoke(configuration, SEL_USER_CONTENT_CONTROLLER)
    val handlerInstance = createAndRegisterMessageHandler(onMessage)
    invoke(userContentController, SEL_ADD_SCRIPT_MESSAGE_HANDLER, handlerInstance, nsString(IPC_HANDLER_NAME))

    // TODO: suppress the browser right-click context menu at this layer instead of
    //  relying on each page injecting `preventDefault` on the `contextmenu` DOM event.
    //  Two native options, both doable without leaving this bridge:
    //    1) Register an ObjC class pair conforming to WKUIDelegate (mirror
    //       `IdeaWKMessageHandler` below) that implements
    //       `webView:contextMenuConfigurationForElement:completionHandler:` and
    //       returns nil. Then `setUIDelegate:` on the WKWebView. Cleanest, public API.
    //    2) Inject a document-start WKUserScript that calls `preventDefault` on
    //       `contextmenu`. Simpler, but needs care with JNA arg ABI — passing the
    //       `WKUserScriptInjectionTime` enum (NSInteger, 64-bit) from a Kotlin Int
    //       through `Foundation.invoke(...)` varargs previously corrupted init and
    //       left WebView in a broken state, so cast to Long explicitly.
    //  Gate behind a `suppressContextMenu: Boolean` flag on `createMacOsFacade` so
    //  consumers that need native menus (e.g. debug tools) can opt out.

    // 4. Allocate a container plus WKWebView with zero frames (will be set when attached).
    val containerView = invoke(invoke(containerClass, SEL_ALLOC), SEL_INIT_WITH_FRAME,
                               NSRect(0.0, 0.0, 0.0, 0.0))
    invoke(containerView, SEL_SET_WANTS_LAYER, true)

    val webView = invoke(getObjcClass(CLS_WKWEBVIEW), SEL_ALLOC)
    val initializedWebView = invoke(webView, SEL_INIT_WITH_FRAME_CONFIGURATION,
                                    NSRect(0.0, 0.0, 0.0, 0.0), configuration)

    // Enable Web Inspector on macOS 13.3+. Without `setInspectable:YES` modern
    // WebKit refuses to attach the inspector even with `developerExtrasEnabled`.
    // Guarded by respondsToSelector: so older macOS versions are unaffected.
    val responds = (invoke(initializedWebView, SEL_RESPONDS_TO_SELECTOR, SEL_SET_INSPECTABLE).toLong() and 0xFFL) != 0L
    if (responds) {
      invoke(initializedWebView, SEL_SET_INSPECTABLE, true)
    }

    // 5. Keep Swing host geometry as the only frame source. The WebView is attached
    // to the window content view, so AppKit autoresizing would follow the whole window.
    invoke(initializedWebView, SEL_SET_AUTORESIZING_MASK, 0)
    invoke(containerView, SEL_ADD_SUBVIEW, initializedWebView)
    containerWebViews[containerView.toLong()] = initializedWebView
    containerFrames[containerView.toLong()] = NativeWebViewFrame(x = 0.0, y = 0.0, width = 0.0, height = 0.0)

    // 6. Release configuration (webview retains it)
    invoke(configuration, SEL_RELEASE)

    return WebViewHandles(
      containerView = containerView,
      webView = initializedWebView,
      messageHandler = handlerInstance,
    )
  }

  fun attachToParent(containerView: ID, parentNSView: ID) {
    invoke(parentNSView, SEL_ADD_SUBVIEW, containerView)
  }

  fun detachFromParent(containerView: ID) {
    invoke(containerView, SEL_REMOVE_FROM_SUPERVIEW)
  }

  fun loadUrl(webView: ID, url: String) {
    val nsUrl = invoke(getObjcClass(CLS_NSURL), SEL_URL_WITH_STRING, nsString(url))
    val request = invoke(getObjcClass(CLS_NSURLREQUEST), SEL_REQUEST_WITH_URL, nsUrl)
    invoke(webView, SEL_LOAD_REQUEST, request)
  }

  fun loadHtml(webView: ID, html: String, baseUrl: String?) {
    val nsBaseUrl = if (baseUrl != null) {
      invoke(getObjcClass(CLS_NSURL), SEL_URL_WITH_STRING, nsString(baseUrl))
    }
    else {
      ID.NIL
    }
    invoke(webView, SEL_LOAD_HTML_STRING_BASE_URL, nsString(html), nsBaseUrl)
  }

  /**
   * Evaluates JavaScript in the WebView and reports the result through the message handler channel.
   *
   * The message payload format is one of:
   * - `__eval__:<evalId>:<value>`
   * - `__eval_err__:<evalId>:<error>`
   *
   * [evalId] is provided by the facade and scoped per WebView instance.
   */
  fun evaluateJavaScript(webView: ID, script: String, evalId: Long) {
    val taggedScript = """
      (function() {
        try {
          var __result = eval(${escapeJsString(script)});
          window.webkit.messageHandlers.$IPC_HANDLER_NAME.postMessage('__eval__:$evalId:' + String(__result));
        } catch(e) {
          window.webkit.messageHandlers.$IPC_HANDLER_NAME.postMessage('__eval_err__:$evalId:' + e.message);
        }
      })();
    """.trimIndent()

    executeJavaScript(webView, taggedScript)
  }

  /**
   * Executes JavaScript without routing result/error back into the bridge channel.
   */
  fun executeJavaScript(webView: ID, script: String) {
    invoke(webView, SEL_EVALUATE_JAVASCRIPT, nsString(script), ID.NIL)
  }

  /**
   * Delivers raw JSON-RPC frame into JS runtime ingress `window.__KWRY__.__deliver(...)`.
   */
  fun deliverJsonToJavaScript(webView: ID, rawJson: String) {
    val script = "window.__KWRY__ && window.__KWRY__.__deliver(${escapeJsString(rawJson)});"
    executeJavaScript(webView, script)
  }

  fun setFrame(containerView: ID, webView: ID, x: Double, y: Double, w: Double, h: Double) {
    invoke(containerView, SEL_SET_FRAME, NSRect(x, y, w, h))
    invoke(webView, SEL_SET_FRAME, NSRect(0.0, 0.0, w, h))
    containerFrames[containerView.toLong()] = NativeWebViewFrame(x = x, y = y, width = w, height = h)
  }

  fun setFrame(webView: ID, x: Double, y: Double, w: Double, h: Double) {
    invoke(webView, SEL_SET_FRAME, NSRect(x, y, w, h))
  }

  fun setHidden(containerView: ID, hidden: Boolean) {
    invoke(containerView, SEL_SET_HIDDEN, hidden)
  }

  /**
   * Reconfigures the container's `CAShapeLayer` mask from [shapes] in the AppKit coordinate space
   * (origin already y-flipped by the caller).
   *
   * When [flushImmediately] is `true`, the layer update is bracketed by `CATransaction.begin` /
   * `setDisableActions:YES` / `commit` / `flush` so the new mask is on-screen before this call
   * returns. Used on overlay hide transitions to eliminate the residual mask-hole frame after a
   * balloon dismissal. When `false`, the implicit transaction of the current runloop iteration
   * commits the mask asynchronously — appropriate for show/move/resize transitions where the
   * async path avoids thrashing the render server.
   *
   * On any reflection / `Foundation` failure, [NativeOverlayClippingPolicy] disables AppKit
   * clipping for the rest of the session and the mask is cleared.
   *
   * Must be called on the macOS main thread.
   */
  fun setOverlayClipShapes(
    containerView: ID,
    width: Double,
    height: Double,
    shapes: List<NativeOverlayClipShape>,
    flushImmediately: Boolean = false,
  ) {
    if (!NativeOverlayClippingPolicy.isEnabled()) {
      clearOverlayClipShapes(containerView)
      return
    }

    try {
      if (flushImmediately) {
        // Wrap the mask reconfiguration in an explicit CATransaction with implicit-action
        // disabling, then flush, so the new mask is committed to the render server before
        // executeOnMainThread(waitUntilDone=true) returns. Without this, the mask change can
        // sit in the implicit transaction queued by the current runloop iteration and
        // become visible only after a delay, leaving a residual hole on the WebView after a
        // balloon dismissal.
        val txClass = getObjcClass(CLS_CATRANSACTION)
        invoke(txClass, SEL_BEGIN)
        try {
          invoke(txClass, SEL_SET_DISABLE_ACTIONS, true)
          setOverlayClipShapesUnsafe(containerView, width, height, shapes)
        }
        finally {
          invoke(txClass, SEL_COMMIT)
          invoke(txClass, SEL_FLUSH)
        }
      }
      else {
        setOverlayClipShapesUnsafe(containerView, width, height, shapes)
      }
    }
    catch (t: Throwable) {
      NativeOverlayClippingPolicy.disableForSession("failed to apply AppKit mask", t)
      clearOverlayClipShapes(containerView)
    }
  }

  private fun setOverlayClipShapesUnsafe(
    containerView: ID,
    width: Double,
    height: Double,
    shapes: List<NativeOverlayClipShape>,
  ) {
    val containerKey = containerView.toLong()
    val layer = ensureLayer(containerView)
    if (width <= 0.0 || height <= 0.0 || shapes.isEmpty()) {
      containerOverlayClipShapes.remove(containerKey)
      invoke(layer, SEL_SET_MASK, ID.NIL)
      return
    }

    val clippedShapes = shapes
      .mapNotNull { it.clippedTo(width, height) }
      .distinct()
    if (clippedShapes.isEmpty()) {
      containerOverlayClipShapes.remove(containerKey)
      invoke(layer, SEL_SET_MASK, ID.NIL)
      return
    }

    containerOverlayClipShapes[containerKey] = clippedShapes

    val path = invoke(getObjcClass(CLS_NSBEZIER_PATH), SEL_BEZIER_PATH)
    invoke(path, SEL_APPEND_BEZIER_PATH_WITH_RECT, NSRect(0.0, 0.0, width, height))
    for (shape in clippedShapes) {
      appendShapeToPath(path, shape)
    }
    invoke(path, SEL_SET_WINDING_RULE, 1L)

    val mask = invoke(getObjcClass(CLS_CASHAPE_LAYER), SEL_LAYER)
    invoke(mask, SEL_SET_FILL_RULE, nsString("even-odd"))
    invoke(mask, SEL_SET_PATH, invoke(path, SEL_CG_PATH))
    invoke(layer, SEL_SET_MASK, mask)
  }

  private fun appendShapeToPath(path: ID, shape: NativeOverlayClipShape) {
    when (shape) {
      is NativeOverlayClipShape.Rect -> {
        invoke(
          path,
          SEL_APPEND_BEZIER_PATH_WITH_RECT,
          NSRect(shape.x.toDouble(), shape.y.toDouble(), shape.width.toDouble(), shape.height.toDouble()),
        )
      }
      is NativeOverlayClipShape.RoundedRect -> {
        invoke(
          path,
          SEL_APPEND_BEZIER_PATH_WITH_ROUNDED_RECT,
          NSRect(shape.x.toDouble(), shape.y.toDouble(), shape.width.toDouble(), shape.height.toDouble()),
          shape.radius,
          shape.radius,
        )
      }
      is NativeOverlayClipShape.Polygon -> {
        if (shape.points.isEmpty()) return
        val first = shape.points.first()
        invoke(path, SEL_MOVE_TO_POINT, NSPoint(first.x, first.y))
        for (i in 1 until shape.points.size) {
          val p = shape.points[i]
          invoke(path, SEL_LINE_TO_POINT, NSPoint(p.x, p.y))
        }
        invoke(path, SEL_CLOSE_PATH)
      }
    }
  }

  private fun clearOverlayClipShapes(containerView: ID) {
    containerOverlayClipShapes.remove(containerView.toLong())
    try {
      val layer = ensureLayer(containerView)
      invoke(layer, SEL_SET_MASK, ID.NIL)
    }
    catch (t: Throwable) {
      WebViewLogger.LOG.warn("Failed to clear native WebView overlay clipping mask", t)
    }
  }

  fun requestFocus(webView: ID) {
    val window = invoke(webView, SEL_WINDOW)
    if (!isNil(window)) {
      invoke(window, SEL_MAKE_FIRST_RESPONDER, webView)
    }
  }

  fun clearFocus(webView: ID) {
    val window = invoke(webView, SEL_WINDOW)
    if (isNil(window)) return
    // Transfer first responder to the window's contentView (the AWT/Swing root
    // NSView) instead of nil. With nil, every subsequent NSEvent.keyDown
    // arrives at a window with no responder and AppKit emits NSBeep — even
    // though Java/Swing has already dispatched the key to the focused
    // JComponent and inserted text. Routing the responder back to the content
    // view keeps the AppKit responder chain alive, so AppKit treats keystrokes
    // as handled and stays quiet.
    val contentView = invoke(window, SEL_CONTENT_VIEW)
    val target = if (isNil(contentView) || contentView == webView) ID.NIL else contentView
    invoke(window, SEL_MAKE_FIRST_RESPONDER, target)
  }

  fun performEditCommand(webView: ID, command: MacWebViewEditCommand) {
    val app = invoke(getObjcClass(CLS_NSAPPLICATION), SEL_SHARED_APPLICATION)
    if (!isNil(app)) {
      invoke(app, SEL_SEND_ACTION_TO_FROM, createSelector(command.selectorName), ID.NIL, webView)
    }
  }

  /**
   * Releases the native WKWebView and its associated message handler.
   * Must be called on the macOS main thread.
   */
  fun release(handles: WebViewHandles) {
    // 1. Remove the message handler from user content controller to break retain cycle
    val configuration = invoke(handles.webView, SEL_CONFIGURATION)
    val ucc = invoke(configuration, SEL_USER_CONTENT_CONTROLLER)
    invoke(ucc, SEL_REMOVE_SCRIPT_MESSAGE_HANDLER, nsString(IPC_HANDLER_NAME))

    // 2. Detach from superview
    invoke(handles.containerView, SEL_REMOVE_FROM_SUPERVIEW)

    // 3. Unregister message callback
    messageHandlerCallbacks.remove(handles.messageHandler.toLong())
    containerWebViews.remove(handles.containerView.toLong())
    containerFrames.remove(handles.containerView.toLong())
    containerOverlayClipShapes.remove(handles.containerView.toLong())

    // 4. Release native objects
    invoke(handles.messageHandler, SEL_RELEASE)
    invoke(handles.webView, SEL_RELEASE)
    invoke(handles.containerView, SEL_RELEASE)
  }

  // region Message handler class registration

  private fun createAndRegisterMessageHandler(onMessage: (String) -> Unit): ID {
    ensureMessageHandlerClassRegistered()

    val instance = invoke(invoke(messageHandlerClass, SEL_ALLOC), SEL_INIT)
    messageHandlerCallbacks[instance.toLong()] = onMessage
    return instance
  }

  @Synchronized
  private fun ensureMessageHandlerClassRegistered() {
    if (!ID.NIL.equals(messageHandlerClass)) return

    val superclass = getObjcClass(CLS_NSOBJECT)
    val cls = allocateObjcClassPair(superclass, "IdeaWKMessageHandler")

    val protocol = getProtocol("WKScriptMessageHandler")
    if (!isNil(protocol)) {
      addProtocol(cls, protocol)
    }

    // Implement userContentController:didReceiveScriptMessage:
    // Type encoding: v@:@@ (void, self, _cmd, WKUserContentController, WKScriptMessage)
    val callback = object : Callback {
      @Suppress("unused", "UNUSED_PARAMETER") // called from native
      fun callback(self: ID, selector: String, controller: ID, message: ID) {
        val body = invoke(message, SEL_BODY)
        val bodyString = toStringViaUTF8(body)
        if (bodyString != null) {
          val handler = messageHandlerCallbacks[self.toLong()]
          handler?.invoke(bodyString)
        }
      }
    }
    messageHandlerCallback = callback // prevent GC

    addMethod(cls, createSelector("userContentController:didReceiveScriptMessage:"), callback, "v@:@@")

    registerObjcClassPair(cls)
    messageHandlerClass = cls
  }

  // endregion

  // region Maskable WebView container class registration

  @Synchronized
  private fun ensureMaskableContainerClassRegistered() {
    if (!ID.NIL.equals(maskableContainerClass)) return

    val superclass = getObjcClass(CLS_NSVIEW)
    val cls = allocateObjcClassPair(superclass, CLS_MASKABLE_WEBVIEW_CONTAINER)
    val callback = object : Callback {
      @Suppress("unused", "UNUSED_PARAMETER") // called from native
      fun callback(self: ID, selector: String, point: NSPoint): ID {
        return try {
          hitTestMaskableContainer(self, point)
        }
        catch (t: Throwable) {
          NativeOverlayClippingPolicy.disableForSession("native hitTest callback failed", t)
          clearOverlayClipShapes(self)
          ID.NIL
        }
      }
    }
    containerHitTestCallback = callback // prevent GC

    addMethod(cls, createSelector("hitTest:"), callback, "@@:{CGPoint=dd}")
    registerObjcClassPair(cls)
    maskableContainerClass = cls
  }

  private fun hitTestMaskableContainer(self: ID, point: NSPoint): ID {
    val frame = containerFrames[self.toLong()] ?: return ID.NIL
    val shapes = containerOverlayClipShapes[self.toLong()]
    val localX = pointCoordinate(point.x) - frame.x
    val localY = pointCoordinate(point.y) - frame.y
    if (!frame.containsLocalPoint(localX, localY)) {
      return ID.NIL
    }
    if (NativeOverlayClippingPolicy.isEnabled() && shapes != null && shapes.any { it.contains(localX, localY) }) {
      return ID.NIL
    }
    val webView = containerWebViews[self.toLong()] ?: return self
    val target = invoke(webView, SEL_HIT_TEST, NSPoint(localX, localY))
    return if (isNil(target)) webView else target
  }

  private fun ensureLayer(view: ID): ID {
    invoke(view, SEL_SET_WANTS_LAYER, true)
    return invoke(view, SEL_LAYER)
  }

  private fun NativeOverlayClipShape.clippedTo(width: Double, height: Double): NativeOverlayClipShape? {
    return when (this) {
      is NativeOverlayClipShape.Rect -> {
        val left = x.coerceAtLeast(0)
        val top = y.coerceAtLeast(0)
        val right = (x + this.width).coerceAtMost(width.toInt())
        val bottom = (y + this.height).coerceAtMost(height.toInt())
        if (right <= left || bottom <= top) null
        else NativeOverlayClipShape.Rect(left, top, right - left, bottom - top)
      }
      is NativeOverlayClipShape.RoundedRect -> {
        if (this.width <= 0 || this.height <= 0) return null
        if (!boundsIntersectHost(bounds, width, height)) null else this
      }
      is NativeOverlayClipShape.Polygon -> {
        if (points.isEmpty() || !boundsIntersectHost(bounds, width, height)) null else this
      }
    }
  }

  private fun boundsIntersectHost(b: java.awt.Rectangle, width: Double, height: Double): Boolean {
    val right = b.x + b.width
    val bottom = b.y + b.height
    return right > 0 && bottom > 0 && b.x < width.toInt() && b.y < height.toInt()
  }

  private fun pointCoordinate(value: Any): Double {
    val field = value.javaClass.getDeclaredField("value")
    field.isAccessible = true
    return field.getDouble(value)
  }

  // endregion

  // region Utilities

  private fun escapeJsString(s: String): String {
    val escaped = s.replace("\\", "\\\\")
      .replace("'", "\\'")
      .replace("\"", "\\\"")
      .replace("\n", "\\n")
      .replace("\r", "\\r")
      .replace("\t", "\\t")
    return "'$escaped'"
  }

  // endregion

  data class WebViewHandles(
    val containerView: ID,
    val webView: ID,
    val messageHandler: ID,
  )

  private data class NativeWebViewFrame(
    val x: Double,
    val y: Double,
    val width: Double,
    val height: Double,
  ) {
    fun containsLocalPoint(x: Double, y: Double): Boolean {
      return x >= 0.0 && x < width && y >= 0.0 && y < height
    }
  }
}
