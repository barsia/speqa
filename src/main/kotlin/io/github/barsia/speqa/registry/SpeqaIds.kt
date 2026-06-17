package io.github.barsia.speqa.registry

import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.indexing.FileBasedIndex

/**
 * Stateless query facade over [SpeqaIdIndex]. All queries are guarded against dumb
 * mode (indexes unavailable) and re-verify candidate files against their CURRENT
 * parsed id, because getContainingFiles is an over-approximation that can return
 * stale or extra files.
 */
object SpeqaIds {

    fun isDuplicate(project: Project, type: IdType, id: Int): Boolean =
        containingFiles(project, type, id).size > 1

    fun containingFiles(project: Project, type: IdType, id: Int): List<VirtualFile> {
        if (DumbService.isDumb(project)) return emptyList()
        val scope = GlobalSearchScope.projectScope(project)
        val candidates = FileBasedIndex.getInstance()
            .getContainingFiles(SpeqaIdIndex.NAME, SpeqaIdIndex.key(type, id), scope)
        return candidates.filter { it.isValid && currentId(project, it) == id }
    }

    fun usedIds(project: Project, type: IdType): Set<Int> {
        if (DumbService.isDumb(project)) return emptySet()
        val prefix = SpeqaIdIndex.typePrefix(type)
        val ids = HashSet<Int>()
        FileBasedIndex.getInstance().processAllKeys(
            SpeqaIdIndex.NAME,
            { key ->
                if (key.startsWith(prefix)) key.removePrefix(prefix).toIntOrNull()?.let(ids::add)
                true
            },
            project,
        )
        return ids
    }

    fun nextFreeId(project: Project, type: IdType): Int {
        val used = usedIds(project, type)
        var candidate = 1
        while (candidate in used) candidate++
        return candidate
    }

    /** All (file, id) pairs of the given type, verified against current content. */
    fun allEntries(project: Project, type: IdType): List<Pair<VirtualFile, Int>> {
        if (DumbService.isDumb(project)) return emptyList()
        val result = ArrayList<Pair<VirtualFile, Int>>()
        for (id in usedIds(project, type)) {
            for (file in containingFiles(project, type, id)) {
                result.add(file to id)
            }
        }
        return result
    }

    /** Current id parsed from the live document if loaded, else from disk bytes. */
    private fun currentId(project: Project, file: VirtualFile): Int? {
        val text = FileDocumentManager.getInstance().getCachedDocument(file)?.text
            ?: runCatching { String(file.contentsToByteArray(), file.charset) }.getOrNull()
            ?: return null
        return SpeqaIdIndex.indexKeysFor(file.name, text)
            .firstOrNull()
            ?.substringAfter(':')
            ?.toIntOrNull()
    }
}
