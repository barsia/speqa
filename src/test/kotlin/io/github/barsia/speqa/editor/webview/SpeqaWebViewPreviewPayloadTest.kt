package io.github.barsia.speqa.editor.webview

import io.github.barsia.speqa.model.DescriptionBlock
import io.github.barsia.speqa.model.PreconditionsBlock
import io.github.barsia.speqa.model.Priority
import io.github.barsia.speqa.model.Status
import io.github.barsia.speqa.model.TestCase
import io.github.barsia.speqa.model.TestStep
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.boolean
import org.junit.Assert.assertEquals
import org.junit.Test

class SpeqaWebViewPreviewPayloadTest {
  @Test
  fun `build includes editable test case fields`() {
    val payload = SpeqaWebViewPreviewPayload.build(
      TestCase(
        id = 42,
        title = "Checkout",
        priority = Priority.CRITICAL,
        status = Status.READY,
        environment = listOf("macOS", "Chrome"),
        tags = listOf("smoke"),
        bodyBlocks = listOf(
          DescriptionBlock("Validate **checkout**"),
          PreconditionsBlock(markdown = "User is signed in"),
        ),
        steps = listOf(TestStep(action = "Open cart", expected = "Cart is visible", tickets = listOf("QA-1"))),
      ),
      theme = "dark",
    )

    assertEquals("dark", payload["theme"]!!.jsonPrimitive.content)
    assertEquals(true, payload["restorePreviewTextFocus"]!!.jsonPrimitive.boolean)
    assertEquals("42", payload["id"]!!.jsonPrimitive.content)
    assertEquals("Checkout", payload["title"]!!.jsonPrimitive.content)
    assertEquals("critical", payload["priority"]!!.jsonPrimitive.content)
    assertEquals("ready", payload["status"]!!.jsonPrimitive.content)
    assertEquals("macOS", payload["environment"]!!.jsonArray[0].jsonPrimitive.content)
    assertEquals("smoke", payload["tags"]!!.jsonArray[0].jsonPrimitive.content)
    assertEquals("Validate **checkout**", payload["description"]!!.jsonPrimitive.content)
    assertEquals("User is signed in", payload["preconditions"]!!.jsonPrimitive.content)

    val step = payload["steps"]!!.jsonArray[0].jsonObject
    assertEquals("Open cart", step["action"]!!.jsonPrimitive.content)
    assertEquals("Cart is visible", step["expected"]!!.jsonPrimitive.content)
    assertEquals("QA-1", step["tickets"]!!.jsonArray[0].jsonPrimitive.content)
  }

  @Test
  fun `build can disable preview text focus restoration for editor driven snapshots`() {
    val payload = SpeqaWebViewPreviewPayload.build(
      TestCase(title = "Checkout"),
      theme = "light",
      restorePreviewTextFocus = false,
    )

    assertEquals(false, payload["restorePreviewTextFocus"]!!.jsonPrimitive.boolean)
  }
}
