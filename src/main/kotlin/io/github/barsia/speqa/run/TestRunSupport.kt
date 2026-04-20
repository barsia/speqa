package io.github.barsia.speqa.run

import com.intellij.openapi.editor.Document
import io.github.barsia.speqa.editor.RunImportOptions
import io.github.barsia.speqa.model.Attachment
import io.github.barsia.speqa.model.SpeqaDefaults
import io.github.barsia.speqa.model.RunResult
import io.github.barsia.speqa.model.StepResult
import io.github.barsia.speqa.model.StepVerdict
import io.github.barsia.speqa.model.TestCase
import io.github.barsia.speqa.model.TestRun
import io.github.barsia.speqa.parser.TestRunParser
import java.nio.file.Path
import java.nio.file.Paths
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

internal object TestRunSupport {
    private val runFileTimestampFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")

    fun isTestCaseFile(file: com.intellij.openapi.vfs.VirtualFile?): Boolean =
        file?.name?.endsWith(".${SpeqaDefaults.TEST_CASE_EXTENSION}") == true

    fun isTestRunFile(file: com.intellij.openapi.vfs.VirtualFile?): Boolean =
        file?.name?.endsWith(".${SpeqaDefaults.TEST_RUN_EXTENSION}") == true

    fun nextRunFileName(testCaseFileName: String, now: LocalDateTime, existingNames: Set<String>): String {
        val stem = testCaseFileName.removeSuffix(".${SpeqaDefaults.TEST_CASE_EXTENSION}")
        val timestamp = runFileTimestampFormat.format(now)
        var candidate = "${stem}_$timestamp.${SpeqaDefaults.TEST_RUN_EXTENSION}"
        var counter = 2
        while (candidate in existingNames) {
            candidate = "${stem}_${timestamp}-$counter.${SpeqaDefaults.TEST_RUN_EXTENSION}"
            counter += 1
        }
        return candidate
    }

    fun normalizeRunFileName(requestedFileName: String, existingNames: Set<String>): String {
        val trimmed = requestedFileName.trim()
        val withExtension = if (trimmed.endsWith(".${SpeqaDefaults.TEST_RUN_EXTENSION}")) {
            trimmed
        } else {
            "$trimmed.${SpeqaDefaults.TEST_RUN_EXTENSION}"
        }
        if (withExtension !in existingNames) return withExtension

        val suffix = ".${SpeqaDefaults.TEST_RUN_EXTENSION}"
        val stem = withExtension.removeSuffix(suffix)
        var counter = 2
        var candidate = "$stem-$counter$suffix"
        while (candidate in existingNames) {
            counter += 1
            candidate = "$stem-$counter$suffix"
        }
        return candidate
    }

    fun createInitialRun(
        testCase: TestCase,
        sourceFilePath: String,
        targetDirectoryPath: String,
        importOptions: RunImportOptions = RunImportOptions(),
        runner: String = defaultRunner(),
    ): TestRun {
        return TestRun(
            title = testCase.title,
            tags = if (importOptions.importTags) testCase.tags.orEmpty() else emptyList(),
            priority = testCase.priority,
            environment = if (importOptions.importEnvironment) testCase.environment.orEmpty() else emptyList(),
            runner = runner,
            bodyBlocks = testCase.bodyBlocks,
            links = if (importOptions.importLinks) testCase.links else emptyList(),
            attachments = if (importOptions.importAttachments) {
                rebaseAttachments(testCase.attachments, sourceFilePath, targetDirectoryPath)
            } else {
                emptyList()
            },
            stepResults = testCase.steps.map { step ->
                StepResult(
                    action = step.action,
                    expected = step.expected.orEmpty(),
                    tickets = if (importOptions.importTickets) step.tickets else emptyList(),
                    links = if (importOptions.importLinks) step.links else emptyList(),
                    attachments = if (importOptions.importAttachments) {
                        rebaseAttachments(step.attachments, sourceFilePath, targetDirectoryPath)
                    } else {
                        emptyList()
                    },
                )
            },
        )
    }

    fun createInitialRun(
        testCase: TestCase,
        sourceFilePath: String,
        targetDirectoryPath: String,
        runner: String,
    ): TestRun {
        return createInitialRun(
            testCase = testCase,
            sourceFilePath = sourceFilePath,
            targetDirectoryPath = targetDirectoryPath,
            importOptions = RunImportOptions(),
            runner = runner,
        )
    }

    fun rebaseAttachmentPath(sourcePath: String, sourceFilePath: String, targetDirectoryPath: String): String {
        val sourceDir = Paths.get(sourceFilePath).normalize().parent ?: return sourcePath
        val targetDir = Paths.get(targetDirectoryPath).normalize()
        val rebased = try {
            targetDir.relativize(sourceDir.resolve(sourcePath).normalize())
        } catch (_: IllegalArgumentException) {
            return sourcePath
        }
        return rebased.invariantSeparatorsPath
    }

    fun parseTestRunOrNull(content: String): TestRun? = runCatching {
        TestRunParser.parse(content)
    }.getOrNull()

    fun deriveRunResult(stepResults: List<StepResult>): RunResult {
        if (stepResults.isEmpty()) return RunResult.NOT_STARTED
        val allNone = stepResults.all { it.verdict == StepVerdict.NONE }
        if (allNone) return RunResult.NOT_STARTED
        val hasNone = stepResults.any { it.verdict == StepVerdict.NONE }
        if (hasNone) return RunResult.IN_PROGRESS
        val meaningful = stepResults.filter { it.verdict != StepVerdict.SKIPPED }
        if (meaningful.any { it.verdict == StepVerdict.FAILED }) return RunResult.FAILED
        if (meaningful.any { it.verdict == StepVerdict.BLOCKED }) return RunResult.BLOCKED
        return RunResult.PASSED
    }

    fun updateDocument(document: Document, content: String): Boolean {
        if (document.text == content) {
            return false
        }
        document.setText(content)
        return true
    }

    fun defaultRunner(): String = System.getProperty("user.name").orEmpty()

    private fun rebaseAttachments(
        attachments: List<Attachment>,
        sourceFilePath: String,
        targetDirectoryPath: String,
    ): List<Attachment> {
        return attachments.map { attachment ->
            Attachment(
                rebaseAttachmentPath(attachment.path, sourceFilePath, targetDirectoryPath),
            )
        }
    }

    private val Path.invariantSeparatorsPath: String
        get() = toString().replace('\\', '/')
}
