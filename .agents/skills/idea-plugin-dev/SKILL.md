---
name: idea-plugin-dev
description: Use when registering extensions in plugin.xml, implementing FileEditorProvider/TextEditorWithPreview split editors, creating PersistentStateComponent settings, adding actions to menus, handling VirtualFile/Document/PsiFile, implementing ErrorReportSubmitter, wiring i18n via DynamicBundle, or debugging plugin.xml and sandbox issues in Kotlin + Compose + Jewel IntelliJ plugins.
user-invocable: true
---

# IntelliJ Plugin Development (Kotlin + Compose)

Non-obvious patterns and pitfalls for IntelliJ platform plugins with Compose/Jewel UI. For standard APIs (PersistentStateComponent, DynamicBundle, IconProvider, ToggleAction, ErrorReportSubmitter) see [IntelliJ Platform SDK docs](https://plugins.jetbrains.com/docs/intellij/).

## When to activate

- Implementing split editors with text + Compose preview
- Patching documents without destroying undo history
- Choosing the right action group for menu placement
- Debugging plugin.xml wiring or sandbox issues

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
- `PsiAwareTextEditorProvider` (not `TextEditorProvider`) — preserves Markdown syntax highlighting and PSI features
- `HIDE_OTHER_EDITORS` — prevents duplicate tab switcher when another plugin (e.g., Markdown) also provides a split editor for the same file type
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

Never `document.setText(serialized)` for small changes — it destroys undo history and reformats the entire document. Use targeted `replaceString`:

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

Never nest `runWriteAction` — do file I/O in one block, then patch the document in a separate `invokeLater`.

---

## Action Group IDs

| Group ID | Where it appears |
|----------|-----------------|
| `NewGroup` | File → New menu |
| `ProjectViewPopupMenu` | Right-click in project tree |
| `EditorPopupMenu` | Right-click in editor content |
| `EditorTabPopupMenu` | Right-click on editor tab |
| `EditorTabsEntryPoint` | "..." button on editor tab |

---

## Common Pitfalls

| Pitfall | Fix |
|---------|-----|
| Markdown split editor appears alongside yours | Use `HIDE_OTHER_EDITORS` policy |
| Left editor lacks Markdown highlighting | Use `PsiAwareTextEditorProvider`, not `TextEditorProvider` |
| `setText()` destroys undo history | Use `replaceString()` with targeted edits |
| Action not visible in menu | `VIRTUAL_FILE` may be null — fallback to `FileEditorManager.selectedEditor?.file` |
| Compose panel blank on startup | Defer mount until `HierarchyEvent.DISPLAYABILITY_CHANGED` |
| `runWriteAction` inside `runWriteAction` | Never nest — separate `invokeLater` blocks |
