package io.github.barsia.speqa.editor.webview

import io.github.barsia.speqa.editor.ui.steps.mergeBodyBlocks
import io.github.barsia.speqa.model.Attachment
import io.github.barsia.speqa.model.DescriptionBlock
import io.github.barsia.speqa.model.Link
import io.github.barsia.speqa.model.PreconditionsBlock
import io.github.barsia.speqa.model.Priority
import io.github.barsia.speqa.model.Status
import io.github.barsia.speqa.model.TestCase
import io.github.barsia.speqa.model.TestStep
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal object SpeqaWebViewPreviewPayload {
  fun build(
    testCase: TestCase,
    theme: String,
    createdLabel: String = "",
    updatedLabel: String = "",
    restorePreviewTextFocus: Boolean = true,
  ): JsonObject = buildJsonObject {
    put("theme", theme)
    put("restorePreviewTextFocus", restorePreviewTextFocus)
    put("id", testCase.id?.toString().orEmpty())
    put("idPrefix", "TC-")
    put("title", testCase.title)
    put("priority", (testCase.priority ?: Priority.NORMAL).label)
    put("status", (testCase.status ?: Status.DRAFT).label)
    put("createdLabel", createdLabel)
    put("updatedLabel", updatedLabel)
    put("environment", stringArray(testCase.environment.orEmpty()))
    put("tags", stringArray(testCase.tags.orEmpty()))
    put("attachments", attachmentArray(testCase.attachments))
    put("links", linkArray(testCase.links))
    put("description", mergeBodyBlocks(testCase.bodyBlocks, DescriptionBlock::class.java))
    put("preconditions", mergeBodyBlocks(testCase.bodyBlocks, PreconditionsBlock::class.java))
    put("steps", stepArray(testCase.steps))
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

  private fun stepArray(values: List<TestStep>): JsonArray = buildJsonArray {
    values.forEachIndexed { index, step ->
      add(
        buildJsonObject {
          put("index", index)
          put("action", step.action)
          put("expected", step.expected.orEmpty())
          put("tickets", stringArray(step.tickets))
          put("attachments", attachmentArray(step.attachments))
          put("links", linkArray(step.links))
        },
      )
    }
  }
}
