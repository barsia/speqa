package io.github.barsia.speqa.error

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant
import java.util.UUID

object SpeqaSentryClient {

    private const val DSN = "https://c93ec35f9f4d6886b441592432870f13@o4510727659716608.ingest.de.sentry.io/4511201044922448"

    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build()

    internal var sender: (
        stacktrace: SanitizedStacktrace,
        pluginVersion: String,
        ideVersion: String,
        osInfo: String,
        javaVersion: String,
        userComment: String,
    ) -> Boolean = { stacktrace, pluginVersion, ideVersion, osInfo, javaVersion, userComment ->
        sendViaHttp(stacktrace, pluginVersion, ideVersion, osInfo, javaVersion, userComment)
    }

    fun send(
        stacktrace: SanitizedStacktrace,
        pluginVersion: String,
        ideVersion: String,
        osInfo: String,
        javaVersion: String,
        userComment: String,
    ): Boolean {
        return sender(stacktrace, pluginVersion, ideVersion, osInfo, javaVersion, userComment)
    }

    private fun sendViaHttp(
        stacktrace: SanitizedStacktrace,
        pluginVersion: String,
        ideVersion: String,
        osInfo: String,
        javaVersion: String,
        userComment: String,
    ): Boolean {
        try {
            val envelope = buildEnvelope(stacktrace, pluginVersion, ideVersion, osInfo, javaVersion, userComment)
            val ingestUrl = extractIngestUrl(DSN)
            val request = HttpRequest.newBuilder()
                .uri(URI.create(ingestUrl))
                .header("Content-Type", "application/x-sentry-envelope")
                .timeout(Duration.ofSeconds(5))
                .POST(HttpRequest.BodyPublishers.ofString(envelope))
                .build()
            val response = httpClient.send(request, HttpResponse.BodyHandlers.discarding())
            return response.statusCode() in 200..299
        } catch (_: Exception) {
            // Silently ignore — do not bother the user with reporting failures
            return false
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
