package io.github.barsia.speqa.actions

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.IconLoader
import com.intellij.ui.HyperlinkLabel
import com.intellij.ui.components.JBLabel
import com.intellij.util.IconUtil
import com.intellij.util.ui.JBUI
import io.github.barsia.speqa.SpeqaBundle
import io.github.barsia.speqa.filetype.SpeqaIcons
import io.github.barsia.speqa.model.SpeqaDefaults
import java.awt.BorderLayout
import java.awt.Cursor
import java.awt.Font
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.AbstractButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingConstants

class AboutSpeqaAction : AnAction(), DumbAware {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project
        val version = PluginManagerCore.getPlugin(PluginId.getId("io.github.barsia.speqa"))?.version ?: "unknown"
        val appInfo = com.intellij.openapi.application.ApplicationInfo.getInstance()
        val environment = java.net.URLEncoder.encode("SpeQA $version | ${appInfo.fullApplicationName}", "UTF-8")
        val bugUrl = "https://github.com/barsia/speqa/issues/new?template=bug_report.yml&environment=$environment"
        val featureUrl = "https://github.com/barsia/speqa/issues/new?template=feature_request.yml"

        val dialog = object : DialogWrapper(project, false) {
            init {
                title = SpeqaBundle.message("about.title")
                init()
            }

            override fun createCenterPanel(): JComponent {
                val logo = JBLabel(IconUtil.scale(SpeqaIcons.PluginIcon, null, 2.5f)).apply {
                    verticalAlignment = SwingConstants.TOP
                    border = JBUI.Borders.empty(3, 0, 0, 12)
                }

                val infoPanel = JPanel(GridBagLayout())
                val gbc = GridBagConstraints().apply {
                    gridx = 0
                    gridy = 0
                    anchor = GridBagConstraints.WEST
                    fill = GridBagConstraints.NONE
                    weightx = 1.0
                    insets = JBUI.emptyInsets()
                }

                // Title + version on the same line
                val titlePanel = JPanel(GridBagLayout())
                val titleGbc = GridBagConstraints().apply {
                    gridy = 0
                    anchor = GridBagConstraints.BASELINE_LEADING
                }
                titleGbc.gridx = 0
                titlePanel.add(JBLabel("SpeQA").apply {
                    font = font.deriveFont(Font.BOLD, font.size + 4f)
                }, titleGbc)
                titleGbc.gridx = 1
                titleGbc.insets = JBUI.insetsLeft(6)
                titlePanel.add(JBLabel(SpeqaBundle.message("about.version", version)).apply {
                    font = font.deriveFont(font.size - 1f)
                    foreground = JBUI.CurrentTheme.Label.disabledForeground()
                }, titleGbc)
                infoPanel.add(titlePanel, gbc)

                // Subtitle
                gbc.gridy++
                gbc.insets = JBUI.insets(2, 0, 2, 0)
                infoPanel.add(JBLabel(SpeqaBundle.message("about.subtitle")).apply {
                    font = font.deriveFont(font.size - 1f)
                    foreground = JBUI.CurrentTheme.Label.disabledForeground()
                }, gbc)

                // Vendor
                gbc.gridy++
                gbc.insets = JBUI.insetsBottom(10)
                infoPanel.add(JBLabel(SpeqaBundle.message("about.vendor")).apply {
                    font = font.deriveFont(font.size - 1f)
                    val dim = JBUI.CurrentTheme.Label.disabledForeground()
                    val norm = javax.swing.UIManager.getColor("Label.foreground")
                    foreground = com.intellij.ui.JBColor(
                        java.awt.Color((dim.red + norm.red) / 2, (dim.green + norm.green) / 2, (dim.blue + norm.blue) / 2),
                        java.awt.Color((dim.red + norm.red) / 2, (dim.green + norm.green) / 2, (dim.blue + norm.blue) / 2)
                    )
                }, gbc)

                // Bug report & Feature request (icon + link pairs)
                gbc.gridy++
                gbc.insets = JBUI.insetsBottom(4)
                val issuePanel = JPanel(GridBagLayout())
                val issueGbc = GridBagConstraints().apply {
                    gridy = 0
                    anchor = GridBagConstraints.WEST
                    insets = JBUI.emptyInsets()
                }
                val bugIcon = IconLoader.getIcon("/icons/bug.svg", AboutSpeqaAction::class.java)
                val featureIcon = IconLoader.getIcon("/icons/feature.svg", AboutSpeqaAction::class.java)

                val smallFont = javax.swing.UIManager.getFont("Label.font").deriveFont(javax.swing.UIManager.getFont("Label.font").size - 2f)

                issueGbc.gridx = 0
                issueGbc.insets = JBUI.insetsRight(12)
                issuePanel.add(createClickableIconLink(bugIcon, SpeqaBundle.message("about.link.reportBug"),
                    bugUrl, smallFont), issueGbc)
                issueGbc.gridx = 1
                issueGbc.insets = JBUI.emptyInsets()
                issuePanel.add(createClickableIconLink(featureIcon, SpeqaBundle.message("about.link.requestFeature"),
                    featureUrl, smallFont), issueGbc)
                infoPanel.add(issuePanel, gbc)

                // GitHub | Website links
                gbc.gridy++
                gbc.insets = JBUI.emptyInsets()
                val linksPanel = JPanel(GridBagLayout())
                val linkGbc = GridBagConstraints().apply {
                    gridy = 0
                    anchor = GridBagConstraints.WEST
                    insets = JBUI.emptyInsets()
                }
                linkGbc.gridx = 0
                linksPanel.add(HyperlinkLabel(SpeqaBundle.message("about.link.github")).apply {
                    font = smallFont
                    setHyperlinkTarget("https://github.com/barsia/speqa")
                }, linkGbc)
                linkGbc.gridx = 1
                linkGbc.insets = JBUI.insets(0, 4, 0, 4)
                linksPanel.add(JBLabel("|").apply {
                    font = smallFont
                    foreground = JBUI.CurrentTheme.Label.disabledForeground()
                }, linkGbc)
                linkGbc.gridx = 2
                linkGbc.insets = JBUI.emptyInsets()
                linksPanel.add(HyperlinkLabel(SpeqaBundle.message("about.link.website")).apply {
                    font = smallFont
                    setHyperlinkTarget("https://barsia.github.io")
                }, linkGbc)
                infoPanel.add(linksPanel, gbc)

                return JPanel(BorderLayout()).apply {
                    border = JBUI.Borders.empty(0, 10, 10, 10)
                    add(logo, BorderLayout.WEST)
                    add(infoPanel, BorderLayout.CENTER)
                }
            }

            override fun createActions() = arrayOf(okAction, cancelAction)

            override fun getCancelAction() = super.getCancelAction().apply {
                putValue(javax.swing.Action.NAME, SpeqaBundle.message("action.close"))
            }

            override fun createSouthPanel(): JComponent? {
                val south = super.createSouthPanel() ?: return null
                setButtonCursors(south)
                return south
            }

            override fun doOKAction() {
                val clipboardText = buildString {
                    appendLine(appInfo.fullApplicationName)
                    appendLine("Build #${appInfo.build}, built on ${appInfo.buildDate?.let {
                        java.text.SimpleDateFormat("MMMM d, yyyy", java.util.Locale.ENGLISH).format(it.time)
                    } ?: "unknown"}")
                    append("SpeQA $version")
                }
                val selection = java.awt.datatransfer.StringSelection(clipboardText)
                java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, null)
                super.doOKAction()
            }

            override fun getOKAction() = super.getOKAction().apply {
                putValue(javax.swing.Action.NAME, SpeqaBundle.message("action.copyAndClose"))
            }
        }
        dialog.show()
    }

    override fun update(e: AnActionEvent) {
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
            ?: e.project?.let { FileEditorManager.getInstance(it).selectedEditor?.file }
        e.presentation.isVisible = file != null && isSpeqaFile(file.name)
        e.presentation.isEnabled = e.presentation.isVisible
    }

    override fun getActionUpdateThread() = ActionUpdateThread.EDT

    private fun isSpeqaFile(name: String): Boolean =
        name.endsWith(".${SpeqaDefaults.TEST_CASE_EXTENSION}") ||
            name.endsWith(".${SpeqaDefaults.TEST_RUN_EXTENSION}")

    private fun setButtonCursors(component: JComponent) {
        for (child in component.components) {
            if (child is AbstractButton) {
                child.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            }
            if (child is JComponent) setButtonCursors(child)
        }
    }

    private fun createClickableIconLink(icon: javax.swing.Icon, text: String, url: String, font: Font): JComponent {
        val panel = JPanel(GridBagLayout()).apply {
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            isOpaque = false
        }
        val gbc = GridBagConstraints().apply {
            gridy = 0
            anchor = GridBagConstraints.WEST
        }
        gbc.gridx = 0
        gbc.insets = JBUI.insetsRight(2)
        panel.add(JBLabel(icon), gbc)
        gbc.gridx = 1
        gbc.insets = JBUI.emptyInsets()
        val link = HyperlinkLabel(text).apply {
            this.font = font
            setHyperlinkTarget(url)
        }
        panel.add(link, gbc)
        panel.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: java.awt.event.MouseEvent?) {
                com.intellij.ide.BrowserUtil.browse(url)
            }
        })
        return panel
    }
}
