// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package io.github.barsia.speqa.run

import io.github.barsia.speqa.model.RunCase
import io.github.barsia.speqa.model.RunResult
import org.junit.Assert.assertEquals
import org.junit.Test

class RunProgressTextTest {
  @Test
  fun `caseProgress counts cases with a terminal result`() {
    val cases = listOf(
      RunCase(caseId = 1, result = RunResult.PASSED),
      RunCase(caseId = 2, result = RunResult.FAILED),
      RunCase(caseId = 3, result = RunResult.NOT_STARTED),
      RunCase(caseId = 4, result = RunResult.IN_PROGRESS),
    )
    assertEquals(2 to 4, RunProgressText.caseProgress(cases))
  }

  @Test
  fun `caseProgress of empty is zero of zero`() {
    assertEquals(0 to 0, RunProgressText.caseProgress(emptyList()))
  }

  @Test
  fun `zero of zero is rendered`() {
    assertEquals("Progress: 0/0", runProgressText(stepCount = 0, completedCount = 0))
  }

  @Test
  fun `partial run is rendered`() {
    assertEquals("Progress: 2/5", runProgressText(stepCount = 5, completedCount = 2))
  }

  @Test
  fun `complete run is rendered`() {
    assertEquals("Progress: 5/5", runProgressText(stepCount = 5, completedCount = 5))
  }

  @Test
  fun `completed exceeds total is clamped to total`() {
    assertEquals("Progress: 5/5", runProgressText(stepCount = 5, completedCount = 99))
  }

  @Test
  fun `negative inputs become zero`() {
    assertEquals("Progress: 0/0", runProgressText(stepCount = -3, completedCount = -1))
  }
}
