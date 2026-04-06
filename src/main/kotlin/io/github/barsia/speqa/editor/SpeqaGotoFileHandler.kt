package io.github.barsia.speqa.editor

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager

class SpeqaGotoFileHandler : GotoDeclarationHandler {
    override fun getGotoDeclarationTargets(
        sourceElement: PsiElement?,
        offset: Int,
        editor: Editor?,
    ): Array<PsiElement>? {
        val element = sourceElement ?: return null
        val vFile = element.containingFile?.virtualFile ?: return null
        val name = vFile.name
        if (!name.endsWith(".tc.md") && !name.endsWith(".tr.md")) return null

        val linkDest = findLinkDestination(element) ?: return null
        val fullPath = linkDest.text?.replace("%20", " ") ?: return null

        if (fullPath.startsWith("http://") || fullPath.startsWith("https://")) return null
        if (fullPath.isBlank()) return null

        // Determine which path segment the cursor is on
        val linkStart = linkDest.textRange.startOffset
        val cursorInLink = offset - linkStart
        val pathUpToCursor = if (cursorInLink in 0..fullPath.length) {
            val nextSlash = fullPath.indexOf('/', cursorInLink)
            if (nextSlash >= 0) fullPath.substring(0, nextSlash) else fullPath
        } else {
            fullPath
        }

        val project = element.project
        val parentDir = vFile.parent ?: return null

        val resolved = parentDir.findFileByRelativePath(pathUpToCursor)
            ?: run {
                val basePath = project.basePath ?: return null
                VirtualFileManager.getInstance().findFileByUrl("file://$basePath")
                    ?.findFileByRelativePath(pathUpToCursor)
            }
            ?: return null

        val psiTarget = if (resolved.isDirectory) {
            PsiManager.getInstance(project).findDirectory(resolved)
        } else {
            PsiManager.getInstance(project).findFile(resolved)
        }
        return psiTarget?.let { arrayOf(it) }
    }

    private fun findLinkDestination(element: PsiElement): PsiElement? {
        var current: PsiElement? = element
        var depth = 0
        while (current != null && depth < 8) {
            val typeName = current.node?.elementType?.toString() ?: ""
            if (typeName.contains("LINK_DESTINATION") || typeName.contains("GFM_AUTOLINK")) {
                return current
            }
            if (typeName.contains("INLINE_LINK") || typeName.contains("IMAGE") || typeName.contains("FULL_REFERENCE_LINK")) {
                var child = current.firstChild
                while (child != null) {
                    val childType = child.node?.elementType?.toString() ?: ""
                    if (childType.contains("LINK_DESTINATION")) {
                        return child
                    }
                    child = child.nextSibling
                }
            }
            current = current.parent
            depth++
        }
        return null
    }
}
