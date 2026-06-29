package io.github.barsia.speqa.toolwindow

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Locks the pure per-node decision used to walk the TRs tree to the freshly created run
 * file. The platform tree-visitor wiring is UI; this guards the descend/select/skip logic
 * that decides, for each visited node, whether the target file is here, below, or elsewhere.
 */
class SpeqaTestRunRevealTest {

    @Test
    fun `selects the node whose file is the target`() {
        assertEquals(
            RevealStep.SELECT,
            revealStepFor("/p/test-runs/Run-1.tr.md", candidateIsDirectory = false, targetPath = "/p/test-runs/Run-1.tr.md"),
        )
    }

    @Test
    fun `descends into an ancestor directory of the target`() {
        assertEquals(
            RevealStep.DESCEND,
            revealStepFor("/p/test-runs", candidateIsDirectory = true, targetPath = "/p/test-runs/sub/Run-1.tr.md"),
        )
    }

    @Test
    fun `skips a directory that is not an ancestor of the target`() {
        assertEquals(
            RevealStep.SKIP,
            revealStepFor("/p/test-runs/other", candidateIsDirectory = true, targetPath = "/p/test-runs/sub/Run-1.tr.md"),
        )
    }

    @Test
    fun `skips a non-target leaf`() {
        assertEquals(
            RevealStep.SKIP,
            revealStepFor("/p/test-runs/Run-2.tr.md", candidateIsDirectory = false, targetPath = "/p/test-runs/Run-1.tr.md"),
        )
    }

    @Test
    fun `does not treat a sibling directory sharing a name prefix as an ancestor`() {
        assertEquals(
            RevealStep.SKIP,
            revealStepFor("/p/test-runs-old", candidateIsDirectory = true, targetPath = "/p/test-runs/Run-1.tr.md"),
        )
    }

    @Test
    fun `a file path that only prefixes the target is not a match`() {
        assertEquals(
            RevealStep.SKIP,
            revealStepFor("/p/test-runs/Run-1", candidateIsDirectory = false, targetPath = "/p/test-runs/Run-1.tr.md"),
        )
    }
}
