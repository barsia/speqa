package io.github.barsia.speqa.registry

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import io.github.barsia.speqa.model.SpeqaDefaults
import com.intellij.util.concurrency.AppExecutorUtil
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Project-level service that maintains sets of all known tags and environments
 * across all `.tc.md` files. Initialized lazily on first access. Kept current
 * via [BulkFileListener] filtered to `.tc.md` files.
 *
 * Only parses YAML frontmatter (first 2KB) for performance — does not read
 * the full Markdown body.
 */
@Service(Service.Level.PROJECT)
class SpeqaTagRegistry(private val project: Project) {

    private val tags = ConcurrentHashMap.newKeySet<String>()
    private val environments = ConcurrentHashMap.newKeySet<String>()
    private val testCaseFilesByTag = ConcurrentHashMap<String, MutableSet<VirtualFile>>()
    private val testRunFilesByTag = ConcurrentHashMap<String, MutableSet<VirtualFile>>()
    private val testCaseFilesByEnvironment = ConcurrentHashMap<String, MutableSet<VirtualFile>>()
    private val testRunFilesByEnvironment = ConcurrentHashMap<String, MutableSet<VirtualFile>>()

    @Volatile
    private var initialized = false
    private val initializationScheduled = AtomicBoolean(false)
    private val subscribedToVfs = AtomicBoolean(false)

    val allTags: List<String> get() {
        ensureInitialized()
        return tags.sorted()
    }

    val allEnvironments: List<String> get() {
        ensureInitialized()
        return environments.sorted()
    }

    fun ensureInitialized() {
        if (initialized) return
        if (subscribedToVfs.compareAndSet(false, true)) {
            subscribeToVfsEvents()
        }
        scheduleScan()
    }

    private fun scan() {
        tags.clear()
        environments.clear()
        testCaseFilesByTag.clear()
        testRunFilesByTag.clear()
        testCaseFilesByEnvironment.clear()
        testRunFilesByEnvironment.clear()
        val basePath = project.basePath ?: return
        val projectDir = VirtualFileManager.getInstance().findFileByUrl("file://$basePath") ?: return
        scanDirectory(projectDir)
    }

    private fun scanDirectory(dir: VirtualFile) {
        for (child in dir.children) {
            if (child.isDirectory) {
                if (child.name !in SKIP_DIRS) {
                    scanDirectory(child)
                }
            } else if (child.name.endsWith(".${SpeqaDefaults.TEST_CASE_EXTENSION}")) {
                extractTagsAndEnvironments(child, isTestRun = false)
            } else if (child.name.endsWith(".${SpeqaDefaults.TEST_RUN_EXTENSION}")) {
                extractTagsAndEnvironments(child, isTestRun = true)
            }
        }
    }

    private fun extractTagsAndEnvironments(file: VirtualFile, isTestRun: Boolean) {
        val header = readFrontmatter(file) ?: return
        val parsedTags = parseYamlList(header, "tags")
        val parsedEnvironments = parseYamlList(header, "environment")
        if (isTestRun) {
            parsedTags.forEach { indexValue(testRunFilesByTag, it, file) }
            parsedEnvironments.forEach { indexValue(testRunFilesByEnvironment, it, file) }
        } else {
            parsedTags.forEach {
                tags.add(it)
                indexValue(testCaseFilesByTag, it, file)
            }
            parsedEnvironments.forEach {
                environments.add(it)
                indexValue(testCaseFilesByEnvironment, it, file)
            }
        }
    }

    fun findTestCasesByTag(tag: String): List<VirtualFile> {
        ensureInitialized()
        return indexedFilesFor(testCaseFilesByTag, tag)
    }

    fun findTestRunsByTag(tag: String): List<VirtualFile> {
        ensureInitialized()
        return indexedFilesFor(testRunFilesByTag, tag)
    }

    fun findTestCasesByEnvironment(environment: String): List<VirtualFile> {
        ensureInitialized()
        return indexedFilesFor(testCaseFilesByEnvironment, environment)
    }

    fun findTestRunsByEnvironment(environment: String): List<VirtualFile> {
        ensureInitialized()
        return indexedFilesFor(testRunFilesByEnvironment, environment)
    }

    private fun scheduleScan() {
        if (!initializationScheduled.compareAndSet(false, true)) return
        com.intellij.openapi.application.ReadAction.nonBlocking<Unit> {
            scan()
        }
            .finishOnUiThread(com.intellij.openapi.application.ModalityState.any()) {
                initialized = true
                initializationScheduled.set(false)
            }
            .submit(AppExecutorUtil.getAppExecutorService())
    }

    private fun subscribeToVfsEvents() {
        project.messageBus.connect().subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
            override fun after(events: List<VFileEvent>) {
                val hasChange = events.any { event ->
                    when (event) {
                        is VFileCreateEvent -> isRelevantSpeqaFileName(event.childName)
                        is VFileDeleteEvent -> isRelevantSpeqaFileName(event.file.name)
                        is VFileContentChangeEvent -> isRelevantSpeqaFileName(event.file.name)
                        else -> false
                    }
                }
                if (hasChange) {
                    scheduleScan()
                }
            }
        })
    }

    companion object {
        fun getInstance(project: Project): SpeqaTagRegistry = project.service()

        private val SKIP_DIRS = setOf(
            ".git", ".idea", ".gradle", ".intellijPlatform",
            "build", "out", "target", "dist",
            "node_modules", "vendor",
        )

        private val LIST_ITEM = Regex("""^\s*-\s+"?([^"\n]+?)"?\s*$""")

        private fun isRelevantSpeqaFileName(name: String): Boolean {
            return name.endsWith(".${SpeqaDefaults.TEST_CASE_EXTENSION}") ||
                name.endsWith(".${SpeqaDefaults.TEST_RUN_EXTENSION}")
        }

        /**
         * Parses YAML list values under [fieldName] from frontmatter text.
         * Handles both `field: [a, b]` inline and multi-line `- value` forms.
         */
        fun parseYamlList(frontmatter: String, fieldName: String): List<String> {
            val lines = frontmatter.lines()
            val result = mutableListOf<String>()
            var inField = false

            for (line in lines) {
                if (line.startsWith("$fieldName:")) {
                    val inlineValue = line.substringAfter("$fieldName:").trim()
                    if (inlineValue.startsWith("[")) {
                        val inner = inlineValue.removePrefix("[").removeSuffix("]")
                        splitCommaSeparatedScalar(inner).forEach { item ->
                            val trimmed = item.trim().removeSurrounding("\"").removeSurrounding("'")
                            if (trimmed.isNotBlank()) result.add(trimmed)
                        }
                        return result
                    }
                    if (inlineValue.isNotBlank()) {
                        if (fieldName == "environment") {
                            val trimmed = inlineValue.trim().removeSurrounding("\"").removeSurrounding("'")
                            if (trimmed.isNotBlank()) result.add(trimmed)
                        } else {
                            splitCommaSeparatedScalar(inlineValue).forEach { item ->
                                val trimmed = item.trim().removeSurrounding("\"").removeSurrounding("'")
                                if (trimmed.isNotBlank()) result.add(trimmed)
                            }
                        }
                        return result
                    }
                    inField = true
                    continue
                }
                if (inField) {
                    val match = LIST_ITEM.matchEntire(line)
                    if (match != null) {
                        result.add(match.groupValues[1])
                    } else {
                        break
                    }
                }
            }
            return result
        }

        private fun readFrontmatter(file: VirtualFile): String? {
            return try {
                val stream = file.inputStream
                val buf = ByteArray(2048)
                val read = stream.read(buf)
                stream.close()
                if (read <= 0) return null
                val text = String(buf, 0, read, Charsets.UTF_8)
                val start = text.indexOf("---")
                if (start < 0) return null
                val end = text.indexOf("---", start + 3)
                if (end < 0) text.substring(start) else text.substring(start, end + 3)
            } catch (_: Exception) {
                null
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

    private fun indexValue(index: ConcurrentHashMap<String, MutableSet<VirtualFile>>, value: String, file: VirtualFile) {
        index.computeIfAbsent(value) { ConcurrentHashMap.newKeySet() }.add(file)
    }

    private fun indexedFilesFor(index: ConcurrentHashMap<String, MutableSet<VirtualFile>>, value: String): List<VirtualFile> {
        return index[value]
            ?.filter { it.isValid }
            ?.sortedBy { it.path }
            .orEmpty()
    }
}
