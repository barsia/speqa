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
    fun `buildEnvelope escapes special characters in user comment`() {
        val stacktrace = SanitizedStacktrace(
            exceptionClass = "java.lang.RuntimeException",
            exceptionMessage = "",
            frames = listOf(SanitizedFrame("io.speqa.Test", "run", 1)),
        )
        val envelope = SpeqaSentryClient.buildEnvelope(
            stacktrace = stacktrace,
            pluginVersion = "0.1.0",
            ideVersion = "IU-261.1234",
            osInfo = "Linux",
            javaVersion = "21",
            userComment = "He said \"hello\"\nand then\\crashed",
        )

        val payload = envelope.split("\n")[2]
        assertTrue("Escaped quote must be present", payload.contains("He said \\\"hello\\\""))
        assertTrue("Escaped newline must be present", payload.contains("and then\\\\crashed"))
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
