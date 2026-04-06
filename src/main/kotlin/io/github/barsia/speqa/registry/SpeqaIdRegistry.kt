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
import com.intellij.util.concurrency.AppExecutorUtil
import io.github.barsia.speqa.model.SpeqaDefaults
import java.util.concurrent.ConcurrentHashMap

enum class IdType() {
    TEST_CASE(),
    TEST_RUN(),
}

class IdSet {
    private val counts = ConcurrentHashMap<Int, Int>()

    fun register(id: Int) {
        counts.merge(id, 1, Int::plus)
    }

    fun unregister(id: Int) {
        counts.computeIfPresent(id) { _, count -> if (count <= 1) null else count - 1 }
    }

    fun isUsed(id: Int): Boolean = counts.containsKey(id)

    fun isDuplicate(id: Int): Boolean = (counts[id] ?: 0) > 1

    fun nextFreeId(): Int {
        var candidate = 1
        while (counts.containsKey(candidate)) candidate++
        return candidate
    }

    fun clear() = counts.clear()

    fun replaceAll(newCounts: Map<Int, Int>) {
        counts.putAll(newCounts)
        counts.keys.removeAll { it !in newCounts }
    }
}

@Service(Service.Level.PROJECT)
class SpeqaIdRegistry(private val project: Project) {
    private val tcIds = IdSet()
    private val trIds = IdSet()

    @Volatile
    internal var initialized = false
        private set

    @Volatile
    internal var scanTask: () -> Unit = { scan() }

    fun idSet(type: IdType): IdSet = when (type) {
        IdType.TEST_CASE -> tcIds
        IdType.TEST_RUN -> trIds
    }

    fun ensureInitialized() {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            initialized = true
            subscribeToVfsEvents()
            AppExecutorUtil.getAppExecutorService().submit {
                scanTask()
            }
        }
    }

    private fun scan() {
        val basePath = project.basePath ?: return
        val projectDir = VirtualFileManager.getInstance().findFileByUrl("file://$basePath") ?: return
        val newTcCounts = mutableMapOf<Int, Int>()
        val newTrCounts = mutableMapOf<Int, Int>()
        scanDirectory(projectDir, newTcCounts, newTrCounts)
        tcIds.replaceAll(newTcCounts)
        trIds.replaceAll(newTrCounts)
    }

    private val skipDirs = setOf(
        ".git", ".idea", ".gradle", ".intellijPlatform",
        "build", "out", "target", "dist",
        "node_modules", "vendor",
    )

    private fun scanDirectory(dir: VirtualFile, tcCounts: MutableMap<Int, Int>, trCounts: MutableMap<Int, Int>) {
        for (child in dir.children) {
            if (child.isDirectory) {
                if (child.name !in skipDirs) {
                    scanDirectory(child, tcCounts, trCounts)
                }
            } else {
                extractId(child)?.let { (type, id) ->
                    val map = if (type == IdType.TEST_CASE) tcCounts else trCounts
                    map.merge(id, 1, Int::plus)
                }
            }
        }
    }

    private fun subscribeToVfsEvents() {
        project.messageBus.connect().subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
            override fun after(events: List<VFileEvent>) {
                val hasSpeqaChange = events.any { event ->
                    when (event) {
                        is VFileCreateEvent -> event.childName.endsWith(".${SpeqaDefaults.TEST_CASE_EXTENSION}") ||
                            event.childName.endsWith(".${SpeqaDefaults.TEST_RUN_EXTENSION}")
                        is VFileDeleteEvent -> isSpeqaFile(event.file)
                        is VFileContentChangeEvent -> isSpeqaFile(event.file)
                        else -> false
                    }
                }
                if (hasSpeqaChange) {
                    com.intellij.openapi.application.ReadAction.nonBlocking<Unit> { scan() }
                        .submit(AppExecutorUtil.getAppExecutorService())
                }
            }
        })
    }

    private fun isSpeqaFile(file: VirtualFile): Boolean {
        return file.name.endsWith(".${SpeqaDefaults.TEST_CASE_EXTENSION}") ||
            file.name.endsWith(".${SpeqaDefaults.TEST_RUN_EXTENSION}")
    }

    companion object {
        fun getInstance(project: Project): SpeqaIdRegistry = project.service()

        private val ID_REGEX = Regex("""^id:\s*(\d+)\s*$""", RegexOption.MULTILINE)

        fun extractId(file: VirtualFile): Pair<IdType, Int>? {
            val type = when {
                file.name.endsWith(".${SpeqaDefaults.TEST_CASE_EXTENSION}") -> IdType.TEST_CASE
                file.name.endsWith(".${SpeqaDefaults.TEST_RUN_EXTENSION}") -> IdType.TEST_RUN
                else -> return null
            }
            val bytes = try {
                val stream = file.inputStream
                val buf = ByteArray(512)
                val read = stream.read(buf)
                stream.close()
                if (read <= 0) return null
                buf.copyOf(read)
            } catch (_: Exception) {
                return null
            }
            val header = String(bytes, Charsets.UTF_8)
            val match = ID_REGEX.find(header) ?: return null
            val id = match.groupValues[1].toIntOrNull() ?: return null
            return type to id
        }
    }
}
