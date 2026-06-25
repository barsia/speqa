package io.github.barsia.speqa.filetype

import com.intellij.openapi.util.IconLoader
import io.github.barsia.speqa.model.Status
import javax.swing.Icon

object SpeqaIcons {
    val PluginIcon: Icon = IconLoader.getIcon("/icons/speqa16.svg", SpeqaIcons::class.java)
    val TestCaseDraft: Icon = IconLoader.getIcon("/icons/testCaseDraft.svg", SpeqaIcons::class.java)
    val TestCaseReady: Icon = IconLoader.getIcon("/icons/testCaseReady.svg", SpeqaIcons::class.java)
    val TestCaseDeprecated: Icon = IconLoader.getIcon("/icons/testCaseDeprecated.svg", SpeqaIcons::class.java)
    val TestRunPassed: Icon = IconLoader.getIcon("/icons/testRunPassed.svg", SpeqaIcons::class.java)
    val TestRunFailed: Icon = IconLoader.getIcon("/icons/testRunFailed.svg", SpeqaIcons::class.java)
    val TestRunBlocked: Icon = IconLoader.getIcon("/icons/testRunBlocked.svg", SpeqaIcons::class.java)

    val FilterStatus: Icon = IconLoader.getIcon("/icons/filterStatus.svg", SpeqaIcons::class.java)
    val FilterPriority: Icon = IconLoader.getIcon("/icons/filterPriority.svg", SpeqaIcons::class.java)
    val FilterTags: Icon = IconLoader.getIcon("/icons/filterTags.svg", SpeqaIcons::class.java)
    val FilterEnvironment: Icon = IconLoader.getIcon("/icons/filterEnvironment.svg", SpeqaIcons::class.java)

    fun forStatus(status: Status): Icon = when (status) {
        Status.DRAFT -> TestCaseDraft
        Status.READY -> TestCaseReady
        Status.DEPRECATED -> TestCaseDeprecated
    }
}
