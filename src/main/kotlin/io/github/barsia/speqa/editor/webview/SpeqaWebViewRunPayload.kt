// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package io.github.barsia.speqa.editor.webview

import io.github.barsia.speqa.editor.ui.steps.mergeBodyBlocks
import io.github.barsia.speqa.model.Attachment
import io.github.barsia.speqa.model.DescriptionBlock
import io.github.barsia.speqa.model.Link
import io.github.barsia.speqa.model.PreconditionsBlock
import io.github.barsia.speqa.model.Priority
import io.github.barsia.speqa.model.StepResult
import io.github.barsia.speqa.model.TestRun
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.format.DateTimeFormatter

private val headerDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm")

internal object SpeqaWebViewRunPayload {
  fun build(
    run: TestRun,
    theme: String,
    createdLabel: String = "",
    restorePreviewTextFocus: Boolean = true,
  ): JsonObject = buildJsonObject {
    put("mode", "run")
    put("theme", theme)
    put("restorePreviewTextFocus", restorePreviewTextFocus)
    put("id", run.id?.toString().orEmpty())
    put("idPrefix", "TR-")
    put("title", run.title)
    put("priority", (run.priority ?: Priority.NORMAL).label)
    put("runResult", run.result.label)
    put("resultOverride", run.resultOverride)
    put("runner", run.runner)
    put("createdLabel", createdLabel)
    put("startedLabel", run.startedAt?.let { headerDateFormatter.format(it) }.orEmpty())
    put("finishedLabel", run.finishedAt?.let { headerDateFormatter.format(it) }.orEmpty())
    put("environment", stringArray(run.environment))
    put("tags", stringArray(run.tags))
    put("attachments", attachmentArray(run.attachments))
    put("links", linkArray(run.links))
    put("description", mergeBodyBlocks(run.bodyBlocks, DescriptionBlock::class.java))
    put("preconditions", mergeBodyBlocks(run.bodyBlocks, PreconditionsBlock::class.java))
    put("steps", stepResultArray(run.stepResults))
  }

  private fun stringArray(values: List<String>): JsonArray = buildJsonArray {
    values.forEach { add(it) }
  }

  private fun attachmentArray(values: List<Attachment>): JsonArray = buildJsonArray {
    values.forEach { attachment ->
      add(buildJsonObject { put("path", attachment.path) })
    }
  }

  private fun linkArray(values: List<Link>): JsonArray = buildJsonArray {
    values.forEach { link ->
      add(
        buildJsonObject {
          put("title", link.title)
          put("url", link.url)
        },
      )
    }
  }

  private fun stepResultArray(values: List<StepResult>): JsonArray = buildJsonArray {
    values.forEachIndexed { index, result ->
      add(
        buildJsonObject {
          put("index", index)
          put("action", result.action)
          put("expected", result.expected)
          put("tickets", stringArray(result.tickets))
          put("attachments", attachmentArray(result.attachments))
          put("links", linkArray(result.links))
          put("verdict", result.verdict.label)
          put("comment", result.comment)
        },
      )
    }
  }
}
