package io.github.barsia.speqa.editor.runcreation

import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.AppExecutorUtil
import io.github.barsia.speqa.editor.RunImportOptions
import io.github.barsia.speqa.model.TestRun
import io.github.barsia.speqa.parser.TestCaseParser
import io.github.barsia.speqa.parser.TestRunSerializer
import io.github.barsia.speqa.registry.IdType
import io.github.barsia.speqa.registry.SpeqaIds
import io.github.barsia.speqa.run.TestRunSupport
import io.github.barsia.speqa.toolwindow.openAndRevealTestRun
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.Callable

/**
 * Builds and writes a single multi-case test run (`.tr.md`) from a set of selected
 * test-case files. The pure run-building lives in [buildMultiCaseRun]; the VFS write/open
 * is the thin wrapper [createMultiCaseRunFile].
 */
internal object CreateMultiCaseRunWriter {

    /** Pure run builder: delegates to [TestRunSupport.createMultiCaseRun]. */
    fun buildMultiCaseRun(
        sources: List<TestRunSupport.SourceCase>,
        targetDirectoryPath: String,
        importOptions: RunImportOptions,
        runner: String,
        title: String,
    ): TestRun = TestRunSupport.createMultiCaseRun(
        cases = sources,
        targetDirectoryPath = targetDirectoryPath,
        importOptions = importOptions,
        runner = runner,
        title = title,
    )

    /** Result of the off-EDT preparation step: everything needed to write the run file. */
    private class PreparedRun(
        val destinationPath: Path,
        val runFileName: String,
        val run: TestRun,
    )

    /**
     * Parses each selected `.tc.md` file, builds a single multi-case run targeting
     * [destinationRelativePath], writes it under a unique file name, and opens it in the editor.
     *
     * Parsing and id allocation hit the VFS and the file-based index, which require a read
     * action and are slow operations forbidden on the EDT. They run in a background read
     * action; the VFS write and editor-open happen back on the EDT once preparation completes.
     */
    fun createMultiCaseRunFile(
        project: Project,
        selectedFiles: List<VirtualFile>,
        destinationRelativePath: String,
        fileName: String,
        importOptions: RunImportOptions,
        title: String,
    ) {
        ReadAction.nonBlocking(
            Callable {
                prepareRun(
                    project = project,
                    selectedFiles = selectedFiles,
                    destinationRelativePath = destinationRelativePath,
                    fileName = fileName,
                    importOptions = importOptions,
                    title = title,
                )
            },
        )
            .expireWith(project)
            .finishOnUiThread(ModalityState.defaultModalityState()) { prepared ->
                if (prepared != null) writeAndOpen(project, prepared)
            }
            .submit(AppExecutorUtil.getAppExecutorService())
    }

    /** Off-EDT step: parse the selected cases, build the run, allocate an id, and pick a file name. */
    private fun prepareRun(
        project: Project,
        selectedFiles: List<VirtualFile>,
        destinationRelativePath: String,
        fileName: String,
        importOptions: RunImportOptions,
        title: String,
    ): PreparedRun? {
        val projectRoot = project.basePath?.let(Paths::get) ?: return null
        val destinationPath = projectRoot.resolve(destinationRelativePath).normalize()

        val sources = selectedFiles.mapNotNull { file ->
            val content = readFileText(file) ?: return@mapNotNull null
            val testCase = runCatching { TestCaseParser.parse(content) }.getOrNull() ?: return@mapNotNull null
            TestRunSupport.SourceCase(testCase, file.path)
        }
        if (sources.isEmpty()) return null

        val run = buildMultiCaseRun(
            sources = sources,
            targetDirectoryPath = destinationPath.toString(),
            importOptions = importOptions,
            runner = TestRunSupport.defaultRunner(),
            title = title,
        )

        val trId = SpeqaIds.nextFreeId(project, IdType.TEST_RUN)
        val runWithId = run.copy(id = trId)

        val existingNames = runCatching {
            Files.list(destinationPath).use { stream ->
                stream.map { it.fileName.toString() }.toList().toSet()
            }
        }.getOrDefault(emptySet())
        val runFileName = TestRunSupport.normalizeRunFileName(
            requestedFileName = fileName,
            existingNames = existingNames,
        )

        return PreparedRun(destinationPath, runFileName, runWithId)
    }

    /** On-EDT step: write the run file in a write action and open it in the editor. */
    private fun writeAndOpen(project: Project, prepared: PreparedRun) {
        val runFile = runWriteAction {
            val destDir = VfsUtil.createDirectoryIfMissing(prepared.destinationPath.toString())
                ?: return@runWriteAction null
            val file = destDir.createChildData(null, prepared.runFileName)
            VfsUtil.saveText(file, TestRunSerializer.serialize(prepared.run))
            file
        } ?: return

        openAndRevealTestRun(project, runFile)
    }

    private fun readFileText(file: VirtualFile): String? {
        @Suppress("DEPRECATION")
        return runReadAction {
            FileDocumentManager.getInstance().getDocument(file)?.text
                ?: runCatching { String(file.contentsToByteArray(), file.charset) }.getOrNull()
        }
    }
}
