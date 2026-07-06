#!/usr/bin/env bash
# PreToolUse gate: block editing Kotlin sources until the idea-plugin-dev skill has been
# loaded in this session (a marker is dropped by mark-ipd-skill.sh on PostToolUse:Skill).
# The skill documents EDT/slow-op, dumb-mode, threading and UI-DSL rules that repeatedly bite
# IntelliJ plugin work; loading it first prevents whole classes of regressions.
input=$(cat)
sid=$(printf '%s' "$input" | jq -r '.session_id // ""')
p=$(printf '%s' "$input" | jq -r '.tool_input.file_path // ""')
case "$p" in
  *.kt|*.kts)
    if [ ! -f "${TMPDIR:-/tmp}/claude-ipd-skill-$sid" ]; then
      echo "Load the idea-plugin-dev skill (Skill tool) before editing Kotlin code. It documents the EDT/slow-op, dumb-mode, threading (ReadAction.nonBlocking) and Kotlin UI DSL rules for IntelliJ plugin work." >&2
      exit 2
    fi
    ;;
esac
exit 0
