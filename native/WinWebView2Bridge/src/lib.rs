// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

#![cfg(target_os = "windows")]

use std::{
    cell::RefCell,
    ffi::c_void,
    rc::Rc,
    thread::sleep,
    time::{Duration, Instant},
};

use jni::{
    objects::{GlobalRef, JClass, JObject, JString, JValue},
    sys::{jboolean, jdouble, jint, jlong},
    JNIEnv, JavaVM,
};
use webview2_com::{Microsoft::Web::WebView2::Win32::*, *};
use windows::{
    core::{w, HSTRING, PCWSTR, PWSTR},
    Win32::{
        Foundation::*,
        Graphics::Gdi::*,
        System::{Com::*, LibraryLoader::GetModuleHandleW},
        UI::{
            Input::KeyboardAndMouse::{
                GetKeyState, SetFocus, VIRTUAL_KEY, VK_CONTROL, VK_LWIN, VK_MENU, VK_RWIN, VK_SHIFT,
            },
            WindowsAndMessaging::*,
        },
    },
};

type BridgeResult<T> = std::result::Result<T, String>;
type NativeHandle = Rc<RefCell<NativeWebView>>;
type EventRegistrationToken = i64;

const MODIFIER_SHIFT: jint = 1;
const MODIFIER_CONTROL: jint = 1 << 1;
const MODIFIER_ALT: jint = 1 << 2;
const MODIFIER_META: jint = 1 << 3;

struct JavaCallbacks {
    vm: JavaVM,
    object: GlobalRef,
}

impl JavaCallbacks {
    fn on_created(&self, handle: jlong) {
        self.with_env(|env, object| {
            env.call_method(object, "onCreated", "(J)V", &[JValue::Long(handle)])?;
            Ok(())
        });
    }

    fn on_create_failed(&self, message: String) {
        self.with_env(|env, object| {
            let message = JObject::from(env.new_string(message)?);
            env.call_method(
                object,
                "onCreateFailed",
                "(Ljava/lang/String;)V",
                &[JValue::Object(&message)],
            )?;
            Ok(())
        });
    }

    fn on_message(&self, raw: String) {
        self.with_env(|env, object| {
            let raw = JObject::from(env.new_string(raw)?);
            env.call_method(
                object,
                "onMessage",
                "(Ljava/lang/String;)V",
                &[JValue::Object(&raw)],
            )?;
            Ok(())
        });
    }

    fn on_evaluation_result(&self, eval_id: jlong, result: String) {
        self.with_env(|env, object| {
            let result = JObject::from(env.new_string(result)?);
            env.call_method(
                object,
                "onEvaluationResult",
                "(JLjava/lang/String;)V",
                &[JValue::Long(eval_id), JValue::Object(&result)],
            )?;
            Ok(())
        });
    }

    fn on_evaluation_error(&self, eval_id: jlong, message: String) {
        self.with_env(|env, object| {
            let message = JObject::from(env.new_string(message)?);
            env.call_method(
                object,
                "onEvaluationError",
                "(JLjava/lang/String;)V",
                &[JValue::Long(eval_id), JValue::Object(&message)],
            )?;
            Ok(())
        });
    }

    fn on_accelerator_key_pressed(
        &self,
        key_event_kind: jint,
        virtual_key: jint,
        modifiers: jint,
        key_event_lparam: jint,
    ) -> bool {
        let Ok(mut env) = self.vm.attach_current_thread() else {
            return false;
        };
        env.call_method(
            self.object.as_obj(),
            "onAcceleratorKeyPressed",
            "(IIII)Z",
            &[
                JValue::Int(key_event_kind),
                JValue::Int(virtual_key),
                JValue::Int(modifiers),
                JValue::Int(key_event_lparam),
            ],
        )
        .ok()
        .and_then(|value| value.z().ok())
        .unwrap_or(false)
    }

    #[allow(dead_code)]
    fn on_log(&self, level: jint, message: String) {
        self.with_env(|env, object| {
            let message = JObject::from(env.new_string(message)?);
            env.call_method(
                object,
                "onLog",
                "(ILjava/lang/String;)V",
                &[JValue::Int(level), JValue::Object(&message)],
            )?;
            Ok(())
        });
    }

    fn with_env<F>(&self, action: F)
    where
        F: FnOnce(&mut JNIEnv<'_>, &JObject<'_>) -> jni::errors::Result<()>,
    {
        let Ok(mut env) = self.vm.attach_current_thread() else {
            return;
        };
        let _ = action(&mut env, self.object.as_obj());
    }
}

struct NativeWebView {
    handle: jlong,
    parent: HWND,
    hwnd: HWND,
    env: Option<ICoreWebView2Environment>,
    controller: Option<ICoreWebView2Controller>,
    webview: Option<ICoreWebView2>,
    environment_completed_handler:
        Option<ICoreWebView2CreateCoreWebView2EnvironmentCompletedHandler>,
    controller_completed_handler: Option<ICoreWebView2CreateCoreWebView2ControllerCompletedHandler>,
    execute_script_handlers: Vec<(u64, ICoreWebView2ExecuteScriptCompletedHandler)>,
    next_script_handler_id: u64,
    web_message_token: EventRegistrationToken,
    accelerator_key_pressed_token: Option<EventRegistrationToken>,
    callbacks: Rc<JavaCallbacks>,
    destroyed: bool,
    visible: bool,
    x: i32,
    y: i32,
    width: i32,
    height: i32,
    scale: f64,
}

impl NativeWebView {
    fn destroy(&mut self) {
        if self.destroyed {
            return;
        }
        self.destroyed = true;
        if let (Some(controller), Some(token)) =
            (&self.controller, self.accelerator_key_pressed_token.take())
        {
            unsafe {
                let _ = controller.remove_AcceleratorKeyPressed(token);
            }
        }
        self.webview = None;
        self.controller = None;
        self.env = None;
        self.controller_completed_handler = None;
        self.environment_completed_handler = None;
        self.execute_script_handlers.clear();
        if !self.hwnd.0.is_null() {
            unsafe {
                let _ = DestroyWindow(self.hwnd);
            }
            self.hwnd = HWND::default();
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_io_github_barsia_speqa_webview_internal_windows_WinWebView2Bridge_createNative(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    parent_hwnd: jlong,
    user_data_dir: JString<'_>,
    callbacks: JObject<'_>,
) -> jlong {
    match create_native(&mut env, parent_hwnd, user_data_dir, callbacks) {
        Ok(handle) => handle,
        Err(message) => {
            let _ = env.throw_new("java/lang/IllegalStateException", message);
            0
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_io_github_barsia_speqa_webview_internal_windows_WinWebView2Bridge_destroyNative(
    _env: JNIEnv<'_>,
    _class: JClass<'_>,
    handle: jlong,
) {
    if handle == 0 {
        return;
    }
    unsafe {
        let native = Rc::from_raw(handle as *const RefCell<NativeWebView>);
        native.borrow_mut().destroy();
    }
}

#[no_mangle]
pub extern "system" fn Java_io_github_barsia_speqa_webview_internal_windows_WinWebView2Bridge_attachToParentNative(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    handle: jlong,
    parent_hwnd: jlong,
) {
    run_with_handle(&mut env, handle, |native| {
        let parent = HWND(parent_hwnd as *mut c_void);
        let mut view = native.borrow_mut();
        view.parent = parent;
        unsafe { SetParent(view.hwnd, Some(parent)).map_err(format_windows_error)? };
        apply_bounds(&view)?;
        Ok(())
    });
}

#[no_mangle]
pub extern "system" fn Java_io_github_barsia_speqa_webview_internal_windows_WinWebView2Bridge_detachFromParentNative(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    handle: jlong,
) {
    run_with_handle(&mut env, handle, |native| {
        let view = native.borrow();
        unsafe {
            let _ = ShowWindow(view.hwnd, SW_HIDE);
        }
        Ok(())
    });
}

#[no_mangle]
pub extern "system" fn Java_io_github_barsia_speqa_webview_internal_windows_WinWebView2Bridge_setBoundsNative(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    handle: jlong,
    x: jint,
    y: jint,
    width: jint,
    height: jint,
    scale: jdouble,
) {
    run_with_handle(&mut env, handle, |native| {
        let mut view = native.borrow_mut();
        view.x = x;
        view.y = y;
        view.width = width.max(0);
        view.height = height.max(0);
        view.scale = if scale > 0.0 { scale } else { 1.0 };
        apply_bounds(&view)
    });
}

#[no_mangle]
pub extern "system" fn Java_io_github_barsia_speqa_webview_internal_windows_WinWebView2Bridge_setVisibleNative(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    handle: jlong,
    visible: jboolean,
) {
    run_with_handle(&mut env, handle, |native| {
        let mut view = native.borrow_mut();
        view.visible = visible != 0;
        unsafe {
            let _ = ShowWindow(view.hwnd, if view.visible { SW_SHOW } else { SW_HIDE });
            if let Some(controller) = &view.controller {
                controller
                    .SetIsVisible(view.visible)
                    .map_err(format_windows_error)?;
            }
        }
        Ok(())
    });
}

#[no_mangle]
pub extern "system" fn Java_io_github_barsia_speqa_webview_internal_windows_WinWebView2Bridge_focusNative(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    handle: jlong,
) {
    run_with_handle(&mut env, handle, |native| {
        let view = native.borrow();
        unsafe {
            let _ = SetFocus(Some(view.hwnd));
            if let Some(controller) = &view.controller {
                controller
                    .MoveFocus(COREWEBVIEW2_MOVE_FOCUS_REASON_PROGRAMMATIC)
                    .map_err(format_windows_error)?;
            }
        }
        Ok(())
    });
}

#[no_mangle]
pub extern "system" fn Java_io_github_barsia_speqa_webview_internal_windows_WinWebView2Bridge_clearFocusNative(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    handle: jlong,
) {
    run_with_handle(&mut env, handle, |native| {
        let view = native.borrow();
        unsafe {
            let _ = SetFocus(Some(view.parent));
        }
        Ok(())
    });
}

#[no_mangle]
pub extern "system" fn Java_io_github_barsia_speqa_webview_internal_windows_WinWebView2Bridge_loadUrlNative(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    handle: jlong,
    url: JString<'_>,
) {
    let Ok(url) = jstring_to_string(&mut env, url) else {
        return;
    };
    run_with_handle(&mut env, handle, |native| {
        let webview = native
            .borrow()
            .webview
            .clone()
            .ok_or_else(|| "WebView2 is not ready".to_string())?;
        unsafe {
            webview
                .Navigate(&HSTRING::from(url))
                .map_err(format_windows_error)?;
        }
        Ok(())
    });
}

#[no_mangle]
pub extern "system" fn Java_io_github_barsia_speqa_webview_internal_windows_WinWebView2Bridge_loadHtmlNative(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    handle: jlong,
    html: JString<'_>,
    _base_url: JObject<'_>,
) {
    let Ok(html) = jstring_to_string(&mut env, html) else {
        return;
    };
    run_with_handle(&mut env, handle, |native| {
        let webview = native
            .borrow()
            .webview
            .clone()
            .ok_or_else(|| "WebView2 is not ready".to_string())?;
        unsafe {
            webview
                .NavigateToString(&HSTRING::from(html))
                .map_err(format_windows_error)?;
        }
        Ok(())
    });
}

#[no_mangle]
pub extern "system" fn Java_io_github_barsia_speqa_webview_internal_windows_WinWebView2Bridge_evaluateJavaScriptNative(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    handle: jlong,
    eval_id: jlong,
    script: JString<'_>,
) {
    let Ok(script) = jstring_to_string(&mut env, script) else {
        return;
    };
    run_with_handle(&mut env, handle, |native| {
        let (webview, handler, handler_id) = {
            let mut view = native.borrow_mut();
            let webview = view
                .webview
                .clone()
                .ok_or_else(|| "WebView2 is not ready".to_string())?;
            let callbacks = view.callbacks.clone();
            let handler_id = view.next_script_handler_id;
            view.next_script_handler_id += 1;
            let native_for_callback = native.clone();
            let handler =
                ExecuteScriptCompletedHandler::create(Box::new(move |error_code, result| {
                    remove_execute_script_handler(&native_for_callback, handler_id);
                    match error_code {
                        Ok(()) => callbacks.on_evaluation_result(eval_id, result),
                        Err(error) => {
                            callbacks.on_evaluation_error(eval_id, format_windows_error(error))
                        }
                    }
                    Ok(())
                }));
            view.execute_script_handlers
                .push((handler_id, handler.clone()));
            (webview, handler, handler_id)
        };
        let result = unsafe { webview.ExecuteScript(&HSTRING::from(script), &handler) };
        if let Err(error) = result {
            remove_execute_script_handler(&native, handler_id);
            return Err(format_windows_error(error));
        }
        Ok(())
    });
}

#[no_mangle]
pub extern "system" fn Java_io_github_barsia_speqa_webview_internal_windows_WinWebView2Bridge_deliverJsonToJavaScriptNative(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    handle: jlong,
    raw_json: JString<'_>,
) {
    let Ok(raw_json) = jstring_to_string(&mut env, raw_json) else {
        return;
    };
    let script = format!(
        "window.__KWRY__ && window.__KWRY__.__deliver({});",
        js_string_literal(&raw_json)
    );
    run_with_handle(&mut env, handle, |native| {
        let (webview, handler, handler_id) = {
            let mut view = native.borrow_mut();
            let webview = view
                .webview
                .clone()
                .ok_or_else(|| "WebView2 is not ready".to_string())?;
            let handler_id = view.next_script_handler_id;
            view.next_script_handler_id += 1;
            let native_for_callback = native.clone();
            let handler = ExecuteScriptCompletedHandler::create(Box::new(move |_, _| {
                remove_execute_script_handler(&native_for_callback, handler_id);
                Ok(())
            }));
            view.execute_script_handlers
                .push((handler_id, handler.clone()));
            (webview, handler, handler_id)
        };
        let result = unsafe { webview.ExecuteScript(&HSTRING::from(script), &handler) };
        if let Err(error) = result {
            remove_execute_script_handler(&native, handler_id);
            return Err(format_windows_error(error));
        }
        Ok(())
    });
}

#[no_mangle]
pub extern "system" fn Java_io_github_barsia_speqa_webview_internal_windows_WinWebView2Bridge_pumpMessagesNative(
    _env: JNIEnv<'_>,
    _class: JClass<'_>,
    max_messages: jint,
) -> jboolean {
    if pump_pending_messages_limited(max_messages.max(1) as u32) {
        1
    } else {
        0
    }
}

fn create_native(
    env: &mut JNIEnv<'_>,
    parent_hwnd: jlong,
    user_data_dir: JString<'_>,
    callbacks: JObject<'_>,
) -> BridgeResult<jlong> {
    unsafe {
        let _ = CoInitializeEx(None, COINIT_APARTMENTTHREADED).ok();
    }

    let callbacks = Rc::new(JavaCallbacks {
        vm: env.get_java_vm().map_err(format_jni_error)?,
        object: env.new_global_ref(callbacks).map_err(format_jni_error)?,
    });
    let parent = HWND(parent_hwnd as *mut c_void);
    let hwnd = create_container_hwnd(parent)?;
    let user_data_dir = jstring_to_string(env, user_data_dir)?;

    let native = Rc::new(RefCell::new(NativeWebView {
        handle: 0,
        parent,
        hwnd,
        env: None,
        controller: None,
        webview: None,
        environment_completed_handler: None,
        controller_completed_handler: None,
        execute_script_handlers: Vec::new(),
        next_script_handler_id: 0,
        web_message_token: EventRegistrationToken::default(),
        accelerator_key_pressed_token: None,
        callbacks,
        destroyed: false,
        visible: true,
        x: 0,
        y: 0,
        width: 0,
        height: 0,
        scale: 1.0,
    }));

    let raw = Rc::into_raw(native.clone()) as jlong;
    native.borrow_mut().handle = raw;
    begin_create_environment(native.clone(), user_data_dir)?;
    pump_messages_until_controller_ready(&native, Duration::from_secs(5));
    Ok(raw)
}

fn begin_create_environment(native: NativeHandle, user_data_dir: String) -> BridgeResult<()> {
    let options = CoreWebView2EnvironmentOptions::default();
    let user_data_dir = HSTRING::from(user_data_dir);
    let native_for_callback = native.clone();
    let handler = CreateCoreWebView2EnvironmentCompletedHandler::create(Box::new(
        move |error_code, environment| {
            if let Ok(mut view) = native_for_callback.try_borrow_mut() {
                view.environment_completed_handler = None;
            }
            if let Err(error) = error_code {
                fail_create(&native_for_callback, format_windows_error(error));
                return Ok(());
            }

            let Some(environment) = environment else {
                fail_create(
                    &native_for_callback,
                    "WebView2 environment callback returned null".to_string(),
                );
                return Ok(());
            };

            if let Err(message) = begin_create_controller(native_for_callback.clone(), environment)
            {
                fail_create(&native_for_callback, message);
            }
            Ok(())
        },
    ));
    native.borrow_mut().environment_completed_handler = Some(handler.clone());
    unsafe {
        CreateCoreWebView2EnvironmentWithOptions(
            PCWSTR::null(),
            &user_data_dir,
            &ICoreWebView2EnvironmentOptions::from(options),
            &handler,
        )
        .map_err(format_windows_error)?;
    }
    Ok(())
}

fn begin_create_controller(
    native: NativeHandle,
    environment: ICoreWebView2Environment,
) -> BridgeResult<()> {
    let hwnd = native.borrow().hwnd;
    let environment_for_callback = environment.clone();
    let native_for_callback = native.clone();
    let handler = CreateCoreWebView2ControllerCompletedHandler::create(Box::new(
        move |error_code, controller| {
            if let Ok(mut view) = native_for_callback.try_borrow_mut() {
                view.controller_completed_handler = None;
            }
            if let Err(error) = error_code {
                fail_create(&native_for_callback, format_windows_error(error));
                return Ok(());
            }

            let Some(controller) = controller else {
                fail_create(
                    &native_for_callback,
                    "WebView2 controller callback returned null".to_string(),
                );
                return Ok(());
            };

            match finish_create(
                native_for_callback.clone(),
                environment_for_callback.clone(),
                controller,
            ) {
                Ok(()) => {}
                Err(message) => fail_create(&native_for_callback, message),
            }
            Ok(())
        },
    ));
    native.borrow_mut().controller_completed_handler = Some(handler.clone());
    unsafe {
        environment
            .CreateCoreWebView2Controller(hwnd, &handler)
            .map_err(format_windows_error)?;
    }
    Ok(())
}

fn finish_create(
    native: NativeHandle,
    environment: ICoreWebView2Environment,
    controller: ICoreWebView2Controller,
) -> BridgeResult<()> {
    let webview = unsafe { controller.CoreWebView2().map_err(format_windows_error)? };
    let token = attach_ipc_handler(&webview, native.clone())?;
    let accelerator_token = attach_accelerator_key_handler(&controller, native.clone())?;

    let (callbacks, handle, hwnd, controller, visible, x, y, width, height, scale) = {
        let mut view = native.borrow_mut();
        if view.destroyed {
            return Ok(());
        }

        view.web_message_token = token;
        view.accelerator_key_pressed_token = Some(accelerator_token);
        view.env = Some(environment);
        view.controller = Some(controller);
        view.webview = Some(webview);
        (
            view.callbacks.clone(),
            view.handle,
            view.hwnd,
            view.controller.clone(),
            view.visible,
            view.x,
            view.y,
            view.width,
            view.height,
            view.scale,
        )
    };

    apply_bounds_values(hwnd, controller.as_ref(), x, y, width, height, scale)?;
    unsafe {
        if let Some(controller) = &controller {
            controller
                .SetIsVisible(visible)
                .map_err(format_windows_error)?;
        }
        let _ = ShowWindow(hwnd, if visible { SW_SHOW } else { SW_HIDE });
    }

    callbacks.on_created(handle);
    Ok(())
}

fn attach_ipc_handler(
    webview: &ICoreWebView2,
    native: NativeHandle,
) -> BridgeResult<EventRegistrationToken> {
    let mut token = EventRegistrationToken::default();
    unsafe {
        webview
            .add_WebMessageReceived(
                &WebMessageReceivedEventHandler::create(Box::new(move |_, args| {
                    let Some(args) = args else {
                        return Ok(());
                    };

                    let mut message = PWSTR::null();
                    args.TryGetWebMessageAsString(&mut message)?;
                    let message = take_pwstr(message);
                    if let Ok(view) = native.try_borrow() {
                        view.callbacks.on_message(message);
                    }
                    Ok(())
                })),
                &mut token,
            )
            .map_err(format_windows_error)?;
    }
    Ok(token)
}

fn attach_accelerator_key_handler(
    controller: &ICoreWebView2Controller,
    native: NativeHandle,
) -> BridgeResult<EventRegistrationToken> {
    let mut token = EventRegistrationToken::default();
    unsafe {
        controller
            .add_AcceleratorKeyPressed(
                &AcceleratorKeyPressedEventHandler::create(Box::new(move |_, args| {
                    let Some(args) = args else {
                        return Ok(());
                    };

                    let mut key_event_kind = COREWEBVIEW2_KEY_EVENT_KIND::default();
                    args.KeyEventKind(&mut key_event_kind)?;
                    let mut virtual_key = 0;
                    args.VirtualKey(&mut virtual_key)?;
                    let mut key_event_lparam = 0;
                    args.KeyEventLParam(&mut key_event_lparam)?;

                    let handled = native
                        .try_borrow()
                        .map(|view| {
                            view.callbacks.on_accelerator_key_pressed(
                                key_event_kind.0,
                                virtual_key as jint,
                                current_modifier_flags(),
                                key_event_lparam,
                            )
                        })
                        .unwrap_or(false);
                    if handled {
                        args.SetHandled(true)?;
                    }
                    Ok(())
                })),
                &mut token,
            )
            .map_err(format_windows_error)?;
    }
    Ok(token)
}

fn fail_create(native: &NativeHandle, message: String) {
    let callbacks = match native.try_borrow() {
        Ok(view) => view.callbacks.clone(),
        Err(_) => return,
    };
    if let Ok(mut view) = native.try_borrow_mut() {
        view.destroy();
    }
    callbacks.on_create_failed(message);
}

fn remove_execute_script_handler(native: &NativeHandle, handler_id: u64) {
    if let Ok(mut view) = native.try_borrow_mut() {
        view.execute_script_handlers
            .retain(|(id, _)| *id != handler_id);
    }
}

fn create_container_hwnd(parent: HWND) -> BridgeResult<HWND> {
    unsafe extern "system" fn window_proc(
        hwnd: HWND,
        msg: u32,
        wparam: WPARAM,
        lparam: LPARAM,
    ) -> LRESULT {
        if msg == WM_SETFOCUS {
            if let Ok(child) = GetWindow(hwnd, GW_CHILD) {
                let _ = SetFocus(Some(child));
            }
        }
        DefWindowProcW(hwnd, msg, wparam, lparam)
    }

    let class_name = w!("IJ_WEBVIEW2_BRIDGE");
    let class = WNDCLASSEXW {
        cbSize: std::mem::size_of::<WNDCLASSEXW>() as u32,
        style: CS_HREDRAW | CS_VREDRAW,
        lpfnWndProc: Some(window_proc),
        cbClsExtra: 0,
        cbWndExtra: 0,
        hInstance: unsafe { HINSTANCE(GetModuleHandleW(PCWSTR::null()).unwrap_or_default().0) },
        hIcon: HICON::default(),
        hCursor: HCURSOR::default(),
        hbrBackground: HBRUSH::default(),
        lpszMenuName: PCWSTR::null(),
        lpszClassName: class_name,
        hIconSm: HICON::default(),
    };
    unsafe {
        RegisterClassExW(&class);
    }

    let hwnd = unsafe {
        CreateWindowExW(
            WINDOW_EX_STYLE::default(),
            class_name,
            PCWSTR::null(),
            WS_CHILD | WS_CLIPCHILDREN | WS_CLIPSIBLINGS | WS_VISIBLE,
            0,
            0,
            0,
            0,
            Some(parent),
            None,
            GetModuleHandleW(PCWSTR::null()).map(Into::into).ok(),
            None,
        )
        .map_err(format_windows_error)?
    };

    Ok(hwnd)
}

fn apply_bounds(view: &NativeWebView) -> BridgeResult<()> {
    apply_bounds_values(
        view.hwnd,
        view.controller.as_ref(),
        view.x,
        view.y,
        view.width,
        view.height,
        view.scale,
    )
}

fn apply_bounds_values(
    hwnd: HWND,
    controller: Option<&ICoreWebView2Controller>,
    x: i32,
    y: i32,
    width: i32,
    height: i32,
    scale: f64,
) -> BridgeResult<()> {
    if hwnd.0.is_null() {
        return Ok(());
    }

    let x = scale_to_i32(x, scale);
    let y = scale_to_i32(y, scale);
    let width = scale_to_i32(width, scale).max(0);
    let height = scale_to_i32(height, scale).max(0);

    unsafe {
        SetWindowPos(
            hwnd,
            None,
            x,
            y,
            width,
            height,
            SWP_ASYNCWINDOWPOS | SWP_NOACTIVATE | SWP_NOZORDER,
        )
        .map_err(format_windows_error)?;

        if let Some(controller) = controller {
            controller
                .SetBounds(RECT {
                    left: 0,
                    top: 0,
                    right: width,
                    bottom: height,
                })
                .map_err(format_windows_error)?;
            controller
                .NotifyParentWindowPositionChanged()
                .map_err(format_windows_error)?;
        }
    }
    Ok(())
}

fn run_with_handle<F>(env: &mut JNIEnv<'_>, handle: jlong, action: F)
where
    F: FnOnce(NativeHandle) -> BridgeResult<()>,
{
    if handle == 0 {
        let _ = env.throw_new(
            "java/lang/IllegalStateException",
            "WebView2 native handle is 0",
        );
        return;
    }

    let native = unsafe {
        let native = Rc::from_raw(handle as *const RefCell<NativeWebView>);
        let cloned = native.clone();
        let _ = Rc::into_raw(native);
        cloned
    };

    if let Err(message) = action(native) {
        let _ = env.throw_new("java/lang/IllegalStateException", message);
    }
}

fn pump_messages_until_controller_ready(native: &NativeHandle, timeout: Duration) {
    pump_messages_until(
        || {
            native
                .try_borrow()
                .map(|view| view.destroyed || view.controller.is_some())
                .unwrap_or(true)
        },
        timeout,
    );

    if let Ok(view) = native.try_borrow() {
        if !view.destroyed && view.controller.is_none() {
            view.callbacks.on_log(
                2,
                "WinWebView2Bridge: controller callback did not arrive during bounded message pump"
                    .to_string(),
            );
        }
    }
}

fn pump_messages_until<F>(condition: F, timeout: Duration)
where
    F: Fn() -> bool,
{
    let deadline = Instant::now() + timeout;
    while !condition() && Instant::now() < deadline {
        if !pump_pending_messages() {
            sleep(Duration::from_millis(10));
        }
    }
}

fn pump_pending_messages() -> bool {
    let mut processed_message = false;
    unsafe {
        let mut message = MSG::default();
        while PeekMessageW(&mut message, None, 0, 0, PM_REMOVE).as_bool() {
            processed_message = true;
            let _ = TranslateMessage(&message);
            DispatchMessageW(&message);
        }
    }
    processed_message
}

fn pump_pending_messages_limited(max_messages: u32) -> bool {
    let mut processed_message = false;
    let deadline = Instant::now() + Duration::from_millis(2);
    let mut processed_count = 0;
    unsafe {
        let mut message = MSG::default();
        while processed_count < max_messages && Instant::now() < deadline {
            if !PeekMessageW(&mut message, None, 0, 0, PM_NOREMOVE).as_bool() {
                break;
            }
            if should_leave_message_for_awt(message.message) {
                break;
            }
            if !PeekMessageW(&mut message, None, 0, 0, PM_REMOVE).as_bool() {
                break;
            }
            processed_message = true;
            processed_count += 1;
            let _ = TranslateMessage(&message);
            DispatchMessageW(&message);
        }
    }
    processed_message
}

fn should_leave_message_for_awt(message: u32) -> bool {
    matches!(
        message,
        WM_ACTIVATE
            | WM_SETFOCUS
            | WM_KILLFOCUS
            | WM_ACTIVATEAPP
            | WM_SETCURSOR
            | WM_MOUSEACTIVATE
            | WM_CAPTURECHANGED
            | WM_ENTERSIZEMOVE
            | WM_EXITSIZEMOVE
    ) || is_message_in_range(message, WM_KEYFIRST, WM_KEYLAST)
        || is_message_in_range(message, WM_MOUSEFIRST, WM_MOUSELAST)
        || is_message_in_range(message, WM_NCMOUSEMOVE, WM_NCXBUTTONDBLCLK)
        || is_message_in_range(message, WM_POINTERUPDATE, WM_POINTERROUTEDRELEASED)
}

fn is_message_in_range(message: u32, first: u32, last: u32) -> bool {
    message >= first && message <= last
}

fn current_modifier_flags() -> jint {
    let mut flags = 0;
    unsafe {
        if is_key_down(VK_SHIFT) {
            flags |= MODIFIER_SHIFT;
        }
        if is_key_down(VK_CONTROL) {
            flags |= MODIFIER_CONTROL;
        }
        if is_key_down(VK_MENU) {
            flags |= MODIFIER_ALT;
        }
        if is_key_down(VK_LWIN) || is_key_down(VK_RWIN) {
            flags |= MODIFIER_META;
        }
    }
    flags
}

unsafe fn is_key_down(virtual_key: VIRTUAL_KEY) -> bool {
    (GetKeyState(virtual_key.0 as i32) as u16 & 0x8000) != 0
}

fn jstring_to_string(env: &mut JNIEnv<'_>, value: JString<'_>) -> BridgeResult<String> {
    env.get_string(&value)
        .map(|value| value.into())
        .map_err(format_jni_error)
}

fn js_string_literal(value: &str) -> String {
    let mut result = String::with_capacity(value.len() + 2);
    result.push('\'');
    for ch in value.chars() {
        match ch {
            '\\' => result.push_str("\\\\"),
            '\'' => result.push_str("\\'"),
            '"' => result.push_str("\\\""),
            '\n' => result.push_str("\\n"),
            '\r' => result.push_str("\\r"),
            '\t' => result.push_str("\\t"),
            '\u{2028}' => result.push_str("\\u2028"),
            '\u{2029}' => result.push_str("\\u2029"),
            _ => result.push(ch),
        }
    }
    result.push('\'');
    result
}

fn scale_to_i32(value: i32, scale: f64) -> i32 {
    ((value as f64) * scale).round() as i32
}

fn format_windows_error<E: std::fmt::Debug>(error: E) -> String {
    format!("{error:?}")
}

fn format_jni_error(error: jni::errors::Error) -> String {
    format!("{error:?}")
}
