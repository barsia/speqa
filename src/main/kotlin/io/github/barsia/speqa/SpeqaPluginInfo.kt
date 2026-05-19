package io.github.barsia.speqa

import com.intellij.ide.plugins.PluginManager
import com.intellij.openapi.extensions.PluginId

object SpeqaPluginInfo {
    private val pluginId: PluginId = PluginId.getId("io.github.barsia.speqa")

    val version: String
        get() = PluginManager.getInstance()
            .findEnabledPlugin(pluginId)
            ?.version
            ?: "unknown"
}
