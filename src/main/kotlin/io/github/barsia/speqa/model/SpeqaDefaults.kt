package io.github.barsia.speqa.model

object SpeqaDefaults {
    const val TEST_CASE_EXTENSION = "tc.md"
    const val TEST_RUN_EXTENSION = "tr.md"

    fun speqaExtension(fileName: String): String? = when {
        fileName.endsWith(".$TEST_CASE_EXTENSION") -> TEST_CASE_EXTENSION
        fileName.endsWith(".$TEST_RUN_EXTENSION") -> TEST_RUN_EXTENSION
        else -> null
    }

    fun speqaStem(fileName: String): String? {
        val ext = speqaExtension(fileName) ?: return null
        return fileName.removeSuffix(".$ext")
    }
}
