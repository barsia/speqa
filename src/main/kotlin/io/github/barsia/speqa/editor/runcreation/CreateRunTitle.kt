package io.github.barsia.speqa.editor.runcreation

object CreateRunTitle {
    fun defaultTitle(activeLabel: String?, timestamp: String): String = "${activeLabel ?: "Test Run"} - $timestamp"
}
