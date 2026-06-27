package io.github.barsia.speqa.toolwindow

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.treeStructure.Tree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath

/**
 * Covers the VFS/PSI-backed tree resolution that drives the tool-window context menu and the
 * empty-state decision: which file/kind a selected node maps to, where a new file is created,
 * and whether a directory subtree contains leaves. These under-pin item visibility, Create Test
 * Run scoping/enablement, and the CTA-vs-tree choice, so a regression here is user-visible.
 */
class SpeqaTreeResolutionTest : BasePlatformTestCase() {

    private fun tcSpec(): SpeqaLeafSpec = TestCaseLeafSpec(TestCaseSummaryCache(), SpeqaTreeFilter())
    private fun trSpec(): SpeqaLeafSpec = TestRunLeafSpec(TestRunSummaryCache(), TestRunTreeFilter())

    /** A tree whose selection's last node carries [userObject], or has no selection when null. */
    private fun treeSelecting(userObject: Any?): Tree {
        val root = DefaultMutableTreeNode("root")
        val tree = Tree(DefaultTreeModel(root))
        if (userObject == null) {
            tree.clearSelection()
        } else {
            val child = DefaultMutableTreeNode(userObject)
            root.add(child)
            (tree.model as DefaultTreeModel).reload()
            tree.selectionPath = TreePath(arrayOf(root, child))
        }
        return tree
    }

    // ---- anyTcLeaf / hasAnyLeaf ------------------------------------------------------------

    fun testAnyTcLeafTrueForDirectChild() {
        val dir = myFixture.addFileToProject("a1/Case.tc.md", "").virtualFile.parent
        assertTrue(anyTcLeaf(dir))
    }

    fun testAnyTcLeafTrueForNestedChild() {
        val nestedFile = myFixture.addFileToProject("a2/sub/Case.tc.md", "").virtualFile
        val topDir = nestedFile.parent.parent
        assertTrue(anyTcLeaf(topDir))
    }

    fun testAnyTcLeafFalseWhenOnlyOtherFiles() {
        val dir = myFixture.addFileToProject("a3/Run.tr.md", "").virtualFile.parent
        assertFalse(anyTcLeaf(dir))
    }

    fun testHasAnyLeafIsSpecific() {
        val dir = myFixture.addFileToProject("h1/Case.tc.md", "").virtualFile.parent
        assertTrue(hasAnyLeaf(dir, tcSpec()))
        assertFalse(hasAnyLeaf(dir, trSpec()))

        val runDir = myFixture.addFileToProject("h2/Run.tr.md", "").virtualFile.parent
        assertTrue(hasAnyLeaf(runDir, trSpec()))
        assertFalse(hasAnyLeaf(runDir, tcSpec()))
    }

    // ---- selectedNodeKind / selectedNodeFile ----------------------------------------------

    fun testSelectedNodeKindLeaf() {
        val file = myFixture.addFileToProject("k1/Case.tc.md", "").virtualFile
        val node = SpeqaLeafNode(project, file, tcSpec())
        assertEquals(SpeqaPopupNodeKind.LEAF, selectedNodeKind(treeSelecting(node)))
    }

    fun testSelectedNodeKindFolder() {
        val dir = myFixture.addFileToProject("k2/Case.tc.md", "").virtualFile.parent
        val node = SpeqaFolderNode(project, dir, tcSpec())
        assertEquals(SpeqaPopupNodeKind.FOLDER, selectedNodeKind(treeSelecting(node)))
    }

    fun testSelectedNodeKindNoneWhenNothingSelected() {
        assertEquals(SpeqaPopupNodeKind.NONE, selectedNodeKind(treeSelecting(null)))
    }

    fun testSelectedNodeFileForLeaf() {
        val file = myFixture.addFileToProject("f1/Case.tc.md", "").virtualFile
        val node = SpeqaLeafNode(project, file, tcSpec())
        assertEquals(file, selectedNodeFile(treeSelecting(node)))
    }

    fun testSelectedNodeFileForFolder() {
        val dir = myFixture.addFileToProject("f2/Case.tc.md", "").virtualFile.parent
        val node = SpeqaFolderNode(project, dir, tcSpec())
        assertEquals(dir, selectedNodeFile(treeSelecting(node)))
    }

    fun testSelectedNodeFileNullWhenNothingSelected() {
        assertNull(selectedNodeFile(treeSelecting(null)))
    }

    // ---- resolveCreationDir ----------------------------------------------------------------

    fun testResolveCreationDirLeafReturnsParent() {
        val file = myFixture.addFileToProject("r1/Case.tc.md", "").virtualFile
        val rootDir = file.parent.parent
        val node = SpeqaLeafNode(project, file, tcSpec())
        assertEquals(file.parent, resolveCreationDir(treeSelecting(node), rootDir))
    }

    fun testResolveCreationDirFolderReturnsItself() {
        val dir = myFixture.addFileToProject("r2/Case.tc.md", "").virtualFile.parent
        val node = SpeqaFolderNode(project, dir, tcSpec())
        assertEquals(dir, resolveCreationDir(treeSelecting(node), dir.parent))
    }

    fun testResolveCreationDirNoSelectionReturnsRoot() {
        val rootDir = myFixture.addFileToProject("r3/Case.tc.md", "").virtualFile.parent
        assertEquals(rootDir, resolveCreationDir(treeSelecting(null), rootDir))
    }
}
