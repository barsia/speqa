package io.github.barsia.speqa.actions

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import io.github.barsia.speqa.SpeqaBundle
import io.github.barsia.speqa.registry.DuplicateIdReviewRow
import com.intellij.util.ui.JBUI
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.table.DefaultTableModel

class ResolveDuplicateIdsDialog(
    project: Project,
    private val review: List<DuplicateIdReviewRow>,
) : DialogWrapper(project) {

    private val basePath: String? = project.basePath

    init {
        title = SpeqaBundle.message("resolveDuplicateIds.dialog.title")
        setOKButtonText(SpeqaBundle.message("resolveDuplicateIds.apply"))
        init()
    }

    override fun createNorthPanel(): JComponent =
        JBLabel(SpeqaBundle.message("resolveDuplicateIds.dialog.header")).apply {
            border = JBUI.Borders.emptyBottom(10)
        }

    override fun createCenterPanel(): JComponent {
        val columns = arrayOf(
            SpeqaBundle.message("resolveDuplicateIds.column.file"),
            SpeqaBundle.message("resolveDuplicateIds.column.currentId"),
            SpeqaBundle.message("resolveDuplicateIds.column.newId"),
        )
        val model = object : DefaultTableModel(columns, 0) {
            override fun isCellEditable(row: Int, column: Int): Boolean = false
        }
        for (row in review) {
            val newCell = if (row.keepsId) {
                SpeqaBundle.message("resolveDuplicateIds.keptId", row.oldId)
            } else {
                "TC-${row.newId}"
            }
            model.addRow(arrayOf(displayPath(row.path), "TC-${row.oldId}", newCell))
        }
        val table = JBTable(model)
        // File column takes the space; the two id columns stay narrow.
        table.columnModel.getColumn(0).preferredWidth = JBUI.scale(420)
        listOf(1, 2).forEach { col ->
            table.columnModel.getColumn(col).apply {
                preferredWidth = JBUI.scale(84)
                maxWidth = JBUI.scale(120)
            }
        }
        val scroll = JBScrollPane(table)
        scroll.preferredSize = Dimension(640, 320)
        return scroll
    }

    private fun displayPath(path: String): String {
        val base = basePath ?: return path
        return path.removePrefix(base).trimStart('/', '\\').ifEmpty { path }
    }
}
