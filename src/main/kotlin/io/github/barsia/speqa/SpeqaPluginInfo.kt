package io.github.barsia.speqa

import java.util.Properties

object SpeqaPluginInfo {
    /**
     * Plugin version, baked into `speqa-plugin.properties` at build time from the
     * Gradle `version`. Read from a resource so we don't depend on internal
     * IntelliJ Platform plugin-lookup APIs (which change between releases).
     */
    val version: String by lazy {
        SpeqaPluginInfo::class.java.getResourceAsStream("/speqa-plugin.properties")
            ?.use { stream -> Properties().apply { load(stream) }.getProperty("version") }
            ?: "unknown"
    }
}
