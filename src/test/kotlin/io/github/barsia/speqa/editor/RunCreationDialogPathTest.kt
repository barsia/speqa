package io.github.barsia.speqa.editor

import io.github.barsia.speqa.model.Link
import io.github.barsia.speqa.model.TestCase
import io.github.barsia.speqa.model.TestStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RunCreationDialogPathTest {

    @Test
    fun `blank destination normalizes to project root`() {
        val normalized = RunCreationPathSupport.normalizeDestinationRelativePath(
            projectBasePath = "/repo",
            rawPath = "",
        )

        assertEquals(".", normalized)
        assertTrue(RunCreationPathSupport.isDestinationInsideProject("/repo", ""))
    }

    @Test
    fun `absolute destination under project becomes project relative`() {
        val normalized = RunCreationPathSupport.normalizeDestinationRelativePath(
            projectBasePath = "/repo",
            rawPath = "/repo/test-runs/custom",
        )

        assertEquals("test-runs/custom", normalized)
        assertTrue(RunCreationPathSupport.isDestinationInsideProject("/repo", "/repo/test-runs/custom"))
    }

    @Test
    fun `relative destination escaping project is invalid`() {
        assertFalse(RunCreationPathSupport.isDestinationInsideProject("/repo", "../outside"))
    }

    @Test
    fun `absolute destination outside project is invalid`() {
        assertFalse(RunCreationPathSupport.isDestinationInsideProject("/repo", "/other/location"))
    }

    @Test
    fun `file name must not be blank`() {
        assertFalse(RunCreationPathSupport.isValidFileName("   "))
    }

    @Test
    fun `file name must not contain path separators`() {
        assertFalse(RunCreationPathSupport.isValidFileName("nested/run"))
        assertFalse(RunCreationPathSupport.isValidFileName("nested\\run"))
    }

    @Test
    fun `file name must reject dot segments and invalid characters`() {
        assertFalse(RunCreationPathSupport.isValidFileName("."))
        assertFalse(RunCreationPathSupport.isValidFileName(".."))
        assertFalse(RunCreationPathSupport.isValidFileName("bad:name"))
        assertFalse(RunCreationPathSupport.isValidFileName("bad*name"))
    }

    @Test
    fun `file name accepts ordinary run name`() {
        assertTrue(RunCreationPathSupport.isValidFileName("sample-run"))
    }

    @Test
    fun `run import options defaults match product decision`() {
        val defaults = RunImportOptions()

        assertTrue(defaults.importTags)
        assertTrue(defaults.importEnvironment)
        assertFalse(defaults.importTickets)
        assertFalse(defaults.importLinks)
        assertFalse(defaults.importAttachments)
    }

    @Test
    fun `run import options use two columns`() {
        assertEquals(2, runCreationImportColumns())
    }

    @Test
    fun `step links alone do not count as importable run links`() {
        val testCase = TestCase(
            links = emptyList(),
            steps = listOf(
                TestStep(
                    action = "Open page",
                    links = listOf(Link(title = "Deep link", url = "https://example.com/step")),
                ),
            ),
        )

        assertFalse(hasImportableRunLinks(testCase))
    }

    @Test
    fun `top level links count as importable run links`() {
        val testCase = TestCase(
            links = listOf(Link(title = "Spec", url = "https://example.com/spec")),
        )

        assertTrue(hasImportableRunLinks(testCase))
    }
}
