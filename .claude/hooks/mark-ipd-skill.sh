#!/usr/bin/env bash
# PostToolUse:Skill marker: when the idea-plugin-dev skill is loaded, drop a per-session marker
# so require-ipd-skill.sh stops blocking Kotlin edits for the rest of this session.
input=$(cat)
sid=$(printf '%s' "$input" | jq -r '.session_id // ""')
s=$(printf '%s' "$input" | jq -r '.tool_input.skill // ""')
case "$s" in
  *idea-plugin-dev*) touch "${TMPDIR:-/tmp}/claude-ipd-skill-$sid" ;;
esac
exit 0
