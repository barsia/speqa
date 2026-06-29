package io.github.barsia.speqa.toolwindow

import com.intellij.ide.util.treeView.NodeRenderer
import com.intellij.ide.util.treeView.TreeState
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.CommonShortcuts
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.DoubleClickListener
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.TreeSpeedSearch
import com.intellij.ui.components.JBLabel
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.content.ContentManagerEvent
import com.intellij.ui.content.ContentManagerListener
import com.intellij.ui.tree.AsyncTreeModel
import com.intellij.ui.tree.StructureTreeModel
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.IconUtil
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.tree.TreeUtil
import io.github.barsia.speqa.SpeqaBundle
import io.github.barsia.speqa.filetype.SpeqaIcons
import io.github.barsia.speqa.editor.ui.chips.MetadataScope
import io.github.barsia.speqa.registry.SpeqaTagRegistry
import io.github.barsia.speqa.wizard.SpeqaProjectScaffold
import io.github.barsia.speqa.editor.ui.primitives.handCursor
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Component
import java.awt.event.MouseEvent
import java.util.concurrent.Callable
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

class SpeqaToolWindowFactory : ToolWindowFactory, DumbAware {

    override fun shouldBeAvailable(project: Project): Boolean = true

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        // Show an immediate "Loading..." placeholder so the tool window never flashes the
        // platform default empty state ("Nothing to show") while the first content build runs
        // off the EDT. The first buildContent replaces it via removeAllContents.
        installLoadingPlaceholder(toolWindow)
        // Holds the disposable scoping the current build's content listeners, so a rebuild
        // can release the previous build's listeners before reinstalling.
        val contentDisposable = arrayOfNulls<Disposable>(1)
        buildContent(project, toolWindow, contentDisposable)

        // Resolving the SpeQA directories' initial existence calls VFS findChild, which hits
        // the persistence layer - a slow operation forbidden on the EDT. Resolve it in a
        // background read action, then install the directory watcher on the EDT.
        ReadAction.nonBlocking(Callable { resolveDirectoryWatchState(project) })
            .expireWith(toolWindow.disposable)
            .finishOnUiThread(ModalityState.any()) { state ->
                if (state != null) installDirectoryWatcher(project, toolWindow, contentDisposable, state)
            }
            .submit(AppExecutorUtil.getAppExecutorService())
    }

    /** Off-EDT: resolve the project dir and whether the SpeQA directories currently exist. */
    private fun resolveDirectoryWatchState(project: Project): DirectoryWatchState? {
        val projectDir = project.guessProjectDir() ?: return null
        return DirectoryWatchState(
            projectDir = projectDir,
            tcPath = "${projectDir.path}/${SpeqaProjectScaffold.TEST_CASES_DIR}",
            trPath = "${projectDir.path}/${SpeqaProjectScaffold.TEST_RUNS_DIR}",
            hadTc = projectDir.findChild(SpeqaProjectScaffold.TEST_CASES_DIR) != null,
            hadTr = projectDir.findChild(SpeqaProjectScaffold.TEST_RUNS_DIR) != null,
        )
    }

    /**
     * Installs the VFS listener that rebuilds the tool-window content when the test-cases or
     * test-runs directories appear or disappear. The [state]'s existence flags were resolved
     * off the EDT; subsequent findChild calls here run from a VFS change callback, when the
     * affected children are already loaded.
     */
    private fun installDirectoryWatcher(
        project: Project,
        toolWindow: ToolWindow,
        contentDisposable: Array<Disposable?>,
        state: DirectoryWatchState,
    ) {
        val projectDir = state.projectDir
        var hadTc = state.hadTc
        var hadTr = state.hadTr
        project.messageBus.connect(toolWindow.disposable)
            .subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
                override fun after(events: List<VFileEvent>) {
                    // Only the SpeQA directories themselves changing existence triggers a
                    // rebuild, not edits to files inside them (those are handled per-tab).
                    if (events.none { it.path == state.tcPath || it.path == state.trPath }) return
                    val hasTc = projectDir.findChild(SpeqaProjectScaffold.TEST_CASES_DIR) != null
                    val hasTr = projectDir.findChild(SpeqaProjectScaffold.TEST_RUNS_DIR) != null
                    if (hasTc == hadTc && hasTr == hadTr) return
                    hadTc = hasTc
                    hadTr = hasTr
                    SwingUtilities.invokeLater {
                        buildContent(project, toolWindow, contentDisposable)
                    }
                }
            })
    }

    /** Project-directory existence state for the SpeQA dirs, resolved off the EDT. */
    private class DirectoryWatchState(
        val projectDir: VirtualFile,
        val tcPath: String,
        val trPath: String,
        val hadTc: Boolean,
        val hadTr: Boolean,
    )

    /**
     * Adds a transient "Loading..." content shown until the first real build completes. It uses
     * a [com.intellij.ui.components.JBPanelWithEmptyText] so the text renders via the same
     * StatusText placement as the tree's empty text, so swapping to the tree does not move it.
     */
    private fun installLoadingPlaceholder(toolWindow: ToolWindow) {
        val panel = com.intellij.ui.components.JBPanelWithEmptyText().apply {
            emptyText.text = SpeqaBundle.message("toolwindow.speqa.loading")
        }
        val content = ContentFactory.getInstance().createContent(panel, "", false).apply {
            isCloseable = false
        }
        toolWindow.contentManager.addContent(content)
    }

    /** Resolves directories off the EDT, then builds and installs the tabs on the EDT. */
    private fun buildContent(project: Project, toolWindow: ToolWindow, contentDisposable: Array<Disposable?>) {
        // Resolving the test-case/test-run directories and scanning them for leaves hits
        // the VFS persistence layer, which is a slow operation forbidden on the EDT.
        // Do it in a background read action, then build and install the Swing UI on the EDT.
        // Clearing and installing in the same EDT task keeps overlapping rebuilds from
        // producing duplicate tabs: the last completed build simply wins.
        ReadAction.nonBlocking(Callable { prepareContent(project) })
            .expireWith(toolWindow.disposable)
            .finishOnUiThread(ModalityState.any()) { prepared ->
                // Release the previous build's content listeners BEFORE clearing the tabs.
                // Otherwise the stale ContentManagerListener reacts to the auto-selection that
                // removeAllContents triggers (the remaining tab becomes selected) and corrupts
                // the persisted selected tab, so the rebuild lands on the wrong tab.
                contentDisposable[0]?.let { Disposer.dispose(it) }
                val disposable = Disposer.newDisposable(toolWindow.disposable, "SpeqaToolWindowContent")
                contentDisposable[0] = disposable
                toolWindow.contentManager.removeAllContents(true)
                if (prepared != null) installContent(project, toolWindow, prepared, disposable)
            }
            .submit(AppExecutorUtil.getAppExecutorService())
    }

    /** Off-EDT: resolve directories, build filters/caches/specs, and scan for leaves. */
    private fun prepareContent(project: Project): PreparedContent? {
        val tcDir = projectDirChild(project, SpeqaProjectScaffold.TEST_CASES_DIR)
        val trDir = projectDirChild(project, SpeqaProjectScaffold.TEST_RUNS_DIR)

        val tcFilter = SpeqaTreeFilter()
        val tcCache = TestCaseSummaryCache()
        val tcSpec = TestCaseLeafSpec(tcCache, tcFilter)

        val trFilter = TestRunTreeFilter()
        val trCache = TestRunSummaryCache()
        val trSpec = TestRunLeafSpec(trCache, trFilter)

        return PreparedContent(
            registry = SpeqaTagRegistry.getInstance(project),
            tcDir = tcDir,
            tcFilter = tcFilter,
            tcCache = tcCache,
            tcSpec = tcSpec,
            tcHasLeaves = tcDir != null && hasAnyLeaf(tcDir, tcSpec),
            trDir = trDir,
            trFilter = trFilter,
            trCache = trCache,
            trSpec = trSpec,
            trHasLeaves = trDir != null && hasAnyLeaf(trDir, trSpec),
            tcLeavesForCta = tcLeafsExist(project),
        )
    }

    /** On the EDT: build the tabs from the precomputed [prepared] state and install them. */
    private fun installContent(project: Project, toolWindow: ToolWindow, prepared: PreparedContent, contentDisposable: Disposable) {
        val registry = prepared.registry

        val tcCtaButton = JButton(SpeqaBundle.message("toolwindow.speqa.empty.ctaLabel")).apply {
            handCursor()
        }
        val tcCtaPanel = buildCtaPanel(
            icon = SpeqaIcons.TestCaseDraft,
            title = SpeqaBundle.message("toolwindow.speqa.empty.title"),
            description = SpeqaBundle.message("toolwindow.speqa.empty.description"),
            button = tcCtaButton,
        )
        val tcTab = buildTab(
            project, contentDisposable,
            displayName = SpeqaBundle.message("toolwindow.speqa.tab.testCases"),
            rootDir = prepared.tcDir,
            spec = prepared.tcSpec,
            emptyText = SpeqaBundle.message("toolwindow.speqa.empty"),
            invalidateCache = prepared.tcCache::invalidate,
            filter = prepared.tcFilter,
            primary = statusPrimaryFacet(prepared.tcFilter),
            scope = MetadataScope.TEST_CASES,
            knownTags = { registry.allTags.toSet() },
            knownEnvironments = { registry.allEnvironments.toSet() },
            stateStore = SpeqaToolWindowTreeState.getInstance(project),
            initialHasLeaves = prepared.tcHasLeaves,
            leadingActions = { tree -> listOf(CreateTestCaseToolWindowAction(tree)) },
            ctaComponent = tcCtaPanel,
        )
        tcCtaButton.addActionListener {
            tcTab.tree?.let { createTestCaseFromToolWindow(project, it) }
        }

        val trFilter = prepared.trFilter
        val trCache = prepared.trCache

        val trCtaButton = JButton(SpeqaBundle.message("toolwindow.speqa.emptyRuns.ctaLabel")).apply {
            handCursor()
            isEnabled = prepared.tcLeavesForCta
            toolTipText = if (!isEnabled) SpeqaBundle.message("toolwindow.speqa.emptyRuns.ctaHint") else null
            addActionListener { openCreateTestRunDialog(project) }
        }
        val trCtaPanel = buildCtaPanel(
            icon = SpeqaIcons.TestRunNotStarted,
            title = SpeqaBundle.message("toolwindow.speqa.emptyRuns.title"),
            description = SpeqaBundle.message("toolwindow.speqa.emptyRuns.description"),
            button = trCtaButton,
        )

        val trTab = buildTab(
            project, contentDisposable,
            displayName = SpeqaBundle.message("toolwindow.speqa.tab.testRuns"),
            rootDir = prepared.trDir,
            spec = prepared.trSpec,
            emptyText = SpeqaBundle.message("toolwindow.speqa.emptyRuns"),
            invalidateCache = trCache::invalidate,
            filter = trFilter,
            primary = resultPrimaryFacet(trFilter),
            scope = MetadataScope.TEST_RUNS,
            knownTags = { registry.allTestRunTags.toSet() },
            knownEnvironments = { registry.allTestRunEnvironments.toSet() },
            stateStore = SpeqaTestRunsToolWindowTreeState.getInstance(project),
            initialHasLeaves = prepared.trHasLeaves,
            leadingActions = { tree -> listOf(CreateTestRunAction(tree)) },
            ctaComponent = trCtaPanel,
        )

        // Keep the CTA button enabled state current when TC files are added/removed.
        val tcDirForCta = prepared.tcDir
        if (tcDirForCta != null) {
            val tcRootPath = tcDirForCta.path
            val tcDescendantPrefix = "$tcRootPath/"
            project.messageBus.connect(contentDisposable)
                .subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
                    override fun after(events: List<VFileEvent>) {
                        if (events.none { it.path == tcRootPath || it.path.startsWith(tcDescendantPrefix) }) return
                        val hasTc = tcLeafsExist(project)
                        SwingUtilities.invokeLater {
                            trCtaButton.isEnabled = hasTc
                            trCtaButton.toolTipText = if (!hasTc) SpeqaBundle.message("toolwindow.speqa.emptyRuns.ctaHint") else null
                        }
                    }
                })
        }

        val contentManager = toolWindow.contentManager
        contentManager.addContent(tcTab.content)
        contentManager.addContent(trTab.content)

        val selectionStore = SpeqaToolWindowSelection.getInstance(project)

        // Title actions are window-global, so install the active tab's filter actions
        // and swap them whenever the selected content changes. The selected tab is also
        // persisted so the tool window reopens on it.
        val contentListener = object : ContentManagerListener {
            override fun selectionChanged(event: ContentManagerEvent) {
                if (event.operation != ContentManagerEvent.ContentOperation.add) return
                val (header, tabId) = when (event.content) {
                    tcTab.content -> tcTab.header to SpeqaToolWindowSelection.TAB_TEST_CASES
                    trTab.content -> trTab.header to SpeqaToolWindowSelection.TAB_TEST_RUNS
                    else -> return
                }
                toolWindow.setTitleActions(header.titleActions)
                selectionStore.selectedTab = tabId
            }
        }
        contentManager.addContentManagerListener(contentListener)
        // Scope the listener to this build: a rebuild disposes it before removeAllContents,
        // so the stale listener cannot rewrite the selected tab during teardown.
        Disposer.register(contentDisposable) { contentManager.removeContentManagerListener(contentListener) }

        val initialTab =
            if (selectionStore.selectedTab == SpeqaToolWindowSelection.TAB_TEST_RUNS) trTab else tcTab
        contentManager.setSelectedContent(initialTab.content)
        // Apply the title actions explicitly: selecting the already-current first content
        // does not fire selectionChanged.
        toolWindow.setTitleActions(initialTab.header.titleActions)

        // Let the test-run creation flow reveal a freshly created run in this tab: activate the
        // TRs tab and select the new row, without pulling focus back from the run editor. The
        // model is invalidated first so the row for the just-created file exists before the
        // visitor walks the tree. Scoped to this build's disposable, so a rebuild swaps it out.
        val trTree = trTab.tree
        val trModel = trTab.treeModel
        if (trTree != null && trModel != null) {
            val revealer = TestRunRevealer { file ->
                contentManager.setSelectedContent(trTab.content)
                toolWindow.activate(null, false)
                trModel.invalidateAsync().whenComplete { _, _ ->
                    SwingUtilities.invokeLater { selectFileInTree(trTree, file) }
                }
            }
            SpeqaTestRunRevealService.getInstance(project).register(revealer, contentDisposable)
        }

        // Surface "About SpeQA" as the last entry in the tool-window options menu,
        // mirroring its placement in the editor's entry-point menu.
        ActionManager.getInstance().getAction("Speqa.About")?.let { about ->
            toolWindow.setAdditionalGearActions(DefaultActionGroup(about))
        }
    }

    private class Tab(
        val content: Content,
        val header: SpeqaFilterHeader,
        val tree: Tree? = null,
        val treeModel: StructureTreeModel<SpeqaTreeStructure>? = null,
    )

    /** Centered empty-state panel: a large stamp icon, a title, a one-line description, and a button. */
    private fun buildCtaPanel(icon: javax.swing.Icon, title: String, description: String, button: JButton): JPanel =
        JPanel(java.awt.GridBagLayout()).apply {
            isOpaque = false
            val iconLabel = JBLabel(IconUtil.scale(icon, null, 2.5f)).apply {
                alignmentX = Component.CENTER_ALIGNMENT
            }
            val titleLabel = JBLabel(title).apply {
                font = JBFont.label().asBold().biggerOn(1f)
                alignmentX = Component.CENTER_ALIGNMENT
            }
            val descLabel = JBLabel(
                "<html><div style='text-align:center;width:${JBUI.scale(200)}px'>$description</div></html>",
            ).apply {
                foreground = UIUtil.getContextHelpForeground()
                alignmentX = Component.CENTER_ALIGNMENT
            }
            button.alignmentX = Component.CENTER_ALIGNMENT
            val inner = JPanel().apply {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                isOpaque = false
                add(iconLabel)
                add(Box.createVerticalStrut(JBUI.scale(12)))
                add(titleLabel)
                add(Box.createVerticalStrut(JBUI.scale(6)))
                add(descLabel)
                add(Box.createVerticalStrut(JBUI.scale(14)))
                add(button)
            }
            add(inner)
        }

    /** VFS-derived state resolved off the EDT, consumed when building the UI on the EDT. */
    private class PreparedContent(
        val registry: SpeqaTagRegistry,
        val tcDir: VirtualFile?,
        val tcFilter: SpeqaTreeFilter,
        val tcCache: TestCaseSummaryCache,
        val tcSpec: TestCaseLeafSpec,
        val tcHasLeaves: Boolean,
        val trDir: VirtualFile?,
        val trFilter: TestRunTreeFilter,
        val trCache: TestRunSummaryCache,
        val trSpec: TestRunLeafSpec,
        val trHasLeaves: Boolean,
        val tcLeavesForCta: Boolean,
    )

    /**
     * Builds one tool-window tab: filter header, tree, and a non-closeable content.
     * All per-tab listeners and tree models are scoped to [contentDisposable] (a per-build
     * child of the tool-window disposable), so a content rebuild releases them cleanly.
     */
    private fun buildTab(
        project: Project,
        contentDisposable: Disposable,
        displayName: String,
        rootDir: VirtualFile?,
        spec: SpeqaLeafSpec,
        emptyText: String,
        invalidateCache: (String) -> Unit,
        filter: SpeqaFilter,
        primary: PrimaryFacet,
        scope: MetadataScope,
        knownTags: () -> Set<String>,
        knownEnvironments: () -> Set<String>,
        stateStore: SpeqaTreeStateStore,
        initialHasLeaves: Boolean,
        leadingActions: (Tree) -> List<AnAction> = { emptyList() },
        ctaComponent: JPanel? = null,
    ): Tab {
        // Whether the tab has any leaf at all (ignoring the filter); drives whether the
        // filter controls are shown. Computed off the EDT by the caller and recomputed on
        // every relevant VFS change.
        val hasLeaves = AtomicBoolean(initialHasLeaves)

        // Card layout for optional empty-state CTA panel (used by the TRs tab).
        val cardLayout: CardLayout?
        val cardPanel: JPanel?
        if (ctaComponent != null) {
            cardLayout = CardLayout()
            cardPanel = JPanel(cardLayout)
        } else {
            cardLayout = null
            cardPanel = null
        }

        val treeModel: StructureTreeModel<SpeqaTreeStructure>?
        val tree: Tree
        if (rootDir != null) {
            val structure = SpeqaTreeStructure(project, rootDir, spec)
            val model = StructureTreeModel(structure, contentDisposable)
            treeModel = model
            // showLoadingNode = false: suppress the platform's transient "loading..." row so
            // the tree shows our centered StatusText "Loading..." (matching the placeholder)
            // instead of a top-left node while children are read from disk.
            val asyncModel = AsyncTreeModel(model, false, contentDisposable)
            tree = viewportWidthTree(project, asyncModel).apply {
                isRootVisible = false
                showsRootHandles = true
                cellRenderer = NodeRenderer()
                // The tree's own empty text is only ever visible when the tree card is shown and
                // the tree is empty under an active filter (loading uses a separate card below).
                this.emptyText.text = emptyText
            }
            // While the first load runs, show a dedicated "Loading..." card rather than the tree,
            // so the tree's empty text never flashes before its rows are inserted on the EDT.
            // The initial has-leaves scan in prepareContent can run against a cold VFS and
            // under-report, so the CTA-vs-tree decision is made only here, once the model has
            // FINISHED its first load (the invalidate future) and the VFS is warm.
            val leafTree = tree
            model.invalidateAsync().whenComplete { _, _ ->
                SwingUtilities.invokeLater {
                    hasLeaves.set(hasAnyLeaf(rootDir, spec))
                    if (cardLayout != null && cardPanel != null) {
                        if (hasLeaves.get()) {
                            // At startup no filter is active, so a non-empty tab WILL have rows.
                            // Reveal the tree only once those rows are in the JTree, so its empty
                            // text never flashes in the gap before the async rows are inserted.
                            revealTreeWhenPopulated(leafTree, cardLayout, cardPanel, attemptsLeft = 100)
                        } else {
                            cardLayout.show(cardPanel, CTA_CARD)
                        }
                    }
                }
            }
            subscribeToVfsChanges(project, contentDisposable, rootDir, invalidateCache, model) {
                hasLeaves.set(hasAnyLeaf(rootDir, spec))
                // Re-run the title actions' update() so the facet visibility follows.
                com.intellij.ide.ActivityTracker.getInstance().inc()
                if (cardLayout != null && cardPanel != null) {
                    SwingUtilities.invokeLater {
                        cardLayout.show(cardPanel, if (hasLeaves.get()) TREE_CARD else CTA_CARD)
                    }
                }
            }
            subscribeToDocumentChanges(contentDisposable, rootDir, spec, invalidateCache, model)
        } else {
            treeModel = null
            tree = viewportWidthTree(project, DefaultTreeModel(DefaultMutableTreeNode())).apply {
                isRootVisible = false
                this.emptyText.text = emptyText
            }
        }
        TreeSpeedSearch.installOn(tree)
        installOpenHandlers(tree)
        if (rootDir != null) {
            installToolWindowPopup(tree, scope)
        }

        val header = SpeqaFilterHeader(
            project = project,
            filter = filter,
            primary = primary,
            metadataScope = scope,
            knownTags = knownTags,
            knownEnvironments = knownEnvironments,
            hasContent = { hasLeaves.get() },
            parentDisposable = contentDisposable,
            onChanged = { treeModel?.invalidateAsync() },
            leadingActions = leadingActions(tree),
        )

        val centerComponent = if (ctaComponent != null && cardLayout != null && cardPanel != null) {
            cardPanel.add(treeScrollPane(tree), TREE_CARD)
            cardPanel.add(ctaComponent, CTA_CARD)
            cardPanel.add(loadingCard(), LOADING_CARD)
            // When the directory exists, start on the "Loading..." card until the async model
            // resolves the CTA-vs-tree decision; a genuinely absent directory shows the CTA
            // immediately, since there is nothing to load.
            cardLayout.show(cardPanel, if (rootDir == null) CTA_CARD else LOADING_CARD)
            cardPanel
        } else {
            treeScrollPane(tree)
        }

        val panel = JPanel(BorderLayout()).apply {
            add(header.component, BorderLayout.NORTH)
            add(centerComponent, BorderLayout.CENTER)
        }

        val content = ContentFactory.getInstance().createContent(panel, displayName, false).apply {
            isCloseable = false
        }

        if (rootDir != null) {
            restoreAndTrackTreeState(contentDisposable, tree, stateStore)
        }
        return Tab(content, header, tree = tree, treeModel = treeModel)
    }

    /**
     * Builds a [Tree] that never grows wider than its viewport (so the scroll pane shows
     * no horizontal scrollbar) and publishes the selected node's file context via
     * [com.intellij.openapi.actionSystem.UiDataProvider], so platform context-menu actions
     * (Rename/Delete/Reveal/Select In) resolve their target from the tree selection. Long
     * node names are clipped and revealed by the platform's expandable-item hover popup.
     */
    private fun viewportWidthTree(project: Project, model: javax.swing.tree.TreeModel): Tree =
        object : Tree(model), com.intellij.openapi.actionSystem.UiDataProvider {
            override fun getScrollableTracksViewportWidth(): Boolean = true

            override fun uiDataSnapshot(sink: com.intellij.openapi.actionSystem.DataSink) {
                val file = selectedNodeFile(this) ?: return
                sink[com.intellij.openapi.actionSystem.CommonDataKeys.VIRTUAL_FILE] = file
                sink[com.intellij.openapi.actionSystem.CommonDataKeys.VIRTUAL_FILE_ARRAY] = arrayOf(file)
                // Reuse the platform's PSI safe-delete (the same provider the Project view uses),
                // so Delete shows the standard confirmation / safe-delete dialog and respects
                // refactoring. It reads PSI_ELEMENT_ARRAY from the data context.
                sink[com.intellij.openapi.actionSystem.PlatformDataKeys.DELETE_ELEMENT_PROVIDER] =
                    com.intellij.ide.util.DeleteHandler.DefaultDeleteProvider()
                // PSI must be resolved lazily: the platform forbids providing a PSI element
                // synchronously on the EDT (it requires a read action). DataSink.lazy runs the
                // block off-EDT only when the data is actually requested (Rename, Delete, Select In).
                sink.lazy(com.intellij.openapi.actionSystem.CommonDataKeys.PSI_ELEMENT) {
                    resolvePsi(project, file)
                }
                sink.lazy(com.intellij.openapi.actionSystem.PlatformCoreDataKeys.PSI_ELEMENT_ARRAY) {
                    resolvePsi(project, file)?.let { arrayOf(it) }
                }
                sink.lazy(com.intellij.openapi.actionSystem.CommonDataKeys.NAVIGATABLE) {
                    resolvePsi(project, file) as? com.intellij.pom.Navigatable
                }
            }
        }.apply {
            isHorizontalAutoScrollingEnabled = false
        }

    /**
     * Switches the card to the tree only once the tree actually has rows, so the tree's empty
     * text never flashes between the "Loading..." card and the first painted rows. Bounded by
     * [attemptsLeft] so it always settles even if rows never arrive (then the tree shows its
     * own empty text, which is the correct outcome for a genuinely empty result).
     */
    private fun revealTreeWhenPopulated(tree: Tree, cardLayout: CardLayout, cardPanel: JPanel, attemptsLeft: Int) {
        if (tree.rowCount > 0 || attemptsLeft <= 0) {
            cardLayout.show(cardPanel, TREE_CARD)
        } else {
            SwingUtilities.invokeLater { revealTreeWhenPopulated(tree, cardLayout, cardPanel, attemptsLeft - 1) }
        }
    }

    /**
     * The "Loading..." card shown while a tab's tree performs its first load. Uses the same
     * StatusText placement as the tree's empty text and the startup placeholder, so the text
     * stays put across the placeholder -> loading card -> tree transitions.
     */
    private fun loadingCard(): JPanel =
        com.intellij.ui.components.JBPanelWithEmptyText().apply {
            emptyText.text = SpeqaBundle.message("toolwindow.speqa.loading")
        }

    /** Wraps [tree] in a scroll pane with the horizontal scrollbar suppressed. */
    private fun treeScrollPane(tree: Tree): javax.swing.JScrollPane =
        ScrollPaneFactory.createScrollPane(tree).apply {
            horizontalScrollBarPolicy = javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
        }

    /**
     * Re-applies the persisted expansion/selection state and keeps the persisted
     * snapshot current. State is captured eagerly on every expand/collapse and
     * selection change (and once more on dispose) rather than only on dispose:
     * on project close the platform may serialize the component before the
     * tool-window disposable runs, so a dispose-only capture would persist a
     * stale snapshot and the tree would not restore.
     */
    private fun restoreAndTrackTreeState(parentDisposable: Disposable, tree: Tree, store: SpeqaTreeStateStore) {
        store.read()?.let { TreeState.createFrom(it).applyTo(tree) }

        val capture = {
            val element = org.jdom.Element("state")
            TreeState.createOn(tree).writeExternal(element)
            store.write(element)
        }
        tree.addTreeExpansionListener(object : javax.swing.event.TreeExpansionListener {
            override fun treeExpanded(event: javax.swing.event.TreeExpansionEvent) = capture()
            override fun treeCollapsed(event: javax.swing.event.TreeExpansionEvent) = capture()
        })
        tree.addTreeSelectionListener { capture() }
        Disposer.register(parentDisposable) { capture() }
    }

    /**
     * Opens the leaf under a double-click, or the selected one on Enter, by
     * calling the node's own [SpeqaLeafNode.navigate]. We do not use
     * `EditSourceOnDoubleClickHandler`/`EditSourceOnEnterKeyHandler`: those resolve
     * the target through `CommonDataKeys.NAVIGATABLE`, which a plain tree over
     * `AbstractTreeNode`s does not publish (only the Project view's pane does), so
     * they would silently open nothing.
     */
    private fun installOpenHandlers(tree: Tree) {
        object : DoubleClickListener() {
            override fun onDoubleClick(event: MouseEvent): Boolean {
                val path = tree.getPathForLocation(event.x, event.y) ?: return false
                val node = TreeUtil.getLastUserObject(SpeqaLeafNode::class.java, path) ?: return false
                node.navigate(true)
                return true
            }
        }.installOn(tree)

        DumbAwareAction.create {
            TreeUtil.getLastUserObject(SpeqaLeafNode::class.java, tree.selectionPath)?.navigate(true)
        }.registerCustomShortcutSet(CommonShortcuts.ENTER, tree)
    }

    private fun subscribeToVfsChanges(
        project: Project,
        parentDisposable: Disposable,
        rootDir: VirtualFile,
        invalidateCache: (String) -> Unit,
        treeModel: StructureTreeModel<SpeqaTreeStructure>,
        afterChange: () -> Unit,
    ) {
        project.messageBus.connect(parentDisposable)
            .subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
                override fun after(events: List<VFileEvent>) {
                    val rootPath = rootDir.path
                    // VFS paths use '/' on all platforms; match the directory itself
                    // or a descendant, never a sibling like "test-cases-old".
                    val descendantPrefix = "$rootPath/"
                    val relevant = events.filter {
                        it.path == rootPath || it.path.startsWith(descendantPrefix)
                    }
                    if (relevant.isEmpty()) return
                    relevant.forEach { invalidateCache(it.path) }
                    treeModel.invalidateAsync()
                    afterChange()
                }
            })
    }

    /**
     * Refreshes a leaf when its in-memory document changes, before the file is saved.
     * Status/result edits go to the open `Document` (not disk), so a VFS-only refresh
     * leaves the leaf icon and label stale until save. We listen on the application-wide
     * document multicaster and act only on documents whose backing file is a leaf under
     * [rootDir]; the listener is scoped to [parentDisposable].
     */
    private fun subscribeToDocumentChanges(
        parentDisposable: Disposable,
        rootDir: VirtualFile,
        spec: SpeqaLeafSpec,
        invalidateCache: (String) -> Unit,
        treeModel: StructureTreeModel<SpeqaTreeStructure>,
    ) {
        val rootPath = rootDir.path
        val descendantPrefix = "$rootPath/"
        EditorFactory.getInstance().eventMulticaster.addDocumentListener(object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                val file = FileDocumentManager.getInstance().getFile(event.document) ?: return
                if (!spec.isLeaf(file.name)) return
                val path = file.path
                if (path != rootPath && !path.startsWith(descendantPrefix)) return
                invalidateCache(path)
                treeModel.invalidateAsync()
            }
        }, parentDisposable)
    }

    private fun projectDirChild(project: Project, name: String): VirtualFile? =
        project.guessProjectDir()
            ?.findChild(name)
            ?.takeIf { it.isDirectory }

    companion object {
        /** Tool-window id; must match the `id` in `plugin.xml`. */
        const val TOOL_WINDOW_ID = "SpeQA"

        private const val TREE_CARD = "tree"
        private const val CTA_CARD = "cta"
        private const val LOADING_CARD = "loading"
    }
}

/** Resolves the PSI file/directory for [file]. Call inside a read action / DataSink.lazy block. */
private fun resolvePsi(project: Project, file: com.intellij.openapi.vfs.VirtualFile): com.intellij.psi.PsiElement? =
    if (file.isDirectory) {
        com.intellij.psi.PsiManager.getInstance(project).findDirectory(file)
    } else {
        com.intellij.psi.PsiManager.getInstance(project).findFile(file)
    }

/** The [VirtualFile] backing the tree's selected leaf or folder node, if valid. */
internal fun selectedNodeFile(tree: com.intellij.ui.treeStructure.Tree): com.intellij.openapi.vfs.VirtualFile? =
    when (val node = com.intellij.util.ui.tree.TreeUtil.getLastUserObject(tree.selectionPath)) {
        is SpeqaLeafNode -> node.value.takeIf { it.isValid }
        is SpeqaFolderNode -> node.value.takeIf { it.isValid && it.isDirectory }
        else -> null
    }
