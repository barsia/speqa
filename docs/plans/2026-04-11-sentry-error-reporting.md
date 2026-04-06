# Sentry Error Reporting — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let users report SpeQA plugin exceptions to Sentry via the standard IntelliJ error dialog, with stacktrace sanitization that strips all file paths and personal data.

**Architecture:** Three classes in `io.github.barsia.speqa.error` package — `SpeqaStacktraceSanitizer` extracts safe frames (class/method/line) from a `Throwable`, `SpeqaSentryClient` builds a Sentry envelope and POSTs it via `java.net.http.HttpClient`, `SpeqaErrorReportSubmitter` extends `ErrorReportSubmitter` and wires everything together. No Sentry SDK — just one HTTP POST per report.

**Tech Stack:** Kotlin, IntelliJ Platform SDK (`ErrorReportSubmitter`, `IdeaLoggingEvent`), `java.net.http.HttpClient` (JDK 21), Sentry Envelope API.

**Spec:** `docs/specs/2026-04-06-speqa-design.md` — section 14.

---

## File Structure

| File | Responsibility |
|------|---------------|
| Create: `src/main/kotlin/io/speqa/speqa/error/SpeqaStacktraceSanitizer.kt` | Takes a `Throwable`, returns list of sanitized frames (class, method, line). No file paths, no exception messages. |
| Create: `src/main/kotlin/io/speqa/speqa/error/SpeqaSentryClient.kt` | Builds Sentry envelope JSON from sanitized frames + metadata, sends HTTP POST. Fire-and-forget with 5s timeout. |
| Create: `src/main/kotlin/io/speqa/speqa/error/SpeqaErrorReportSubmitter.kt` | `ErrorReportSubmitter` implementation — `getReportActionText()`, `getPrivacyNoticeText()`, `submit()`. Orchestrates sanitizer + client. |
| Modify: `src/main/resources/META-INF/plugin.xml` | Register `<errorReportSubmitter>` extension |
| Modify: `src/main/resources/messages/SpeqaBundle.properties` | Add bundle keys for privacy notice and report action text |
| Create: `src/test/kotlin/io/speqa/speqa/error/SpeqaStacktraceSanitizerTest.kt` | Tests for sanitizer — verifies paths stripped, class/method/line preserved |
| Create: `src/test/kotlin/io/speqa/speqa/error/SpeqaSentryClientTest.kt` | Tests for envelope JSON structure — verifies payload format, no sensitive data |

---

### Task 1: Bundle Keys

**Files:**
- Modify: `src/main/resources/messages/SpeqaBundle.properties`

- [ ] **Step 1: Add error reporting bundle keys**

Append to `SpeqaBundle.properties`:

```properties
# --- Error Reporting ---
error.report.actionText=Report to SpeQA
error.report.privacyNotice=Error reports are sent to SpeQA\u2019s error tracker. Only exception class names, method names, and line numbers are included. File paths, project data, and personal information are not sent.
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/messages/SpeqaBundle.properties
git commit -m "feat: add error reporting bundle keys"
```

---

### Task 2: Stacktrace Sanitizer

**Files:**
- Create: `src/test/kotlin/io/speqa/speqa/error/SpeqaStacktraceSanitizerTest.kt`
- Create: `src/main/kotlin/io/speqa/speqa/error/SpeqaStacktraceSanitizer.kt`

- [ ] **Step 1: Write the failing test**

Create `src/test/kotlin/io/speqa/speqa/error/SpeqaStacktraceSanitizerTest.kt`:

```kotlin
package io.github.barsia.speqa.error

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeqaStacktraceSanitizerTest {

    @Test
    fun `extracts class name, method name, and line number from throwable`() {
        val exception = RuntimeException("secret message with /Users/john/project/file.kt")
        val result = SpeqaStacktraceSanitizer.sanitize(exception)

        assertTrue(result.exceptionClass == "java.lang.RuntimeException")
        assertTrue(result.frames.isNotEmpty())
        val frame = result.frames.first()
        assertTrue(frame.module.isNotEmpty())
        assertTrue(frame.function.isNotEmpty())
        assertTrue(frame.lineno > 0)
    }

    @Test
    fun `exception message is not included in result`() {
        val exception = RuntimeException("/Users/john/secrets/password.txt")
        val result = SpeqaStacktraceSanitizer.sanitize(exception)

        assertEquals("", result.exceptionMessage)
    }

    @Test
    fun `file paths are not present in any frame`() {
        val exception = RuntimeException("test")
        val result = SpeqaStacktraceSanitizer.sanitize(exception)

        for (frame in result.frames) {
            assertTrue("module should not contain /", !frame.module.contains("/"))
            assertTrue("module should not contain \\", !frame.module.contains("\\"))
            assertTrue("function should not contain /", !frame.function.contains("/"))
        }
    }

    @Test
    fun `handles chained exceptions`() {
        val cause = IllegalArgumentException("root cause with /home/user/data")
        val exception = RuntimeException("wrapper", cause)
        val result = SpeqaStacktraceSanitizer.sanitize(exception)

        assertEquals("java.lang.RuntimeException", result.exceptionClass)
        assertTrue(result.frames.isNotEmpty())
    }

    @Test
    fun `handles exception with no stacktrace`() {
        val exception = object : RuntimeException("no stack") {
            override fun getStackTrace(): Array<StackTraceElement> = emptyArray()
        }
        val result = SpeqaStacktraceSanitizer.sanitize(exception)

        assertEquals("", result.exceptionMessage)
        assertTrue(result.frames.isEmpty())
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "io.github.barsia.speqa.error.SpeqaStacktraceSanitizerTest" --info`
Expected: FAIL — `SpeqaStacktraceSanitizer` does not exist yet.

- [ ] **Step 3: Write the implementation**

Create `src/main/kotlin/io/speqa/speqa/error/SpeqaStacktraceSanitizer.kt`:

```kotlin
package io.github.barsia.speqa.error

data class SanitizedFrame(
    val module: String,
    val function: String,
    val lineno: Int,
)

data class SanitizedStacktrace(
    val exceptionClass: String,
    val exceptionMessage: String,
    val frames: List<SanitizedFrame>,
)

object SpeqaStacktraceSanitizer {

    fun sanitize(throwable: Throwable): SanitizedStacktrace {
        val frames = throwable.stackTrace.map { element ->
            SanitizedFrame(
                module = element.className,
                function = element.methodName,
                lineno = element.lineNumber.coerceAtLeast(0),
            )
        }
        return SanitizedStacktrace(
            exceptionClass = throwable.javaClass.name,
            exceptionMessage = "",
            frames = frames,
        )
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "io.github.barsia.speqa.error.SpeqaStacktraceSanitizerTest" --info`
Expected: ALL PASS

- [ ] **Step 5: Commit**

```bash
git add src/test/kotlin/io/speqa/speqa/error/SpeqaStacktraceSanitizerTest.kt \
        src/main/kotlin/io/speqa/speqa/error/SpeqaStacktraceSanitizer.kt
git commit -m "feat: add stacktrace sanitizer for Sentry error reporting"
```

---

### Task 3: Sentry Client

**Files:**
- Create: `src/test/kotlin/io/speqa/speqa/error/SpeqaSentryClientTest.kt`
- Create: `src/main/kotlin/io/speqa/speqa/error/SpeqaSentryClient.kt`

- [ ] **Step 1: Write the failing test**

Create `src/test/kotlin/io/speqa/speqa/error/SpeqaSentryClientTest.kt`:

```kotlin
package io.github.barsia.speqa.error

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeqaSentryClientTest {

    @Test
    fun `buildEnvelope produces valid envelope with three lines`() {
        val stacktrace = SanitizedStacktrace(
            exceptionClass = "java.lang.NullPointerException",
            exceptionMessage = "",
            frames = listOf(
                SanitizedFrame("io.github.barsia.speqa.editor.SpeqaEditorSupport", "loadDocument", 42),
                SanitizedFrame("io.github.barsia.speqa.parser.TestCaseParser", "parse", 118),
            ),
        )
        val envelope = SpeqaSentryClient.buildEnvelope(
            stacktrace = stacktrace,
            pluginVersion = "0.1.0",
            ideVersion = "IU-261.1234",
            osInfo = "Mac OS X 14.0",
            javaVersion = "21.0.1",
            userComment = "Crashed when opening test case",
        )

        val lines = envelope.split("\n")
        assertTrue("Envelope must have 3 lines", lines.size == 3)

        // Line 1: envelope header with dsn
        assertTrue("Header must contain dsn", lines[0].contains("\"dsn\""))

        // Line 2: item header with type=event
        assertTrue("Item header must contain type", lines[1].contains("\"type\":\"event\""))

        // Line 3: event payload
        val payload = lines[2]
        assertTrue("Payload must contain exception type", payload.contains("NullPointerException"))
        assertTrue("Payload must contain module", payload.contains("io.github.barsia.speqa.editor.SpeqaEditorSupport"))
        assertTrue("Payload must contain function", payload.contains("loadDocument"))
        assertTrue("Payload must contain lineno", payload.contains("42"))
        assertTrue("Payload must contain plugin version tag", payload.contains("0.1.0"))
        assertTrue("Payload must contain IDE version tag", payload.contains("IU-261.1234"))
        assertTrue("Payload must contain user comment", payload.contains("Crashed when opening test case"))
    }

    @Test
    fun `buildEnvelope does not contain file paths`() {
        val stacktrace = SanitizedStacktrace(
            exceptionClass = "java.lang.RuntimeException",
            exceptionMessage = "",
            frames = listOf(
                SanitizedFrame("io.github.barsia.speqa.Main", "run", 10),
            ),
        )
        val envelope = SpeqaSentryClient.buildEnvelope(
            stacktrace = stacktrace,
            pluginVersion = "0.1.0",
            ideVersion = "IU-261.1234",
            osInfo = "Windows 11",
            javaVersion = "21",
            userComment = "",
        )

        assertFalse("Must not contain filename field", envelope.contains("\"filename\""))
        assertFalse("Must not contain abs_path field", envelope.contains("\"abs_path\""))
    }

    @Test
    fun `buildEnvelope with empty frames`() {
        val stacktrace = SanitizedStacktrace(
            exceptionClass = "java.lang.Error",
            exceptionMessage = "",
            frames = emptyList(),
        )
        val envelope = SpeqaSentryClient.buildEnvelope(
            stacktrace = stacktrace,
            pluginVersion = "0.1.0",
            ideVersion = "IU-261.1234",
            osInfo = "Linux",
            javaVersion = "21",
            userComment = "",
        )

        assertTrue("Must still produce 3 lines", envelope.split("\n").size == 3)
        assertTrue("Must contain exception class", envelope.contains("java.lang.Error"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "io.github.barsia.speqa.error.SpeqaSentryClientTest" --info`
Expected: FAIL — `SpeqaSentryClient` does not exist yet.

- [ ] **Step 3: Write the implementation**

Create `src/main/kotlin/io/speqa/speqa/error/SpeqaSentryClient.kt`:

```kotlin
package io.github.barsia.speqa.error

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant
import java.util.UUID

object SpeqaSentryClient {

    // TODO: Replace with real DSN from Sentry project
    private const val DSN = "https://PASTE_YOUR_PUBLIC_KEY@o0.ingest.sentry.io/0"

    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build()

    fun send(
        stacktrace: SanitizedStacktrace,
        pluginVersion: String,
        ideVersion: String,
        osInfo: String,
        javaVersion: String,
        userComment: String,
    ) {
        try {
            val envelope = buildEnvelope(stacktrace, pluginVersion, ideVersion, osInfo, javaVersion, userComment)
            val ingestUrl = extractIngestUrl(DSN)
            val request = HttpRequest.newBuilder()
                .uri(URI.create(ingestUrl))
                .header("Content-Type", "application/x-sentry-envelope")
                .timeout(Duration.ofSeconds(5))
                .POST(HttpRequest.BodyPublishers.ofString(envelope))
                .build()
            httpClient.send(request, HttpResponse.BodyHandlers.discarding())
        } catch (_: Exception) {
            // Silently ignore — do not bother the user with reporting failures
        }
    }

    fun buildEnvelope(
        stacktrace: SanitizedStacktrace,
        pluginVersion: String,
        ideVersion: String,
        osInfo: String,
        javaVersion: String,
        userComment: String,
    ): String {
        val eventId = UUID.randomUUID().toString().replace("-", "")
        val timestamp = Instant.now().epochSecond

        val framesJson = stacktrace.frames.joinToString(",") { frame ->
            """{"module":${jsonString(frame.module)},"function":${jsonString(frame.function)},"lineno":${frame.lineno}}"""
        }

        val commentJson = if (userComment.isNotBlank()) {
            ""","contexts":{"feedback":{"message":${jsonString(userComment)}}}"""
        } else {
            ""
        }

        val payload = """{"event_id":"$eventId","timestamp":$timestamp,"level":"error","exception":{"values":[{"type":${jsonString(stacktrace.exceptionClass)},"value":"","stacktrace":{"frames":[$framesJson]}}]},"tags":{"plugin_version":${jsonString(pluginVersion)},"ide_version":${jsonString(ideVersion)},"os":${jsonString(osInfo)},"java_version":${jsonString(javaVersion)}}$commentJson}"""

        val payloadBytes = payload.toByteArray(Charsets.UTF_8)
        val envelopeHeader = """{"event_id":"$eventId","dsn":"$DSN"}"""
        val itemHeader = """{"type":"event","length":${payloadBytes.size},"content_type":"application/json"}"""

        return "$envelopeHeader\n$itemHeader\n$payload"
    }

    private fun extractIngestUrl(dsn: String): String {
        // DSN format: https://<key>@<host>/<project_id>
        val uri = URI.create(dsn)
        val projectId = uri.path.trimStart('/')
        return "${uri.scheme}://${uri.host}/api/$projectId/envelope/"
    }

    private fun jsonString(value: String): String {
        val escaped = value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
        return "\"$escaped\""
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "io.github.barsia.speqa.error.SpeqaSentryClientTest" --info`
Expected: ALL PASS

- [ ] **Step 5: Commit**

```bash
git add src/test/kotlin/io/speqa/speqa/error/SpeqaSentryClientTest.kt \
        src/main/kotlin/io/speqa/speqa/error/SpeqaSentryClient.kt
git commit -m "feat: add Sentry HTTP client for error reporting"
```

---

### Task 4: Error Report Submitter

**Files:**
- Create: `src/main/kotlin/io/speqa/speqa/error/SpeqaErrorReportSubmitter.kt`
- Modify: `src/main/resources/META-INF/plugin.xml`

- [ ] **Step 1: Write the implementation**

Create `src/main/kotlin/io/speqa/speqa/error/SpeqaErrorReportSubmitter.kt`:

```kotlin
package io.github.barsia.speqa.error

import com.intellij.diagnostic.IdeaReportingEvent
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.diagnostic.ErrorReportSubmitter
import com.intellij.openapi.diagnostic.IdeaLoggingEvent
import com.intellij.openapi.diagnostic.SubmittedReportInfo
import com.intellij.openapi.extensions.PluginId
import com.intellij.util.Consumer
import io.github.barsia.speqa.SpeqaBundle
import java.awt.Component

class SpeqaErrorReportSubmitter : ErrorReportSubmitter() {

    override fun getReportActionText(): String =
        SpeqaBundle.message("error.report.actionText")

    override fun getPrivacyNoticeText(): String =
        SpeqaBundle.message("error.report.privacyNotice")

    override fun submit(
        events: Array<out IdeaLoggingEvent>,
        additionalInfo: String?,
        parentComponent: Component,
        consumer: Consumer<in SubmittedReportInfo>,
    ): Boolean {
        val event = events.firstOrNull() ?: return false
        val throwable = event.throwable ?: return false

        val sanitized = SpeqaStacktraceSanitizer.sanitize(throwable)
        val pluginVersion = PluginManagerCore.getPlugin(PluginId.getId("io.github.barsia.speqa"))
            ?.version ?: "unknown"
        val appInfo = ApplicationInfo.getInstance()
        val ideVersion = appInfo.build.asString()
        val osInfo = "${System.getProperty("os.name")} ${System.getProperty("os.version")}"
        val javaVersion = System.getProperty("java.version") ?: "unknown"

        Thread {
            SpeqaSentryClient.send(
                stacktrace = sanitized,
                pluginVersion = pluginVersion,
                ideVersion = ideVersion,
                osInfo = osInfo,
                javaVersion = javaVersion,
                userComment = additionalInfo ?: "",
            )
            consumer.consume(SubmittedReportInfo(SubmittedReportInfo.SubmissionStatus.NEW_ISSUE))
        }.start()

        return true
    }
}
```

- [ ] **Step 2: Register in plugin.xml**

Add inside the `<extensions defaultExtensionNs="com.intellij">` block in `plugin.xml`:

```xml
<errorReportSubmitter implementation="io.github.barsia.speqa.error.SpeqaErrorReportSubmitter"/>
```

- [ ] **Step 3: Verify compilation**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/io/speqa/speqa/error/SpeqaErrorReportSubmitter.kt \
        src/main/resources/META-INF/plugin.xml
git commit -m "feat: register SpeQA error report submitter with Sentry integration"
```

---

### Task 5: Smoke Test in Sandbox

- [ ] **Step 1: Run the plugin sandbox**

Run: `./gradlew runIde`

- [ ] **Step 2: Trigger an exception manually**

In the sandbox IDE, open a `.tc.md` file. If no natural exception occurs, verify the error dialog integration by checking that Help → About → Plugins shows SpeQA with its error handler registered.

- [ ] **Step 3: Verify "Report to SpeQA" button appears**

When an exception occurs in SpeQA code, the error dialog must show "Report to SpeQA" (not "Report to JetBrains") and the privacy notice text.

- [ ] **Step 4: Verify event appears in Sentry dashboard**

After clicking "Report to SpeQA", check the Sentry project dashboard — a new issue should appear with sanitized stacktrace (class/method/line only, no file paths).
