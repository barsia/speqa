# Links Feature — Implementation Plan

**Date:** 2026-04-10
**Spec:** `docs/specs/2026-04-06-speqa-design.md` (updated with Links feature)

---

## Phase Overview

```
Phase 1: Model + Parser + Serializer  (no UI dependencies)
Phase 2: DocumentRangeLocator + DocumentPatcher  (depends on Phase 1 model)
Phase 3: UI components + Localization  (depends on Phase 1 model)
Phase 4: Integration + Attachment truncation  (depends on Phases 1-3)
Phase 5: Tests  (depends on Phases 1-4)

Parallelism:
- Phase 2 and Phase 3 can run in parallel (both depend only on Phase 1)
- Phase 4 depends on both Phase 2 and Phase 3
- Phase 5 runs last
```

---

## Phase 1: Model + Parser + Serializer

### 1.1 — TestCase.kt (model)

**File:** `src/main/kotlin/io/speqa/speqa/model/TestCase.kt`

**Changes:**
1. Add `data class Link(val title: String, val url: String)` after the `Attachment` data class (line 28).
2. Add `links: List<Link> = emptyList()` to `TestCase` constructor, after `attachments` and before `bodyBlocks` (between current lines 69 and 70).

**Result:**
```kotlin
data class Attachment(val path: String)

data class Link(val title: String, val url: String)

// ...

data class TestCase(
    // ... existing fields ...
    val attachments: List<Attachment> = emptyList(),
    val links: List<Link> = emptyList(),
    val bodyBlocks: List<TestCaseBodyBlock> = emptyList(),
    val steps: List<TestStep> = emptyList(),
)
```

### 1.2 — TestCaseParser.kt

**File:** `src/main/kotlin/io/speqa/speqa/parser/TestCaseParser.kt`

**Changes:**

1. Add import for `Link` model: `import io.github.barsia.speqa.model.Link`

2. Add `LINKS_MARKER` regex constant alongside existing markers:
   ```kotlin
   private val LINKS_MARKER = Regex("""^[Ll]inks:\s*$""")
   ```

3. Add `LINK_PATTERN` regex for parsing `[title](url)`:
   ```kotlin
   private val LINK_PATTERN = Regex("""^\[([^\]]+)\]\(([^)]+)\)$""")
   ```

4. Add `parseLinks(body: String): List<Link>` private method (same pattern as `parseGeneralAttachments`):
   - Scan lines for `LINKS_MARKER`
   - After marker, collect lines matching `LINK_PATTERN` as `Link(title, url)`
   - Stop at `STEPS_MARKER` or end of body
   - Skip blank lines

5. Update `bodyBeforeStepsMarker` (line 106): add `LINKS_MARKER.matches(trimmed)` to the break condition, so link lines are not parsed as body blocks.

6. Update `parseGeneralAttachments` (line 124): add `LINKS_MARKER.matches(trimmed)` as a stop condition (break when we hit the Links section), same as it already does for `STEPS_MARKER`.

7. Update `parse()` return to include `links = parseLinks(body)` (after `attachments`, line 42-43).

### 1.3 — TestCaseSerializer.kt

**File:** `src/main/kotlin/io/speqa/speqa/parser/TestCaseSerializer.kt`

**Changes:**

1. Add import: `import io.github.barsia.speqa.model.Link`

2. Add links serialization block after the attachments block (after line 42) and before the steps block (line 43):
   ```kotlin
   if (testCase.links.isNotEmpty()) {
       appendLine("Links:")
       appendLine()
       testCase.links.forEach { appendLink(it) }
       appendLine()
   }
   ```

3. Add private helper method `appendLink`:
   ```kotlin
   private fun StringBuilder.appendLink(link: Link) {
       appendLine("[${link.title}](${link.url})")
   }
   ```

4. Update the `orderedBlocks.forEachIndexed` trailing condition (line 31): add `|| testCase.links.isNotEmpty()` so body blocks get trailing blank lines when links exist.

---

## Phase 2: DocumentRangeLocator + DocumentPatcher

### 2.1 — DocumentRangeLocator.kt

**File:** `src/main/kotlin/io/speqa/speqa/parser/DocumentRangeLocator.kt`

**Changes:**

1. Add fields to `DocumentLayout` (after `attachmentsBodyRange`, before `stepsMarkerRange`):
   ```kotlin
   val linksMarkerRange: TextRange?,
   val linksBodyRange: TextRange?,
   ```

2. Add `LINKS_MARKER` regex in the companion/private area:
   ```kotlin
   private val LINKS_MARKER = Regex("""^[Ll]inks:\s*$""")
   ```

3. Update the section scanning loop (line 115-120) to recognize `Links:`:
   ```kotlin
   LINKS_MARKER.matches(trimmed) -> sectionStarts += SectionStart("links", i)
   ```

4. Add variables for links ranges (after attachments vars, around line 99):
   ```kotlin
   var linksMarkerRange: TextRange? = null
   var linksBodyRange: TextRange? = null
   ```

5. Add `"links"` case in the section parsing `when` block (after `"attachments"`, line 167-172):
   ```kotlin
   "links" -> {
       linksMarkerRange = TextRange(
           lineStarts[section.lineIdx],
           lineStarts.endOfLine(section.lineIdx, text),
       )
       val bodyRange = findBodyRange(lines, lineStarts, text, section.lineIdx + 1, nextSectionLine)
       linksBodyRange = bodyRange
   }
   ```

6. Update the `DocumentLayout` return (line 184-193) to include `linksMarkerRange` and `linksBodyRange`.

### 2.2 — DocumentPatcher.kt

**File:** `src/main/kotlin/io/speqa/speqa/parser/DocumentPatcher.kt`

**Changes:**

1. Add import: `import io.github.barsia.speqa.model.Link`

2. Add `SetLinks` variant to `PatchOperation` sealed interface (after `SetStepExpectedAttachments`):
   ```kotlin
   data class SetLinks(val links: List<Link>) : PatchOperation
   ```

3. Add dispatch case in `patch()` method's `when` block:
   ```kotlin
   is PatchOperation.SetLinks -> buildSetLinksEdits(normalized, layout, operation.links)
   ```

4. Add `buildSetLinksEdits` private method (modeled after `buildSetAttachmentsEdits`):
   - Section exists + non-empty links: replace `linksBodyRange` with `[title](url)\n` lines
   - Section exists + empty links: delete entire section (marker + body + surrounding blank lines)
   - No section + non-empty links: insert `Links:\n\n[title1](url1)\n...\n\n` after Attachments (or after body blocks) and before Steps
   - No section + empty links: no-op

5. Add `formatDocumentLinks` private method:
   ```kotlin
   private fun formatDocumentLinks(links: List<Link>): String {
       return buildString {
           for (link in links) {
               append("[${link.title}](${link.url})\n")
           }
       }
   }
   ```

6. Add `findLinksInsertOffset` private method:
   ```kotlin
   private fun findLinksInsertOffset(text: String, layout: DocumentLayout): Int {
       // Insert before Steps marker if it exists
       if (layout.stepsMarkerRange != null) {
           return findBlankLinesBefore(text, layout.stepsMarkerRange.start)
       }
       return text.length
   }
   ```
   Note: This inserts after any existing Attachments section because Attachments comes before Links in document order, and we insert before Steps.

---

## Phase 3: UI Components + Localization

### 3.1 — SpeqaBundle.properties (localization)

**File:** `src/main/resources/messages/SpeqaBundle.properties`

**Changes:** Add these entries after the `# --- Attachments ---` section:

```properties
# --- Links ---
label.links=Links
label.noLinks=No links
tooltip.addLink=Add link
tooltip.removeLink=Remove link
tooltip.openLink=Open in browser
dialog.addLink.title=Add Link
dialog.editLink.title=Edit Link
dialog.link.titleField=Title
dialog.link.urlField=URL
dialog.link.urlRequired=URL is required
```

### 3.2 — LinkRow.kt (new file)

**File:** `src/main/kotlin/io/speqa/speqa/editor/ui/LinkRow.kt`

**Pattern:** Closely follows `AttachmentRow.kt` structure.

**Content:**
- `@Composable internal fun LinkRow(link: Link, onClick: () -> Unit, onDelete: () -> Unit, modifier: Modifier = Modifier)`
- Row with:
  - Link icon: `AllIcons.General.Web` (16dp, accent color)
  - Title text: `link.title`, 12sp, accent color, maxLines=1, `TextOverflow.Ellipsis`, `Modifier.weight(1f)`
  - Delete button: `SpeqaIconButton` with `AllIcons.Actions.GC` (same pattern as AttachmentRow)
- Click on row: calls `onClick` (which opens URL in browser)
- Uses `hoverable`, `handOnHover`, `focusTarget`, `pointerInput` for interactions
- `.semantics { role = Role.Button }` on the Row
- Focus border (same as AttachmentRow)
- Keyboard: Enter/Space -> onClick, Tab -> focus next

### 3.3 — LinkList.kt (new file)

**File:** `src/main/kotlin/io/speqa/speqa/editor/ui/LinkList.kt`

**Pattern:** Closely follows `AttachmentList.kt` structure.

**Content:**
- `@Composable internal fun LinkList(links: List<Link>, onLinksChange: (List<Link>) -> Unit, modifier: Modifier = Modifier)`
- Column with `Arrangement.spacedBy(SpeqaLayout.tightGap)`:
  - For each link: `LinkRow(link, onClick = { BrowserUtil.browse(link.url) }, onDelete = { onLinksChange(links - link) })`
  - `AddLinkButton(links, onLinksChange)` at the bottom
- `AddLinkButton`: `SpeqaIconButton` with `AllIcons.General.Web` icon, tooltip from `SpeqaBundle.message("tooltip.addLink")`
  - On click: opens `AddEditLinkDialog` in add mode
  - On dialog OK: appends new `Link` to list via `onLinksChange(links + newLink)`

### 3.4 — AddEditLinkDialog.kt (new file)

**File:** `src/main/kotlin/io/speqa/speqa/editor/ui/AddEditLinkDialog.kt`

**Implementation:** Uses IntelliJ `DialogWrapper` (not Compose) since it's a modal dialog.

**Content:**
- `class AddEditLinkDialog(project: Project?, title: String, initialTitle: String = "", initialUrl: String = "") : DialogWrapper(project)`
- `createCenterPanel()`: JPanel with two labeled text fields (Title, URL) using `com.intellij.ui.dsl.builder.panel`
- `doOKAction()`: validates URL is non-blank, stores result
- `getTitle()` / `getUrl()` accessors for retrieving values after dialog closes
- Title field label: `SpeqaBundle.message("dialog.link.titleField")`
- URL field label: `SpeqaBundle.message("dialog.link.urlField")`
- Dialog title: `SpeqaBundle.message("dialog.addLink.title")` or `SpeqaBundle.message("dialog.editLink.title")`
- If title field is blank on OK, default to URL value
- Companion method: `fun show(project: Project?, editLink: Link? = null): Link?` returns null on cancel

### 3.5 — AttachmentRow.kt (filename middle-truncation)

**File:** `src/main/kotlin/io/speqa/speqa/editor/ui/AttachmentRow.kt`

**Changes:**
- Replace `TextOverflow.Ellipsis` on the filename `Text` (line 99) with a computed display string
- Add a helper function `middleTruncate(name: String, maxChars: Int): String`:
  ```kotlin
  private fun middleTruncate(name: String, maxChars: Int = 40): String {
      if (name.length <= maxChars) return name
      val ext = name.substringAfterLast('.', "")
      val extWithDot = if (ext.isNotEmpty()) ".$ext" else ""
      val availableForName = maxChars - extWithDot.length - 3 // 3 for "..."
      if (availableForName <= 0) return name.take(maxChars - 3) + "..."
      return name.take(availableForName) + "..." + extWithDot
  }
  ```
- Change the `Text` composable to use `middleTruncate(fileName)` as the text value
- Keep `TextOverflow.Ellipsis` as a fallback for extremely narrow widths, but the primary truncation is character-based middle-truncation

---

## Phase 4: Integration

### 4.1 — TestCasePreview.kt (layout change)

**File:** `src/main/kotlin/io/speqa/speqa/editor/ui/TestCasePreview.kt`

**Changes:**

1. Add imports for `Link`, `LinkList`, `BrowserUtil`.

2. Replace the current Attachments section block (lines 189-203) with a side-by-side Row layout:

   **Before (current):**
   ```kotlin
   onPatch?.let { patch ->
       SectionHeaderWithDivider(SpeqaBundle.message("label.attachments"))
       AttachmentList(...)
       StepsSection(...)
   }
   ```

   **After (new):**
   ```kotlin
   onPatch?.let { patch ->
       Row(
           modifier = Modifier.fillMaxWidth(),
           horizontalArrangement = Arrangement.spacedBy(SpeqaLayout.blockGap),
       ) {
           // Left column: Attachments
           Column(
               modifier = Modifier.weight(1f),
               verticalArrangement = Arrangement.spacedBy(SpeqaLayout.tightGap),
           ) {
               SectionLabel(SpeqaBundle.message("label.attachments"))
               AttachmentList(
                   attachments = testCase.attachments,
                   project = project,
                   tcFile = file,
                   onAttachmentsChange = { newAttachments ->
                       patch(testCase.copy(attachments = newAttachments), PatchOperation.SetAttachments(newAttachments))
                   },
                   onOpenFile = { attachment ->
                       AttachmentSupport.resolveFile(file, attachment)?.let { vf ->
                           FileEditorManager.getInstance(project).openFile(vf, true)
                       }
                   },
               )
           }
           // Right column: Links
           Column(
               modifier = Modifier.weight(1f),
               verticalArrangement = Arrangement.spacedBy(SpeqaLayout.tightGap),
           ) {
               SectionLabel(SpeqaBundle.message("label.links"))
               LinkList(
                   links = testCase.links,
                   onLinksChange = { newLinks ->
                       patch(testCase.copy(links = newLinks), PatchOperation.SetLinks(newLinks))
                   },
               )
           }
       }

       StepsSection(...)
   }
   ```

   Note: `SectionHeaderWithDivider` for attachments is replaced with `SectionLabel` since both sections sit side by side and a full-width divider does not make sense in a two-column layout. A single `SurfaceDivider()` before the Row could be added if visual separation from preconditions is needed.

### 4.2 — SpeqaPreviewEditor.kt (drag-and-drop — no changes needed)

The existing `TransferHandler` on `composePanel` handles drag-and-drop for attachments only. No changes needed for links since links are added via the dialog, not drag-and-drop.

### 4.3 — SpeqaPreviewEditor.kt (patchFromPreview — no changes needed)

The existing `patchFromPreview` method handles all `PatchOperation` variants generically via `DocumentPatcher.patch`. The new `SetLinks` operation will work through the same path without any changes to the editor.

---

## Phase 5: Tests

### 5.1 — TestCaseParserTest

**File:** `src/test/kotlin/io/speqa/speqa/parser/TestCaseParserTest.kt`

**New tests:**
- `parse Links section with standard markdown links` — document with `Links:` section containing `[title](url)` lines parses into `testCase.links`
- `parse empty Links section` — `Links:` marker with no content yields empty list
- `Links section stops at Steps marker` — links between `Links:` and `Steps:` are collected; step content is not mixed in
- `bodyBeforeStepsMarker stops at Links marker` — description text does not include link lines
- `Attachments section stops at Links marker` — attachment parsing does not consume link lines

### 5.2 — TestCaseSerializerTest

**File:** `src/test/kotlin/io/speqa/speqa/parser/TestCaseSerializerTest.kt`

**New tests:**
- `serialize Links section` — `TestCase` with links produces `Links:` section with `[title](url)` lines
- `round trip preserves links` — serialize then parse preserves all link titles and URLs
- `Links section appears after Attachments and before Steps` — verify section ordering in serialized output

### 5.3 — DocumentRangeLocatorTest

**File:** `src/test/kotlin/io/speqa/speqa/parser/DocumentRangeLocatorTest.kt`

**New tests:**
- `locate Links section ranges` — document with `Links:` section has non-null `linksMarkerRange` and `linksBodyRange`
- `no Links section yields null ranges` — document without Links has null link ranges
- `Links section between Attachments and Steps` — verify range positions are ordered correctly

### 5.4 — DocumentPatcherLinksTest (new file)

**File:** `src/test/kotlin/io/speqa/speqa/parser/DocumentPatcherLinksTest.kt`

**New tests (same pattern as DocumentPatcherAttachmentTest):**
- `edit existing links` — replace links body with new links
- `add links section to document without one` — inserts Links section
- `remove links section entirely` — deleting all links removes the section
- `add links to document with attachments and steps` — verify correct insertion position
- `remove links from document with attachments and steps` — verify no damage to surrounding sections

---

## Dependency Graph

```
Phase 1 (Model + Parser + Serializer)
  |
  +---> Phase 2 (DocumentRangeLocator + DocumentPatcher)
  |           |
  |           +---> Phase 4 (Integration: TestCasePreview layout)
  |           |
  +---> Phase 3 (UI components + Localization)
              |
              +---> Phase 4 (Integration: TestCasePreview layout)
                          |
                          +---> Phase 5 (Tests)
```

## Verification

After each phase, run:
```bash
./gradlew compileKotlin
```

After Phase 5:
```bash
./gradlew test
```

Final smoke test: open a `.tc.md` file in the IDE sandbox, verify:
1. Links section parses correctly from existing files
2. Adding a link via the dialog writes correct Markdown
3. Clicking a link opens the browser
4. Deleting a link updates the document
5. Attachments and Links display side-by-side
6. Attachment filenames show middle-truncation
7. Light and dark themes render correctly

---

## Files Summary

| File | Action | Phase |
|------|--------|-------|
| `model/TestCase.kt` | Edit: add `Link` class + `links` field | 1 |
| `parser/TestCaseParser.kt` | Edit: add `parseLinks`, update stop conditions | 1 |
| `parser/TestCaseSerializer.kt` | Edit: add Links section serialization | 1 |
| `parser/DocumentRangeLocator.kt` | Edit: add links ranges to layout | 2 |
| `parser/DocumentPatcher.kt` | Edit: add `SetLinks` operation | 2 |
| `messages/SpeqaBundle.properties` | Edit: add link-related strings | 3 |
| `editor/ui/LinkRow.kt` | **New file** | 3 |
| `editor/ui/LinkList.kt` | **New file** | 3 |
| `editor/ui/AddEditLinkDialog.kt` | **New file** | 3 |
| `editor/ui/AttachmentRow.kt` | Edit: middle-truncation for filenames | 3 |
| `editor/ui/TestCasePreview.kt` | Edit: side-by-side Attachments + Links layout | 4 |
| `parser/TestCaseParserTest.kt` | Edit: add link parsing tests | 5 |
| `parser/TestCaseSerializerTest.kt` | Edit: add link serialization tests | 5 |
| `parser/DocumentRangeLocatorTest.kt` | Edit: add link range tests | 5 |
| `parser/DocumentPatcherLinksTest.kt` | **New file**: link patching tests | 5 |
