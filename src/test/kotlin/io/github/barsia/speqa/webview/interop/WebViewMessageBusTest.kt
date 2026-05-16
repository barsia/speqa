package io.github.barsia.speqa.webview.interop

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class WebViewMessageBusTest {
  @Test
  fun `publishRaw sends a JSON RPC notification envelope`() {
    val sent = mutableListOf<String>()
    val bus = WebViewMessageBus(outgoingSink = sent::add)

    bus.publishRaw("speqa/test", buildJsonObject { put("value", "ok") })

    assertEquals(1, sent.size)
    val envelope = Json.decodeFromString(WebViewEnvelope.serializer(), sent.single())
    assertEquals("2.0", envelope.jsonrpc)
    assertEquals("speqa/test", envelope.method)
    assertEquals("ok", envelope.params!!.jsonObject["value"]!!.jsonPrimitive.content)
  }

  @Test
  fun `publishTyped serializes typed params into a JSON RPC notification envelope`() {
    val sent = mutableListOf<String>()
    val bus = WebViewMessageBus(outgoingSink = sent::add)

    bus.publishTyped("speqa/typed", TypedParams(value = "ok", count = 2))

    val envelope = Json.decodeFromString(WebViewEnvelope.serializer(), sent.single())
    val params = envelope.params!!.jsonObject
    assertEquals("speqa/typed", envelope.method)
    assertEquals("ok", params["value"]!!.jsonPrimitive.content)
    assertEquals("2", params["count"]!!.jsonPrimitive.content)
  }

  @Test
  fun `onIncomingMessage dispatches only to subscribers for the envelope method`() {
    val bus = WebViewMessageBus(outgoingSink = {})
    val scope = CoroutineScope(Job())
    val received = mutableListOf<String>()
    bus.subscribe(scope, "speqa/target") { params ->
      received += params!!.jsonObject["value"]!!.jsonPrimitive.content
    }
    bus.subscribe(scope, "speqa/other") {
      fail("unrelated subscribers must not receive target messages")
    }

    bus.onIncomingMessage(envelope("speqa/target", "first"))
    bus.onIncomingMessage(envelope("speqa/missing", "ignored"))

    assertEquals(listOf("first"), received)
  }

  @Test
  fun `subscriber failure does not prevent later subscribers from receiving the same message`() {
    val bus = WebViewMessageBus(outgoingSink = {})
    val scope = CoroutineScope(Job())
    val received = mutableListOf<String>()
    bus.subscribe(scope, "speqa/event") {
      error("first subscriber failed")
    }
    bus.subscribe(scope, "speqa/event") { params ->
      received += params!!.jsonObject["value"]!!.jsonPrimitive.content
    }

    bus.onIncomingMessage(envelope("speqa/event", "delivered"))

    assertEquals(listOf("delivered"), received)
  }

  @Test
  fun `malformed incoming message is dropped without notifying subscribers`() {
    val bus = WebViewMessageBus(outgoingSink = {})
    val scope = CoroutineScope(Job())
    var called = false
    bus.subscribe(scope, "speqa/event") {
      called = true
    }

    bus.onIncomingMessage("{not json")

    assertTrue(!called)
  }

  @Test
  fun `cancelling subscription scope removes the handler`() {
    val bus = WebViewMessageBus(outgoingSink = {})
    val job = Job()
    val scope = CoroutineScope(job)
    var calls = 0
    bus.subscribe(scope, "speqa/event") {
      calls += 1
    }

    bus.onIncomingMessage(envelope("speqa/event", "before"))
    job.cancel()
    bus.onIncomingMessage(envelope("speqa/event", "after"))

    assertEquals(1, calls)
  }

  @Test
  fun `request response RPC fails explicitly until implemented`() = runBlocking {
    val bus = WebViewMessageBus(outgoingSink = {})

    try {
      bus.request(
        method = "speqa/request",
        params = TypedParams(value = "request", count = 1),
        paramsSerializer = TypedParams.serializer(),
        resultSerializer = TypedParams.serializer(),
      )
      fail("request should be an explicit stub")
    }
    catch (error: IllegalStateException) {
      assertTrue(error.message!!.contains("request/response RPC is not implemented"))
    }
  }

  private fun envelope(method: String, value: String): String =
    Json.encodeToString(
      WebViewEnvelope.serializer(),
      WebViewEnvelope(
        method = method,
        params = buildJsonObject { put("value", value) },
      ),
    )

  @Serializable
  private data class TypedParams(
    val value: String,
    val count: Int,
  )
}
