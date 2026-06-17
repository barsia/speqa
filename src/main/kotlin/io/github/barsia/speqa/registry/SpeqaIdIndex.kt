package io.github.barsia.speqa.registry

import com.intellij.util.indexing.DataIndexer
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.FileContent
import com.intellij.util.indexing.ID
import com.intellij.util.indexing.ScalarIndexExtension
import com.intellij.util.io.EnumeratorStringDescriptor
import com.intellij.util.io.KeyDescriptor
import io.github.barsia.speqa.model.SpeqaDefaults

/**
 * Project index mapping each test-case / test-run id to the files that declare it.
 * Keys are "TC:<id>" / "TR:<id>". The platform reindexes unsaved documents at query
 * time, so queries reflect the live editor buffer (this is what makes duplicate
 * detection update as you type, through the daemon's normal highlighting pass).
 */
class SpeqaIdIndex : ScalarIndexExtension<String>() {

    override fun getName(): ID<String, Void> = NAME

    override fun getIndexer(): DataIndexer<String, Void, FileContent> =
        DataIndexer { content ->
            indexKeysFor(content.fileName, content.contentAsText.toString()).associateWith { null }
        }

    override fun getKeyDescriptor(): KeyDescriptor<String> = EnumeratorStringDescriptor.INSTANCE

    override fun getVersion(): Int = 1

    override fun getInputFilter(): FileBasedIndex.InputFilter =
        FileBasedIndex.InputFilter { file ->
            file.name.endsWith(".${SpeqaDefaults.TEST_CASE_EXTENSION}") ||
                file.name.endsWith(".${SpeqaDefaults.TEST_RUN_EXTENSION}")
        }

    override fun dependsOnFileContent(): Boolean = true

    companion object {
        val NAME: ID<String, Void> = ID.create("io.github.barsia.speqa.id")

        /** Pure mapping: file name + content -> index keys. No platform APIs. */
        fun indexKeysFor(fileName: String, text: String): Set<String> {
            val typePrefix = when {
                fileName.endsWith(".${SpeqaDefaults.TEST_CASE_EXTENSION}") -> "TC"
                fileName.endsWith(".${SpeqaDefaults.TEST_RUN_EXTENSION}") -> "TR"
                else -> return emptySet()
            }
            val id = ID_REGEX.find(text)?.groupValues?.get(1)?.toIntOrNull() ?: return emptySet()
            return setOf("$typePrefix:$id")
        }

        fun key(type: IdType, id: Int): String =
            (if (type == IdType.TEST_CASE) "TC" else "TR") + ":" + id

        fun typePrefix(type: IdType): String = if (type == IdType.TEST_CASE) "TC:" else "TR:"

        private val ID_REGEX = Regex("""(?m)^id:\s*(\d+)\s*$""")
    }
}
