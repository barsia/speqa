# Rename SpeQA → SpeQA Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rename the plugin from SpeQA to SpeQA across all code, resources, docs, and build files.

**Architecture:** Single scripted batch rename using `find`+`sed` for content, `mv` for directories/files. No logic changes. Pre-edit hook temporarily bypassed by touching spec marker before the batch.

**Tech Stack:** Bash (sed, find, mv), Kotlin compilation verification

---

### Task 1: Batch rename all content and files

- [ ] **Step 1: Edit spec first (satisfy pre-edit hook)**

Add a line to `docs/specs/2026-04-06-speqa-mvp-design.md` documenting the rename.

- [ ] **Step 2: Run the rename script**

```bash
cd "$(git rev-parse --show-toplevel)"

# 1. Move source directories
mkdir -p src/main/kotlin/io/speqa
mv src/main/kotlin/io/speqa/speqa src/main/kotlin/io/speqa/speqa
rmdir src/main/kotlin/io/speqa

mkdir -p src/test/kotlin/io/speqa
mv src/test/kotlin/io/speqa/speqa src/test/kotlin/io/speqa/speqa
rmdir src/test/kotlin/io/speqa

# 2. Rename resource files
mv src/main/resources/messages/SpeqaBundle.properties src/main/resources/messages/SpeqaBundle.properties
mv src/main/resources/fileTemplates/internal/"Speqa Test Case.tc.md.ft" src/main/resources/fileTemplates/internal/"SpeQA Test Case.tc.md.ft"
mv src/main/kotlin/io/speqa/speqa/SpeqaBundle.kt src/main/kotlin/io/speqa/speqa/SpeqaBundle.kt

# 3. Replace content in ALL files (case-sensitive replacements in order)
find . -type f \( -name "*.kt" -o -name "*.xml" -o -name "*.properties" -o -name "*.kts" -o -name "*.md" -o -name "*.ft" \) \
  ! -path "./.git/*" ! -path "./.gradle/*" ! -path "./build/*" ! -path "./.intellijPlatform/*" \
  -exec sed -i '' \
    -e 's/SpeQA/SpeQA/g' \
    -e 's/Speqa/Speqa/g' \
    -e 's/speqa/speqa/g' \
    -e 's/SPEQA/SPEQA/g' \
    {} +

# 4. Also rename in CLAUDE.md and memory
sed -i '' -e 's/SpeQA/SpeQA/g' -e 's/Speqa/Speqa/g' -e 's/speqa/speqa/g' CLAUDE.md
```

- [ ] **Step 3: Fix the pre-edit hook script path**

The hook at `.claude/hooks/pre-kt-edit.sh` may reference "speqa" in paths. Check and update.

- [ ] **Step 4: Update settings.gradle.kts rootProject.name**

Verify `settings.gradle.kts` has `rootProject.name = "speqa"` (should be done by sed).

- [ ] **Step 5: Verify compilation**

```bash
./gradlew compileKotlin
```

- [ ] **Step 6: Run tests**

```bash
./gradlew test
```

- [ ] **Step 7: Verify no remaining "speqa" references**

```bash
grep -ri "speqa" src/ --include="*.kt" --include="*.xml" --include="*.properties" --include="*.kts"
grep -ri "speqa" CLAUDE.md
grep -ri "speqa" docs/specs/
```

- [ ] **Step 8: Clean build artifacts**

```bash
rm -rf build/
./gradlew compileKotlin
```
