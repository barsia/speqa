package io.github.barsia.speqa.editor

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import io.github.barsia.speqa.SpeqaBundle
import io.github.barsia.speqa.model.TestCase
import io.github.barsia.speqa.parser.TestCaseParser
import io.github.barsia.speqa.parser.TestCaseSerializer
import io.github.barsia.speqa.parser.TestRunSerializer
import io.github.barsia.speqa.registry.IdType
import io.github.barsia.speqa.registry.SpeqaIdRegistry
import io.github.barsia.speqa.run.TestRunSupport
import io.github.barsia.speqa.settings.SpeqaSettings
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.attribute.BasicFileAttributes
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap

internal data class TestCaseHeaderMeta(
    val createdLabel: String,
    val updatedLabel: String,
)

private val headerDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm")

internal class CreatedAtResolver(
    private val resolveCreatedAt: (projectBasePath: String, filePath: String) -> Instant?,
) {
    private data class CacheKey(
        val projectBasePath: String,
        val filePath: String,
        val timestamp: Long,
    )

    private val cache = ConcurrentHashMap<CacheKey, Instant?>()

    fun resolve(projectBasePath: String, filePath: String, timestamp: Long): Instant? {
        val key = CacheKey(projectBasePath, filePath, timestamp)
        return cache.computeIfAbsent(key) {
            resolveCreatedAt(projectBasePath, filePath)
        }
    }
}

private val createdAtResolver = CreatedAtResolver(::resolveGitCreatedInstant)

internal data class ParsedTestCase(
    val testCase: TestCase,
)

internal fun parseTestCaseSafely(text: String): ParsedTestCase {
    return ParsedTestCase(TestCaseParser.parse(text))
}

internal fun hasImportableRunLinks(testCase: TestCase): Boolean = testCase.links.isNotEmpty()

internal fun writeTestCaseToDocument(
    project: Project,
    document: Document,
    testCase: TestCase,
    commandName: String,
) {
    val serialized = TestCaseSerializer.serialize(testCase)
    if (serialized == document.text) return

    ApplicationManager.getApplication().invokeLater {
        CommandProcessor.getInstance().executeCommand(project, {
            runWriteAction {
                document.setText(serialized)
            }
        }, commandName, null)
    }
}

internal fun resolveTestCaseHeaderMeta(project: Project, file: VirtualFile): TestCaseHeaderMeta {
    val updatedInstant = Instant.ofEpochMilli(file.timeStamp)
    val basePath = project.basePath
    val createdInstant = if (basePath != null) {
        createdAtResolver.resolve(basePath, file.path, file.timeStamp)
    } else {
        null
    }
        ?: resolveFileCreatedInstant(file)
        ?: updatedInstant

    return TestCaseHeaderMeta(
        createdLabel = headerDateFormatter.format(createdInstant.atZone(ZoneId.systemDefault())),
        updatedLabel = headerDateFormatter.format(updatedInstant.atZone(ZoneId.systemDefault())),
    )
}

internal fun startTestRun(project: Project, testCaseFile: VirtualFile) {
    @Suppress("DEPRECATION")
    val testCaseContent = com.intellij.openapi.application.runReadAction<String?> {
        FileDocumentManager.getInstance().getDocument(testCaseFile)?.text
            ?: runCatching { String(testCaseFile.contentsToByteArray(), testCaseFile.charset) }.getOrNull()
    } ?: return
    val testCase = runCatching { TestCaseParser.parse(testCaseContent) }.getOrElse { return }

    val now = LocalDateTime.now()
    val settings = SpeqaSettings.getInstance(project)
    val savedDestination = settings.defaultRunDestination
    val projectRoot = project.basePath?.let(Paths::get)
    if (projectRoot == null) {
        Messages.showErrorDialog(project, SpeqaBundle.message("dialog.createRun.errorNoRoot"), SpeqaBundle.message("dialog.createRun.title"))
        return
    }
    val initialDestinationPath = projectRoot.resolve(savedDestination).normalize()
    val initialExistingNames = runCatching {
        Files.list(initialDestinationPath).use { stream ->
            stream.map { it.fileName.toString() }.toList().toSet()
        }
    }.getOrDefault(emptySet())
    val defaultFileName = TestRunSupport.nextRunFileName(
        testCaseFileName = testCaseFile.name,
        now = now,
        existingNames = initialExistingNames,
    )
    val hasTags = testCase.tags.orEmpty().isNotEmpty()
    val hasEnvironment = testCase.environment.orEmpty().isNotEmpty()
    val hasTickets = testCase.steps.any { it.tickets.isNotEmpty() }
    val hasLinks = hasImportableRunLinks(testCase)
    val hasAttachments = testCase.attachments.isNotEmpty() || testCase.steps.any { it.attachments.isNotEmpty() }

    val request = RunCreationDialog(
        project = project,
        destinationRelativePath = savedDestination,
        fileName = defaultFileName,
        hasTags = hasTags,
        hasEnvironment = hasEnvironment,
        hasTickets = hasTickets,
        hasLinks = hasLinks,
        hasAttachments = hasAttachments,
    ).takeIf { it.showAndGet() }?.request ?: return

    val destinationRelativePath = request.destinationRelativePath.ifBlank { savedDestination }
    val destinationPath = projectRoot.resolve(destinationRelativePath).normalize()
    if (!destinationPath.startsWith(projectRoot.normalize())) {
        Messages.showErrorDialog(project, SpeqaBundle.message("dialog.createRun.errorOutsideProject"), SpeqaBundle.message("dialog.createRun.title"))
        return
    }
    val existingNames = runCatching {
        Files.list(destinationPath).use { stream ->
            stream.map { it.fileName.toString() }.toList().toSet()
        }
    }.getOrDefault(emptySet())
    val runFileName = TestRunSupport.normalizeRunFileName(
        requestedFileName = request.fileName.ifBlank { defaultFileName },
        existingNames = existingNames,
    )

    settings.defaultRunDestination = destinationRelativePath

    val initialRun = TestRunSupport.createInitialRun(
        testCase = testCase,
        sourceFilePath = testCaseFile.path,
        targetDirectoryPath = destinationPath.toString(),
        importOptions = request.importOptions,
    )

    val trRegistry = SpeqaIdRegistry.getInstance(project)
    trRegistry.ensureInitialized()
    val trId = trRegistry.idSet(IdType.TEST_RUN).nextFreeId()
    val initialRunWithId = initialRun.copy(id = trId)

    val runFile = runWriteAction {
        val destDir = VfsUtil.createDirectoryIfMissing(destinationPath.toString()) ?: return@runWriteAction null
        val file = destDir.createChildData(null, runFileName)
        VfsUtil.saveText(file, TestRunSerializer.serialize(initialRunWithId))
        file
    } ?: return
    trRegistry.idSet(IdType.TEST_RUN).register(trId)
    FileEditorManager.getInstance(project).openFile(runFile, true)
}

private fun resolveGitCreatedInstant(basePath: String, filePath: String): Instant? {
    val relativePath = filePath.removePrefix(basePath).trimStart('/', '\\')
    if (relativePath.isBlank()) return null

    return runCatching {
        val process = ProcessBuilder(
            "git",
            "-C",
            basePath,
            "log",
            "--diff-filter=A",
            "--follow",
            "--format=%aI",
            "--",
            relativePath,
        ).start()
        val output = process.inputStream.bufferedReader().readLines().filter(String::isNotBlank)
        process.waitFor()
        output.lastOrNull()?.let(Instant::parse)
    }.getOrNull()
}

private fun resolveFileCreatedInstant(file: VirtualFile): Instant? {
    return runCatching {
        val path: Path = Paths.get(file.path)
        if (!Files.exists(path)) return null
        Files.readAttributes(path, BasicFileAttributes::class.java).creationTime().toInstant()
    }.getOrNull()
}
