---
name: idea-plugin-dev
description: Use when registering extensions in plugin.xml, implementing FileEditorProvider/TextEditorWithPreview split editors, creating PersistentStateComponent settings, adding actions to menus, handling VirtualFile/Document/PsiFile, implementing ErrorReportSubmitter, wiring i18n via DynamicBundle, building dialogs with Kotlin UI DSL v2, using JB platform components, or debugging plugin.xml and sandbox issues in Kotlin + Swing IntelliJ plugins.
user-invocable: true
---

# IntelliJ Plugin Development (Kotlin + Swing)

Non-obvious patterns and pitfalls for IntelliJ platform plugins. For standard APIs (PersistentStateComponent, DynamicBundle, IconProvider, ToggleAction, ErrorReportSubmitter) see [IntelliJ Platform SDK docs](https://plugins.jetbrains.com/docs/intellij/).

---

## Split Editor (TextEditorWithPreview)

### EditorProvider

```kotlin
class SpeqaEditorProvider : FileEditorProvider, DumbAware {
    override fun accept(project: Project, file: VirtualFile): Boolean {
        return file.name.endsWith(".tc.md")
    }

    override fun createEditor(project: Project, file: VirtualFile): FileEditor {
        val document = FileDocumentManager.getInstance().getDocument(file)
            ?: return PsiAwareTextEditorProvider().createEditor(project, file)
        val textEditor = PsiAwareTextEditorProvider().createEditor(project, file) as TextEditor
        return SpeqaSplitEditor(textEditor, SpeqaPreviewEditor(project, file, document, textEditor.editor))
    }

    override fun getEditorTypeId(): String = "speqa-editor"
    override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_OTHER_EDITORS
}
```

Key decisions:
- `PsiAwareTextEditorProvider` (not `TextEditorProvider`): preserves Markdown syntax highlighting and PSI features
- `HIDE_OTHER_EDITORS`: prevents duplicate tab switcher when another plugin (e.g., Markdown) also provides a split editor for the same file type
- Pass `textEditor.editor` to preview for scroll sync wiring

### Preview editor with debounced document sync

```kotlin
class SpeqaPreviewEditor(...) : UserDataHolderBase(), FileEditor, Disposable {
    private var parsed by mutableStateOf(parse(document.text))
    private var suppressDocumentRefresh = false

    private val documentListener = object : DocumentListener {
        override fun documentChanged(event: DocumentEvent) {
            if (!suppressDocumentRefresh) refreshTimer.restart()
        }
    }

    private val refreshTimer = Timer(300) {
        parsed = parse(document.text)
    }.apply { isRepeats = false }

    init { document.addDocumentListener(documentListener, this) }
}
```

---

## Document Patching (preserving undo history)

Never `document.setText(serialized)` for small changes: it destroys undo history and reformats the entire document. Use targeted `replaceString`:

```kotlin
suppressDocumentRefresh = true
ApplicationManager.getApplication().invokeLater {
    try {
        CommandProcessor.getInstance().executeCommand(project, {
            runWriteAction {
                val edits = DocumentPatcher.patch(document.text, operation)
                for (edit in edits) {
                    document.replaceString(edit.offset, edit.offset + edit.length, edit.replacement)
                }
            }
        }, commandName, null)
    } finally {
        suppressDocumentRefresh = false
    }
}
```

Never nest `runWriteAction`: do file I/O in one block, then patch the document in a separate `invokeLater`.

---

## Action Group IDs

| Group ID               | Where it appears              |
|------------------------|-------------------------------|
| `NewGroup`             | File -> New menu              |
| `ProjectViewPopupMenu` | Right-click in project tree   |
| `EditorPopupMenu`      | Right-click in editor content |
| `EditorTabPopupMenu`   | Right-click on editor tab     |
| `EditorTabsEntryPoint` | "..." button on editor tab    |

---

## Kotlin UI DSL v2

Use Kotlin UI DSL v2 for dialogs, settings pages, forms, and any layout composed of standard components. It produces correct spacing, label alignment, HiDPI scaling, and accessibility automatically. Only fall back to manual Swing for custom components (canvas, rich list renderer, custom painting) that the DSL cannot express.

Top-level builder is `panel { }` (returns `DialogPanel`). Structure: `panel -> row -> cells`. Lives in `com.intellij.ui.dsl.builder`.

### Components reference

All cell factory methods available inside `row { }`:

| Method                                 | Description                                               |
|----------------------------------------|-----------------------------------------------------------|
| `checkBox("text")`                     | Checkbox                                                  |
| `radioButton("text", value)`           | Radio button, must be inside `buttonsGroup {}`            |
| `button("text") {}`                    | Push button                                               |
| `actionButton(action)`                 | Icon button bound to an `AnAction`                        |
| `segmentedButton(items) { text = it }` | Segmented control                                         |
| `label("text")`                        | Static label                                              |
| `text("html")`                         | Rich text with links, icons                               |
| `link("text") {}`                      | Focusable clickable link                                  |
| `browserLink("text", "url")`           | Opens URL in browser                                      |
| `icon(AllIcons.*)`                     | Icon display                                              |
| `contextHelp("description", "title")`  | Help icon with popup                                      |
| `textField()`                          | Text input                                                |
| `passwordField()`                      | Password input                                            |
| `textFieldWithBrowseButton()`          | Text field + browse dialog                                |
| `expandableTextField()`                | Expandable multi-line text field                          |
| `intTextField(range)`                  | Integer input with validation                             |
| `spinner(intRange)`                    | Numeric spinner                                           |
| `textArea()`                           | Multi-line text, use `.rows(n)` and `.align(AlignX.FILL)` |
| `comboBox(items)`                      | Combo box / dropdown                                      |
| `comment("text")`                      | Gray comment text                                         |
| `cell(component)`                      | Wrap any arbitrary Swing component                        |
| `scrollCell(component)`                | Wrap component in a scroll pane                           |

### Binding

| Method                                       | Component    |
|----------------------------------------------|--------------|
| `bindSelected(model::prop)`                  | checkBox     |
| `bindText(model::prop)`                      | textField    |
| `bindIntText(model::prop)`                   | intTextField |
| `bindItem(model::prop.toNullableProperty())` | comboBox     |
| `bindValue(model::prop)`                     | slider       |
| `buttonsGroup {}.bind(model::prop)`          | radio group  |

Values applied on `DialogPanel.apply()`, checked with `.isModified()`, reverted with `.reset()`.

### Validation

```kotlin
panel {
    row("Username:") {
        textField()
            .columns(COLUMNS_MEDIUM)
            .cellValidation {
                addInputRule("Must not be empty") { it.text.isBlank() }
            }
    }
}
```

Activate with `dialogPanel.registerValidators(disposable)` after creating the panel.

### Groups, spacing, visibility

```kotlin
panel {
    group("Settings") {
        row("Name:") { textField() }
    }
    collapsibleGroup("Advanced") {
        row("Timeout:") { intTextField(0..1000) }
    }
    separator()
    indent {
        row { checkBox("Option") }
    }.enabledIf(someCheckbox.selected)
}
```

### Spacing constants

| Constant                             | Unscaled px | Usage                            |
|--------------------------------------|-------------|----------------------------------|
| `RightGap.SMALL`                     | 6           | Related inline components        |
| `RightGap.COLUMNS`                   | 60          | Logical column separation        |
| `TopGap.SMALL` / `BottomGap.SMALL`   | 8           | Minor section separation         |
| `TopGap.MEDIUM` / `BottomGap.MEDIUM` | 20          | Major section / group separation |

### Tips

| Pattern                    | Usage                                       |
|----------------------------|---------------------------------------------|
| `.bold()`                  | Bold text on any cell                       |
| `.columns(COLUMNS_MEDIUM)` | Set preferred width of textField / comboBox |
| `.resizableColumn()`       | Column fills remaining horizontal space     |
| `.align(AlignX.FILL)`      | Stretch to fill available width             |
| `.applyToComponent { }`    | Direct access to underlying Swing component |
| `.widthGroup("name")`      | Equalize widths across rows                 |

---

## Dialogs (DialogWrapper)

```kotlin
class MyDialog(project: Project) : DialogWrapper(project) {
    init { init() }

    override fun createCenterPanel(): JComponent = panel {
        row("Name:") { textField().bindText(model::name) }
    }

    override fun getPreferredFocusedComponent() = /* first field */
    override fun getDimensionServiceKey() = "MyDialog"  // persists size
}
```

Show with `showAndGet()` for modal boolean, or `show()` + `getExitCode()`.
For validation: call `initValidation()` in constructor, override `doValidate()` returning `null` (valid) or `ValidationInfo(message, component)`.

---

## Swing State Management

Swing is retained-mode: build a stable component tree once, then mutate existing components via `update(model)`. Do not use React-style `state -> render()` loops.

**Avoid:**
```kotlin
fun render() {
    root.removeAll()
    if (open) root.add(JBScrollPane(JBTextArea(text)))
    revalidate(); repaint()
}
```

**Prefer:**
```kotlin
fun update(model: MyModel) {
    if (titleLabel.text != model.title) titleLabel.text = model.title
    // compare before assigning; repaint only when something changed
}

fun isExpanded(): Boolean = scroll?.parent === root  // derive state from containment

fun toggle() {
    val changed = if (isExpanded()) collapse() else expand()
    if (!changed) return
    syncArrow()
    revalidate(); repaint()
}

private fun scroll(): JBScrollPane {  // lazy-create expensive components
    scroll?.let { return it }
    return JBScrollPane(body()).also { scroll = it }
}
```

Rules:
- Derive expanded state from containment (`scroll.parent === root`), not a boolean flag
- Lazy-create expensive bodies (`JBTextArea`, `JBScrollPane`, markdown panes) on first expansion
- Empty deltas, identical text, repeated hover values: no repaint
- Name helpers `syncBody()`, `syncArrow()`, `applyModel()` -- not `render()`
- All non-EDT UI updates route through `SwingUtilities.invokeLater` with `revalidate()` + `repaint()`

---

## Platform Components

Always use JB* equivalents instead of raw Swing:

| Instead of            | Use                    |
|-----------------------|------------------------|
| `JLabel`              | `JBLabel`              |
| `JTextField`          | `JBTextField`          |
| `JTextArea`           | `JBTextArea`           |
| `JList`               | `JBList`               |
| `JScrollPane`         | `JBScrollPane`         |
| `JTable`              | `JBTable`              |
| `JTree`               | `Tree`                 |
| `JSplitPane`          | `JBSplitter`           |
| `JCheckBox`           | `JBCheckBox`           |
| `EmptyBorder`         | `JBUI.Borders.empty()` |
| Hardcoded pixel sizes | `JBUI.scale(px)`       |

For rich list/tree renderers: `ColoredListCellRenderer`, `ColoredTreeCellRenderer`, `SimpleTextAttributes` for text styles.

---

## Platform Spacing (Manual Swing)

| Need                      | API                                                      |
|---------------------------|----------------------------------------------------------|
| Empty padding             | `JBUI.Borders.empty(...)`                                |
| Insets                    | `JBUI.insets(...)`, `JBUI.insetsTop(...)` etc.           |
| Dimensions                | `JBUI.size(...)`, `JBDimension`, `JBUI.scale(...)`       |
| Side separators           | `JBUI.Borders.customLineTop/Bottom/Left/Right(...)`      |
| Compound borders          | `JBUI.Borders.compound(...)`                             |
| Simple BorderLayout panel | `JBUI.Panels.simplePanel(...)`, `BorderLayoutPanel`      |
| Row height                | `JBUI.CurrentTheme.List.rowHeight()`, `Tree.rowHeight()` |

---

## Theme-Derived Colors

SpeQA uses `SpeqaThemeColors` as the central source -- prefer it over direct API calls. When `SpeqaThemeColors` does not cover a need, use these:

| Need                    | API                                                                                 |
|-------------------------|-------------------------------------------------------------------------------------|
| Label text              | `UIUtil.getLabelForeground()`                                                       |
| Secondary/help text     | `UIUtil.getContextHelpForeground()`                                                 |
| Error text              | `UIUtil.getErrorForeground()`                                                       |
| Border/bounds           | `JBColor.border()`, `NamedColorUtil.getBoundsColor()`                               |
| Named theme key         | `JBColor.namedColor("Key.Name", fallback)`                                          |
| Runtime-evaluated color | `JBColor.lazy { EditorColorsManager.getInstance().globalScheme.defaultBackground }` |
| Links                   | `JBUI.CurrentTheme.Link.Foreground.ENABLED / HOVERED / PRESSED`                     |

Never hardcode `Color(0xFF, ...)` in UI code.

---

## Common Pitfalls

| Pitfall                                       | Fix                                                                              |
|-----------------------------------------------|----------------------------------------------------------------------------------|
| Markdown split editor appears alongside yours | Use `HIDE_OTHER_EDITORS` policy                                                  |
| Left editor lacks Markdown highlighting       | Use `PsiAwareTextEditorProvider`, not `TextEditorProvider`                       |
| `setText()` destroys undo history             | Use `replaceString()` with targeted edits                                        |
| Action not visible in menu                    | `VIRTUAL_FILE` may be null, fallback to `FileEditorManager.selectedEditor?.file` |
| `runWriteAction` inside `runWriteAction`      | Never nest, use separate `invokeLater` blocks                                    |
| Panel flickers on model update                | Use `update(model)` mutation pattern, not `removeAll()`/rebuild                  |
| Expensive component created eagerly           | Lazy-create on first expansion/access                                            |

---

## Project-Wide ID / Registry via FileBasedIndex

For a project-wide registry (unique ids, tags, cross-file lookup), back it with a `FileBasedIndex`, not an ad-hoc scan. Lessons that bite:

- **Filter by filename, not file type.** `getInputFilter()` should match `file.name.endsWith(".tc.md")`, not a `FileType` - a compound extension (`.tc.md`) is plain Markdown to the platform, so a type filter mis-matches. Key entries by your own prefix (`"TC:<id>"` / `"TR:<id>"`).
- **`getContainingFiles` is an over-approximation.** Re-verify each candidate against its *current* parsed content before trusting it; the index can return stale or extra files (`candidates.filter { it.isValid && currentId(it) == id }`).
- **Guard against dumb mode.** During indexing the index is unavailable; return empty / "not duplicate" rather than throwing, so the daemon stays calm.
- **Scope includes test sources.** `GlobalSearchScope.projectScope(project)` also scans `src/test/resources`. When you dogfood the plugin on its own repo, test fixtures (`.tc.md` data) share the id space with real cases - plan ids/scoping so fixtures don't collide.
- The inline annotator and the batch resolver MUST share one query path - if the warning counts files the resolver's preview doesn't list, you get an unresolvable "phantom" duplicate.

## Bundling Templates into the Jar (wizard starters, skills)

To ship a template the New Project wizard installs into user projects:

- Bundle with `processResources { from("path/in/repo") { into("templates/...") } }`, read it at runtime via `getResourceAsStream("/templates/...")`.
- **Do not use Gradle `rename {}`** - it is flaky with the IntelliJ test classpath (a clean build's test can't find the renamed resource; a rerun finds it). Bundle the file under its final name.
- If the bundled file is a type your own plugin would index in this repo (e.g. a `.tc.md` starter that would collide with real ids), store the source with a non-indexed suffix (`starter.tc.md.template`), bundle it unchanged, and have the scaffold strip `.template` when writing it into the user's project - it ships as a real `.tc.md` yet never pollutes your own index. Guard the whole chain with a classpath test (see idea-plugin-testing).

## Release Gate: verifyPlugin and internal API

`./gradlew verifyPlugin` runs the JetBrains Plugin Verifier against recommended IDEs. Treat its findings by class:

- **Internal (non-public) API usage is a hard release blocker.** The verifier flags every `@ApiStatus.Internal` reference; replace each with stable/public API before tagging - reaching for internal symbols is the most common way to break on a future IDE.
- **Experimental-API overrides/usages are warnings, not blockers** (e.g. overriding `ToolWindowFactory.manage`/`getAnchor`). Acceptable, but minimize them.
- Environment failures (no disk, can't download the verifier IDE) are not plugin defects; fix the environment or fall back to `compileKotlin compileTestKotlin test` and note that `verifyPlugin` could not run.

---

> The sections below distil patterns from the local `codocation` plugin (`intellij-community/plugins/codocation/src/main/kotlin/com/codocation/plugin/...`) - open the cited files for the canonical implementation.

## Keep Work Off the EDT (threading)

Freezing the IDE is the most common plugin sin.

- **Derive heavy state once, recompute only on real change.** Cache behind `CachedValuesManager.createCachedValue { CachedValueProvider.Result.create(build(), tracker) }` with a `SimpleModificationTracker`, and bump the tracker from BOTH a `VFS_CHANGES` `BulkFileListener` AND `EditorFactory.getEventMulticaster().addDocumentListener(...)` - the document listener is what makes *unsaved* edits invalidate. Wrap the accessor in `ReadAction.compute {}` since EDT callers (widgets, annotators) hold no read action. (`validate/ProjectContentProvider.kt`)
- **Never compute in a paint/getter.** A status-bar widget returns its last `@Volatile` value instantly and kicks `ReadAction.nonBlocking { heavy }.expireWith(this).coalesceBy(this).finishOnUiThread(ModalityState.any()) { applyOnEdt }.submit(AppExecutorUtil.getAppExecutorService())`. An `AtomicBoolean` guard collapses overlapping requests; **capture UI-thread state (selected file, caret) BEFORE** entering the background action; `coalesceBy` drops superseded recomputes. (`status/DiagnosticsStatusBarWidget.kt`, `toc/TocEditorPanel.kt`)
- **Whole-file/project validation without blocking typing:** an `ExternalAnnotator` splits into `collectInformation` (EDT: grab `document.text` + path, filter early), `doAnnotate` (background: run a pure validator off the cached snapshot), `apply` (map to `holder.newAnnotation(...).range(...).withFix(...)`). One shared snapshot feeds many annotators. (`validate/MarkdownDocAnnotator.kt`)
- **Long one-shots** (build/export) run under `ProgressManager.run(Task.Backgroundable)`; files written outside the Document layer need `LocalFileSystem.getInstance().refreshAndFindFileByNioFile(dir)?.refresh(true, true)` to appear. (`build/BuildSiteAction.kt`)
- A legitimate user-initiated EDT action that must touch the filesystem wraps it in `SlowOperations.allowSlowOperations { … }` instead of tripping the slow-op assertion.

## Editing the Document Well (beyond replaceString)

- Wrap writes in `CommandProcessor.executeCommand(project, { runWriteAction { … } }, name, groupId, UndoConfirmationPolicy.DEFAULT, document)`. A shared **`groupId`** merges related edits into ONE undo; passing the `document` ties undo to that file.
- Apply multiple edits **sorted descending by offset** so earlier offsets stay valid as you mutate.
- **Suppress your own echo:** disable the panel's document listener while writing, or the plugin rebuilds its UI from the change it just made. (`toc/TocWriteBack.kt`)

## Service & State Architecture

- A `@Service(Service.Level.PROJECT)` holding an `AtomicReference<State>` (immutable data class) swapped atomically on refresh is a clean single source of truth. (`project/CodocationProjectService.kt`)
- **Match a config file by `VirtualFile` identity, not `path.endsWith("config.yml")`** - the suffix also matches `my-config.yml` and nested files; keep a path fallback only for create/delete when the cached VF is stale.
- Notify other components via a custom `Topic` + `project.messageBus.syncPublisher(TOPIC)` on state change, so they react without re-parsing.
- For settings, prefer `SimplePersistentStateComponent<MyState : BaseState>` with `by property()` / `by string()` delegates over hand-rolling `PersistentStateComponent`. (`settings/CodocationSettings.kt`)
- Store tokens/passwords in `PasswordSafe` via `CredentialAttributes(generateServiceName("My Plugin", key))` - never in the project tree or settings. (`deploy/CodocationCredentials.kt`)

## More Extension Points Worth Knowing

- **Schema-backed plain YAML/JSON** without a custom language: a `JsonSchemaProviderFactory` whose provider serves a bundled draft-07 schema (`SchemaType.embeddedSchema`) gated by a filename matcher gives completion + validation on `*.yml`. Needs `<depends>com.intellij.modules.json</depends>`. (`schema/CodocationSchemaProviderFactory.kt`)
- **Gutter icons:** a `LineMarkerProvider` returns `null` from `getLineMarkerInfo` and does its scanning in `collectSlowLineMarkers` (background), anchoring each marker to the smallest *leaf* PSI element (`firstChild == null`), never a composite. (`gutter/VariableUsageLineMarkerProvider.kt`)
- **JCEF local assets:** serve preview files through a custom `my-asset://` scheme (registered once via an `AppLifecycleListener` + `CefAppHandlerAdapter`, with a canonical-path traversal guard) rather than `file://`, which JCEF blocks for relative loads. (`editor/CodocationAssetScheme*.kt`)

## More Pitfalls

| Pitfall | Fix |
|---------|-----|
| Icon referenced from `plugin.xml` `icon="…"` throws "Icon cannot be found" | The Kotlin `val` must be `@JvmField` - a plain val compiles to a getter the reflective loader can't resolve |
| Config-file listener also fires for `my-<name>` / nested files | Match by `VirtualFile` identity, not `path.endsWith(...)` |
| "Slow operations are prohibited on EDT" on a user one-shot | Wrap in `SlowOperations.allowSlowOperations { … }` |
| Files written outside the Document layer don't appear | `LocalFileSystem…refreshAndFindFileByNioFile(dir)?.refresh(true, true)` |
| `CachedValue` doesn't refresh on unsaved edits | Bump the `ModificationTracker` from a Document listener too, not only VFS |
