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
            ?: tryParseYaml(normalizeQuotedCommaSeparatedValues(yaml))
            ?: tryParseYamlDroppingBadLines(yaml)
            ?: emptyMap()
    }

    private val QUOTED_CSV_LINE = Regex("""^(\w+):\s+(.+,.+)$""")

    private fun normalizeQuotedCommaSeparatedValues(yaml: String): String {
        return yaml.lines().joinToString("\n") { line ->
            val match = QUOTED_CSV_LINE.matchEntire(line)
            if (match != null) {
                val key = match.groupValues[1]
                val value = match.groupValues[2]
                if (value.contains('"')) {
                    val items = splitQuotedCommaSeparatedValues(value)
                    if (items.size > 1) {
                        buildString {
                            appendLine("$key:")
                            items.forEach { item ->
                                appendLine("  - ${quoteYamlScalar(item)}")
                            }
                        }.trimEnd()
                    } else {
                        line
                    }
                } else {
                    line
                }
            } else line
        }
    }

    private fun splitQuotedCommaSeparatedValues(raw: String): List<String> {
        val parts = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var escaped = false

        for (ch in raw) {
            when {
                escaped -> {
                    current.append(ch)
                    escaped = false
                }
                ch == '\\' && inQuotes -> {
                    current.append(ch)
                    escaped = true
                }
                ch == '"' -> {
                    inQuotes = !inQuotes
                    current.append(ch)
                }
                ch == ',' && !inQuotes -> {
                    parts += current.toString().trim()
                    current.clear()
                }
                else -> current.append(ch)
            }
        }

        if (current.isNotBlank()) {
            parts += current.toString().trim()
        }

        return parts
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { it.removeSurrounding("\"").replace("\\\"", "\"").replace("\\\\", "\\") }
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

    fun parseTagList(value: Any?): List<String> {
        return when (value) {
            is List<*> -> value.filterNotNull().map { it.toString() }
            is String -> splitCommaSeparatedScalar(value)
                .map { it.trim().removeSurrounding("\"").removeSurrounding("'") }
                .filter { it.isNotBlank() }
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

    fun appendStringListField(
        builder: StringBuilder,
        key: String,
        values: List<String>,
    ) {
        val filtered = values.filter { it.isNotBlank() }
        if (filtered.isEmpty()) return
        if (filtered.size == 1) {
            builder.appendLine("$key: ${quoteYamlScalar(filtered.single())}")
        } else {
            builder.appendLine("$key:")
            filtered.forEach { value ->
                builder.appendLine("  - ${quoteYamlScalar(value)}")
            }
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

    private fun splitCommaSeparatedScalar(value: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var quoteChar: Char? = null

        for (char in value) {
            when {
                quoteChar == null && (char == '"' || char == '\'') -> {
                    quoteChar = char
                    current.append(char)
                }
                quoteChar != null && char == quoteChar -> {
                    quoteChar = null
                    current.append(char)
                }
                quoteChar == null && char == ',' -> {
                    result += current.toString()
                    current.setLength(0)
                }
                else -> current.append(char)
            }
        }

        result += current.toString()
        return result
    }
}
