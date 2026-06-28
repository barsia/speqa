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
