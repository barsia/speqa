package io.github.barsia.speqa.toolwindow

import com.intellij.openapi.vfs.VirtualFile
import io.github.barsia.speqa.SpeqaBundle
import io.github.barsia.speqa.filetype.SpeqaIcons
import javax.swing.Icon

/** [SpeqaLeafSpec] for the TCs tab: `.tc.md` files matched against a [SpeqaTreeFilter]. */
class TestCaseLeafSpec(
    private val cache: TestCaseSummaryCache,
    private val filter: SpeqaTreeFilter,
) : SpeqaLeafSpec {
    override fun isLeaf(name: String): Boolean = isTestCaseFileName(name)
    override fun isFiltering(): Boolean = !filter.isEmpty()
    override fun matches(file: VirtualFile): Boolean = matchesFilter(cache.summaryFor(file), filter)
    override fun title(file: VirtualFile): String = cache.summaryFor(file).title
    override fun icon(file: VirtualFile): Icon = SpeqaIcons.forStatus(cache.summaryFor(file).status)
}

/** [SpeqaLeafSpec] for the TRs tab: `.tr.md` files matched against a [TestRunTreeFilter]. */
class TestRunLeafSpec(
    private val cache: TestRunSummaryCache,
    private val filter: TestRunTreeFilter,
) : SpeqaLeafSpec {
    override fun isLeaf(name: String): Boolean = isTestRunFileName(name)
    override fun isFiltering(): Boolean = !filter.isEmpty()
    override fun matches(file: VirtualFile): Boolean = matchesRunFilter(cache.summaryFor(file), filter)
    override fun title(file: VirtualFile): String =
        cache.summaryFor(file).title.ifBlank { SpeqaBundle.message("toolwindow.speqa.untitledRun") }
    override fun icon(file: VirtualFile): Icon = SpeqaIcons.forResult(cache.summaryFor(file).result)
}
