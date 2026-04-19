#!/bin/bash
# PreToolUse hook: block .kt edits unless spec was edited at least once this session
INPUT=$(cat /dev/stdin 2>/dev/null)

FILE_PATH=$(echo "$INPUT" | jq -r '.tool_input.file_path // empty' 2>/dev/null)

if [ -n "$FILE_PATH" ] && echo "$FILE_PATH" | grep -q '\.kt$'; then
  if [ -f /tmp/.speqa-spec-edited ]; then
    # Marker stays — one spec edit unlocks all .kt edits for the session
    exit 0
  else
    echo 'BLOCKED: Update the spec (docs/specs/2026-04-06-speqa-design.md) before editing .kt files (once per session).'
    exit 2
  fi
fi
