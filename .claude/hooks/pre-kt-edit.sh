#!/bin/bash
# PreToolUse hook: block .kt edits unless spec was edited immediately before
INPUT=$(cat /dev/stdin 2>/dev/null)
echo "[pre-kt-edit] raw input: $INPUT" >> /tmp/speqa-hook-debug.log

FILE_PATH=$(echo "$INPUT" | jq -r '.tool_input.file_path // empty' 2>/dev/null)
echo "[pre-kt-edit] file_path: $FILE_PATH" >> /tmp/speqa-hook-debug.log

if [ -n "$FILE_PATH" ] && echo "$FILE_PATH" | grep -q '\.kt$'; then
  if [ -f /tmp/.speqa-spec-edited ]; then
    rm -f /tmp/.speqa-spec-edited
    echo "[pre-kt-edit] ALLOWED (marker consumed)" >> /tmp/speqa-hook-debug.log
    exit 0
  else
    echo "[pre-kt-edit] BLOCKED" >> /tmp/speqa-hook-debug.log
    echo 'BLOCKED: Update the spec (docs/specs/2026-04-06-speqa-design.md) BEFORE editing .kt files.'
    exit 2
  fi
fi
echo "[pre-kt-edit] SKIPPED (not .kt)" >> /tmp/speqa-hook-debug.log
