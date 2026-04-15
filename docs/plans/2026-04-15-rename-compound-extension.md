# Compound Extension Rename Selection Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** When renaming `.tc.md` / `.tr.md` files via Refactor → Rename, only the base name (before `.tc.md`/`.tr.md`) is pre-selected in the dialog — not everything before `.md`.

**Architecture:** Custom `RenamePsiElementProcessor` intercepts rename for Speqa files and overrides `createRenameDialog()` to return a `RenameDialog` subclass. The subclass calls `preselectExtension(0, baseName.length)` to select only the base name. A companion `RenameInputValidator` prevents accidental removal of the compound extension.

**Tech Stack:** IntelliJ Platform SDK (RenamePsiElementProcessor, RenameDialog, RenameInputValidator)

---

## File Structure

| Action | Path | Responsibility |
|--------|------|---------------|
| Create | `src/main/kotlin/io/github/barsia/speqa/refactoring/SpeqaRenamePsiFileProcessor.kt` | Routes Speqa files to custom dialog |
| Create | `src/main/kotlin/io/github/barsia/speqa/refactoring/SpeqaRenameDialog.kt` | Controls selection range in rename field |
| Create | `src/main/kotlin/io/github/barsia/speqa/refactoring/SpeqaRenameInputValidator.kt` | Prevents removing compound extension |
| Modify | `src/main/resources/META-INF/plugin.xml` | Register 2 extension points |
| Modify | `src/main/resources/messages/SpeqaBundle.properties` | Add validation error message |
| Modify | `src/main/kotlin/io/github/barsia/speqa/model/SpeqaDefaults.kt` | Add `speqaExtension()` utility |
| Modify | `docs/specs/2026-04-06-speqa-design.md` | Document rename behavior |

---

### Task 1: Add `speqaExtension()` utility to SpeqaDefaults

**Files:**
- Modify: `src/main/kotlin/io/github/barsia/speqa/model/SpeqaDefaults.kt`

Both the processor and the validator need to extract the compound extension from a filename. Add a single utility to `SpeqaDefaults`.

- [ ] **Step 1: Update spec**

Add to the spec section that describes `SpeqaDefaults` a note about the new utility function.

- [ ] **Step 2: Add utility function**

In `SpeqaDefaults.kt`, add:

```kotlin
/** Returns "tc.md" or "tr.md" if the filename ends with a Speqa compound extension, null otherwise. */
fun speqaExtension(fileName: String): String? = when {
    fileName.endsWith(".$TEST_CASE_EXTENSION") -> TEST_CASE_EXTENSION
    fileName.endsWith(".$TEST_RUN_EXTENSION") -> TEST_RUN_EXTENSION
    else -> null
}

/** Returns the base name without the Speqa compound extension, or null if not a Speqa file. */
fun speqaStem(fileName: String): String? {
    val ext = speqaExtension(fileName) ?: return null
    return fileName.removeSuffix(".$ext")
}
```

- [ ] **Step 3: Verify compilation**

Run: `./gradlew compileKotlin 2>&1 | grep -E "^e:|BUILD"`
Expected: `BUILD SUCCESSFUL`

---

### Task 2: Create `SpeqaRenamePsiFileProcessor`

**Files:**
- Create: `src/main/kotlin/io/github/barsia/speqa/refactoring/SpeqaRenamePsiFileProcessor.kt`

This processor tells IntelliJ to use our custom dialog for `.tc.md`/`.tr.md` files.

- [ ] **Step 1: Update spec**

Document the rename processor in the spec: it intercepts rename for Speqa files and delegates to `SpeqaRenameDialog`.

- [ ] **Step 2: Create the processor**

```kotlin
package io.github.barsia.speqa.refactoring

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.refactoring.rename.RenameDialog
import com.intellij.refactoring.rename.RenamePsiElementProcessor
import io.github.barsia.speqa.model.SpeqaDefaults

class SpeqaRenamePsiFileProcessor : RenamePsiElementProcessor() {

    override fun canProcessElement(element: PsiElement): Boolean {
        val file = (element as? PsiFile)?.virtualFile ?: return false
        return SpeqaDefaults.speqaExtension(file.name) != null
    }

    override fun createRenameDialog(
        project: Project,
        element: PsiElement,
        nameSuggestionContext: PsiElement?,
        editor: Editor?,
    ): RenameDialog {
        return SpeqaRenameDialog(project, element, nameSuggestionContext, editor)
    }
}
```

- [ ] **Step 3: Verify compilation**

Run: `./gradlew compileKotlin 2>&1 | grep -E "^e:|BUILD"`
Expected: Will fail — `SpeqaRenameDialog` doesn't exist yet. That's expected; proceed to Task 3.

---

### Task 3: Create `SpeqaRenameDialog`

**Files:**
- Create: `src/main/kotlin/io/github/barsia/speqa/refactoring/SpeqaRenameDialog.kt`

The dialog subclass overrides selection to cover only the base name (before `.tc.md`/`.tr.md`). The key is `preselectExtension(startOffset, endOffset)` — a protected method in `RenameDialog` that calls `NameSuggestionsField.select()`.

- [ ] **Step 1: Create the dialog**

```kotlin
package io.github.barsia.speqa.refactoring

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.refactoring.rename.RenameDialog
import io.github.barsia.speqa.model.SpeqaDefaults
import javax.swing.JComponent

class SpeqaRenameDialog(
    project: Project,
    element: PsiElement,
    nameSuggestionContext: PsiElement?,
    editor: Editor?,
) : RenameDialog(project, element, nameSuggestionContext, editor) {

    override fun createCenterPanel(): JComponent? {
        val panel = super.createCenterPanel()
        val fileName = (psiElement as? PsiFile)?.name ?: return panel
        val stem = SpeqaDefaults.speqaStem(fileName) ?: return panel
        preselectExtension(0, stem.length)
        return panel
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew compileKotlin 2>&1 | grep -E "^e:|BUILD"`
Expected: `BUILD SUCCESSFUL`

---

### Task 4: Create `SpeqaRenameInputValidator`

**Files:**
- Create: `src/main/kotlin/io/github/barsia/speqa/refactoring/SpeqaRenameInputValidator.kt`
- Modify: `src/main/resources/messages/SpeqaBundle.properties`

Prevents the user from accidentally changing or removing the `.tc.md`/`.tr.md` extension during rename.

- [ ] **Step 1: Add bundle string**

In `SpeqaBundle.properties`, add:

```properties
# --- Rename ---
rename.error.extensionChanged=File must keep the .{0} extension
```

- [ ] **Step 2: Create the validator**

```kotlin
package io.github.barsia.speqa.refactoring

import com.intellij.openapi.project.Project
import com.intellij.patterns.ElementPattern
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.refactoring.rename.RenameInputValidatorEx
import com.intellij.util.ProcessingContext
import io.github.barsia.speqa.model.SpeqaDefaults
import java.util.ResourceBundle

class SpeqaRenameInputValidator : RenameInputValidatorEx {

    override fun getPattern(): ElementPattern<out PsiElement> =
        PlatformPatterns.psiElement(PsiFile::class.java)

    override fun isInputValid(newName: String, element: PsiElement, context: ProcessingContext): Boolean {
        val file = (element as? PsiFile)?.virtualFile ?: return true
        val ext = SpeqaDefaults.speqaExtension(file.name) ?: return true
        return newName.endsWith(".$ext")
    }

    override fun getErrorMessage(newName: String, project: Project): String? {
        // This is called without element context, so we check both extensions
        if (newName.endsWith(".${SpeqaDefaults.TEST_CASE_EXTENSION}")) return null
        if (newName.endsWith(".${SpeqaDefaults.TEST_RUN_EXTENSION}")) return null
        val bundle = ResourceBundle.getBundle("messages.SpeqaBundle")
        // Show whichever extension was likely intended
        val ext = if (newName.contains(".tc")) SpeqaDefaults.TEST_CASE_EXTENSION
                  else SpeqaDefaults.TEST_RUN_EXTENSION
        return bundle.getString("rename.error.extensionChanged").replace("{0}", ext)
    }
}
```

- [ ] **Step 3: Verify compilation**

Run: `./gradlew compileKotlin 2>&1 | grep -E "^e:|BUILD"`
Expected: `BUILD SUCCESSFUL`

---

### Task 5: Register extensions in plugin.xml

**Files:**
- Modify: `src/main/resources/META-INF/plugin.xml`

- [ ] **Step 1: Add extensions**

In the `<extensions defaultExtensionNs="com.intellij">` block, add after the existing `psi.referenceContributor`:

```xml
<renamePsiElementProcessor
    implementation="io.github.barsia.speqa.refactoring.SpeqaRenamePsiFileProcessor"/>
<renameInputValidator
    implementation="io.github.barsia.speqa.refactoring.SpeqaRenameInputValidator"/>
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew compileKotlin 2>&1 | grep -E "^e:|BUILD"`
Expected: `BUILD SUCCESSFUL`

---

### Task 6: Manual smoke test

- [ ] **Step 1: Run the plugin sandbox**

Run: `./gradlew runIde`

- [ ] **Step 2: Verify rename selection**

1. Create or open a `.tc.md` file (e.g. `login-flow.tc.md`)
2. Right-click → Refactor → Rename (or Shift+F6)
3. Verify: only `login-flow` is selected, not `login-flow.tc`
4. Type a new name → verify the `.tc.md` extension stays
5. Try to remove the extension → verify the error message appears

- [ ] **Step 3: Verify same for `.tr.md`**

Repeat step 2 with a `.tr.md` file.

- [ ] **Step 4: Verify native refactoring still works**

1. Rename a `.tc.md` file that has an attachments folder
2. Verify the attachment folder is renamed (existing `AttachmentRefactoringListener` behavior)
3. Verify Undo (Cmd+Z) reverts the rename

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/io/github/barsia/speqa/refactoring/ \
       src/main/kotlin/io/github/barsia/speqa/model/SpeqaDefaults.kt \
       src/main/resources/META-INF/plugin.xml \
       src/main/resources/messages/SpeqaBundle.properties \
       docs/specs/2026-04-06-speqa-design.md
git commit -m "feat: compound extension rename — select only base name in Rename dialog"
```
