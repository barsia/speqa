package io.github.barsia.speqa.parser

import org.yaml.snakeyaml.Yaml

internal object SpeqaMarkdown {
    fun normalizeLineEndings(text: String): String {
        return text.replace("\r\n", "\n").replace("\r", "\n")
    }

    fun splitFrontmatter(content: String): Pair<String, String> {
        val normalized = normalizeLineEndings(content).trimStart()
        if (!normalized.startsWith("---")) {
            return "" to normalized
        }

        val lines = normalized.lines()
        if (lines.isEmpty() || lines.first().trim() != "---") {
            return "" to normalized
        }

        val frontmatterLines = mutableListOf<String>()
        var closingIndex = -1

        for (index in 1 until lines.size) {
            val line = lines[index]
            if (line.trim() == "---") {
                closingIndex = index
                break
            }
            frontmatterLines += line
        }

        if (closingIndex == -1) {
            throw IllegalArgumentException("Malformed frontmatter: missing closing --- delimiter")
        }

        val body = lines.drop(closingIndex + 1).joinToString("\n").trimStart('\n')
        return frontmatterLines.joinToString("\n").trim() to body
    }

    fun extractSection(body: String, heading: String): String? {
        val lines = normalizeLineEndings(body).lines()
        var inSection = false
        val sectionLines = mutableListOf<String>()

        for (line in lines) {
            val trimmed = line.trim()
            if (!inSection) {
                if (trimmed == heading.trim()) {
                    inSection = true
                }
                continue
            }

            if (trimmed.startsWith("## ")) {
                break
            }

            sectionLines += line
        }

        return if (inSection) sectionLines.joinToString("\n").trimEnd() else null
    }

    @Suppress("UNCHECKED_CAST")
    fun parseYamlMap(yaml: String): Map<String, Any?> {
        if (yaml.isBlank()) {
            return emptyMap()
        }

        return tryParseYaml(yaml)
            ?: tryParseYaml(normalizeBareCommaSeparatedValues(yaml))
            ?: tryParseYamlDroppingBadLines(yaml)
            ?: emptyMap()
    }

    private val BARE_CSV_LINE = Regex("""^(\w+):\s+(.+,.+)$""")

    private fun normalizeBareCommaSeparatedValues(yaml: String): String {
        return yaml.lines().joinToString("\n") { line ->
            val match = BARE_CSV_LINE.matchEntire(line)
            if (match != null) {
                val key = match.groupValues[1]
                val value = match.groupValues[2]
                if (!value.startsWith("[")) "$key: [$value]" else line
            } else line
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun tryParseYaml(yaml: String): Map<String, Any?>? {
        val value = try {
            Yaml().load<Any>(yaml)
        } catch (_: Exception) {
            return null
        }
        return value as? Map<String, Any?>
    }

    private fun tryParseYamlDroppingBadLines(yaml: String): Map<String, Any?>? {
        val allLines = yaml.lines()
        if (allLines.isEmpty()) return null

        // Try dropping each top-level line (+ its indented children) one at a time
        var i = 0
        while (i < allLines.size) {
            // Find the extent of this entry (line + indented continuation)
            var end = i + 1
            while (end < allLines.size && allLines[end].startsWith(" ")) end++

            val without = allLines.subList(0, i) + allLines.subList(end, allLines.size)
            val attempt = without.joinToString("\n")
            val result = tryParseYaml(attempt)
            if (result != null) return result

            i = end
        }
        return null
    }

    fun parseStringList(value: Any?): List<String> {
        return when (value) {
            is List<*> -> value.filterNotNull().map { it.toString() }
            is String -> if (value.isBlank()) emptyList() else listOf(value)
            null -> emptyList()
            else -> listOf(value.toString())
        }
    }

    fun parseScalar(value: Any?): String {
        return when (value) {
            null -> ""
            is String -> value
            else -> value.toString()
        }
    }

    fun quoteYamlScalar(value: String): String {
        return buildString {
            append('"')
            value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r\n", "\n")
                .replace("\r", "\n")
                .replace("\n", "\\n")
                .forEach { append(it) }
            append('"')
        }
    }
}
