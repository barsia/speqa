package io.github.barsia.speqa.error

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.diagnostic.ErrorReportSubmitter
import com.intellij.openapi.diagnostic.IdeaLoggingEvent
import com.intellij.openapi.diagnostic.SubmittedReportInfo
import com.intellij.openapi.extensions.PluginId
import com.intellij.util.Consumer
import com.intellij.util.concurrency.AppExecutorUtil
import io.github.barsia.speqa.SpeqaBundle
import java.awt.Component

class SpeqaErrorReportSubmitter : ErrorReportSubmitter() {

    override fun getReportActionText(): String =
        SpeqaBundle.message("error.report.actionText")

    override fun getPrivacyNoticeText(): String =
        SpeqaBundle.message("error.report.privacyNotice")

    override fun submit(
        events: Array<out IdeaLoggingEvent>,
        additionalInfo: String?,
        parentComponent: Component,
        consumer: Consumer<in SubmittedReportInfo>,
    ): Boolean {
        val event = events.firstOrNull() ?: return false
        val throwable = event.throwable ?: return false

        val sanitized = SpeqaStacktraceSanitizer.sanitize(throwable)
        val pluginVersion = PluginManagerCore.getPlugin(PluginId.getId("io.github.barsia.speqa"))
            ?.version ?: "unknown"
        val appInfo = ApplicationInfo.getInstance()
        val ideVersion = appInfo.build.asString()
        val osInfo = "${System.getProperty("os.name")} ${System.getProperty("os.version")}"
        val javaVersion = System.getProperty("java.version") ?: "unknown"

        AppExecutorUtil.getAppExecutorService().submit {
            val sent = SpeqaSentryClient.send(
                stacktrace = sanitized,
                pluginVersion = pluginVersion,
                ideVersion = ideVersion,
                osInfo = osInfo,
                javaVersion = javaVersion,
                userComment = (additionalInfo ?: "").trim().take(1000),
            )
            val status = if (sent) {
                SubmittedReportInfo.SubmissionStatus.NEW_ISSUE
            } else {
                SubmittedReportInfo.SubmissionStatus.FAILED
            }
            consumer.consume(SubmittedReportInfo(status))
        }

        return true
    }
}
