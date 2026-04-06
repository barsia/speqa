package io.github.barsia.speqa.filetype

import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

object SpeqaIcons {
    val PluginIcon: Icon = IconLoader.getIcon("/icons/speqa16.svg", SpeqaIcons::class.java)
    val TestCaseDraft: Icon = IconLoader.getIcon("/icons/testCaseDraft.svg", SpeqaIcons::class.java)
    val TestCaseReady: Icon = IconLoader.getIcon("/icons/testCaseReady.svg", SpeqaIcons::class.java)
    val TestCaseDeprecated: Icon = IconLoader.getIcon("/icons/testCaseDeprecated.svg", SpeqaIcons::class.java)
    val TestRunPassed: Icon = IconLoader.getIcon("/icons/testRunPassed.svg", SpeqaIcons::class.java)
    val TestRunFailed: Icon = IconLoader.getIcon("/icons/testRunFailed.svg", SpeqaIcons::class.java)
    val TestRunBlocked: Icon = IconLoader.getIcon("/icons/testRunBlocked.svg", SpeqaIcons::class.java)
}
