// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package io.github.barsia.speqa.editor.webview

import io.github.barsia.speqa.model.Link
import io.github.barsia.speqa.model.RunResult
import io.github.barsia.speqa.model.StepResult
import io.github.barsia.speqa.model.StepVerdict
import io.github.barsia.speqa.model.TestRun
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class SpeqaWebViewRunPayloadTest {
  @Test
  fun `payload exposes run mode flag and runResult label`() {
    val run = TestRun(
      title = "smoke",
      result = RunResult.IN_PROGRESS,
    )
    val json = SpeqaWebViewRunPayload.build(run, theme = "light")
    assertEquals("run", json["mode"]!!.jsonPrimitive.content)
    assertEquals("in_progress", json["runResult"]!!.jsonPrimitive.content)
  }

  @Test
  fun `step results carry verdict label and comment`() {
    val run = TestRun(
      stepResults = listOf(
        StepResult(action = "login", expected = "ok", verdict = StepVerdict.PASSED, comment = "fine"),
        StepResult(action = "logout", expected = "out", verdict = StepVerdict.FAILED, comment = "popup blocked"),
      ),
    )
    val json = SpeqaWebViewRunPayload.build(run, theme = "light")
    val steps: JsonArray = json["steps"]!!.jsonArray
    assertEquals(2, steps.size)
    val first: JsonObject = steps[0].jsonObject
    assertEquals(0, first["index"]!!.jsonPrimitive.content.toInt())
    assertEquals("login", first["action"]!!.jsonPrimitive.content)
    assertEquals("passed", first["verdict"]!!.jsonPrimitive.content)
    assertEquals("fine", first["comment"]!!.jsonPrimitive.content)
    val second: JsonObject = steps[1].jsonObject
    assertEquals("failed", second["verdict"]!!.jsonPrimitive.content)
    assertEquals("popup blocked", second["comment"]!!.jsonPrimitive.content)
  }

  @Test
  fun `shared fields match test case payload shape`() {
    val run = TestRun(
      title = "T",
      tags = listOf("a", "b"),
      environment = listOf("dev"),
      links = listOf(Link(title = "doc", url = "https://example.com")),
    )
    val json = SpeqaWebViewRunPayload.build(run, theme = "dark")
    assertEquals("dark", json["theme"]!!.jsonPrimitive.content)
    assertEquals("T", json["title"]!!.jsonPrimitive.content)
    val tags = json["tags"]!!.jsonArray.map { it.jsonPrimitive.content }
    assertEquals(listOf("a", "b"), tags)
    val env = json["environment"]!!.jsonArray.map { it.jsonPrimitive.content }
    assertEquals(listOf("dev"), env)
    val links = json["links"]!!.jsonArray.map { it.jsonObject }
    assertEquals(1, links.size)
    assertEquals("doc", links[0]["title"]!!.jsonPrimitive.content)
    assertEquals("https://example.com", links[0]["url"]!!.jsonPrimitive.content)
  }

  @Test
  fun `started and finished labels are formatted from LocalDateTime`() {
    val run = TestRun(
      startedAt = LocalDateTime.of(2026, 5, 10, 14, 30),
      finishedAt = LocalDateTime.of(2026, 5, 10, 15, 45),
    )
    val json = SpeqaWebViewRunPayload.build(
      run,
      theme = "light",
      createdLabel = "10-05-2026, 14:00",
    )
    assertEquals("10-05-2026, 14:00", json["createdLabel"]!!.jsonPrimitive.content)
    // Exact format must match headerDateFormatter; we assert non-emptiness and the year is present.
    val started = json["startedLabel"]!!.jsonPrimitive.content
    val finished = json["finishedLabel"]!!.jsonPrimitive.content
    assertEquals(true, started.contains("2026"))
    assertEquals(true, finished.contains("2026"))
  }

  @Test
  fun `payload exposes resultOverride flag`() {
    val auto = SpeqaWebViewRunPayload.build(TestRun(resultOverride = false), theme = "light")
    assertEquals(false, auto["resultOverride"]!!.jsonPrimitive.booleanOrNull)
    val pinned = SpeqaWebViewRunPayload.build(TestRun(resultOverride = true), theme = "light")
    assertEquals(true, pinned["resultOverride"]!!.jsonPrimitive.booleanOrNull)
  }

  @Test
  fun `started and finished labels are empty strings when null`() {
    val run = TestRun()
    val json = SpeqaWebViewRunPayload.build(run, theme = "light")
    assertEquals("", json["startedLabel"]!!.jsonPrimitive.content)
    assertEquals("", json["finishedLabel"]!!.jsonPrimitive.content)
    assertEquals("", json["createdLabel"]!!.jsonPrimitive.content)
  }
}
