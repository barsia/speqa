// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package io.github.barsia.speqa.editor.webview

import io.github.barsia.speqa.SpeqaBundle

internal fun rootFailureMessage(t: Throwable): String {
  val messages = generateSequence(t) { it.cause }
    .mapNotNull { it.message?.takeIf(String::isNotBlank) }
    .distinct()
    .toList()
  return when (messages.size) {
    0 -> t.javaClass.simpleName
    1 -> messages.single()
    else -> messages.joinToString(separator = " | ${SpeqaBundle.message("webview.failure.causedBy")}")
  }
}
